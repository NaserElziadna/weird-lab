# Omooma UI test report

Device: sdk_gphone64_x86_64, Android 14

## Onboarding
  - texts on onboarding: أهلًا بكِ في أمومة | رفيقتك الهادئة لمتابعة الرضاعة: مؤقت، سجل، وتنبيهات لطيفة. | اسم الطفل (اختياري) | اسمكِ (اختياري) | منبّه الرضعة | متوقف | كل فترة | أوقات ثابتة | 2 س | 2.5 س | 3 س | 4 س | يرنّ المنبّه بعد كل رضعة بهذه المدة. يمكنكِ تغييرها لاحقًا. | هيا نبدأ
- ✅ baby name field
- ✅ interval chip 3h
- ✅ interval chip 2.5h
- ✅ onboarding: fixed times
- ✅ onboarding: interval
- ✅ start button

## Permissions
- ✅ system dialog: tapped «Allow»
- ✅ battery permission
- ✅ system dialog: tapped «Allow»
  - texts on permissions_done: نحتاج إذنكِ كي يعمل المنبّه | حتى يرنّ تنبيه الرضعة في وقته، حتى لو كان الهاتف مقفلًا. | الإشعارات والمنبّه | ضرورية لظهور التنبيه والمؤقت على الشاشة. | تم السماح | المنبّه الدقيق | يجعل التنبيه يرنّ في الدقيقة المحددة تمامًا. | تم السماح | العمل في الخلفية | يمنع الهاتف من إيقاف المنبّه لتوفير البطارية. | تم السماح | ممتاز، هيا نبدأ
- ✅ continue (all granted)

## Home + feeding session
  - texts on home_empty: — | لا توجد بعد | — | بعد أول رضعة | رضعة جديدة لـ Mohammad | اختاري الجهة ثم اضغطي ابدأ | الصدر | الأيمن | مقترح | الصدر | الأيسر |   | ابدأ | زجاجة | حفاض | تسجيل يدوي | ملخص اليوم | 0 | رضعات | 0 د | مدة الرضاعة | 0 | مل زجاجة | 0 | حفاضات | خذي نفسًا عميقًا… أنتِ وطفلك بخير. | الرئيسية | السجل | الإحصائيات | الإعدادات
- ✅ choose left side
- ✅ START feeding
  - texts on session_running: — | لا توجد بعد | رضعة جارية | المؤقت يعمل | رضعة جارية | إيقاف | تبديل | إنهاء | إلغاء الرضعة دون حفظ | زجاجة | حفاض | تسجيل يدوي | ملخص اليوم | 0 | رضعات | 0 د | مدة الرضاعة | 0 | مل زجاجة | 0 | حفاضات | خذي نفسًا عميقًا… أنتِ وطفلك بخير. | الرئيسية | السجل | الإحصائيات | الإعدادات
- ✅ PAUSE
- ✅ RESUME
- ✅ SWITCH SIDE
- ✅ FINISH
- ✅ close saved sheet
  - texts on home_after_feed: 6:48 ص | الآن | 9:17 ص | بعد 2 س 28 د | رضعة جديدة لـ Mohammad | اختاري الجهة ثم اضغطي ابدأ | الصدر | الأيمن | مقترح | الصدر | الأيسر |   | ابدأ | زجاجة | حفاض | تسجيل يدوي | ملخص اليوم | 1 | رضعات | 52 ث | مدة الرضاعة | 0 | مل زجاجة | 0 | حفاضات | خذي نفسًا عميقًا… أنتِ وطفلك بخير. | الرئيسية | السجل | الإحصائيات | الإعدادات

## Session from the notification
- ✅ start again
- ❌ **notification: switch side**: button/label «تبديل الجهة» not found on screen
- ✅ notification: finish

## Quick add
- ✅ quick: bottle
- ✅ bottle 120 ml
- ✅ bottle +10
- ✅ save bottle
- ✅ quick: diaper
- ✅ diaper type
- ✅ quick: manual
- ✅ manual +1 min
- ✅ manual right side
- ✅ save manual
  - texts on home_after_quick_add: رضعة جارية | إيقاف | تبديل | إنهاء | إلغاء الرضعة دون حفظ | زجاجة | حفاض | تسجيل يدوي | ملخص اليوم | 3 | رضعات | 11 د | مدة الرضاعة | 130 | مل زجاجة | 1 | حفاضات | خذي نفسًا عميقًا… أنتِ وطفلك بخير. | الرئيسية | السجل | الإحصائيات | الإعدادات

## History
- ✅ tab: history
  - texts on history: السجل | اليوم | 3 رضعات · 1 حفاض | رضاعة طبيعية | الصدر الأيمن · 11 د | 6:51 ص | حفاض | مبلل ومتّسخ | 6:51 ص | رضاعة بالزجاجة | 130 مل | 6:50 ص | رضاعة طبيعية | الصدر الأيمن · 11 ث | 6:48 ص | رضاعة طبيعية | الصدر الأيسر · 41 ث | 6:47 ص | الرئيسية | السجل | الإحصائيات | الإعدادات
