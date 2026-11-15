import numpy as np
import pytest

from ai.vision.detector import letterbox, nms, OnnxDetector


def test_letterbox_output_size_and_scale():
    img = np.zeros((896, 568, 3), dtype=np.uint8)
    blob, scale, (px, py) = letterbox(img, 416)
    assert blob.shape == (416, 416, 3)
    assert abs(scale - 416 / 896) < 0.01      # height-bound
    assert px >= 0 and py == 0


def test_nms_suppresses_overlaps():
    boxes = np.array([
        [10, 10, 50, 50],
        [12, 12, 52, 52],     # overlaps heavily -> suppressed
        [100, 100, 140, 140],  # far away -> kept
    ], dtype=np.float32)
    scores = np.array([0.9, 0.8, 0.7])
    keep = nms(boxes, scores, 0.45)
    assert 0 in keep and 2 in keep and 1 not in keep


def test_detector_requires_real_model(tmp_path):
    # no trained model exists in a fresh checkout -> construction must fail loudly
    with pytest.raises(Exception):
        OnnxDetector(str(tmp_path / "missing.onnx"), class_names=["knight"])
