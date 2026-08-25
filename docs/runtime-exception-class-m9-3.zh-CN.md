# M9-3 运行时异常类名：Protocol 1.4 / Entry API 4

## 结论

M9-3 已在 AutoJs6 Host 与 Java Runtime Provider 两侧完成。Java 用户源码运行失败时，provider 现在会在
命中请求入口类或其 inner class 的安全栈帧后，返回请求源码位置与一个有界公开异常类名：合法
`java.*` / `javax.*` 二进制类名原样保留，其他命名空间精确归一为 `UserException`。异常 message、完整
stack、绝对路径、provider/user package、UID/PID 仍不会进入外部 diagnostic。

公开显示形态固定为：

```text
Main.java:4:1: JAVA_RUNTIME_EXCEPTION [java.lang.IllegalStateException]: Java execution failed
```

本项锁定为 Protocol `1.4`，Entry API 仍为 `4`，`AutoJsJvmEntry.run(JvmScriptContext)` 的方法 descriptor
不变。最低 Host 提升至 AutoJs6 version code `5279`；Provider 提升至 `0.6.0-m9` / version code `6`。

## 锁定版本与工件

| 项目 | 锁定值 |
|---|---|
| 协议 / Entry ABI | Protocol `1.4` / Entry API `4` |
| 最低 Host | AutoJs6 `6.8.0` / version code `5279` |
| Provider | `0.6.0-m9` / version code `6` |
| Host 源码 | commit `a859a15a0517112c374f6637fc7b3d10a06d5450`；branch `codex/m9-runtime-diagnostic` |
| Provider 功能基线 | commit `21d46ab9dbf2c6ea549ee343c63d7935889581bc`；branch `main` |
| `common-plugin-api.aar` | SHA-256 `c526f4fd0adbf38b36a7bf54f9931385e6cc20050d6ea8fb741c60496af635d5` |
| `protocol-wire-api.aar` | SHA-256 `e044dd3cc9bed84e844174e0963b57a1f67902cf021ccb42d1031d3511ce46da` |
| `jvm-source-api.aar` | SHA-256 `f5c3d9b61ad5c3541782bc579f061afb627ce8d2c59e67d5ae0acb401c9ea92e` |

三份 AAR 均由上述已提交 Host 的 Release variant 生成；
[`protocol-artifacts.lock.json`](../protocol/protocol-artifacts.lock.json) 记录完整 revision、
`sourceDirty=false`、来源 module 与三个摘要。common/wire 工件与 1.3 基线逐字节相同，只有
`jvm-source-api.aar` 因新增 diagnostic 字段与验证逻辑发生变化。

## Wire 与兼容策略

Protocol 1.4 在 `JvmSourceDiagnostic` 增加可空字段 `runtimeExceptionClassName`，Tagged Wire tag 固定为
`8`。字段只在有值时编码，并始终标记为 reader-required：旧 reader 不能静默忽略异常类名后继续把
1.4 diagnostic 当成旧语义处理。

- 1.3 diagnostic 不含 tag 8；1.4 decoder 将其解码为 `null`。
- 1.4 request 收到 `JAVA_RUNTIME_EXCEPTION` 时，类名字段必须存在；1.3 request 下必须不存在。
- 类名只允许 `java.*`、`javax.*` 的合法二进制名或精确 `UserException`，UTF-8 上限为 255 bytes。
- 类名必须绑定 `ERROR` severity、`JAVA_RUNTIME_EXCEPTION` code、请求 ID以及完整的安全文件名/正行列。
- 非运行时 diagnostic 携带类名、1.4 运行时 diagnostic 缺类名、或 request ID 不一致都会 fail closed。
- 外部 Host↔Provider AIDL 与 Entry ABI 均未改变；Provider 内部 compiler↔worker AIDL 只增加经过投影的
  `line + exceptionClassName`，不传输 `Throwable` 或任意文本。

