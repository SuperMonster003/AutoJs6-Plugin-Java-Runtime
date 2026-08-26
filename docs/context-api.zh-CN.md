# Java Context API 与运行边界

本文适用于 AutoJs6 Java Runtime Plugin <code>0.7.0-m9</code>、JVM Source Protocol
<code>1.5</code>、Entry API <code>4</code>，描述当前 Java 能力 profile 与 R4 DEX 工件 profile
的用户可见行为。
协议升级或后续 profile 可能扩展这些能力，但不会放宽当前请求已经协商出的边界。

## 最小可运行脚本

~~~java
import org.autojs.plugin.jvmsource.api.AutoJsJvmEntry;
import org.autojs.plugin.jvmsource.api.JvmScriptContext;

public final class Main implements AutoJsJvmEntry {
    @Override
    public Object run(JvmScriptContext context) throws Exception {
        context.console().log("Java worker started");
        context.toast("Hello from Java");
        context.sleep(250L);
        context.throwIfCancellationRequested();
        return 42;
    }
}
~~~

入口方法可以返回受支持的 JSON profile 值，也可以返回 <code>null</code>。完整能力样例见
[samples/m5-capabilities.java](../samples/m5-capabilities.java)；剪贴板读写样例见
[samples/capability-set-2-clipboard.java](../samples/capability-set-2-clipboard.java)；宿主参数样例见
[samples/script-args.java](../samples/script-args.java)；运行时异常投影样例见
[samples/runtime-exception.java](../samples/runtime-exception.java)。

## 源码形态

当前 profile 对源码有以下硬性约束：

- 一次请求只接受一个 Java 源文件。一个文件内可以声明辅助类和内部类，但不能提交多文件源码包。
- 入口类的简单名固定为 <code>Main</code>。宿主侧脚本文件名可以任意；传入编译器的逻辑编译单元名必须与入口简单名一致，即 <code>Main.java</code>。
- 可以省略 <code>package</code>，也可以使用合法包名。使用包名时，入口仍是该包中的 <code>Main</code>。
- <code>Main</code> 必须是 public、非 abstract、非 interface，必须实现 <code>AutoJsJvmEntry</code>，并具有 public 无参构造器。
- 编译结果中必须恰好有一个具体类实现 <code>AutoJsJvmEntry</code>；存在第二个具体实现时会以入口歧义拒绝。
- 入口签名为 <code>public Object run(JvmScriptContext context) throws Exception</code>。可以省略 <code>throws Exception</code>，也可以使用协变返回类型。
- ECJ 使用 <code>-source 8 -target 8</code>、UTF-8 和禁用注解处理器。Java 8 指语言级别；完整 Android
  平台面仍来自受控的 API 24 stub，另有窄化的 <code>java.time</code>/增强 Stream core-library stub 与
  Entry API，并不等同于完整桌面 JDK 8 类库或高版本 Android SDK。
- Lambda 语法能通过源码词法策略，但当前 API 24 编译 stub 不含 ECJ 生成 lambda class 所需的
  <code>java.lang.invoke</code> 类型，因此不属于已验证的可运行 profile；请使用匿名类。这里不会为了
  lambda 整体开放 API 26 的 invoke 表面，以免制造可编译但 API 24 运行时缺类的新漏洞。
- 输入必须是严格 UTF-8；仅允许文件开头存在一个 UTF-8 BOM，插件会在编译前移除它。畸形 UTF-8 和 NUL 字符会被拒绝。
- 源码任何位置都禁止 Java Unicode escape 形式 <code>&#92;uXXXX</code>，包括注释、字符串和字符字面量。这避免词法检查结果被 Java 编译器的 Unicode 预处理重新解释。
- 当前 R4 D8 profile 接受 1 至 4 个严格连续命名的工件：<code>classes.dex</code>、
  <code>classes2.dex</code>、<code>classes3.dex</code>、<code>classes4.dex</code>。不允许缺号、别名、重排或
  第 5 个 DEX；所有 DEX 的总量仍受同一个 32 MiB 上限约束。该集合只在 provider 的编译器与隔离
  worker 之间传递，不改变宿主侧单源码请求形态。
