# M8-4 编译缓存跨进程持久化评估

本文记录 2026-08-25 的 M8-4 评估、原型、设备对照和安全评审。最终结论为：

> **No-go。当前三进程/同 UID 架构下不接入 Android Keystore 持久 HMAC key，继续使用
> `:compiler` 进程 epoch 随机 key；进程重启后缓存全部失效。**

Android Keystore 原型在 API 24 上实现了预期性能功能：同一缓存经过 5 次 `:compiler` 强制重启后命中
5/5，而旧 epoch key 控制组命中 0/5。然而安全红队样例随后证明，普通 R1 用户 Java 代码可在一次性
`:worker` 中加载并使用同一个生产 alias。原因不是 key material 被导出，而是 Android Keystore 的应用
隔离以 **UID** 为边界；本插件的 compiler 与 worker 虽然是不同进程，却共享应用 UID。

一旦不受信任的 worker 能请求 Keystore 代算 HMAC，“worker 不知道 compiler 缓存认证秘密”这一现有边界
就被破坏。性能收益不能覆盖真实性边界回归，因此原型运行代码、Keystore alias、测试和临时 benchmark
build type 已全部从最终树移除，只保留本决策记录和不含源码/路径/摘要的 schema-v1 原始观测数据。

## 原安全设计与候选变化

### 当前保留的设计

`CompilationArtifactCache` 在每个 `:compiler` 进程初始化时生成新的 256-bit 随机 HMAC-SHA-256 key。
key 只存在于 compiler 内存，且缓存只在以下两项同时成立时启用：

- APK 实际为 non-debuggable；
- `:compiler` 已成功执行 `PR_SET_DUMPABLE=0` 并回读确认。

manifest HMAC 认证后，lookup 仍核对精确文件集合、schema、canonical cache key、TTL、运行 profile、
JAR 结构/类摘要、每个 DEX 的大小与 SHA-256、DEX header 与 class descriptor 集合。命中复制到新会话
workspace 后，JAR 与全部 DEX 会再次完整验证。进程重启使旧 HMAC 无法认证，维护路径删除旧条目并重新
编译；这是有意用性能换取 compiler-only secret boundary。

### 被否决的候选

原型把随机 `ByteArray` key 替换为不可导出的 `CompilationCacheAuthenticator`，生产环境从固定 alias
加载或创建 Android Keystore 256-bit `HmacSHA256` key：

- alias：`org.autojs.jvm-source.java-compilation-cache-hmac-v1`；
- purpose：`PURPOSE_SIGN`；
- user authentication：显式 `false`；
- 加载后要求 `SecretKey.format == null` 且 `encoded == null`；
- 固定 domain 文本 HMAC 自检失败时只禁用 cache，不影响 ECJ/D8 主路径；
- debuggable 与 non-dumpable 门禁、完整 lookup/materialize 复验全部保持不变。

这个 profile 本身符合 Android Keystore API，也确实没有把 raw key bytes 暴露给应用进程。失败点在于
“谁可以使用 key handle”：同一 UID 内的 worker 与 compiler 权限相同。

## 阻断性安全发现：进程隔离不等于 Keystore 隔离

### 架构事实

manifest 中的 compiler 使用 `android:process=":compiler"`，worker 使用 `android:process=":worker"`，但
worker 没有 `isolatedProcess=true`，也不是另一个 application/package。两者因此共享 provider 应用 UID。
Android Keystore 的 APP domain 会把 caller UID 纳入 key descriptor；它能阻止其他应用 UID 使用 alias，
不能区分同一应用 UID 下的两个 Linux 进程。

这也是为什么“key 不可导出”不足以通过 gate：攻击者不需要读取 32-byte key，只需调用：

```java
KeyStore store = KeyStore.getInstance("AndroidKeyStore");
store.load(null);
SecretKey key = (SecretKey) store.getKey(
    "org.autojs.jvm-source.java-compilation-cache-hmac-v1",
    null
);
Mac mac = Mac.getInstance("HmacSHA256");
mac.init(key);
byte[] tag = mac.doFinal(controlledBytes);
```

