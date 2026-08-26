# M9-5 多文件源码包：Protocol 1.6 / Schema 1.3

## 结论

M9-5 已在 AutoJs6 Host 与 Java Runtime Provider 两侧完成。公开 Binder AIDL 仍只传一个 source
`ParcelFileDescriptor`；多文件请求把这个 FD 定义为一个严格有界、无压缩、字节级规范的源码归档。Host
先把目录树冻结成规范快照，Provider 再独立验证完整归档、清单、路径、摘要与每个 Java compilation unit，
验证完成前不会把任何归档路径或源码文件写入私有 workspace。

本项锁定为 JVM Source Protocol `1.6`、Tagged Wire schema `1.3`、Entry API `4`。最低 Host 为
AutoJs6 version code `5281`；Provider 为 `0.8.0-m9` / version code `8`。入口简单名仍固定为 `Main`，
任意入口类名继续由 M9-7 放开。

## 锁定版本与工件

| 项目 | 锁定值 |
|---|---|
| 协议 / Wire schema / Entry ABI | Protocol `1.6` / schema `1.3` / Entry API `4` |
| 最低 Host | AutoJs6 `6.8.0` / version code `5281` |
| Provider | `0.8.0-m9` / version code `8`；发现资源 min/max 均为 `1.6` |
| Host 源码 | commit `1b79603bc7304ae44fe878b27ef01d2d77b2a963`；branch `codex/m9-multifile-source` |
| Provider 功能基线 | commit `ead53e35ecbc0dbc024bfc85ce5718bb4df22627`；branch `main` |
| `common-plugin-api.aar` | 11,876 bytes；SHA-256 `c526f4fd0adbf38b36a7bf54f9931385e6cc20050d6ea8fb741c60496af635d5` |
| `protocol-wire-api.aar` | 29,463 bytes；SHA-256 `e044dd3cc9bed84e844174e0963b57a1f67902cf021ccb42d1031d3511ce46da` |
| `jvm-source-api.aar` | 231,629 bytes；SHA-256 `dbfe313ec052b951c83734595f9c010fe8a6fc24a8ea7c2fa3643ebbafe611f9` |

`protocol/protocol-artifacts.lock.json` 记录上述干净 Host revision、`sourceDirty=false` 与三个精确摘要；
`verifyPinnedInputs` 在编译和组装前继续检查工件集合、模块名、普通文件属性和 SHA-256。

## 为什么继续使用单 FD

M9-5 对 Roadmap 中两个候选方案作出如下选择：

- **采用**：单 FD 承载规范源码归档；
- **不采用**：修改 AIDL 传递多 FD 或 Binder 对象数组。

单 FD 保留已有所有权转移、错误检查、关闭与会话取消语义，也让请求继续绑定一个明确的长度与 SHA-256。
Host 可以在 bind 前完成原子快照，Provider 可以在内存中先验证整体后再落盘；文件数量不会放大 Binder FD
预算，也不需要为部分 FD 成功、部分 FD 失败定义新的事务语义。归档 profile 自己禁用压缩和可变元数据，
因此没有引入通用 ZIP 解压器的宽泛攻击面。

## Wire 变更与兼容

`JvmSourceRequest` 在既有 tag 1–17 后增加三个 Protocol 1.6 字段：

| Tag | 字段 | 类型 | 语义 |
|---:|---|---|---|
| 18 | `sourcePayloadKind` | string enum | `single-file` 或 `source-archive` |
| 19 | `sourceFileCount` | int32 | 规范源码文件数；源码包为 `2..32` |
| 20 | `sourceContentBytes` | int64 | 清单内全部 Java 源码内容的字节总量 |

Protocol 1.6 编码时三个字段都标记为 `requiredForReader=true`，缺少任意字段均拒绝；只认识 tag 1–17 的
reader 会返回 `UNKNOWN_REQUIRED_FIELD`，不能把源码包静默当成旧单文件。Protocol 1.5 及更早的请求不包含
这些 tag，新 decoder 明确补为 `single-file`、`1`、`sourceSizeBytes`，原单文件 wire 语义保持不变。

