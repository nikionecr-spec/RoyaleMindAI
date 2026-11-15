#!/usr/bin/env python3
"""Model evaluation (training pipeline step 8) + inference latency benchmark.

- Runs ultralytics validation on the held-out test split (grouped by match -
  no leakage) and records the REAL measured metrics.
- Benchmarks ONNX inference latency on the canonical 568x896 frame.
Outputs documentation/accuracy_report.json (+ .md).
"""
import argparse
import json
import os
import time


def eval_detection(weights: str, data: str, imgsz: int, device: str) -> dict:
    from ultralytics import YOLO
    model = YOLO(weights)
    r = model.val(data=data, split="test", imgsz=imgsz, device=device,
                  project="runs/eval", name="test", exist_ok=True, verbose=True)
    box = r.box
    out = {}
    for key, val in (("precision", box.mp), ("recall", box.mr),
                     ("map50", box.map50), ("map5095", box.map)):
        out[key] = round(float(val), 4)
    return out


def bench_onnx(onnx_path: str, runs: int = 30) -> dict:
    import numpy as np
    import onnxruntime as ort
    sess = ort.InferenceSession(onnx_path, providers=["CPUExecutionProvider"])
    inp = sess.get_inputs()[0]
    h, w = (inp.shape[-2], inp.shape[-1]) if isinstance(inp.shape[-1], int) else (416, 416)
    x = np.random.rand(1, 3, h, w).astype(np.float32)
    for _ in range(5):
        sess.run(None, {inp.name: x})
    t0 = time.perf_counter()
    for _ in range(runs):
        sess.run(None, {inp.name: x})
    ms = (time.perf_counter() - t0) / runs * 1000
    return {"input": f"1x3x{h}x{w}", "avg_latency_ms": round(ms, 2),
            "fps_theoretical": round(1000 / ms, 1)}


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--weights", default="runs/smoke/weights/best.pt")
    ap.add_argument("--data", default="datasets/processed/detection/data.yaml")
    ap.add_argument("--imgsz", type=int, default=320)
    ap.add_argument("--device", default="cpu")
    ap.add_argument("--onnx", default="models/detection/detector.onnx")
    args = ap.parse_args()

    report: dict = {"generated_by": "training/evaluate.py"}

    if os.path.exists(args.weights):
        print("evaluating", args.weights, "on test split ...")
        report["detection_test"] = eval_detection(args.weights, args.data, args.imgsz, args.device)
    else:
        report["detection_test"] = None
        print("no trained weights found at", args.weights)

    if os.path.exists(args.onnx):
        print("benchmarking", args.onnx, "...")
        report["onnx_cpu_benchmark"] = bench_onnx(args.onnx)
    else:
        report["onnx_cpu_benchmark"] = None

    os.makedirs("documentation", exist_ok=True)
    with open("documentation/accuracy_report.json", "w", encoding="utf-8") as f:
        json.dump(report, f, indent=2, ensure_ascii=False)

    md = ["# گزارش دقت مدل (اعداد واقعی و اندازه‌گیری‌شده)", ""]
    if report["detection_test"]:
        d = report["detection_test"]
        md += ["## Detection (test split، بدون نشت داده)", "",
               "| metric | value |", "|---|---|"]
        md += [f"| {k} | {v} |" for k, v in d.items()]
    else:
        md += ["مدل آموزش‌دیده‌ای برای ارزیابی موجود نیست — ابتدا train.py را اجرا کنید."]
    if report["onnx_cpu_benchmark"]:
        b = report["onnx_cpu_benchmark"]
        md += ["", "## Benchmark استنتاج CPU (میزبان)", "",
               f"- ورودی: `{b['input']}`", f"- میانگین تأخیر: **{b['avg_latency_ms']} ms**",
               f"- FPS نظری: {b['fps_theoretical']}",
               "", "> این تأخیر روی CPU میزبان است؛ تأخیر روی گوشی باید با benchmark.md اندازه‌گیری شود."]
    with open("documentation/accuracy_report.md", "w", encoding="utf-8") as f:
        f.write("\n".join(md))
    print("report -> documentation/accuracy_report.md")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
