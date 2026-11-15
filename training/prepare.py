#!/usr/bin/env python3
"""Dataset preparation (training pipeline step 3).

Builds two ready-to-train datasets from the validated raw parquet files:

1. detection/  - YOLO format from synthetic_arena frames (568x896 + pixel boxes)
                 grouped split by ``backdrop_video`` so frames of one match never
                 leak across train/val/test (hard project requirement).
2. classifier/ - ultralytics-cls format from the graded crops (208 classes),
                 grouped split by video_id.

After a successful run the parquet shards are REMOVED to free disk (all content
is fully materialized into images). Re-run training/download.py to restore.
"""
import argparse
import json
import os
import shutil
from collections import Counter, defaultdict

import pyarrow.parquet as pq

RAW = "datasets/raw"
DET_OUT = "datasets/processed/detection"
CLS_OUT = "datasets/processed/classifier"


def load_classes(raw: str) -> list[str]:
    """Canonical class list. Prefer classes_full.txt (209 names parsed from the
    dataset card - the parquet label indices follow it); fall back to
    classes.txt (187, older) or data.yaml."""
    for name in ("classes_full.txt", "classes.txt"):
        p = os.path.join(raw, name)
        if os.path.exists(p):
            return [l.strip() for l in open(p, encoding="utf-8").read().splitlines() if l.strip()]
    import yaml
    d = yaml.safe_load(open(os.path.join(raw, "data.yaml"), encoding="utf-8"))
    n = d["names"]
    return [n[k] for k in sorted(n)] if isinstance(n, dict) else list(n)


def label_to_name(label, classes: list[str]) -> str | None:
    """HF stores crops labels as ints (indices into the card's name list);
    synthetic categories are strings."""
    if label is None:
        return None
    if isinstance(label, int) or (isinstance(label, str) and label.isdigit()):
        i = int(label)
        return classes[i] if 0 <= i < len(classes) else None
    return label


def img_bytes(rec) -> bytes:
    if isinstance(rec, dict):
        return rec.get("bytes") or b""
    return b""


def grouped_split(keys: list[str], ratios=(0.8, 0.1, 0.1), seed: int = 42):
    """Split unique group keys into train/val/test (no group crosses splits)."""
    import random
    uniq = sorted(set(k for k in keys if k))
    random.Random(seed).shuffle(uniq)
    n = len(uniq)
    n_train = max(1, int(n * ratios[0]))
    n_val = max(1, int(n * ratios[1]))
    split = {}
    for i, k in enumerate(uniq):
        split[k] = "train" if i < n_train else ("val" if i < n_train + n_val else "test")
    return split


def prepare_detection(classes: list[str], out_root: str, max_images: int | None):
    shards = sorted(os.path.join(RAW, "synthetic_arena", f)
                    for f in os.listdir(os.path.join(RAW, "synthetic_arena"))
                    if f.endswith(".parquet"))
    # pass 1: collect all backdrop videos to build the grouped split
    all_groups = []
    for shard in shards:
        pf = pq.ParquetFile(shard)
        tbl = pf.read(columns=["backdrop_video"])
        all_groups += tbl.column(0).to_pylist()
    split = grouped_split(all_groups)
    print(f"detection frames: {len(all_groups)} from {len(set(all_groups))} backdrop videos")

    counts = Counter()
    class_counts = Counter()
    for shard in shards:
        pf = pq.ParquetFile(shard)
        for batch in pf.iter_batches(batch_size=64,
                                     columns=["image", "image_id", "objects", "backdrop_video"]):
            d = batch.to_pydict()
            for img, image_id, obj, group in zip(d["image"], d["image_id"],
                                                 d["objects"], d["backdrop_video"]):
                raw = img_bytes(img)
                if not raw:
                    counts["no_image"] += 1
                    continue
                # objects may arrive as a struct {"bbox": [...], "category": [...]}
                # or as flattened columns "objects.bbox" / "objects.category"
                if obj is not None:
                    boxes = obj.get("bbox") or []
                    cats = obj.get("category") or []
                else:
                    boxes, cats = [], []
                lines = []
                for b, c in zip(boxes, cats):
                    c = label_to_name(c, classes)
                    if c not in classes or len(b) != 4:
                        continue
                    x0, y0, x1, y1 = b
                    if x1 <= x0 or y1 <= y0 or x0 < 0 or y0 < 0 or x1 > 568 or y1 > 896:
                        continue
                    cx, cy = (x0 + x1) / 2 / 568, (y0 + y1) / 2 / 896
                    w, h = (x1 - x0) / 568, (y1 - y0) / 896
                    lines.append(f"{classes.index(c)} {cx:.6f} {cy:.6f} {w:.6f} {h:.6f}")
                    class_counts[c] += 1
                if not lines:
                    counts["skipped_empty"] += 1
                    continue
                split_name = split[group]
                dimg = os.path.join(out_root, "images", split_name)
                dlab = os.path.join(out_root, "labels", split_name)
                os.makedirs(dimg, exist_ok=True)
                os.makedirs(dlab, exist_ok=True)
                name = f"syn_{group}_{image_id}"
                with open(os.path.join(dimg, name + ".jpg"), "wb") as f:
                    f.write(raw)
                with open(os.path.join(dlab, name + ".txt"), "w") as f:
                    f.write("\n".join(lines))
                counts[split_name] += 1
                if max_images and sum(counts[k] for k in ("train", "val", "test")) >= max_images:
                    return counts, class_counts
    return counts, class_counts


