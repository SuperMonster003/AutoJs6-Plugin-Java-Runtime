# Roadmap — AutoJs6 Java Runtime Plugin

> 基于 `0.3.0-m5` (VERSION_BUILD=3, Protocol 1.1, Entry API 2) 现状制定, 探查日期 2026-08-24。
> 当前形态 = R1 能力 profile + R2 落盘规则 + R3 缓存/观测 + R4 多 DEX 工件 + M5 能力集。
>
> **总体结论**: 插件在"安全与工程严谨度"维度已远超同类水准 (三进程隔离、双侧白名单、DEX 全量结构校验、
> HMAC 缓存、多层看门狗、140 个单测全绿、零 TODO)。主要的精进空间集中在两条线:
> **① 能力面与开发者体验** (宿主桥接仅 2 个方法、运行时诊断极简、Java 8 单源码文件);
> **② 工程可持续性** (设备证据矩阵、远端仓库/CI 与后续发布流程)。
> 扩展受 Protocol 1.1 冻结约束, 因此路线分为 **本仓独立可落地** 与 **需宿主协同 (协议升级)** 两轨推进。

## 标签图例

| 标签 | 含义 |
|---|---|
| `[本仓]` | 本仓库独立完成, 不改协议, 不需要宿主发版 |
| `[宿主]` | 需要与 AutoJs6 宿主协同: 协议/AAR 变更 + 刷新 `protocol/` 三件套与 lock + 提升 `REQUIRED_HOST_VERSION_CODE` |
| `[设备]` | 需要真机/模拟器证据 (单测无法覆盖) |
| `[网络]` | 需要外网下载依赖 — **集中批量执行, 其余条目全部可 `--offline` 完成** (当前网络易触发 Cloudflare 502/524/529) |
| `[评估]` | 先做时间盒调研, 产出 go/no-go 决策记录, 再决定是否实施 |

## 里程碑总览

| 里程碑 | 主题 | 建议版本 | 依赖 |
|---|---|---|---|
| M6 | 工程基线巩固 | 0.4.0-m6 | 无 |
| M7 | 诊断与可观测性 | 0.4.x-m7 | M6 |
| M8 | 运行时增强 (本仓独立) | 0.5.x-m8 | M7 (基线数据) |
| M9 | 协议协同能力扩展 | 0.6.x-m9 | 宿主协商 |
| M10 | 远期探索 | — | 按需 |

---

## M6 — 工程基线巩固 (全部 `[本仓]`)

- [x] **M6-1 提交构建体系迁移改动**
  - 内容: 当前工作区有 6 个已修改 + 2 个未跟踪文件 (`build-logic/`、`gradle/libs.versions.toml`、
    `settings.gradle.kts`、`app/build.gradle.kts`、`version.properties` 的 `VERSION_CODE→VERSION_BUILD` 等),
    主题是迁移到 `org.autojs.build.platform-versions` 共享 convention 插件体系。审阅后拆分成语义清晰的 commit 提交。
  - 验收: `git status` 干净; `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease --offline` 三连通过; `verifyPinnedInputs` 常绿。

- [x] **M6-2 修复 2 处 UseRequiresApi lint**
  - 内容: `D8JavaCompiler.kt:55` 与 `WorkerDexLoader.kt:113` 的 `@TargetApi(O)` 改为 `@RequiresApi(O)`, 使 API 约束传播到调用方。
  - 验收: `:app:lintDebug --offline` 报告中 UseRequiresApi 归零; 单测全绿。

- [x] **M6-3 依赖版本目录化**
  - 内容: `app/build.gradle.kts` 中硬编码的 ECJ 3.26.0 / D8 8.13.17 / desugar_jdk_libs_nio 2.1.5 / kotlin-stdlib 2.3.21
    坐标迁入 `gradle/libs.versions.toml` (目前该 toml 只服务 build-logic)。**保留** `verifyPinnedInputs` 的字面量断言双源模式 (刻意冗余)。
  - 验收: 离线构建产物等价; lock 校验行为不变。

- [x] **M6-4 建立 CHANGELOG 与版本 tag 基线**
  - 内容: 新建 `CHANGELOG.md`, 补记 m5 能力集与构建迁移; 打 tag `v0.3.0-m5` (仓库目前 0 个 tag)。
  - 验收: tag 存在; CHANGELOG 与 `version.properties` 版本一致。

