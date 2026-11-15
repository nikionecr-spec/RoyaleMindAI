#!/usr/bin/env python3
"""Fetch two complementary synthetic_arena shards (disk-guarded)."""
import os
import sys

from huggingface_hub import hf_hub_download

REPO = "SudhishNeel/clash-royale-detection"
SHARDS = [
    "synthetic_arena/train-b3-00000-of-00002.parquet",
    "synthetic_arena/train-b2-00000-of-00003.parquet",
]
DEST = "datasets/raw"


def free_gb() -> float:
    return os.statvfs("/").f_bavail * os.statvfs("/").f_frsize / 1e9


for s in SHARDS:
    f = free_gb()
    print(f"free {f:.2f} GB - downloading {s}", flush=True)
    if f < 0.9:
        print("ABORT: less than 0.9 GB free", flush=True)
        sys.exit(2)
    hf_hub_download(repo_id=REPO, repo_type="dataset", filename=s, local_dir=DEST)
    print("OK", s, flush=True)

print("ALL DONE", flush=True)
