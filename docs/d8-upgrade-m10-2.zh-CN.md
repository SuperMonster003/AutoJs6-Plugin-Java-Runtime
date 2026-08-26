# M10-2 R8/D8 补丁位升级评估

## 决策

**Go：正式 Provider 的运行时 D8/R8 从 `8.13.17` 升级并精确固定为 `8.13.23`。**

Roadmap 原始候选是 lint 当时提示的 `8.13.22`。2026-08-27 重新读取 Google Maven 后，`8.13.23`
已经是已发布的 8.13 系列最高非 `-dev` 工件；它完整包含 `8.13.22`，并在其上增加 annotation 排序后
刷新缓存 hash 的修复。继续停在 `8.13.22` 会漏掉同一补丁线已经发布的确定性修复，因此本次选择
`8.13.23`，而不是机械照抄 Roadmap 中较早的版本号。

本次只改变 Provider APK 内、用户 class→DEX 动态编译路径使用的显式
`com.android.tools:r8` 依赖。AGP 9.3.0 自带的构建期 R8 仍是 `8.13.19`；它负责 APK shrink/L8，
不与 Provider 运行时 D8 坐标混为一个版本。本次不改变 ECJ 3.26.0、Java 8、minSdk 24、
targetSdk 36、Protocol 1.6、Entry API 4、协议 AAR、缓存 schema/domain、Provider version code/name
或 Host 代码。

## 上游版本选择

### 发布源与候选边界

