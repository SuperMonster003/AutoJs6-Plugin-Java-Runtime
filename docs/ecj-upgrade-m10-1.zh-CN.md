# M10-1 ECJ 升级 spike

## 决策

**No-go：不把正式 Provider 从 ECJ 3.26.0 直接升级到 3.42.0/3.46.0，也不在当前
API 24 产品线中开放 `-source 11/17`。**

这不是对 Java 11/17 用户源码能力的永久否定。实验已经证明：当编译器本体能够在设备上运行、受控
平台桩改放普通 classpath、且 Java 8 class 版本门禁临时放宽后，ECJ 3.33.0 可以在 API 35 真机生成
Java 11/17 class，当前 D8 8.13.17 能以 minApi 24 转成 DEX，现有校验器、动态 class loader 与 ART
可以执行并返回预期结果。因此，下游 D8/ART 不是本次 no-go 的主因。

阻断点在编译器自身的 Android 运行时兼容性：

1. ECJ 3.33.0、3.42.0 与 3.46.0 都直接调用 `InputStream.readAllBytes()`；Android 直到 API 33
   才提供该方法，API 24/25/28 在 ECJ 初始化或读取 classpath 时抛出 `NoSuchMethodError`。
2. ECJ 3.42.0 与 3.46.0 的 parser 还直接链接 `java.lang.Runtime.Version`。该 JDK 9 类型在本次
   API 35 Android 15 真机上仍不存在，Java 8、11、17 三种源码都在 parser 阶段抛出
   `NoClassDefFoundError`。
3. Android/AGP 的 core library desugaring 没有改写上述 ECJ 自身调用。用户源码 D8 更晚才运行，
   不可能补救一个尚未完成解析的 ECJ 进程。

提高 minSdk 也不是合格的直接修复：提高到 API 33 只能绕过 `InputStream.readAllBytes()`，仍不能给
Android 增加 `Runtime.Version`；同时会放弃本项目已经锁定并持续验证的 API 24–32 用户。

本次评估锁定主线提交 `f1dcfc61924bdfa202a623279f43d30a1310d5f4`、Provider `0.8.0-m9`、
Protocol 1.6、Entry API 4、minSdk 24、ECJ 3.26.0 与用户代码 D8 8.13.17。实验性依赖替换、编译
参数和 class 版本放宽仅存在于隔离分支提交 `baf8ec71489ea2287c269142015bf3e2be6cd99f`，没有合入
`main`，也没有改变发布 APK、协议 AAR 或缓存格式。

## 需要回答的问题

| 问题 | 结果 | 证据 |
| --- | --- | --- |
| Android 构建链能否打包以 Java 17 class 发布的 ECJ？ | 可以 | 3.42.0、3.46.0 的 Debug 与 AndroidTest APK 均构建成功，minSdk 仍为 24 |
| Roadmap 指定的 ECJ 3.42.0 能否在 ART 上编译 Java 8？ | 不可以 | API 24 缺 `InputStream.readAllBytes()`；API 35 缺 `Runtime.Version` |
| 最新稳定 ECJ 3.46.0 是否已经修复？ | 没有 | API 24/25/28/35 矩阵复现同类运行时链接失败 |
| 较早的 Java 11 基线 ECJ 3.33.0 能否运行？ | 仅新系统部分可行 | API 24 失败；API 35 的 Java 8 路径通过 |
| `-source/-target 11/17` 是否被 D8 或 ART 本身阻断？ | 没有被本样例阻断 | 3.33.0 在 API 35 上生成 major 55/61 class，D8→DEX 校验→ART 均通过 |
| 能否只改版本号后发布？ | 不可以 | 破坏 minSdk 24，最新候选即使在 API 35 也无法启动 parser |

## 上游版本与运行时基线