- [x] **M6-5 本地一键离线验证脚本**
  - 内容: `scripts/verify.ps1` + `scripts/verify.sh`, 封装 `testDebugUnitTest + lintDebug + assembleDebug --offline`,
    作为 pre-push 级本地门禁 (替代暂不可用的远端 CI, 规避网络问题)。
  - 验收: 双脚本一次通过; README 引用。

- [x] **M6-6 用户面 API 文档 (中文优先)**
  - 内容: 新建 `docs/context-api.zh-CN.md`: `JvmScriptContext` 全部方法 (`app().launch` / `console().log|error` /
    `sleep` / `toast` / `cancellation()`)、源码形态规则 (单文件、入口 `Main`、Java 8、禁 `\uXXXX`、UTF-8)、
    上限表 (源码 4MB / stdout+stderr 各 1MB / 返回值 JSON 64KB / 超时默认 30s 上限 120s)、
    返回值支持类型表 (JSON profile: 基础类型/String-key Map/Iterable/数组; POJO 与枚举拒绝)、错误码语义。
  - 验收: 文档覆盖上述全部边界; README 添加链接与 zh-CN 摘要。

- [x] **M6-7 样例库扩充** `[设备]`
  - 内容: `samples/` 目前仅 1 个文件, 新增: 取消与 `sleep` 中断样例、各返回值类型样例、编译错误诊断观感样例、超限行为样例。
  - 验收: 每个样例在真机通过并在文档记录预期输出。

## M7 — 诊断与可观测性

- [x] **M7-1 诊断脱敏改为分段处理** `[本仓]`
  - 内容: `EcjDiagnosticSanitizer.kt:64-72` 目前一旦触发非编译单元路径的 redaction, **整条消息降级为固定 fallback 文本**,
    用户丢失全部原始诊断。改为逐段替换敏感片段、保留其余内容。
  - 验收: 新增单测覆盖混合内容消息的保留率; 现有"不泄露绝对路径/摘要/uid/pid/Binder"测试继续通过。

- [x] **M7-2 单次回传多条编译诊断** `[本仓/已确认]`
  - 内容: 确认 wire 诊断格式是否允许在 64KB 预算 (`EncodedDiagnosticBudget`) 内打包多条诊断; 若 wire 限单条, 移入 M9 协议项。
  - 验收: 多错误源文件一次回传多条诊断 (或形成移交 M9 的结论记录)。
  - 结论: Protocol 1.1 允许重复发送单条 `onDiagnostic` frame；provider 已按 ECJ 源顺序逐条发送，宿主按累计 wire bytes 验证并收集为列表，详见 `docs/diagnostic-wire.zh-CN.md`。

- [x] **M7-3 缓存 lane 毒化恢复** `[本仓]`
  - 内容: `CompilationCacheOperationLane` 1 秒超时后**永久毒化**直到进程重启。增加有界恢复: 冷却期后单次重建 lane, 二次失败再永久毒化, 保持单线程单槽语义。
  - 验收: 新单测: 超时→冷却→恢复→可用; 连续失败路径行为可预期; 现有 lane 测试不回归。
  - 实现: 默认冷却 5 秒；仅在旧任务和旧 worker 均确认退出后重建一次，旧 I/O 未退出时继续 fail-closed，替代 worker 二次超时后永久毒化。

- [x] **M7-4 观测数据本地导出通道 (debug 构建限定)** `[本仓]`
  - 内容: 在"不跨 Protocol V1"前提下 (`JavaProviderObservationPolicy` 明确协议导出需未来协商 + 运行时证据),
    为 debug 构建增加本地结构化导出 (logcat 结构化行或私有目录文件): 5 阶段耗时 (COMPILE/D8/LOAD/RUN/TERMINATION)、缓存命中计数。
    这是 M9-4 协议化导出的证据积累。
  - 验收: debug 构建可稳定取数; release 构建零导出 (测试断言)。
  - 实现: `:compiler` 在会话清理后向私有目录写入有界 schema-v1 JSONL，额外记录
    `bindService`→`onServiceConnected` 的 worker 冷启动耗时；`BuildConfig.DEBUG` 与 debuggable flag 双门禁，
    Debug/Release unit-test variant 分别断言可写与零文件副作用，详见 `docs/local-observability.zh-CN.md`。

