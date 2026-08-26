# M8 设备证据矩阵

本文是 M8 的发布阻断清单。它把“单元测试推导的平台分支”和“设备上实际走到的运行时分支”分开，
并为最近一次执行锁定源码、APK、设备指纹、测试入口和原始记录。兼容性声明不能只引用某一个测试类；
下列发布清单必须全部为 `[x]`，才能发布 M8 构建。

- 最近执行日期：2026-08-25（Asia/Shanghai）
- Provider application ID：`io.github.supermonster003.autojs6.plugin.java.runtime`
- 测试 runner：
  `io.github.supermonster003.autojs6.plugin.java.runtime.test/androidx.test.runner.AndroidJUnitRunner`
- 本轮结论：API 24/25 的私有 `DexClassLoader`、API 26 的单 buffer
  `InMemoryDexClassLoader`、API 36 的多 buffer `InMemoryDexClassLoader`、API 36 的 R2 只读发布时序，
  以及 API 24/36 的宿主生产 Binder 路径均已在设备上通过。

## 证据层级与仓库分工

四类证据回答不同问题，不能互相替代：

| 代号 | 证据 | 回答的问题 | 权威归属 |
| --- | --- | --- | --- |
| **P** | Provider pipeline instrumentation | ECJ、JAR、D8、DEX 校验、指定 loader 与 ART 入口执行是否在目标 API 真正成功 | 本仓 |
| **W** | Provider 内部 compiler/worker Binder instrumentation | DEX 集是否经内部 AIDL/FD 边界交给一次性 `:worker`，且 worker 进程隔离、终态退出 | 本仓 |
| **H** | AutoJs6 生产 Activity → provider Binder | 宿主是否完成插件发现、签名/版本选择、协议握手、生产 Binder 调用及结果回传 | **宿主仓** |
| **R** | R2 真实文件系统审计 | API 34/36 且 targetSdk 34+ 时，落盘 DEX 是否在首个内容字节前只读，失败发布是否清理 | 本仓 |

`P` 不经过宿主 Binder，不能证明宿主发现和选择逻辑；`W` 只证明 provider 内部 Binder；`H` 的成功观测
不能单独证明实际 class loader 类型或首字节前的文件 mode；`R` 也不证明 ART 执行。发布结论必须组合
这些层级。

宿主侧规范测试位于 AutoJs6 仓库的
`app/src/androidTest/java/org/autojs/autojs/core/plugin/jvm/JvmSourceR1EndToEndInstrumentationTest.kt`。
凡是变更宿主的 provider discovery、签名验证、显式 JVM source 路由、`RemoteJvmSourceHost`、协议 AAR
或宿主进程隔离，必须在宿主仓重新生成 `H` 证据；本仓的 `P/W/R` 不得代替它。本文件保留一份当次
联调镜像，便于把 provider 发布候选与宿主证据关联起来，但不转移代码所有权。

## M8 发布阻断清单

| 状态 | Gate | 最低要求 | 2026-08-25 最近证据 |
| --- | --- | --- | --- |
| [x] | `P24` | API 24 实际选择私有 `DexClassLoader`，完成 `Main` 和非 `Main` 入口的 ECJ→D8→ART | `JavaProviderPipelineInstrumentedTest` 2/2 |
| [x] | `P25` | API 25 实际选择私有 `DexClassLoader`，完成同一 pipeline | `JavaProviderPipelineInstrumentedTest` 2/2 |
| [x] | `P26` | API 26 实际选择单 buffer `InMemoryDexClassLoader`；多 DEX 仍 fail closed | 设备 2/2；两项 API 26 单测随离线 gate 执行 |
| [x] | `P/W-R4` | API 24 和一个 API 27+ 代表设备分别执行真实 2-DEX direct 与内部 worker Binder 路径 | API 24、36 各 1/1，均为 2 DEX / 129 classes |
| [x] | `P-M8-3` | API 24 和高 API 代表设备执行 desugaring、取消、诊断、结果上限与返回值样例 | API 24、36 各 6/6 |
| [x] | `R34/36` | API 34 或 36、targetSdk 34+ 的 R2 精确时序、非零部分写失败与清理均被观测 | API 36 1/1，canonical run `351eecc5-3951-4c94-adcf-2244d3fb1d56` |
| [x] | `H24/H34+` | 当前 provider APK 在 API 24 和 API 34+ 各经一次宿主生产 Binder 路径完成 | API 24、36 各新增 1 条完整 schema-v1 观测 |
| [x] | `G` | `scripts/verify.ps1 :app:assembleRelease` 与 AndroidTest APK 离线构建通过 | Debug/Release 各 140/140 单测，lint 与三类 APK 构建通过 |

