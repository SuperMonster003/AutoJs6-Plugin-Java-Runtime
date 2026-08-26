# M9-8 JVM Source 会话并发模型评估

## 决策

**No-go：本里程碑不把 Protocol V1 的 `MAX_CONCURRENT_SESSIONS=1` 放开为大于 1，也不在
`RemoteJvmSourceHost` 内加入透明自动排队或轮询重试。继续保留“一个 Provider 进程只接受一个活动会话，
第二个会话立即返回可重试 `BUSY`”的 fail-fast 语义。**

本次评估锁定 Java Provider 运行时代码
`00d7ab245cbadeb4acedf845f305f2653b82d6ff` 与 AutoJs6 Host
`5c3f6f38e1fd1595b4ca576c6647854ec0204ae0`。结论保留 Protocol 1.6、schema 1.3、Entry API 4、
Provider `0.8.0-m9` 及现有 AAR 锁；没有 wire、AIDL、运行时代码或 APK 行为变更。

当前单槽不是一个可以独立调大的性能常量，而是下列安全和资源边界的共同前提：

- compiler 进程只有一个 process-global session gate、一个单线程编译 executor 和一个有界串行回调 lane；
- compilation cache 只有一个无等待队列的 I/O operation lane；
- compiler/worker 的进程级观测只保存一个 last-snapshot 槽，冷暖状态和累计计数也按串行完成解释；
- manifest 只声明一个 `:worker` 进程，该进程只允许领取一次任务，任何终态都会杀死自己；
- compiler 卡死的最终 containment 是杀死整个 `:compiler` 进程，而不是只杀一个会话。

只把 gate 改成计数器会同时破坏这些假设：多个已接纳会话仍会在编译线程中排队并消耗各自 deadline，
缓存操作会互相拒绝，回调会争用同一 64 项队列，观测会互相覆盖，而任一 worker 或 compiler 的 hard kill
都可能误杀其他会话。它不是“并发度从 1 改成 2”，而是新的进程池、配额、调度、公平性和故障隔离架构。

Host 侧排队比 Provider 真并发更接近可行，但当前也不能安全地局部补丁化。AutoJs6 的 engine supplier
会为每次 JVM 脚本新建 `RemoteJvmScriptEngine` 和 `RemoteJvmSourceHost`；现有
`JvmSourceHostExecutionGate` 因而只约束单个 engine/host 实例，并非 Host 进程级队列。把 FIFO 放进
该类会产生多个互不知情的队列，最终仍在 Provider gate 相撞。正确实现必须新增 Host 进程级、按精确
Provider 身份分区的协调器，并联动总 deadline、排队取消、`stopAll()`、身份漂移和外部 Host 竞争。

因此短期产品语义仍是显式 `BUSY`：交互调用向用户显示稳定错误码与“稍后重试”；不得在同一次调用里
自旋、固定间隔轮询或依据泛化的 `retryable=true` 自动重放任意 Java 入口。只有精确 `BUSY` 能证明本次
请求未进入 ECJ/D8/worker；`TIMEOUT`、transport loss、`WORKER_DIED` 等结果可能发生在用户代码已产生
副作用之后，不能自动重试。

## 当前单槽链路

### Protocol 与能力协商

共享 `JvmSourceContract.MAX_CONCURRENT_SESSIONS` 固定为 1。Provider 在 capabilities 中原样广告该值；
Host 和 Provider 共用的 `JvmSourceValidation.validateCapabilities` 要求它**精确等于** 1，并明确报错
“Protocol v1 requires exactly one active session per provider process”。现有畸形/边界测试也验证值 2
会被拒绝。

capability wire 已有 `maxConcurrentSessions` 字段，不表示旧 reader 已支持更大值。若未来放开，至少需要：

1. 新的协商协议修订，使旧 Host 与新 Provider 没有错误的兼容交集，并让旧 reader fail closed；
2. 将“精确为 1”改成双方有上限的协商规则，冻结 admission、terminal 与 `BUSY` 的语义；
3. 补齐超过上限、公平性、取消、身份漂移和部分进程死亡的 conformance cases；
4. 更新 Host AAR、Provider pinned input、能力文档和设备矩阵。

AIDL 已经为每次 `openSession` 返回独立 session handle，形状上不一定必须增加方法；但进程所有权、回调
背压和终态隔离语义都必须升级，不能因为 AIDL 能创建多个对象就推断运行时支持并发。

### Provider admission 与 compiler

`JavaSourceCompilerService` 使用 process-global `SingleActiveSessionGate<RemoteJavaSourceSession>`：