对 `source-archive`：

- 既有 `sourceSizeBytes` 与 `sourceSha256` 绑定原始规范归档；
- `sourceFileName` 仍为入口逻辑文件名 `Main.java`，语言仍为 `JAVA`；
- `entryClassName` 保持入口全限定类名；清单入口路径必须精确等于该类名转换出的路径；
- `sourceFileCount` 与 `sourceContentBytes` 必须和解码后的清单及 ZIP 内容一致。

外部 `IJvmSourceCompilerService` / `IJvmSourceSession` AIDL、回调、Entry descriptor 与
`AutoJsJvmEntry.run(JvmScriptContext)` 均未改变。

## 规范归档 profile

共享 `JvmSourcePackageCodec` 不调用平台 ZIP 解压 API，而是按以下窄 profile 自行编码和解析：

1. 第一项固定为 `META-INF/AUTOJS6-JVM-SOURCE.MF`；
2. 其余项是按 ASCII 路径字典序排列的 Java 源文件；
3. 所有项只能使用 ZIP `STORED` method，压缩前后长度必须相等；
4. general-purpose flags、DOS 时间、extra、comment、internal/external attributes、disk number 全为零；
5. local header 与 central-directory 的名称、CRC-32、长度、偏移必须完全一致；
6. local records 必须从 offset 0 连续排列，central directory 与无 comment EOCD 必须精确贴合文件末尾；
7. 禁止 data descriptor、ZIP64、split disk、重排、别名与任何尾随记录；
8. 解码后按相同输入重新编码，最终必须与收到的归档逐字节相同。

二进制 manifest 固定使用 magic `AJSM`、schema `1`，依次包含入口路径、文件数、源码总字节数，以及每个
有序条目的路径、长度和 SHA-256。Provider 同时验证 ZIP CRC-32、manifest SHA-256、manifest/ZIP 顺序、
计数与总量；任一层不一致都在 INPUT 阶段以 `ARTIFACT_INVALID` 拒绝。

因为不接受 DEFLATE 或任何其他压缩 method，归档声称巨大解压尺寸、压缩比或 ZIP64 长度时会在读取内容前
失败；M9-5 的“压缩炸弹拒绝”由格式本身消除，而不是依赖启发式压缩比。

## 路径、树形与容量边界

| 边界 | 当前值 |
|---|---:|
| 源码包文件数 | `2..32` 个非空 `.java` 文件 |
| 全部源码内容 | 最多 4 MiB |
| 单 FD 规范归档 | 最多 4 MiB + 64 KiB |
| manifest | 最多 16 KiB |
| 单个相对路径 | 最多 240 bytes |
| package 目录深度 | 最多 16 层 |

源码路径只允许 ASCII Java identifier segment，形式为
`(?:identifier/){0,16}identifier.java`。绝对路径、`.` / `..`、反斜杠、空 segment、非 Java 文件、
非普通文件、空文件、空目录、精确重复和 Unicode-independent 小写折叠冲突都拒绝。每个文件的路径必须
精确对应其词法 `package` 声明与文件简单名，例如 `demo/Helper.java` 只能声明 `package demo;`。

Host 通过 `lstat` 分类目录项，通过 `O_NOFOLLOW` 打开普通文件，并在打开后用 `fstat` 重验；目录树中的
符号链接和其他特殊节点一律拒绝。归档 central-directory 的 external attributes 必须为零，所以伪装成
Unix symlink 的条目也会拒绝。Provider 从不按归档名称调用通用 extract，也不会让归档路径参与私有根目录
之外的文件解析。

每个 Java 文件仍独立执行既有严格 UTF-8、单前导 BOM 归一、NUL 拒绝、Java Unicode escape 拒绝和
词法 package 检查。Host 与 Provider 各自执行一遍；Provider 不信任 Host 已经完成的结果。