- Android API 26 的公开 <code>InMemoryDexClassLoader</code> 只有单 buffer 构造器，因此该版本继续只接受
  一个 <code>classes.dex</code>；API 24/25 和 API 27+ 才能安全共享 2 至 4 个 DEX 的同一 class namespace。

### 受控 core library desugaring

当前额外支持 API 26 的 <code>java.time</code> 表面，以及设备证据确认可承接的增强 Stream
三参数 <code>iterate</code> 和 <code>toList</code>。例如
[core-library-desugaring.java](../samples/core-library-desugaring.java) 可在
API 24 返回 <code>2024-03-01:CORE</code>。

这不是把 bootclasspath 整体提高到 API 26+：高版本 Android framework API、完整
<code>java.nio.file</code>、<code>Stream.ofNullable</code>、<code>takeWhile</code>、
<code>dropWhile</code> 和 <code>mapMulti*</code> 均不属于当前编译 profile。这些方法会在 ECJ 阶段
失败，而不会编译成功后留到旧设备抛缺方法异常。具体 go/no-go
证据、D8 配置与 Release 运行库策略见
[M8-3 评估记录](core-library-desugaring.zh-CN.md)。

## JvmScriptContext API

每项调用都受会话协商的 capability 约束。调用未授予的能力不会静默降级，而会终止本次执行。

| 方法 | 行为与返回值 | 重要边界 |
|---|---|---|
| <code>app()</code> | 返回 <code>JvmAppApi</code> 视图。 | 需要 <code>APP_LAUNCH</code> 才能调用其中的方法。 |
| <code>app().launch(String packageName)</code> | 请求宿主启动指定包，返回宿主给出的 boolean 结果。 | 包名必须至少包含两个以点分隔的合法段，例如 <code>com.example.app</code>；wire 方法固定为 <code>app.launch</code>。宿主拒绝、响应畸形或 10 秒内无响应会令脚本失败。 |
| <code>args()</code> | 返回宿主在本次执行开始前冻结的 <code>Map&lt;String, Object&gt;</code> 参数快照；没有参数时返回空 Map。 | Entry API 4；不需要 capability，也不会产生 host call。Map、嵌套 Map 与 List 均不可修改。 |
| <code>clipboard()</code> | 返回 <code>JvmClipboardApi</code> 视图。 | 读与写使用两个独立 capability；获得其中一个不会隐式获得另一个。Android 10+ 调用时 AutoJs6 必须有 resumed 前台 Activity。 |
| <code>clipboard().getText()</code> | 返回宿主当前纯文本剪贴板；不存在文本时返回空字符串。 | 需要 <code>CLIPBOARD_READ</code>；wire 方法固定为 <code>clipboard.get</code>，请求只接受精确 <code>{}</code>。 |
| <code>clipboard().setText(String text)</code> | 用给定纯文本替换宿主剪贴板。 | 需要 <code>CLIPBOARD_WRITE</code>；wire 方法固定为 <code>clipboard.set</code>；允许空字符串，文本最多 16 KiB UTF-8。 |
| <code>console()</code> | 返回 <code>JvmConsoleApi</code> 视图。 | 需要 <code>CONSOLE_STREAM</code>。 |
| <code>console().log(String message)</code> | 以 UTF-8 向 stdout 写入消息和一个换行。 | stdout 单独计入 1 MiB 上限；写入前检查取消。 |
| <code>console().error(String message)</code> | 以 UTF-8 向 stderr 写入消息和一个换行。 | stderr 单独计入 1 MiB 上限；写入前检查取消。 |
| <code>sleep(long millis)</code> | 可取消的休眠。 | 需要 <code>SLEEP</code>；参数不得为负；取消会中断休眠并抛出 <code>JvmCancellationException</code>。休眠仍受会话总超时约束。 |
| <code>toast(String message)</code> | 请求宿主显示 toast。 | 需要 <code>TOAST</code>，wire 方法固定为 <code>toast.show</code>；消息 UTF-8 编码不得超过 8 KiB。宿主拒绝或响应畸形会令脚本失败。 |
| <code>cancellation()</code> | 返回只读的 <code>JvmCancellation</code> 视图。 | 不授予取消请求的能力；仅用于观察当前会话状态。 |
| <code>cancellation().isCancellationRequested()</code> | 非阻塞查询是否已收到取消。 | 适合长循环定期轮询。 |
| <code>cancellation().throwIfCancellationRequested()</code> | 已取消时抛出 <code>JvmCancellationException</code>。 | 建议在每个有界工作单元之间调用。 |
| <code>isCancellationRequested()</code> | Context 上的便利方法，等价于 cancellation 视图的同名方法。 | 自 Entry API 2 起提供的默认方法。 |
| <code>throwIfCancellationRequested()</code> | Context 上的便利方法，等价于 cancellation 视图的同名方法。 | 自 Entry API 2 起提供的默认方法。 |