1. `openSession` 先完成 caller/request 校验和 FD duplication；
2. gate 的 `compareAndSet(null, session)` 成功才调用 `session.start()`；
3. 失败会走 `rejectBusy()`，返回 `BUSY / NEGOTIATION / retryable=true`，不进入编译或 worker；
4. 成功会话只有在 worker/FD/workspace/observation 清理、terminal retirement barrier 完成后，才精确
   release 自己持有的 gate；随后才向 Host 投递外部终态。

这一顺序保证 Host 收到可信 terminal 时，下一会话已经可以安全 admission；晚到或错误 session 不能释放
当前 owner。它也是 Host timeout/quarantine 可以把 terminal 当作 R2 retirement acknowledgement 的依据。

compiler executor 目前只有一个线程。若仅允许 gate 接纳多个 session，它们会在 executor 内形成一个不可见
队列，但各 session 的 Provider watchdog 和 Host deadline 已经开始计时，后排任务可能在真正编译前耗尽
预算。若改为多线程，则 ECJ、D8、内存峰值、临时工件与缓存发布都会并发，必须先建立进程级资源配额和
压力验收。

更严重的是，compiler cancellation 的最终边界是：合作式 interrupt 经过 2 秒 grace 后仍有关键工作，
就 `killProcess` 当前 `:compiler`。单会话时这能确定性清除卡死的 ECJ/D8；多会话时，一个不响应中断的
任务会让其他健康会话一起失去 Binder。没有独立 compiler containment，就不能声称会话之间隔离。

### Compilation cache

`CompilationCacheOperationLane` 明确只有一个 daemon thread、一个 admitted operation，且**没有 pending
queue**。重叠调用直接返回 `REJECTED`；I/O timeout 会 poison lane，只有在旧线程和任务都停止且 cooldown
结束后允许一次恢复，第二次 poison 为永久不可用。

单会话把 cache lookup/materialize/publish 自然串行化。放开会话后会出现：

- 一个会话的 lookup 因另一会话的 publish 占槽而被拒，退化成不稳定的 cache miss；
- 相同 key 的并发 miss 重复执行 ECJ/D8，随后竞争发布；
- 一个卡死 I/O poison 全进程 cache lane，影响所有已接纳会话；
- 累计 hit/miss 仍是线程安全计数，但快照无法解释某次拒绝到底是内容 miss 还是并发调度结果。

可接受的并发设计至少要有独立于 session admission 的有界公平 cache 调度、同 key single-flight、原子发布、
poison generation 隔离和明确的“cache 不可用是否消耗会话 deadline”规则。简单把现有 lane 换成线程池会
失去“不重叠 abandoned I/O”的安全性质。

### Observation 与 callback

每个 `RemoteJavaSourceSession` 内的 observation builder 有同步保护，Protocol 1.5+ 的 terminal
observation 也绑定 request ID，因此单个终态对象本身可以区分 session；问题在进程级辅助设施：

- `JavaProviderObservationRegistry` 只有 `lastCompiler`、`lastWorker` 与一个
  `compilerCompleted` 标志，后完成的会话会覆盖先完成者；
- compiler `WARM` 目前表示“同一 compiler 进程已有一个完成会话”，并发 start 时无法稳定区分；
- Debug JSONL exporter 通过 `@Synchronized` 可以保证单行写入不交错，但记录顺序将只是完成顺序；
- cache telemetry 使用原子累计计数，线程安全不等于能把并发增量准确归因到单个 terminal。

共享 `SerialCallbackLane` 同样只有一个线程和 64 项队列。多个会话的 started/diagnostic/terminal 会在同一
队列竞争；一个诊断密集会话可以令另一个会话的终态 dispatch 被拒，形成跨会话故障。真并发需要每会话
有界 ordering lane，或具备逐会话配额与 terminal 优先级的公平 multiplexer，并证明 callback death 不会
连带终止无关会话。

### Disposable worker 进程

manifest 只注册一个 `JavaExecutionWorkerService`，固定运行在 `:worker`。Android 对同一 component 的
并发绑定仍落到同一个 service/process 实例。该实例包含：

- `SingleUseWorkerLifecycle`：`FRESH -> RUNNING -> TERMINAL/RETIRED`，没有等待队列；
- 一个 `active` task 原子槽和一个单线程 executor；
- 一个被固定到当前 compiler PID/UID 的 caller identity；
- 任意 task terminal、invalid admission、callback death 或 hard terminate 都杀死当前 worker 进程。

第二个 session 若在第一个 worker 存活时 dispatch，不会获得“第二个 disposable worker”；它会命中同一个
single-use 实例，拒绝 admission，并可能触发该进程退出，连带破坏第一个 session。保持一次性 worker
隔离的可行方向是预声明并管理多个独立 process/component 槽，或建立能为每个 session 分配独立 OS 进程
的 broker；在同一个 `:worker` 内改成线程池不满足“每次执行后杀进程清除用户静态状态”的威胁模型。