`P26` 的 fail-closed 判据由
`DexRuntimePolicyTest.api26RetainsItsFrozenInMemoryKindButOnlyHasASoundSingleDexConstructor` 与
`DexArtifactSetValidatorTest.api26RejectsMultipleDexFilesBecauseItsInMemoryLoaderHasNoArrayConstructor`
固定。API 26 只有单 buffer 构造器；多 buffer 支持从 API 27 开始，所以“API 26+ 使用内存 loader”
不等于“API 26 支持多 DEX”。

## 本轮设备与二进制身份

### 设备

ADB serial 只标识本次会话，发布记录以 API、ABI 与完整 build fingerprint 共同识别环境。

| API | Serial | ABI | Build fingerprint | 应命中的 loader 分支 |
| ---: | --- | --- | --- | --- |
| 24 | `emulator-5558` | `x86` | `google/sdk_google_phone_x86/generic_x86:7.0/NYC/6696031:userdebug/dev-keys` | `PRIVATE_DEX_CLASS_LOADER` |
| 25 | `emulator-5556` | `x86` | `google/sdk_google_phone_x86/generic_x86:7.1.1/NYC/6695155:userdebug/test-keys` | `PRIVATE_DEX_CLASS_LOADER` |
| 26 | `emulator-5560` | `x86_64` | `Android/sdk_phone_x86_64/generic_x86_64:8.0.0/OSR1.180418.004/4931640:userdebug/test-keys` | `IN_MEMORY_DEX_CLASS_LOADER`（单 buffer） |
| 36 | `emulator-5562` | `x86_64` | `google/sdk_gphone64_x86_64/emu64xa:16/BE4B.251210.005/14574095:userdebug/dev-keys` | `IN_MEMORY_DEX_CLASS_LOADER`（多 buffer 可用） |

### 输入与产物

| 输入 | 锁定值 |
| --- | --- |
| Provider production baseline | commit `4fc228548763564bc94482cec2f029fb63179bc3` |
| Baseline tracked-tree manifest SHA-256 | `a647b45d94c89de60389377b2505f55ff49bf6d3b551414d477c290e7e05e7bd` |
| Provider Debug APK | 31,546,798 bytes；SHA-256 `22aba17e7203f11282f17acde6d53e023c0d4f92d9788f02e8d52332d568db0e` |
| Provider AndroidTest APK | 1,021,724 bytes；SHA-256 `89804e5bbea18e384a55d0b133be255702e1774abb3e59d83eb9b0fd861b7d94` |
| Provider Release APK | 27,090,802 bytes；SHA-256 `f1d680f6779ccf30bac16a1cb9f79a8220995fdf548fdf4fa827c6f03c27f4a0` |
| AutoJs6 Debug APK | 43,993,004 bytes；version `6.8.0` / code `5276` / target 36；SHA-256 `c48d5ab68b0b9548d5efeafe1bb4138ab288ffebda31d8fdaf725ef47c2919c5` |
| Host source context | commit `afca7b14c4ba3971b60a9ce3587e2f10bfd0ab1e`；APK 摘要是安装输入的最终身份 |
| ECJ / D8 | `3.26.0` / `8.13.17` |
| Host-path Java input | [`samples/return-values.java`](../samples/return-values.java)；SHA-256 `2176b2a7a8341ceb04cedc7f74e2bbb82c6c63503a67101aeedc45f0243bcd28` |

tracked-tree manifest 摘要的输入是 production baseline commit 的完整 `git ls-tree -r --full-tree`
清单（UTF-8、LF、末尾 LF），不是工作区文件内容的拼接。AndroidTest APK 另行以自身摘要锁定，因为本轮
修正了 R2 fixture 中把 Gradle namespace 误当 application ID 的陈旧常量；fixture 现在从
`targetContext.packageName` 读取实际安装包名并与 `BuildConfig.APPLICATION_ID` 对照。该修正不改变
production APK，本轮所有受影响的设备用例均在修正后的 AndroidTest APK 上重跑。

## 最近执行记录

