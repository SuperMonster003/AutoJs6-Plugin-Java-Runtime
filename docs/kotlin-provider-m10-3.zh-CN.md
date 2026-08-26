# M10-3 Kotlin Provider 架构评估

## 决策

**独立 Kotlin Provider：Go，且已经由同目录 sibling 仓库实现并发布验证。**

**把 kotlinc 嵌入本 Java Provider：No-go。** 不在
`io.github.supermonster003.autojs6.plugin.java.runtime` 中增加第二套编译器、运行库、Android 字节码
补丁和 compiler 生命周期。本仓继续只广告 `JvmSourceLanguage.JAVA` / `ECJ`；Kotlin sibling 继续以
独立 application ID、provider ID、版本、进程、缓存和发布节奏广告
`JvmSourceLanguage.KOTLIN` / `KOTLIN_JVM`。

**当前也不抽取跨仓 production 共享库。** 两仓应共享冻结协议 AAR、规范、golden vectors 与可执行
conformance tests，而不是让任一插件在构建或运行时依赖另一个插件，或立即把看似相同的源码搬进一个
第三仓。协议和行为收敛优先于源码去重；只有在两边重新对齐同一协议/Entry API 后，才重新评估一个窄、
无 Android 组件所有权的公共模块。

本次审计对象是 sibling
[`AutoJs6-Plugin-Kotlin-Runtime`](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime)
已提交的 `aa5b55f41e129b2ce09c7deee5fce5098caa0c81`，其 canonical release 为 commit
`8db8602093c23389c2ffad2d92c752a200260b78` / annotated tag `v0.7.0-m10`。本地 sibling 主工作区含
用户未提交的 README、Roadmap、CHANGELOG、发布清单和未跟踪发布资产；本轮没有修改、暂存或覆盖它们。
构建与设备验证来自 detached clean worktree，完成后已删除。

## Roadmap 问题已经改变

M10-3 原始假设是“评估是否能在 Android Provider 内嵌 kotlinc，并大概率建议新建独立插件”。sibling
项目的存在把问题从设计猜测变成了事实审计：

| 问题 | 结论 | 主要证据 |
| --- | --- | --- |
| kotlinc 能否在 Android/ART 内运行 | 可以，但不是原始 JAR 直接运行 | Kotlin 2.3.21 经四类形状锁定补丁后，API 26 K2→D8→ART 1/1 |
| 独立 APK 体积是否可接受 | 可以 | canonical Release 31.86 MB，低于既定 40 MB decimal 预算 |
| 编译器进程能否无限复用 | 不可以 | 未退休的第二个 50-session epoch 曾升至 1.893 GB PSS |
| 是否存在可接受生命周期 | 可以 | binding-scoped compiler epoch + 每次 dispose + final-unbind kill；50-session 最终 156.7 MiB |
| 是否应合并到 Java APK | 不应 | 两套 compiler、minSdk、协议、运行库、patch、缓存和发布故障域会重新耦合 |
| 是否应直接抽共享源码库 | 暂不 | 60 个同名文件中只有 34 个包名归一化后逐字节相同，26 个核心文件已经分化 |

所以 M10-3 的交付不是新增 Kotlin production 代码，而是承认并验证正确的仓库边界，避免 Java 仓重复
实现 sibling 已经解决的问题。

## 已实现 Kotlin Provider 基线

审计提交的发布面：

| 字段 | Kotlin sibling | 当前 Java Provider |
| --- | --- | --- |
| Application ID | `io.github.supermonster003.autojs6.plugin.kotlin.runtime` | `io.github.supermonster003.autojs6.plugin.java.runtime` |
| Provider ID | `kotlin-jvm` | `ecj-java` |
| 语言 / compiler family | `KOTLIN` / `KOTLIN_JVM` | `JAVA` / `ECJ` |
| Source compiler | Kotlin/JVM K2 2.3.21 | ECJ 3.26.0 |
| D8 | 8.13.17 | 8.13.23 |
| Script bytecode | JVM target 1.8 / major ≤52 | Java 8 / major ≤52 |
| Android floor | minSdk 26 | minSdk 24 |
| 冻结协议 | Protocol 1.1 / Entry API 2 | Protocol 1.6 / Entry API 4 |
| 最低 Host | version code 5276 | version code 5281 |
| 源码形态 | 单 `.kt`，当前 Host 默认 `Main.kt` | 单文件或 2–32 文件包，显式入口可非 `Main` |
| 受控运行库 | Android/API 24 stubs、Entry API、stdlib、coroutines-core | Android/API 24、受控 core library desugaring、Entry API |
| Release shrink | compiler graph 不做 R8/resource shrink | Release R8/L8 门禁 |

