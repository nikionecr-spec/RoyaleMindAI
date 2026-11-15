"""ONNX crop classifier for hand cards (208-class head on 112px crops)."""
from __future__ import annotations

import numpy as np
import onnxruntime as ort


class OnnxCropClassifier:
    def __init__(self, model_path: str, class_names: list[str], input_size: int | None = None):
        self.session = ort.InferenceSession(model_path, providers=["CPUExecutionProvider"])
        self.input_name = self.session.get_inputs()[0].name
        self.class_names = class_names
        shape = self.session.get_inputs()[0].shape
        self.input_size = int(shape[-1]) if isinstance(shape[-1], int) else (input_size or 112)

    def classify(self, crop_bgr: np.ndarray, top_k: int = 3) -> list[dict]:
        import cv2
        img = cv2.cvtColor(crop_bgr, cv2.COLOR_BGR2RGB)
        img = cv2.resize(img, (self.input_size, self.input_size),
                         interpolation=cv2.INTER_LINEAR)
        x = img.transpose(2, 0, 1)[None].astype(np.float32) / 255.0
        logits = self.session.run(None, {self.input_name: x})[0][0]
        e = np.exp(logits - logits.max())
        probs = e / e.sum()
        idx = probs.argsort()[::-1][:top_k]
        return [{"label": self.class_names[i] if 0 <= i < len(self.class_names) else str(i),
                 "confidence": round(float(probs[i]), 4)} for i in idx]