以下 `Time` 是 AndroidJUnitRunner 报告的单次墙钟时间，只用于识别执行，不作为性能基线。

| 证据 | API | 结果 | Runner `Time` | 覆盖点 |
| --- | ---: | ---: | ---: | --- |
| `JavaProviderPipelineInstrumentedTest` | 24 | 2/2 | 3.306 s | `Main` + 3 个非 `Main` 布局；实际 `DexClassLoader` |
| `JavaProviderPipelineInstrumentedTest` | 25 | 2/2 | 3.764 s | 同上；实际 `DexClassLoader` |
| `JavaProviderPipelineInstrumentedTest` | 26 | 2/2 | 2.934 s | 同上；实际单 buffer `InMemoryDexClassLoader` |
| `JavaProviderPipelineInstrumentedTest` | 36 | 2/2 | 3.169 s | 同上；实际 `InMemoryDexClassLoader` |
| `JavaSampleLibraryInstrumentedTest` | 24 | 6/6 | 3.550 s | `java.time`/增强 Stream、负向 API、取消、诊断、结果上限、返回值 |
| `JavaSampleLibraryInstrumentedTest` | 36 | 6/6 | 3.822 s | 与 API 24 相同的样例契约 |
| `JavaMultiDexPipelineInstrumentedTest` | 24 | 1/1 | 30.005 s | 2 DEX direct + 内部 worker Binder；私有落盘 loader |
| `JavaMultiDexPipelineInstrumentedTest` | 36 | 1/1 | 21.093 s | 2 DEX direct + 内部 worker Binder；内存多 buffer loader |
| `JvmSourceR2ReadOnlyDexInstrumentationTest` | 36 | 1/1 | 1.845 s | 首字节前只读、成功发布、受控部分写失败与清理 |
| AutoJs6 production direct-path | 24 | 1 条完整观测 | 1624 ms provider session | `ShortcutActivity` → host router → provider Binder → worker |
| AutoJs6 production direct-path | 36 | 1 条完整观测 | 3540 ms provider session | 同上；临时放行并随后恢复 all-files app-op |

### R4 多 DEX 运行时事实

两台设备均由同一个 fixture 生成 65,536 个辅助方法、129 个类、2 个连续命名 DEX，总字节数
2,830,848；direct 与内部 worker Binder 两条路径都实际执行入口并验证返回值：

| API | Direct | Provider 内部 Binder |
| ---: | --- | --- |
| 24 | `loader=PRIVATE_DEX_CLASS_LOADER dexFiles=2 ... direct=true` | `loader=PRIVATE_DEX_CLASS_LOADER ... workerIsolated=true` |
| 36 | `loader=IN_MEMORY_DEX_CLASS_LOADER dexFiles=2 ... direct=true` | `loader=IN_MEMORY_DEX_CLASS_LOADER ... workerIsolated=true` |

完整的两行 logcat 原文分别保存在
[`2026-08-25-api24-multidex.log`](device-evidence-data/2026-08-25-api24-multidex.log) 和
[`2026-08-25-api36-multidex.log`](device-evidence-data/2026-08-25-api36-multidex.log)。

### R2 API 36 只读发布事实

canonical run `351eecc5-3951-4c94-adcf-2244d3fb1d56` 直接调用 production
`FileReadOnlyDexWriteTarget`、`ReadOnlyDexWritePolicy` 与 `PrivateDexArtifactPublisher`，并得到：

- 精确顺序为 `CREATE_EMPTY → OPEN_STREAM → MARK_READ_ONLY → WRITE_CONTENT → SYNC`；
- 在 `WRITE_CONTENT` 回调和首个内容字节之前，user/group/other 三组 write bit 均已移除；
- 成功 DEX 为 924 bytes，SHA-256
  `67da773037ab2fadff480d2892a50f86f84d299aa6f207e37736106cf89bff8d`；
- 受控失败发生在只读后的 16-byte 非零部分写、`sync` 之前；失败产物未越过 publication callback；
- 失败 DEX、request root 与测试自有 base directory 均已清理；
- 设备内重新计算的宿主/provider APK 摘要与命令行锁定值一致，且二者当前 signer 集相同。

未重新序列化的输出行保存在
[`2026-08-25-api36-r2-readonly.jsonl`](device-evidence-data/2026-08-25-api36-r2-readonly.jsonl)。
该测试只接受 API 34 或 36，其他 API 会拒绝执行，避免把近似平台结果误记成 Android 14+ R2 证据。