## Host 公开入口与流程

M9-5 增加显式目录入口：

~~~kotlin
JvmSourceExplicitRunner.runJvmSourcePackageExplicit(
    sourceRoot = File(projectDirectory),
    entryFile = File(projectDirectory, "demo/Main.java"),
    config = executionConfig,
)
~~~

此 API 仍受 `autojs.jvmSource.experimental` 显式开关保护，仍禁止 Host-side loop。Host 在 discovery/bind
前完成以下工作：

1. 验证调用来自显式授权入口，root 与 entry 属于同一棵目录树；
2. 不跟随链接地枚举全部节点，逐文件有界读取；
3. 逐文件规范化 UTF-8 并验证 package/path；
4. 构造确定性 manifest 与规范归档，写入 Host 私有只读临时文件；
5. 把归档长度、原始摘要、kind、文件数和内容总量绑定进 Protocol 1.6 request；
6. 继续通过原有单 FD ownership-transfer 路径打开 Provider session。

成功结果的 `RemoteJvmScriptResult` 额外暴露 `sourcePayloadKind`、`sourceFileCount` 与
`sourceContentBytes`，便于调用方和证据代码验证实际走的是源码包路径，而不是从执行结果反推。

## Provider 编译、诊断与缓存

Provider 的顺序固定为：

1. 按 request 声明长度有界读取单 FD，并验证原始 SHA-256；
2. 在内存中解析整个规范归档，验证所有 framing、路径、计数、总量、CRC 和摘要；
3. 对每个 compilation unit 重跑 UTF-8/Unicode/package/path 策略；
4. 对规范化后的文件集重新编码并计算一个 package identity；
5. 只在上述步骤全部成功后，在私有 `sources/` 下创建规范普通文件；
6. 把按逻辑路径排序的完整文件列表作为独立参数交给一次 ECJ batch compile；
7. 沿用既有 class/JAR 验证、D8、DEX 验证与 disposable worker 执行链。

ECJ 诊断会把私有绝对路径替换成对应的安全逻辑路径，如 `demo/Helper.java`。共享协议与 Host 公开投影都
只接受同一规范相对路径 profile；其余私有路径仍统一脱敏。

编译缓存 key 升级为 schema `3`、domain
`org.autojs.jvm-source.java-compilation-cache-key.v3`、implementation revision `r5-cache-v4`。除了原始
归档 SHA-256 与规范化 package identity，还绑定 payload kind、文件数、内容总量和
`source-input=canonical-ordered-file-set-v1` 编译选项身份。旧 schema 缓存不会误命中新文件集语义。

## 威胁用例与拒绝点

| 输入 | 拒绝位置 |
|---|---|
| `../`、绝对路径、反斜杠、非法 segment | 共享 path policy；不会进入 workspace |
| local/central 重复名或大小写折叠冲突 | ZIP 解码与 Host tree admission |
| DEFLATE、伪造超大解压长度、ZIP64 | canonical ZIP metadata/size 检查 |
| external attributes 表示 symlink | external attributes 必须为零 |
| Host 文件树中的 symlink/special node | `lstat` + `O_NOFOLLOW` + `fstat` |
| manifest/ZIP 路径、顺序、长度或 SHA 不同 | manifest 与 entry 交叉验证 |
| CRC 错误、尾随记录、extra/comment/time/flag | canonical ZIP framing 与最终重编码恒等检查 |
| 文件路径与 `package` 不一致 | Host 与 Provider 逐文件词法检查 |
| count/total 与 request 声明不同 | Provider request/archive 交叉验证 |

共享与 Provider 单测直接构造路径穿越、重复项、压缩炸弹元数据、symlink external attributes、大小写冲突、
文件数溢出、package/path 不匹配和 request claim 不一致。Host 单测另用真实符号链接验证目标不会被读取。

