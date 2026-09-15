<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-java-runtime-ic-launcher" border="0" width="128" />
  </p>

  <p>Плагин компиляции и запуска исходного кода Java 8 (один файл / многофайловый пакет исходников) для AutoJs6</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-Java-Runtime?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-Java-Runtime?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/commit/7caffea0b8d593ac3f4bf722d7b12edd78d6b94b"><img alt="Created" src="https://img.shields.io/date/1787396606?color=2e7d32&label=Created"/></a>
    <br>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-Java-Runtime?color=534BAE&label=License"/></a>
  </p>
</div>

******

### Языки (Languages)

******

Текущий README.md поддерживает следующие языки:

- [简体中文 [zh-Hans]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-zh-Hans.md)
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-zh-Hant-HK.md)
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-zh-Hant-TW.md)
- [English [en]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-en.md)
- [Français [fr]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-fr.md)
- [Español [es]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-es.md)
- [日本語 [ja]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-ja.md)
- [한국어 [ko]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-ko.md)
- Русский [ru] # текущий
- [العربية [ar]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-ar.md)

******

### Введение

******

Плагин AutoJs6 Java Runtime позволяет AutoJs6 напрямую компилировать и запускать исходный код Java (`.java`) — как один файл, так и ограниченный пакет из 2–32 файлов. Плагин содержит встроенный компилятор Eclipse ECJ 3.26.0 (уровень языка Java 8) и конвертер байткода D8 8.13.23; скомпилированный результат выполняется в одноразовом процессе worker. Ни компилятор, ни скрипт никогда не выполняются внутри процесса AutoJs6.

Этот плагин и [Kotlin Runtime](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime) — родственные плагины: их можно установить одновременно, каждый обслуживает соответственно исходники Java / Kotlin, а AutoJs6 запоминает выбранный компонент компилятора отдельно для каждого языка.

******

### Возможности

******

- Предоставляет сервис компиляции/выполнения `org.autojs.plugin.JVM_SOURCE` и сервис обнаружения `org.autojs.plugin.INFO` для центра плагинов; оба защищены подписью и работают в отдельных вспомогательных процессах.
- Встроенный компилятор Eclipse ECJ 3.26.0 (уровень языка Java 8); вывод преобразуется в DEX через D8 8.13.23 перед выполнением и может занимать 1–4 последовательных файла `classesN.dex` (всего до 32 MiB; устройства API 26 остаются с одним DEX).
- Поддерживает шесть индивидуально авторизуемых мостов возможностей хоста: живой вывод консоли `console().log/error`, запуск приложений `app().launch`, прерываемый `sleep`, сообщения `toast`, а также независимо предоставляемые чтение `clipboard().getText` и запись `clipboard().setText` буфера обмена.
- Поддерживает аргументы скрипта от хоста: `context.args()` возвращает снимок аргументов конфигурации выполнения в формате JSON только для чтения, без дополнительной авторизации; существующие скрипты без аргументов работают без изменений.
- Принимает один исходный файл или канонический пакет из 2–32 файлов; простое имя входного класса может явно выбираться вызывающей стороной, по умолчанию `Main`.
- Контролируемый core library desugaring: `java.time` и отдельные расширенные методы Stream работают начиная с API 24; неподдерживаемые методы завершаются ошибкой при компиляции, а не падением на старых устройствах во время выполнения.
- Аутентифицированный кэш компиляции: повторный запуск того же исходника попадает в кэш и пропускает компиляцию; любое изменение версии тулчейна автоматически инвалидирует все прежние кэши.
- Ошибки компиляции передаются по одной в исходном порядке ECJ с безопасными именами файлов и позициями строка/столбец; каждое выполнение завершается ограниченной сводкой наблюдений: длительности фаз, результат кэша и образцы ресурсов.
- README и CHANGELOG доступны на десяти языках: упрощённый китайский, традиционный китайский (Гонконг/Тайвань), английский, французский, испанский, японский, корейский, русский и арабский.

******

### Быстрый старт

******