def prepare_classifier(classes: list[str], out_root: str, max_images: int | None):
    shards = sorted(os.path.join(RAW, "crops", f)
                    for f in os.listdir(os.path.join(RAW, "crops"))
                    if f.endswith(".parquet"))
    # pass 1: video ids -> grouped split
    all_vids = []
    for shard in shards:
        pf = pq.ParquetFile(shard)
        tbl = pf.read(columns=["video_id"])
        all_vids += tbl.column(0).to_pylist()
    split = grouped_split(all_vids)
    print(f"classifier crops: {len(all_vids)} rows from {len(set(all_vids))} videos")

    counts = Counter()
    total = 0
    for shard in shards:
        pf = pq.ParquetFile(shard)
        for batch in pf.iter_batches(batch_size=64, columns=["image", "label", "video_id"]):
            d = batch.to_pydict()
            for img, label, vid in zip(d["image"], d["label"], d["video_id"]):
                if max_images and total >= max_images:
                    return counts
                raw = img_bytes(img)
                if not raw or not label or label == "drop" or label_to_name(label, classes) not in classes:
                    continue
                dst = os.path.join(out_root, split[vid], label_to_name(label, classes))
                os.makedirs(dst, exist_ok=True)
                fn = os.path.join(dst, f"cr_{abs(hash((vid, total))) % 10**10}.jpg")
                with open(fn, "wb") as f:
                    f.write(raw)
                counts[split[vid]] += 1
                total += 1
    return counts


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--max-detection", type=int, default=None,
                    help="cap detection frames (smoke runs)")
    ap.add_argument("--max-classifier", type=int, default=None)
    ap.add_argument("--keep-parquet", action="store_true")
    args = ap.parse_args()

    classes = load_classes(RAW)
    os.makedirs(DET_OUT, exist_ok=True)
    os.makedirs(CLS_OUT, exist_ok=True)

    det_counts, class_counts = prepare_detection(classes, DET_OUT, args.max_detection)
    cls_counts = prepare_classifier(classes, CLS_OUT, args.max_classifier)

    print("detection:", dict(det_counts))
    print("classifier:", dict(cls_counts))

    # SAFETY: never delete the raw parquet files unless something was produced
    wrote_det = det_counts.get("train", 0) + det_counts.get("val", 0) + det_counts.get("test", 0)
    wrote_cls = cls_counts.get("train", 0) + cls_counts.get("val", 0) + cls_counts.get("test", 0)
    if wrote_det == 0 or wrote_cls == 0:
        print("ERROR: extraction produced nothing - raw parquet files are KEPT for debugging.")
        return 1

    # data.yaml for YOLO
    with open(os.path.join(DET_OUT, "data.yaml"), "w", encoding="utf-8") as f:
        f.write(f"path: {os.path.abspath(DET_OUT)}\ntrain: images/train\nval: images/val\ntest: images/test\n"
                f"nc: {len(classes)}\nnames:\n")
        for c in classes:
            f.write(f"  - {c}\n")

    # provenance
    with open(os.path.join(DET_OUT, "provenance.json"), "w", encoding="utf-8") as f:
        json.dump({"source": "SudhishNeel/clash-royale-detection (synthetic_arena)",
                   "license": "other (fan-made research data, no redistribution)",
                   "split_rule": "grouped by backdrop_video - no match leaks across splits",
                   "counts": det_counts,
                   "classes_with_boxes": len(class_counts)}, f, indent=2, ensure_ascii=False)

    if not args.keep_parquet:
        for sub in ("synthetic_arena", "crops", "crops_predicted"):
            d = os.path.join(RAW, sub)
            if os.path.isdir(d):
                shutil.rmtree(d)
                print("removed parquet dir (re-run download.py to restore):", d)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
