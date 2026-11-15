"""ONNX Runtime YOLO detector wrapper (CPU, mobile-parity pipeline).

Supports models exported from Ultralytics (YOLOv8 / YOLO11):
  - standard export: output tensor (1, 4+nc, N) with xywh boxes + class scores
  - NMS-baked export: output tensor (N, 6) [x0,y0,x1,y1,score,cls]
Every detection carries its confidence score (project requirement).
"""
from __future__ import annotations

import math

import numpy as np
import onnxruntime as ort

from ..tracking import Detection


def letterbox(img: np.ndarray, size: int) -> tuple[np.ndarray, float, tuple[int, int]]:
    """Resize keeping aspect ratio, pad with gray. Returns (blob, scale, pad)."""
    h, w = img.shape[:2]
    scale = min(size / w, size / h)
    nw, nh = int(round(w * scale)), int(round(h * scale))
    resized = np.asarray(img, dtype=np.uint8)
    # simple resize (cv2 used by caller when available)
    try:
        import cv2
        resized = cv2.resize(resized, (nw, nh), interpolation=cv2.INTER_LINEAR)
    except ImportError:  # pragma: no cover - cv2 is an expected dependency
        ys = (np.linspace(0, h - 1, nh)).astype(int)
        xs = (np.linspace(0, w - 1, nw)).astype(int)
        resized = img[np.ix_(ys, xs)]
    pad_x, pad_y = (size - nw) // 2, (size - nh) // 2
    canvas = np.full((size, size, 3), 114, dtype=np.uint8)
    canvas[pad_y:pad_y + nh, pad_x:pad_x + nw] = resized
    return canvas, scale, (pad_x, pad_y)


def nms(boxes: np.ndarray, scores: np.ndarray, iou_thr: float = 0.45) -> list[int]:
    """Greedy NMS. ``boxes`` = (N, 4) xyxy pixels, ``scores`` = (N,)."""
    if len(boxes) == 0:
        return []
    x0, y0, x1, y1 = boxes[:, 0], boxes[:, 1], boxes[:, 2], boxes[:, 3]
    areas = (x1 - x0) * (y1 - y0)
    order = scores.argsort()[::-1]
    keep: list[int] = []
    while order.size > 0:
        i = int(order[0])
        keep.append(i)
        xx0 = np.maximum(x0[i], x0[order[1:]])
        yy0 = np.maximum(y0[i], y0[order[1:]])
        xx1 = np.minimum(x1[i], x1[order[1:]])
        yy1 = np.minimum(y1[i], y1[order[1:]])
        w = np.maximum(0.0, xx1 - xx0)
        h = np.maximum(0.0, yy1 - yy0)
        inter = w * h
        ov = inter / np.maximum(1e-9, areas[i] + areas[order[1:]] - inter)
        order = order[1:][ov <= iou_thr]
    return keep


class OnnxDetector:
    def __init__(self, model_path: str, class_names: list[str],
                 input_size: int = 416, conf_threshold: float = 0.25,
                 nms_iou: float = 0.45):
        self.session = ort.InferenceSession(
            model_path, providers=["CPUExecutionProvider"])
        self.input_name = self.session.get_inputs()[0].name
        self.class_names = class_names
        self.input_size = input_size
        self.conf_threshold = conf_threshold
        self.nms_iou = nms_iou
        shape = self.session.get_inputs()[0].shape
        self.input_size = int(shape[-1]) if isinstance(shape[-1], int) else input_size

    # ------------------------------------------------------------------
    def detect(self, frame_bgr: np.ndarray) -> list[Detection]:
        import cv2
        blob, scale, (pad_x, pad_y) = letterbox(frame_bgr, self.input_size)
        rgb = cv2.cvtColor(blob, cv2.COLOR_BGR2RGB)
        x = rgb.transpose(2, 0, 1)[None].astype(np.float32) / 255.0
        out = self.session.run(None, {self.input_name: x})[0]
        return self._decode(out, scale, pad_x, pad_y, frame_bgr.shape)

    # ------------------------------------------------------------------
    def _decode(self, out: np.ndarray, scale: float, pad_x: int, pad_y: int,
                frame_shape) -> list[Detection]:
        h, w = frame_shape[:2]
        dets: list[Detection] = []
        if out.ndim == 3 and out.shape[2] == 6:
            # NMS-baked export: (1, N, 6) [x0,y0,x1,y1,conf,cls]
            rows = out[0]
            for x0, y0, x1, y1, conf, cls in rows:
                if conf < self.conf_threshold:
                    continue
                dets.append(self._mk(int(cls), float(conf), (x0, y0, x1, y1),
                                     scale, pad_x, pad_y, w, h))
            return dets
        if out.ndim == 3:
            out = out[0]
        if out.shape[0] < out.shape[1]:      # (4+nc, N) -> (N, 4+nc)
            out = out.T
        ncls = out.shape[1] - 4
        boxes_xywh = out[:, :4]
        class_scores = out[:, 4:]
        cls_ids = class_scores.argmax(axis=1)
        confs = class_scores.max(axis=1)
        mask = confs >= self.conf_threshold
        if not mask.any():
            return []
        boxes, confs, cls_ids = boxes_xywh[mask], confs[mask], cls_ids[mask]
        xyxy = np.empty_like(boxes)
        xyxy[:, 0] = boxes[:, 0] - boxes[:, 2] / 2
        xyxy[:, 1] = boxes[:, 1] - boxes[:, 3] / 2
        xyxy[:, 2] = boxes[:, 0] + boxes[:, 2] / 2
        xyxy[:, 3] = boxes[:, 1] + boxes[:, 3] / 2
        keep = nms(xyxy, confs, self.nms_iou)
        for i in keep:
            dets.append(self._mk(int(cls_ids[i]), float(confs[i]),
                                 tuple(xyxy[i]), scale, pad_x, pad_y, w, h))
        return dets

    def _mk(self, cls_id: int, conf: float, xyxy, scale, pad_x, pad_y,
            w: int, h: int) -> Detection:
        x0, y0, x1, y1 = xyxy
        x0 = (x0 - pad_x) / scale
        y0 = (y0 - pad_y) / scale
        x1 = (x1 - pad_x) / scale
        y1 = (y1 - pad_y) / scale
        name = (self.class_names[cls_id] if 0 <= cls_id < len(self.class_names)
                else f"class_{cls_id}")
        bbox = (max(0.0, x0 / w), max(0.0, y0 / h),
                min(1.0, x1 / w), min(1.0, y1 / h))
        return Detection(bbox=bbox, cls_name=name, conf=round(float(conf), 3))

    # ------------------------------------------------------------------
    def latency_ms(self, frame_bgr: np.ndarray, warmup: int = 2, runs: int = 10) -> float:
        import time
        for _ in range(warmup):
            self.detect(frame_bgr)
        t0 = time.perf_counter()
        for _ in range(runs):
            self.detect(frame_bgr)
        return (time.perf_counter() - t0) / runs * 1000.0