这个差距说明 Kotlin 支持已经存在，但 sibling 还没有自动获得 Java Provider 在 M9 中新增的 Protocol
1.2–1.6 功能。它不是“把语言 enum 从 JAVA 改成 KOTLIN”就能同步的镜像。

## Android compiler 补丁边界

原始 `kotlin-compiler-embeddable:2.3.21` 不是可直接信任的 Android runtime。sibling 的
`patchKotlinCompilerForAndroid` 对精确 class/method/resource 形状 fail closed：

1. `PerformanceManager` 的三个允许方法去除 `java.lang.management` 直接调用；
2. `DefaultJava11Shim` 的 concurrent-long map 重定向到 Android 兼容实现；
3. `PathUtil` 的 compiler resource root 解析重定向到受审计 helper；
4. `KotlinCoreEnvironment.Companion` 的 extension 注册重定向到固定实现；
5. 只允许映射清单中的 `java.awt`、`java.beans`、`javax.swing` 类型进入 headless desktop stub；
6. 精确验证 `META-INF/extensions/compiler.xml` 摘要，移除签名并生成确定性 patched JAR；
7. 预期 class、method descriptor、调用次数、desktop type 或 resource digest 任一漂移都会让构建失败。

`KotlinJvmCompiler` 又把执行 profile 固定为 `noJdk/noStdlib/noReflect`、禁 standard scripts、禁 compiler
plugin、受控 classpath、JVM target 1.8，并在每次真实 K2 调用后执行
`KotlinCoreEnvironment.disposeApplicationEnvironment()`。这些条件是 2.3.21 可行性的组成部分，不能把
结论外推为“任意新版 compiler-embeddable 都能在 Android 上运行”。

### Compiler 工件

| 工件 | 字节数 | SHA-256 |
| --- | ---: | --- |
| 原始 `kotlin-compiler-embeddable-2.3.21.jar` | 59,720,376 | `d3e70fb011675e77ef00f1a68918d3cd91eed26058977d1e3ea2a37a293233af` |
| Android patched compiler JAR | 60,827,634 | `de6a4fe1b1fe0fdaaa0a12a3ba6e217582562445ae8e66711350d91666513a06` |

patched JAR 比原始 JAR 大 1,107,258 bytes，来自固定兼容改写/重打包，并不作为脚本 classpath 暴露。
脚本只看到受控 Android stub、Entry API、stdlib 与 coroutine core。

## 体积评估

### 干净提交复现产物

2026-08-27 在 detached `aa5b55f` worktree、JDK 21、AGP 9.3.0、Gradle 9.6.1 下离线强制重建：

| 工件 | 字节数 | SHA-256 |
| --- | ---: | --- |
| Debug APK | 39,922,225 | `95e6c76e9b4b4502ac0e2f328bd1b6da5c6e9ec38e21af13fcd00b96c69e99f9` |
| Release APK | 31,855,566 | `1b5452cb632b1542f85d3918ee293fcd95beb8efe632e0487529908c89b088bb` |
| AndroidTest APK | 442,813 | `820cb2086d96239a55cae7b7ec4811ed1278202a157209cd9dce5c16f9e78fc9` |
| M7 harness APK | 1,877,589 | `c3089b214fbf08a36f9f7a869f0e34c966274d6380707bdfb2bf714e5f116bb3` |

canonical tagged Release 是 31,855,642 bytes、SHA-256
`c8837d5293dfcf75da12a2352d628c69304b586fe799f61f18121513884586d1`。detached 本地重建仅小 76
bytes；canonical tag 工件仍是发布身份，本次重建只证明源码/依赖/门禁可复现，不替换发布摘要。

Release APK 结构拆解：