- ✅ open bottle entry
- ✅ cancel edit
- ✅ history + button
- ✅ add sheet: diaper type
- ✅ save diaper entry

## Statistics
- ✅ tab: stats
  - texts on stats_7: الإحصائيات | 7 أيام | 14 يومًا | 30 يومًا | 3.0 | رضعة في اليوم | 1 د | متوسط الفاصل | 3 د | متوسط الرضعة | عدد الرضعات كل يوم | التوازن بين الجهتين | الأيمن 94% · 11 د | الأيسر 6% · 41 ث | 130 | مل زجاجة/يوم | 2.0 | حفاض/يوم | 3 | إجمالي الرضعات | مشاركة ملخص (للطبيب مثلًا) | الرئيسية | السجل | الإحصائيات | الإعدادات
- ✅ stats 14 days
- ✅ stats 30 days
- ✅ stats 7 days

## Settings
- ✅ tab: settings
  - texts on settings: تجربة التنبيه الآن | تذكيرات إضافية | شرب الماء | نهارًا بين 7 ص و10 م | الفيتامين | تذكير يومي | تبديل الجهة | تنبيه أثناء الرضعة للانتقال إلى الصدر الآخر | المظهر | الوضع الليلي | تلقائي | ليلي | نهاري | التلقائي يفعّل الألوان الهادئة من 9 مساءً حتى 6 صباحًا. | نظام 24 ساعة | الأذونات والمساعدة | الإشعارات | مفعّل | المنبّه الدقيق | مفعّل | العمل في الخلفية (البطارية) | مفعّل | إذا تأخر المنبّه أو لم يرنّ، فعّلي الأذونات من هنا. | ضبط الأذونات | أمومة · الإصدار 1.4.1 · NMM Soft | الرئيسية | السجل | الإحصائيات | الإعدادات
- ✅ edit baby name
- ✅ cancel name
- ✅ alarm: fixed times
- ✅ add fixed time
- ✅ system dialog: tapped «OK»
- ✅ alarm: interval
- ✅ interval +15
- ✅ loud tone
- ✅ soft tone
- ✅ TEST ALARM
- switches visible: 4
- ✅ toggle switch 1 on
- ✅ toggle switch 2 on
- ✅ toggle switch 4 on
- ✅ toggle switch 5 on
  - texts on settings_all_on: كل ساعتين | الفيتامين | تذكير يومي | الوقت | 09:00 | تبديل الجهة | تنبيه أثناء الرضعة للانتقال إلى الصدر الآخر | بعد 10 دقائق | المظهر | الوضع الليلي | تلقائي | ليلي | نهاري | التلقائي يفعّل الألوان الهادئة من 9 مساءً حتى 6 صباحًا. | نظام 24 ساعة | الأذونات والمساعدة | الإشعارات | مفعّل | المنبّه الدقيق | مفعّل | العمل في الخلفية (البطارية) | مفعّل | إذا تأخر المنبّه أو لم يرنّ، فعّلي الأذونات من هنا. | ضبط الأذونات | أمومة · الإصدار 1.4.1 · NMM Soft | الرئيسية | السجل | الإحصائيات | الإعدادات
- ✅ night mode
  - texts on settings_night: شرب الماء | نهارًا بين 7 ص و10 م | كل ساعتين | الفيتامين | تذكير يومي | الوقت | 09:00 | تبديل الجهة | تنبيه أثناء الرضعة للانتقال إلى الصدر الآخر | بعد 10 دقائق | المظهر | الوضع الليلي | تلقائي | ليلي | نهاري | التلقائي يفعّل الألوان الهادئة من 9 مساءً حتى 6 صباحًا. | نظام 24 ساعة | الأذونات والمساعدة | الإشعارات | مفعّل | المنبّه الدقيق | مفعّل | العمل في الخلفية (البطارية) | مفعّل | إذا تأخر المنبّه أو لم يرنّ، فعّلي الأذونات من هنا. | ضبط الأذونات | أمومة · الإصدار 1.4.1 · NMM Soft | الرئيسية | السجل | الإحصائيات | الإعدادات
- ✅ tab: home (night)
  - texts on home_night: رضعة جارية | إيقاف | تبديل | إنهاء | إلغاء الرضعة دون حفظ | زجاجة | حفاض | تسجيل يدوي | ملخص اليوم | 3 | رضعات | 11 د | مدة الرضاعة | 130 | مل زجاجة | 2 | حفاضات | خذي نفسًا عميقًا… أنتِ وطفلك بخير. | الرئيسية | السجل | الإحصائيات | الإعدادات
- ✅ back to settings
- ✅ day mode
- ✅ open permissions
- ✅ close permissions

