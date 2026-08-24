# R4 多 DEX 实现与验证

本文记录 M8-2 的实现边界与 2026-08-25 设备验证结果。结论为 **go**：Java provider 在 API 24/25
与 API 27+ 已从单 `classes.dex` 放宽到有界 R4 DEX 集合；API 26 保持单 DEX。Protocol 1.1、
Entry API 2、宿主调用方式及 32 MiB 总预算均保持不变。

## R4 不变量

- 每次编译必须生成 1 至 4 个普通 DEX 文件。
- 名称必须严格为 `classes.dex`、`classes2.dex`、`classes3.dex`、`classes4.dex` 的连续前缀；空集合、
  缺号、重排、前导零、别名和第 5 个文件全部拒绝。
- 每个文件必须非空，集合大小之和不得超过 `JvmSourceContract.MAX_DEX_ARTIFACT_BYTES`（32 MiB）。
  编译、缓存复制、缓存物化和 worker 读取分别执行有界检查。
- 每个 DEX 独立通过 magic/version、header、Adler-32、SHA-1、SHA-256、section/map、class descriptor
  与运行 API 交集校验；之后再验证所有 ECJ class 的集合完整性，并拒绝跨 DEX 重复 class。
- 单 DEX 继续把该文件的原始 size/SHA-256 写入既有 `JvmSourceResult` 字段。多 DEX 的 size 为总字节数，
  SHA-256 为域 `autojs6-java-provider-dex-set-r4-v1` 下，对每个规范名称、大小和文件摘要进行长度分帧后
  得到的集合摘要。因此旧的单 DEX 结果语义不变，多 DEX 又能绑定整个集合。
- 设备准入再收窄文件数：API 24/25 和 API 27+ 上限为 4，API 26 上限为 1。Android 8.0 / API 26
  仅公开单 `ByteBuffer` 的 `InMemoryDexClassLoader` 构造器；多 buffer 构造器从 API 27 才存在。多个
  单-buffer loader 串联无法为任意循环跨 DEX 引用提供同一个 namespace，因此不会采用该不可靠方案。

DEX 从不交给 AutoJs6 宿主。上述集合语义只存在于 provider 的 `:compiler` → `:worker` 私有边界，
所以没有修改冻结的 Protocol AAR、协议版本或宿主最低版本。

## 编译、交接与加载

| 阶段 | R4 行为 |
|---|---|
| D8 输出 | `DexIndexed` 输出经过规范名称与最多 4 文件检查。D8 选项 identity 新增 R4 profile 和文件上限。 |
| 集合身份 | `ProviderDexSetIdentity` 绑定规范文件序列、逐文件 size/SHA-256、总量和集合摘要。 |
| 内部 Binder | AIDL 传递名称数组、大小数组、扁平的 32-byte 摘要数组及同数量只读 FD；任一 framing/count 不一致即拒绝，并关闭全部 FD。 |
| worker 校验 | 先完整读取并逐文件验 DEX，再验 class descriptor 并集与跨文件唯一性，校验完成前不创建 ClassLoader。 |
| API 27+ | 多 DEX 使用 `InMemoryDexClassLoader(ByteBuffer[], parent)`；单 DEX 继续使用 API 26 已有的单 buffer 构造器。验证过的 byte array 保留至 loader 关闭。 |
| API 26 | 协议 loader kind 仍为 in-memory；单 DEX 正常使用单 buffer 构造器，多 DEX 在编译后及 worker 校验时双重 fail-closed。 |
| API 24/25 | 每个 DEX 都按 R2 顺序执行“创建空文件 → 打开流 → 移除写位 → 写入 → sync → close”，随后用 path-separator 连接的私有路径创建 `DexClassLoader`。任一文件失败会删除整个 request root，绝不发布部分集合。 |
| 成功终态 | worker 对外仍返回既有单个 DEX version/loader kind 字段；集合内所有 DEX 必须具有相同的已准入版本和加载分支。 |

## 缓存迁移

缓存 manifest 从 schema 1 升级为 schema 2，并记录 DEX 数量及每个规范文件身份。缓存 key 的 provider
实现修订由 `r3-cache-v2` 升为 `r4-cache-v3`，D8 options identity 同时包含 R4 输出参数，因此旧产物
在 key 和 manifest 两层都会 fail-closed。