2026-08-27 直接读取 [Maven Central ECJ metadata](https://repo.maven.apache.org/maven2/org/eclipse/jdt/ecj/maven-metadata.xml)，
`latest`/`release` 均为 3.46.0；Roadmap 中的 3.42.0 已不是最新版本，所以本次同时验证精确候选与
最新候选。Eclipse 上游已经记录 batch compiler 从 Eclipse 4.28 起迁移至 Java 17 运行时，但继续支持
生成 Java 8 目标；“编译器需要什么 JRE”与“用户源码输出什么 class 版本”是两条不同轴，见
[Eclipse JDT issue #886](https://github.com/eclipse-jdt/eclipse.jdt.core/issues/886)。

对下载到 Gradle cache 的原始 JAR 做 manifest、入口 class major 与字节码调用审计：

| ECJ | 编译器入口 class major | manifest 执行环境 | 与本次失败相关的直接链接 | JAR SHA-256 |
| --- | ---: | --- | --- | --- |
| 3.26.0 | 52 | Java 8 | 未发现两项阻断调用 | `ac0ba5876eaf7ebb47749a0d1be179c51f194b9dd0b875d1c09e1b530f5a2db5` |
| 3.33.0 | 55 | JavaSE-11 | `InputStream.readAllBytes()` | `f7686c4960cf70c2ebc5c500a73a8cfc04541b730c18f1c5c21329889b137f45` |
| 3.42.0 | 61 | JavaSE-17 | `InputStream.readAllBytes()`、`Files.readAllBytes(...)`、`Runtime.Version` | `29f6d3918ee02db4400c103bc25dd90a22491c3a395867d9393070cb96a7dd29` |
| 3.46.0 | 61 | JavaSE-17 | 同 3.42.0 | `d0d43f8e2d7003e5efed612e2cbb5f01870043397d8f1bbe536fd9128f4fcbf7` |

Android 官方 API 参考将
[`InputStream.readAllBytes()`](https://developer.android.com/reference/java/io/InputStream#readAllBytes())
标为 API 33；[`Files.readAllBytes(Path)`](https://developer.android.com/reference/java/nio/file/Files#readAllBytes(java.nio.file.Path))
虽从 API 26 可用，但不能覆盖 ECJ 同时保留的 `InputStream` 调用。Android 的
[`java.home`](https://developer.android.com/reference/java/lang/System#getProperties()) 指向 ART APEX，
也不是可交给 ECJ `--system` 的桌面 JDK module image。

## 当前 3.26.0 为什么特殊

正式编译路径固定使用：

- `-source 8 -target 8 -proc:none`；
- 受控 core library stubs 与 API 24 `android.jar` 作为 `-bootclasspath`；
- Entry API JAR 作为普通 `-classpath`；
- `EntryClassAnalyzer` 只接受 major 45..52；
- compiler identity、缓存 key 与文档都显式绑定 source/target 8 和当前 bootclasspath。

项目还提供最小 `javax.lang.model.SourceVersion` 桩，仅含 `RELEASE_8`。ECJ 3.26.0 探测
`RELEASE_12` 时故意失败，从而走 pre-JDK-12 路径；annotation processing 已由 `-proc:none` 关闭。
它是现有 ART 兼容方案的一部分，但不是 3.42.0 的首个失败点：新版在到达可验证该桩的后续路径前，
已经因自身 JDK API 链接失败。3.33.0 的正向实验也说明，在 `-proc:none` 下这个桩不会天然阻止
Java 11/17 语法解析。

## 实验设计

### 隔离原则

从锁定主线创建 `codex/m10-ecj-spike` 工作树。只在该分支进行以下修改：

1. 同时修改 version catalog 与 `verifyPinnedInputs` 的字面量坐标，分别构建 3.46.0、3.42.0、
   3.33.0 候选；
2. 新增三个真机用例：Java 8 基本语法、Java 11 `var`、Java 17 pattern matching + switch expression；
3. 检查 ECJ 输出 major 分别为 52、55、61，再进入 `UserClassJarWriter`、D8、DEX 结构验证、
   read-only 发布、`WorkerDexLoader` 与 `WorkerEntryFactory`；
4. Java 11/17 不再传 ECJ 明确禁止的 `-bootclasspath`，而是把受控 core/Android/Entry API 桩组成
   普通 classpath；
5. 仅为让实验 class 到达 D8，临时把 `EntryClassAnalyzer` 上限从 major 52 放宽到 61。

Eclipse 官方 batch compiler 文档说明 compliance 9+ 不支持 `-bootclasspath`，应改用 `--system` 或
`--release`；参见
[Using the batch compiler](https://help.eclipse.org/latest/rtopic/org.eclipse.jdt.doc.user/tasks/task-using_batch_compiler.htm)。
Android 没有桌面 JDK image，`--system none` 也被 ECJ 判为 invalid location。本实验采用普通 classpath
不是正式 API 设计，只用于判断受控桩能否支持语言前端。3.33.0 的结果证明该方法对本组样例可行；正式
实现仍需系统性验证类型解析、module-info、默认模块语义与完整负向 API 边界。

### 源码夹具

| 级别 | 关键语法 | 期望 class major | 期望返回 |
| --- | --- | ---: | ---: |
| 8 | 局部变量与整数返回 | 52 | 42 |
| 11 | `var base = 40` | 55 | 42 |
| 17 | `instanceof String text` 与 switch expression | 61 | 42 |

三项都实现现有 `AutoJsJvmEntry`，不调用 Host 能力，避免把协议或权限噪声混入编译器判断。

## ART 设备结果

### 设备集合

| API | ABI | 设备/镜像 | build fingerprint |
| ---: | --- | --- | --- |
| 24 | x86 | Android Emulator | `google/sdk_google_phone_x86/generic_x86:7.0/NYC/6696031:userdebug/dev-keys` |
| 25 | x86 | Android Emulator | `google/sdk_google_phone_x86/generic_x86:7.1.1/NYC/6695155:userdebug/test-keys` |
| 28 | arm64-v8a | Sony G8441 | `Sony/G8441/G8441:9/47.2.A.4.45/3677320370:user/release-keys` |
| 35 | arm64-v8a | Xiaomi liuqin | `Xiaomi/liuqin/liuqin:15/AQ3A.241006.001/OS2.0.9.0.VMYCNXM:user/release-keys` |

另有一台 API 31 设备已经安装用户自己的同包名 Provider。实验预检发现后将其排除，未覆盖或卸载该包。

### ECJ 3.42.0：Roadmap 精确候选

这里使用已经去掉 Java 11/17 `-bootclasspath`、并临时允许 major 61 的最终 spike：

| API | Java 8 | Java 11 | Java 17 | 最早失败点 |
| ---: | --- | --- | --- | --- |
| 24 | FAIL | FAIL | FAIL | `NoSuchMethodError: InputStream.readAllBytes()` |
| 35 | FAIL | FAIL | FAIL | `NoClassDefFoundError: java.lang.Runtime$Version`，位于 `Parser.parse` |

这排除了“只因选项仍沿用 Java 8 bootclasspath 才失败”的解释；三种源码均未走到用户 class/D8 阶段。

锁定工件：

- Debug APK：`99ede70572dff15026f9f51d26085f1ba0362fe2da75e53035b2c0cb162ad6d1`
- AndroidTest APK：`9807dea2f4b7d4e346b4eabbe825bf859042ae53ce20612fe4e684724cb859a6`
- 实验提交：`baf8ec71489ea2287c269142015bf3e2be6cd99f`

### ECJ 3.46.0：测试日最新候选

首次直接替换矩阵仍使用正式 Java 8 bootclasspath 形态：

| API | Java 8 | Java 11/17 | 结论 |
| ---: | --- | --- | --- |
| 24 | `InputStream.readAllBytes()` 失败 | compliance 9+ 拒绝 `-bootclasspath` | FAIL |
| 25 | `InputStream.readAllBytes()` 失败 | compliance 9+ 拒绝 `-bootclasspath` | FAIL |
| 28 | `InputStream.readAllBytes()` 失败 | compliance 9+ 拒绝 `-bootclasspath` | FAIL |
| 35 | `Runtime.Version` 失败 | compliance 9+ 拒绝 `-bootclasspath` | FAIL |

随后 3.42.0 的调整后矩阵已经分别证明：移除 `-bootclasspath` 不会消除 API 24 的 I/O API 缺口，
也不会消除 API 35 的 `Runtime.Version` 缺口。故 3.46.0 没有提供比 Roadmap 候选更好的直接升级路径。

锁定工件：

- Debug APK：`a0957ac841b00f64f0760db8f06b61d3f6552c7f826c4439c78f8e2b6e5274d5`
- AndroidTest APK：`e5a30750b70050bba2ecae7e717c89eaaf0ed0cd3c47dcec4cd4a35e560aa9a3`

### ECJ 3.33.0：运行时边界与下游正向证明

3.33.0 的入口 class 为 Java 11 major 55，尚未引入本次观察到的 parser `Runtime.Version` 直接链接，
但已经调用 `InputStream.readAllBytes()`：

| API | Java 8 | Java 11 | Java 17 |
| ---: | --- | --- | --- |
| 24 | FAIL：`InputStream.readAllBytes()` | FAIL：同一缺失 API | FAIL：同一缺失 API |
| 35 | PASS | PASS | PASS |

API 35 的 Java 11/17 首轮其实已经成功生成 major 55/61 class，只被正式
`EntryClassAnalyzer` 的 Java 8 合同拒绝。临时把上限放宽到 61 后，两项均完成
ECJ→JAR→D8(minApi 24)→DEX 校验→read-only loader→ART，并返回 `42`，最终为 `OK (3 tests)`。

锁定工件：

- Debug APK：`0bdd6cef7ffa89af024c151cdcf97a7291bfd576c544afc151f874ff95029855`
- AndroidTest APK：`ae3205d8bcb503fb27dc63d9a8d5f760abe492478978f6690c68d7bfc8d52ddb`

这组正向证据只回答“下游是否可行”，不构成发布 3.33.0 的 go：它仍直接破坏 API 24–32 的编译器
可用性，且不是测试日最新 ECJ。

## 根因分层

### 1. 编译器依赖的 JDK API 不等于 Android API

AGP 能把 major 61 的 ECJ class 转成应用 DEX，说明 class 文件版本本身可被构建工具接受；但 D8 的
普通 desugaring 主要重写语言结构，core library desugaring 也只覆盖配置中受支持的 API。它不会凭空
给 Android `java.io.InputStream` 增加未覆盖的虚方法，更不会定义 `java.lang.Runtime.Version`。

把同名类塞进应用也不是安全修复：`java.*` 由 boot class path/platform class loader 控制，应用不能以
普通依赖可靠替换。可行方案必须在 ECJ 源码或其字节码中把调用改向 Provider 自己的兼容 helper，并对
整个编译器闭包重新做 Android API 审计。

### 2. Java 9+ 编译选项模型已经改变

ECJ 对 compliance 9+ 明确拒绝 `-bootclasspath`。桌面推荐的 `--system`/`--release` 假设可访问 JDK
module image，而 Android 的 ART APEX 不是该格式。本次普通 classpath 实验证明受控桩可以支持有限样例，
但正式方案必须定义：平台类型的唯一来源、module-info 行为、重复类优先级、Android API 24 完整边界、
额外 desugared API 可见性，以及错误诊断是否稳定。

### 3. 项目合同仍是 Java 8，而不只是一个编译参数

即使换用 Android 兼容的现代 ECJ，开放 Java 11/17 还至少联动：

- Host/provider capability 与 source profile 协商，旧 Host 必须继续 fail closed；
- `EcjJavaCompiler` 选项、受控平台 classpath 与 compiler identity；
- `EntryClassAnalyzer` 接受的 major 和新 constant-pool/attribute 负向测试；
- 缓存 schema/domain、manifest 重验与版本自动失效；
- 单文件/多文件 profile、诊断、样例、README 与用户可见错误；
- Java 语言级别和 Android/core-library API 可见级别的独立说明；
- API 24/25 `DexClassLoader`、API 26/27+ loader、R2 read-only 与 production Binder 设备矩阵。

因此不能把本次 API 35 的三项正向用例解释成“只需把两个常量改为 17”。

### 4. `SourceVersion` 桩需要从兼容技巧升级为显式设计

当前单值 `RELEASE_8` 桩只为 ECJ 3.26.0 的特定探测行为服务。未来 fork 若保留 `-proc:none`，仍需基于
目标 ECJ 的实际调用面决定是否删除、补齐或隔离该桩，并增加启动期 linkage smoke；不能继续依赖某个
内部 `valueOf` 分支长期稳定。

## 为什么选择 no-go

M10-1 的时间盒目标是判断“升级依赖后经 D8 是否能在 ART 运行”。判据不是能否在某台新设备上用
实验补丁跑通，而是能否保持现有安全与兼容合同：

1. **minSdk 24 是硬门槛**：3.33+ 的实测编译器在 API 24 立即崩溃。
2. **当前 Android 也不是完整 JDK 17**：3.42/3.46 在 API 35 仍缺 `Runtime.Version`。
3. **构建成功不代表运行成功**：AGP 对 ECJ class 的 desugar 没有修复其 core-library linkage。
4. **补丁不是普通版本升级**：维护 Android-targeted ECJ fork 会新增上游同步、可复现构建、许可证、
   SBOM、字节码/API 审计和每次升级设备矩阵，已经超出两天 spike。
5. **语言升级属于协议能力**：Java 11/17 class 与库边界会改变缓存和 Host/Provider 协商，不能由
   Provider 静默放开。

所以正式线继续固定 ECJ 3.26.0、Java 8 source/target 与 major 52。M10-1 完成的是可证伪的技术判断，
而不是把失败候选带进产品。

## 重新开启条件

仅在准备单独里程碑并同时满足以下条件时重新开启：

1. 选择一个明确版本的 ECJ fork/repack 方案，能够从固定上游 tag 可复现构建；记录源码补丁、许可证、
   JAR/SBOM 摘要和升级维护人。
2. 将 `InputStream.readAllBytes()`、`Files.readAllBytes(...)`、`Runtime.Version` 等 Android 不兼容
   调用替换为 Provider 私有 helper，并对最终 DEX 的所有 platform API 引用执行 minApi 24 静态审计。
3. 在 API 24 真机先跑“仅启动 parser + Java 8 对照”，证明 fork 没有新的 hidden/absent JDK API；
   再跑 API 25、28 和当前 target 设备。
4. 为 source level 新增显式 capability/profile；Host 和 Provider 对未知值、降级与旧版本交互均
   fail closed，不能把 Java 17 源码误报成既有 Java R1。
5. 冻结 Java 9+ 平台类型来源。若继续使用普通 classpath，补齐 module、重复类、API 可见性与
   诊断一致性的正负矩阵；不得读取设备 `java.home` 作为不受控系统库。
6. 更新 class/JAR analyzer、cache schema/domain、compiler options identity、样例与文档，并验证
   major 53..61、nestmates、records、sealed classes、lambdas、string concat、switch 与多文件输出。
7. 分开定义“语言语法等级”和“可用 Java/Android 库等级”；任何新增库仍需 API 24 真机或明确
   desugared-library 支持，不因 `source 17` 自动暴露桌面 JDK 17。
8. 完整通过 Debug/Release 单测、lint、Release L8、API 24/25/26/28/34+ loader/R2、缓存冷暖命中、
   取消/超时、compiler hard-kill 与 Host production Binder 矩阵。

在这些条件出现前，不应反复尝试只改 Maven 坐标的升级。

## 基线对照与收口

候选失败后重新构建未修改主线 ECJ 3.26.0，并在同一 API 24/API 35 设备执行
`JavaProviderPipelineInstrumentedTest`。两台设备均为 `OK (2 tests)`；每项测试内部覆盖默认入口及
三个非 `Main` 布局的 ECJ→D8→DEX 严格校验→read-only 动态加载→入口调用。

对照工件：

- Debug APK：`3de1b614fd585941c0e993f9f7f901a271e24980c7a20c567c4bc9b643c13aca`
- AndroidTest APK：`2b59c9d12be07e5a53145e33abdfc1f10f8ad7343b0465d8313b22beeb2a887d`

最终主线文档变更通过 `git diff --check`、相对链接检查、Debug/Release 全量单测、pinned-input、
Debug lint 与 Debug/Release APK 离线门禁。实验安装只触及预检时无同包名应用的 API 24/25/28/35
设备；收口时已卸载 Provider 与 AndroidTest 包并确认 package path 均为空。API 31 用户设备始终不在
安装或清理目标中，其原有 Provider package path 在清理复核时仍存在。

2026-08-27 最终以 `--offline --no-daemon --rerun-tasks` 强制重跑：Debug 与 Release 各
44 个 suite、163/163 tests，均为 0 failure、0 error、0 skipped；`verifyPinnedInputs`、Debug lint
（0 error，29 个既有 warning）、Debug APK、Release R8/L8 与 Release APK 全部成功。API 24/API 35
主线真机对照均为 `OK (2 tests)`。文档相对链接与本仓 Markdown 本地目标全部存在，`git diff --check`
通过。
