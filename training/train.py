#!/usr/bin/env python3
"""YOLO training wrapper (training pipeline step 7).

Examples:
  # real training (GPU, full dataset)
  python training/train.py --model yolo11n.pt --epochs 100 --imgsz 640 --batch 16

  # CPU smoke run (proves the pipeline end-to-end, NOT a deployable model)
  python training/train.py --model yolo11n.pt --epochs 2 --imgsz 320 --batch 8

Results land in runs/detect/<name>/ (weights/best.pt, results.csv).
"""
import argparse
import os


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", default="datasets/processed/detection/data.yaml")
    ap.add_argument("--model", default="yolo11n.pt")
    ap.add_argument("--epochs", type=int, default=2)
    ap.add_argument("--imgsz", type=int, default=320)
    ap.add_argument("--batch", type=int, default=8)
    ap.add_argument("--workers", type=int, default=1)
    ap.add_argument("--fraction", type=float, default=1.0,
                    help="fraction of the train set used per epoch (CPU time control)")
    ap.add_argument("--name", default="smoke")
    ap.add_argument("--device", default="cpu")
    args = ap.parse_args()

    from ultralytics import YOLO
    model = YOLO(args.model)
    results = model.train(
        data=args.data,
        epochs=args.epochs,
        imgsz=args.imgsz,
        batch=args.batch,
        workers=args.workers,
        fraction=args.fraction,
        device=args.device,
        project="runs",
        name=args.name,
        exist_ok=True,
        verbose=True,
    )
    # persist the real, measured final metrics
    metrics = {}
    try:
        m = results.results_dict
        metrics = {k: round(float(v), 4) for k, v in m.items() if isinstance(v, (int, float))}
    except Exception:
        pass
    with open(f"runs/{args.name}/train_metrics.json", "w") as f:
        import json
        json.dump({"model": args.model, "epochs": args.epochs, "imgsz": args.imgsz,
                   "batch": args.batch, "device": args.device, "metrics": metrics}, f, indent=2)
    print("saved ->", f"runs/{args.name}/train_metrics.json")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
