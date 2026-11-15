#!/usr/bin/env python3
"""Dataset validation / quality control (training pipeline step 2).

Checks the downloaded raw data:
  - row counts per parquet shard
  - image decodability (sampled)
  - exact-duplicate detection (content md5 on sampled images)
  - bbox sanity: in-bounds, x1>x0, y1>y0, label present in classes.txt
  - class distribution (head + tail)
Writes a real, measured report to documentation/dataset_report.md.
"""
import argparse
import hashlib
import json
import os
from collections import Counter

import pyarrow.parquet as pq

RAW_DEFAULT = "datasets/raw"
REPORT_MD = "documentation/dataset_report.md"
REPORT_JSON = "documentation/dataset_report.json"


def md5_of_bytes(b: bytes) -> str:
    return hashlib.md5(b).hexdigest()


def image_bytes(rec_image) -> bytes:
    # HF 'image' feature stored as dict {'bytes': ..., 'path': ...}
    if isinstance(rec_image, dict):
        return rec_image.get("bytes") or b""
    return b""


def validate_parquet(path: str, class_names: list[str], sample_every: int = 7):
    pf = pq.ParquetFile(path)
    n_rows = pf.metadata.num_rows
    cols = pf.schema_arrow.names
    label_counts: Counter = Counter()
    decode_fail = 0
    dup_count = 0
    bbox_bad = 0
    bbox_total = 0
    unknown_label = 0
    seen_md5 = set()
    checked = 0

    for batch in pf.iter_batches(batch_size=256):
        d = batch.to_pydict()
        imgs = d.get("image") or []
        labels = d.get("label") or d.get("pred") or d.get("trusted") or [None] * len(imgs)
        bboxes = d.get("objects") or None
        trusted = d.get("trusted") if "trusted" in cols else None
        for i, img in enumerate(imgs):
            row_label = None
            if trusted is not None:
                row_label = trusted[i] or None
            if row_label is None and i < len(labels):
                row_label = labels[i]
            if isinstance(row_label, int):
                row_label = class_names[row_label] if row_label < len(class_names) else f"?{row_label}"
            if row_label:
                label_counts[row_label] += 1
                if row_label not in class_names and row_label not in ("drop",):
                    unknown_label += 1
            checked += 1
            if checked % sample_every:
                continue
            raw = image_bytes(img)
            if raw:
                if md5_of_bytes(raw) in seen_md5:
                    dup_count += 1
                seen_md5.add(md5_of_bytes(raw))
                try:
                    import cv2
                    import numpy as np
                    arr = np.frombuffer(raw, dtype=np.uint8)
                    ok = cv2.imdecode(arr, cv2.IMREAD_COLOR) is not None
                    if not ok:
                        decode_fail += 1
                except Exception:
                    decode_fail += 1
        if bboxes is not None:
            for obj in bboxes:
                boxes = (obj or {}).get("bbox") or []
                for b in boxes:
                    bbox_total += 1
                    if not b or len(b) != 4 or b[2] <= b[0] or b[3] <= b[1] \
                            or b[0] < 0 or b[1] < 0 or b[2] > 568 or b[3] > 896:
                        bbox_bad += 1

    return {
        "file": os.path.basename(path),
        "rows": n_rows,
        "columns": cols,
        "sampled": checked // sample_every,
        "decode_failures": decode_fail,
        "duplicate_images_in_sample": dup_count,
        "bad_bboxes": bbox_bad,
        "total_bboxes": bbox_total,
        "unknown_labels": unknown_label,
        "label_top10": dict(label_counts.most_common(10)),
        "label_bottom10": dict(label_counts.most_common()[-10:]),
        "distinct_labels": len(label_counts),
    }


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--raw", default=RAW_DEFAULT)
    args = ap.parse_args()

    class_names = [l.strip() for l in open(os.path.join(args.raw, "classes.txt"),
                                           encoding="utf-8").read().splitlines() if l.strip()]
    print(f"classes.txt: {len(class_names)} classes")

    shards = []
    for sub in ("crops", "crops_predicted", "synthetic_arena"):
        d = os.path.join(args.raw, sub)
        if os.path.isdir(d):
            shards += [os.path.join(d, f) for f in sorted(os.listdir(d)) if f.endswith(".parquet")]
    print(f"validating {len(shards)} parquet shards ...")

    results = {"classes": len(class_names), "shards": []}
    for p in shards:
        print("  ...", os.path.basename(p), flush=True)
        results["shards"].append(validate_parquet(p, class_names))

    total_rows = sum(s["rows"] for s in results["shards"])
    results["total_rows"] = total_rows

    os.makedirs(os.path.dirname(REPORT_MD), exist_ok=True)
    with open(REPORT_JSON, "w", encoding="utf-8") as f:
        json.dump(results, f, indent=2, ensure_ascii=False)

    md = ["# گزارش اعتبارسنجی دیتاست (اندازه‌گیری واقعی)",
          "",
          f"منبع: `SudhishNeel/clash-royale-detection` — {len(class_names)} کلاس، {total_rows:,} ردیف در {len(shards)} شارد.",
          "",
          "| شارد | ردیف | نمونه‌برداری تصویر | خطای decode | تکراری | کادر خراب/کل | لیبل ناشناخته |",
          "|---|---|---|---|---|---|---|"]
    for s in results["shards"]:
        md.append(f"| {s['file']} | {s['rows']:,} | {s['sampled']:,} | {s['decode_failures']} | "
                  f"{s['duplicate_images_in_sample']} | {s['bad_bboxes']}/{s['total_bboxes']:,} | {s['unknown_labels']} |")
    md += ["", "## توزیع کلاس‌ها (پرتکرارترین)"]
    for s in results["shards"]:
        if s["label_top10"]:
            md.append(f"- `{s['file']}`: " + ", ".join(f"{k} ({v})" for k, v in s["label_top10"].items()))
    md += ["", "## کم‌نمونه‌ترین کلاس‌ها (نیازمند داده بیشتر)",
           "این کلاس‌ها در نسخهٔ کامل مدل باید با دادهٔ بیشتری تقویت شوند:",
           ""]
    for s in results["shards"]:
        if s["label_bottom10"]:
            md.append(f"- `{s['file']}`: " + ", ".join(f"{k} ({v})" for k, v in s["label_bottom10"].items()))
    md += ["", "> تاریخ اجرا: خروجی واقعی اسکریپت `training/validate.py` — بدون هیچ عدد فرضی."]
    with open(REPORT_MD, "w", encoding="utf-8") as f:
        f.write("\n".join(md))
    print("report ->", REPORT_MD)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