### 为什么当前 R1 profile 可到达这条路径

`JavaSourcePolicy` 当前负责严格 UTF-8、文件名/入口类和 package layout；它不是 Java 平台 API sandbox。
ECJ 的 API 24 Android bootclasspath 公开 `java.security.KeyStore`、`javax.crypto.Mac`、`SecretKey`、
`java.io` 等类型，现有 JAR/DEX validator 关注工件结构、identity、class 完整性和 runtime profile，也没有
禁止这些 method/type references。因此不需要反射、hidden API、native code 或 provider 内部 classpath，
普通公开 Java API 就足够触达生产 alias。

### API 24 生产 Binder 攻击证据

在 Keystore 候选 benchmark APK 已建立生产 alias 后，向 AutoJs6 私有 cache 放入一次性攻击源码，并通过
真实生产路由执行：

`ShortcutActivity → JvmSourceExplicitRunner → RemoteJvmSourceHost → provider Binder → :worker`

源码只做四件事：加载上述 alias、用 `Mac` 对固定 ASCII 输入执行 HMAC、确认结果为 32 bytes，并且仅在
成功后向 provider 私有 `files/` 写入 ASCII marker `worker-used-cache-key`。结果如下：

- marker 文件出现，内容精确为 `worker-used-cache-key`，SHA-256 为
  `db276692075fe3fc53d8e15100ab352863432a2fc71150d14f05bca8816561ce`；
- schema-v1 行显示 `COMPILE=156`、`D8=420`、`LOAD=55`、`RUN=7` ms，说明攻击源码经过正常 ECJ、D8、
  DEX 验证和一次性 worker 执行，而不是测试进程直接调用；
- worker 终态后正常退出；临时源码、marker、ADB staging 文件和测试 alias 随即删除。

攻击观测原始行保存在
[`2026-08-25-m8-4-api24-worker-key-access-probe.jsonl`](perf-baseline-data/2026-08-25-m8-4-api24-worker-key-access-probe.jsonl)。
它不包含 alias、源码、文件路径、摘要、UID/PID 或 Binder 标识；marker 摘要与上述执行步骤用于补足结果
证明。

这条证据已经足以否决候选。worker 若可使用认证 key，就可以读取同 UID 私有 cache、请求合法 HMAC，
从而使“只有 compiler 能发布 authenticated manifest”的假设不再成立。无需等到完整 cache-poisoning exploit
完成，安全 gate 就应 fail closed。

## 为什么没有用简单 denylist 修补后合入

直接禁止 `java.security.KeyStore`、`javax.crypto.*` 三个常量引用只能挡住上面的最短样例，不能自动构成
可信边界。要把 denylist 提升为安全论证，还必须同时审查并封锁至少以下替代面：

- reflection、method handles、动态 class loading 与 provider parent classloader；
- Android Binder/系统服务直达 Keystore 的路径；
- native library/process execution、序列化或其他可充当动态调用代理的 API；
- D8 desugaring/多 DEX 后的全部 method/type reference，以及未来新增 Java API；
- package-private/internal Kotlin 实现在字节码层面的可见性；
- API 24/25 文件型 loader 与 API 26+ 内存 loader 的差异。

本仓当前从未宣称 R1 是通用 Java capability sandbox；临时补几个字符串会制造虚假的安全保证。可靠方案
需要先改变 UID 边界，或引入有完整威胁模型、字节码控制流覆盖和攻击矩阵的受限 Java runtime profile。
这已经超出“M8-4 只替换 HMAC key 生命周期”的范围，所以本次不扩张实现，明确 no-go。

## 性能原型证据（被安全 gate 否决）

性能结果仍被保留，因为它量化了未来若先解决 UID 隔离后可获得的上限，但不能作为当前合入理由。

### 环境与方法

设备为 `AVD_API_24`（`emulator-5558`），API 24 / x86，fingerprint：

`google/sdk_google_phone_x86/generic_x86:7.0/NYC/6696031:userdebug/dev-keys`

