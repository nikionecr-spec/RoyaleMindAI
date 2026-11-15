# راهنمای نصب

## الف) نصب APK روی گوشی (Poco X4 Pro)

1. فایل APK را از GitHub Actions بگیرید: به تب **Actions** → اجرای `build-apk` → Artifact با نام `RoyaleMindAI-debug-apk` → دانلود.
2. APK را روی گوشی منتقل و نصب کنید (اجازه «Install unknown apps» برای مرورگر/فایل‌منیجر لازم است؛ APK دیباگ با کلید دیباگ امضا شده و قابل نصب است).
3. در اولین اجرا:
   - **مجوز پنجره شناور:** صفحه Live Assistant → «Floating window permission» → اجازه نمایش روی برنامه‌ها.
   - **ضبط صفحه:** «Start assistant» → دیالوگ MediaProjection → اجازه. (نوتیفیکیشن ماندگار نمایش داده می‌شود — الزام اندروید ۱۴.)
4. Clash Royale را باز کنید؛ پنجره شناور وضعیت را نشان می‌دهد.
5. دست شما را یک‌بار در صفحه Live Assistant با نام کلاس کارت‌ها ثبت کنید (مثل `hog-rider`) — تا نسخه بعدی که طبقه‌بند نوار دست اضافه می‌شود، این ورودی دستی مبنای پیشنهادها است.
6. خاموش‌کردن سریع: تیک کنترل شناور را لمس کنید (کوچک‌سازی HUD) یا «Stop» در اپ.

## ب) محیط توسعه پایتون (موتورها و آموزش)

```bash
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
# برای آموزش:
pip install ultralytics onnxruntime onnx opencv-python-headless

python -m pytest tests/ -q          # باید 50 تست پاس شود
python training/download.py         # زیرمجموعه دیتاست مجاز (~676MB)
python training/validate.py         # QC و گزارش
python training/prepare.py          # ساخت دیتاست YOLO/کلاس‌بندی (split گروهی)
```

### آموزش کامل (GPU توصیه‌شده)

```bash
python training/train.py --model yolo11n.pt --epochs 100 --imgsz 416 --batch 16
python training/evaluate.py --weights runs/detect/train/weights/best.pt --imgsz 416
python training/export_onnx.py --weights runs/detect/train/weights/best.pt --imgsz 416
```

روی CPU هم می‌توان smoketest گرفت (مثل همین ریپو):

```bash
python training/train.py --model yolo11n.pt --epochs 2 --imgsz 320 --batch 8 --name smoke
```

### مدل در اپ

```bash
cp models/detection/detector_int8.onnx android/app/src/main/assets/model/detector_int8.onnx
```

تا وقتی این فایل نباشد، صفحه Model Status صادقانه «بارگذاری نشده» نشان می‌دهد و بقیه موتورها کار می‌کنند.

## ج) ساخت APK به‌صورت محلی

```bash
cd android
gradle :app:assembleDebug      # نیاز به JDK 17 + Android SDK
# خروجی: app/build/outputs/apk/debug/app-debug.apk
```

اگر SDK ندارید، همان Push به GitHub این کار را با Actions انجام می‌دهد.

## د) نکات امنیتی

- هرگز توکن GitHub را در کد، لاگ یا کامیت قرار ندهید؛ `.gitignore` فایل‌های `.env` و `*token*` را مسدود می‌کند.
- دیتاست مجاز در Git قرار نمی‌گیرد (مجوز آن برای بازنشر نیست)؛ فقط وزن‌های مدل خودتان را توزیع کنید و attribution دیتاست را نگه دارید.
