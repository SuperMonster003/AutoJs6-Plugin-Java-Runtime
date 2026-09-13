<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-java-runtime-ic-launcher" border="0" width="128" />
  </p>

  <p>إضافة لترجمة وتشغيل شيفرة Java 8 (ملف واحد / حزمة مصادر متعددة الملفات) لتطبيق AutoJs6</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-Java-Runtime?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-Java-Runtime?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/commit/7caffea0b8d593ac3f4bf722d7b12edd78d6b94b"><img alt="Created" src="https://img.shields.io/date/1787396606?color=2e7d32&label=Created"/></a>
    <br>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-Java-Runtime?color=534BAE&label=License"/></a>
  </p>
</div>

******

### اللغات (Languages)

******

يدعم README.md الحالي اللغات التالية:

- [简体中文 [zh-Hans]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-zh-Hans.md)
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-zh-Hant-HK.md)
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-zh-Hant-TW.md)
- [English [en]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-en.md)
- [Français [fr]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-fr.md)
- [Español [es]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-es.md)
- [日本語 [ja]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-ja.md)
- [한국어 [ko]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-ko.md)
- [Русский [ru]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-ru.md)
- العربية [ar] # الحالي

******

### مقدمة

******

تتيح إضافة AutoJs6 Java Runtime لتطبيق AutoJs6 ترجمة وتشغيل شيفرة Java المصدرية (`.java`) مباشرة، سواء كملف واحد أو كحزمة مصادر محدودة من 2 إلى 32 ملفًا. تتضمن الإضافة مترجم Eclipse ECJ 3.26.0 (مستوى اللغة Java 8) ومحوّل البايت كود D8 8.13.23، ويُنفَّذ الناتج المترجم في عملية worker أحادية الاستخدام؛ فلا المترجم ولا السكربت يعملان أبدًا داخل عملية AutoJs6.

هذه الإضافة و[Kotlin Runtime](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime) إضافتان شقيقتان: يمكن تثبيتهما معًا لتخدم كل منهما شيفرة Java / Kotlin على التوالي، ويتذكر AutoJs6 مكوّن المترجم المختار لكل لغة على حدة.

******

### الميزات

******

- توفّر خدمة الترجمة/التنفيذ `org.autojs.plugin.JVM_SOURCE` وخدمة الاكتشاف `org.autojs.plugin.INFO` لمركز الإضافات، وكلتاهما محمية بالتوقيع وتعملان في عمليات مساعدة منفصلة.
- تتضمن مترجم Eclipse ECJ 3.26.0 (مستوى اللغة Java 8)؛ يحوَّل الناتج إلى DEX عبر D8 8.13.23 قبل التنفيذ، في 1 إلى 4 ملفات `classesN.dex` متتابعة (بمجموع 32 MiB؛ وتبقى أجهزة API 26 على ملف DEX واحد).
- تدعم ستة جسور لقدرات المضيف بتفويض فردي: إخراج الطرفية الحي `console().log/error`، وتشغيل التطبيقات `app().launch`، و`sleep` القابل للمقاطعة، ورسائل `toast`، إضافة إلى قراءة الحافظة `clipboard().getText` وكتابتها `clipboard().setText` بتفويضين مستقلين.
- تدعم وسائط السكربت من المضيف: تعيد `context.args()` لقطة JSON للقراءة فقط من وسائط إعدادات التنفيذ دون تفويض إضافي؛ والسكربتات الحالية بلا وسائط تعمل دون تعديل.
- تقبل ملفًا مصدريًا واحدًا أو حزمة قانونية من 2 إلى 32 ملفًا؛ ويمكن للمستدعي اختيار الاسم البسيط لصنف الدخول صراحةً، والافتراضي `Main`.
- إزالة سكر core library خاضعة للتحكم: تعمل `java.time` وبعض توابع Stream المحسّنة بدءًا من API 24؛ وتفشل التوابع غير المدعومة عند الترجمة بدل انهيار الأجهزة القديمة وقت التشغيل.
- ذاكرة ترجمة مخبأة موثّقة: إعادة تشغيل المصدر نفسه تصيب الذاكرة وتتخطى الترجمة؛ وأي تغيير في إصدار سلسلة الأدوات يبطل تلقائيًا كل الذواكر السابقة.
- تُبثّ أخطاء الترجمة واحدًا واحدًا بترتيب مصدر ECJ مع أسماء ملفات آمنة ومواضع السطر/العمود؛ وينتهي كل تنفيذ بملخص رصد محدود: أزمنة المراحل ونتيجة الذاكرة المخبأة وعينات الموارد.
- يتوفر README و CHANGELOG بعشر لغات: الصينية المبسطة والصينية التقليدية (هونغ كونغ/تايوان) والإنجليزية والفرنسية والإسبانية واليابانية والكورية والروسية والعربية.