输入为 `samples/return-values.java`（SHA-256
`2176b2a7a8341ceb04cedc7f74e2bbb82c6c63503a67101aeedc45f0243bcd28`），走 M7-4/M7-5 同一生产 Binder
路由。为同时开启真实缓存与取出私有 schema-v1 行，评估期间临时创建了 non-debuggable benchmark build
type；控制组与候选组各自先卸载/重装 provider，从空 cache 和新安装开始。每组第 1 次执行 seed，随后
每次确认 JSONL 完整落盘、worker 退出，再强制终止当前 compiler PID并执行同一源码，共 5 次。

| 组别 | compiler PID 序列（seed → 5 次重启） |
| --- | --- |
| process-epoch 控制 | `21255 → 21529 → 21617 → 21704 → 21795 → 21880` |
| Keystore 候选 | `22972 → 23053 → 23129 → 23206 → 23282 → 23358` |

每条重启样本的 compiler profile 都是 `COLD`。控制源码内容对应临时观测提交 `b760ed8`；Keystore
原型源码对应 `760b1c3`，随后由 `7077d65` 显式 revert。实际安装 APK 以 SHA-256 为权威标识：

| APK | SHA-256 |
| --- | --- |
| process-epoch benchmark 控制 | `6b2a9a8c907b5b26a4d453fa8bcc070edd2383636ce063836ca0fc969e9c871d` |
| Keystore benchmark 候选 | `1190b7ccf45bff793383d62f76068123f73ef49349dba2292d1d708d16be7ace` |

### 对照结果

| 指标（只统计 seed 后 5 次新进程） | process-epoch 控制 | Keystore 候选 |
| --- | ---: | ---: |
| 跨进程 cache hit | 0 / 5（0%） | 5 / 5（100%） |
| `MISS/INVALID_OR_EXPIRED` | 5 / 5 | 0 / 5 |
| 重新执行 `COMPILE` 或 `D8` | 5 / 5 | 0 / 5 |
| session 原始序列（ms） | 1280, 1326, 2011, 927, 899 | 281, 261, 252, 269, 287 |
| session p50 | 1280 ms | 269 ms（-1011 ms，-79.0%） |
| session p90（nearest-rank） | 2011 ms | 287 ms（-1724 ms，-85.7%） |

候选每个新进程都是 `cache.outcome=HIT`、`hits=1`、`misses=0`、`publications=0`，且
`COMPILE/D8=null`。这证明持久 key 的功能和收益方向成立；它不改变同 UID worker 可使用 key 的安全
反证。安全 gate 的优先级高于性能 gate，所以最终仍是 no-go。

样本量只有 5，AVD 未隔离宿主机调度；时延仅是同机工程证据，不推广为所有设备的性能承诺。

### 原始证据

| 文件 | 行数 | SHA-256（LF 字节） |
| --- | ---: | --- |
| [`2026-08-25-m8-4-api24-epoch-control.jsonl`](perf-baseline-data/2026-08-25-m8-4-api24-epoch-control.jsonl) | 6 | `9e576dda415f39388bbd68b42583c85ad8fed967eab3cc078c001cd20af08866` |
| [`2026-08-25-m8-4-api24-keystore-rejected-candidate.jsonl`](perf-baseline-data/2026-08-25-m8-4-api24-keystore-rejected-candidate.jsonl) | 6 | `e2423f68706f80c869b8edbf2fcc49fb706415f37e291258f3409bee1ccabc4c` |
| [`2026-08-25-m8-4-api24-worker-key-access-probe.jsonl`](perf-baseline-data/2026-08-25-m8-4-api24-worker-key-access-probe.jsonl) | 1 | `44613b47fb67ee9912178fe484d33bca8e0a14cca0fc0454b403db0cc1ea419d` |

## 原型验证与最终回退状态

原型阶段曾完成以下验证：