- [x] **M7-5 性能基线测量** `[设备]`
  - 内容: 用 M7-4 通道在 API 24 与 34/36 各一台设备记录端到端时延基线 (含 worker 冷启动占比), 写入 `docs/perf-baseline.md`。
  - 验收: 基线文档完成, 可供 M8-1 预热项对比。
  - 结果: 在 API 24/x86 与 API 36/x86_64 AVD 上经 AutoJs6 生产 Binder 路径分别保留
    1 条 compiler cold + 10 条 warm 原始 schema-v1 JSONL。暖态 worker 启动占 session 的 p50 分别为
    5.44% 与 36.09%；API 36 是 M8-1 的主要收益判据，详见 `docs/perf-baseline.md`。

## M8 — 运行时增强 (本仓独立)

- [x] **M8-1 Worker 预热 (并行绑定) — 评估关闭 / no-go** `[本仓]` `[设备]`
  - 内容: 目前每次执行串行经历"编译完成 → 绑定 `:worker` → 冷启动进程"。改为会话打开/编译进行中并行预绑定 worker,
    保持一次性 worker、pid/uid pin、全部看门狗与取消语义不变。涉及 `WorkerServiceBindingLifecycle`、`RemoteJavaSourceSession`。
  - 验收: 对比 M7-5 基线端到端时延显著下降 (目标: 摊销掉大部分 worker 冷启动); 终态/取消/看门狗全部测试通过。
  - 结论: 立即预绑定会与 ECJ/D8 争用资源；延迟预绑定及单线程 D8 候选在 API 36 的 P–S–P
    交错复验中收益方向反转。最终完整候选区块暖态 p50 仅改善 42.5 ms（门槛 213 ms），p90 回归
    42 ms。全部实验运行时代码已回退，串行路径保持不变；原始 JSONL、变体序列和重新开启条件见
    `docs/worker-prewarm-evaluation.zh-CN.md`。

- [x] **M8-2 多 DEX 支持 (R4 profile)** `[本仓]` `[设备]`
  - 内容: `JavaDexOutputPolicy.kt` 单 DEX 限制 (65536 方法引用上限) 放宽为有界 `classesN.dex` (建议 ≤4 个, 总量仍 ≤32MB)。
    联动: `DexArtifactValidator` 逐文件校验、`WorkerDexLoader` 的 API 24/25 `DexClassLoader` 多路径与
    API 27+ `InMemoryDexClassLoader(ByteBuffer[])`、缓存 manifest 格式、
    `ReadOnlyDexWritePolicy` 逐文件时序。API 26 仅公开单 buffer 构造器，必须保留单 DEX 限制。
    DEX 不跨宿主协议 (编译执行都在 provider 内), 故为本仓事项。
  - 验收: 超方法数样例编译执行通过; API 24/25 与 27+ 双加载路径设备证据; API 26 单 DEX 回归与
    多 DEX fail-closed 测试; 单 DEX 全量用例零回归。
  - 实现: 接受严格连续的 `classes.dex`..`classes4.dex`，集合总量仍为 32 MiB；逐文件验证后对 class
    descriptor 并集执行完整性与跨 DEX 去重。内部 AIDL 按名称/大小/摘要/FD 数组交接，API 27+ 使用
    `InMemoryDexClassLoader(ByteBuffer[])`，API 24/25 使用逐文件 R2 只读发布后的私有多路径
    `DexClassLoader`；API 26 因平台仅有单 buffer 构造器，超过一个 DEX 时在 D8/worker 双门禁拒绝，
    不使用会破坏跨 DEX 引用的链式 loader。缓存 manifest 升级 schema 2，key 修订为 `r4-cache-v3`。
    生成 65,536 个辅助方法的
    129-class fixture 在 API 24 与 API 36 均产出 2 个 DEX，并通过直接加载及一次性 `:worker` Binder
    执行；详见 `docs/multidex-r4.zh-CN.md`。

- [x] **M8-3 用户代码 core library desugaring** `[评估]` `[设备]`
  - 内容: 目前 `desugar_jdk_libs_nio` 仅服务插件自身, 用户代码 D8 无 `--desugared-lib`。分两步评估:
    (a) 维持 API 24 编译 stub 下接入 desugared-lib 的实际收益; (b) 评估提升 ECJ bootclasspath stub 版本
    (24→更高, 解锁 `java.time` 等) 带来的"编译可见但运行时缺类"风险与缓解 (D8 警告面/文档告知)。
  - 验收: go/no-go 决策记录; 若 go: `java.time`/Stream 增强样例在 API 24 真机通过, 缓存 key 随 D8 选项自动失效验证。
  - 结果: **受控 go**。完整 ECJ 平台边界仍为 API 24，只额外生成 API 26 `java.time` 与经裁剪的
    增强 `Stream` stub；API 30 `java/**` stub 仅供 D8 解析，不对源码可见。用户 D8 的 CLI/builder
    两路径接入固定 `desugar.json`，Release L8 禁止裁剪/优化/改名并检查动态链接所需描述符。API 24
    正向 `java.time` + 三参数 `Stream.iterate(...).toList()`、负向 `Stream.ofNullable` 与既有样例
    共 6/6 通过；工具链
    指纹、ECJ/D8 options 和缓存 key 自动失效测试已覆盖。整体抬高 Android stub 判定 no-go；详见
    `docs/core-library-desugaring.zh-CN.md`。

