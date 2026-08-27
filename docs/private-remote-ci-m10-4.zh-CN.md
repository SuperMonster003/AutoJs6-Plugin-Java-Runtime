# M10-4 Private 远端、历史邮箱清洗与 CI

## 决策

M10-4 先以 GitHub Private 仓库交付。Roadmap 的验收边界是可恢复的远端、一次成功 push 和最终
`main` 的 CI 绿灯，不要求代码立即公开。未来切换为 Public 前仍需复核仓库内容、Actions 历史/日志、
协作者和发布资产，但不需要再次迁移 Git 历史邮箱。

首次远端 push 之前，所有拟发布历史的 author、committer 和 annotated-tag tagger 已统一为 GitHub
ID-based noreply 身份：

```text
SuperMonster003 <30370009+SuperMonster003@users.noreply.github.com>
```

仓库级 `user.useConfigOnly=true` 防止未来提交静默回退到全局身份。本次不提交 `.mailmap`，因为它只能
改变显示，不能移除原始 commit object 中的旧身份字段。

## 历史重写方法与验证

重写工具固定为上游 `git-filter-repo v2.47.0`，源码 commit
`6f79afc8c90c592a3052e6cc53c2ca8907515bca`。操作只在 fresh mirror 中执行；用户工作区和原始 refs
先备份，未直接在脏工作树上过滤。

输入范围为 43 个由本地 heads/tags 可达的唯一 commit 和一个 annotated tag。工作区 stash 不进入发布
mirror。验证结果：

- 43/43 commit 的 author 与 committer name/email 均为目标 noreply 身份；
- annotated tag `v0.3.0-m5` 的 tagger 同样为目标 noreply 身份；
- tree object：43/43 与重写前完全相同；
- author/committer timestamp：43/43 完全相同；
- 父拓扑：43/43 按映射完全相同；
- commit message：42/43 逐字节相同；唯一变化是 Revert message 中被工具自动更新的被回退 commit
  SHA，旧 `760b1c3...` 正确映射为新 `e4fe5e3...`；
- tag target 与 tag message 保持一致；
- `git fsck --full --strict` 通过；
- 旧邮箱从未出现在任何 tracked file 历史内容中；历史中也没有 JKS、keystore、`sign.properties`、
  PEM 或私钥文件。

## 发布 refs

| 发布 ref | 重写前 | noreply 重写后基线 | 说明 |
| --- | --- | --- | --- |
| `main` | `9d91d8813d969068fb726d72e5ce03892aaff545` | `ed69b0d592db808d6854bc5deef4611ebdd74f89` | CI 提交继续从该基线向前 |
| `archive/m10-ecj-spike` | `baf8ec71489ea2287c269142015bf3e2be6cd99f` | `07ed3b95f8437ed46759a42c32f31b1b434e1853` | 唯一未合入 main 的 M10-1 实验 |
| `v0.3.0-m5` tag object | `1b417188408bf59a14cb36884f3f606de3216075` | `d41739daad6769a1a89557a0d678b7dea38e742b` | annotated tagger 已清洗 |
| `v0.3.0-m5` target | `3d0b08e4e85fd2e7e0bf06be990068e591d11e8b` | `6353700c17cb440302fd0d45c4ad8c92de43b48c` | 文件树与时间不变 |

已合入 `main` 的临时 `codex/m10-d8-upgrade`、`codex/m10-kotlin-provider` 不发布；原
`codex/m10-ecj-spike` 由稳定的 archive ref 取代。

## 完整 commit 映射

以下映射用于解释早期设备日志、APK provenance 和文档中不可变的 pre-sanitization SHA。旧 commit
objects 不推送到 GitHub；原始证据文件不伪装为由新 SHA 重新构建。两端 tree 相同只说明源码快照等价，
不改写历史设备观测发生时记录的原始身份。