## 自动验证

全部命令在离线模式执行：

- Host `:plugin-api:jvm-source-api:testDebugUnitTest`：68 tests，0 failure，0 skipped；
- Host `:app:testAppDebugUnitTest`：1,665 tests，0 failure，3 skipped；
- Host App Debug、AndroidTest APK 与 `jvm-source-api` Release AAR 构建成功；
- Provider `:app:testDebugUnitTest`：163 tests，0 failure；
- Provider `:app:testReleaseUnitTest`：163 tests，0 failure；
- Provider `lintDebug`：0 error，29 个既有 dependency/resource warning；
- Provider Debug、Release 与 AndroidTest APK 全部离线组装成功。

最终制品摘要：

| 制品 | 字节数 | SHA-256 |
|---|---:|---|
| Host x86 Debug APK；AutoJs6 `6.8.0` / code `5281` | 42,491,124 | `8927933a4997c100b35e0090da5095b4db10706ad916cebb83cb0d6474cfa62c` |
| Host AndroidTest APK | 1,608,928 | `575c950d6bdb3d78617a548199c9e29068ca63c18e23c05324c19b51f3ced129` |
| Provider Debug APK | 30,664,944 | `c5c0e939d87ca7bc231d46c972bc33de848c2b4fb379e75a90df4df4ddd08c14` |
| Provider Release APK；`0.8.0-m9` / code `8` | 27,118,578 | `3f22ed5d8634613d116426863b8ac6713474b6cb3a90dd3e51dc7590c227500c` |
| Provider AndroidTest APK | 1,027,097 | `15bd81d6755e128109b969019ea005555939aae53f156b63ea0352d9fef63c64` |

## Production Binder 设备证据

最终 Host x86 Debug APK、Host AndroidTest APK 与签名一致的 Provider **Release** APK 安装到预检时不含
相关包的 API 25 / x86 模拟器。只运行 selector：

`JvmSourceR1EndToEndInstrumentationTest#explicitMultiFileJavaPackageRunsThroughPinnedProductionProvider`

测试动态创建 `demo/Main.java` 与 `demo/Helper.java`，通过公开显式目录入口走
Host→compiler service→ECJ→D8→disposable worker，结果为：

- `OK (1 test)`，1.543 s；
- Protocol `1.6` / Entry API `4`；
- `sourcePayloadKind=source-archive`；文件数 `2`；源码内容 `308` bytes；归档 `834` bytes；
- 返回 JSON `42`；
- Host PID `11628`、compiler PID `11662`、worker PID `11757`，三者不同；
- 归档 SHA-256 `b092040fa865bab9fc2e0ef75bd4ebb64fe92f7ce600d01190bcdea32d409da3`。

原始 instrumentation status、完整 marker、设备 fingerprint、APK 摘要与清理记录保存在
[`device-evidence-data/2026-08-26-api25-m9-multifile-source.log`](device-evidence-data/2026-08-26-api25-m9-multifile-source.log)。
测试后只卸载本轮在该模拟器新装的 Host test、Provider 与 Host 三个 package；三次卸载均成功，复查查询为空。

## 保持不变与后续项

- 单 `.java` 文件入口继续可用；Protocol 1.5 缺省元数据兼容由共享测试锁定；
- 当前源码包只接受 Java，不接受 Kotlin、资源、JAR、class 或嵌套归档；
- Entry API 仍为 `4`，`JvmScriptContext` 能力、参数、返回值和终态 observation 不变；
- 入口简单名仍固定为 `Main`；M9-7 才会把 Host 入口选择端到端放开；
- 会话总超时仍为 120 s 上限、并发仍为单会话；分别留给 M9-6 与 M9-8 评估。

至此，Roadmap 的“多类工程样例端到端通过”与“路径穿越/压缩炸弹/重复项全部拒绝”均有共享、Host、
Provider 与 production Binder 四层证据，M9-5 可以关闭。