### 宿主生产 Binder 事实与边界

本轮没有把 provider instrumentation 冒充宿主证据，而是把同一份 `return-values.java` 放入 AutoJs6
私有 cache 后启动导出的 production `ShortcutActivity`。实际路由是：

```text
ShortcutActivity
  → Scripts.executeLaunch
  → JvmSourceExplicitRunner
  → RemoteJvmSourceHost
  → provider :compiler Binder
  → provider :worker Binder
```

完成信号不是 `am start` 返回，而是 provider 私有 JSONL 新增一个结构完整的 schema-v1 对象，且随后
确认一次性 `:worker` PID 消失。API 24 与 36 的原始对象分别保存在：

| API | 原始 JSONL | SHA-256（LF 字节） |
| ---: | --- | --- |
| 24 | [`2026-08-25-api24-host.jsonl`](device-evidence-data/2026-08-25-api24-host.jsonl) | `187f21385820f7e0c65456505204faea74ff442b7a5179c1882d9f4f2d8d686a` |
| 36 | [`2026-08-25-api36-host.jsonl`](device-evidence-data/2026-08-25-api36-host.jsonl) | `8a2b056d0b56a3f2ddcb48f90c3f000c7934b6c4d59309f9ab0a05a1c625cd45` |

API 36 的 direct-path 宿主前置检查要求 all-files app-op；本轮先确认原值为 `default`，测试时临时设为
`allow`，完成后恢复为 `default`。源码始终位于 AutoJs6 私有 cache，没有放宽 provider 权限或缓存
安全门禁。这两条记录是功能 smoke，不替代 [`perf-baseline.md`](perf-baseline.md) 的 1 cold + 10 warm
性能样本和统计口径。

曾尝试在宿主 commit `afca7b14...` 的独立干净 worktree 原样构建宿主 AndroidTest APK；构建在
`:app:compileAppDebugAndroidTestKotlin` 被 18 处既存 Python AIDL fixture 阻断，它们尚未实现新增的
`onHeartbeat(ByteArray)` 回调。目标 Java 测试没有开始执行，因此该尝试不计为通过或失败的 `H`
证据，也没有为绕过问题而修改宿主源码。上面的 production Activity 运行使用已安装且摘要锁定的宿主
APK，直接覆盖了本次需要的真实宿主 Binder 路径。

## 原始记录清单

| 文件 | 字节数 | SHA-256（LF 字节） |
| --- | ---: | --- |
| [`2026-08-25-api24-host.jsonl`](device-evidence-data/2026-08-25-api24-host.jsonl) | 514 | `187f21385820f7e0c65456505204faea74ff442b7a5179c1882d9f4f2d8d686a` |
| [`2026-08-25-api24-multidex.log`](device-evidence-data/2026-08-25-api24-multidex.log) | 347 | `44c94c8bb3e2183b198cb233276bdccdc10674bb1566c0e012a390dd7a93da0d` |
| [`2026-08-25-api36-host.jsonl`](device-evidence-data/2026-08-25-api36-host.jsonl) | 515 | `8a2b056d0b56a3f2ddcb48f90c3f000c7934b6c4d59309f9ab0a05a1c625cd45` |
| [`2026-08-25-api36-multidex.log`](device-evidence-data/2026-08-25-api36-multidex.log) | 351 | `3ee4551ed35e411e3b0dc07eb01e3edf1496ec49028b53bfb2009fc96e70ecac` |
| [`2026-08-25-api36-r2-readonly.jsonl`](device-evidence-data/2026-08-25-api36-r2-readonly.jsonl) | 1,331 | `a57a968bea24902c462dab7b6e6465b75158203bfc4a95285450a23452f56d97` |

## 复现命令

所有设备命令必须显式传 `-s <serial>`；先核对 API、ABI 与 fingerprint，不允许依赖 ADB 默认设备。

### 构建、安装和普通 pipeline