```text
old                                      new
00d7ab245cbadeb4acedf845f305f2653b82d6ff bc40fcd926646f723044d73d079803b389336993
03addd51d14aca430bf88f153850e04c483b9166 da775b21ea54fd01e3f5ce541fd68e9740d053e8
0666a84ed576e356e03e47b58184d60bf7261b15 04bd5a9046a612a363dc0d21fbbf63d78ebedbb0
074424d8eaf87a62ee84fbabbec9692d7260c5d1 7472175f4c8f765197ac3a06df4fd2e847a7ecf3
17cddf767f28779eb7adae22f1d82f7f32df2858 24fb21117ef9d17ded6ac9e6bf4bc503442766a8
1b7e2e822d9b9d31d4b25cbe8bb144f6eeca0f81 98baf66082e9b9128cff531b462383dd1f14b93b
1bf3e30a65d946fbd1a3f36bc7240d717ed43550 077bb0cbe61f77b40177cbec58e93d8d380bcead
204e3d4817edbe72c8405dcfaf2ba8eeacc773e2 c19b8c7c65b7cef24110c837f030bb2add3a9023
21d46ab9dbf2c6ea549ee343c63d7935889581bc 568986d74ae2bf94bdd4d9a06f6dd0263ac9647e
34e574a79580304e04b0064e4e2094177d6f0334 fc8badf6b7bdc2e9755c3fd56f2ff4c94f921f56
3d0b08e4e85fd2e7e0bf06be990068e591d11e8b 6353700c17cb440302fd0d45c4ad8c92de43b48c
4fc228548763564bc94482cec2f029fb63179bc3 e0a506dd3e30b03b5fa2b8058e89cf75062169af
54cde93d76e04a7496262876c46ec4e9845fd05a a116e15610812bec07082193b3dfc9bf9eb38861
572a5866d544f565bd06d598153b027dc669b029 455a0b1c844ecec789b7361f97ab71edbd788ef0
5bfcd6317f2c97c97f00ebb51516b946d52b709f 4c5c95f3e2f2bfee4e95bff6f1b2aed452115d57
634e28aafc84d43cf08f49af1ad3bc0fa374c2e2 51d31da1275e47681c182d77fa718b222dd8e8ba
7077d650b087f20b7d0b9fb09fecd1c05c64239b 80eb179114098a33927c9a0f8cfb45cab20245ba
760b1c3d387b7edc2948312c7232e0fadd5d5464 e4fe5e342f763400896dc8648fbd0e4a73de09cf
7b8df515bc23935dc62d4f43c561a1563549b2e9 e162b90a1d30fb237e2455266898969b9c3a1ae4
7caffea0b8d593ac3f4bf722d7b12edd78d6b94b 62b61e60cfe108f891c38264c61b1124ffbbb2ac
7d6506ad5ca44133a3ef4dbca9b81c6ad002b6f9 2a0a8dfe5a109cda5529147701ce7b1e9e74ffc7
82a1d11a835f222185916fd8ce301d359e9f6e1d dbd55845e0e1a3d0b12aea0d72ba522505e07524
937b70669c5472395e5c109dab71f0b6a2ed8dba 1494b838ae65cc86197b6e2af5c67578d4fe6cd4
9d91d8813d969068fb726d72e5ce03892aaff545 ed69b0d592db808d6854bc5deef4611ebdd74f89
a14ea42fb5677d7f1ca427488afad37d79d2540a e2c49280b4cff746503f814fc87dc96ef3bf6ab7
ab3d8a5e3ff89331b7df0c87463b83bb59ff16ce efd8149128d709a4944bbce1fa09fe653ce4d065
ae5f1c6f6f7f914c6d4f8b66fa7365155b10bf01 575f3af56acc43737b38e6085c72f305b92c6ef0
b04de9ad0fac20a9b18547b5ee7ed1f4362f8a31 d945e747b909dbe7dfdf826c15ddea9d9ffd6e91
b760ed808689dc5150d8c3e5ad1707c3bce8b4b7 90e990ef83ec84de3057be6c8a696b56017622da
baf8ec71489ea2287c269142015bf3e2be6cd99f 07ed3b95f8437ed46759a42c32f31b1b434e1853
c30ea676e4a364ca827bab0324798d19ef66d39e 9fffe9af45631c23cddfcf22096fedb343703dcc
c98c4c25c09659e6fd49beda33229fd29b325adc 3d10f8cf69c2d72a0a303b1c25fc9c12b289c182
cfb381b3699977d9379f5eebb18129290e229956 6718479a863ab51da85ef11a7a58a461a2fb1ae7
e0ccad4f285e13d30b6f855d72275a6c9f8cfed3 6afc6c8b8294135b2c187e1e8b4d6846535a20aa
e138779c0312a93c77608bfd05c97282a99bd4f6 7891f58c57fe53dbda30f5a251ab7c2724984972
e15c28c893839b091a8fe2d3646d9c27fa57ca4a 03b46e920205cde42903550639da0a6ddf2c8563
e18d113e9df57a2b2696f3ab5c307a729d8fc223 efc9457e5993ccedf189ce14cc152e6d6012c2d5
e711d2527046e3fb3cfdc0b4a8e6ae1156645c87 6d8d26a7b8d427e4c04c10429a50d16dd8a65575
e7ce0b67e5f3fe7b4eefdd1ca740659ef404de90 70cab5ee7d5968ef8c02e54c0f08f4b951a6f7c5
ead53e35ecbc0dbc024bfc85ce5718bb4df22627 6c7370b540de7683554535657883a8b8026af695
f1dcfc61924bdfa202a623279f43d30a1310d5f4 b44a1352b49e6ef367ec44ac1e543a0065dc63c9
f932bf27d28504ab2555ca88e4d7de2ff01bdd13 7c8d1a9159d94ddbb0aba92a7c4ce3b27aca4b25
fba22003dfb9bf755578f11b7829c8b9447345c4 ab41860d3d6ed6a05e19f399ef72f512ae0ef234
```