- [x] **M8-4 编译缓存跨进程持久化** `[评估]`
  - 内容: 缓存 HMAC 密钥为进程 epoch 级 (进程重启即全部失效, 防篡改设计)。评估 Android Keystore 托管 HMAC 密钥方案,
    保留全部重校验路径 (materialize 后仍重新验 JAR/DEX 摘要), 过安全评审 gate。
  - 验收: 决策记录; 若 go: 进程重启后缓存命中率提升的观测数据 (M7-4 通道)。
  - 结果: **安全 gate no-go，保持进程 epoch key**。Keystore 原型在 API 24 生产 Binder 路径把 5 次
    compiler 重启命中从 0/5 提到 5/5，但普通 R1 用户 Java 随后在同 UID 的一次性 `:worker` 中成功加载
    并使用生产 HMAC alias。不可导出不等于同 UID 不可使用，持久 key 会破坏 compiler-only secret
    boundary；简单 API denylist 也不足以替代 UID 隔离。原型与临时 benchmark build type 已全部回退，
    完整重校验和原行为不变；攻击证据、原始 schema-v1 JSONL 与重新开启条件见
    `docs/cache-persistence-m8-4.zh-CN.md`。

- [x] **M8-5 任意入口类名 — 插件侧就绪** `[本仓]`
  - 内容: 内部 `entryClassName` 已全链路参数化（M8-5 交付时 Host 固定 `Main`；M9-7 后仅保留为兼容
    默认）。补齐非 `Main` 入口
    (含包名组合) 的单测/仪器测试矩阵: `JavaSourcePolicy`、`UserClassJarWriter`、缓存 key、`EntryClassAnalyzer`。端到端放开在 M9-7。
  - 验收: 任意合法"简名+包名"矩阵测试全绿。
  - 结果: 插件侧就绪，生产实现无需修改。3 种非默认简名 (`ScriptEntry`/`_Entry9`/`$Entry`) ×
    3 种包布局 (默认包/常规深包/含 `_` 与 `$` 的边界包) 共 9 个组合，经真实 ECJ、入口 ABI 分析、
    JAR 路径/DEX descriptor 与缓存 key/物化重验全部通过；API 24 另以 3 个代表布局完成
    ECJ→D8→`DexClassLoader`→ART 执行。M8-5 当时的 Host 固定入口边界已由 M9-7 完成公开端到端
    放开；插件侧先行证据见 `docs/arbitrary-entry-class-m8-5.zh-CN.md`。

- [x] **M8-6 设备证据矩阵制度化** `[设备]`
  - 内容: 新建 `docs/device-evidence.md`: API 24/25 (`DexClassLoader` 路径)、26+ (`InMemoryDexClassLoader`)、
    34/36 (R2 只读落盘) 的证据项清单与最近执行记录; 明确宿主↔插件 Binder 端到端证据位于宿主仓的分工边界。
  - 验收: 矩阵表完整, m8 发版前全部打勾。
  - 结果: 发布阻断矩阵、P/W/H/R 四层证据边界、失效规则、复现与清理流程已固化；API 24/25/26/36
    分支、API 24/36 样例和 R4 多 DEX、API 36 R2 精确时序均在锁定 AndroidTest APK 上通过，当前
    provider 又在 API 24/36 经 AutoJs6 production Activity→Binder 路径各完成一条完整观测。原始
    JSONL/logcat、APK/源码树摘要和设备 fingerprint 见 `docs/device-evidence.md`；最终 Debug/Release
    各 140/140 单测、lint、Debug/Release/AndroidTest 离线构建全绿。

## M9 — 协议协同能力扩展 (全部 `[宿主]`)

