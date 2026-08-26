# M9-7 任意 Java 入口类名：Host 端到端放开

## 结论

M9-7 已完成。AutoJs6 Host 现在为单文件和规范多文件源码包各提供一个显式入口 API，调用方可以选择
非 `Main` 的 Java 入口简名；既有 API、编辑器普通启动和 Kotlin 路径继续默认 `Main`。物理脚本文件名、
发送给 ECJ 的逻辑 compilation-unit 名和最终全限定入口类名不再混为同一个概念：

- 单文件物理文件可以名为 `arbitrary-physical-name.java`；
- 调用方选择入口简名 `ScriptEntry`；
- Host 发送逻辑源码名 `ScriptEntry.java`；
- 源码声明 `package demo.entries;` 后，请求入口为 `demo.entries.ScriptEntry`。

最终 API 25 production Binder selector 使用 Provider **Release** APK 完成
Host→compiler service→ECJ→D8→disposable worker→Host 返回链路，返回 JSON 字符串
`"demo.entries.ScriptEntry"`，且 Host、compiler、worker 的三个 PID 互不相同。Roadmap 的“端到端非
`Main` 样例通过”验收条件已经满足。

本项不增加 AIDL 或 wire 字段，不修改 Provider 生产执行逻辑，也不提升版本号。协议身份保持为 JVM
Source Protocol `1.6`、Tagged Wire schema `1.3`、Entry API `4`。

## 锁定版本与工件

| 项目 | 锁定值 |
|---|---|
| Host 实现 | commit `5c3f6f38e1fd1595b4ca576c6647854ec0204ae0`；branch `codex/m9-arbitrary-entry` |
| Provider 实现/冻结工件 | commit `937b70669c5472395e5c109dab71f0b6a2ed8dba`；branch `main` |
| 协议 / Wire schema / Entry ABI | Protocol `1.6` / schema `1.3` / Entry API `4` |
| Host | AutoJs6 `6.8.0` / version code `5281` |
| Provider | `0.8.0-m9` / version code `8` |
| `common-plugin-api.aar` | 11,876 bytes；SHA-256 `c526f4fd0adbf38b36a7bf54f9931385e6cc20050d6ea8fb741c60496af635d5` |
| `protocol-wire-api.aar` | 29,463 bytes；SHA-256 `e044dd3cc9bed84e844174e0963b57a1f67902cf021ccb42d1031d3511ce46da` |
| `jvm-source-api.aar` | 231,834 bytes；SHA-256 `77e57b6b04c09f79d29ccab6f3559d2ff76fdb44dd0481e496f5fcc7dffb6ff6` |

三份 AAR 都从上述干净 Host revision 的 Release 任务重新生成并一起刷新；common 与 wire 的摘要保持
不变，包含共享 Java 布局策略的 `jvm-source-api.aar` 摘要发生变化。
`protocol/protocol-artifacts.lock.json` 继续记录 `sourceDirty=false`、完整 Host revision、来源模块和
精确 SHA-256；Provider 的所有 compile/assemble/lint 任务先执行 `verifyPinnedInputs`。

## 公开 Host API 与兼容行为

既有 API 不改变签名或含义：

~~~kotlin
JvmSourceExplicitRunner.runJvmSourceExplicit(file)
JvmSourceExplicitRunner.runJvmSourcePackageExplicit(sourceRoot, entryFile)
~~~

两者都继续选择 `Main`。M9-7 新增名称不同的显式入口 API，避免 Kotlin 默认参数或 Java
`@JvmOverloads` 产生含糊重载：

~~~kotlin
JvmSourceExplicitRunner.runJvmSourceWithEntryExplicit(
    file = File("arbitrary-physical-name.java"),
    entrySimpleName = "ScriptEntry",
)

JvmSourceExplicitRunner.runJvmSourcePackageWithEntryExplicit(
    sourceRoot = File(projectDirectory),
    entryFile = File(projectDirectory, "demo/entries/ScriptEntry.java"),
    entrySimpleName = "ScriptEntry",
)
~~~

两个新 API 也提供显式 `ScriptEngineService` 版本，并保留 listener、`ExecutionConfig`、覆盖显示路径和
origin URI 参数。它们仍受 `autojs.jvmSource.experimental=true` gate 保护，仍拒绝 Host-side loop 和
非零 loop interval。

非默认入口当前只对 Java 开放。把非 `Main` 名称用于 `.kt` 会在入队、provider discovery 或 bind 前
同步拒绝；Kotlin 的逻辑源码名仍为 `Main.kt`，入口仍为 `Main`。M9-7 没有把普通编辑器启动改成根据
物理文件名或源码内容猜测入口，避免一个文件内多类、重命名和缓存身份产生隐式行为变化。

## 共享名称与布局约束