- **Установка** — Скачайте APK со страницы [Releases](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/releases) и установите его, либо соберите локально согласно разделу «Сборка» ниже. Внимание: плагин должен быть подписан тем же сертификатом, что и AutoJs6, а код версии хоста AutoJs6 должен быть не ниже 5281.
- **Включение** — Запуск JVM-исходников — пока экспериментальная функция AutoJs6: включите экспериментальный переключатель в хосте и явно выберите этот плагин как компонент компилятора для языка Java. Иначе при запуске появятся стабильные коды ошибок `JVM_SOURCE_EXPERIMENT_DISABLED` или `JVM_SOURCE_PROVIDER_NOT_SELECTED` соответственно; если одновременно включено несколько компонентов компилятора Java, AutoJs6 тоже сначала потребует явного выбора.
- **Запуск** — Создайте файл `.java` в редакторе AutoJs6, напишите входной класс, реализующий интерфейс `AutoJsJvmEntry`, и нажмите запуск (см. пример ниже). Физическое имя файла произвольно; простое имя входа по умолчанию — `Main`, а вызывающие скрипты могут выбрать другое ASCII-имя через API явного входа. Объявление `package` необязательно — подойдёт любой допустимый ASCII-пакет.
- **Диагностика** — При ошибке компиляции консоль показывает диагностику ECJ с безопасными именами файлов и позициями строка/столбец, несколько ошибок передаются по одной; сбои выполнения выдают стабильный код ошибки и фазу сбоя (например `SOURCE_TOO_LARGE`, `TIMEOUT`, `BUSY`), а исключения времени выполнения показывают только очищенное имя класса и строку исходника. Ожидаемый вывод каждого примера — в [руководстве по примерам](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/samples/README.zh-CN.md); полная таблица кодов ошибок — в [руководстве Java Context API](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/docs/context-api.zh-CN.md).

******

### Пример использования

******

Минимальный готовый к запуску пример, демонстрирующий четыре базовые возможности: вывод в консоль, toast, прерываемый sleep и запуск приложения:

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

`console().log/error` передаётся построчно в реальном времени во время работы скрипта; `sleep` немедленно прерывается операцией остановки. Больше примеров в каталоге [samples](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/tree/main/samples): смоук возможностей `m5-capabilities.java`, буфер обмена `capability-set-2-clipboard.java`, аргументы скрипта `script-args.java`, многофайловый пакет `multi-file-source-package/`, нестандартный вход `arbitrary-entry.java` и другие — каждый с записями проверок на устройстве.

******

### Границы возможностей

******

Ради безопасности и предсказуемости текущая версия сознательно сохраняет следующие границы:

- Однофайловый исходник Java ограничен 4 MiB; многофайловый пакет должен быть каноническим архивом из 2–32 файлов `.java`, чьи пути точно соответствуют объявлениям `package`, и делит общий лимит исходников 4 MiB. Class-файлы, JAR и DEX-входы не поддерживаются.
- Входной класс должен быть публичным конкретным классом, реализующим `AutoJsJvmEntry` (Entry API 4), с публичным конструктором без аргументов; в результате компиляции допускается ровно одна конкретная реализация.
- Уровень языка зафиксирован на Java 8 (ECJ `-source 8 -target 8`); обработка аннотаций отключена; исходник должен быть строгим UTF-8, а Unicode-экранирования `\uXXXX` запрещены везде, включая комментарии и строки.
- Лямбда-выражения пока не входят в исполняемый профиль (в заглушках компиляции API 24 нет `java.lang.invoke`) — используйте анонимные классы; никакие зависимости Maven или сторонние не разрешаются.
- Тайм-аут сессии по умолчанию 30 секунд, включая компиляцию и выполнение, с жёстким пределом 120 секунд; одновременно активна только одна сессия — вторая получает повторяемый `BUSY`.
- Процесс worker одноразовый и утилизируется после каждого выполнения; фоновые потоки не переживают возврат из `run`.

******

### Библиотеки скриптов

******

Поверхность API, доступная для компиляции и выполнения скриптов, образует точно зафиксированный белый список:

#### Доступно

- Android framework: символы компиляции берутся из class-only заглушек API 24; поведение во время выполнения по-прежнему зависит от версии ОС устройства.
- AutoJs6 JVM Entry API 4: `JvmScriptContext` — единственный поддерживаемый мост хоста (консоль / запуск приложений / sleep / toast / буфер обмена / снимок аргументов / представления отмены).
- Контролируемый core library desugaring: `java.time` (поверхность API 26) плюс расширенный трёхаргументный `Stream.iterate` и `toList`, работающие на устройствах API 24.
- Возвращаемые значения и аргументы следуют JSON-профилю: `null` / логические значения / числа / строки / `Map` со строковыми ключами / `Iterable` / массивы; возвращаемые значения ограничены 64 KiB и 64 уровнями вложенности.

#### Недоступно

- Полная библиотека настольного JDK 8 и Android API выше API 24: `Stream.ofNullable` / `takeWhile` / `dropWhile` / `mapMulti*`, полный `java.nio.file` и подобные завершаются ошибкой прямо при компиляции.
- Поддержка лямбда-выражений и ссылок на методы во время выполнения (`java.lang.invoke` нет в заглушках API 24); POJO, record и enum не могут быть возвращаемыми значениями.
- Процессоры аннотаций, транзитивные зависимости Maven и сторонние JAR.

