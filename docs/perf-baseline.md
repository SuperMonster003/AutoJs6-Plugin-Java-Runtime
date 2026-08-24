# M7-5 Java Provider 性能基线

## 结论

2026-08-25 在两台受控 Android 模拟器上，通过 AutoJs6 的生产 `ShortcutActivity` 路由执行同一份
Java 源码，各采集 1 次 compiler `COLD` 样本和 10 次 compiler `WARM` 样本。每次执行仍创建并回收
一次性 worker，因此所有样本的 worker profile 都是 `COLD`。

| 指标（毫秒，除占比外） | API 24 / x86 | API 36 / x86_64 |
| --- | ---: | ---: |
| compiler `COLD` 会话，n=1 | 2855 | 1458 |
| compiler `WARM` 会话 p50 | 933 | 1160.5 |
| compiler `WARM` 会话 p90 | 1317 | 1701 |
| compiler `WARM` 会话范围 | 717–1734 | 1013–1732 |
| worker 启动 p50 | 52 | 424 |
| worker 启动 p90 | 93 | 661 |
| worker 启动占会话 p50 | 5.44% | 36.09% |
| worker 启动占会话 p90 | 9.79% | 42.86% |

这组证据支持继续推进 M8-1：API 36 暖态会话中，worker 启动 p50 为 424 ms，占 provider 会话
p50 附近的三分之一以上；同一组样本的 `COMPILE + D8` 两项 p50 分别为 221 ms 和 259.5 ms，已有足够长的
前置工作可与 worker 启动重叠。API 24 的 worker 启动 p50 只有 52 ms，预热收益上限明显较小，因此
API 36 是 M8-1 的主要性能判据，API 24 主要承担兼容性与不回归判据。

以上只是同一设备上前后对比的基线，不表示 API 24 比 API 36 更快。两台 AVD 的系统镜像、ABI 和虚拟硬件
不同，不能用这组数据评价 Android 版本之间的性能。

## 计时边界

`sessionElapsedMillis` 是 **provider 请求端到端耗时**：从 `:compiler` 内创建
`RemoteJavaSourceSession` 起，到 compiler/worker 资源清理完成、准备写出观测行止。它包括源码复制与校验、
缓存判断、ECJ、JAR/DEX 处理、worker 绑定、DEX 加载、入口执行、Binder 交接及清理，但不包括：

- AutoJs6 UI/Activity 启动与脚本路由；
- AutoJs6 发现、选择 provider 的时间；
- AutoJs6 首次绑定 `:compiler` 服务及 `openSession` 之前的握手；
- JSONL writer 的文件写入与 `fsync`。

因此这里的“端到端”不是用户点击到结果出现的完整感知时延。compiler `COLD` 也只表示该 compiler
进程完成的第一条 provider 会话；由于计时从 `openSession` 内创建会话开始，它不测量 compiler 进程自身的
首次服务绑定时间。五个阶段只覆盖被明确插桩的区间，阶段之和不应等同于 `sessionElapsedMillis`。

`workerStartupDurationMillis` 从调用 `bindService` 起，到收到并验证预期 worker 的
`onServiceConnected` 止。worker 冷启动占比逐行按下式计算，再对每行的比值取分位数：

```text
workerColdStartShare = workerStartupDurationMillis / sessionElapsedMillis
```

## 环境与输入锁定

### 设备

| 项目 | API 24 设备 | API 36 设备 |
| --- | --- | --- |
| AVD | `AVD_API_24` | `DEX_R1_API36_X64` |
| 类型 | Android Emulator | Android Emulator |
| Model | `Android SDK built for x86` | `sdk_gphone64_x86_64` |
| SDK / ABI | 24 / `x86` | 36 / `x86_64` |
| Build fingerprint | `google/sdk_google_phone_x86/generic_x86:7.0/NYC/6696031:userdebug/dev-keys` | `google/sdk_gphone64_x86_64/emu64xa:16/BE4B.251210.005/14574095:userdebug/dev-keys` |
| ADB serial | `emulator-5558` | `emulator-5562` |

两组测试在同一开发机上顺序执行；未固定模拟器 CPU 频率，也未把宿主机调度噪声隔离掉。API 36 的生产
direct-path 路由在该受控 AVD 上通过 `MANAGE_EXTERNAL_STORAGE` app-op 放行；源文件本身仍位于 AutoJs6
私有 `cache/m7-perf/Main.java`，没有改动 provider 权限或安全策略。

### 二进制与源码