Host 与 Provider 已共同依赖 `JvmJavaSourceLayoutPolicy`。M9-7 将入口简名校验提取为公开共享方法
`requireEntrySimpleName`，布局分析、单文件快照和源码包快照都调用同一个 profile：

1. 名称必须是非空 ASCII Java identifier profile；首字符只能是 `A-Z`、`a-z`、`_` 或 `$`，其余字符
   还可以是 `0-9`；点、连字符、空格、非 ASCII 字符和数字开头都拒绝；
2. `<EntrySimpleName>.java` 的 UTF-8 长度最多 255 bytes，因此当前 ASCII 简名最多 250 个字符；
3. 可选 `package` 继续使用严格词法分析，最终 `package + '.' + entrySimpleName` 最多 512 bytes；
4. Java Unicode escape、NUL、非严格 UTF-8、重复 package 和歧义 package 继续 fail closed；
5. 入口 ABI 仍要求 public 具体类、public 无参构造器、实现 `AutoJsJvmEntry`，并且编译结果中恰好一个
   具体入口实现。

单文件入口的物理 `.java` 文件名只用于 Host 文件选择和安全打开，可以含空格或连字符；规范快照根据
调用方选择生成 `<EntrySimpleName>.java` 和全限定入口。多文件源码包的入口物理文件则必须精确为
`<EntrySimpleName>.java`，并位于与其 package 一致的 canonical 相对路径；其余 M9-5 的 2–32 文件、
路径、链接、计数、内容总量、STORED-only 归档和双侧重验规则全部保持不变。

## Host 数据流与可观测性

显式授权的 `JvmFileSource` 现在同时携带不可伪造的授权 token、可选 package input 和所选
`entrySimpleName`。`RemoteJvmSourceHost` 只从该授权对象取入口选择，并把它传入实际快照：

1. public runner 在入队前完成 feature、loop、语言和名称 admission；
2. `AndroidJvmSourceArtifactStore` 以 `O_NOFOLLOW` 打开单文件，或调用规范源码包 collector；
3. 共享布局策略生成逻辑 `sourceFileName` 与全限定 `entryClassName`；
4. Host 把这两个既有字段写入 Protocol 1.6 request，因而无需新增 wire tag；
5. Provider 继续独立重验名称、package、入口 class/JAR/DEX descriptor 与 ABI；
6. compiler diagnostic 的安全 `sourceFileName` 使用实际所选逻辑文件名，而不是硬编码 `Main.java`；
7. `RemoteJvmScriptResult` 回传请求的 `sourceFileName` 与 `entryClassName`，让调用方和设备证据直接确认
   实际执行布局，不必从返回值反推。

兼容 test double 若只实现旧 `snapshotJavaSource`，只能处理默认 `Main`；向它传非默认入口会明确拒绝，
不会悄悄降级为 `Main`。Android production store 与更新后的测试 store 明确实现动态入口方法。

## Provider 样例

新增 [`samples/arbitrary-entry.java`](../samples/arbitrary-entry.java)，其物理文件名故意不等于 public
入口类名：

~~~java
package demo.entries;

public final class ScriptEntry implements AutoJsJvmEntry {
    @Override
    public Object run(JvmScriptContext context) {
        return ScriptEntry.class.getName();
    }
}
~~~

`JavaSampleLibraryInstrumentedTest` 的公共 helper 现在接受逻辑源码名和全限定入口名；该样例在 API 25
真实执行 ECJ→JAR 筛选→D8→`DexClassLoader`→ART，断言结果为 `demo.entries.ScriptEntry`。样例文件为
311 bytes，SHA-256 为 `7b95fcbd31aff3ef225cf21350b135ad9490d54a27f40badaa8b4801de3fcefa`。

## 自动验证

所有 Gradle 命令均使用 `--offline`。

Host：

- `:plugin-api:jvm-source-api:testDebugUnitTest`：69 tests，0 failure，0 error，0 skipped；
- `:app:testAppDebugUnitTest`：1,670 tests，0 failure，0 error，3 skipped；
- `:plugin-api:jvm-source-api:lintDebug`：通过；
- common/protocol-wire/jvm-source 三个 Release AAR、Host App Debug、Host AndroidTest APK：组装通过。

新增/强化的 Host 矩阵覆盖共享名称合法/非法/长度边界、动态 package 布局、任意物理文件名、源码包入口
名与入口文件不一致拒绝、单文件与源码包 request/result 元数据、非 `Main` 编译诊断投影，以及默认
`Main` 回归。

Provider：

- `:app:testDebugUnitTest`：163 tests，0 failure，0 error，0 skipped；
- `:app:testReleaseUnitTest`：163 tests，0 failure，0 error，0 skipped；
- `:app:lintDebug`、`verifyPinnedInputs`：通过；
- Provider Debug、Release 与 AndroidTest APK：组装通过；
- API 25 标准 Debug target + Debug AndroidTest 配对的 `JavaSampleLibraryInstrumentedTest`：11/11，
  6.11 s；新增 arbitrary-entry 用例包含在内。