> 前置: 从已落地的 Protocol 1.2 / Entry API 3 继续协商增量; 按 `protocol/README.md` 流程
> 同步刷新三个 AAR + `protocol-artifacts.lock.json`; 提升 `REQUIRED_HOST_VERSION_CODE` 与
> `plugin_protocol_api_min/max`; 每个新能力遵守既有安全四件套 — **默认拒绝、双侧硬编码白名单、payload 上限、脱敏**。

- [x] **M9-1 能力集 2 (Capability Set 2)** `[设备]`
  - 内容: 当前仅 4 能力 (APP_LAUNCH/CONSOLE_STREAM/SLEEP/TOAST)、2 个宿主方法 (`app.launch`/`toast.show`)。
    候选新增 (与宿主逐项谈判): `clipboard.get/set`、`device.info` (只读脱敏子集)、`notice.show`、`vibrate`、
    受限文件通道 (宿主授权目录内, 路径白名单)。每项含: 枚举 + wire 方法名 + `SessionHostBridgeProxy` 与
    `RemoteJvmScriptContext` 双侧白名单 + 规范 payload 编解码器 + 上限。
  - 验收: 每能力具备: 双侧白名单测试、payload 边界测试、未授权调用被拒证据、真机样例。
  - 结果: 选择 `clipboard.get/set`，以独立 `CLIPBOARD_READ` / `CLIPBOARD_WRITE` 落地 Protocol 1.2、
    Entry API 3、16 KiB 规范 UTF-8 payload、双侧硬编码方法/响应白名单和稳定脱敏错误。最终 Debug/Release
    各 146/146 单测、lint 0 error、三类 APK 离线构建全绿；API 24 模拟器与 API 28 Sony 真机样例均
    8/8，API 36 又经 AutoJs6 production Activity→Binder 路径实际写入、回读并恢复。Android 10+
    后台静默拒绝问题已通过宿主 resumed-Activity guard fail closed。设计、哈希和原始 JSONL/logcat 见
    `docs/capability-set-2-m9-1.zh-CN.md`。

- [x] **M9-2 脚本入参 (Entry API 4)** `[设备]`
  - 内容: 目前入口 `run(JvmScriptContext)` 无法接收宿主参数。新增 `context.args()` 返回 JSON profile 值
    (随 `openSession` request 携带, 复用 64KB/深度 64 限制)。保持 Entry API 2/3 无参入口向后兼容。
  - 验收: AAR 更新; 双版本入口兼容测试; 样例演示参数往返。M9-1 已使用 Entry API 3，因此本项
    实际版本锁定为 Protocol 1.3 / Entry API 4；`run(JvmScriptContext)` 方法签名不变。
  - 结果: 宿主在 provider discovery/bind 前把 `ExecutionConfig.arguments` 冻结成确定性、64 KiB、深度 64
    的 JSON object，worker 以深层只读 `Map`/`List` 暴露；无效类型、环、非有限数和 Android/host 对象
    稳定映射为 `JVM_SOURCE_INVALID_ARGUMENTS` / `FIX_INVOCATION` 且零 bind。1.2 缺字段兼容为空对象，
    1.3 的 tag 17 为 reader-required，旧 reader 不会静默丢参。最终 AAR 锁指向干净宿主 commit
    `abb64bdd620c33bf9c7ce64c7fc0011237d0365f`；provider Debug/Release 各 147/147 单测、lint 与三类
    APK 全绿，API 24/28 样例各 9/9，API 36 production 三进程参数往返 1/1 且 bridge 调用为 0。设计、
    工件哈希和原始证据见 `docs/script-arguments-m9-2.zh-CN.md`。

- [x] **M9-3 运行时异常类名回传** `[设备]`
  - 内容: 目前运行时错误仅回传一个行号 (无异常类/message)。在 line-only 基础上增加脱敏类名:
    `java.*`/`javax.*` 白名单原样, 其余归一 (如 `UserException`), message 仍不回传。
  - 验收: 协议字段落定; 脱敏审查通过; 样例可见"类名+行号"。
  - 结果: Protocol 1.4 / Entry API 4 以 reader-required diagnostic tag 8 返回最多 255-byte 的安全类名；
    Provider 有界提取并归一用户命名空间，Host 关联 request 再验证且以独立白名单和固定 message 投影。
    最终 AAR 锁指向 Host commit `a859a15a0517112c374f6637fc7b3d10a06d5450`；Provider Debug/Release
    各 149/149 单测、lint 与三类 APK 全绿，API 24/28 样例各 10/10，API 36 最终 Host/Provider 制品的
    production Binder selector 1/1 且秘密异常 message 不可见。设计、工件哈希和原始 marker 见
    `docs/runtime-exception-class-m9-3.zh-CN.md`。

