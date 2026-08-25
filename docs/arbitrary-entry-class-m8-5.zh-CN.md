# M8-5 任意入口类名：插件侧就绪证据

本文记录 2026-08-25 的 M8-5 审计、测试矩阵和 API 24 设备证据。结论为：**Java provider
内部已经能够端到端处理非 `Main` 入口，插件侧就绪；宿主公开行为仍固定为 `Main`，本项不提前开放
用户配置入口类名。**

本项没有修改 `app/src/main`、Protocol 1.1 AAR、Entry API 2、能力声明或最低宿主版本。改动只增强
unit/instrumentation fixture 与文档，用可执行证据固定既有参数化实现。公开端到端能力仍归 M9-7，届时
必须由宿主生成非 `Main` 请求并补宿主到 provider 的 canonical Binder 证据。

## 入口布局边界

插件与宿主当前共享 `JvmJavaSourceLayoutPolicy`。M8-5 按该冻结策略定义“合法布局”，不另造一套更宽的
类名语法：

- 入口简名和每个包名段使用 ASCII Java identifier shape：首字符为 `A-Z`、`a-z`、`_` 或 `$`，
  后续还可使用 `0-9`；
- 默认包入口为 `<SimpleName>`，有包入口为 `<package>.<SimpleName>`；
- 逻辑编译单元名必须精确为 `<SimpleName>.java`；
- class/JAR 内部名为将限定类名中的 `.` 换成 `/`，DEX descriptor 再包为 `L<internal-name>;`；
- 包声明仍由共享词法策略从源快照推导，Unicode escape、非 ASCII 包名、歧义包声明和请求布局不一致
  都在 ECJ 之前拒绝；
- 名称总长度继续受 Protocol 1.1 的 `MAX_ENTRY_CLASS_NAME_BYTES` 约束。

宿主 R1 仍把共享策略的默认简名 `Main` 传入检查器，因此普通用户源码继续得到 `Main.java` 与
`[package.]Main`。M8-5 只是证明 provider 内部没有同该默认值绑定。

## 3 × 3 非 Main 单元矩阵

测试把 3 种代表性简名与 3 种包布局做笛卡尔积，共 9 个非 `Main` 编译单元；既有默认 `Main` 用例
继续作为回归基线。

| 简名 \ 包布局 | 默认包 | 常规深包 `com.example.scripts` | `_/$` 边界包 `_root.$generated.p9` |
|---|---|---|---|
| `ScriptEntry` | `ScriptEntry` | `com.example.scripts.ScriptEntry` | `_root.$generated.p9.ScriptEntry` |
| `_Entry9` | `_Entry9` | `com.example.scripts._Entry9` | `_root.$generated.p9._Entry9` |
| `$Entry` | `$Entry` | `com.example.scripts.$Entry` | `_root.$generated.p9.$Entry` |

每个矩阵元素都生成自己的 `<SimpleName>.java`，并经过真实 ECJ Java 8 编译，而不是通过重命名
`Main.class` 模拟。

## 全链路审计与自动化证据

| 环节 | 参数化不变量 | M8-5 证据 |
|---|---|---|
| 请求/profile | `sourceFileName == entryClassName.substringAfterLast('.') + ".java"` | 非 `Main` 文件名/限定名匹配通过；错误文件名、错误包名、数字开头、非法连字符、空包段与非 ASCII 包名均得到 `INVALID_REQUEST / INPUT`。 |
| `JavaSourcePolicy` | 用请求简名调用共享布局检查，再要求推导出的完整布局与请求完全相等 | 9/9 合法布局保留同一规范源码。 |
| ECJ 输出与 `EntryClassAnalyzer` | 请求限定名转换为 class internal name；指定 class 必须是唯一具体 `AutoJsJvmEntry` 且具有公开无参构造器 | 9/9 源码经 ECJ 生成真实 class 后全部通过 ABI 分析。 |
| `UserClassJarWriter` / validator | JAR 必须包含精确 `<internal-name>.class`；缓存命中时从有界 class bytes 重建相同 ABI 摘要 | 9/9 JAR 路径与 `L<internal-name>;` descriptor 精确匹配，写后重验摘要完全相等。 |
| 缓存 key | `sourceFileName` 与 `entryClassName` 是两个独立 canonical 字段 | 单字段变化分别改变 key；9 个合法布局得到 9 个不同 key。 |
| 缓存物化 | publish、lookup、materialize 都用当前请求的限定入口重新验证 JAR/DEX | `com.example.scripts.ScriptEntry` 完成发布、命中、物化与 descriptor 重验。 |
| D8 / worker | D8 使用 JAR 摘要的 descriptor 集；worker 用请求限定名执行 `Class.forName(..., userDexClassLoader)` | API 24 真实 ECJ→D8→DEX 校验→ART 加载与实例化证据见下一节。 |