| 输入 | 锁定值 |
| --- | --- |
| Provider source | commit `cfb381b` |
| Provider Debug APK SHA-256 | `17bbb835b31a9916ea7625fb8de58259b32409b01975eef49ab593d5d65d62cb` |
| AutoJs6 Debug universal APK SHA-256 | `c48d5ab68b0b9548d5efeafe1bb4138ab288ffebda31d8fdaf725ef47c2919c5` |
| AutoJs6 checkout context | commit `afca7b14c4ba3971b60a9ce3587e2f10bfd0ab1e`；工作区存在与本次测量无关的未提交改动，因此 APK 摘要是权威输入标识 |
| Frozen protocol source revision | `d21c69a2523a529ce6e2cd5d7dc3ced49cbaf74d` |
| Java input | [`samples/return-values.java`](../samples/return-values.java) |
| Java input SHA-256 | `2176b2a7a8341ceb04cedc7f74e2bbb82c6c63503a67101aeedc45f0243bcd28` |

使用 `return-values.java` 是为了让所有五个阶段都成功到达，同时避免 host call、toast、输出和主动 sleep
把业务行为混入基线。它返回一个很小的有界 JSON-profile 值，`RUN` 阶段因此接近空载下限。

## 采集方法

1. 在每台 AVD 安装上表锁定的 AutoJs6 Debug APK 与 provider Debug APK。
2. 把同一份源码复制到 AutoJs6 私有路径 `cache/m7-perf/Main.java`，清空旧观测文件并确保 compiler/worker
   进程不在运行。
3. 通过导出的生产 Activity 发起 direct-path launch：

   ```powershell
   adb -s <serial> shell am start `
     -n org.autojs.autojs6/org.autojs.autojs.external.shortcut.ShortcutActivity `
     --es path /data/user/0/org.autojs.autojs6/cache/m7-perf/Main.java
   ```

4. 每次都等待 provider 私有 JSONL 新增一个完整对象，并确认 terminal worker 进程已经退出；完成后再等待
   750 ms，才发起下一次。第一条保留为 compiler `COLD` 描述性样本，后续 10 条作为 compiler `WARM`
   统计样本。
5. 用 `run-as io.github.supermonster003.autojs6.plugin.java.runtime` 导出原始 JSONL；逐行解析并验证：
   schema 为 1、五阶段均为非负数、第一行 compiler 为 `COLD`、其余为 `WARM`、所有 worker 为 `COLD`。

这条 Activity 路由实际进入 `Scripts.executeLaunch` → `JvmSourceExplicitRunner` →
`RemoteJvmSourceHost` → provider Binder，会覆盖生产宿主与 provider 的真实 Binder 路径。命令刻意不使用
`-W`，因为 API 24 上等待 `Theme.NoDisplay` Activity 可能不会及时返回；`am start` 返回也不被
当作完成信号，观测文件新增完整行才是完成信号。

标准 Debug 构建按安全策略禁用 HMAC 编译缓存，所以 22 条记录都应为 `MISS/CACHE_DISABLED`，累计
`hits=0`，`misses` 与 `missesByReason.CACHE_DISABLED` 从 1 严格递增到 11。该不变量已由原始数据校验。
本次没有为了制造缓存命中而放宽 non-debuggable/non-dumpable 门禁。

## 统计口径

- 暖态样本量固定为每台设备 `n=10`；第一条冷态样本不参与分位数。
- p50 是排序后中间两个值的算术平均；p90 使用 nearest-rank，即位置 `ceil(0.9 × n)`。
- 范围为最小值到最大值。
- 单位来自 Android 单调时钟 `SystemClock.elapsedRealtime()`，分辨率为毫秒。
- 缺失阶段保持 JSON `null`，不按零处理；本次成功样本没有缺失阶段。

### 暖态总体分布

| 指标 | API 24 min | API 24 p50 | API 24 p90 | API 24 max | API 36 min | API 36 p50 | API 36 p90 | API 36 max |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `sessionElapsedMillis` | 717 | 933 | 1317 | 1734 | 1013 | 1160.5 | 1701 | 1732 |
| `workerStartupDurationMillis` | 30 | 52 | 93 | 177 | 373 | 424 | 661 | 729 |
| worker 启动占比 | 3.94% | 5.44% | 9.79% | 10.21% | 30.57% | 36.09% | 42.86% | 48.32% |

### 暖态阶段分布

| 阶段（毫秒） | API 24 p50 | API 24 p90 | API 36 p50 | API 36 p90 |
| --- | ---: | ---: | ---: | ---: |
| `COMPILE` | 157 | 229 | 221 | 267 |
| `D8` | 194 | 297 | 259.5 | 382 |
| `LOAD` | 59.5 | 111 | 7.5 | 10 |
| `RUN` | 2.5 | 5 | 3 | 3 |
| `TERMINATION` | 15 | 90 | 28 | 41 |

