# M7 编译诊断 wire 审计

## 结论

当前冻结的 Protocol 1.1 已经支持一次会话回传多条编译诊断，不需要新增 schema、修改 AIDL，
也不需要移交 M9。协议继续使用“一个 `onDiagnostic(byte[])` 调用承载一个
`JvmSourceDiagnostic` frame”的模型；provider 可以在终态前重复调用。

## 冻结输入证据

审计基于 `protocol/protocol-artifacts.lock.json` 锁定的 AutoJs6 源提交
`d21c69a2523a529ce6e2cd5d7dc3ced49cbaf74d`，而不是宿主仓当前工作区：

- `IJvmSourceCallback.aidl` 将 `onDiagnostic(byte[])` 定义为普通同步回调，没有单次会话只能调用一次的限制。
- 宿主 `CallbackCollector` 每次解码一个 `JvmSourceDiagnostic`，追加到
  `ArrayList<JvmSourceDiagnostic>`，最终以列表交给 `JvmSourceExecutionSink`。
- 宿主 `JvmSourceSessionLifecycle.acceptDiagnostic(encodedBytes)` 允许 STARTED 与终态之间重复接收，
  并对所有 frame 的编码字节数做累计上限检查。
- `JvmSourceCodec` 的 schema `785` 编解码单个诊断；重复 frame 不改变该 schema。

因此，多诊断是现有协议的合法流式用法，不是“在一个 frame 中新增诊断数组”。

## Provider 实现

ECJ `BatchCompiler` 仍将输出写入有界 writer。`EcjDiagnosticSanitizer.sanitizeAll` 识别每个
`N. ERROR in ...` / `N. WARNING in ...` 块，按原顺序分别完成位置提取、分段脱敏和消息长度限制。
没有标准 ECJ block header 的文本仍作为单条兼容诊断处理。

`RemoteJavaSourceSession` 将每个块编码为独立的 `JvmSourceDiagnostic`：

1. 编码前按当前剩余额度调用 `EncodedDiagnosticBudget.encodeWithin`；
2. 用 CAS 原子预留实际 frame 字节数，编译诊断与运行时诊断共用请求预算；
3. 用一个 callback-lane 任务顺序执行多个同步 `onDiagnostic` 调用，避免大量 ECJ 错误耗尽
   lane 的 64 槽任务队列，同时保持一个调用一个 frame；
4. 无法容纳最小合法 frame 时停止回传该条，宿主侧仍会独立复核累计预算。

## 验证边界

- 真实 ECJ 多错误源码单测断言两个错误被拆成两个有序诊断，行号分别为 3 和 4，消息互不串入。
- 累计预算单测以两个完整 Protocol 1.1 frame 的精确字节数为预算，验证两个 frame 均可独立解码且余额归零。
- M7-1 的路径、摘要、Binder、uid/pid、metadata 脱敏与混合消息保留率测试继续覆盖每个拆分后的 block。
- 完整离线门禁继续负责全部单元测试、严格 Lint 与 APK 打包。