审计还确认 `RemoteJavaSourceSession` 在缓存 lookup/materialize/publish、JAR 写入和内部 worker request
的所有调用点都传递 `request.entryClassName`；`WorkerEntryFactory` 在初始化用户类之前验证该 class
确由已校验的 user DEX loader 定义。没有发现需要修复的生产硬编码。

## API 24 设备证据

设备为 `AVD_API_24`（`emulator-5558`），API 24 / x86，fingerprint：

`google/sdk_google_phone_x86/generic_x86:7.0/NYC/6696031:userdebug/dev-keys`

`JavaProviderPipelineInstrumentedTest` 在 provider 内部执行以下 4 个入口：

| 入口 | 源文件 | 预期且实际 descriptor | ART 返回值 |
|---|---|---|---|
| `Main` | `Main.java` | `LMain;` | `Main` |
| `ScriptEntry` | `ScriptEntry.java` | `LScriptEntry;` | `ScriptEntry` |
| `com.example.scripts.ScriptEntry` | `ScriptEntry.java` | `Lcom/example/scripts/ScriptEntry;` | `com.example.scripts.ScriptEntry` |
| `_root.$generated.p9.$Entry9` | `$Entry9.java` | `L_root/$generated/p9/$Entry9;` | `_root.$generated.p9.$Entry9` |

每个入口都独立创建私有 workspace，依次经过源码布局检查、ECJ、JAR writer、D8、DEX identity/结构与
descriptor 完整性校验、只读发布、API 24 `PRIVATE_DEX_CLASS_LOADER`、`Class.forName`、公开无参构造器
实例化及 `run`。测试返回完整限定名，因而不能由误加载的 `Main` 假阳性通过。

执行结果：`2 tests`、`0 failure/error`、`2.719 s`。其中第 1 个 test 固定 `Main` 回归，第 2 个 test
顺序执行 3 个非 `Main` 设备布局。该套件是 provider 内部真实流水线证据，不冒充宿主到 provider 的
canonical Binder 端到端证据。

本次 APK 身份如下：

| 工件 | bytes | SHA-256 |
|---|---:|---|
| Debug provider APK | 31,546,798 | `22aba17e7203f11282f17acde6d53e023c0d4f92d9788f02e8d52332d568db0e` |
| AndroidTest APK | 1,021,701 | `905a0dfd2ab9e3e621c5366e5c655fc587653d1bf3be057df023fa5ea2cd155c` |
| Release provider APK | 27,090,802 | `9f278c5d3014f8a9a0208e766314cb384d3737c1e2bede346399085e9ae58162` |

复现命令（显式选择设备，避免连接的其他设备混入结果）：

```powershell
.\gradlew.bat --offline :app:assembleDebug :app:assembleDebugAndroidTest
adb -s emulator-5558 install -r -t app\build\outputs\apk\debug\app-debug.apk
adb -s emulator-5558 install -r -t app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
adb -s emulator-5558 shell am instrument -w -r `
  -e class org.autojs.plugin.jvmsource.java.JavaProviderPipelineInstrumentedTest `
  io.github.supermonster003.autojs6.plugin.java.runtime.test/androidx.test.runner.AndroidJUnitRunner
```

验证后已卸载本轮安装的 provider 与 AndroidTest 包；AutoJs6 宿主及其测试包未改动。

## 离线回归门禁

最终执行：

```powershell
.\scripts\verify.ps1 :app:assembleRelease
```

结果：

- Debug unit：140 tests，全部通过；
- Release unit：140 tests，全部通过；
- `lintDebug`：通过；
- `assembleDebug`、`assembleRelease`：通过；
- 所有 Gradle 任务均使用 `--offline`。

## M9-7 移交边界

M8-5 不改变用户可见能力。未来 M9-7 若决定开放入口配置，至少还需要：

1. 宿主侧明确入口名的来源、规范化与用户错误呈现，不能信任 provider 替宿主补安全校验；
2. 继续使用共享布局策略，或随协议/AAR 一起版本化新规则；
3. 让宿主真实构造非 `Main` 的 `JvmSourceRequest`，覆盖快照文件名、请求校验、诊断映射与 Binder
   完整执行；
4. 保持 `Main` 默认值与现有脚本向后兼容。

在这些宿主协同工作完成前，文档和能力声明都不得把任意入口类名描述为已对用户开放。
