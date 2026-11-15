# گزارش اعتبارسنجی دیتاست (اندازه‌گیری واقعی)

منبع: `SudhishNeel/clash-royale-detection` — 187 کلاس، 72,778 ردیف در 4 شارد.

| شارد | ردیف | نمونه‌برداری تصویر | خطای decode | تکراری | کادر خراب/کل | لیبل ناشناخته |
|---|---|---|---|---|---|---|
| train-00000-of-00001.parquet | 27,500 | 3,928 | 0 | 0 | 0/0 | 5228 |
| train-00000-of-00001.parquet | 42,778 | 6,111 | 0 | 3 | 0/0 | 3163 |
| train-b2-00002-of-00003.parquet | 1,000 | 142 | 0 | 0 | 0/9,840 | 0 |
| train-b3-00001-of-00002.parquet | 1,500 | 214 | 0 | 0 | 0/16,044 | 0 |

## توزیع کلاس‌ها (پرتکرارترین)
- `train-00000-of-00001.parquet`: ?208 (2159), goblin (1717), spear-goblin (998), barbarian (990), ?191 (639), skeleton-evolution (537), baby-dragon (534), zappy (519), royal-recruit (516), knight (407)
- `train-00000-of-00001.parquet`: drop (4119), goblin (4105), skeleton (2212), spear-goblin (1622), barbarian (1417), the-log (934), skeleton-evolution (784), royal-recruit (767), baby-dragon (754), zappy (719)

## کم‌نمونه‌ترین کلاس‌ها (نیازمند داده بیشتر)
این کلاس‌ها در نسخهٔ کامل مدل باید با دادهٔ بیشتری تقویت شوند:

- `train-00000-of-00001.parquet`: bomber-evolution (6), bat-evolution (6), freeze (5), barbarian-evolution (4), goblin-drill-evolution (4), hunter (4), axe (3), ice-spirit-evolution (3), skeleton-army-evolution (1), tornado (1)
- `train-00000-of-00001.parquet`: bat-evolution (4), goblin-drill-evolution (4), goblin-machine (4), barbarian-evolution (3), hunter (3), ice-spirit-evolution (2), axe (2), skeleton-army-evolution (1), lumberjack (1), tornado (1)

> تاریخ اجرا: خروجی واقعی اسکریپت `training/validate.py` — بدون هیچ عدد فرضی.