## Alarms scheduled (dumpsys)
```
com.nmmsoft.hanan
RTC_WAKEUP #5: Alarm{7916e3c type 0 origWhen 1795676352460 whenElapsed 854491 com.nmmsoft.hanan}
tag=*walarm*:com.nmmsoft.hanan.SIDE
showIntent=PendingIntent{6ea67c5: PendingIntentRecord{beff427 com.nmmsoft.hanan startActivity (allowlist: 9d193f5:+30s0ms/0/NOTIFICATION_SERVICE/NotificationManagerService)}}
operation=PendingIntent{4ca651a: PendingIntentRecord{117284b com.nmmsoft.hanan broadcastIntent}}
RTC_WAKEUP #19: Alarm{4a34b5d type 0 origWhen 1795683335817 whenElapsed 7837848 com.nmmsoft.hanan}
tag=*walarm*:com.nmmsoft.hanan.WATER
operation=PendingIntent{16b9cd2: PendingIntentRecord{105f1a3 com.nmmsoft.hanan broadcastIntent}}
RTC_WAKEUP #20: Alarm{639bca0 type 0 origWhen 1795683600000 whenElapsed 8102032 com.nmmsoft.hanan}
tag=*walarm*:com.nmmsoft.hanan.VIT
operation=PendingIntent{1ce4059: PendingIntentRecord{a42b41e com.nmmsoft.hanan broadcastIntent}}
Next wake from idle: Alarm{7916e3c type 0 origWhen 1795676352460 whenElapsed 854491 com.nmmsoft.hanan}
tag=*walarm*:com.nmmsoft.hanan.SIDE
showIntent=PendingIntent{6ea67c5: PendingIntentRecord{beff427 com.nmmsoft.hanan startActivity (allowlist: 9d193f5:+30s0ms/0/NOTIFICATION_SERVICE/NotificationManagerService)}}
operation=PendingIntent{4ca651a: PendingIntentRecord{117284b com.nmmsoft.hanan broadcastIntent}}
type=RTC_WAKEUP tag=*walarm*:com.nmmsoft.hanan.VIT
type=RTC_WAKEUP tag=*walarm*:com.nmmsoft.hanan.WATER
type=RTC_WAKEUP tag=*walarm*:com.nmmsoft.hanan.WATER
type=RTC_WAKEUP tag=*walarm*:com.nmmsoft.hanan.FEED
```

## Large font (1.3)
  - texts on big_home: رضعة جارية | إيقاف | تبديل | إنهاء | إلغاء الرضعة دون حفظ | زجاجة | حفاض | تسجيل يدوي | ملخص اليوم | 3 | رضعات | 11 د | مدة الرضاعة | 130 | مل زجاجة | 2 | حفاضات | خذي نفسًا عميقًا… أنتِ وطفلك بخير. | الرئيسية | السجل | الإحصائيات | الإعدادات
- ❌ **big: choose side**: button/label «الأيسر» not found on screen
- ❌ **big: start**: button/label «ابدأ الرضعة» not found on screen
- ✅ big: finish
- ✅ big: close sheet
- ✅ big: history
  - texts on big_history: السجل | اليوم | 3 رضعات · 2 حفاض | حفاض | مبلل | 06:52 | رضاعة طبيعية | الصدر الأيمن · 11 د | 06:51 | حفاض | مبلل ومتّسخ | 06:51 | رضاعة بالزجاجة | 130 مل | 06:50 | رضاعة طبيعية | الصدر الأيمن · 9 د | 06:49 | رضاعة طبيعية | الصدر الأيمن · 11 ث | 06:48 | رضاعة طبيعية | الصدر الأيسر · 41 ث | 06:47 | الرئيسية | السجل | الإحصائيات | الإعدادات
- ✅ big: stats
  - texts on big_stats: 7 أيام | 14 يومًا | 30 يومًا | 3.0 | رضعة في اليوم | 1 د | متوسط الفاصل | 5 د | متوسط الرضعة | عدد الرضعات كل يوم | التوازن بين الجهتين | الأيمن 96% · 20 د | الأيسر 4% · 41 ث | 130 | مل زجاجة/يوم | 2.0 | حفاض/يوم | 3 | إجمالي الرضعات | مشاركة ملخص (للطبيب مثلًا) | الرئيسية | السجل | الإحصائيات | الإعدادات
- ✅ big: settings
  - texts on big_settings: الوقت | 09:00 | تبديل الجهة | تنبيه أثناء الرضعة للانتقال إلى الصدر الآخر | بعد 10 دقائق | المظهر | الوضع الليلي | تلقائي | ليلي | نهاري | التلقائي يفعّل الألوان الهادئة من 9 مساءً حتى 6 صباحًا. | نظام 24 ساعة | الأذونات والمساعدة | الإشعارات | مفعّل | المنبّه الدقيق | مفعّل | العمل في الخلفية (البطارية) | مفعّل | إذا تأخر المنبّه أو لم يرنّ، فعّلي الأذونات من هنا. | ضبط الأذونات | أمومة · الإصدار 1.4.1 · NMM Soft | الرئيسية | السجل | الإحصائيات | الإعدادات

## Reboot survival
- alarms after reboot: 1 lines mention the app

## Summary
- screenshots: 79
- problems: 3 → notification: switch side, big: choose side, big: start
- crash buffer: EMPTY (no crashes)
