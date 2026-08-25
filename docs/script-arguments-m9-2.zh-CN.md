# M9-2 脚本入参：Protocol 1.3 / Entry API 4

## 结论

M9-2 已在 AutoJs6 宿主与 Java Runtime Provider 两侧完成。宿主会在任何 provider 查询、绑定或用户代码
dispatch 之前，把显式 JVM Source 执行的 `ExecutionConfig.arguments` 冻结成有界 JSON object；provider
只解码一次，并通过 `JvmScriptContext.args()` 向用户源码暴露深层只读的 Java `Map`/`List` 快照。读取
参数不需要 capability，也不会触发 host bridge。

入口方法仍为 `AutoJsJvmEntry.run(JvmScriptContext)`，因此既有无参数 Java 源码无需修改。由于 M9-1 已
占用 Entry API 3，本项锁定为 Protocol `1.3` / Entry API `4`，最低宿主提升至 AutoJs6 version code
`5278`，provider 提升至 `0.5.0-m9` / version code `5`。

## 锁定版本与工件

| 项目 | 锁定值 |
|---|---|
| 协议 / Entry ABI | Protocol `1.3` / Entry API `4` |
| 最低宿主 | AutoJs6 version code `5278` |
| Provider | `0.5.0-m9` / version code `5` |
| 宿主最终源码 | commit `abb64bdd620c33bf9c7ce64c7fc0011237d0365f`；branch `codex/m9-jvm-args` |
| `common-plugin-api.aar` | SHA-256 `c526f4fd0adbf38b36a7bf54f9931385e6cc20050d6ea8fb741c60496af635d5` |
| `protocol-wire-api.aar` | SHA-256 `e044dd3cc9bed84e844174e0963b57a1f67902cf021ccb42d1031d3511ce46da` |
| `jvm-source-api.aar` | SHA-256 `022020e3488f5dc098b0d7357fdeae639292c9f8329be696dfafbf6a48bf35f7` |

三份 AAR 均由上述干净宿主提交的 Release variant 生成；
[`protocol-artifacts.lock.json`](../protocol/protocol-artifacts.lock.json) 同时记录完整 revision、
`sourceDirty=false`、来源 module 与三个摘要。基础 common/wire 工件未因本项发生字节变化。

## Wire 与 ABI 兼容策略

Protocol 1.3 在 `JvmSourceRequest` 增加 `argsJson`，Tagged Wire tag 固定为 `17`。该字段只在协议
`>= 1.3` 时编码，并标记为 reader 必须理解的字段；这使旧 reader 无法静默忽略参数后继续执行。

- 1.3 请求必须携带 `argsJson`，空参数的规范值为精确 `{}`。
- 1.2 fixture 没有 tag 17；新 decoder 把缺失字段解释为空对象，以保留旧请求兼容性。
- 1.2 请求若在内存模型中附带非空参数会被 validation 拒绝，不能借旧协议绕过协商。
- `JvmSourceRequest.toString()` 只记录参数 JSON 的 UTF-8 字节数，不记录 key 或 value。
- `AutoJsJvmEntry.run(JvmScriptContext)` 的方法 descriptor 未改变；旧入口源码和旧入口 class 都不需要
  新增重载。Entry API 4 只在协商后的 Context 上新增 `args()`。

宿主 `RemoteJvmScriptEngine` 在启动请求前取得一次 `ExecutionConfig.arguments` 快照；
`RemoteJvmSourceHost` 只接收已编码的字符串。编码失败会在 provider discovery、package inspection、
Binder bind 和用户代码 dispatch 之前终止。

## 参数 profile

根值必须是 string-keyed object。宿主编码器接受以下 Java/Kotlin 形态：

- `null`、`Boolean`；
- `Byte`、`Short`、`Integer`、`Long`、`BigInteger`；
- 有限的 `Float` / `Double` 与 JSON 数字语法可表示的 `BigDecimal`；
- `Character`、`CharSequence`；
- key 全为 `String` 的 `Map`、`Iterable` 与任意 Java 数组，元素递归遵守同一 profile。

对象 key 按自然字符串顺序编码，所以同一逻辑参数得到确定性 JSON。整个 JSON 最多 65,536 UTF-8
bytes，容器嵌套最多 64 层，单个数字文本最多 256 字符。引用环、非字符串 key、重复 JSON key、
NaN/无穷、非配对 UTF-16 surrogate、普通 POJO、Android 对象和其他未列类型全部拒绝。

worker 解码后，`Long` 范围内的整数为 `Long`，更大的整数为 `BigInteger`，小数和指数形式为
`BigDecimal`。根 Map、嵌套 Map 和 List 都是不可修改视图；编码发生在调用开始前，所以随后修改宿主
原集合也不会影响脚本快照。空参数返回非 null 的空 Map。

外部 Android `Intent` 不会自动注入 JVM 参数。需要参数的调用方必须显式使用
`ExecutionConfig.setArgument(key, value)`，并提供上述 JSON profile 值。参数内容不会进入公开错误、
请求 `toString()`、provider 本地观测或生产证据记录。

## 失败封闭与验证