### Host admission、retirement 与真实并发入口

`RemoteJvmSourceHost.execute` 的本地 gate 是零队列、单 owner：本实例重叠调用立即映射成稳定
`JVM_SOURCE_PROVIDER_BUSY`、`RETRY_LATER`、`retryable=true`。在 timeout、取消或协议违规后，owner 会
一直持有该 gate，直到可信 Provider terminal、transport death 或有界 quarantine 完成；因此新的调用
不会与尚未 retirement 的旧 session 重叠。

但 AutoJs6 当前在 `AbstractAutoJs` 注册的是 engine supplier。`ScriptEngineManager.createEngine` 每次调用
supplier，而非复用单例；非 JavaScript execution 又各自在独立 `ThreadCompat` 线程运行。所以两个并发
`.java` 脚本通常拥有两个不同的 `RemoteJvmSourceHost` 和两个本地 gate，最终由 Provider 的
process-global gate 判定第二个 `BUSY`。

Provider 返回给第二个请求的 `BUSY` terminal 只证明**被拒绝的第二个 session object**已清理；它不是第一个
活动 session 已结束的通知。收到 `BUSY` 后立即重试，很可能只会再次收到 `BUSY`。Host 也无法仅凭这个
terminal 推算另一个应用/进程何时释放 Provider。

## 候选方案

| 候选 | 能解决的问题 | 关键缺口 | 本次结论 |
| --- | --- | --- | --- |
| A. 保留 fail-fast 单槽 | 故障隔离明确；延迟与副作用边界可见；现有 terminal retirement 可证明 | 并发调用需要用户稍后重试 | **接受，作为当前正式语义** |
| B. 在每个 `RemoteJvmSourceHost` 内自动 FIFO | 局部代码量小 | host 实例并不共享；产生多个假队列；deadline/stop/身份漂移未定义 | **No-go** |
| C. 新增 Host 进程级、按 Provider 身份分区的有界协调器 | 可在不放开 Provider 的情况下串行本进程请求；保留一次性 worker 隔离 | 需重构 enqueue deadline、取消、`stopAll()`、公平性和外部竞争；尚无需求/延迟数据 | **未来首选探索方向，本次不实现** |
| D. 仅把 Provider gate 改成 N permits | 表面上可同时持有多个 session | compiler/cache/callback 仍串行或互相拒绝；worker/hard-kill 跨会话 | **No-go** |
| E. Provider 独立 compiler/worker process pool | 可实现真正并发和逐会话 hard containment | 新协议、固定进程池/调度器、聚合配额、cache single-flight、设备压力矩阵，成本最高 | **远期条件式方向** |

## 短期 BUSY 与重试指引

以下规则在不新增 Host 队列的前提下立即适用：

1. **交互入口不自动重放。** 收到 `JVM_SOURCE_PROVIDER_BUSY` 时显示稳定消息和
   `RETRY_LATER`，由用户在正在运行的 JVM 脚本结束后重新发起。
2. **只识别精确 `BUSY`。** 不得把所有 `retryable=true` 都转换成自动重试；任意 Java 入口可能启动
   Activity、写剪贴板、显示 toast 或产生其他外部副作用。
3. **不得紧循环或固定频率轮询。** `BUSY` terminal 不携带活动 owner 的完成信号、剩余时间或队列位置；
   轮询只会重复 discovery/bind/FD/session 开销并制造惊群。
4. **调用方若拥有当前活动任务，可等待其可信结束事件。** 只有同一上层 orchestrator 同时拥有活动任务
   和待执行任务时，才可在前者 terminal/transport retirement 后重新发起；不得用 sleep 猜测释放时间。
5. **外部竞争直接返回 BUSY。** 若活动 owner 来自另一个 Host 进程或应用，本 Host 没有可认证的 release
   event；不应在后台悄悄等待或无限重试。
6. **取消优先。** 用户 stop、`stopAll()` 或 execution 销毁后，不得再因先前的 BUSY 触发新 dispatch。
7. **保留原始总预算。** 未来若实现显式排队，30 秒 Host 总 deadline 必须从 enqueue 时开始；排队等待
   消耗同一预算，过期以 `NOT_DISPATCHED` 收口，不能 dequeue 后重新获得完整 30 秒。

若后续对 Host 进程级协调器做 spike，首版至少满足以下门槛：

