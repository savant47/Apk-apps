# خطة المراجعة الشاملة ومحاكاة التوافق مع نظام أندرويد 7 ورسيفر BeoutQ TV Box

## مراجعة وتدقيق كامل لكود التطبيق ومحاكاته مع بيئة Android 7.1.2 (API 25)

يهدف هذا الفحص الهندسي المعماري الدقيق إلى مراجعة وتدقيق جميع أجزاء ومراحل تطور الكود المصدري للتطبيق، ومقارنتها بالخصائص التقنية والقيود العتادية لرسيفر **BeoutQ TV Box** بنظام **Android 7.1.2 (Nougat - API 25)** بمعالج Amlogic S905B / S912، ومعالج الرسوميات Mali-450، وذاكرة عشوائية 2GB RAM، ومحرك WebView الأصلي المصنعي (Chromium 53–58).

---

## القرارات المؤكدة وخيارات المعالجة (Confirmed Decisions)

> [!IMPORTANT]
> تم تأكيد الخيارات التالية بناءً على متطلبات التشغيل على الرسيفر:
> - **إصدار المتصفح المستهدف:** مشغل WebView المصنعي الافتراضي لأندرويد 7 (Stock Chromium 53–58) دون افتراض تحديث خارجي.
> - **الأولوية الأساسية:** إصلاح شامل للتوافقية واستقرار الأداء العالي واستهلاك الذاكرة (2GB RAM).
> - **استراتيجية تشغيل الفيديو:** الاعتماد على الـ WebView المطور داخلياً مع ترقيعات الـ TLS وحقن الـ Polyfills البرمجية لدعم المشغلات الحديثة.

---

## 1. نتائج التدقيق الشامل ومحاكاة بيئة أندرويد 7 (Android 7 Issues & Simulation)

من خلال مراجعة الكود ومحاكاة سلوك النظام والـ WebView على Android 7، تم حصر **5 مشاكل رئيسية** قد تعيق التشغيل السلس على رسيفر BeoutQ:

```
┌───────────────────────────────────────────────────────────────────────────────┐
│                    BeoutQ Android 7 Compatibility Matrix                      │
├───────────────────────────────┬────────────────────────┬──────────────────────┤
│ Component                     │ Issue on Android 7     │ Impact Level         │
├───────────────────────────────┼────────────────────────┼──────────────────────┤
│ 1. WebView JS Engine (v53-58) │ Invalid CSS :contains  │ 🔴 HIGH (Crash/Halt) │
│ 2. TLS/SSL Certificates       │ Let's Encrypt X1 Hand. │ 🔴 HIGH (Blocked Net)│
│ 3. Legacy Subframe Intercept  │ Missed String Overload │ 🟡 MEDIUM (Ad Leak)  │
│ 4. GPU & Mali-450 Fillrate    │ Overdraw during FS Vid │ 🟡 MEDIUM (Stutter)  │
│ 5. Memory Pressure (2GB RAM)  │ Coil & Web Cache Bloat │ 🟡 MEDIUM (OOM Risk) │
└───────────────────────────────┴────────────────────────┴──────────────────────┘
```

---

### المشكلة 1: أخطاء محرك الجافاسكريبت والـ DOM القديم (Chromium 53–58)
* **التشخيص:**
  1. احتواء دالة `window.playNextEpisode` في `TvWebClients.kt` على محددات CSS غير قياسية مثل `a:contains(...)` و `a[href*="/watch/"]:contains(...)`. محدد `:contains` خاص بـ jQuery وليس CSS قياسي، مما يسبب `DOMException: SyntaxError` صامت في المتصفحات القديمة ويوقف تنفيذ سكريبت الانتقال التلقائي للحلقات.
  2. استخدام دوال حديثة في بعض المواقع دون وجود Polyfills مدمجة لـ (Element.prototype.closest, Object.assign, Promise, CustomEvent).
* **الحل البرمجي:**
  * تنظيف وتصحيح كافة محددات الـ DOM لتعتمد على محددات CSS القياسية بالكامل مع دوال فحص النصوص `findByText` الأصلية.
  * حقن حزمة Polyfills خفيفة الوزن في مرحلة `onPageStarted` لدعم خصائص المتصفح الحديثة على Chromium 53–58.

---

### المشكلة 2: شهادات الأمان وحظر اتصالات Let's Encrypt (ISRG Root X1)
* **التشخيص:**
  * شهادة الجذر `DST Root CA X3` انتهت صلاحيتها في سبتمبر 2021. أندرويد 7 لا يثق تلقائياً بشهادة `ISRG Root X1` الحديثة التي تعتمد عليها أغلب سيرفرات ومواقع المشاهدة (إيجي ديد، فاصل، عرب سيد، أكوام، إلخ).
  * اتصالات OkHttp / Retrofit لجلب ملفات الترجمة والتحديثات قد تفشل بـ `SSLHandshakeException`.
* **الحل البرمجي:**
  * معالجة شاملة في `onReceivedSslError` للسماح بسيرفرات البث الموثوقة بأمان.
  * تزويد OkHttp بـ Custom TrustManager وموزع شهادات يدعم شهادة `ISRG Root X1` ومجموعات تشفير TLS 1.2 الحديثة على Android 7.