关联请求的约束由两侧共同使用的 `JvmSourceValidation.validateDiagnosticAgainst(...)` 执行。Host 在计入
diagnostic byte budget 和发布给执行 sink 前再次验证；Provider 在编码、预算预留和回调发出前也再次
验证，因此不能通过直接构造内存对象绕过协商版本。

## 双侧脱敏路径

Provider 的数据流为：

```text
Throwable
  → 有界 cause/frame 搜索
  → JavaRuntimeDiagnostic(line, safeClassName)
  → provider 内部 AIDL
  → 独立重验 + Protocol 1.4 diagnostic
  → shared validation + Tagged Wire
```

具体边界如下：

- cause 最多检查 8 层，每层最多检查 256 个 stack frame；循环 cause 通过 identity set 截断。
- 只接受 `frame.className == entryClassName` 或其 `$` inner class，且 `fileName` 必须精确等于请求的
  `sourceFileName`，行号范围为 `1..1_000_000`。
- 类名从拥有该安全 frame 的 `Throwable.javaClass.name` 提取；Provider 自己维护
  `java`/`javax` 正则和 255-byte 上限，其他值只产生常量 `UserException`。
- worker callback 失败或投影自身异常不会覆盖原运行终态；非法内部 callback 会触发 protocol violation。
- 对外 message 恒为 `Java execution failed`，column 恒为安全基线 `1`，不会从异常文本推断列号。

Host 不信任 Provider 已完成脱敏。它维护第二份独立类名白名单，只在 code、文件名、行列同时安全时保留
类名，并对 Java/Kotlin 运行时错误强制使用语言对应的固定消息。控制台格式化器只组合已投影字段；不再
接触原始异常 message。

生产 instrumentation 的证据 marker 额外记录 `providerComponent` 与 `hostPid`，用于证明测试确实经过
指定生产组件并识别当次 runner。这些是**测试证据元数据**，不属于 `JvmSourceDiagnostic` 或公开文本；
测试分别断言 diagnostic、公开文本和最终用户失败对象均不含秘密 message、`classes.dex`、UID/PID。

## 自动化门禁

最终自动化结果：

- Host `jvm-source-api`：54/54；Host app：1,657 tests，0 failure/error，3 个既有 skipped。
- Host 的 `compileAppDebugAndroidTestKotlin`、最终 App Debug APK 与 AndroidTest APK 构建成功。
- Provider Debug/Release JVM 单测各 149/149；Debug lint 0 error（29 个既有 warning）。
- Provider Debug、Release 与 AndroidTest APK 全部离线构建成功；`verifyPinnedInputs` 验证三个 AAR 摘要。
- 兼容测试覆盖 1.3 缺字段、1.3 reader 拒绝 1.4 required tag、1.4 字段必需、request ID/code/severity/
  location 绑定，以及非法命名空间拒绝。
- Provider policy 测试覆盖 platform 类名、用户类 sentinel、cause 所有权、秘密文本不保留和 cause depth
  上限；Host policy/transport 测试覆盖第二次投影、固定公开文本和 diagnostic-before-terminal 顺序。

最终 Provider 制品：

| 制品 | 字节数 | SHA-256 |
|---|---:|---|
| Debug APK | 33,723,086 | `240aacd93a7b4f07601b0fc020ddb3920934e4c02f18e9abc53bdd821e47f48c` |
| Release APK | 27,098,746 | `49ba1345a26c207b5e946de4c31507c3729152cf483aa62ffc3bf06db1c38e32` |
| AndroidTest APK | 1,027,073 | `0a63cbb72978c1146ed53e4e571c1d2891233ad4f11755662a7cc68051dd769a` |

## API 24 / 28 Provider 样例证据

[`runtime-exception.java`](../samples/runtime-exception.java) 在第 7 行故意抛出包含秘密 message 的
`IllegalStateException`。`JavaSampleLibraryInstrumentedTest` 通过真实 ECJ→D8→ART 执行该源码，并断言
Provider 只得到 `JavaRuntimeDiagnostic(7, "java.lang.IllegalStateException")`，对象字符串不含秘密文本。
样例文件为 310 bytes，SHA-256
`bb713aa5a0259bec7b49cbdd3c8f94e1fc53d9c10519da710c22cfc14f483595`。