******

### البدء السريع

******

- **التثبيت** — نزّل ملف APK من [Releases](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/releases) وثبّته، أو ابنِ محليًا كما في قسم البناء أدناه. تنبيه: يجب توقيع الإضافة بنفس شهادة AutoJs6، ويجب ألا يقل رمز إصدار مضيف AutoJs6 عن 5281.
- **التفعيل** — تشغيل شيفرة JVM حاليًا ميزة تجريبية في AutoJs6: فعّل مفتاح التجربة في المضيف، ثم اختر هذه الإضافة صراحةً كمكوّن المترجم للغة Java. إن لم تفعل، يعرض التشغيل رمزي الخطأ الثابتين `JVM_SOURCE_EXPERIMENT_DISABLED` أو `JVM_SOURCE_PROVIDER_NOT_SELECTED` على التوالي؛ وإذا فُعّلت عدة مكوّنات مترجم Java معًا، يطلب AutoJs6 أيضًا اختيارًا صريحًا أولًا.
- **التشغيل** — أنشئ ملف `.java` في محرر AutoJs6، واكتب صنف دخول ينفّذ الواجهة `AutoJsJvmEntry` ثم اضغط تشغيل (انظر مثال الاستخدام أدناه). اسم الملف الفعلي حر؛ والاسم البسيط للدخول افتراضيًا `Main`، ويمكن لمستدعي السكربت اختيار اسم بسيط ASCII آخر عبر واجهة الدخول الصريح. إعلان `package` اختياري — وتصلح أي حزمة ASCII قانونية.
- **استكشاف الأخطاء** — عند فشل الترجمة تعرض الطرفية تشخيصات ECJ بأسماء ملفات آمنة ومواضع السطر/العمود، وتُبثّ الأخطاء المتعددة واحدًا واحدًا؛ أما إخفاقات التشغيل فتُظهر رمز خطأ ثابتًا ومرحلة الفشل (مثل `SOURCE_TOO_LARGE` و`TIMEOUT` و`BUSY`)، وتعرض استثناءات وقت التشغيل اسم صنف منقّى وسطر المصدر فقط. المخرجات المتوقعة لكل مثال في [دليل الأمثلة](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/samples/README.zh-CN.md)؛ والجدول الكامل لرموز الخطأ في [دليل Java Context API](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/docs/context-api.zh-CN.md).

******

### مثال الاستخدام

******

مثال أدنى جاهز للتشغيل يوضح أربع قدرات أساسية: إخراج الطرفية و toast و sleep القابل للمقاطعة وتشغيل التطبيقات:

```java
import org.autojs.plugin.jvmsource.api.AutoJsJvmEntry;
import org.autojs.plugin.jvmsource.api.JvmScriptContext;

public final class Main implements AutoJsJvmEntry {
    @Override
    public Object run(JvmScriptContext context) throws Exception {
        context.console().log("Hello from Java 8");
        context.toast("AutoJs6 Java Runtime");
        context.sleep(500L);
        boolean launched = context.app().launch("org.autojs.autojs6");
        return launched;
    }
}
```

يُبثّ `console().log/error` سطرًا بسطر أثناء تشغيل السكربت؛ ويُقاطَع `sleep` فورًا بإجراء الإيقاف. مزيد من الأمثلة في مجلد [samples](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/tree/main/samples): اختبار القدرات `m5-capabilities.java`، والحافظة `capability-set-2-clipboard.java`، ووسائط السكربت `script-args.java`، وحزمة المصادر متعددة الملفات `multi-file-source-package/`، والدخول غير الافتراضي `arbitrary-entry.java` وغيرها — وكلها بسجلات تحقق على أجهزة حقيقية.

******

### الحدود

******

حفاظًا على سلوك آمن وقابل للتنبؤ، يحافظ الإصدار الحالي عمدًا على الحدود التالية:

- شيفرة Java أحادية الملف بحد أقصى 4 MiB؛ ويجب أن تكون حزمة المصادر متعددة الملفات أرشيفًا قانونيًا من 2 إلى 32 ملف `.java` تتطابق مساراتها تمامًا مع إعلانات `package`، وتتشارك حد المصدر الكلي نفسه 4 MiB. لا تُدعم ملفات class و JAR ومدخلات DEX.
- يجب أن يكون صنف الدخول صنفًا عامًا ملموسًا ينفّذ `AutoJsJvmEntry` (Entry API 4) وله مُنشئ عام بلا وسائط؛ ويجب أن يحتوي ناتج الترجمة على تنفيذ ملموس واحد بالضبط.
- مستوى اللغة مثبت على Java 8 (ECJ `-source 8 -target 8`)؛ ومعالجة التوصيفات معطلة؛ ويجب أن يكون المصدر UTF-8 صارمًا، وتُحظر تهريبات Unicode بصيغة `\uXXXX` في كل مكان بما في ذلك التعليقات والسلاسل.
- تعابير lambda ليست بعد ضمن الملف الشخصي القابل للتشغيل (أنصال الترجمة لواجهة API 24 تفتقر إلى `java.lang.invoke`) — استخدم الأصناف المجهولة؛ ولا تُحل أي تبعيات Maven أو خارجية.
- مهلة الجلسة افتراضيًا 30 ثانية تشمل الترجمة والتنفيذ، بسقف صارم 120 ثانية؛ ولا تنشط سوى جلسة واحدة في كل مرة — وتتلقى الجلسة الثانية `BUSY` قابلة لإعادة المحاولة.
- عملية worker أحادية الاستخدام وتُصفّى بعد كل تنفيذ؛ ولا تبقى خيوط الخلفية بعد عودة `run`.