- Debug/Release JVM 单测各 140 个，包括同一 key 跨 cache 实例命中、换 key 拒绝、普通 SHA-256 不能
  伪造 HMAC、Keystore provision 失败只禁用 cache，以及 materialize 后 JAR/DEX 复验；
- API 24 专用 alias 两次 fresh load 得到相同 32-byte HMAC，`format`/`encoded` 都为 `null`，finally
  删除 alias；
- API 24 生产 Binder 路径控制/候选共 12 次功能执行，以及 1 次阻断性 worker key-access 攻击执行。

最终树不保留这些原型类或 benchmark build type。它恢复到评估前的 process-epoch authenticator、Debug
私有观测双门禁和 Release 零导出边界；既有 `newCompilerProcessEpochRejectsOldEntries` 与所有 JAR/DEX
重校验测试继续作为当前行为契约。换言之，Roadmap 勾选表示“评估完成并关闭”，不表示能力已发布。

回退后的最终离线门禁为 `BUILD SUCCESSFUL`：Debug/Release JVM 单测各恢复为 137 个，`lintDebug`、
`assembleDebug` 与 `assembleRelease` 全部通过。测试数量回到 M8-4 前基线，也从构建侧确认原型测试和
benchmark variant 没有留在正式变体图中。

## 生命周期结论

在当前实现中：

| 事件 | 最终行为 |
| --- | --- |
| compiler 进程存活 | 同一 epoch 内可正常命中，仍执行全部 lookup/materialize 复验 |
| compiler 退出/被回收/强制终止 | 新随机 key；旧缓存认证失败并清理，重新 ECJ+D8 |
| APK debuggable 或 non-dumpable 未确认 | cache 完全禁用 |
| provider 更新、签名/版本/工具链/设备 profile 漂移 | canonical cache key 失配；不能复用 |
| code-cache 被系统回收 | 普通 miss；重新编译，不影响正确性 |

Android backup 文档明确始终排除 `cacheDir`/`codeCacheDir`，本项目还固定 `allowBackup=false`；无论当前
epoch 设计还是被否决的候选，都不设计跨设备 cache 迁移。

## 重新开启条件

只有先满足以下至少一种架构前提，才重新评估持久 HMAC：

1. **worker 使用真正的 isolated UID。** 例如评估 `isolatedProcess` 或独立 package，并重新解决 API 24/25
   私有 DEX 文件 loader、内部 Binder caller identity、FD 交接、host bridge、资源统计和一次性退出语义；
2. **cache key 由 worker 无法访问的独立 UID/系统组件持有。** 不能只是换进程名或隐藏 alias；
3. **形成经安全评审的受限字节码/runtime sandbox。** 必须覆盖 direct API、reflection、method handles、
   class loading、Binder、native/process、desugaring 和多 API loader 矩阵，不能只做字符串 denylist。

重新开启时，可直接复用本文件的 0/5 → 5/5 性能方法，但必须先运行 worker key-access 负向攻击：只有
worker 无法枚举、加载或使用生产认证 key，才允许进入性能验收。

## 官方依据

- [Android `KeyGenParameterSpec` API reference](https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec)
  给出 Android Keystore HMAC-SHA-256 的生成、`Mac` 使用和后续 `KeyStore.getKey` 加载示例，并说明生成的
  对称 key material 对应用不可访问。攻击不需要导出 material，所以不与该保证矛盾。
- [AOSP Hardware-backed Keystore](https://source.android.com/docs/security/features/keystore) 说明应用 alias
  映射到 APP domain，并把 caller UID 纳入 key identity。这正是“其他应用不可用、同 UID 多进程可用”的
  决策依据。
- [`KeyGenParameterSpec.Builder.setUserAuthenticationRequired`](https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec.Builder#setUserAuthenticationRequired%28boolean%29)
  说明默认不要求用户认证；原型显式 `false` 只解决后台可用性，不能提供进程级授权。
- [Android Auto Backup](https://developer.android.com/identity/data/autobackup#Files) 明确
  `getCacheDir()`、`getCodeCacheDir()` 与 `getNoBackupFilesDir()` 始终排除在备份外。
