<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <picture>
      <source media="(prefers-color-scheme: dark)" srcset="https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/app/src/main/res/mipmap-night/ic_launcher.png?raw=true" />
      <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-java-runtime-ic-launcher" border="0" width="128" />
    </picture>
  </p>

  <p>Java 8 source (single-file / multi-file source package) compiler and runner plugin for AutoJs6</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-Java-Runtime?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-Java-Runtime?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/commit/7caffea0b8d593ac3f4bf722d7b12edd78d6b94b"><img alt="Created" src="https://img.shields.io/date/1787396606?color=2e7d32&label=Created"/></a>
    <br>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-Java-Runtime?color=534BAE&label=License"/></a>
  </p>
</div>

******

### Languages

******

The current README.md supports the following languages:

- [简体中文 [zh-Hans]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-zh-Hans.md)
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-zh-Hant-HK.md)
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-zh-Hant-TW.md)
- English [en] # current
- [Français [fr]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-fr.md)
- [Español [es]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-es.md)
- [日本語 [ja]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-ja.md)
- [한국어 [ko]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-ko.md)
- [Русский [ru]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-ru.md)
- [العربية [ar]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-ar.md)

******

### Introduction

******

The AutoJs6 Java Runtime plugin lets AutoJs6 compile and run Java source code (`.java`) directly, accepting a single file or a bounded source package of 2–32 files. It embeds the Eclipse compiler ECJ 3.26.0 (language level Java 8) and the D8 8.13.23 bytecode converter, and executes the compiled output in a disposable worker process; neither the compiler nor the script ever runs inside the AutoJs6 process.

This plugin and [Kotlin Runtime](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime) are sister plugins: they can be installed side by side, each serving Java / Kotlin source respectively, and AutoJs6 remembers the selected compiler component per language.

******

### Features

******

- Provides the `org.autojs.plugin.JVM_SOURCE` compile/execute service and the `org.autojs.plugin.INFO` Plugin Center discovery service, both signature-protected and running in separate auxiliary processes.
- Embeds the Eclipse compiler ECJ 3.26.0 (language level Java 8); output is converted to DEX by D8 8.13.23 before execution, spanning 1–4 contiguous `classesN.dex` files (32 MiB total; API 26 devices stay single-DEX).
- Supports six individually authorized host capability bridges: live console output `console().log/error`, app launch `app().launch`, interruptible `sleep`, `toast` messages, plus independently granted clipboard read `clipboard().getText` and write `clipboard().setText`.
- Supports host script arguments: `context.args()` returns a read-only JSON snapshot of the execution-config arguments without extra authorization; existing argument-free scripts keep working unchanged.
- Accepts a single source file or a canonical source package of 2–32 files; the entry class simple name can be selected explicitly by the caller and defaults to `Main`.
- Controlled core library desugaring: `java.time` and selected enhanced Stream methods work from API 24 onward; unsupported methods fail at compile time instead of crashing older devices at runtime.
- Authenticated compilation cache: rerunning identical source hits the cache and skips compilation; any toolchain version change automatically invalidates all previous caches.
- Compilation errors are streamed one by one in ECJ source order with safe file names and line/column positions; every run ends with a bounded observation summary of phase timings, cache outcome, and resource samples.
- README and CHANGELOG are available in ten languages: Simplified Chinese, Traditional Chinese (HK/TW), English, French, Spanish, Japanese, Korean, Russian, and Arabic.

******

### Quick Start

******

