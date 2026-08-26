# AutoJs6 Java Runtime Plugin

Independent Java single-file and bounded multi-file compiler/runtime provider for AutoJs6.

The plugin exposes `org.autojs.plugin.JVM_SOURCE`, compiles Java 8 source with the pinned ECJ 3.26.0,
converts verified class output with D8 8.13.17, and executes the resulting DEX in a disposable worker
process. The compiler and worker never run inside the AutoJs6 process.

## Compatibility

- Application ID: `io.github.supermonster003.autojs6.plugin.java.runtime`
- Minimum Android API: 24
- Required AutoJs6 version code: 5281
- JVM source protocol: 1.6
- Entry API: 4 (`AutoJsJvmEntry.run(JvmScriptContext)`; `context.args()` added)
- Current source shape: one `.java` file or a canonical 2–32-file Java package; caller-selected entry simple name (`Main` by default)

## 中文使用说明

当前版本接受严格 UTF-8 的单文件 Java 8 源码，或含 2–32 个 compilation unit 的有界规范源码包；Host
调用方可以显式选择合法入口简名，既有入口继续默认使用 <code>Main</code>。脚本通过
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
验证；M9-7 又在 Host 公开显式入口、共享校验、快照、请求、诊断和 production Binder 链路完成放开，
同时保留 `Main` 默认。插件侧先行证据见
[M8-5 任意入口类名插件侧证据](docs/arbitrary-entry-class-m8-5.zh-CN.md)，最终交付见
[M9-7 任意入口类名端到端证据](docs/arbitrary-entry-class-m9-7.zh-CN.md)。
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
Protocol 1.5 保持 Entry API 4，并在成功、错误或取消终态的资源清理完成后返回有界 observation：
编译/执行/清理耗时、当次缓存结果、冷暖启动和最多六个 identity-free 数值资源样本。Host 会独立验证并
输出固定摘要；Release 路径不依赖 Debug 私有 JSONL，也不会导出累计缓存计数。Wire 上限、隐私边界、
MISS/HIT 生产证据见 [M9-4 观测数据协议化](docs/observation-protocol-m9-4.zh-CN.md)。
Protocol 1.6 保持单 source FD 与 Entry API 4，并允许该 FD 承载确定性、STORED-only 的多文件 Java
源码归档。Host 与 Provider 独立验证 manifest、ASCII 相对路径、文件数/总量、CRC/SHA、package/path
一致性和零链接属性；路径穿越、重复项、压缩/ZIP64 与符号链接全部 fail closed。完整 wire、归档 profile、
缓存迁移与 API 25 production Binder 证据见
[M9-5 多文件源码包](docs/multifile-source-package-m9-5.zh-CN.md)。
M9-6 评估后继续保留 Host 30 秒正式总截止时间与 120 秒协议安全上限，不增加滑动 keep-alive：当前
非前台 bound-service 链和进程心跳无法证明非可信计算仍有进展。若未来确需长任务，应以用户前台授权、
会话专属前台服务、独立三层租约及不可续约绝对上限实现单独模式；约束与重开门槛见
[M9-6 超时上限评估](docs/timeout-limit-m9-6.zh-CN.md)。
M9-7 不增加 wire 字段或改变 Entry API；Host 通过
<code>runJvmSourceWithEntryExplicit</code> / <code>runJvmSourcePackageWithEntryExplicit</code>
接收入口简名，并在绑定 provider 前使用共享策略生成规范源码名与全限定入口。合法名称 profile、默认兼容、
非 <code>Main</code> 样例与 production Binder 证据见
[M9-7 任意入口类名端到端证据](docs/arbitrary-entry-class-m9-7.zh-CN.md)。
M9-8 评估后继续保留每个 Provider 进程一个活动会话和第二会话立即 <code>BUSY</code>：单槽同时是
compiler hard-kill、一次性 worker、cache lane、callback lane 与观测归属的隔离边界。当前 Host 每次脚本
都会创建独立 host 实例，不能在实例内安全伪装成全局队列；交互入口只显示稳定的稍后重试提示，不自动重放
可能已有外部副作用的 Java 入口。完整连锁分析、短期 BUSY 指引和未来有界协调器门槛见
[M9-8 会话并发模型评估](docs/session-concurrency-m9-8.zh-CN.md)。
M10-1 对 Roadmap 候选 ECJ 3.42.0、测试日最新 3.46.0 与边界版本 3.33.0 完成 ART spike 后判定
直接升级 no-go：API 24–28 缺少新版 ECJ 直接调用的 <code>InputStream.readAllBytes()</code>，而
3.42/3.46 即使在 API 35 仍因 Android 不提供 <code>Runtime.Version</code> 而无法进入编译。隔离实验
同时证明 3.33 在 API 35 可将 Java 11/17 源码完整送过 D8 与 ART，但它不满足 minSdk 24，不能发布。
正式线继续固定 ECJ 3.26.0 与 Java 8；版本矩阵、工件摘要、失败根因和未来 Android-targeted fork 的
重开条件见 [M10-1 ECJ 升级评估](docs/ecj-upgrade-m10-1.zh-CN.md)。

## Source example

The ready-to-run [M5 capability sample](samples/m5-capabilities.java) demonstrates the complete first
capability set. The physical file name may be arbitrary; this existing sample uses the backward-compatible
`Main` default:

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

Protocol 1.5 keeps Entry API 4 and attaches one bounded, required-for-reader observation to every
negotiated terminal after compiler/worker cleanup. AutoJs6 independently validates and formats
phase timings, the per-request cache outcome, start profiles, and up to six identity-free resource
samples; cumulative cache telemetry remains confined to the debug-only private exporter.

Protocol 1.6 keeps the single source FD and Entry API 4 while allowing that FD to carry one
deterministic, STORED-only Java source archive. AutoJs6 and the provider independently validate its
manifest, canonical relative paths, count/byte claims, hashes, package layout, and no-link metadata
before the provider materializes any source path. Source packages contain 2–32 `.java` files and
select the caller-supplied entry; single-file requests remain supported.

M9-7 keeps Protocol 1.6 and Entry API 4. AutoJs6 callers can select an ASCII Java entry simple name;
`Main` remains the default for existing launches. The
[arbitrary-entry sample](samples/arbitrary-entry.java) deliberately uses a physical file name that differs
from its public `demo.entries.ScriptEntry` class and must be launched through the explicit-entry Host API.

More samples cover arbitrary entry selection, script arguments, controlled `java.time`/enhanced Stream desugaring,
cancellation, every supported return-value family, sanitized compiler/runtime errors, and result-size
rejection. Their expected output and current API 24/API 28 device evidence are recorded in the
[sample guide](samples/README.zh-CN.md).

## Versioning

`VERSION_NAME` follows SemVer with an optional milestone suffix (currently `0.8.0-m9`), while
`VERSION_BUILD` is a positive, monotonically increasing Android package version. Release metadata
declares engine `jvm-source`, provider ID `ecj-java`, variant `java-ecj-d8`, Protocol 1.6, and
required host version code 5281 for schema-v2 official-index generation.

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
