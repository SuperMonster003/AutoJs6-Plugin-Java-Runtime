# M9-1 Capability Set 2：剪贴板读写

## 结论

M9-1 选择剪贴板纯文本读写作为 Capability Set 2，并在 AutoJs6 与 provider 两侧完成 Protocol 1.2、
Entry API 3 的协同实现。读写权限相互独立，默认均不授予；请求与成功响应都经过双侧硬编码方法白名单、
规范 payload 校验和字节上限。2026-08-25，最终候选已在 API 24 跑通 provider 的真实
ECJ→D8→ART/worker 链路，并在 API 36 跑通 AutoJs6 production Activity→Binder→worker 路径的实际
写入、回读和恢复。

## 锁定的协商面

| 项目 | 锁定值 |
|---|---|
| 协议 / Entry ABI | Protocol `1.2` / Entry API `3` |
| 最低宿主 | AutoJs6 version code `5277` |
| Provider | `0.4.0-m9` / version code `4` |
| 宿主最终源码 | commit `b87d9827cbe7725839b1728ca7e4ab2daf306352`；branch `codex/m9-jvm-clipboard` |
| `common-plugin-api.aar` | SHA-256 `c526f4fd0adbf38b36a7bf54f9931385e6cc20050d6ea8fb741c60496af635d5` |
| `protocol-wire-api.aar` | SHA-256 `e044dd3cc9bed84e844174e0963b57a1f67902cf021ccb42d1031d3511ce46da` |
| `jvm-source-api.aar` | SHA-256 `5ff4a4d6e72c0db17744a679ad9702e2aafc36084cefa491b8022191e0502a9d` |

能力与 wire 方法固定如下：

| Entry API | Capability | wire 方法 | 请求 payload | 成功响应 payload |
|---|---|---|---|---|
| `clipboard().getText()` | `CLIPBOARD_READ` / `clipboard.read` | `clipboard.get` | 精确 `{}` | `{"text":"..."}` |
| `clipboard().setText(text)` | `CLIPBOARD_WRITE` / `clipboard.write` | `clipboard.set` | `{"text":"..."}` | 精确 `true` |

两种文本 payload 共用规范编解码器：只接受严格 UTF-8、恰好一个 `text` 字段、无重复或额外字段、无
非配对 surrogate，并要求重新编码后与输入字节完全相同。文本上限为 16 KiB UTF-8；它叠加在既有
64 KiB host-call wire 上限之内。空字符串合法。

## 安全边界

一次调用要经过四层独立约束：

1. 宿主只把 provider 广告能力与硬编码支持集的交集放入会话授权；读写不会相互蕴含。
2. worker 的 `RemoteJvmScriptContext` 先检查对应 capability，再从固定方法表选择
   `clipboard.get` 或 `clipboard.set`。
3. provider 的 `SessionHostBridgeProxy` 分别校验出站请求和宿主成功响应；未知方法、畸形 payload、
   非规范 JSON 与越界文本均在 provider 边界失败。
4. 宿主 `JvmHostAppLaunchBridge` 再次执行独立方法白名单、capability 映射、payload 校验和调用配额；
   Android action 的异常只映射为稳定的 `CLIPBOARD_GET_FAILED` / `CLIPBOARD_SET_FAILED`，不会把
   剪贴板内容或内部异常回传给 worker。

Android 10+ 另有平台前台限制。设备联调发现，应用真正退到后台后系统服务会静默拒绝剪贴板访问；
仅依赖 `ClipboardManager` 的无返回值写入会造成假阳性。最终宿主在调用前检查 AutoJs6 是否存在
resumed Activity：后台状态在触碰系统剪贴板前 fail closed；同一应用的模态窗口不会被误判为后台。
API 28 及以下保持原平台行为。

## 自动化验证

- 宿主 `jvm-source-api` 44/44 单测覆盖空值、16 KiB 精确边界、越界、畸形 UTF-8、非配对
  surrogate、重复/额外字段和 Java 调用兼容性；宿主 app 的 12/12 定向单测覆盖双白名单、独立拒绝、
  畸形请求、稳定脱敏失败，以及 Android 10+ 前台 guard。
- provider `JavaHostCapabilityWirePolicyTest` 覆盖四个允许方法、规范请求/响应、未知方法、额外字段和
  越界响应；`RemoteJvmScriptContextTest` 覆盖授权往返、读写各自未授权时零 dispatch、畸形宿主响应。