Debug AndroidTest 只作为 Debug variant 的测试制品使用。一次额外的 Debug-test/Release-target 非标准
配对在 Runner 启动前因 Release R8 不保留 Kotlin 测试运行时而失败；它没有进入任何产品测试或用户代码，
不计入支持矩阵。Release Provider 的真实验收由下节 Host production Binder selector 独立完成，并在
恢复 Release APK 后再次运行通过。

最终制品摘要：

| 制品 | 字节数 | SHA-256 |
|---|---:|---|
| Host x86 Debug APK；AutoJs6 `6.8.0` / code `5281` | 43,161,236 | `e3fc0513c4e23e96ee2d072c497bbb72a9d4d6424b91a530b7f1bd5217f22988` |
| Host AndroidTest APK | 1,610,360 | `2103acda032d5967d2749750012766510a512f07111dc87b724568d6994e111b` |
| Provider Debug APK | 33,744,498 | `3de1b614fd585941c0e993f9f7f901a271e24980c7a20c567c4bc9b643c13aca` |
| Provider Release APK；`0.8.0-m9` / code `8` | 27,119,262 | `d2453cc79faa4dbd0e9dced71c3fe2ba482a093006a64a6f125da27249ea3567` |
| Provider AndroidTest APK | 1,027,641 | `2b59c9d12be07e5a53145e33abdfc1f10f8ad7343b0465d8313b22beeb2a887d` |

## Production Binder 设备证据

最终 Host x86 Debug、Host AndroidTest 与 Provider **Release** APK 安装到预检时不含 Host、Host test、
Provider、Provider test 四个相关 package 的 API 25 / x86 模拟器：

- fingerprint：`google/sdk_google_phone_x86/generic_x86:7.1.1/NYC/6695155:userdebug/test-keys`；
- Provider `pkgFlags` 不含 `DEBUGGABLE`；
- selector：
  `JvmSourceR1EndToEndInstrumentationTest#explicitNonMainJavaEntryRunsThroughPinnedProductionProvider`；
- selector 动态创建物理文件 `arbitrary-physical-name.java`，选择 `ScriptEntry`，源码声明
  `package demo.entries;`；
- 结果：`OK (1 test)`，1.345 s；
- request/result：`sourcePayloadKind=single-file`、`sourceFileName=ScriptEntry.java`、
  `entryClassName=demo.entries.ScriptEntry`；
- 返回：JSON `"demo.entries.ScriptEntry"`；
- Host PID `22034`、compiler PID `22066`、worker PID `22124`，三者不同；
- 源码 261 bytes，SHA-256 `5cc164dea748b2ee6078b2d88cfac93887fb3fe37b33f45f663bf101534a33f0`；
- result SHA-256 `09df059fc9e6805feb886430b8cbddacc421077aae99087738e21f3eee49d1f7`。

完整 instrumentation status、`JVM_SOURCE_M9_ENTRY_EVIDENCE` JSON、设备 fingerprint、APK 摘要、样例
测试结果与清理记录保存在
[`device-evidence-data/2026-08-26-api25-m9-arbitrary-entry.log`](device-evidence-data/2026-08-26-api25-m9-arbitrary-entry.log)。

测试结束后只卸载预检时确认不存在、由本轮新装的四个 package；四次卸载均返回 `Success`，最终精确包名
查询为空。本地 APK 和原始 Host 工作区都未删除或修改；隔离 worktree 的临时 `sign.properties` 副本已
移除，原签名文件保留。临时 `app/sm003.jks` 是被 Git 忽略的构建副本；执行策略拒绝删除凭据类二进制，
因此它仍留在隔离 worktree，不属于任何提交，原始 keystore 同样未改动。

## 保持不变与后续项

- Protocol 1.6 的 source FD、request tags、AIDL ownership、Entry API 4 和能力 profile 不变；
- `Main` 是兼容默认值，不再是 Java provider 的固定入口；
- Kotlin 非 `Main`、Java/Kotlin 混编、自定义 classpath、注解处理器和自动扫描入口不属于本项；
- 多文件源码包仍只接受 Java，所有 M9-5 安全上限保持不变；
- 总截止时间仍采用 M9-6 的 no-go 结论；Provider 仍为单活动会话，排队/并发语义留给 M9-8。

至此，M8-5 的 Provider 参数化先行能力已由 Host 的公开 API、共享 pre-bind 校验、单文件/源码包快照、
request/diagnostic/result 元数据、样例和 production Release Binder 证据完整接通，M9-7 可以关闭。
