#!/usr/bin/env python3
"""ONNX export + dynamic INT8 quantization + runtime verification (step 10).

Copies the final artifacts to:
  models/detection/detector.onnx          (fp32)
  models/detection/detector_int8.onnx     (dynamic quantized, smaller)
  models/detection/classes.json           (209 canonical class names)
"""
import argparse
import json
import os
import shutil

import numpy as np
import onnxruntime as ort


def verify(path: str) -> dict:
    sess = ort.InferenceSession(path, providers=["CPUExecutionProvider"])
    inp = sess.get_inputs()[0]
    shape = [d if isinstance(d, int) else 1 for d in inp.shape]
    x = np.random.rand(*shape).astype(np.float32)
    out = sess.run(None, {inp.name: x})[0]
    return {"input_shape": shape, "output_shape": list(out.shape),
            "output_mean": round(float(np.abs(out).mean()), 5),
            "size_mb": round(os.path.getsize(path) / 1e6, 2)}


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--weights", default="runs/smoke/weights/best.pt")
    ap.add_argument("--imgsz", type=int, default=320)
    ap.add_argument("--out", default="models/detection")
    args = ap.parse_args()

    from ultralytics import YOLO
    model = YOLO(args.weights)
    onnx_path = model.export(format="onnx", imgsz=args.imgsz, simplify=True,
                             opset=17, dynamic=False)
    os.makedirs(args.out, exist_ok=True)
    dst = os.path.join(args.out, "detector.onnx")
    shutil.copy(onnx_path, dst)

    # dynamic INT8 (weight-only) quantization
    try:
        from onnxruntime.quantization import quantize_dynamic, QuantType
        int8_dst = os.path.join(args.out, "detector_int8.onnx")
        quantize_dynamic(dst, int8_dst, weight_type=QuantType.QInt8)
    except Exception as e:
        print("quantization skipped:", e)
        int8_dst = None

    # canonical class list
    cls_src = "ai/data/classes_full.json"
    if os.path.exists(cls_src):
        shutil.copy(cls_src, os.path.join(args.out, "classes.json"))

    report = {"fp32": verify(dst), "int8": verify(int8_dst) if int8_dst else None,
              "imgsz": args.imgsz}
    with open(os.path.join(args.out, "export_report.json"), "w") as f:
        json.dump(report, f, indent=2)
    print(json.dumps(report, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
