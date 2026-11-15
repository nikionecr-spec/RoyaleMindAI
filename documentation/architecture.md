# معماری RoyaleMind AI

## 1. اصول

1. **فقط تحلیل:** هیچ کدی ورودی به بازی نمی‌فرستد؛ HUD با `FLAG_NOT_TOUCHABLE` نمایش داده می‌شود و تنها حباب کنترل کوچک تعاملی است.
2. **بدون LLM در حلقه تصمیم:** تصمیم لحظه‌ای، rule-based و چندمعیاره است؛ خروجی با «دلیل» و «درصد اطمینان» ارائه می‌شود.
3. **صداقت اطلاعاتی:** هر عددی که قطعی نیست (مثل اکسیر حریف) با برچسب «تخمین» و مقدار اطمینان نمایش داده می‌شود؛ مدلِ بارگذاری‌نشده به‌صراحت اعلام می‌شود.
4. **یک قرارداد داده بین دو سمت:** JSON پیشنهاد دقیقاً در پایتون و کاتلین یکسان تولید می‌شود (`action / recommended_card / placement / reason / confidence / risk / reasons / alternatives / elixir / opponent / threats`).

## 2. جریان داده

```
┌──────────────────── ScreenCaptureService (foreground) ─────────────────┐
│ MediaProjection → VirtualDisplay → ImageReader(RGBA)                    │
│ throttle 15–20 FPS → frame-skip (acquireLatestImage + drop)             │
│         │                                                               │
│         ▼                                                               │
│ OnnxDetector (assets/model/detector_int8.onnx, 209 classes)             │
│         │  detections (bbox, cls, conf)                                 │
│         ▼                                                               │
│ ByteTrackerLite → BoardUnits (feet position, team via river side)       │
│         │  born enemy tracks = opponent plays                           │
│         ▼                                                               │
│ ElixirEngine (regen + plays)   CardCycleEngine (hand/queue rotation)    │
│         └────────────┬─────────────────────────────────────┘            │
│                      ▼                                                  │
│ PlacementEngine (grid + semantic anchors → ranked points)               │
│                      ▼                                                  │
│ TacticalEngine (counter-fit, timing, tower support, elixir efficiency)  │
│                      ▼                                                  │
│ GameStateRepository (Compose state)                                     │
└──────────────┬──────────────────────────────────────────────────────────┘
               ▼
   OverlayService → OverlayHudView (4 Hz refresh, touch-transparent)
```

## 3. نگاشت به ساختار دستور کار

| بخش دستور کار | محل در ریپو |
|---|---|
| android/overlay | `app/src/main/java/.../overlay/` (OverlayService + OverlayHudView) |
| android/screen_capture | `app/src/main/java/.../capture/ScreenCaptureService.kt` |
| android/native_bridge | ONNX Runtime Android مستقیماً از Kotlin (`vision/OnnxDetector.kt`) — لایه JNI جدا لازم نبود |
| flutter_app | **حذف عمدی:** Overlay/Capture/MediaProjection ذاتاً Native هستند و Flutter یک UI دومِ تکراری می‌ساخت؛ ۸ بخش UI با Jetpack Compose پیاده شده (Dashboard، Live Assistant، Card Database، Match History، Tactical Analysis، Model Status، Performance Monitor، Settings) |
| ai/vision | `ai/vision/` (Python) + `app/.../vision/` (Kotlin) |
| ai/tracking | `ai/tracking/` + `app/.../engine/TrackerEngine.kt` |
| ai/elixir | `ai/elixir/` + `app/.../engine/ElixirEngine.kt` |
| ai/card_cycle | `ai/card_cycle/` + `app/.../engine/CardCycleEngine.kt` |
| ai/tactical | `ai/tactical/` + `app/.../engine/TacticalEngine.kt` + `PlacementEngine.kt` |
| ai/prediction | `ai/prediction/` + `app/.../engine/Predictor.kt` |
| models/ | `models/detection|classification|tracking` (خروجی export_onnx؛ باینری‌ها از Git خارج) |
| datasets/ | `raw / processed/detection / processed/classifier` (split گروهی؛ خارج از Git) |
| training/ | `download.py, validate.py, prepare.py, train.py, evaluate.py, export_onnx.py` |
| tests/ | ۵۰ تست pytest |
| documentation/ | تحقیق، معماری، نصب، توسعه، QC دیتاست، دقت، بنچمارک |

## 4. مرجع هندسی زمین

از SPEC دیتاست (قاب 568×896، گرید 18×32):

- برج‌های ملکه: x = 116/568 = **0.2042** و 461/568 = **0.8114**؛ y = 200/896 = **0.2232** و 689/896 = **0.7690**
- برج‌های شاه: (0.5, 108/896) و (0.5, 790/896)
- رودخانه: y = میانگین دو برج ملکه = **0.4961**
- پل‌ها هم‌تراز ستون برج‌های ملکه (فرض مستند، قابل کالیبراسیون)

مختصات همه‌چیز نرمال است و `ArenaGeometry.normalizedToScreen` با درنظر گرفتن letterbox به پیکسل واقعی گوشی نگاشت می‌کند.

## 5. قرارداد JSON خروجی (نمونه واقعی از تست)

```json
{
  "action": "DEFEND",
  "recommended_card": "mini-pekka",
  "placement": {"x": 0.204, "y": 0.536},
  "reason": "Defensive answer to hog-rider",
  "confidence": 0.566,
  "risk": "low",
  "reasons": ["arrives in time", "tower support"],
  "alternatives": [],
  "elixir": {"player": 9.0, "opponent_estimate": 7.8, "opponent_confidence": 1.0,
             "phase": "single", "elapsed_s": 65.0},
  "opponent": {"hand": [...], "coverage": 1.0, "next": [{"card": "fireball",
               "probability": 0.55, "evolution": false}]},
  "threats": [{"card": "hog-rider", "lane": "left", "eta_to_tower_s": 10.5,
               "threat": 4.28}]
}
```

## 6. تصمیم‌های طراحی مهم

| تصمیم | چرا |
|---|---|
| ByteTrack-lite به‌جای ByteTrack کامل | بدون وابستگی سنگین؛ دو-مرحله‌ای association و مدل حرکت خطی برای دنس‌بودن صحنه کافی است |
| به‌جای OCR برای اکسیر: موتور حسابی + تشخیص پلی | اکسیر حریف اصلاً در UI نمایش داده نمی‌شود؛ مدل حسابی از پلی‌های دیده‌شده دقیق‌تر و سریع‌تر است |
| قفل کلاس/تیم در tracker | track هرگز کلاس یا سمت زمین را وسط راه عوض نمی‌کند |
| ساعات مسابقه: start_match صریح | جلوگیری از رجنِ اکسیر قبل از شروع مسابقه |
| مدل INT8 داینامیک | کاهش حجم و افزایش سرعت CPU موبایل با افت دقت ناچیز (گزارش export) |
| split گروهی بر اساس ویدیو/مسابقه | الزام دستور کار: هیچ مسابقه‌ای بین train/test تقسیم نشود |
