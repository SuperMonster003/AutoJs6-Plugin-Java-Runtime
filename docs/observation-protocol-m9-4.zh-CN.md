# M9-4 观测数据协议化：Protocol 1.5 / Schema 1.2

## 结论

M9-4 已在 AutoJs6 Host 与 Java Runtime Provider 两侧完成。Provider 现在会在成功、错误或取消终态的
compiler/worker 资源清理结束后，附带一个有界 `JvmSourceObservation`；Host 重新验证请求绑定、字段组合、
数值上限和资源样本形状，再向执行 sink 发布并投影为固定公开文本。Release APK 使用同一条协议路径，
不依赖 M7 的 Debug 私有 JSONL。

一次缓存未命中的公开显示例如：

```text
JVM observation: total=828 ms, worker-start=41 ms, compile=224 ms, d8=343 ms, load=4 ms, run=0 ms, cleanup=12 ms, cache=MISS/NOT_FOUND, starts=compiler:COLD worker:COLD, samples=5, peak-rss=43905024 B, max-fd=89, max-temp=1668 B, max-output=0 B
```

缓存命中时，`compile` 与 `d8` 不会伪造为零，而是精确显示为 `n/a`：

```text
JVM observation: total=118 ms, worker-start=52 ms, compile=n/a, d8=n/a, load=1 ms, run=1 ms, cleanup=10 ms, cache=HIT, starts=compiler:WARM worker:COLD, samples=5, peak-rss=52588544 B, max-fd=93, max-temp=1668 B, max-output=0 B
```

本项锁定为 Protocol `1.5`、Tagged Wire schema `1.2`。Entry API 仍为 `4`，外部 Binder AIDL 与
`AutoJsJvmEntry.run(JvmScriptContext)` descriptor 均不变。最低 Host 提升至 AutoJs6 version code
`5280`；Provider 提升至 `0.7.0-m9` / version code `7`。

## 锁定版本与工件

| 项目 | 锁定值 |
|---|---|
| 协议 / Wire schema / Entry ABI | Protocol `1.5` / schema `1.2` / Entry API `4` |
| 最低 Host | AutoJs6 `6.8.0` / version code `5280` |
| Provider | `0.7.0-m9` / version code `7`；发现资源 min/max 均为 `1.5` |
| Host 源码 | commit `22bf823288d8b7b8f12acd71fa4f8e149ee03954`；branch `codex/m9-observation-export` |
| Provider 功能基线 | commit `03addd51d14aca430bf88f153850e04c483b9166`；branch `main` |
| `common-plugin-api.aar` | SHA-256 `c526f4fd0adbf38b36a7bf54f9931385e6cc20050d6ea8fb741c60496af635d5` |
| `protocol-wire-api.aar` | SHA-256 `e044dd3cc9bed84e844174e0963b57a1f67902cf021ccb42d1031d3511ce46da` |
| `jvm-source-api.aar` | 200,068 bytes；SHA-256 `97006eb8a892d617939a5f49586a19a91ed05baaf6c59d75c46f6faf18fc81b3` |

三份 AAR 均来自上述已提交且干净的 Host snapshot；common/wire 工件与 M9-3 逐字节相同，只有
`jvm-source-api.aar` 因 observation model、验证和 codec 变化而更新。
[`protocol-artifacts.lock.json`](../protocol/protocol-artifacts.lock.json) 记录 Host revision、
`sourceDirty=false`、来源 module 与摘要，Provider 构建的 `verifyPinnedInputs` 会逐项重验。

## Wire 形态与终态时序

Observation 不增加新的 Binder 方法，而是嵌入三种既有终态：

| 终态 | Tagged Wire tag | reader 属性 |
|---|---:|---|
| `JvmSourceResult` | `24` | 有值时 `requiredForReader=true` |
| `JvmSourceError` | `6` | 有值时 `requiredForReader=true` |
| `JvmSourceCancellation` | `5` | 有值时 `requiredForReader=true` |