| 内容 | 数量 | APK 内压缩字节 | 原始字节 |
| --- | ---: | ---: | ---: |
| `classes*.dex` | 8 | 22,645,012 | 63,168,436 |
| `assets/compiler-classpath/*` | 4 | 5,876,889 | 6,687,629 |
| 其中 class-only API 24 `android.jar` | 1 | 2,742,560 | 3,300,306 |
| 其中 Kotlin stdlib | 1 | 1,683,663 | 1,804,721 |
| 其中 coroutines core | 1 | 1,446,189 | 1,577,052 |
| 其中 Entry API | 1 | 4,477 | 5,550 |

compiler/runtime DEX 是主要体积，但独立 APK 仍比 40 MB decimal 预算低约 8.14 MB。当前 Java M10-2
Release 约 27.13 MB；两者都能单独发布。不能用 `31.86 + 27.13` 简单推导合并 APK，因为协议、Android
stub、D8、worker 与公共库可能去重；但合并一定会把 Kotlin 的 8-DEX compiler graph、补丁资产和
不可 shrink 边界带入 Java 包。既然宿主已经支持按语言选择独立 Provider，没有承担该耦合成本的收益。

## 运行时与内存评估

### 本轮 API 26 ART 证据

在冷引导 `DEX_R1_API26_X64` 上安装上述 Debug/AndroidTest APK，设备为 API 26 / x86_64，fingerprint：

`Android/sdk_phone_x86_64/generic_x86_64:8.0.0/OSR1.180418.004/4931640:userdebug/test-keys`

`KotlinProviderPipelineInstrumentedTest` 为 `OK (1 test)`，runner `Time: 5.468`。它实际执行：

```text
patched K2 2.3.21
  → JVM 1.8 classes
  → deterministic JAR / descriptor identity
  → D8 8.13.17 (minApi 24)
  → strict DEX structure + digest validation
  → API 26 single-buffer InMemoryDexClassLoader
  → ART load / AutoJsJvmEntry invocation
  → true
```

该测试不绕过 production compiler、D8、DEX validator 或 loader；它只是不冒充 Host discovery/Binder
证据。测试前设备没有 Kotlin Provider/Test package，完成后两包已卸载、package path 为空，专用 AVD
已关闭。

### sibling 既有 API 31 production 证据

canonical `v0.7.0-m10` 已在 Sony XQ-AT72 / API 31 / arm64-v8a 上通过真实 AutoJs6 Binder
回归 `1/1`（5.022 s）：结构化 coroutine 返回 `sum=30;mainUnavailable=true`，一次认证
`app.launch`，随后完成 `REQUESTED / EXECUTION` 取消；解绑后 Provider/harness 辅助进程数为 0，设备
回拉 APK 与 canonical SHA-256 完全一致。完整记录在 sibling 的
[`0.7.0-m10 release evidence`](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/aa5b55f41e129b2ce09c7deee5fce5098caa0c81/docs/releases/0.7.0-m10.md)。

本轮只读确认 API 31 仍安装 `0.7.0-m10` / version code 7；没有安装、覆盖、卸载或运行调试 harness。

### 生命周期事实

sibling M7 已证明 compiler application dispose 不等于无限期 ART 内存安全：故意关闭缓存并让同一
compiler process 连续跨两个 50-session epoch 复用时，第二批 midpoint PSS 达 706,523,136 bytes，
final PSS 达 1,893,345,280 bytes。文件描述符和 workspace 均稳定，问题来自 compiler/ART 状态滞留，
不能用常规文件清理掩盖。

采纳 binding-scoped compiler epoch 后，一次 50-session 审计为：

- PSS：131,467,264 → 135,791,616 → 164,301,824 bytes；
- 总增长 32,834,560 bytes；final 约 156.7 MiB，低于 256 MiB budget；
- descriptor `79 → 78`，private workspace 为 0；
- 40 个成功执行使用 40 个不同 worker PID；
- Host final unbind 后 compiler/worker 均不存在。