### 脚本参数 profile

AutoJs6 把 <code>ExecutionConfig.arguments</code> 在 provider 绑定之前编码成一个确定性 JSON object；对象
key 按字符串自然顺序排列，随后作为 Protocol 1.3 请求字段传入 worker。参数值只允许以下形态：

- <code>null</code>、<code>Boolean</code>；
- <code>Byte</code>/<code>Short</code>/<code>Integer</code>/<code>Long</code>/<code>BigInteger</code>；
- 有限的 <code>Float</code>/<code>Double</code> 与合法 <code>BigDecimal</code>；
- <code>Character</code>/<code>CharSequence</code>；
- key 全为 <code>String</code> 的 <code>Map</code>、<code>Iterable</code> 与任意 Java 数组，元素递归遵守本表。

解码后，整数在 <code>Long</code> 范围内以 <code>Long</code> 暴露，更大的整数为 <code>BigInteger</code>；
小数或指数形式为 <code>BigDecimal</code>。根对象、嵌套对象和数组分别以深层不可变的 Java
<code>Map</code>/<code>List</code> 暴露；脚本不能借参数引用修改宿主原对象，宿主在开始执行后再修改原
集合也不会改变已发出的快照。

整个 JSON 最多 65,536 UTF-8 bytes，容器嵌套最多 64 层，数字文本最多 256 字符。引用环、非字符串
Map key、NaN/无穷、非配对 surrogate、普通 POJO、Android/宿主对象与其他未列类型会在绑定 provider
之前拒绝，并映射为稳定错误 <code>JVM_SOURCE_INVALID_ARGUMENTS</code> 与恢复动作
<code>FIX_INVOCATION</code>。外部 Android <code>Intent</code> 不会自动作为 JVM 参数暴露；调用方必须通过
显式 <code>ExecutionConfig.setArgument</code> 提供 JSON profile 值。参数内容不会进入请求
<code>toString()</code>、公开错误或观测记录。

旧 Protocol 1.2 请求没有参数字段，当前解码器把它解释为空对象；使用旧式
<code>run(JvmScriptContext)</code> 且不调用 <code>args()</code> 的源码可以原样针对 Entry API 4 编译运行。
完整的 wire/ABI 决策与设备证据见
[M9-2 脚本入参实现与证据](script-arguments-m9-2.zh-CN.md)。