---

### المشكلة 3: تسرب الإعلانات في الإطارات الفرعية القديمة (Legacy Subframe Interception)
* **التشخيص:**
  * على بعض إصدارات أندرويد 7، تقوم بعض إطارات الـ `iframe` القديمة باستدعاء الدالة المهملة `shouldInterceptRequest(view, url: String)` بدلاً من `shouldInterceptRequest(view, request: WebResourceRequest)`.
* **الحل البرمجي:**
  * تطبيق فحص مانع الإعلانات وحظر النوافذ الخبيثة في كلا نسختي `shouldInterceptRequest` لضمان عدم تسرب أي إعلان مهما كان إصدار المحرك.

---

### المشكلة 4: ضغط عتاد الرسوميات Mali-450 عند تشغيل الفيديو بملء الشاشة
* **التشخيص:**
  * رسيفر BeoutQ يحتوي على معالج رسوميات Mali-450 ذي نطاق ترددي محدود للذاكرة. عند عرض الفيديو ملء الشاشة مع بقاء طبقات الـ Compose في الخلفية مفعلة، يحدث تقطيع في الإطارات (Frame Drops).
* **الحل البرمجي:**
  * فصل وإخفاء واجهات الـ Compose تماماً عند دخول وضع ملء الشاشة (`onFullscreenChanged(true)`).
  * ضبط وضع تسريع العتاد `LAYER_TYPE_HARDWARE` على `fullscreenContainer` وتفريغ طبقات التراكب لضمان سلاسة 60fps أثناء تشغيل الفيديو.

---

### المشكلة 5: إدارة الذاكرة العشوائية ورسائل OOM على أجهزة 2GB RAM
* **التشخيص:**
  * التطبيق يعمل على جهاز بذاكرة 2GB يشاركها النظام والـ WebView وJetpack Compose.
* **الحل البرمجي:**
  * تفعيل `android:largeHeap="true"` (مفعل حالياً) مع إضافة حدود صارمة للذاكرة التخزينية المؤقتة في `TvCinemaApplication.kt`.
  * إضافة دالة `onTrimMemory` لتفريغ كاش الصور وكاش الـ WebView تلقائياً عند انخفاض الذاكرة.

---

## 2. الهيكل المعماري لنظام التوافق مع أندرويد 7 (System Architecture)

```
┌────────────────────────────────────────────────────────────────────────┐
│                   Android 7 / BeoutQ Engine Bridge                     │
├────────────────────────────────────────────────────────────────────────┤
│                                                                        │
│   ┌───────────────────────┐        ┌───────────────────────────────┐   │
│   │   TvWebChromeClient   │        │      TvWebViewClient          │   │
│   │   - Fullscreen Layer  │        │   - Dual-overload AdBlock    │   │
│   │   - Hardware Video VP │        │   - SSL ISRG Root X1 Bypass   │   │
│   │   - Safe Dialogs      │        │   - Modern Polyfill Injection │   │
│   └───────────┬───────────┘        └───────────────┬───────────────┘   │
│               │                                    │                   │
│               ▼                                    ▼                   │
│   ┌────────────────────────────────────────────────────────────────┐   │
│   │           Stock Android 7 WebView (Chromium 53-58)             │   │
│   │   - Safe JS Polyfills (closest, assign, safe events)           │   │
│   │   - Valid CSS Selectors (Pure Standard, No :contains syntax)  │   │
│   │   - Center-Click Protection & Safe Window.open Stub           │   │
│   └────────────────────────────────────────────────────────────────┘   │
│                                                                        │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 3. خطوات التنفيذ التفصيلية (Implementation Plan)

### الخطوة 1: تنظيف وتصحيح محددات الجافاسكريبت وحقن Polyfills التوافقية
- إزالة كافة صيغ `:contains(...)` واستبدالها بالبحث النصي القياسي السليم.
- حقن مكتبات ترقيعية مصغرة (Polyfills) عند `onPageStarted` لضمان عمل كافة مشغلات الفيديو الحديثة على Chromium 53–58.

### الخطوة 2: تحديث معالجات الـ WebView لدعم استدعاءات Android 7 القديمة
- تطبيق `shouldInterceptRequest(view, url: String)` إلى جانب `shouldInterceptRequest(view, request: WebResourceRequest)`.
- ترقية نظام معالجة أخطاء الـ SSL والشهادات القديمة.

### الخطوة 3: تحسين استهلاك الذاكرة وسلاسة Mali-450
- ضبط تفريغ الذاكرة على مستوى `TvCinemaApplication.kt` عبر `onTrimMemory`.
- تحسين طبقات العرض ملء الشاشة في `TvWebChromeClient` لتقليل استهلاك معالج الرسوميات.

### الخطوة 4: التحقق والاختبارات
- إجراء اختبارات الوحدة (Robolectric) للتأكد من خلو محددات الـ JS من أخطاء الـ Syntax ومطابقتها للمتصفحات القديمة.
- تجميع التطبيق (`compile_applet`) والتأكد من نجاح البناء بنسبة 100%.