缓存冷编译中位数 577 ms，认证 cache hit/materialization 中位数 46 ms，约 12.5×；所以“每请求新建
compiler process”和“无限保持一个 compiler process”都不合适。现有 binding epoch 是在启动成本和
内存隔离间已经过设备证据支持的边界。详细数据见 sibling 的
[`compiler lifecycle decision`](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/aa5b55f41e129b2ce09c7deee5fce5098caa0c81/docs/decisions/compiler-lifecycle.md)
与
[`compile latency baseline`](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/aa5b55f41e129b2ce09c7deee5fce5098caa0c81/docs/perf/compile-latency.md)。

## 干净构建门禁

detached audit 使用 `--offline --no-daemon --rerun-tasks`：

1. repository-local platform build logic：5 suites、55/55 tests；7/7 task 实际执行；
2. `patchKotlinCompilerForAndroid`：通过全部 class/method/resource/desktop-type 形状断言；
3. `verifyKotlinCompilerRuntime`：通过 patched compiler、Trove、stdlib 与受控资产验证；
4. `verifyPinnedInputs`：通过；dirty provenance 和一字节协议 AAR 篡改的 failure path 均按设计拒绝；
5. app 单测：45 suites、153/153，0 failure/error/skipped；
6. app Lint：0 new issue，13 项既有 baseline；harness Lint：clean；
7. Debug、Release、AndroidTest 与 M7 harness APK：全部构建成功；
8. aggregate：`BUILD SUCCESSFUL`，193/193 task 实际执行，4m14s。

这组结果独立复核了 sibling release 文档，而不是只引用它声称通过的历史数字。

Java 主项目也在本记录完成后运行标准 `scripts/verify.ps1 --no-daemon --rerun-tasks
--console=plain` 离线门禁：Debug/Release 各 44 suites、164/164 tests，0 failure/error/skipped；
Debug Lint 为 0 error、29 项既有 warning；Debug APK 重新组装成功；aggregate 为
`BUILD SUCCESSFUL`，92/92 task 实际执行，1m43s。M10-3 没有修改 Java 生产代码、协议工件、依赖或 APK
版本。

## 为什么不合并到 Java Provider

### 1. 平台与工具链故障域不同

Java Provider 必须维持 API 24；Kotlin compiler graph 当前以 API 26 为最低运行边界。Java 的 ECJ
3.26.0 无 Android 字节码重写，Kotlin 2.3.21 则依赖精确上游形状的 patched JAR。把两者放在一个 APK
会让任一 compiler 升级、R8 keep 规则、desktop/JDK 引用或 minSdk 问题阻断两种语言发布。

### 2. compiler 生命周期不同

ECJ 与 K2 的启动、全局 application/VFS、dispose、PSS 与 hard-retire 风险不同。一个统一 compiler
service 要么按 K2 最严格策略牺牲 Java 路径，要么产生条件化生命周期，扩大难以审计的状态空间。

### 3. 协议功能已经分叉

Java 已到 Protocol 1.6 / Entry API 4，并包含剪贴板、参数、运行时异常类、协议化 observation、多文件
源码包和任意入口；Kotlin release 仍为 1.1 / Entry API 2。共享旧 session 实现会让 Java 回退，直接
复制新实现又会绕过 Kotlin source/runtime 的逐项威胁建模。

### 4. 运行库边界不同

Kotlin 脚本需要 stdlib 和精确 coroutine core，明确拒绝 Android Main、完整 reflect、serialization、
compiler plugin 与任意 Maven 依赖。Java 使用不同的 core-library desugaring profile。把 classpath
合并会增加用户源码意外可见 API 和 cache/runtime fingerprint 组合。

### 5. 安装与回滚独立更安全

宿主已经通过 provider language/capabilities 选择实现。独立 application ID 允许 Kotlin compiler
升级失败时回滚 Kotlin APK，而 Java 用户继续使用 ECJ；也允许设备只安装所需语言，避免无条件承担
约 31.86 MB Kotlin 包体和 compiler PSS。

## 共享策略

### 现在共享

- Host 生成并冻结的 `common-plugin-api`、`protocol-wire-api`、`jvm-source-api` AAR；
- protocol negotiation、capability、cache canonical encoding、DEX profile 和错误码的规范；
- golden byte vectors、恶意输入 corpus、设备矩阵定义与发布证据 schema；
- 可在两仓分别执行的 conformance tests，要求行为一致但不要求源码来自同一文件；
- 上游变更时的联合 review checklist。