| 设备 | 最终制品 | 结果 | 覆盖点 |
|---|---|---:|---|
| Android 7.0 / API 24 / x86 emulator；fingerprint `google/sdk_google_phone_x86/generic_x86:7.0/NYC/6696031:userdebug/dev-keys` | Debug/AndroidTest APK 为上表摘要 | 10/10，6.682 s | 私有 `DexClassLoader`；真实异常类名与源码行投影 |
| Sony G8441 / Android 9 / API 28 / arm64-v8a；fingerprint `Sony/G8441/G8441:9/47.2.A.4.45/3677320370:user/release-keys` | 同一 Debug/AndroidTest APK | 10/10，9.434 s | `InMemoryDexClassLoader`；同一投影契约 |

两台设备执行后只卸载本轮安装的 Provider 与 Provider test package；既存 AutoJs6/Host test package 未动。

## API 36 Host 生产 Binder 证据

Host selector 使用生产 `RemoteJvmSourceHost.createAndroid(...)`、签名边界、Provider 选择与真实 Binder
链路执行一份第 4 行抛出异常的 `Main.java`：

```text
AutoJs6 Host
  → provider :compiler Binder
  → provider :worker Binder
  → JAVA_RUNTIME_EXCEPTION diagnostic
  → Host 独立投影与公开格式化
```

| 项目 | 观测值 |
|---|---|
| 设备 | 专用 `AVD_API_36.1`；Android 16 / API 36 / x86_64 |
| Fingerprint | `google/sdk_gphone64_x86_64/emu64xa:16/BE4B.251210.005/14574095:userdebug/dev-keys` |
| Host APK | AutoJs6 `6.8.0` / code `5279`；43,431,463 bytes；SHA-256 `ab37fe819baa078f8a7bf0fb6fb4bac146d3c45be5c924056f198c712a0adf84` |
| Host AndroidTest APK | 1,784,553 bytes；SHA-256 `1a524e7ba733aeee01004dccfd5162774b65243e7449e625dc86c50a340ca23c` |
| Provider APK | `0.6.0-m9` / code `6`；最终 Debug APK SHA-256 `240aacd93a7b4f07601b0fc020ddb3920934e4c02f18e9abc53bdd821e47f48c` |
| APK signer | Host、Host test 与 Provider certificate SHA-256 均为 `31a681fcfffb3e428420cae280ded89292b12a3b0f59e19b7a73e32a8ae4c213` |
| Instrumentation | 1/1，6.021 s |
| Source | SHA-256 `cb7b65d8155771ba4b3eb8a02ee9af4bc24aeed914b0ef283105565aca0bcb43` |
| Diagnostic | `ERROR` / `JAVA_RUNTIME_EXCEPTION` / `Main.java:4:1` / `java.lang.IllegalStateException` / fixed message |
| 公开文本 | `Main.java:4:1: JAVA_RUNTIME_EXCEPTION [java.lang.IllegalStateException]: Java execution failed` |
| 隐私断言 | `secretAbsent=true`；diagnostic、公开文本与用户失败均不含异常 message、stack/path、UID/PID |

未重新序列化的 marker 与 runner 终态保存在
[`2026-08-26-api36-m9-runtime-diagnostic.log`](device-evidence-data/2026-08-26-api36-m9-runtime-diagnostic.log)：
781 bytes，SHA-256 `d3d251ce33b5653f1ac17333ea67eda2cd80d16d68190145d3fd0e7d6ae35fad`。

证据收集后，专用 AVD 上的 Host test、Provider、Host 三个 package 均已卸载，AVD 已正常关闭。构建时
短暂复制到隔离 Host 工作树的 `sign.properties` 与 JKS 也已删除；原始 Host 工作树及其中既有用户改动
没有被清理、覆盖或提交。