```powershell
.\gradlew.bat --offline :app:assembleDebug :app:assembleDebugAndroidTest

adb -s <serial> install -r app\build\outputs\apk\debug\app-debug.apk
adb -s <serial> install -r -t app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk

adb -s <serial> shell am instrument -w -r `
  -e class org.autojs.plugin.jvmsource.java.JavaProviderPipelineInstrumentedTest `
  io.github.supermonster003.autojs6.plugin.java.runtime.test/androidx.test.runner.AndroidJUnitRunner

adb -s <serial> shell am instrument -w -r `
  -e class org.autojs.plugin.jvmsource.java.JavaSampleLibraryInstrumentedTest `
  io.github.supermonster003.autojs6.plugin.java.runtime.test/androidx.test.runner.AndroidJUnitRunner

adb -s <serial> shell am instrument -w -r `
  -e class org.autojs.plugin.jvmsource.java.JavaMultiDexPipelineInstrumentedTest `
  io.github.supermonster003.autojs6.plugin.java.runtime.test/androidx.test.runner.AndroidJUnitRunner
```

普通 pipeline 必须同时断言 `DexRuntimePolicy.loaderKind` 和实际 class loader；仅看到入口返回值不算
loader 分支证据。多 DEX 运行后还要读取 `JvmSourceR4MultiDex` tag，确认 direct 与 Binder 两行都存在、
DEX 数/字节数/类数一致，且 `workerIsolated=true`。

### R2 API 34/36

先计算当前安装的宿主/provider APK SHA-256 和待测源码树清单摘要，再传入 runner；测试会在设备内独立
重算两个 APK 摘要并验证 signer：

```powershell
adb -s <api34-or-36-serial> shell am instrument -w -r `
  -e class org.autojs.plugin.jvmsource.java.JvmSourceR2ReadOnlyDexInstrumentationTest `
  -e r2.readOnly.canonicalRunId <uuid> `
  -e r2.readOnly.sourceTreeSha256 <lowercase-source-tree-sha256> `
  -e r2.readOnly.hostApkSha256 <lowercase-host-apk-sha256> `
  -e r2.readOnly.providerApkSha256 <lowercase-provider-apk-sha256> `
  io.github.supermonster003.autojs6.plugin.java.runtime.test/androidx.test.runner.AndroidJUnitRunner
```

通过必须同时满足 runner `OK (1 test)` 和输出前缀
`JVM_SOURCE_R2_READONLY_DEX_OBSERVATION=` 后 JSON 的 `result=observed`；`am instrument` 之外自行打印的
相似 JSON 不算证据。

### 宿主 production Binder smoke

下面是原理化命令。API 36 如需临时调整 app-op，必须先记录原值并在 `finally`/清理步骤恢复原值：

```powershell
adb -s <serial> push samples\return-values.java /data/local/tmp/m8-device-evidence-Main.java
adb -s <serial> shell run-as org.autojs.autojs6 mkdir -p cache/m8-device-evidence
adb -s <serial> shell run-as org.autojs.autojs6 cp `
  /data/local/tmp/m8-device-evidence-Main.java cache/m8-device-evidence/Main.java

adb -s <serial> shell am start `
  -n org.autojs.autojs6/org.autojs.autojs.external.shortcut.ShortcutActivity `
  --es path /data/user/0/org.autojs.autojs6/cache/m8-device-evidence/Main.java

adb -s <serial> exec-out run-as io.github.supermonster003.autojs6.plugin.java.runtime `
  cat files/debug-observations/java-provider-observations-v1.jsonl
```

必须比较启动前后的完整 JSONL 行数，等待新增完整对象，并验证五阶段都是非负整数、worker profile 为
`COLD`、终态 worker PID 已消失。最后删除 host 私有 fixture 与 `/data/local/tmp` staging 文件。若宿主
仓的规范 instrumentation 可构建，应优先在那里运行并将新证据落回宿主仓。

## 证据失效与更新规则

出现以下任一变化，相关行必须先改回 `[ ]`，重建 APK 并在设备上重跑；不能沿用旧截图或只引用单测：

- `DexRuntimePolicy`、`WorkerDexLoader`、DEX 集验证、D8 参数/版本、缓存物化或 class loader 构造方式变化：
  失效 `P24/P25/P26/P/W-R4`；
- compiler→worker AIDL、FD 所有权、进程声明、UID/PID pin、终态/取消/看门狗变化：失效 `W`；
- `ReadOnlyDexWritePolicy`、`FileReadOnlyDexWriteTarget`、`PrivateDexArtifactPublisher`、targetSdk 或 R2
  清理语义变化：失效 `R34/36`；
