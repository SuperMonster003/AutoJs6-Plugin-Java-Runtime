# AutoJs6 Java Runtime Plugin

Independent Java single-file compiler/runtime provider for AutoJs6.

The plugin exposes `org.autojs.plugin.JVM_SOURCE`, compiles Java 8 source with the pinned ECJ 3.26.0,
converts verified class output with D8 8.13.17, and executes the resulting DEX in a disposable worker
process. The compiler and worker never run inside the AutoJs6 process.

## Compatibility

- Application ID: `io.github.supermonster003.autojs6.plugin.java.runtime`
- Minimum Android API: 24
- Required AutoJs6 version code: 5276
- JVM source protocol: 1.1
- Entry API: 2 (`AutoJsJvmEntry.run(JvmScriptContext)`)
- Current source shape: one `.java` file, entry simple name `Main`, optional package/imports

## 中文使用说明

当前版本接受严格 UTF-8 的单文件 Java 8 源码，入口简单名固定为 <code>Main</code>，并通过
<code>JvmScriptContext</code> 提供启动应用、stdout/stderr、可取消休眠和 toast 能力。返回值只接受
有界 JSON profile；源码、输出、诊断、返回值和会话时间均有硬上限。完整的方法说明、类型表、限额与
错误码见 [Java Context API 与运行边界](docs/context-api.zh-CN.md)。

Debug APK 的五阶段耗时、worker 冷启动和缓存计数可从应用私有 JSONL 读取；字段口径、ADB 命令和
Release 零导出边界见 [M7 Debug 本地观测通道](docs/local-observability.zh-CN.md)。API 24/36 的
生产 Binder 路径基线、原始 JSONL 与 M8-1 对比口径见 [M7-5 性能基线](docs/perf-baseline.md)；
worker 并行预绑定的交错测量、no-go 决策与重新开启条件见
[M8-1 评估记录](docs/worker-prewarm-evaluation.zh-CN.md)。

## Source example

The ready-to-run [M5 capability sample](samples/m5-capabilities.java) demonstrates the complete first
capability set. The file name may be arbitrary, but the entry simple name remains `Main` in
Protocol 1:

```java
import org.autojs.plugin.jvmsource.api.AutoJsJvmEntry;
import org.autojs.plugin.jvmsource.api.JvmScriptContext;

public final class Main implements AutoJsJvmEntry {
    @Override
    public Object run(JvmScriptContext context) throws Exception {
        context.console().log("M5 Java log");
        context.toast("M5 Java toast");
        context.sleep(500L);
        context.console().error("M5 Java error after sleep");
        return 5;
    }
}
```

`console().log/error` is streamed line by line while the worker is running. `sleep` is interrupted by
session cancellation, and `toast` is an explicitly granted host bridge capability.

More samples cover cancellation, every supported return-value family, sanitized compiler errors,
and result-size rejection. Their expected output and API 24/API 37 device evidence are recorded in
the [sample guide](samples/README.zh-CN.md).

## Versioning

`VERSION_NAME` follows SemVer with an optional milestone suffix (currently `0.3.0-m5`), while
`VERSION_BUILD` is a positive, monotonically increasing Android package version. Release metadata
declares engine `jvm-source`, provider ID `ecj-java`, variant `java-ecj-d8`, Protocol 1.1, and
required host version code 5276 for schema-v2 official-index generation.

## Local build

The repository uses frozen AARs from `protocol/` and can build without a sibling AutoJs6 checkout.
Release/debug APKs must be signed with the same certificate as AutoJs6. Local signing material is
expected at the ignored files `sign.properties` and `app/sm003.jks`.

Run the pre-push verification gate from PowerShell or a POSIX shell. Both scripts may be invoked
from any working directory, force Gradle offline mode, and run both Debug/Release unit-test variants,
Debug lint, and Debug APK assembly:

```powershell
.\scripts\verify.ps1
```

```bash
./scripts/verify.sh
```

Additional Gradle options are forwarded, for example `./scripts/verify.sh --no-daemon`. To also
produce the signed Release APK, run the full release gate directly:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:testReleaseUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease --offline
```

Output:

```text
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/app-release.apk
```

Install with `adb install -r`. The old in-tree provider uses a different application ID and must not
remain enabled at the same time, otherwise AutoJs6 correctly sees multiple eligible providers and
requires an explicit selection.

## Discovery

The APK provides two signature-protected services:

- `org.autojs.plugin.INFO` for Plugin Center metadata;
- `org.autojs.plugin.JVM_SOURCE` for compilation/execution sessions.

AutoJs6 discovers both by action. It does not depend on this plugin's package name.