- **Install** — Download the APK from [Releases](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/releases) and install it, or build locally as described in the Build section below. Note: the plugin must be signed with the same certificate as AutoJs6, and the AutoJs6 host version code must be at least 5281.
- **Enable** — Running JVM source is currently an experimental AutoJs6 feature: enable the experiment switch in the host, then explicitly select this plugin as the compiler component for the Java language. If either step is missing, running reports the stable error codes `JVM_SOURCE_EXPERIMENT_DISABLED` or `JVM_SOURCE_PROVIDER_NOT_SELECTED` respectively; if several Java compiler components are enabled at once, AutoJs6 also asks for an explicit selection first.
- **Run** — Create a `.java` file in the AutoJs6 editor, write an entry class implementing the `AutoJsJvmEntry` interface, and tap run (see the Usage Example below). The physical file name is arbitrary; the entry simple name defaults to `Main`, and script callers may select another ASCII simple name through the explicit-entry API. The `package` declaration is optional — any legal ASCII package works.
- **Troubleshoot** — On compilation failure the console shows ECJ diagnostics with safe file names and line/column positions, and multiple errors are streamed one by one; runtime failures surface a stable error code plus failure phase (such as `SOURCE_TOO_LARGE`, `TIMEOUT`, `BUSY`), and runtime exceptions show only a sanitized class name with the source line. Expected outputs for every sample live in the [sample guide](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/samples/README.zh-CN.md); the full error-code table is in the [Java Context API guide](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/docs/context-api.zh-CN.md).

******

### Usage Example

******

A minimal ready-to-run example demonstrating four basic capabilities: console output, toast, interruptible sleep, and app launch:

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

`console().log/error` streams line by line while the script is running; `sleep` is promptly interrupted by a stop action. More examples live in the [samples](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/tree/main/samples) directory — capability smoke test `m5-capabilities.java`, clipboard `capability-set-2-clipboard.java`, script arguments `script-args.java`, multi-file source package `multi-file-source-package/`, non-default entry `arbitrary-entry.java`, and more — each with recorded device evidence.

******

### Boundaries

******

To keep behavior safe and predictable, the current version deliberately maintains the following boundaries:

- Single-file Java source is capped at 4 MiB; a multi-file source package must be a canonical archive of 2–32 `.java` files whose paths match their `package` declarations exactly, sharing the same 4 MiB total source cap. Class files, JARs, and DEX inputs are not supported.
- The entry class must be a public concrete class implementing `AutoJsJvmEntry` (Entry API 4) with a public no-arg constructor; the compiled output must contain exactly one concrete implementation.
- The language level is fixed at Java 8 (ECJ `-source 8 -target 8`); annotation processing is disabled; source must be strict UTF-8, and `\uXXXX` Unicode escapes are forbidden everywhere, including comments and strings.
- Lambda expressions are not part of the runnable profile yet (the API 24 compile stubs lack `java.lang.invoke`) — use anonymous classes; no Maven or third-party dependencies are resolved.
- The session timeout defaults to 30 seconds covering compilation and execution, with a 120-second hard cap; only one session may be active at a time — a second session receives a retryable `BUSY`.
- The worker process is single-use and retires after every execution; background threads do not survive `run` returning.

******

### Script Runtime

******

The API surface available for script compilation and execution forms an exactly pinned allowlist:

#### Available

- Android framework: compile-time symbols come from API 24 class-only stubs; runtime behavior still depends on the device OS version.
- AutoJs6 JVM Entry API 4: `JvmScriptContext` is the only supported host bridge (console / app launch / sleep / toast / clipboard / argument snapshot / cancellation views).
- Controlled core library desugaring: `java.time` (the API 26 surface) plus the three-argument enhanced `Stream.iterate` and `toList`, runnable on API 24 devices.
- Return values and arguments use a JSON profile: `null` / booleans / numbers / strings / String-key `Map` / `Iterable` / arrays; return values are capped at 64 KiB with 64 nesting levels.

#### Unavailable

- The full desktop JDK 8 library and Android APIs above API 24: `Stream.ofNullable` / `takeWhile` / `dropWhile` / `mapMulti*`, the full `java.nio.file`, and similar APIs fail directly at compile time.
- Runtime support for lambda expressions and method references (`java.lang.invoke` is not in the API 24 stubs); POJOs, records, and enums cannot be used as return values.
- Annotation processors, transitive Maven dependencies, and third-party JARs.

******

### Security & Isolation

******

The plugin is designed deny-by-default; the following restrictions are always in effect:

- The compiler and worker run in separate processes and never enter the AutoJs6 process; services accept same-signature host calls only.
- Every host capability is authorized per request, with clipboard read and write as two independent grants; unauthorized calls are rejected by dual-side allowlists before dispatch.
- Source, artifacts, output, diagnostics, and return values all have hard ceilings — exceeding one fails the run instead of silently truncating; DEX files pass full structural validation and read-only publication rules before execution.
- Outward errors carry only stable error codes, failure phases, and sanitized text; runtime exceptions surface only `java.*`/`javax.*` class names or the unified `UserException` — exception messages and stacks never leave the process boundary.
- The compilation cache is authenticated, and its key never leaves the compiler process; any toolchain version change automatically invalidates all previous caches.

******

### Release History

******

# v0.8.4

###### 2026/10/04

* `Improvement` Application and Plugin Center icons use maintainer-supplied artwork, preserving colors and proportions with transparent padding and unified optical sizing to keep the complete silhouette visible, with Icon Studio adjustment and regeneration
* `Improvement` Plugin Center icons use the sizes, positions, light and dark artwork, and circular backgrounds adjusted in Icon Studio, retaining reproducible sources and parameters

# v0.8.3

###### 2026/09/19

* `Fix` Concurrent resource cleanup and worker exit can no longer finalize the same session twice or deliver a terminal callback before execution observations are ready, preventing intermittent host timeouts
* `Fix` SDK XML v4 parsing warnings with AGP 9.1 and APK native alignment checks incorrectly triggered by JVM unit-test assembly tasks, using shared build plugins 1.8.3

# v0.8.2

###### 2026/09/15

* `Improvement` Raise compileSdk and targetSdk to 37 (Android 17); the plugin's behavior does not depend on the new target

##### For more release history, see

* [CHANGELOG-en.md](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/app/src/main/assets/doc/CHANGELOG-en.md)

******

### Build

******

The repository ships frozen protocol AARs (`protocol/`) and builds offline without an AutoJs6 checkout. JDK 21 is recommended; the Android SDK must provide platforms 24 / 26 / 30 / 34 / 36 (the lower platforms feed the controlled compile stubs). Debug build:

```powershell
.\gradlew.bat :app:assembleDebug --offline
```

Release build:

```powershell
.\gradlew.bat :app:assembleRelease --offline
```

Build parameters are centralized in `version.properties`: current version 0.8.4-m9 (build 64), minSdk 24, targetSdk 37. Before committing, run the one-shot gate scripts `scripts/verify.ps1` / `scripts/verify.sh` (Debug/Release unit tests + lint + debug APK, fully offline).

Release/debug APKs must be signed with the same certificate as AutoJs6 to be accepted by the host; local signing material lives in the version-control-ignored `sign.properties` and `app/sm003.jks`.

******

### Resource Layout

******

```text
.readme/lang_*.json
.readme/template_readme.md
.changelog/lang_*.json
.changelog/template_changelog.md
.python/generate_markdown.py
app/src/main/res/values*/strings.xml
```

`strings.xml` localizes the plugin name and description; README and CHANGELOG are generated by `.python/generate_markdown.py` from the JSON sources. To change the docs, edit the JSON sources rather than the generated Markdown.

The [original artwork](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.python/icons/java-runtime.svg) was supplied by the maintainer. `.icons/recipe.json` records later adjustments; use `python .python/generate_icon_studio.py --check` to verify the recipe and resources.

******

### Links

******

- AutoJs6 documentation: https://docs.autojs6.com
- AutoJs6 project home: https://github.com/SuperMonster003/AutoJs6
- Sister plugin Kotlin Runtime: https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime
- Eclipse JDT Core (ECJ) project: https://github.com/eclipse-jdt/eclipse.jdt.core
- Java Context API & runtime boundaries (methods/limits/error codes): https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/docs/context-api.zh-CN.md
- Samples directory: https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/tree/main/samples
- Project roadmap (with per-milestone verification records): https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/ROADMAP.md
- Third-party notices: https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/THIRD_PARTY_NOTICES.md


[16 KB page alignment and build verification](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/master/docs/16kb.md)
