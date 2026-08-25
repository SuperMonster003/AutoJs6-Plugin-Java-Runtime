# M7 Debug 本地观测通道

## 边界与用途

本通道为 M7-5 性能基线、M8-4 缓存重启对照和 M9-4 协议设计积累设备证据。它不修改 Protocol 1.1，不向
AutoJs6 宿主、外部 Binder 回调、网络或 logcat 发送观测数据；编译器进程只在一次会话完成清理后，
向插件自己的私有目录追加一行 JSON。

导出同时受两个条件约束：

- 构建常量 `BuildConfig.DEBUG` 为 `true`；
- APK 的 `ApplicationInfo.FLAG_DEBUGGABLE` 为 `true`。

任一条件不成立时，环境安装的是显式 no-op exporter。该路径不会创建目录、打开文件或写入字节。
同一组单测会分别编译并运行在 Debug 和 Release unit-test variant 中；Release variant 使用真实的
`BuildConfig.DEBUG=false` 断言零文件副作用。

M8-4 评估期间曾临时使用 non-debuggable benchmark build type 采集 cache-enabled 对照；同 UID worker
可使用 Keystore alias 的攻击证据使候选 no-go 后，该 build type 与全部原型代码均已移除。当前源码只有
上述 Debug 双门禁与 Release no-op 两种正式行为；历史证据见
[`cache-persistence-m8-4.zh-CN.md`](cache-persistence-m8-4.zh-CN.md)。

## 取数

Debug APK 每次完成会话后写入：

```text
/data/user/0/io.github.supermonster003.autojs6.plugin.java.runtime/files/
  debug-observations/java-provider-observations-v1.jsonl
```

这是应用私有路径。安装 Debug APK、执行样例并等待终态后，可以从开发机读取：

```powershell
adb shell run-as io.github.supermonster003.autojs6.plugin.java.runtime `
  cat files/debug-observations/java-provider-observations-v1.jsonl
```

需要保存为本地文件时：

```powershell
adb exec-out run-as io.github.supermonster003.autojs6.plugin.java.runtime `
  cat files/debug-observations/java-provider-observations-v1.jsonl `
  > .\java-provider-observations-v1.jsonl
```

每行都是独立的 UTF-8 JSON 对象。文件上限为 256 KiB；下一行无法完整容纳时，writer 会截断旧窗口，
再写入完整的新行。若上次进程异常退出留下不完整尾行，下一次写入也会从一个新窗口开始，避免把两个
JSON 对象粘连。writer 在会话耗时采样完成后才执行并同步文件，因此文件 I/O 不计入该行的
`sessionElapsedMillis`。

切换 Debug/Release APK 做验证时，建议先卸载旧包或清除应用数据，以免把 Debug 安装曾留下的历史文件
误认为 Release 新写入；Release 代码本身不会创建或更新该文件。

## JSON schema v1

示例（为便于阅读进行了换行；实际文件每个对象占一行）：

```json
{
  "schemaVersion": 1,
  "sessionElapsedMillis": 180,
  "workerStartupDurationMillis": 25,
  "startProfile": { "compiler": "COLD", "worker": "COLD" },
  "phasesMillis": {
    "COMPILE": 10,
    "D8": 20,
    "LOAD": 30,
    "RUN": 40,
    "TERMINATION": 7
  },
  "cache": {
    "outcome": "MISS",
    "missReason": "NOT_FOUND",
    "hits": 2,
    "misses": 1,
    "publications": 1,
    "publicationFailures": 0,
    "missesByReason": {
      "NOT_FOUND": 1,
      "INVALID_OR_EXPIRED": 0,
      "CACHE_UNAVAILABLE": 0,
      "MATERIALIZATION_FAILED": 0,
      "CACHE_DISABLED": 0,
      "PROVIDER_IDENTITY_UNAVAILABLE": 0,
      "PROVIDER_IDENTITY_DRIFTED": 0
    }
  }
}
```

字段语义：

| 字段 | 语义 |
| --- | --- |
| `schemaVersion` | 本地 JSONL schema，当前固定为 `1`；不是 Protocol 版本。 |
| `sessionElapsedMillis` | 编译器收到会话对象起，到编译器/worker 资源清理完成并准备导出止的单调时钟耗时。 |
| `workerStartupDurationMillis` | 调用 `bindService` 到收到预期 worker 的 `onServiceConnected` 的耗时，用于 M7-5/M8-1 冷启动对比。 |
| `startProfile.compiler` | 编译器进程中第一条已完成会话为 `COLD`，其后的会话为 `WARM`。 |
| `startProfile.worker` | 当前一次性 worker 始终为 `COLD`；未进入 worker 时为 `null`。 |
| `phasesMillis.COMPILE` | ECJ 编译耗时。缓存命中或未进入编译时为 `null`。 |
| `phasesMillis.D8` | D8 转换耗时。缓存命中或未进入 D8 时为 `null`。 |
| `phasesMillis.LOAD` | worker 中校验 DEX、创建 class loader、加载并实例化入口类的耗时。 |
| `phasesMillis.RUN` | 调用入口、编码返回值前后的运行阶段耗时。 |
| `phasesMillis.TERMINATION` | 当前可用的 compiler 与 worker 清理耗时之和；均未采到时为 `null`。 |
| `cache.outcome` / `missReason` | 当前会话的缓存结果；未评估、命中、未命中分别为 `NOT_EVALUATED`、`HIT`、`MISS`。 |
| `cache.hits` / `misses` | 当前 `:compiler` 进程 epoch 内的累计查找计数。 |
| `cache.publications` / `publicationFailures` | 当前进程 epoch 内的累计发布结果。 |
| `cache.missesByReason` | 与 `misses` 同一进程 epoch、按固定枚举拆分的累计未命中计数。 |

阶段未到达或样本未回传时使用 JSON `null`，不会用 `0` 冒充缺失值；实际测得的零毫秒仍写成数字 `0`。
所有字段只有有界枚举和非负数值。schema 无法表示 request ID、源码/工件路径、类名、包名、摘要、签名、
UID/PID、组件名或 Binder 标识。

## 缓存计数说明

现有安全策略只在“非 debuggable 且编译器已设为 non-dumpable”时启用带进程 epoch HMAC 的编译缓存。
因此标准 Debug APK 的真实设备记录会把每次查找记为 `MISS/CACHE_DISABLED`，`hits` 预期为零；本地 schema
仍完整导出命中、未命中、发布和原因计数，其组合与序列化由单测覆盖。不要为了采集非零命中数而放宽
缓存安全条件。M8-4 的临时 Keystore 候选虽完成 0/5 → 5/5 的进程重启对照，最终因同 UID worker
key-access 反证而 no-go；方法、原始 JSONL 和剩余风险见
[`cache-persistence-m8-4.zh-CN.md`](cache-persistence-m8-4.zh-CN.md)。

## M7-5 计算口径

基线至少分别保留 API 24 与 API 34/36 设备的原始 JSONL。当前一次性 worker 的冷启动占比按下式计算：

```text
workerColdStartShare = workerStartupDurationMillis / sessionElapsedMillis
```

仅对两个字段都为非空且 `sessionElapsedMillis > 0` 的行计算。报告应分别呈现 compiler `COLD`/`WARM`，
不要把缺失阶段当成零，也不要用五阶段之和反推 worker 冷启动；源码读取、缓存判断、Binder 交接等时间
并不属于五阶段中的任何一个。

2026-08-25 的 API 24/API 36 生产 Binder 路径基线、原始 JSONL、统计口径和 M8-1 前后对比契约见
[`perf-baseline.md`](perf-baseline.md)。