Android 10 起，平台只允许获得输入焦点的应用或少数系统角色访问剪贴板。宿主因此在执行
<code>clipboard.get/set</code> 前检查 AutoJs6 是否仍有 resumed Activity；真正后台状态会在触碰系统
剪贴板前失败，并只向 worker 返回稳定的 <code>CLIPBOARD_GET_FAILED</code> 或
<code>CLIPBOARD_SET_FAILED</code>。这也避免把平台的静默拒绝误报为写入成功。完整协商和设备证据见
[M9-1 剪贴板能力实现与证据](capability-set-2-m9-1.zh-CN.md)。

脚本执行期间，<code>System.out</code> 和 <code>System.err</code> 也会被重定向到同一组受限输出通道。
因此它们与 <code>console().log/error</code> 共享各自的字节预算。任一通道超限都会得到
<code>OUTPUT_LIMIT_EXCEEDED</code>，不会只截断后继续成功。

### 取消建议

CPU 密集型循环不会自动产生安全检查点，应主动轮询：

~~~java
for (int i = 0; i < workItems.size(); i++) {
    context.throwIfCancellationRequested();
    process(workItems.get(i));
}
~~~

不要捕获 <code>JvmCancellationException</code> 后继续无限运行。取消拥有有限宽限期；不合作的 worker
最终会被直接终止。宿主看到的取消原因可能是：

- <code>REQUESTED</code>：用户或宿主主动取消；
- <code>TIMEOUT</code>：会话总时限用尽；
- <code>SESSION_CLOSED</code>：宿主关闭会话或 provider 被销毁；
- <code>HOST_DIED</code>：宿主 Binder 端点消失。

## 返回值 JSON profile

<code>run</code> 的返回值在隔离 worker 中编码为 JSON；不会对任意对象进行反射序列化。

| Java 返回类型 | JSON 结果 | 备注 |
|---|---|---|
| <code>null</code> | <code>null</code> | 支持。 |
| <code>Boolean</code> | boolean | 支持。 |
| <code>Byte</code>、<code>Short</code>、<code>Integer</code>、<code>Long</code>、<code>BigInteger</code> | number | 数字文本最长 256 个字符。 |
| <code>Float</code>、<code>Double</code>、<code>BigDecimal</code> | number | NaN、正无穷和负无穷会被拒绝。 |
| <code>CharSequence</code>、<code>Character</code> | string | 非配对 surrogate 会替换为 U+FFFD。 |
| key 全为 <code>String</code> 的 <code>Map</code> | object | value 递归遵守本表；非字符串 key 会被拒绝。 |
| <code>Iterable</code> | array | 元素递归遵守本表。 |
| 任意 Java 数组，包括基本类型数组 | array | 元素递归遵守本表。 |

以下返回值会以 <code>EXECUTION_FAILED</code> 拒绝：

- 普通 POJO、record、enum、任意未列出的 <code>Number</code> 子类；
- 包含非字符串 Map key 的对象；
- 包含引用环的 Map、Iterable 或数组；
- 超过 64 层容器嵌套；
- UTF-8 JSON 编码超过 64 KiB；
- 非有限浮点数或不符合 JSON number 语法的数值。

例如：

~~~java
Map<String, Object> result = new LinkedHashMap<>();
result.put("ok", true);
result.put("items", Arrays.asList(1, 2, 3));
return result;
~~~

## 资源与协议上限

除默认值外，宿主可以在请求中协商更小的预算，但不能超过下表硬上限。

