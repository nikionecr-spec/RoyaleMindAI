#!/usr/bin/env python3
"""RoyaleMindAI - Dataset downloader (training pipeline step 1).

Downloads a *verified subset* of the fan-made research dataset
`SudhishNeel/clash-royale-detection` from Hugging Face (license: "other",
fan-made research data, not affiliated with Supercell - use for research,
do not redistribute).

Subset (fits constrained disk / RAM):
  - small metadata files: classes.txt, data.yaml, SPEC.md, LABELLING.md
  - crops config          : 27,500 hand-graded unit crops  (~204 MB parquet)
  - crops_predicted config: 42,778 classifier-verified crops (~202 MB parquet)
  - synthetic_arena       : 2 shards with YOLO-style boxes (~285 MB parquet)
      * train-b2-00002-of-00003  (rare-card targeted, 158 classes)
      * train-b3-00001-of-00002  (spell overlays batch)
  - track_grades/**/*.csv : per-track grades with pixel boxes (99 videos)

Usage:
  python training/download.py [--dest datasets/raw] [--skip-predicted]
"""
import argparse
import os
import sys

REPO_ID = "SudhishNeel/clash-royale-detection"

SMALL_FILES = ["classes.txt", "data.yaml", "SPEC.md", "LABELLING.md", "README.md"]
SYNTHETIC_SHARDS = [
    "synthetic_arena/train-b2-00002-of-00003.parquet",
    "synthetic_arena/train-b3-00001-of-00002.parquet",
]


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--dest", default="datasets/raw")
    ap.add_argument("--skip-predicted", action="store_true",
                    help="skip the crops_predicted config (~202 MB)")
    args = ap.parse_args()

    try:
        from huggingface_hub import HfApi, hf_hub_download
    except ImportError:
        print("ERROR: pip install huggingface_hub first", file=sys.stderr)
        return 1

    api = HfApi()
    os.makedirs(args.dest, exist_ok=True)
    free_gb = (os.statvfs("/").f_bavail * os.statvfs("/").f_frsize) / 1e9
    print(f"free disk: {free_gb:.2f} GB")

    # discover parquet shards of the two crop configs
    files = api.list_repo_files(REPO_ID, repo_type="dataset")
    crop_shards = sorted(f for f in files if f.startswith("crops/train-") and f.endswith(".parquet"))
    pred_shards = sorted(f for f in files if f.startswith("crops_predicted/train-") and f.endswith(".parquet"))
    grade_files = sorted(f for f in files if f.startswith("track_grades/") and f.endswith(".csv"))
    print(f"found: {len(crop_shards)} crops shards, {len(pred_shards)} predicted shards, "
          f"{len(grade_files)} grade csv files")

    targets = list(SMALL_FILES) + list(crop_shards) + list(SYNTHETIC_SHARDS) + grade_files
    if not args.skip_predicted:
        targets += pred_shards

    est_gb = 0.0
    for t in targets:
        try:
            info = api.get_paths_info(REPO_ID, t, repo_type="dataset")
            if isinstance(info, list):
                info = info[0]
            est_gb += (info.size or 0) / 1e9
        except Exception:
            pass
    print(f"estimated download: {est_gb:.2f} GB")
    if est_gb > free_gb - 1.0:
        print("WARNING: estimated download leaves <1 GB free; aborting for safety.")
        print("         re-run with --skip-predicted to reduce size.")
        return 2

    ok, fail = 0, []
    for i, fname in enumerate(targets, 1):
        try:
            path = hf_hub_download(
                repo_id=REPO_ID, repo_type="dataset", filename=fname,
                local_dir=args.dest,
            )
            ok += 1
            print(f"[{i}/{len(targets)}] OK  {fname}")
        except Exception as e:  # noqa: BLE001 - report every failure, keep going
            fail.append((fname, str(e)))
            print(f"[{i}/{len(targets)}] FAIL {fname}: {str(e)[:120]}")

    print(f"\ndownloaded {ok}/{len(targets)} -> {args.dest}")
    if fail:
        print("failures:")
        for f, e in fail[:20]:
            print(f"  {f}: {e[:120]}")
    return 0 if not fail else 1


if __name__ == "__main__":
    raise SystemExit(main())
