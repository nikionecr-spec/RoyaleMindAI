# models/tracking

Trained model binaries are NOT committed (see .gitignore). Run the training
pipeline and copy the exported files here:

```bash
python training/train.py && python training/export_onnx.py
```

Then distribute via GitHub Releases and copy the final ONNX into
`android/app/src/main/assets/model/` (see documentation/installation.md).
