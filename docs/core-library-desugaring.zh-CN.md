# M8-3 用户代码 core library desugaring 评估与验证

本文记录 2026-08-25 的 M8-3 决策、实现边界与设备证据。结论为 **go，但只开放受控子集**：
用户源码的 D8 阶段接入固定版本 `desugar_jdk_libs_configuration_nio:2.1.5`；ECJ 继续以 Android
API 24 为完整平台边界，只额外看见能够由同一配置可靠承接的 `java.time` 与少量增强 Stream API。
不会把整套 API 26/30/34 Android stub 暴露给用户源码。

## 两步评估结论

### (a) 只接 D8 配置，不改变 API 24 编译 stub

单独给 D8 增加 `--desugared-lib` 可以改写已经存在于 class 文件中的引用，但 ECJ 仍先于 D8 执行。
原始 API 24 `android.jar` 不包含 `java.time`，也不声明 `Stream.toList()`；这两类源码会在 ECJ 阶段
失败，根本到不了 D8。因此，仅接配置对目标能力没有可见收益，不能单独作为 go 方案。

### (b) 提高 ECJ bootclasspath stub

把完整 `android.jar` 从 API 24 提高到 26 或更高会同时开放两类表面：

- 可由 core library desugaring 改写的 Java 核心库 API；
- 只存在于较高 Android 版本的 framework API。

第二类不在 `desugar_jdk_libs` 的职责范围内。例如 API 26 framework 类即使编译可见，在 API 24
运行时仍可能直接缺类。D8 的 core library 配置不能把它改写成兼容实现。故“整体抬高 stub + 文档告知”
判定为 **no-go**；编译器必须从结构上避免暴露这个表面，而不能把风险留到运行期。

## 最终受控方案

### ECJ 可见面

ECJ 的 bootclasspath 顺序为：受控 core-library stub 在前，完整 API 24 `android.jar` 在后；Entry API
仍是唯一普通 classpath。受控 stub 在构建时确定性生成，只包含：

- API 26 官方 SDK stub 中的全部 `java/time/**` class；
- API 34 官方 `java.util.stream.Stream` stub，但在写入前删除配置不支持的
  `ofNullable`、`takeWhile`、`dropWhile` 与四个 `mapMulti*` 方法。

当前增强 Stream 表面仅包含设备证据确认可承接的三参数 `iterate` 与 `toList`。构建任务按精确
method descriptor 验证两者必须存在，并验证 `ofNullable`、`takeWhile`、`dropWhile` 与
`mapMulti*` 必须不存在。因而三参数 `Stream.iterate(...).toList()` 可编译，而上述未支持方法在
ECJ 阶段即失败。

这不是完整桌面 JDK，也不是完整高版本 Android Java API。特别是 `java.nio.file` 目前仍不向用户源码
开放；依赖名中的 `_nio` 只说明插件 APK 内携带的 desugared runtime 变体，不代表 ECJ 自动获得整套
NIO 编译表面。样例使用匿名 `Predicate`/`UnaryOperator`：当前 API 24 stub 不含 ECJ lambda 输出所需的
`java.lang.invoke` 类型，lambda 尚未进入设备验证 profile，也不会借本项整体开放 API 26 invoke API。

### D8 库模型与配置

D8 使用 API 24 `android.jar`、Entry API，以及从 API 30 官方 `android.jar` 中仅抽出的 `java/**`
library stub。API 30 Java stub 只供 D8 解析固定配置所要求的核心库定义，不进入 ECJ bootclasspath，
因此不会扩大用户的编译可见面。这个拆分同时消除了用纯 API 24 D8 library 时出现的缺失
`java.nio.file` 定义警告。

- API 24/25 路径通过 D8 CLI 的 `--desugared-lib <private desugar.json>` 接入配置；
- API 26+ 路径通过 `D8Command.Builder.addDesugaredLibraryConfiguration(...)` 接入同一配置文本；
- 配置标识固定为 `com.tools.android:desugar_jdk_libs_configuration_nio:2.1.5`，并在构建时验证其
  `required_compilation_api_level` 仍为 30。

所有生成资产安装到 provider 私有 code-cache 目录、置为只读，并在 ECJ 前和 D8 前重新核对大小与
SHA-256 身份。

### 动态运行库与 Release