一个多 DEX 命中始终作为一个事务处理：HMAC 认证 manifest、核对目录的精确文件集合、逐文件摘要与
结构重验、复制到新会话工作区、再次逐文件及集合校验。第二个或后续 DEX 被篡改会使整个缓存目录失效
并被清理，不会退化为部分命中。

## 超过单 DEX 方法引用上限的设备 fixture

仪器测试 `JavaMultiDexPipelineInstrumentedTest` 在设备上动态生成源码，避免提交数 MiB 的静态样例：

- 128 个 helper class，每类 512 个静态方法，共 65,536 个辅助方法，另含构造器和入口方法；
- ECJ 产出 129 个用户 class；
- D8 稳定产出 `classes.dex` 与 `classes2.dex`，总计 2,830,752 bytes；
- `Main.run` 调用最后一个 helper 方法，预期并实际返回 JSON number `65535`；
- 同一 DEX 集合先经当前进程直接加载，再通过真实、一次性的 `:worker` Binder 服务传输、加载和执行；
  后者同时覆盖 AIDL 数组 framing、多 DEX 与 stdout/stderr FD 所有权、worker PID 隔离和集合结果摘要。

2026-08-25 的验证记录如下：

| 设备 | ABI | 加载分支 | DEX / bytes / class | 直接加载 | 独立 worker | 测试耗时 | 结果 |
|---|---|---|---:|---:|---:|---:|---|
| `AVD_API_24`（`emulator-5558`，API 24） | x86 | `PRIVATE_DEX_CLASS_LOADER` | 2 / 2,830,752 / 129 | 通过 | 通过 | 26.277 s | 1 test，0 failure/error |
| `DEX_R1_API36_X64`（`emulator-5562`，API 36） | x86_64 | `IN_MEMORY_DEX_CLASS_LOADER` | 2 / 2,830,752 / 129 | 通过 | 通过 | 16.202 s | 1 test，0 failure/error |

关键 logcat 证据：

```text
R4_MULTIDEX_EVIDENCE api=24 loader=PRIVATE_DEX_CLASS_LOADER dexFiles=2 dexBytes=2830752 classes=129 direct=true
R4_MULTIDEX_BINDER_EVIDENCE api=24 loader=PRIVATE_DEX_CLASS_LOADER dexFiles=2 dexBytes=2830752 classes=129 workerIsolated=true
R4_MULTIDEX_EVIDENCE api=36 loader=IN_MEMORY_DEX_CLASS_LOADER dexFiles=2 dexBytes=2830752 classes=129 direct=true
R4_MULTIDEX_BINDER_EVIDENCE api=36 loader=IN_MEMORY_DEX_CLASS_LOADER dexFiles=2 dexBytes=2830752 classes=129 workerIsolated=true
```

复现命令（PowerShell，设备选择必须显式设置）：

```powershell
$env:ANDROID_SERIAL='emulator-5558'
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.plugin.jvmsource.java.JavaMultiDexPipelineInstrumentedTest" --offline

$env:ANDROID_SERIAL='emulator-5562'
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.plugin.jvmsource.java.JavaMultiDexPipelineInstrumentedTest" --offline
```

API 24 是 API 24/25 私有磁盘加载分支的最低版本代表；API 36 覆盖 API 27+ 多 buffer 内存加载分支。
API 26 的单 buffer 正向代码路径保持不变，新增纯策略与集合校验用例固定其多 DEX 拒绝边界；本次没有
把该静态 SDK 结论误记成 API 26 设备证据。已有 R2
设备测试继续单独审计首字节写入前只读与受控失败清理时序；R4 单元测试额外证明该顺序会逐个应用到
集合中的每个文件，并且第二个文件失败时不会越过发布边界。

## 回归门禁

- Debug unit suite：137 tests，全部通过；包括名称/数量、API 26 特例、transport framing、集合摘要、逐文件 DEX
  校验、跨文件 class 去重、只读发布和缓存第二 DEX 篡改。
- 原有单 DEX 适配器与全部既有测试保留；单 DEX 的 raw size/SHA-256 兼容性有独立断言。
- Debug/Release 单元测试、Debug lint、Debug/Release APK 构建继续由仓库离线门禁统一执行。