| 项目 | 默认值 / 上限 | 超限结果 |
|---|---:|---|
| Java 源码 | 上限 4 MiB（4,194,304 bytes） | <code>SOURCE_TOO_LARGE</code> |
| stdout | 上限 1 MiB（1,048,576 bytes） | <code>OUTPUT_LIMIT_EXCEEDED</code> |
| stderr | 上限 1 MiB（1,048,576 bytes） | <code>OUTPUT_LIMIT_EXCEEDED</code> |
| 编译诊断总 wire 预算 | 上限 64 KiB（65,536 bytes） | 超出预算的诊断不再回传 |
| 单条诊断消息 | 上限 4,096 Unicode code points | 安全截断 |
| 返回值 JSON | 上限 64 KiB（65,536 UTF-8 bytes） | <code>EXECUTION_FAILED</code> |
| JSON 容器深度 | 上限 64 层 | <code>EXECUTION_FAILED</code> |
| 默认会话超时 | 30 秒 | 取消原因 <code>TIMEOUT</code> |
| 会话超时硬上限 | 120 秒 | 请求验证失败或按上限协商 |
| 单次 host payload | 上限 64 KiB | host call 被拒绝 |
| toast 消息 | 上限 8 KiB UTF-8 | <code>EXECUTION_FAILED</code> |
| 剪贴板文本 | 上限 16 KiB UTF-8，仍受 64 KiB host wire payload 总上限约束 | host call 被拒绝或 <code>EXECUTION_FAILED</code> |
| JVM class 文件总量 | 上限 16 MiB，最多 256 个 class | <code>COMPILATION_FAILED</code> |
| class JAR 协议工件 | 上限 20 MiB | <code>ARTIFACT_INVALID</code> 或编译失败 |
| DEX 工件集合 | 总量上限 32 MiB；API 24/25、27+ 为 1 至 4 个，API 26 为 1 个 | <code>DEXING_FAILED</code> 或 <code>ARTIFACT_INVALID</code> |
| 终态 observation | 独立 wire 上限 4 KiB；最多 6 个资源样本 | 畸形、超限、错绑或协商后缺失会被 Host 隔离 |
| 并发会话 | 1 | 第二个会话返回可重试的 <code>BUSY</code> |

30 秒默认超时覆盖整次会话，而不只是 <code>run</code>：源码读取、ECJ、D8、worker 启动与执行都会消耗该预算。

## 终态观测

Protocol 1.5 的成功、错误与取消终态都会在 compiler/worker 清理完成后携带同一类有界 observation。
AutoJs6 控制台显示固定摘要，包括：

- Provider session 总耗时和 worker 启动耗时；
- ECJ、D8、DEX load、入口 run 与 termination 耗时；
- 当次 cache 的 <code>NOT_EVALUATED</code>、<code>HIT</code> 或
  <code>MISS/reason</code>，以及 compiler/worker 的 <code>COLD</code>/<code>WARM</code>；
- 最多六个 compiler/worker 样本汇总出的 peak RSS、最大 open FD、临时工件字节和输出字节。

不可用字段显示为 <code>n/a</code>，与真实的零毫秒/零字节不同。cache hit 时 ECJ 与 D8 没有执行，因此
两项必须为 <code>n/a</code>；不会用零伪装成已执行阶段。

协议对象不能表达源码、参数、诊断文本、异常、路径、package/component、签名、Binder identity、
UID 或 PID。用于终态绑定的 request ID 在 Host 公开投影前删除；Debug 私有 JSONL 的累计 cache 计数也
不会进入 Protocol 1.5。完整字段表、数值上限与 Release MISS/HIT 证据见
[M9-4 观测数据协议化](observation-protocol-m9-4.zh-CN.md)。

## 错误码语义

错误同时带有 <code>code</code>、<code>phase</code>、面向用户的脱敏消息和
<code>retryable</code>。当前只有并发冲突 <code>BUSY</code> 明确标记为可重试。

