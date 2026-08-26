# M9-6 JVM Source 超时上限提升评估

## 决策

**No-go：本里程碑不提高 `MAX_TIMEOUT_MILLIS=120_000`，不把正式宿主默认值从 30 秒调大，
也不在现有有界会话上增加可无限续约的 keep-alive。**

本次评估锁定 Provider 运行时代码 `ead53e35ecbc0dbc024bfc85ce5718bb4df22627` 与 AutoJs6 Host
`1b79603bc7304ae44fe878b27ef01d2d77b2a963`。结论保留 Protocol 1.6、Provider `0.8.0-m9`、
Host version code 5281 以及现有 AAR 锁；没有 wire、AIDL、看门狗或 APK 运行时代码变更。

直接调大常量不能解决正式路径的限制。AutoJs6 当前在源码快照、重新发现、绑定和握手之前就创建一个
30 秒总截止时间，请求中的 `timeoutMillis` 也固定取 `min(30 s, provider max)`。Provider 直到
`openSession` 成功后才启动自己的单次请求看门狗。因此正式路径的有效预算是**包含 Host 前置工作的
30 秒总预算**，而不是 120 秒；120 秒只是双方验证允许的协议安全上限。

更重要的是，当前 JVM Source 是三个进程之间的有界、一次性执行：Host 绑定 Provider `:compiler`，
compiler 再绑定一次性 `:worker`。两项 Provider 服务均不是前台服务，Host 的通用前台服务也不是每个
JVM 会话必须持有的执行授权。把静态上限调大或让 compiler 自行续约，会让不受信任的死循环长期占用
唯一会话槽、CPU、内存和电量，却不会增加“仍在取得进展”的可信证据。

未来若确有长任务需求，应新增与有界执行明确分离的 `LONG_RUNNING` 模式：只能由前台用户动作授权，
由 Host 持有会话专属前台服务和可见停止动作，Host/provider/worker 各自维护不可由单一对端无限延长的
单调时钟租约，并保留绝对运行上限。它是跨 Host、协议和 Provider 的新能力，不是本次常量调整。

## 当前实际超时链

一次正式执行同时受以下边界约束：

| 层 | 当前行为 | 时钟起点 | 终止/清理 |
| --- | --- | --- | --- |
| Host 总截止时间 | `DEFAULT_TIMEOUT_MILLIS = 30_000` | 选择 provider 后、源码快照前 | 超时后请求 provider 取消并保持 Host execution gate |
| wire 请求 | `min(30 s, capabilities.maxTimeoutMillis)` | 请求构造时冻结 | Provider 独立验证 `1..120 s` |
| Provider 会话看门狗 | 单次 `schedule(timeoutMillis)`，不可续约 | `openSession` 后会话 `start()` | 进入 `TIMEOUT` 取消状态 |
| Host terminal 等待 | 请求预算与 Host 剩余总预算的较小值 | dispatch 后 | 先取消，再等待可信 retirement |
| Host quarantine | 最多 3 秒 | 超时/取消后 | terminal、Binder death 或超限后释放隔离等待 |
| Provider worker 取消 | 500 ms cooperative grace | 取消发出后 | 未退出则杀死一次性 worker |
| Provider worker retirement ack | 500 ms | worker 终态后 | ack 缺失按 worker 失败收口 |
| Provider compiler 取消 | 2 秒 grace | compiler thread 被 interrupt 后 | 仍有关键工作则杀死 compiler 进程 |
| Host 输出 drain | 最多 2 秒，且正常路径不超过总预算剩余值 | terminal 后 | 关闭 descriptor/capture |

这套双侧截止时间有意让任何一方都不能单独绕过安全边界。Host 先到期时会请求隔离；Provider 先到期时
必须返回 `TIMEOUT` 取消终态。3 秒、500 ms、2 秒等值是**终止与隔离预算**，不是执行租约，不能随
keep-alive 一起延长。

