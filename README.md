# AutoJs6 Java Runtime Plugin

Independent Java single-file compiler/runtime provider for AutoJs6.

The plugin exposes `org.autojs.plugin.JVM_SOURCE`, compiles Java 8 source with the pinned ECJ 3.26.0,
converts verified class output with D8 8.13.17, and executes the resulting DEX in a disposable worker
process. The compiler and worker never run inside the AutoJs6 process.

## Compatibility

- Application ID: `io.github.supermonster003.autojs6.plugin.java.runtime`
- Minimum Android API: 24
- Required AutoJs6 version code: 5279
- JVM source protocol: 1.4
- Entry API: 4 (`AutoJsJvmEntry.run(JvmScriptContext)`; `context.args()` added)
- Current source shape: one `.java` file, entry simple name `Main`, optional package/imports

## 中文使用说明

当前版本接受严格 UTF-8 的单文件 Java 8 源码，入口简单名固定为 <code>Main</code>，并通过
<code>JvmScriptContext</code> 提供只读脚本参数、启动应用、剪贴板读写、stdout/stderr、可取消休眠和 toast 能力。返回值只接受
有界 JSON profile；源码、输出、诊断、返回值和会话时间均有硬上限。完整的方法说明、类型表、限额与
错误码见 [Java Context API 与运行边界](docs/context-api.zh-CN.md)。

Debug APK 的五阶段耗时、worker 冷启动和缓存计数可从应用私有 JSONL 读取；字段口径、ADB 命令和
Release 零导出边界见 [M7 Debug 本地观测通道](docs/local-observability.zh-CN.md)。API 24/36 的
生产 Binder 路径基线、原始 JSONL 与 M8-1 对比口径见 [M7-5 性能基线](docs/perf-baseline.md)；
worker 并行预绑定的交错测量、no-go 决策与重新开启条件见
[M8-1 评估记录](docs/worker-prewarm-evaluation.zh-CN.md)。编译产物在 API 24/25 与 API 27+ 现支持
总量 32 MiB 内、最多 4 个连续命名的 DEX；API 26 因平台只有单 buffer 内存 loader 而继续限制为
单 DEX。集合完整性、缓存迁移和 API 24/36 双加载路径证据见
[R4 多 DEX 实现与验证](docs/multidex-r4.zh-CN.md)。
用户代码现以受控方式支持 API 24 上的 `java.time` 与若干增强 Stream API；完整 Android 编译边界仍为
API 24，不支持的高版本方法会停在编译期。go 决策、精确 API 面、Release L8 约束和设备证据见
[M8-3 core library desugaring](docs/core-library-desugaring.zh-CN.md)。
编译缓存跨进程 Keystore key 原型虽在 API 24 把重启命中从 0/5 提到 5/5，却因同 UID 用户 worker 也能
使用该 alias 而未通过安全 gate；最终继续使用 compiler 进程 epoch key。攻击证据、已回退原型数据和
重新开启条件见 [M8-4 缓存持久化评估](docs/cache-persistence-m8-4.zh-CN.md)。
provider 内部的入口类名全链路已通过非 `Main` 简名与包名矩阵，以及 API 24 的真实 ECJ→D8→ART
验证；这不改变宿主当前固定 `Main` 的公开行为。测试边界与 M9-7 移交条件见
[M8-5 任意入口类名插件侧证据](docs/arbitrary-entry-class-m8-5.zh-CN.md)。
M8 发布所需的 API 24/25 `DexClassLoader`、API 26+ `InMemoryDexClassLoader`、API 34/36 R2 只读
发布时序，以及宿主↔provider Binder 分工和最近一次锁定记录，统一收录于
[M8 设备证据矩阵](docs/device-evidence.md)。
Protocol 1.2 / Entry API 3 的 Capability Set 2 已把剪贴板读写拆成两项独立授权，并在 API 24/28
provider 设备链路与 API 36 AutoJs6 生产 Binder 路径完成验证；wire、安全边界和原始记录见
[M9-1 剪贴板能力实现与证据](docs/capability-set-2-m9-1.zh-CN.md)。
Protocol 1.3 / Entry API 4 通过 <code>context.args()</code> 暴露宿主在执行配置中提供的有界、深层只读
JSON profile 快照；空参数的既有 Java 入口无需改动。类型边界、兼容策略和生产 Binder 证据见
[M9-2 脚本入参实现与证据](docs/script-arguments-m9-2.zh-CN.md)。
Protocol 1.4 保持 Entry API 4，并把 Java 运行失败投影为安全的“源码行 + 异常类名”：
<code>java.*</code>/<code>javax.*</code> 原样，其余类统一显示为 <code>UserException</code>，异常
message 与 stack 不跨边界。Wire 兼容、双侧脱敏和设备证据见
[M9-3 运行时异常类名实现与证据](docs/runtime-exception-class-m9-3.zh-CN.md)。

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
session cancellation, and `toast` is an explicitly granted host bridge capability. Protocol 1.2 /
Entry API 3 additionally exposes independently granted `clipboard.read` and `clipboard.write` via
the restoring [Capability Set 2 clipboard sample](samples/capability-set-2-clipboard.java). On
Android 10 and later, AutoJs6 must have a resumed foreground activity when either clipboard method
is called; background calls fail before the system clipboard is touched.

Protocol 1.3 / Entry API 4 additionally exposes the deterministic host execution-argument snapshot
through `context.args()`. The [script arguments sample](samples/script-args.java) reads nested maps,
lists, booleans, numbers, strings, and null without issuing a host call. Existing sources that only
implement `run(JvmScriptContext)` remain source compatible.

Protocol 1.4 keeps Entry API 4 and adds a required-for-reader class-name field to located Java
runtime diagnostics. Platform `java.*`/`javax.*` names remain visible, while every other namespace
is projected to `UserException`; exception messages and stacks never cross the provider boundary.
The intentional [runtime exception sample](samples/runtime-exception.java) demonstrates the exact
user-visible class-and-line shape.

More samples cover script arguments, controlled `java.time`/enhanced Stream desugaring,
cancellation, every supported return-value family, sanitized compiler/runtime errors, and result-size
rejection. Their expected output and current API 24/API 28 device evidence are recorded in the
[sample guide](samples/README.zh-CN.md).

## Versioning

`VERSION_NAME` follows SemVer with an optional milestone suffix (currently `0.6.0-m9`), while
`VERSION_BUILD` is a positive, monotonically increasing Android package version. Release metadata
declares engine `jvm-source`, provider ID `ecj-java`, variant `java-ecj-d8`, Protocol 1.4, and
required host version code 5279 for schema-v2 official-index generation.

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