- [x] **M9-4 观测数据导出协议化**
  - 内容: 兑现 `JavaProviderObservationPolicy` 的"future negotiated protocol": 以 M7-4/5 积累的字段与
    真机证据为输入, 与宿主谈定导出 schema (阶段耗时、缓存结果、资源采样)。
  - 验收: 宿主侧可见编译/执行耗时展示; release 路径字段边界测试。
  - 结果: Protocol 1.5 / schema 1.2 在 success/error/cancellation 清理后终态增加 reader-required 的
    4 KiB observation；Host 独立重验并固定显示阶段耗时、单请求 cache 结果、冷暖启动与最多 6 个无身份
    资源样本，Debug 累计 cache telemetry 不跨协议。AAR 锁指向 Host commit
    `22bf823288d8b7b8f12acd71fa4f8e149ee03954`；Host shared/app 为 61/61、1,660 tests，Provider
    Debug/Release 各 155/155，lint 与三类 APK 全绿。API 28 样例 10/10；API 36 最终 Host + Release
    Provider 的 production Binder 冷态 MISS 与暖态 HIT selector 各 1/1，设计、边界、工件摘要和原始
    marker 见 `docs/observation-protocol-m9-4.zh-CN.md`。

- [x] **M9-5 多文件源码包**
  - 内容: 目前 `openSession` 单 source FD = 单 `.java` 文件。与宿主选型: 单 FD 承载有界 zip
    (清单 + 路径白名单 + 文件数/总量上限 + 禁符号链接) vs 多 FD 协议。沿用现有词法策略逐文件校验。
  - 验收: 多类工程样例端到端通过; 路径穿越/压缩炸弹/重复项用例全部被拒。
  - 结果: 选择单 FD 承载 Protocol 1.6 / schema 1.3 的确定性 STORED-only 规范归档，2..32 个 Java
    compilation unit、4 MiB 源码总量、4 MiB + 64 KiB 归档上限；manifest/路径/CRC/SHA/ZIP metadata
    逐层交叉验证并以规范重编码字节恒等收口。Host 以 `lstat`/`O_NOFOLLOW` 冻结目录，Provider 在任何
    源码路径落盘前整包重验，再向一次 ECJ batch compile 提交有序文件集；诊断保留安全逻辑相对路径，
    cache key 升至 schema 3 / `r5-cache-v4`。AAR 锁指向 Host commit
    `1b79603bc7304ae44fe878b27ef01d2d77b2a963`；Host shared/app 为 68/68、1,665 tests（3 skipped），
    Provider Debug/Release 各 163/163，lint 与三类 APK 全绿。API 25 最终 Host + Release Provider 的
    production Binder 两文件 selector 1/1 返回 `42` 且三进程 PID 分离；路径穿越、重复项、压缩/ZIP64、
    symlink 与 package/path 不一致均有拒绝测试。设计、威胁边界、工件摘要和原始 marker 见
    `docs/multifile-source-package-m9-5.zh-CN.md`。

- [x] **M9-6 超时上限提升评估** `[评估]`
  - 内容: `MAX_TIMEOUT_MILLIS=120s` 限制长任务。与宿主评估更高上限或续约 (keep-alive) 机制, 分析前台服务/ANR/功耗约束。
  - 验收: 决策记录 (含约束分析); 若 go: 协议字段与看门狗联动更新。
  - 结果: **No-go**；保留正式 Host 30 秒总截止时间与 Protocol 1.6 的 120 秒安全上限，不增加可续约
    keep-alive。审计确认 Host 在源码快照/发现/绑定前启动 30 秒 deadline，请求也固定为 30 秒，而
    Provider 只在 `openSession` 后启动单次看门狗；只调大常量不会扩大正式预算。当前 compiler/worker
    均为非前台 bound service，心跳只能证明进程存活、不能证明 ECJ/D8 或用户入口有进展，并会让非可信
    死循环长期占用唯一会话槽。未来仅考虑由前台用户动作授权、Host 会话专属 FGS + 可见 stop、三层
    独立租约和不可续约绝对上限组成的显式 `LONG_RUNNING` 模式；Protocol/Provider/AAR 均未改变。
    完整双截止时间、Android FGS/ANR/Doze/功耗约束、Python 先例、联动面和重开条件见
    `docs/timeout-limit-m9-6.zh-CN.md`。Provider Debug/Release 强制重跑各 163/163，pinned-input、Debug
    lint 与 Debug APK gate 全绿；因 no-go 且无运行时代码变化，不新增实验 APK 或设备结果。