仓库外的完整 pre-rewrite bundle 为 1,941,071 bytes，SHA-256
`102a42ad5e7769edba1ecc8694d73a91fb5cc9715f13683bf120779bbb1a5767`；它含旧身份，只保存在本机私有
备份目录，禁止上传。原始 `git-filter-repo` commit map 的 SHA-256 为
`e4fd8d98fd64631c177045cd70551e521ad7a280d22c7ff03339131698d917d4`。

## CI 安全与执行边界

GitHub Actions 使用固定 `windows-2025`、Temurin JDK 21 和按完整 commit SHA 钉住的官方 action：

| Action | Release | Commit SHA |
| --- | --- | --- |
| `actions/checkout` | 7.0.1 | `3d3c42e5aac5ba805825da76410c181273ba90b1` |
| `actions/setup-java` | 6.0.0 | `dd06d9cba3e5552c54d9f8ea23572deb30010f7c` |
| `gradle/actions/setup-gradle` | 6.3.0 | `9c971963bec38e04b3d30dcc455b5382be2fdbfb` |

runner 先确认/安装 API 24、26、30、34、36 platform JAR，再在线执行同一组 Gradle tasks 预热 wrapper
与依赖缓存；正式验收随后先强制重跑 repository-local settings-plugin tests，再调用
`scripts/verify.ps1 --no-daemon --rerun-tasks`，全部验证 task 都在 `--offline` 下重新实际执行。PR 只读
缓存，`main` push 才写缓存；workflow token 只有 `contents: read`。

Windows runner 还在 `$RUNNER_TEMP` 下创建独立 JVM 临时根，并在写入后续 step 环境前用 JDK 自身读取
`java.io.tmpdir` 验证路径一致。失败时只打印 JUnit XML 中的 testcase/异常摘要；成功时该诊断 step 跳过。
这既避免 JUnit 临时根中的 Windows 旧式路径别名触发私有目录 fail-closed，也不放宽任何生产目录检查。

首次 clean runner 揭示根 settings 原先通过 `mavenLocal()` 解析未公开的
`org.autojs.build.platform-versions:1.4.1`，属于本机隐式依赖。正式修复从已审计 Kotlin sibling commit
`aa5b55f41e129b2ce09c7deee5fce5098caa0c81` 精确导入 35-file / tree
`67bf5be8be3f5bff2b476004449335da1eb7e87c` 的 `build-logic/platform-versions`，在
`pluginManagement` 中以 included build 解析并删除 `mavenLocal()`。它的 5 suites、55 tests 进入 CI
在线预热和离线强制重跑，clean checkout 不再要求开发机先发布私有 Gradle plugin。