| 错误码 | 语义 |
|---|---|
| <code>INVALID_REQUEST</code> | 元数据、UTF-8、源码布局、包名、能力或其他请求字段不属于当前 Java R1 profile。 |
| <code>UNSUPPORTED_PROTOCOL</code> | 宿主请求的 Protocol 版本与 provider 支持范围不相交。 |
| <code>UNSUPPORTED_LANGUAGE</code> | 请求语言不是当前 provider 广告的 Java。 |
| <code>SOURCE_TOO_LARGE</code> | 源码流超过声明大小或 4 MiB 硬上限。 |
| <code>COMPILATION_FAILED</code> | ECJ 报错，或 class 输出数量、大小、格式不满足编译 profile。 |
| <code>DEXING_FAILED</code> | D8 失败，或输出不满足连续命名、总量及当前设备文件数限制。 |
| <code>EXECUTION_FAILED</code> | 入口构造器或 <code>run</code> 抛异常、返回值无法编码，或 Context 调用在 worker 中失败。 |
| <code>OUTPUT_LIMIT_EXCEEDED</code> | stdout 或 stderr 超过各自协商预算。 |
| <code>TIMEOUT</code> | 协议级超时错误。当前正常的会话看门狗通常通过取消终态和 <code>TIMEOUT</code> cancellation reason 报告。 |
| <code>WORKER_DIED</code> | disposable worker 崩溃、Binder 消失、启动失败或违反内部调用身份/次序协议。 |
| <code>BUSY</code> | 已有一个活动会话；当前唯一明确可重试的错误。 |
| <code>HOST_CALL_REJECTED</code> | host bridge 方法未授权、不在双侧白名单、payload 非法，或宿主拒绝执行。 |
| <code>INTERNAL</code> | provider 内部故障，无法归入更具体且安全公开的类别。 |
| <code>ARTIFACT_INVALID</code> | 源码 framing/SHA-256、JAR、DEX、缓存工件或受控编译类路径未通过完整性/结构校验。 |
| <code>ENTRY_POINT_MISSING</code> | 未生成请求的 <code>Main</code>，或它没有实现 <code>AutoJsJvmEntry</code>。 |
| <code>ENTRY_POINT_AMBIGUOUS</code> | 编译结果包含多个具体的 <code>AutoJsJvmEntry</code> 实现。 |
| <code>ENTRY_POINT_ABI_INCOMPATIBLE</code> | 入口不是 public concrete class、缺少 public 无参构造器，或与 Entry API 不兼容。 |
| <code>CLASS_LOADING_FAILED</code> | ART 无法从已验证的用户 DEX 装载/链接入口，或入口并非由用户 DEX ClassLoader 定义。 |

<code>phase</code> 用于定位失败阶段：

- <code>NEGOTIATION</code>：协议、能力或并发协商；
- <code>INPUT</code>：源码读取、编码、大小、摘要与布局；
- <code>COMPILATION</code>：ECJ 与 class/JAR 校验；
- <code>DEXING</code>：D8 输出；
- <code>WORKER_START</code>：worker 绑定、DEX 校验/ClassLoader 与入口装载；
- <code>EXECUTION</code>：构造入口、运行脚本、输出与返回值；
- <code>HOST_BRIDGE</code>：宿主能力调用；
- <code>CLEANUP</code>：终态资源回收。

## 诊断与隐私

- ECJ 诊断在回传前会限制总字节数和消息长度；私有绝对路径、摘要、uid/pid、Binder 引用和其他敏感元数据会按片段替换为 <code>&lt;redacted&gt;</code>，其余可操作的编译器原文会保留。
- 一个源码包含多处 ECJ 问题时，provider 会按编译器顺序分别回传多条诊断；每条沿用 Protocol 1.4 引入的单诊断 frame，所有 frame 共用 64 KiB 总 wire 预算。
- 编译错误包含可安全确认的源码文件名、行和列。诊断预算耗尽时，后续诊断可能不再出现。
- Java 运行时异常回传通用的 <code>JAVA_RUNTIME_EXCEPTION</code>、安全源码位置与脱敏类名；
  <code>java.*</code>/<code>javax.*</code> 原样，其余精确归一为 <code>UserException</code>。异常 message、
  完整堆栈、绝对路径、provider/user package、UID/PID 均不回传；详见
  [M9-3 运行时异常类名实现与证据](runtime-exception-class-m9-3.zh-CN.md)。
- 错误消息是稳定的公开摘要，不应依赖内部异常文本进行程序逻辑判断；应使用 error code 与 phase。