### 现在不共享

- Android `Service`、process/manifest ownership、signing 或 BuildConfig；
- compiler invoker、classpath、source policy、diagnostic sanitizer；
- compiler process lifetime 与内存退休实现；
- cache implementation revision、runtime-library fingerprint 或 release version；
- 一个插件 APK内的 production classes/JAR 给另一个插件动态加载。

34 个归一化后逐字节相同文件可作为未来提取候选，但不能仅以代码行重复为理由建立跨仓依赖。优先把它们
转换为同一组 conformance vectors；等两边协议重新对齐且至少两个发布周期证明行为稳定后，再评估
`jvm-provider-conformance` 之类的纯 Kotlin/JVM 测试模块，production Android runtime 模块仍保持独立。

## Kotlin 版本现状

JetBrains 官方 release history 显示：2.3.21 发布于 2026-04-23；当前稳定版已经是
[`2.4.10`](https://github.com/JetBrains/kotlin/releases/tag/v2.4.10)，发布于 2026-07-14；2.4.20
计划在 2026-09 发布。参见
[`Kotlin release process/history`](https://kotlinlang.org/docs/releases.html)。

因此本次结论精确表述为：**2.3.21 的 Android-targeted sibling 实现已证明可行，但不是对 2.4.10 的
兼容声明。** 2.4.10 必须在 Kotlin 仓单独完成其既有 SOP：

1. 上游差异和最终 JAR API/JDK/desktop 引用审计；
2. 原有四类 patch 的 class/method/resource 形状锁不能放宽；
3. patched runtime verify、缓存 compiler identity 失效与诊断 snapshot review；
4. 离线全量单测、双 Lint、四类 APK、source archive rebuild；
5. API 26 minimum ART、API 31 production Binder、50-session PSS/retirement 与 latency 重测；
6. 任一 patch 形状或 PSS gate 失败即保留 2.3.21，不做部分升级。

同理，Kotlin sibling 的 D8 8.13.17 可独立评估升级至 8.13.23，但必须更新其 D8 version、toolchain/cache
identity、R2 断言并跑自己的设备矩阵；不能因为 Java M10-2 已通过而直接继承结论。

## 后续收敛顺序与所有权

后续工作属于 Kotlin sibling 的新里程碑，不在 Java APK 内实现：

1. 从当前 Host 的可达、干净 commit 刷新三份协议 AAR和 lock provenance；
2. 建立 Protocol 1.1→1.6 / Entry API 2→4 差距矩阵，逐项实现或明确不适用；
3. 优先对齐 negotiation、缓存身份、错误/observation wire 与 capability 授权，再扩展 Kotlin 源码形态；
4. 单独评估 Kotlin 2.4.10；不要与协议升级、D8 升级或 JVM target 变更合并成一个候选；
5. 单独评估 D8 8.13.23，并保留 Kotlin compiler patch 三件套；
6. 每个阶段继续使用独立 provider ID/application ID、独立版本与可回滚发布。

Java 仓只负责在 M10-3 记录边界，并在协议/安全规范变化时与 Kotlin 仓做联合 conformance review。

## 清理与完整性

- Kotlin 原主工作区的 4 个 tracked 文档改动和 `.changelog/`、`.python/`、`.readme/`、`app/release/`
  未跟踪内容在审计前后保持存在，本轮未修改或暂存；
- detached audit worktree 的临时签名配置和 keystore 副本已删除，随后 audit worktree 及可重建 build
  输出已移除；Windows 长路径导致第一次目录清理未完成，确认 Git 已注销且绝对路径正确后使用 .NET
  long-path API 删除，最终目录不存在；
- API 26 AVD 上临时安装的 Kotlin Provider/Test 已卸载并确认 package path 为空，AVD 已关闭；
- API 31 用户设备原有 Kotlin Provider `0.7.0-m10` / code 7 的 package path 保持存在；
- Java Provider production 代码、协议 AAR、依赖、版本和构建产物均未因 M10-3 改变。

这满足 M10-3 的 go/no-go 与架构建议验收：Kotlin 支持不再是远期占位，而是由独立 sibling 插件承担；
Java Provider 保持单语言、最小故障域和 API 24 合同。