clean Windows runner 随后实际获得创建 symbolic link 的能力，暴露出 host JVM 的 `File.canonicalFile`
不会像 Android/Linux 一样稳定解析目录链接：规范临时根修复后，失败由 20 项降为 5 项，且其中嵌套链接
清理会跟随到测试保护目标。修复没有跳过测试，也没有改变 Android 分支；Android 继续使用既有
`canonicalFile`、`lstat` 与 fd-pinned cleaner，Windows host fallback 额外以 NIO 识别/删除链接本身，
trusted-ancestor 测试则用 `toRealPath` 获得等价规范路径。GitHub runner 上这 5 项随后全部通过。

生产 `sm003.jks` 和任何密码都不进入 GitHub。`scripts/prepare-ci-signing.ps1` 在每个全新 runner
中生成两天有效、随机密码、CI-only 的一次性 JKS；若发现已有 `sign.properties` 或目标 keystore 即
拒绝覆盖。临时 APK 只证明构建完整性，不能用于发布或设备证据；job 结束始终精确删除两个临时文件，
也不上传 APK、测试报告或 lint 报告 artifact。

本地先对 repository-local settings plugin 强制离线重跑：5 suites、55/55 tests，7/7 tasks，43s；
随后以同一临时签名脚本强制重跑根项目离线 gate：Debug/Release 各 44 suites、164/164 tests，0
failure/error/skipped；Debug Lint 0 error、29 warning；Debug APK 构建成功；包含 included-build 任务在内
96/96 tasks 实际执行。settings-plugin 导入后的首轮为 4m12s；Windows link-safe 收口后的最终本地复验为
1m52s（依赖已预热）。两次结束后都确认 CI-only keystore 与 `sign.properties` 不存在。

## 远端验收

2026-08-27 通过 GitHub API 复核
`SuperMonster003/AutoJs6-Plugin-Java-Runtime` visibility 为 `PRIVATE`，default branch 为 `main`。远端仅有：

- `main`（首个绿灯 code head `b8d63b700cb82be0e0a1a196c18a39624a4757d3`）；
- `archive/m10-ecj-spike` → `07ed3b95f8437ed46759a42c32f31b1b434e1853`；
- annotated `v0.3.0-m5` tag object → `d41739daad6769a1a89557a0d678b7dea38e742b`，
  peeled target → `6353700c17cb440302fd0d45c4ad8c92de43b48c`。

clean runner 的失败均作为验收输入保留，没有重跑伪装：

1. [33042083935](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/actions/runs/33042083935)：
   settings plugin 只能从开发机 `mavenLocal()` 解析；以精确 sibling tree 导入和 included build 修复。
2. [33042778574](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/actions/runs/33042778574)：
   插件已通过，但 Windows JVM 临时根 alias 令 20 项私有目录测试 fail-closed；配置并验证 canonical temp。
3. [33043735143](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/actions/runs/33043735143)：
   普通目录测试已通过，剩余 5 项实际 symlink 语义揭示 Windows host fallback 会跟随目标；按上节修复。

首个完整绿灯为
[33044386683](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/actions/runs/33044386683)，
job `98424897886`，code head `b8d63b7`，总耗时 20m54s：

- settings plugin 在线 7/7 tasks（53s），离线强制 7/7 tasks（59s）；
- 根项目在线预热成功（8m58s），随后离线强制 96/96 tasks（7m09s）；
- tracked files 零差异；失败诊断 step 正确跳过；临时签名删除 step 成功；
- Gradle cache、JDK 与 checkout post-action 全部成功，workflow 最终 conclusion 为 `success`；
- 没有上传 APK、测试报告、Lint 报告或任何 release artifact。

因此 Private 可见性不妨碍 M10-4 的 `push + CI 绿灯一次` 验收；未来切换 Public 前仍按“决策”一节复核
仓库内容和历史 Actions 日志。
