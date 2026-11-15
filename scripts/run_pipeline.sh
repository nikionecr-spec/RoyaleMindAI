#!/usr/bin/env bash
# Full pipeline runner - from data to a mobile-ready ONNX model.
# Real training needs a GPU machine; on CPU use --smoke.
set -euo pipefail
cd "$(dirname "$0")/.."

MODE="${1:-full}"

echo "== 1/6 dataset download (subset, ~676MB) =="
python training/download.py

echo "== 2/6 dataset validation QC =="
python training/validate.py

echo "== 3/6 prepare YOLO + classifier datasets (grouped split) =="
python training/prepare.py

if [[ "$MODE" == "--smoke" ]]; then
  echo "== 4/6 SMOKE training (pipeline validation, NOT deployable) =="
  python training/train.py --model yolo11n.pt --epochs 2 --imgsz 320 --batch 8 --name smoke
  W=runs/smoke/weights/best.pt; I=320
else
  echo "== 4/6 real training (GPU recommended) =="
  python training/train.py --model yolo11n.pt --epochs 100 --imgsz 416 --batch 16 --name full
  W=runs/full/weights/best.pt; I=416
fi

echo "== 5/6 evaluation on the held-out test split + ONNX CPU latency =="
python training/evaluate.py --weights "$W" --imgsz "$I"

echo "== 6/6 ONNX export + INT8 quantization =="
python training/export_onnx.py --weights "$W" --imgsz "$I"

echo "== done. copy models/detection/detector_int8.onnx into the app assets =="