嵌套文档 schema ID 为 `0x0316`，单个资源样本 schema ID 为 `0x0317`。Protocol 1.4 的旧终态不含这些
tag，新 decoder 会把 observation 解码为 `null`；反向读取时，1.4 reader 遇到 1.5 的 reader-required
tag 会明确报 `UNKNOWN_REQUIRED_FIELD`，不能静默丢弃新语义。

Presence 还与请求协商版本绑定：Protocol 1.5 终态必须有 observation，低于 1.5 时必须没有。当前官方
Host 与 Provider 都将 min/max 锁在 1.5，并通过 Host 5280 做版本门禁；共享验证仍保留低版本规则，避免
直接构造请求绕过协商。

Provider 的终态流程固定为：

```text
compiler/worker 产生内部终态
  → 关闭 FD、删除 workspace、终止并解绑一次性 worker
  → 采集最终 TERMINATION 数值
  → compiler 合成 Release-safe JvmSourceObservation
  → terminal delivery barrier retire
  → 此时才编码并发送外部 success/error/cancellation
```

compiler↔worker 的内部终态必须不含 observation；compiler 收到带 observation 的内部 result/error/
cancellation 会按 worker protocol violation 处理。这样 worker 无法伪造 compiler 耗时、缓存结果或最终
资源采样，外部 snapshot 只能由拥有清理状态的 compiler 生成。

## 字段语义与不变量

| 字段 | 语义 | 组合规则 |
|---|---|---|
| `requestId` | 与终态及原请求关联 | 必须三者完全一致；公开 Host 投影会删除 |
| `sessionElapsedMillis` | Provider session 创建至清理后准备投递的墙钟耗时 | 必填、非负、有界 |
| `workerStartupDurationMillis` | bind 开始至 `onServiceConnected` | 未启动或不可用时为 `null`；不得大于 session |
| `compilerStartProfile` | 当次 compiler 为 `COLD` 或 `WARM` | 必填 |
| `workerStartProfile` | 一次性 worker 为 `COLD`/`WARM` | 未到 worker 时为 `null`；生产成功路径应为 `COLD` |
| `compileDurationMillis` / `d8DurationMillis` | ECJ 与 D8 阶段耗时 | 未到阶段或 cache hit 时为 `null` |
| `loadDurationMillis` / `runDurationMillis` | worker 装载与用户入口执行耗时 | 未到阶段时为 `null`；真实零毫秒保留为 `0` |
| `terminationDurationMillis` | compiler 与 worker 可用清理耗时之和 | 未采到时为 `null`；独立上限允许两个进程求和 |
| `cacheOutcome` | `NOT_EVALUATED`、`HIT` 或 `MISS` | `HIT` 强制 compile/D8 为 `null` |
| `cacheMissReason` | 固定枚举 miss 原因 | 当且仅当 outcome 为 `MISS` 时存在 |
| `resources` | 最多六个 path-free、identity-free 数值样本 | `(process, phase)` 唯一，至少一个 metric 非空 |

允许的资源采样点只有：

- compiler：`COMPILE`、`D8`、`TERMINATION`；
- worker：`LOAD`、`RUN`、`TERMINATION`。

Host 进程样本不会进入协议。每个样本只能包含 `rssBytes`、`openFileDescriptorCount`、
`temporaryStorageBytes` 与 `outputBytes` 四个可空数值；`null` 表示平台探针不可用，零表示真实观测。
Provider 会先过滤全空样本，再按 `(process, phase)` 去重并截断到六个。

## 硬上限与 Release 饱和策略

| 边界 | 上限 |
|---|---:|
| Observation 独立 Tagged Wire envelope | 4 KiB（4,096 bytes） |
| 资源样本数 | 6 |
| session elapsed | 180,000 ms（请求超时上限 120 s + 清理余量 60 s） |
| 单个 COMPILE/D8/LOAD/RUN 数值 | 130,000 ms |
| compiler + worker TERMINATION 总数值 | 260,000 ms |
| RSS | 8 GiB（8,589,934,592 bytes） |
| open FD | 32,768 |
| 临时存储 | 56 MiB（58,720,256 bytes；source + class artifact + DEX artifact 上限之和） |
| 输出 | 2 MiB（2,097,152 bytes；stdout + stderr 上限之和） |