`MAX_TIMEOUT_MILLIS` 还不是孤立常量。当前 observation 的 session 上限是 `MAX_TIMEOUT + 60 s`，
phase 上限是 `MAX_TIMEOUT + 10 s`；Provider 私有 observation 校验、Host terminal 等待、host-call
timeout、测试 fixture 和设备取证等待窗口都依赖同一假设。任何 go 方案必须联动重审这些边界，不能只
修改能力广告。

## 候选方案

| 候选 | 优点 | 关键缺口 | 结论 |
| --- | --- | --- | --- |
| A. 仅把静态上限提高到数分钟 | wire 变化最少，现有单次看门狗容易调整 | 正式 Host 仍固定 30 秒；后台存活、用户可见性、功耗和唯一会话占用均未解决 | No-go |
| B. 在现有会话增加滑动 keep-alive | 理论上可承载未知时长 | “进程活着”不等于 ECJ/D8/用户代码有进展；可无限续约的非可信计算形成拒绝服务；双侧截止时间会出现晚到/重复/终态竞争 | No-go |
| C. 新增显式 `LONG_RUNNING` 模式 | 可把用户授权、通知、停止和租约语义与普通执行隔离 | 需要 Host FGS、协议字段、三进程 liveness、绝对上限、设备矩阵和发布政策评审；已超出常量调整 | 未来唯一可接受方向 |
| D. 改为 WorkManager 持久任务 | 平台能够调度、重试并遵守功耗约束 | 当前会话依赖实时 Binder、FD、流式输出和不可序列化的用户代码状态，进程重建后无法断点恢复 | 不适合当前执行模型 |

### 为什么 keep-alive 不能直接复用普通心跳

JVM Source 至少包含三种不同信号：

1. Host 主进程及会话专属前台服务仍存活；
2. Provider compiler 进程及其调度线程仍存活；
3. 一次性 worker 仍存活，且尚未失去与 compiler 的 Binder 关系。

compiler 的独立定时线程即使在 ECJ 或 D8 卡死时也能继续发心跳；worker 的独立定时线程即使用户入口
陷入无限循环时也能继续发心跳。两者都只能证明 liveness，不能证明 progress。反过来，如果要求用户
执行线程主动心跳，合法的长时间计算又可能没有安全插点。故心跳可以缩短“进程已丢失”的检测时间，
但不能成为取消绝对运行上限的依据。

现有身份链也要求新增信号继续 fail closed：Host 只接受锁定 Provider UID/PID 的回调，compiler 只接受
当前 worker generation 的回调，晚到信号不得复活已取消或已终态的会话。单纯重排一次
`ScheduledFuture` 无法覆盖这些要求。

## Android 生命周期与前台服务约束

### 当前绑定模型

- Host 使用 application context 和 `BIND_AUTO_CREATE` 绑定外部 compiler service；compiler 同样用
  `BIND_AUTO_CREATE` 绑定内部 worker service。
- bound service 通常只在客户端保持绑定时存活。Android 会依据客户端重要性提高服务进程优先级，
  但进程生命周期最终仍由系统按内存和用户相关性决定，不能把 Binder 连接视为完成保证。
- Provider manifest 没有 JVM 执行专属前台服务。Host 的可选通用 `AppForegroundService` 不是本协议
  的前置条件，也没有每会话 token、租约或停止确认，不能被看门狗当作可信授权。

Android 官方说明，前台服务应只用于用户可感知、希望立即且不中断的工作，并持续显示通知；Android 12
及以上通常禁止应用从后台启动前台服务，违规会抛出
`ForegroundServiceStartNotAllowedException`。Android 14 及以上还要求声明匹配的 service type 和
权限；`specialUse` 需要在 manifest 解释具体用途，并会在发布时接受用例审查。

因此，若未来提供长任务，Host 必须在仍有 live Activity 的用户动作中先启动会话专属 FGS，确认通知和
停止动作有效后才 dispatch。后台脚本、隐式重试、provider 自行提权或“先执行、稍后补 FGS”都应失败
关闭。前台服务只能提高进程重要性；系统在资源紧张时仍可能杀进程，所以 Host/provider/worker 的
Binder death 和独立清理仍不可删除。

官方参考：