API 24 的 `LOAD` p50 为 59.5 ms，API 36 为 7.5 ms；这与 API 24/25 使用私有
`DexClassLoader` 落盘路径、API 26+ 使用 `InMemoryDexClassLoader` 的实现差异一致，但不能仅凭这 10 个
样本建立因果或做跨设备性能结论。

### 冷态描述性样本

| 设备 | Session | Worker startup | Share | Compile | D8 | Load | Run | Termination |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| API 24 | 2855 | 151 | 5.29% | 717 | 1027 | 155 | 3 | 31 |
| API 36 | 1458 | 299 | 20.51% | 518 | 363 | 8 | 2 | 14 |

每台设备只有一个 compiler `COLD` 样本，它只用于确认冷/暖分组和展示首次执行量级，不用于统计推断。

### 逐次会话/worker 启动

| 序号 | Compiler | API 24 session / startup / share | API 36 session / startup / share |
| ---: | --- | ---: | ---: |
| 1 | `COLD` | 2855 / 151 / 5.29% | 1458 / 299 / 20.51% |
| 2 | `WARM` | 1054 / 59 / 5.60% | 1147 / 373 / 32.52% |
| 3 | `WARM` | 863 / 34 / 3.94% | 1105 / 404 / 36.56% |
| 4 | `WARM` | 950 / 93 / 9.79% | 1368 / 661 / 48.32% |
| 5 | `WARM` | 717 / 30 / 4.18% | 1117 / 444 / 39.75% |
| 6 | `WARM` | 759 / 45 / 5.93% | 1580 / 483 / 30.57% |
| 7 | `WARM` | 916 / 56 / 6.11% | 1013 / 394 / 38.89% |
| 8 | `WARM` | 1734 / 177 / 10.21% | 1701 / 729 / 42.86% |
| 9 | `WARM` | 909 / 48 / 5.28% | 1104 / 382 / 34.60% |
| 10 | `WARM` | 1148 / 46 / 4.01% | 1174 / 386 / 32.88% |
| 11 | `WARM` | 1317 / 65 / 4.94% | 1732 / 617 / 35.62% |

## 原始证据

| 设备 | JSONL | 行数 | SHA-256 |
| --- | --- | ---: | --- |
| API 24 | [`2026-08-25-api24.jsonl`](perf-baseline-data/2026-08-25-api24.jsonl) | 11 | `ce561648aaec29b6e517da9c0160b87c5a45a69b53d00114aa066da2ed57acce` |
| API 36 | [`2026-08-25-api36.jsonl`](perf-baseline-data/2026-08-25-api36.jsonl) | 11 | `2a9bd7e2a95fc964ed9eca4e1592d81823fd217b1c0a0ceb20d22e52b2d8cae6` |

文件是设备上 schema-v1 JSONL 的逐行副本，没有重新序列化。字段定义、隐私边界、文件轮转规则和 ADB
导出方式见 [`local-observability.zh-CN.md`](local-observability.zh-CN.md)。

## M8-1 前后对比契约

M8-1 应在相同 AVD、相同 AutoJs6 APK、相同 Java 输入和相同 Debug/cache-disabled 条件下重新采集一组
“1 cold + 10 warm”数据，并同时保留原始 JSONL。比较时：

1. 以 API 36 暖态 `sessionElapsedMillis` p50/p90 为主要性能指标；继续报告 worker 启动时长与逐行占比，
   但不能把被并行隐藏的启动时间从该字段本身直接推断出来。
2. 将“摊销掉大部分 worker 冷启动”量化为：API 36 暖态 session p50 至少减少 213 ms，即超过本基线
   worker 启动 p50（424 ms）的一半；同时 p90 不应回归。
3. API 24 以功能正确、一次性 worker 退出、pid/uid pin、取消/超时/看门狗语义不变为主，并报告
   p50/p90 是否回归；不把其 52 ms 的低启动中位数作为主要收益来源。
4. 若宿主机负载或 AVD snapshot 改变，应把新数据标为新的环境基线，不能与本文件直接计算百分比。

样本量较小且环境没有做实验室级隔离；这个阈值是工程验收线，不是统计显著性声明。若结果靠近阈值，
应扩大样本量并交错运行旧/新实现，再决定是否接受性能结论。

M8-1 已按该契约完成候选与串行控制的交错测量。收益不能稳定复现，最终候选区块未通过 p50/p90 门槛，
因此保持串行 worker 绑定；完整 no-go 证据见
[`worker-prewarm-evaluation.zh-CN.md`](worker-prewarm-evaluation.zh-CN.md)。
