# RoyaleMind AI

دستیار تاکتیکی زنده برای Clash Royale — یک پنجره شناور (Overlay) روی بازی که میدان را تحلیل می‌کند و پیشنهاد حرکت می‌دهد.

> **سیاست مهم:** این برنامه **فقط تحلیلگر و مربی بصری** است. هیچ ورودی‌ای به بازی تزریق نمی‌کند، هیچ کلاینتی دست‌کاری نمی‌شود و هرگز به‌جای شما بازی نمی‌کند.

**English summary:** RoyaleMind AI is a floating, analysis-only tactical assistant for Clash Royale. It captures the screen locally (MediaProjection), runs an on-device YOLO detector (ONNX Runtime), tracks units (ByteTrack-lite), models elixir and the 8-card cycle, and suggests concrete card placements with reasons and confidence. It never touches the game.

---

## چه چیزهایی واقعاً ساخته شده؟ (وضعیت صادقانه)

| بخش | وضعیت |
|---|---|
| تحقیق و راستی‌آزمایی ۹ منبع | ✅ کامل — `documentation/research.md` |
| دریافت دیتاست مجاز و QC | ✅ 72,778 ردیف بررسی شد (0 خطای decode، 0 کادر خراب از 25,884) |
| پایپ‌لاین آموزش (۶ اسکریپت) | ✅ کارا و اجرا شده |
| Smoke training واقعی YOLO11n (2 اپوک CPU، 1975 فریم، 209 کلاس) | ✅ Precision 0.63 — مخصوص اثبات پایپ‌لاین؛ مدل نهایی با 100 اپوک GPU (دستور در docs) |
| خروجی ONNX + INT8 | ✅ fp32 = 10.7MB، **INT8 = 2.96MB** (بارگذاری و اجرا با ONNX Runtime تأیید شد) |
| تأخیر استنتاج (CPU میزبان، 320px) | ✅ **16.3ms/فریم** میانگین — `documentation/accuracy_report.md` |
| موتورهای AI پایتون (۶ موتور) | ✅ + **۵۰ تست pytest پاس** |
| اپ اندروید (Kotlin/Compose) | ✅ سورس کامل: Overlay، MediaProjection، ONNX Runtime، موتورها، ۸ صفحه UI |
| APK | 📦 با Push، GitHub Actions آن را می‌سازد (`.github/workflows/build-apk.yml`) |
| تست روی گوشی Poco X4 Pro | ⏳ نیازمند نصب APK روی دستگاه واقعی (چک‌لیست در `documentation/benchmark.md`) |

## معماری در یک نگاه

```
MediaProjection (capture 15-20 FPS, frame-skip)
        │
        ▼
OnnxDetector (ONNX INT8, on-device)
        │ detections + confidence
        ▼
ByteTrackerLite ──► BoardUnits (position, velocity, team)
        │
        ▼
ElixirEngine ── CardCycleEngine ── PlacementEngine
        │
        ▼
TacticalEngine (multi-criteria, rule-based — NO LLM)
        │
        ▼
Overlay HUD: green placement arrow • red threat circles • elixir bars
```

## شروع سریع (توسعه‌دهنده)

```bash
# 1) تست موتورها (Python 3.12)
pip install -r requirements.txt
python -m pytest tests/ -q

# 2) دیتاست (زیرمجموعه مجاز، ~676MB)
python training/download.py
python training/validate.py
python training/prepare.py

# 3) آموزش کامل روی GPU
python training/train.py --model yolo11n.pt --epochs 100 --imgsz 416 --batch 16
python training/evaluate.py
python training/export_onnx.py

# 4) APK
cd android && gradle :app:assembleDebug
# یا فقط Push کنید - CI می‌سازد و Artifact «RoyaleMindAI-debug-apk» می‌دهد
```

## مستندات

- `documentation/research.md` — راستی‌آزمایی منابع، مجوزها، تصمیم‌ها
- `documentation/architecture.md` — معماری و نگاشت به ساختار دستور کار
- `documentation/installation.md` — نصب روی گوشی و PC
- `documentation/development.md` — راهنمای توسعه و قراردادهای داده
- `documentation/dataset_report.md` — QC واقعی دیتاست
- `documentation/accuracy_report.md` — دقت اندازه‌گیری‌شده
- `documentation/benchmark.md` — بنچمارک و اهداف عملکرد

## مجوز و انتساب

- کد این ریپو: **MIT** (فایل `LICENSE`).
- دیتاست آموزش: [SudhishNeel/clash-royale-detection](https://huggingface.co/datasets/SudhishNeel/clash-royale-detection) (fan-made research data؛ **بازنشر نمی‌شود**، فقط وزن‌های مدل توزیع می‌شود).
- Clash Royale برند Supercell است؛ این پروژه وابسته و تأییدشده نیست.