- [Foreground services overview](https://developer.android.com/develop/background-work/services/fgs)
- [Restrictions on starting a foreground service from the background](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)
- [Foreground service types / special use](https://developer.android.com/develop/background-work/services/fgs/service-types#special-use)
- [Processes and app lifecycle](https://developer.android.com/guide/components/activities/process-lifecycle)
- [Bound services overview](https://developer.android.com/develop/background-work/services/bound-services)

### ANR

当前 ECJ、D8 和用户入口不在 Android service 主线程执行，因而把会话从 30 秒改成更长不会直接等价为
主线程 ANR；这一隔离必须保留。风险集中在新的 FGS 生命周期和终止路径：`onCreate`、
`onStartCommand`、通知更新、`onTimeout`/`onDestroy` 等回调仍运行在 service 主线程，必须快速返回；
达到平台或应用租约后也必须及时 `stopSelf()`。任何心跳验证、Binder 等待、编译或 worker retirement
都不能搬进这些回调。

Android 对不同 service 类型和目标版本还可能施加独立的 FGS 时限。Android 15 的 6 小时/24 小时
配额当前针对 `dataSync` 与 `mediaProcessing`，`shortService` 更短；这不应被解释为 `specialUse`
可以不受应用自身绝对上限约束。平台规则变化时仍需重新核对目标 SDK 行为。

官方参考：

- [Services overview](https://developer.android.com/develop/background-work/services)
- [Diagnose and fix ANRs](https://developer.android.com/topic/performance/anrs/diagnose-and-fix-anrs)
- [Foreground service timeouts](https://developer.android.com/develop/background-work/services/fgs/timeout)

### 功耗、Doze 与任务调度

长时间 ECJ/D8 和任意用户循环是持续 CPU/内存负载。当前源码、DEX、输出、诊断和临时目录上限可以
约束字节数，却不能约束 CPU 时间、热节流或电量。前台服务让用户看到资源消耗，并可避免应用因普通
standby 条件立即失去执行机会，但官方明确不应只为了阻止 idle 而启动 FGS。

WorkManager 适合需要跨应用退出或重启可靠恢复的持久任务，也会遵守 Doze/配额；当前 JVM 会话不是
可恢复任务。若未来能把编译输入和结果改造成幂等、可检查点的离线 job，编译阶段可另行评估
WorkManager；任意 Java 入口的实时 Binder 会话仍需独立设计。

官方参考：

- [Optimize for Doze and App Standby](https://developer.android.com/training/monitoring-device-state/doze-standby)
- [Task scheduling](https://developer.android.com/develop/background-work/background-tasks/persistent)
- [Support for long-running workers](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running)

## 与 AutoJs6 Python 长任务先例的关系

AutoJs6 已有 Python `LONG_RUNNING` 先例，但它证明的是需要完整模式，而不是 JVM 可以只加心跳：

- 协议用独立 `executionMode=LONG_RUNNING`，并要求 `timeoutMillis=0` 与 capability 明确支持；
- Host 只从 live foreground Activity 铸造 opaque grant；
- Host 先启动专属 `specialUse` FGS，显示 ongoing notification 与 stop action，且使用
  `START_NOT_STICKY`；
- provider start lease 为 120 秒，heartbeat interval/lease 为 15/45 秒；Host FGS 另有 15 秒独立
  pulse lease，provider 心跳不能续 FGS lease，FGS pulse 也不能续 provider lease；
- 任一 lease、FGS stop 或 provider/transport 失败都会 fail closed。

JVM 路径还多出 compiler 与一次性 worker 的两级关系、ECJ/D8 不透明阶段、worker generation 和
硬杀隔离。未来可以复用 Python 的**授权与独立租约原则**，不能直接复用其协议字段或把 compiler 心跳
当作用户代码进展。

## 安全与资源结论

1. **拒绝服务边界**：用户源码是不可信计算；可续约会话若无绝对上限，可以永久占用
   `MAX_CONCURRENT_SESSIONS=1` 的 provider 与 Host execution gate。
2. **取消优先级**：当前第一个 timeout/cancel 原因胜出，晚到 terminal 不得覆盖取消。续约会增加
   renew-vs-timeout-vs-terminal 竞争，必须有序列号、epoch 和冻结点，且过期续约绝不能复活会话。
3. **进程隔离**：Host death、callback death、compiler death、worker death 目前都会触发取消或硬杀。
   FGS 只能加强 Host 生命周期，不能替代任一 death recipient。
4. **不可中断阶段**：线程 interrupt 只能合作式打断；ECJ/D8 若不返回，2 秒后杀 compiler 是最终边界。
   更长执行不应把该 2 秒 containment grace 一并拉长。
5. **资源计费**：源码和输出字节有界不等于 CPU/电量有界。未来至少需要绝对 wall-clock 上限、每会话
   用户可见性，以及 thermal/battery/device-state 证据；不能让 provider 广告值单方面决定预算。
6. **并发联动**：长任务与 M9-8 会话并发策略直接耦合。在排队、公平性、取消队首和 provider 单槽
   语义未决前，不应让一个会话把单槽生命周期从秒级扩大到未知时长。

## 未来 go 所需的最小设计

只有同时满足以下条件，才重新开启实现：

1. 有可复现的真实任务数据证明 30 秒不足，并分别给出编译、D8 和用户执行阶段分布；先区分“需要
   31–120 秒的有界配置”与“确实需要长任务模式”。
2. Host 提供只能由 live foreground user gesture 铸造、不可序列化伪造的一次性授权，并在 dispatch
   前启动 execution-scoped FGS；通知包含任务身份、运行时长和可靠 stop action。
3. 协议用新 minor capability 显式协商 `BOUNDED`/`LONG_RUNNING`，冻结 start lease、heartbeat
   interval/lease、绝对最长 wall-clock 和终止 grace；旧 reader/writer 必须 fail closed。
4. Host 与 Provider 使用各自单调时钟维护独立租约。心跳至少携带 request ID、严格递增 sequence、
   execution phase 和 worker generation；重复、乱序、晚到、错误 UID/PID 或 terminal 后心跳均拒绝。
5. compiler 与 worker 各有独立 liveness/watchdog，Host FGS pulse、provider heartbeat、worker
   heartbeat 互不续约；任何一层丢失都会取消并走现有 hard-stop/quarantine 路径。
6. 即使心跳持续也保留不可续约的绝对上限；上限由 Host policy 取 provider capability 的较小值，不能
   由用户代码或 Provider 自行扩大。
7. 联动更新 request/capability/callback wire、AIDL、Host `Deadline` 分层、Provider scheduler、
   observation duration、host-call budget、错误码/UX、M9-8 单槽公平性与所有边界测试。
8. 设备验收至少覆盖 API 24 与当前 target/API：前台启动成功和后台拒绝、通知 stop、锁屏/息屏、
   Doze/待机、Host/compiler/worker 分别被杀、心跳丢失/乱序/晚到、绝对上限、ECJ/D8 卡住、无限循环、
   取消后的三进程与 FD/临时目录清理，并记录功耗和 thermal 状态。

在这些条件满足前，`DEFAULT_TIMEOUT_MILLIS=30_000` 与 `MAX_TIMEOUT_MILLIS=120_000` 是当前威胁模型的
一部分，不作为简单的性能配置项放开。

## 本次验证范围

本结论没有运行时代码、协议 AAR 或 APK 变化，因此不重复制造“长任务成功”的设备证据；那会与 no-go
决策冲突。收口验证包括双仓静态链路审计、Android 官方约束核对、Provider Debug/Release 全量单测、
lint 与 Debug APK 构建。既有取消、timeout 优先级、worker hard-stop、compiler shutdown、Binder death
和 Host quarantine 测试继续作为未变更运行时的回归证据。

2026-08-26 最终强制重跑结果为 Provider Debug 163/163、Release 163/163（各 44 个 suite，0 failure、
0 error、0 skipped）；`verify.ps1 --no-daemon` 的 pinned-input、Debug lint 与 Debug APK gate 同样通过。
变更文档的全部相对链接均解析到现存文件，`git diff --check` 通过。Host M9-5 隔离工作树保持 clean，
原始 AutoJs6 工作树未被本评估修改。