- `JavaSampleLibraryInstrumentedTest` 使用最终 Provider Debug APK
  `b5d29a9d87f5b715748bc452f3dda10951349fef26b2b4180cc9eb9faf2db72b` 与 AndroidTest APK
  `a2764ec265ac8fea22d1aa7d3e6a9cceafa2cb6c1dd61da0340a20d29c0c6b2c`，在 API 24 模拟器
  8/8（4.836 s）和 Sony G8441 API 28 真机 8/8（8.062 s）通过真实 ECJ→D8→ART 执行恢复型
  样例，并验证 `CLIPBOARD_READ` / `CLIPBOARD_WRITE` 分别默认拒绝且未发生 host dispatch。
- 最终离线 gate 中 Debug/Release JVM 单测各 146/146；lint 为 0 error；Debug、Release、
  AndroidTest APK 均构建成功。Release APK SHA-256 为
  `9b3ddfd78eae5bbf211a67eb081e5721e077e9fbdcd35acb8e3eec6661e6c4fd`。

恢复型样例位于
[`samples/capability-set-2-clipboard.java`](../samples/capability-set-2-clipboard.java)。它先保存原文本，
写入并回读 `M9 clipboard 你好`，再恢复原文本并二次回读确认；`finally` 还提供失败恢复路径。

## API 36 宿主生产路径证据

| 项目 | 观测值 |
|---|---|
| 设备 | `emulator-5562`；API 36；x86_64 |
| Fingerprint | `google/sdk_gphone64_x86_64/emu64xa:16/BE4B.251210.005/14574095:userdebug/dev-keys` |
| AutoJs6 APK | version `6.8.0` / code `5277`；SHA-256 `2617d7c6fd734078274b6cd152e9c61e7bad113c5a9a8fcce872bf9b930ffd1e` |
| Provider APK | version `0.4.0-m9` / code `4`；SHA-256 `b5d29a9d87f5b715748bc452f3dda10951349fef26b2b4180cc9eb9faf2db72b` |
| APK signer | 二者 SHA-256 `31a681fcfffb3e428420cae280ded89292b12a3b0f59e19b7a73e32a8ae4c213` |
| 样例源码 | SHA-256 `da1dbc200f5f0a40fee9456b0d036b8d649c067078fe909fe8529dfce156fbf9` |
| Provider session | 1,687 ms；`COMPILE=229`、`D8=635`、`LOAD=3`、`RUN=39`、`TERMINATION=30` ms |

生产入口为：

```text
ShortcutActivity
  → Scripts.executeLaunch
  → RemoteJvmScriptEngine / RemoteJvmSourceHost
  → JvmHostAppLaunchBridge
  → provider :compiler Binder
  → disposable :worker
```

AutoJs6 `MainActivity` 在执行期间保持 resumed；控制台得到：

```text
clipboard round-trip: M9 clipboard 你好; restored=true
```

同一 logcat 窗口内 `Denying clipboard access to org.autojs.autojs6` 为 0 条，终态
`:worker` PID 为空。设备内重算的两个 base APK 摘要与本地输入完全一致。原始、未重新序列化的记录为：

| 文件 | 字节数 | SHA-256（LF 字节） | 内容 |
|---|---:|---|---|
| [`2026-08-25-api36-m9-clipboard-host.jsonl`](device-evidence-data/2026-08-25-api36-m9-clipboard-host.jsonl) | 514 | `40dedf4eb80051ed9be8198a7277e8db9dd1fd4fba7bce3ac73f5192cfded820` | provider schema-v1 完整会话观测 |
| [`2026-08-25-api36-m9-clipboard.log`](device-evidence-data/2026-08-25-api36-m9-clipboard.log) | 273 | `c09f54716184ab09e5e782fcf0fd3004860c19c34f46c27e81273b81dc79aba7` | 实际 marker、恢复结果与宿主完成行 |

API 36 direct-path 按既有设备证据流程临时把宿主 `MANAGE_EXTERNAL_STORAGE` app-op 从测试前
`default` 调为 `allow`；源码始终位于宿主私有 cache。完成后 app-op 已恢复为 `default`，host/private、
`/data/local/tmp` 与 UI dump fixture 均已删除，本轮新增的宿主/provider 包已卸载，`emulator-5562`
已正常关闭。API 24 的 provider/test 包及 API 28 真机上仅由本轮新增的 provider/test 包也已卸载；
两台设备原有 AutoJs6 均未覆盖或卸载。