- 以经选择的 exact Provider component/版本/签名身份为 key；dequeue 前重新发现并重验身份；
- 每个 key 为严格 FIFO，`1 active + 最多 1 pending`，超出立即 `BUSY`，不允许无界内存队列；
- queued/dispatching/active/retiring/terminal 状态原子转换，queued cancel 必须从队列删除且只终态一次；
- 当前活动项在 trusted terminal/transport death/有界 quarantine 前不释放调度 permit；
- deadline 从 enqueue 开始，排队超时绝不调用 `openSession`；
- 本地 coordinator 释放后若仍收到外部 `BUSY`，直接返回用户，不做内部二次循环；
- 记录 queue wait、reject、cancel、外部 BUSY 与 retirement wait，且不导出源码、参数或身份敏感信息；
- 覆盖多 engine 竞争、FIFO、队首取消、`stopAll()`、provider 更新/卸载、Host close、timeout quarantine、
  外部 BUSY 和晚到 terminal。

这些是未来实现的最小安全条件，不表示 M9-8 已授权或暗中启用了队列。

## Provider 真并发的重新开启条件

只有同时具备以下证据，才重新讨论 `maxConcurrentSessions > 1`：

1. 真实并发工作负载证明 fail-fast 或 Host 有界串行造成不可接受的用户延迟，并给出到达率、执行时间
   p50/p90/p99、BUSY 比例、取消比例和设备内存等级；
2. 先完成 Host 进程级有界协调器实验，证明仅串行排队仍不能满足需求；
3. 新协议修订定义协商上限、admission、公平性、每会话 terminal、部分失败、aggregate quota 和旧版本
   fail-closed 行为；
4. 每个活动 session 获得独立 compiler containment 与独立 disposable worker process；一个会话的
   timeout/hard kill 不得终止其他会话；
5. callback 有逐会话配额/ordering/terminal 优先级，cache 有公平调度、同 key single-flight 与 poison
   generation 隔离，observation 有 request-scoped registry；
6. Host quarantine、Provider shutdown、package update、Binder death 与系统 low-memory kill 都有明确
   的逐会话/全进程结果矩阵；
7. 总量限制不再只是“每会话上限 × N”：为 RSS、FD、临时文件、ECJ/D8 并行度、输出与 CPU 建立
   process-global admission budget；
8. API 24 与当前 target/API 的真机压力测试覆盖同时编译、同时执行、相同/不同 cache key、诊断洪泛、
   队首取消、一个 compiler 卡死、一个 worker 死亡、Host 死亡和连续进程回收，并证明无跨会话误杀、
   无 FD/目录泄漏且 p90/p99 不劣于有界串行方案。

在这些条件满足前，单会话是当前隔离模型的一部分，不作为可调吞吐参数。

## 验证范围

本项是 no-go 评估，没有运行时代码、协议 AAR 或 APK 变化，因此不制造一个通过修改常量得到的“伪并发”
设备结果。收口验证覆盖：

- Provider `SingleActiveSessionGateTest`、cache lane、observation registry、single-use worker 与 terminal
  retirement barrier 的定向测试；
- AutoJs6 Host `JvmSourceHostExecutionGateTest`、BUSY 用户投影，以及 timeout/protocol violation 后持续
  占槽直到 retirement 的定向测试；
- Provider Debug/Release 全量单测、pinned-input、Debug lint 与 Debug APK gate；
- 文档链接、`git diff --check` 和双仓工作树边界。

2026-08-26 最终结果：

- Provider 单槽链聚焦测试 7 个 suite、28/28（gate、cache lane、observation、JSONL export、resource /
  terminal barrier、single-use worker），零失败/错误/跳过；
- AutoJs6 Host 聚焦测试 42/42（`JvmSourceHostExecutionGateTest`、
  `JvmSourceUserExperiencePolicyTest`、`RemoteJvmSourceHostTest`），零失败/错误/跳过；
- shared API `JvmSourceMalformedAndLimitsTest` 强制重跑 16/16，包含
  `maxConcurrentSessions=2` 必须拒绝的边界；
- Provider Debug/Release 全量强制重跑各 163/163、各 44 个 suite，零失败/错误/跳过；
- `scripts/verify.ps1 --no-daemon --console=plain` 通过：pinned-input、双 variant 单测、Debug lint 与
  Debug APK gate 全绿；
- 变更文档的相对链接全部解析到现存文件，`git diff --check` 通过。

Provider 只修改文档与 Roadmap，没有刷新 AAR 或生成新的设备结论。M9-7 Host 隔离 worktree 的 tracked
文件保持 clean；原始 AutoJs6 工作树未被本评估读取以外的操作修改。Provider 的构建输出均位于既有
ignored `build/` 目录，不属于提交。