宿主把 profile 编码失败映射为稳定用户错误 `JVM_SOURCE_INVALID_ARGUMENTS`、恢复动作
`FIX_INVOCATION`、不可重试；内部类型名、值和异常文本不会成为公开错误。协议层另行拒绝畸形 UTF-8、
超限、重复 key、错误根类型和过深 JSON。宿主测试确认无效参数发生时 provider inspection 与 bind
计数都为零；Intent 边界测试确认 Android Intent 仍只服务既有引擎，不会进入隔离 JVM 参数。

最终自动化结果：

- AutoJs6 `jvm-source-api`：49/49；宿主 app：1654 tests，0 failure/error，3 个既有 skipped。
- AutoJs6 已成功生成三个 Release API AAR、App Debug APK 与对应 AndroidTest APK，并完成
  `compileAppDebugAndroidTestKotlin`。
- Provider Debug/Release JVM 单测各 147/147；Debug lint 0 error；Debug、Release 与 AndroidTest APK
  全部离线构建成功。Release APK SHA-256 为
  `70e4d70b6a03db73f651d41f418ad77f85db6745dfce8b8e091cbfb820960ccd`。
- `JavaSampleLibraryInstrumentedTest` 使用 Provider Debug APK
  `9aacbd90f06aad9f4a754080835420d192721d3d79abc3ae06131f4651d1a1e7` 与 AndroidTest APK
  `1d1639e95ff01472cb1669adc714361f3320db0df6990175227c73a4f247dd88`，在 API 24 模拟器
  9/9（4.244 s）及 Sony G8441 API 28 真机 9/9（8.911 s）通过真实 ECJ→D8→ART 路径。
  新增用例同时验证参数样例和旧无参数源码针对 Entry API 4 的兼容性。

可运行样例与规范输入、输出见
[`samples/script-args.java`](../samples/script-args.java) 和
[`samples/README.zh-CN.md`](../samples/README.zh-CN.md)。

## API 36 宿主生产路径证据

新增宿主 selector 直接从 `ExecutionConfig.setArgument(...)` 构造以下逻辑参数，并经真实
AutoJs6→provider `:compiler`→一次性 `:worker` 链路执行：

```json
{"enabled":true,"name":"AutoJs6","nested":{"count":3,"nullable":null},"tags":["java","m9"]}
```

脚本得到并返回：

```json
{"name":"AutoJs6","enabled":true,"count":3,"firstTag":"java","tagCount":2,"nullValue":null}
```

| 项目 | 观测值 |
|---|---|
| 设备 | `emulator-5564`；Android 16 / API 36；x86_64 |
| Fingerprint | `google/sdk_gphone64_x86_64/emu64xa:16/BE4B.251210.005/14574095:userdebug/dev-keys` |
| AutoJs6 APK | version `6.8.0` / code `5278`；x86_64 APK SHA-256 `04c71a3336600517fec13b876aee60cdd7cff6b0ce764237406195cb13359283` |
| Provider APK | version `0.5.0-m9` / code `5`；SHA-256 `9aacbd90f06aad9f4a754080835420d192721d3d79abc3ae06131f4651d1a1e7` |
| APK signer | 二者 certificate SHA-256 `31a681fcfffb3e428420cae280ded89292b12a3b0f59e19b7a73e32a8ae4c213` |
| Instrumentation | 1/1，1.929 s |
| Source / request | source SHA-256 `fc547d5f14dfab72f47818c8bd578eea51d30a83c8a12dcc621a95f3986425c6`；request SHA-256 `2c9c403b72f2e15a77cd996c4573586fa4b20e374509068644a806850242906d` |
| Result | SHA-256 `2784216ff32a4bc90699a568aabbf93f65c4bb0f207c071532b6924d49554baf` |
| 进程 | host PID `1702`；compiler PID `1734`；worker PID `1894`，三者互异 |
| Bridge | allowlisted host bridge `0`；internal bridge `0` |
| Provider session | 1,209 ms；worker startup `283` ms；`COMPILE=224`、`D8=480`、`LOAD=5`、`RUN=2`、`TERMINATION=18` ms |

仪器测试同时断言 Protocol `1.3`、Entry API `4`、provider component/version、结果 JSON/hash、源码
大小/hash、三个 PID 与零 bridge 调用。原始记录为：

| 文件 | 字节数 | SHA-256（LF 字节） | 内容 |
|---|---:|---|---|
| [`2026-08-25-api36-m9-args-provider.jsonl`](device-evidence-data/2026-08-25-api36-m9-args-provider.jsonl) | 513 | `9bd3ae7695ebc55930d2eeada5b699c1dbdab801bdad75ae5450800b222ca359` | provider schema-v1 完整会话观测 |
| [`2026-08-25-api36-m9-args-runtime.log`](device-evidence-data/2026-08-25-api36-m9-args-runtime.log) | 862 | `2d67af9337290f89ad6cdbfa102ec6251fa1b974855d3b1ed88b50d77e9e9b22` | 仪器 marker、三进程身份、结果与 1/1 终态 |

参数测试只使用宿主私有 cache，fixture 由 selector 的 `finally` 删除；本轮没有改动 app-op、共享存储或
剪贴板。证据收集后，API 24/28 上本轮安装的 provider/test package 已卸载；API 36 专用
`DEX_R1_API36_X64` 上的宿主、宿主测试和 provider package 已移除，AVD 已正常关闭。