- desugared-library 配置、ECJ 可见 stub、L8 约束或样例契约变化：失效 `P-M8-3`；
- provider service manifest、签名、协议 AAR、application ID、宿主 discovery/selection/bridge 变化：
  失效 `H24/H34+`，并由宿主仓重新取证；
- 任何 production 代码、依赖锁或签名输入变化：重新记录 provider APK 摘要并至少重跑所有受影响行；
- 只有文档变化且 APK 字节完全不变时，可以保留设备行，但仍需更新文档审校与离线 gate 结果。

新记录不得覆盖旧原始文件。文件名使用 UTC/本地日期均可，但文档必须明确时区；同时记录完整
fingerprint、API、ABI、production APK SHA-256、AndroidTest APK SHA-256、测试类、测试数和 runner
结论。模拟器 snapshot、ADB serial 或宿主机负载变化不自动推翻功能结论，但若用于性能比较，必须按
[`perf-baseline.md`](perf-baseline.md) 建立新环境基线。

## 清理检查

每轮设备取证结束后执行并记录：

- host 私有 Java fixture 和 `/data/local/tmp` staging 文件已删除；
- 临时 app-op 已恢复到测试前值；
- provider `:worker` 不再存活，compiler 不持有活动会话；
- 本轮临时安装的 provider / AndroidTest 包已卸载；
- 仅为本轮启动的 AVD 已正常关机；
- 宿主仓临时 worktree 已移除，原宿主工作区的未提交改动未被修改。

2026-08-25 本轮清理结果：

- [x] API 24/36 的 host 私有 fixture 与 `/data/local/tmp` staging 文件均已删除；
- [x] API 36 `MANAGE_EXTERNAL_STORAGE` app-op 已从临时 `allow` 恢复到测试前的 `default`；
- [x] API 24/25/26/36 均已卸载本轮安装的 provider 与 AndroidTest 包，存在的 AutoJs6 宿主包未卸载；
- [x] 本轮启动的 `emulator-5560`（API 26）与 `emulator-5562`（API 36）已正常关闭；
- [x] 独立宿主 worktree `D:\idea-projects\AutoJs6-m8-evidence-afca` 已解除注册并删除；宿主主工作区
  原有的 Node/Explorer 未提交改动保持存在，本轮没有修改这些文件。

## M10-2 D8 升级增量证据（2026-08-27）

本文件前述表格继续保留 2026-08-25 M8 发布基线及其 D8 8.13.17 工件身份，不能用新结果覆盖历史
记录。M10-2 把 Provider 运行时 D8/R8 升至 8.13.23 后，按失效规则重新取得受影响的 Provider 证据：

| Gate | M10-2 结果 |
| --- | --- |
| `P24/P25` | `JavaProviderPipelineInstrumentedTest` 各 2/2；API 24/25 私有 loader 分支通过 |
| `P26` | pipeline 2/2、样例 11/11；API 26 独有的单 buffer `InMemoryDexClassLoader` 分支通过 |
| `P27+` | API 28 与 API 35 各 2/2；`InMemoryDexClassLoader` 分支通过 |
| 样例矩阵 | API 24/25/26/28/35 各 11/11，含 desugaring 正负边界、取消、诊断和返回值 |
| `P/W-R4` | API 24、28、35 各 1/1；65,536-method fixture 产出 2 DEX 并经 direct/worker 执行 |
| `R34/36` | API 36 精确 R2 时序 1/1；D8 8.13.23、首字节前只读、部分写失败不发布且清理完成 |
| 本地 gate | Debug/Release 各 164/164，lint 0 error，Debug/AndroidTest/Release APK 全部通过 |

M10-2 没有改变 Host discovery、签名、协议 AAR 或 production Binder wire，因此这次依赖补丁不伪造
新的 `H` 证据；既有宿主/Provider 分工保持不变。API 36 canonical run
`a1956e81-4503-42a6-b088-00606ff31479` 的原始行见
[`2026-08-27-api36-m10-2-r2-readonly.jsonl`](device-evidence-data/2026-08-27-api36-m10-2-r2-readonly.jsonl)。
完整版本选择、缓存自动失效、JAR/APK 摘要、API 24/25/26/28/35/36 指纹、API 28 真机 JDWP 基线对照与
设备清理记录见 [`d8-upgrade-m10-2.zh-CN.md`](d8-upgrade-m10-2.zh-CN.md)。