******

### مكتبات السكربت

******

يشكّل سطح API المتاح لترجمة السكربتات وتنفيذها قائمة سماح مثبتة بدقة:

#### متاح

- إطار عمل Android: رموز وقت الترجمة تأتي من أنصال class-only لواجهة API 24؛ ويظل سلوك وقت التشغيل معتمدًا على إصدار نظام الجهاز.
- AutoJs6 JVM Entry API 4: يمثل `JvmScriptContext` جسر المضيف الوحيد المدعوم (الطرفية / تشغيل التطبيقات / sleep / toast / الحافظة / لقطة الوسائط / عروض الإلغاء).
- إزالة سكر core library خاضعة للتحكم: `java.time` (سطح API 26) مع `Stream.iterate` الثلاثي الوسائط و`toList` المحسّنين، قابلة للتشغيل على أجهزة API 24.
- تتبع القيم المعادة والوسائط ملفًا شخصيًا لـ JSON: `null` / قيم منطقية / أعداد / سلاسل / `Map` بمفاتيح String / `Iterable` / مصفوفات؛ وتُحد القيم المعادة بـ 64 KiB وبعمق تداخل 64.

#### غير متاح

- مكتبة JDK 8 المكتبية الكاملة وواجهات Android الأعلى من API 24: تفشل `Stream.ofNullable` / `takeWhile` / `dropWhile` / `mapMulti*` و`java.nio.file` الكاملة وأمثالها مباشرة عند الترجمة.
- دعم وقت التشغيل لتعابير lambda ومراجع التوابع (`java.lang.invoke` ليست في أنصال API 24)؛ ولا يمكن إعادة POJO أو record أو enum كقيم معادة.
- معالجات التوصيفات وتبعيات Maven المتعدية وملفات JAR الخارجية.

******

### الأمان والعزل

******

صُممت الإضافة على مبدأ الرفض الافتراضي؛ والقيود التالية سارية دائمًا:

- يعمل المترجم و worker في عمليتين منفصلتين ولا يدخلان أبدًا عملية AutoJs6؛ ولا تقبل الخدمات سوى استدعاءات مضيف بنفس التوقيع.
- تُفوَّض كل قدرة مضيف لكل طلب على حدة، وقراءة الحافظة وكتابتها تفويضان مستقلان؛ وتُرفض الاستدعاءات غير المفوضة عبر قوائم سماح من الجانبين قبل الإرسال.
- للمصدر والمخرجات والتشخيصات والقيم المعادة جميعًا سقوف صارمة — وتجاوز أي منها يُفشل التنفيذ بدل الاقتطاع الصامت؛ وتجتاز ملفات DEX تحققًا هيكليًا كاملًا وقواعد نشر للقراءة فقط قبل التنفيذ.
- لا تحمل الأخطاء الخارجية سوى رموز ثابتة ومراحل فشل ونص منقّى؛ ولا تكشف استثناءات وقت التشغيل سوى أسماء أصناف `java.*`/`javax.*` أو `UserException` الموحّد — ولا تعبر رسائل الاستثناءات ومكدساتها حدود العملية أبدًا.
- ذاكرة الترجمة المخبأة موثّقة ولا يغادر مفتاحها عملية المترجم أبدًا؛ وأي تغيير في إصدار سلسلة الأدوات يبطل تلقائيًا كل الذواكر السابقة.

******

### سجل الإصدارات

******

# v0.8.1

###### 2026/09/13

* `إصلاح` يمكن لمركز الإضافات تنشيط المزود المثبت حديثا عبر مدخل محمي; تطابق البيانات المعروضة الحزمة المثبتة
* `تحسين` توحيد تنشيط المضيف وبيانات الإضافة والوثائق المترجمة وتجميع إصدارات APK الموقعة وفق قواعد الإضافات المشتركة

# v0.8.0

###### 2026/09/12