******

### Безопасность и изоляция

******

Плагин спроектирован по принципу запрета по умолчанию; следующие ограничения действуют всегда:

- Компилятор и worker работают в отдельных процессах и никогда не входят в процесс AutoJs6; сервисы принимают вызовы только от хоста с той же подписью.
- Каждая возможность хоста авторизуется по каждому запросу, причём чтение и запись буфера обмена — два независимых разрешения; неавторизованные вызовы отклоняются двусторонними белыми списками до диспетчеризации.
- Исходники, артефакты, вывод, диагностика и возвращаемые значения имеют жёсткие пределы — превышение приводит к сбою выполнения, а не к тихому усечению; DEX проходят полную структурную валидацию и правила публикации только для чтения перед выполнением.
- Внешние ошибки несут только стабильные коды, фазы сбоя и очищенный текст; исключения времени выполнения раскрывают только имена классов `java.*`/`javax.*` либо унифицированный `UserException` — сообщения и стеки исключений никогда не покидают границу процесса.
- Кэш компиляции аутентифицирован, и его ключ никогда не покидает процесс компилятора; любое изменение версии тулчейна автоматически инвалидирует все прежние кэши.

******

### История выпусков

******

# v0.8.2

###### 2026/09/15

* `Улучшение` Подняты compileSdk и targetSdk до 37 (Android 17); поведение плагина не зависит от нового целевого уровня

# v0.8.1

###### 2026/09/13

* `Исправление` Центр плагинов может активировать новую установку через защищенный вход; метаданные соответствуют установленному пакету
* `Улучшение` Активация из хоста, метаданные, переведенная документация и сборка подписанных APK приведены к общим правилам

# v0.8.0

###### 2026/09/12

* `Улучшение` Перестроить README и историю выпусков вокруг использования, примеров и границ возможностей с согласованной генерацией десяти языков из JSON
* `Улучшение` Проверка сборки отклоняет непреднамеренные нативные зависимости и создает отчет JSON

##### Подробнее об истории выпусков см.

* [CHANGELOG-ru.md](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/app/src/main/assets/doc/CHANGELOG-ru.md)

******

### Сборка

******

Репозиторий содержит замороженные AAR протокола (`protocol/`) и собирается офлайн без чекаута AutoJs6. Рекомендуется JDK 21; Android SDK должен предоставлять platforms 24 / 26 / 30 / 34 / 36 (нижние платформы питают контролируемые заглушки компиляции). Debug-сборка:

```powershell
.\gradlew.bat :app:assembleDebug --offline
```

Release-сборка:

```powershell
.\gradlew.bat :app:assembleRelease --offline
```

Параметры сборки централизованы в `version.properties`: текущая версия 0.8.2-m9 (build 56), minSdk 24, targetSdk 37. Перед коммитом запускайте скрипты контроля `scripts/verify.ps1` / `scripts/verify.sh` (юнит-тесты Debug/Release + lint + debug-APK, полностью офлайн).

Release/debug APK должны быть подписаны тем же сертификатом, что и AutoJs6, чтобы хост их принял; локальные материалы подписи находятся в игнорируемых системой контроля версий `sign.properties` и `app/sm003.jks`.

******

### Структура ресурсов

******

```text
.readme/lang_*.json
.readme/template_readme.md
.changelog/lang_*.json
.changelog/template_changelog.md
.python/generate_markdown.py
app/src/main/res/values*/strings.xml
```

`strings.xml` локализует имя и описание плагина; README и CHANGELOG генерируются скриптом `.python/generate_markdown.py` из JSON-источников. Для изменения документации редактируйте JSON-источники, а не сгенерированный Markdown.

******

### Ссылки

******

- Документация AutoJs6: https://docs.autojs6.com
- Домашняя страница проекта AutoJs6: https://github.com/SuperMonster003/AutoJs6
- Родственный плагин Kotlin Runtime: https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime
- Проект Eclipse JDT Core (ECJ): https://github.com/eclipse-jdt/eclipse.jdt.core
- Java Context API и границы выполнения (методы/пределы/коды ошибок): https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/docs/context-api.zh-CN.md
- Каталог примеров: https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/tree/main/samples
- Дорожная карта проекта (с записями проверок по вехам): https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/ROADMAP.md
- Уведомления о сторонних компонентах: https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/THIRD_PARTY_NOTICES.md


[16 KB page alignment and build verification](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/master/docs/16kb.md)