- [x] **M9-7 任意入口类名端到端放开**
  - 内容: 宿主请求侧允许用户指定入口简名 (插件侧 M8-5 已就绪), README/文档同步移除 "`Main` 固定"表述。
  - 验收: 端到端非 `Main` 样例通过。
  - 结果: Host 新增单文件/源码包显式入口 API，共享 ASCII identifier、`<Entry>.java <= 255 bytes`
    与全限定名上限在 discovery/bind 前统一校验；旧 API 和 Kotlin 继续默认 `Main`，Protocol 仍为
    `1.6` / schema `1.3` / Entry API `4`。Provider 新增 `demo.entries.ScriptEntry` 样例，API 25
    Debug 样例矩阵 11/11；最终 Provider Release production Binder selector 返回
    `"demo.entries.ScriptEntry"`，并观测到不同的 Host/compiler/worker PID。Host 69 + 1,670 项单测、
    Provider Debug/Release 各 163 项单测、Lint、pinned-input 与双仓 APK/AAR 构建全绿；完整实现、制品
    摘要、设备 marker 和清理记录见 `docs/arbitrary-entry-class-m9-7.zh-CN.md`。

- [x] **M9-8 会话并发模型评估** `[评估]`
  - 内容: `MAX_CONCURRENT_SESSIONS=1` 且第二会话立即 `BUSY`。评估宿主侧排队 vs 协议放开 >1 的语义
    (连锁: `SingleActiveSessionGate`、缓存单槽 lane、观测单槽、worker 进程模型)。
  - 验收: 决策文档 (含连锁影响分析); 短期可先落地宿主侧排队重试指引。
  - 结果: **No-go**；保留 Protocol 1.6 每 Provider 进程一个活动会话、第二会话立即返回可重试
    `BUSY`，不在 `RemoteJvmSourceHost` 内加入透明队列或轮询。审计确认单槽同时约束 process-global
    gate、单线程 compiler、无 pending queue 的 cache lane、单槽观测、共享 64 项 callback lane、
    单个一次性 `:worker` 与 compiler hard-kill；只提高常量会产生 deadline 排队、观测覆盖、cache
    竞争和跨会话误杀。Host 每次脚本都会创建独立 engine/host，现有 gate 不是进程级协调器；未来若有
    真实 BUSY/延迟数据，先探索按精确 Provider 身份分区、deadline 从 enqueue 起算、取消优先的
    `1 active + 1 pending` Host 有界 FIFO。短期仅对精确 `BUSY` 提示用户稍后重试，不自动重放可能已有
    外部副作用的 Java 入口。Provider 聚焦 28/28、Debug/Release 各 163/163，Host 聚焦 42/42、shared
    边界 16/16，pinned-input、Debug lint 与 APK gate 全绿；无 runtime/AAR/设备行为变化。完整连锁分析、
    重试规则与重开条件见 `docs/session-concurrency-m9-8.zh-CN.md`。

## M10 — 远期探索

- [x] **M10-1 ECJ 升级 spike — 直接升级 no-go** `[评估]` `[网络]`
  - 内容: ECJ 钉在 3.26.0 (配套自建 `javax.lang.model.SourceVersion` 桩强制 pre-JDK-12 路径, 属 ART 运行约束)。
    时间盒 (≤2 天) 验证更新 ECJ (lint 提示已有 3.42.0) 经 D8 desugar 后能否在 ART 上运行, 从而解锁 `-source 11/17`。
  - 验收: go/no-go 记录 (含失败根因); 若 go: 语言级别提升进入下一里程碑正式项。
  - 结果: **直接升级 no-go，继续固定 ECJ 3.26.0 / Java 8**。精确候选 3.42.0 在 API 24 因
    `InputStream.readAllBytes()` 缺失失败，在 API 35 因 Android 不提供 `Runtime.Version` 失败；测试日
    Maven Central 最新 3.46.0 在 API 24/25/28/35 得到同类结论，AGP/core library desugaring 未改写
    ECJ 自身调用。边界版本 3.33.0 虽在 API 35 将 Java 8/11/17 三项完整送过 ECJ→D8→ART，却同样
    无法运行于 API 24。实验代码仅保留在隔离提交 `baf8ec71489ea2287c269142015bf3e2be6cd99f`；正式
    运行时、协议、缓存和发布依赖均未改变。版本/API 矩阵、工件摘要、根因与 Android-targeted ECJ fork
    的重开门槛见 `docs/ecj-upgrade-m10-1.zh-CN.md`。