Release 投影对 session、启动耗时、每个阶段、终止求和与四类资源数值分别做显式饱和；先把两个终止
分量各自限制到单阶段上限，再求和，避免 `Long` 溢出。单测用 `Long.MIN_VALUE/MAX_VALUE` 与
`Int.MIN_VALUE/MAX_VALUE` 覆盖上下边界，确认最终对象仍通过共享验证并可在 4 KiB envelope 内编码。

这类饱和只影响异常的测量输入，不放宽执行预算或工件大小；请求/worker 看门狗与原来的 fail-closed
策略不变。

## 隐私与 Host 独立投影

`JvmSourceObservation` 的类型结构不能表达源码、参数、诊断文本、异常、路径、文件名、package/component、
签名、Binder identity、UID 或 PID。为关联终态而存在的 `requestId` 只用于 Host 验证，公开投影立即删除。

M7 Debug JSONL 中的累计 cache hit/miss 计数与按原因累计值不会跨协议。Protocol 1.5 只返回当次请求的
`outcome + optional reason`；Debug 私有通道仍由 `BuildConfig.DEBUG` 与 debuggable 双门禁控制，Release
路径不会创建本地观测目录或写文件。

Host 不信任 Provider 已完成边界处理。callback 收到任一终态后，会重新执行：

1. Tagged Wire schema、required tag、byte envelope 与 enum 检查；
2. observation 自身字段/样本不变量检查；
3. observation request ID、terminal request ID 与提交 request 的三方绑定；
4. Protocol 1.5 presence 检查；
5. 删除 request ID 与样本维度，只保留阶段数值、cache 枚举和资源峰值；
6. 使用固定 formatter 生成控制台文本，不拼接 Provider 自由文本。

成功时 observation 同时保存在 `RemoteJvmScriptResult`；错误或取消时，Host 也会先发布 observation，再抛出
稳定的用户失败。执行 sink 自身抛错会被隔离，不会覆盖原终态。Provider 缺少、错绑或畸形 observation
会触发 transport quarantine，测试还验证了下一次健康连接可以恢复。

设备 marker 为证明 production 路径，额外记录 provider component 与三进程 PID；这些字段属于
instrumentation 证据元数据，不在 `JvmSourceObservation` 或公开文本内。

## 自动化门禁与最终制品

最终自动化结果：

- Host `jvm-source-api`：61/61；Host app：1,660 tests，0 failure/error，3 个既有 skipped。
- Host App Debug、AndroidTest APK 与 `jvm-source-api` Release AAR 构建成功。
- Provider Debug/Release JVM 单测各 155/155；Debug lint 0 error（29 个既有资源/依赖 warning）。
- Provider Debug、Release 与 AndroidTest APK 全部离线构建成功；发现资源在 Debug/Release/AndroidTest
  三个 variant 均核验为 Protocol `1.5`。
- 共享测试覆盖三种终态 round-trip、旧 reader required-tag 拒绝、1.4 空字段兼容、4 KiB/六样本边界、
  非法 process/phase、重复样本、cache 组合、每类数值越界与 request 绑定。
- Provider Debug/Release 测试覆盖所有 cache miss reason、六个 Release-safe 样本、全空样本删除、HIT
  阶段缺省以及极端数值饱和；Host 测试覆盖固定公开文本、成功/失败发布顺序和缺字段隔离恢复。

最终 Provider 制品：

| 制品 | 字节数 | SHA-256 |
|---|---:|---|
| Debug APK | 30,860,146 | `e5c04fd547dfaae8c207873097e1e4d9d3c3fba17c85b828fe953bdf2f328ef9` |
| Release APK | 27,105,286 | `36af723721b5aef2d9ebbf7fb6b1d659bf39ae110f55a002bef67d242168d713` |
| AndroidTest APK | 1,027,077 | `0387d1929f6cffd019ed4c54fcf8fd597cb5f348d0dbb1acae40ee780769403e` |

## API 28 Provider 样例回归