普通 Android 构建可以根据 APK 静态引用裁剪并混淆 L8 的 `j$` 输出，但用户 DEX 在 APK 构建完成后
才生成，AGP 的引用追踪无法预见它。若沿用默认 Release 行为，Debug 可运行的用户 DEX 可能在 Release
中因 `j$` 类被删除或改名而失败。

本项目因此对所有 L8 任务固定增加 `-dontshrink`、`-dontoptimize`、`-dontobfuscate`。Release 构建结束
前还会直接检查 L8 DEX 中必须存在精确描述符 `Lj$/time/LocalDate;` 与
`Lj$/util/stream/Stream$-EL;`。当前 Release APK 为 27,090,802 bytes；相对 M8-2 基线
25,489,373 bytes 增加 1,601,429 bytes（约 6.3%），这是保证动态用户 DEX 在发布构建中可链接的
明确成本。

## 被实验否决的候选

| 候选 | 实际结果 | 处理 |
|---|---|---|
| 直接把 `desugar_jdk_libs_nio` 的实现型 `Stream.class` 当编译 stub | 桌面实验可编译；API 24 上 ECJ 间接要求不存在的 `java.lang.invoke.MethodHandles` | 否决，改用 Android 官方 stub |
| 把 API 34 `Stream` stub 原样开放 | 同时包含 program-side 配置未承接的 `ofNullable`、`takeWhile`、`dropWhile` 与 `mapMulti*` | 在生成阶段删除这些方法 |
| 暂时保留 `ofNullable` | ECJ 与 D8 成功，但 API 24 ART 以 `NoSuchMethodError` 失败；DEX 中该调用未被改写 | 删除该方法并增加设备负向回归 |
| 暂时保留 `takeWhile`/`dropWhile` | 三参数 `iterate` 已正常执行，但 API 24 在 `dropWhile` 处以 `NoSuchMethodError` 失败 | 两个方法均从编译 stub 删除 |
| 仅在应用 ProGuard 规则中 `-keep j$.**` | L8 是独立 shrink/obfuscation 阶段，Release 中目标类仍被改名 | 改为直接配置 L8，并加构建后描述符断言 |

这些失败候选没有进入最终能力声明；它们用于证明“编译可见但运行时缺类”不是理论风险。

## 缓存与指纹

无需改变缓存 wire schema，既有 canonical key 已覆盖本次变化：

- 编译类路径身份域从 `compiler-classpath.v1` 升为 `v2`；指纹现在绑定 API 24 jar、Entry API、
  受控 core stub、D8-only API 30 Java stub 与 `desugar.json` 五个文件；
- ECJ options identity 记录受控 core-library bootclasspath；
- D8 options identity 新增配置版本与 API 30 Java library stub；
- 单测明确断言任意 D8 option 变化都会得到不同缓存 key，并验证篡改 `desugar.json` 会令已安装身份校验失败。

因此旧缓存会随工具链/运行库指纹和 options 自动失效，不需要依赖手工清目录。

## API 24 设备证据

设备为 `AVD_API_24`（`emulator-5558`），API 24 / x86，fingerprint：

`google/sdk_google_phone_x86/generic_x86:7.0/NYC/6696031:userdebug/dev-keys`

| 工件 | SHA-256 |
|---|---|
| Debug provider APK（31,546,798 bytes） | `7c89dc7d50682fee2a41f46e96d924e80747f8d59ec51cae960c615a853e5613` |
| AndroidTest APK（1,022,312 bytes） | `03d1ced11199ad24b749213022c14573ac2eeb1e3c015fcd75c446bf0db2f700` |
| Release provider APK（27,090,802 bytes） | `30570963cb12d0a13cfbbaa2e6e70ad26a442906accfcc7843466c13fe72d627` |

`JavaSampleLibraryInstrumentedTest` 最终为 6 tests、0 failure/error、3.160 s：

- 正向样例经真实 ECJ、D8、DEX 校验和 API 24 私有文件 `DexClassLoader` 执行，返回
  `2024-03-01:CORE`；
- 负向用例确认 `Stream.ofNullable` 仍在 ECJ 阶段失败；构建门禁同时排除
  `takeWhile`、`dropWhile` 与 `mapMulti*`；
- 其余返回值、取消、诊断和结果上限样例全部零回归。

该套件是 provider 内部真实流水线证据，不冒充宿主到 provider 的 canonical Binder 端到端证据；
后者仍由 AutoJs6 宿主仓负责。