- [x] **M10-2 R8/D8 补丁位升级评估** `[评估]` `[网络]`
  - 内容: 8.13.17 → 8.13.22 (lint 提示)。评估变更日志, 升级后跑全量单测 + 设备矩阵; 缓存 key 含 D8 版本会自动失效, 验证该路径。
  - 验收: 决策记录或完成升级 + 证据。
  - 结果: **Go，正式 Provider 运行时 D8/R8 升至 8.13.23**。测试日 Google Maven 中 `.23` 已是
    最高已发布非开发 8.13 补丁，并在 Roadmap 候选 `.22` 上补充 annotation 排序后刷新缓存 hash；
    未追随尚未发布到 Maven 的 `.24/.25` 分支头。显式 runtime classpath 为 8.13.23，AGP 9.3.0
    内嵌构建期 R8 独立保持 8.13.19。精确回归证明 8.13.17→8.13.23 同时改变 toolchain fingerprint
    与 schema-3 compilation-cache key，无需重复 bump cache schema/domain。Debug/Release 各 164/164，
    pinned-input、lint 与三类 APK 全绿；API 24/25/26/28/35 pipeline 与 11 项样例全绿，API 24/28/35
    R4 多 DEX 通过，API 36 R2 精确首字节前只读与失败清理 1/1。协议、ECJ/Java 8、Host/AAR、
    version code/name 与安全边界均未改变。上游差异、JAR/APK 摘要、设备指纹、异常设备归因、原始
    observation 和清理记录见 `docs/d8-upgrade-m10-2.zh-CN.md`。

- [ ] **M10-3 Kotlin provider spike** `[评估]`
  - 内容: 协议已含 `KOTLIN`/`KOTLIN_JVM` 枚举但本插件仅广告 JAVA。评估嵌入式 kotlinc 的体积/内存可行性;
    大概率结论是"独立插件、复用本仓 worker/校验设施"。
  - 验收: go/no-go 与架构建议记录。

- [ ] **M10-4 远端仓库与 CI** `[网络]`
  - 内容: 仓库目前无 remote (纯本地, 2 个 commit)。确定托管方案; 鉴于网络易 524, CI 采用依赖预热缓存/自托管 runner,
    验证任务全部 `--offline`。M6-5 本地脚本先行兜底。
  - 验收: push + CI 绿灯一次。

- [ ] **M10-5 i18n 扩展**
  - 内容: `strings.xml` 目前 en + zh-rCN, 按宿主语言矩阵逐步补齐 (插件字符串仅 5 条, 成本低)。
  - 验收: 新语种资源合入且 lint 无 MissingTranslation。

- [ ] **M10-6 SECURITY.md 威胁模型文档化**
  - 内容: 将现有安全设计显性化成文档: 三进程隔离、签名校验、R1 白名单、DEX 校验链、脱敏策略、缓存 HMAC。既是评审基线也是对外信任背书。
  - 验收: 文档评审通过, README 链接。

## 持续性事项 (每个里程碑例行)

- [ ] 宿主协议源变更时, `protocol/` 三个 AAR 与 lock **必须整组刷新** (见 `protocol/README.md`)
- [ ] 发版前离线三连: `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease --offline`
- [ ] 发版 checklist: `VERSION_BUILD` 递增、tag、CHANGELOG、official-index 元数据字面量与 `version.properties` 双源一致 (由 `verifyPinnedInputs` 断言)
- [ ] 新增桥接能力必须过"安全四件套"评审: 默认拒绝 / 双侧白名单 / payload 上限 / 脱敏

## 附录 — 网络受限环境的执行约定

当前网络易触发 Cloudflare 502/524/529 (尤以 524 为甚), 因此:

1. 本 Roadmap 中除标注 `[网络]` 的 3 项 (M10-1/2/4) 外, **全部条目可在 `--offline` 下完成验证**;
2. `[网络]` 条目集中到单独时段批量执行 (一次性拉齐依赖进本地缓存), 失败重试, 不阻塞其他线;
3. 日常验证统一走 M6-5 的本地脚本, 禁用远端仓库解析。