* `تحسين` إعادة تنظيم README وسجل الإصدارات حول الاستخدام والأمثلة وحدود القدرات, مع توليد متسق لعشر لغات من مصادر JSON
* `تحسين` التحقق أثناء البناء لمنع إدخال تبعيات أصلية غير مقصودة, مع تقرير JSON

# v0.8.0-m9

###### 2026/08/27

* `تلميح` تبقى مهلة الجلسة عند 30 ثانية افتراضيًا / سقف صارم 120 ثانية، ويبقى التزامن عند جلسة نشطة واحدة (تتلقى الجلسة الثانية `BUSY` قابلة لإعادة المحاولة) - وخلُص تقييما التخفيف كلاهما إلى عدم المضي حاليًا
* `تلميح` تقييم ترقية ECJ: لا تستطيع الإصدارات الأحدث (3.42/3.46) العمل على Android، فيبقى خط الإصدار مثبتًا على ECJ 3.26.0 و Java 8
* `ميزة` إضافة حزم المصادر متعددة الملفات (Protocol 1.6): يمكن لطلب واحد تقديم أرشيف قانوني من 2 إلى 32 ملف `.java` يجب أن تتطابق مساراتها تمامًا مع إعلانات `package`؛ ويُرفض اجتياز المسارات والمدخلات المضغوطة والروابط الرمزية والمكررات جميعًا
* `ميزة` فتح أسماء أصناف الدخول الاختيارية من طرف إلى طرف: يمكن للمستدعين اختيار اسم بسيط ASCII للدخول صراحةً (يبقى الافتراضي `Main`)، مع مثال الدخول غير الافتراضي الجديد `arbitrary-entry.java`
* `تحسين` تأكيد بقاء دعم Kotlin/JVM لدى الإضافة الشقيقة Kotlin Runtime؛ يتشارك المستودعان البروتوكول المجمّد واختبارات المطابقة دون اعتمادية وقت تشغيل
* `اعتمادية` ترقية D8/R8 وقت التشغيل من 8.13.17 إلى 8.13.23؛ وتُبطل مفاتيح ذاكرة الترجمة المخبأة تلقائيًا مع إصدار سلسلة الأدوات

##### لمزيد من سجل الإصدارات، انظر

* [CHANGELOG-ar.md](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/app/src/main/assets/doc/CHANGELOG-ar.md)

******

### البناء

******

يتضمن المستودع ملفات AAR مجمّدة للبروتوكول (`protocol/`) ويُبنى دون اتصال ودون الحاجة إلى نسخة من AutoJs6. يوصى بـ JDK 21؛ ويجب أن يوفر Android SDK المنصات 24 / 26 / 30 / 34 / 36 (تغذي المنصات الأدنى أنصال الترجمة الخاضعة للتحكم). بناء Debug:

```powershell
.\gradlew.bat :app:assembleDebug --offline
```

بناء Release:

```powershell
.\gradlew.bat :app:assembleRelease --offline
```

تتركز معاملات البناء في `version.properties`: الإصدار الحالي 0.8.1-m9 (build 52)، minSdk 24، targetSdk 36. قبل الالتزام شغّل سكربتي البوابة `scripts/verify.ps1` / `scripts/verify.sh` (اختبارات وحدة Debug/Release + lint + APK بنسخة debug، وكل ذلك دون اتصال).

يجب توقيع APK بنسختي release/debug بنفس شهادة AutoJs6 ليقبلها المضيف؛ وتوجد مواد التوقيع المحلية في `sign.properties` و `app/sm003.jks` المتجاهلين من نظام التحكم بالإصدارات.

******

### بنية الموارد

******

```text
.readme/lang_*.json
.readme/template_readme.md
.changelog/lang_*.json
.changelog/template_changelog.md
.python/generate_markdown.py
app/src/main/res/values*/strings.xml
```

يوفّر `strings.xml` توطين اسم الإضافة ووصفها؛ ويُولَّد README و CHANGELOG بواسطة `.python/generate_markdown.py` من مصادر JSON. لتعديل الوثائق حرّر مصادر JSON لا ملفات Markdown المولّدة.

******

### روابط

******

- وثائق AutoJs6: https://docs.autojs6.com
- الصفحة الرئيسية لمشروع AutoJs6: https://github.com/SuperMonster003/AutoJs6
- الإضافة الشقيقة Kotlin Runtime: https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime
- مشروع Eclipse JDT Core (ECJ): https://github.com/eclipse-jdt/eclipse.jdt.core
- Java Context API وحدود التشغيل (التوابع/السقوف/رموز الخطأ): https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/docs/context-api.zh-CN.md
- مجلد الأمثلة: https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/tree/main/samples
- خارطة طريق المشروع (مع سجلات التحقق لكل مرحلة): https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/ROADMAP.md
- إشعارات الأطراف الثالثة: https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/THIRD_PARTY_NOTICES.md


[16 KB page alignment and build verification](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/master/docs/16kb.md)