最终 Debug 与 AndroidTest APK 在既有 Sony G8441（Android 9 / API 28 / arm64-v8a，fingerprint
`Sony/G8441/G8441:9/47.2.A.4.45/3677320370:user/release-keys`）运行
`JavaSampleLibraryInstrumentedTest`：10/10，runner `Time: 9.648`。它覆盖参数、返回值、剪贴板默认拒绝与
授权、取消、core library desugaring、编译诊断、运行时异常投影和结果超限，确认共享 AAR 与终态模型没有
破坏 API 28 的真实 ECJ→D8→ART 路径。

测试后只卸载本轮安装的 Provider 与 Provider test package；设备原有 AutoJs6 package 保持存在。

## API 36 Host + Release Provider 生产证据

专用 `AVD_API_36.1` 使用 Android 16 / API 36 / x86_64，fingerprint
`google/sdk_gphone64_x86_64/emu64xa:16/BE4B.251210.005/14574095:userdebug/dev-keys`。三件套使用同一证书，
certificate SHA-256 为
`31a681fcfffb3e428420cae280ded89292b12a3b0f59e19b7a73e32a8ae4c213`：

| 制品 | 字节数 | SHA-256 |
|---|---:|---|
| Host x86_64 Debug APK；AutoJs6 `6.8.0` / code `5280` | 42,516,701 | `d1bf5d327386d1c92c6de13fe26749316eddf6342d15ff65d2462992ffb87770` |
| Host AndroidTest APK | 1,607,396 | `4a4efcb11746d01316d14240b33d456c46591233e957af2da95d73e07fa7a067` |
| Provider Release APK；`0.7.0-m9` / code `7` | 27,105,286 | `36af723721b5aef2d9ebbf7fb6b1d659bf39ae110f55a002bef67d242168d713` |

Selector
`JvmSourceR1EndToEndInstrumentationTest#negotiatedObservationIsVisibleAfterProductionProviderCleanup`
使用生产 `RemoteJvmSourceHost.createAndroid(...)`、签名/版本门禁与真实三进程 Binder 链执行
`return 42`。第一次为空 cache，第二次复用同一 compiler cache：

| 运行 | Runner | session / startup | 阶段 ms | cache / starts | 资源峰值 | Wire |
|---|---|---|---|---|---|---|
| 冷态 | 1/1，1.45 s | `828 / 41` | `C=224 D8=343 L=4 R=0 T=12` | `MISS/NOT_FOUND`；`COLD/COLD` | 5 samples；RSS `43,905,024` B；FD `89`；temp `1,668` B；output `0` B | 1,140 bytes |
| 暖态 | 1/1，0.274 s | `118 / 52` | `C=n/a D8=n/a L=1 R=1 T=10` | `HIT`；`WARM/COLD` | 5 samples；RSS `52,588,544` B；FD `93`；temp `1,668` B；output `0` B | 1,072 bytes |

两次都完成 encode→decode 相等检查、4 KiB 检查、Host 固定公开文本与身份/路径不出现检查。冷态 host/
compiler/worker PID 为 `4213/4868/5053`，暖态为 `5102/4868/5139`，证明两次 Host runner 与 disposable
worker 都不同，而 compiler 被复用并产生真实 HIT。

终态后独立检查得到：`worker-pid=`、`host-test-pid=`、`m9-cache-entry-count=0`，compiler PID `4868`
仍作为可复用服务存在。对 Provider 执行 `run-as` 返回 `package not debuggable`；Release exporter 的零目录/
零写入副作用由 Release variant 单测覆盖。

两条未重序列化 marker、runner 时间与清理检查保存在
[`2026-08-26-api36-m9-observation.log`](device-evidence-data/2026-08-26-api36-m9-observation.log)：
3,600 bytes，SHA-256 `b567d66c03cf6b2c51fe3ea6d20ab0611fb6321742007b02c565b1718d74275a`。

证据收集后，专用 AVD 上的 Host test、Provider、Host 三个 package 均已卸载，并只关闭本轮
`emulator-5554`。构建时短暂复制到隔离 Host worktree 的 `sign.properties` 与 JKS 已删除；原始 Host
工作树及其中既有用户改动没有被清理、覆盖或提交。
