# Detector model asset

Put the trained, quantized detector here as `detector_int8.onnx`:

```
python training/train.py --model yolo11n.pt --epochs 100 --imgsz 416 --batch 16   # real training (GPU)
python training/export_onnx.py --weights runs/detect/<name>/weights/best.pt --imgsz 416
cp models/detection/detector_int8.onnx android/app/src/main/assets/model/detector_int8.onnx
```

The app runs WITHOUT this file: Model Status will honestly report "not loaded"
and the Live Assistant keeps everything except vision-based detection working.

The smoke-trained model exported in this repo is intentionally not committed
(model binaries stay out of Git - download them from the GitHub Release).
