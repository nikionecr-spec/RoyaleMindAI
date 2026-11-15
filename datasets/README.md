# Datasets layout

- raw/          downloaded source subset (training/download.py) - NOT in Git
- processed/    detection (YOLO) + classifier crops built by training/prepare.py - NOT in Git
- synthetic/    synthetic generation step (extensible) - generator hooks documented
- validation/ test/  produced by the grouped split (no match leakage)

License of the source dataset: fan-made research data (no redistribution);
only trained weights are distributed. See documentation/research.md.