Google Maven 的
[`com.android.tools:r8` metadata](https://dl.google.com/dl/android/maven2/com/android/tools/r8/maven-metadata.xml)
在测试日列出 `8.13.6`、`.17`、`.19`、`.21`、`.22` 与 `.23` 等 8.13 非开发版本。metadata 顶部
`latest`/`release` 还可能指向更高的 `-dev` 工件，不能据此把开发快照当成当前补丁线发布候选。

R8 上游保留了
[`8.13.22` tag](https://r8.googlesource.com/r8/+/refs/tags/8.13.22)，而
[`8.13` 分支](https://r8.googlesource.com/r8/+/refs/heads/8.13) 在测试日已经继续出现 `.24/.25`
版本提交；后两者尚未出现在 Google Maven metadata 中，所以本次也没有追随未发布分支头。版本选择
规则是“最高已发布非开发 8.13 补丁”，不是“任意最高上游 commit”。

### `8.13.17 → 8.13.23` 变更审计

R8 没有为每个 Maven 补丁提供一份独立、面向用户的完整 release note。以下是对
[`8.13` 官方分支日志](https://r8.googlesource.com/r8/+log/refs/heads/8.13)中两个版本点之间提交的逐项
归类；版本 bump 本身不重复列为行为变化：

| 首次进入版本 | 上游变化 | 与本项目的关系 |
| --- | --- | --- |
| `8.13.18` | 修复 API evolution 场景的 `VerifyError` 与 lock verification error，并加入复现 | 降低 D8/API 演进边界误生成或误校验风险 |
| `8.13.19` | Kotlin metadata JVM 更新至 2.2.10 | 当前用户输入仅 Java；对本路径无直接功能扩展 |
| `8.13.20` | ApplicationWriter 的 annotation 排序改为确定性行为 | 有利于相同输入的 DEX 稳定性 |
| `8.13.21` | 修复带 subclass 的 enum unboxing 问题 | 主要属于 shrinker 优化；Provider D8 使用 debug/no-minify 路径 |
| `8.13.22` | 减少 `NoClassInitializerCycles` 中不必要的 worklist 添加 | 主要是分析正确性/效率维护 |
| `8.13.23` | annotation 排序后刷新缓存 hash | 与 `.20` 的确定性修复配套，避免排序后保留陈旧 hash |

这组差异没有要求改变本项目的 D8 命令 profile、DEX 校验器、loader、desugared-library 配置或
协议。选择 go 的依据不是“版本更新总是更好”，而是差异范围可审计、缓存身份已覆盖版本、完整构建与
最低 API 到当前 target API 的 ART 矩阵均通过。

### 依赖工件身份

| 工件 | 字节数 | SHA-256 |
| --- | ---: | --- |
| `com.android.tools:r8:8.13.17` JAR | 18,279,079 | `d31fd0dc751d48740009cdd9a485126acb1d0d14c59b9f05579479940f4adf74` |
| `com.android.tools:r8:8.13.23` JAR | 18,273,519 | `e3cdcb003d9beca956209ad6b9e9df31f26b732bfaed9c7c8674e903ca9f3b81` |

`dependencyInsight` 对 `debugRuntimeClasspath` 的解析结果只有
`com.android.tools:r8:8.13.23`。同一次构建环境报告单独显示
`com.android.tools:r8:8.13.19 [agp-bundled]`；这证明两个 R8 身份没有因 Gradle 冲突解析而被静默
合并。

## 实现范围

版本坐标在两个独立位置同步更新：

1. `gradle/libs.versions.toml` 的版本目录把运行时 R8/D8 固定为 `8.13.23`；
2. `app/build.gradle.kts` 的 `verifyPinnedInputs` 保留独立字面量
   `com.android.tools:r8:8.13.23`，避免只改 alias 或 catalog 后绕过固定输入检查。

`D8JavaCompiler` 本身没有改动。它继续使用：

- `CompilationMode.DEBUG`、`OutputMode.DexIndexed` 与 `minApi=24`；
- 受控 Android API 24 编译桩、D8-only API 30 桩与 Entry API library；
- `desugar_jdk_libs_nio` / configuration `2.1.5`；
- API 24/25 的 CLI 兼容入口，以及 API 26+ 的 `D8Command` builder 入口；
- DEX 集连续命名、文件/集合摘要、结构、descriptor 与 loader 前重验。

因此设备结果可以归因到依赖补丁，而不是同时修改命令参数或放宽校验所得的假阳性。

## 缓存自动失效验证

正式缓存身份原本就同时绑定：

1. provenance 中的直接 `d8Version` 字段；
2. `JvmToolchainFingerprint.compute(...)` 中参与摘要的 D8 版本；
3. 包含该 toolchain fingerprint 的 `CompilationArtifactCacheKey` 规范序列化。

本次新增精确回归测试
`d8PatchUpgradeFrom81317To81323InvalidatesToolchainAndCompilationCache`，以相同语言、ECJ、runtime
library fingerprint 和其余 provenance 构造旧/新身份，并分别断言：

- `8.13.17` 与 `8.13.23` 的 toolchain fingerprint 不相等；
- 两个 `CompilationArtifactCacheKey` 不相等。

测试 fixture 的当前 D8 身份改为 `8.13.23` 后，schema-3 golden key 从
`4d1dd549bc02a20e975541c88cafacf0d97845f7eb5a60d9aac8ae9a59b4cf3d` 变为
`b01de504fdf13eedf4c26430e2aafc053533185c244613aa134bf411e4269ac4`。这不是 cache schema 迁移：
既有 schema/domain 已把工具链版本设计为输入，旧条目会自然 MISS，不能以新 key 被认证或物化，并由
既有容量/清理策略回收。为补丁升级额外 bump schema 反而会重复表达同一个身份变化。

## 本地构建门禁

在依赖已预热后，以离线、无常驻 daemon、强制重跑模式执行：

```powershell
.\gradlew.bat `
  :app:testDebugUnitTest `
  :app:testReleaseUnitTest `
  :app:lintDebug `
  :app:assembleDebug `
  :app:assembleDebugAndroidTest `
  :app:assembleRelease `
  --offline --no-daemon --rerun-tasks --console=plain
```

结果为 `BUILD SUCCESSFUL`，151 个 task 全部实际执行：

| Gate | 结果 |
| --- | --- |
| Debug JVM tests | 44 suites，164/164，0 failure/error/skipped |
| Release JVM tests | 44 suites，164/164，0 failure/error/skipped |
| `verifyPinnedInputs` | 通过，catalog 与独立字面量均解析为 `8.13.23` |
| Debug lint | 0 error，28 warning |
| Debug / AndroidTest APK | 构建通过 |
| Release R8/L8 / APK | 构建通过；仅保留既有 ECJ service/missing-type warning |

候选 APK 身份：

| 工件 | 字节数 | SHA-256 |
| --- | ---: | --- |
| Provider Debug APK | 30,678,176 | `ddca4282e3b0a15e4813adf034978d18550ba699ee450c4d127f0ae908a8d110` |
| Provider AndroidTest APK | 987,422 | `e449935ee877f97983039a9213559833e03504446ab6b3ba7bb09c097859cd13` |
| Provider Release APK | 27,129,674 | `e95b2d9bb63df814753f50259f07fe21123542756b1a78a5d1fb2d31f486dac7` |

## 设备矩阵

### 设备身份

| API | ABI | 设备 / fingerprint | 覆盖分支 |
| ---: | --- | --- | --- |
| 24 | x86 | `emulator-5558`；`google/sdk_google_phone_x86/generic_x86:7.0/NYC/6696031:userdebug/dev-keys` | CLI D8 + 私有只读 `DexClassLoader` |
| 25 | x86 | `emulator-5556`；`google/sdk_google_phone_x86/generic_x86:7.1.1/NYC/6695155:userdebug/test-keys` | CLI D8 + 私有只读 `DexClassLoader` |
| 26 | x86_64 | `emulator-5560`；`Android/sdk_phone_x86_64/generic_x86_64:8.0.0/OSR1.180418.004/4931640:userdebug/test-keys` | builder D8 + API 26 独有的单 buffer `InMemoryDexClassLoader` |
| 28 | x86_64 | `emulator-5560`；`Android/sdk_phone_x86_64/generic_x86_64:9/PSR1.180720.012/4923214:userdebug/test-keys` | builder D8 + `InMemoryDexClassLoader` |
| 35 | arm64-v8a | Xiaomi `liuqin`；`Xiaomi/liuqin/liuqin:15/AQ3A.241006.001/OS2.0.9.0.VMYCNXM:user/release-keys` | 当前真机 ART + builder D8 |
| 36 | x86_64 | `emulator-5562`；`google/sdk_gphone64_x86_64/emu64xa:16/BE4B.251210.005/14574095:userdebug/dev-keys` | 精确 R2 只读发布时序 |

API 26 与 API 28 是先后冷引导的两个不同 AVD，会话间均已关机；表中的 `emulator-5560` 只是复用的
ADB 端口，环境身份以 API、ABI 与完整 fingerprint 为准。

### 执行结果

| 测试 | API 24 | API 25 | API 26 | API 28 | API 35 | API 36 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `JavaProviderPipelineInstrumentedTest` | 2/2 | 2/2 | 2/2 | 2/2 | 2/2 | — |
| `JavaSampleLibraryInstrumentedTest` | 11/11 | 11/11 | 11/11 | 11/11 | 11/11 | — |
| `JavaMultiDexPipelineInstrumentedTest` | 1/1 | — | 不适用 | 1/1 | 1/1 | — |
| `JvmSourceR2ReadOnlyDexInstrumentationTest` | — | — | — | — | 不适用 | 1/1 |

`JavaProviderPipelineInstrumentedTest` 的两项 JUnit 测试内部覆盖默认入口和三个非 `Main` 布局，并完整
经过 ECJ→JAR→D8→DEX 严格验证→目标 loader→ART 入口调用。样例库 11 项覆盖任意入口、脚本参数、
旧返回值、剪贴板授权与默认拒绝、运行时异常投影、结果上限、取消、`java.time`/增强 Stream、编译诊断
和不支持 Stream API 的负向编译边界。精确 API 26 runner 时间分别为 pipeline 4.196 s、样例 6.061 s。

R4 fixture 生成超过 65,535 method-reference 的 2-DEX 集合；API 24、28、35 均完成直接加载和一次性
worker Binder 执行。runner 报告 API 24 为 26.406 s、API 28 为 44.183 s、API 35 为 19.234 s；
这些墙钟时间只用于识别执行，不作为跨设备性能比较。

### API 36 R2 精确证据

API 36 冷引导 AVD 安装了摘要锁定、同 signer 的 Host 与候选 Provider。R2 用例先断言
`BuildConfig.D8_VERSION == "8.13.23"`，再使用正式 `D8JavaCompiler` 生成真实 DEX，并观测：

- 写入顺序严格为 `CREATE_EMPTY → OPEN_STREAM → MARK_READ_ONLY → WRITE_CONTENT → SYNC`；
- 首个内容字节前 user/group/other 写位均已移除；
- 受控失败在只读后的 16-byte 非零部分写、sync 前发生，partial artifact 没有发布；
- 失败 DEX、request root 与测试自有 base directory 均清理成功；
- 924-byte DEX SHA-256 为
  `8ce6f825fd5915dc3d6c7d44d60e24b2e848d22658900baf7f90ac4af5debb17`。

安装输入中的 AutoJs6 x86_64 Debug APK 为 43,216,225 bytes，SHA-256
`5a58bfe586ea21bea3930dae2d88894be094562729816c2bf8f87a894baa9a04`；Provider Debug APK 使用上表
`ddca4282...8d110`。测试在设备内重新读取两者 `sourceDir`、计算摘要并校验当前 signer 集相同。
`sourceTreeSha256=5464320ba72440bb821652fd945b62f2d79aeb2abd5e0d304a4c0ceae6fd5523`
来自实际 APK 构建后、补写纯文档前的 219 个 tracked file 工作树清单：每行是小写文件 SHA-256、两个
空格、正斜杠路径和 LF。该时间点冻结了本轮所有 production/test/Gradle 输入，后续 Markdown 与原始
observation 文件不参与已生成 APK 的字节身份。

canonical run 为 `a1956e81-4503-42a6-b088-00606ff31479`。未重新序列化的 observation 保存在
[`2026-08-27-api36-m10-2-r2-readonly.jsonl`](device-evidence-data/2026-08-27-api36-m10-2-r2-readonly.jsonl)，
文件 1,375 bytes，SHA-256
`ba8a384a12d5f309a8d4020d5c1085d8a001f41065922d71ea3c19598d36ffbf`。

API 35 上曾直接调用同一 R2 类；它在任何写入或编译前按设计拒绝，因为该证据生产器只接受精确 API
34/36。该次前置条件拒绝不计为候选失败，也没有通过放宽白名单伪造证据；随后使用 API 36 完成上述
正式 `1/1` 记录。

### 异常设备隔离

Sony G8441 / API 28 真机在 instrumentation 进程尚为 `<pre-initialized>` 时，偶发从
`ADB-JDWP Connec` 线程进入 `libart.so` SIGSEGV。候选 `8.13.23` 与重新安装的基线 `8.13.17` 都能
复现，崩溃发生在 Provider 初始化和 D8 调用之前，因此不能归因于本次补丁。该设备结果没有计入 go
矩阵；改用冷引导 API 28 x86_64 AVD 后，pipeline 2/2、样例 11/11、多 DEX 1/1 均稳定通过。

API 31 真机上原有用户 Provider 从安装目标中排除；测试前后 package path 均保留，没有安装、覆盖或
卸载。

## 兼容性与安全结论

- D8 版本是 capability/toolchain identity 的既有组成部分，Host 可见 compiler version 与缓存身份
  同步变化，不存在“实际升级但仍广告旧版本”的分叉。
- DEX 结构校验、descriptor 白名单、R2 发布、loader 选择、compiler/worker 隔离与 Host 调用授权没有
  放宽；设备矩阵走的仍是 production 实现。
- 补丁升级不增加 Java 语言级别或平台 API。M10-1 的 ECJ 3.26.0 / Java 8 决策保持有效。
- 协议 wire、Entry API、AAR 与 Host discovery 均未变化，因此不需要协议版本、最低 Host 或
  Provider version code bump；正式发版前仍按 release checklist 单独递增 build version。
- cache schema/domain 不变是有意结果：版本字段已经提供严格失效，旧 DEX 不会跨工具链身份复用。

## 清理与收口

取证前确认 API 24/25/26/28/35/36 目标环境均没有本项目 Provider/Test package。结束后：

- API 24、25、26、28、35、36 均卸载本轮 Provider 与 AndroidTest package，并复核 package path 为空；
- API 36 额外卸载本轮为 signer/APK 摘要交叉验证安装的 Host；
- 本轮启动的 API 26、28 与 API 36 AVD 已正常关闭；API 24/25 既有 AVD 未因本轮被关闭；
- API 31 用户设备的原有 Provider package path 仍存在；
- Sony API 28 上为 A/B 归因安装的 `8.13.17` 基线也已卸载；
- 原 AutoJs6 工作区及其已有未提交改动没有被修改。

正式依赖和文档合入后，M10-2 可以关闭为完成升级。未来若继续升级，仍需同时更新 catalog 与
`verifyPinnedInputs` 字面量，重新锁定 JAR/APK 摘要，运行 Debug/Release 全量门禁，并至少覆盖 API
24/25、精确 API 26、一个 API 27+ loader、当前 target ART、R4 多 DEX、缓存版本失效与精确 API
34/36 R2 时序。
