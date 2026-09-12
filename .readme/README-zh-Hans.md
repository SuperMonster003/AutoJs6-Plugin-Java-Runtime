<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-java-runtime-ic-launcher" border="0" width="128" />
  </p>

  <p>用于 AutoJs6 的 Java 8 源码 (单文件 / 多文件源码包) 编译与运行插件</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-Java-Runtime?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-Java-Runtime?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/commit/7caffea0b8d593ac3f4bf722d7b12edd78d6b94b"><img alt="Created" src="https://img.shields.io/date/1787396606?color=2e7d32&label=Created"/></a>
    <br>
    <a href="https://developer.android.com/studio/archive"><img alt="Android Studio" src="https://img.shields.io/badge/Android%20Studio-2023.3+-B64FC8"/></a>
    <a href="https://www.jetbrains.com/idea/download/other.html"><img alt="IntelliJ IDEA" src="https://img.shields.io/badge/IntelliJ%20IDEA-2023.3+-EE4677"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-Java-Runtime?color=534BAE&label=License"/></a>
  </p>
</div>

******

### 语言 (Languages)

******

当前 README.md 支持以下语言:

- 简体中文 [zh-Hans] # 当前
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-zh-Hant-HK.md)
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-zh-Hant-TW.md)
- [English [en]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-en.md)
- [Français [fr]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-fr.md)
- [Español [es]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-es.md)
- [日本語 [ja]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-ja.md)
- [한국어 [ko]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-ko.md)
- [Русский [ru]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-ru.md)
- [العربية [ar]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-ar.md)

******

### 简介

******

AutoJs6 Java Runtime 插件让 AutoJs6 可以直接编译并运行 Java 源码 (`.java`), 支持单文件与 2–32 个文件的有界源码包. 插件内置 Eclipse 编译器 ECJ 3.26.0 (语言级别 Java 8) 与 D8 8.13.23 字节码转换器, 编译产物在一次性 worker 进程中执行; 编译器与脚本都不会进入 AutoJs6 进程.

本插件与 [Kotlin Runtime](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime) 互为姊妹插件, 可同时安装并各自服务于 Java / Kotlin 源码; AutoJs6 会按语言分别记忆所选的编译组件.

******

### 功能

******

- 提供 `org.autojs.plugin.JVM_SOURCE` 编译/执行服务与 `org.autojs.plugin.INFO` 插件中心发现服务, 均受签名保护并运行于独立辅助进程.
- 内置 Eclipse 编译器 ECJ 3.26.0 (语言级别 Java 8), 经 D8 8.13.23 转换为 DEX 后执行; 编译产物支持 1–4 个连续 `classesN.dex` (总量 32 MiB 内, API 26 设备保持单 DEX).
- 支持六项逐次授权的宿主能力桥: 控制台实时输出 `console().log/error`, 应用启动 `app().launch`, 可中断休眠 `sleep`, 消息浮窗 `toast`, 以及独立授权的剪贴板读 `clipboard().getText` 与写 `clipboard().setText`.
- 支持宿主脚本入参: `context.args()` 返回执行配置的只读 JSON 参数快照, 无需额外授权; 既有无参脚本无需修改.
- 支持单文件源码与 2–32 个文件的规范源码包; 入口类简名可由调用方显式指定, 默认 `Main`.
- 受控 core library desugaring: 最低 API 24 即可使用 `java.time` 与增强 Stream 的部分方法; 不支持的方法在编译期报错, 不会留到旧设备运行时崩溃.
- 带鉴权的编译缓存: 相同源码重复运行直接命中缓存并跳过编译; 工具链版本变更会使全部旧缓存自动失效.
- 编译错误按 ECJ 源码顺序逐条回传, 带安全的文件名与行列位置; 每次运行结束附带阶段耗时/缓存命中/资源采样的有界观测摘要.
- README 与 CHANGELOG 支持简体中文/繁体中文 (香港/台湾)/英语/法语/西班牙语/日语/韩语/俄语/阿拉伯语十种语言.

******

### 快速上手

******

- **怎么装** — 从 [Releases](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/releases) 下载 APK 并安装, 或按下方「构建」小节本地构建. 注意: 插件必须与 AutoJs6 使用相同证书签名; 宿主 AutoJs6 版本号需不低于 5281.
- **怎么启用** — JVM 源码运行目前是 AutoJs6 的实验性功能: 需在宿主中开启该实验开关, 并为 Java 语言显式选择本插件作为编译组件. 未开启或未选择时, 运行会分别提示稳定错误码 `JVM_SOURCE_EXPERIMENT_DISABLED` 与 `JVM_SOURCE_PROVIDER_NOT_SELECTED`; 若同时启用了多个 Java 编译组件, AutoJs6 也会要求先显式选择其一.
- **怎么跑** — 在 AutoJs6 编辑器中新建 `.java` 文件, 编写一个实现 `AutoJsJvmEntry` 接口的入口类后点击运行 (见下方「使用示例」). 物理文件名可以任意; 入口类简名默认为 `Main`, 脚本调用方也可通过显式入口 API 指定其他 ASCII 简名. 可以省略 `package`, 也可以使用合法的 ASCII 包名.
- **出错了看哪里** — 编译失败时控制台会显示 ECJ 诊断与安全的文件名/行列位置, 多个错误会逐条回传; 运行失败给出稳定错误码与失败阶段 (如 `SOURCE_TOO_LARGE`、`TIMEOUT`、`BUSY`), 运行时异常只显示脱敏类名与源码行. 各样例的预期输出与讲解见 [样例指南](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/samples/README.zh-CN.md), 完整错误码表见 [Java Context API 文档](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/docs/context-api.zh-CN.md).

******

### 使用示例

******

下面是一个可直接运行的最小示例, 演示控制台输出、浮窗、可中断休眠与应用启动四项基础能力:

```java
import org.autojs.plugin.jvmsource.api.AutoJsJvmEntry;
import org.autojs.plugin.jvmsource.api.JvmScriptContext;

public final class Main implements AutoJsJvmEntry {
    @Override
    public Object run(JvmScriptContext context) throws Exception {
        context.console().log("Hello from Java 8");
        context.toast("AutoJs6 Java Runtime");
        context.sleep(500L);
        boolean launched = context.app().launch("org.autojs.autojs6");
        return launched;
    }
}
```

`console().log/error` 在脚本运行期间逐行实时回传; `sleep` 可被停止操作即时中断. 更多示例见 [samples](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/tree/main/samples) 目录: 能力冒烟 `m5-capabilities.java`, 剪贴板 `capability-set-2-clipboard.java`, 脚本入参 `script-args.java`, 多文件源码包 `multi-file-source-package/`, 非默认入口 `arbitrary-entry.java` 等, 均附真机验证记录.

******

### 能力边界

******

为保证安全与行为可预期, 当前版本刻意保持以下边界:

- 单文件 Java 源码上限 4 MiB; 多文件源码包须为含 2–32 个 `.java` 文件的规范归档, 文件路径与 `package` 声明精确对应, 与单文件共用 4 MiB 源码总量上限. 不支持 class 文件、JAR 或 DEX 输入.
- 入口类须为 public 具体类并实现 `AutoJsJvmEntry` (Entry API 4), 且带 public 无参构造器; 编译结果中只允许恰好一个具体实现.
- 语言级别固定 Java 8 (ECJ `-source 8 -target 8`); 注解处理器禁用; 源码必须为严格 UTF-8 且禁止 `\uXXXX` Unicode 转义 (包括注释与字符串).
- lambda 表达式暂不属于可运行 profile (API 24 编译桩不含 `java.lang.invoke`), 请使用匿名类; 不解析任何 Maven 或第三方依赖.
- 会话默认超时 30 秒 (含编译与执行全程), 硬上限 120 秒; 同一时刻仅允许一个活动会话, 第二个会话会得到可重试的 `BUSY`.
- worker 进程一次性使用, 每次执行后即退休; 后台线程不会在 `run` 返回后存活.

******

### 脚本运行库

******

脚本编译与运行可用的 API 面是一份精确锁定的白名单:

#### 可用

- Android framework: 以 API 24 class-only 编译桩提供编译期符号; 运行期行为仍取决于设备系统版本.
- AutoJs6 JVM Entry API 4: `JvmScriptContext` 是唯一受支持的宿主桥 (控制台/应用启动/休眠/浮窗/剪贴板/参数快照/取消视图).
- 受控 core library desugaring: `java.time` (API 26 表面) 与增强 Stream 的三参数 `iterate` 及 `toList`, 在 API 24 设备即可运行.
- 返回值与参数使用 JSON profile: `null`/布尔/数字/字符串/String-key `Map`/`Iterable`/数组; 返回值上限 64 KiB, 嵌套深度 64.

#### 不可用

- 完整桌面 JDK 8 类库与高于 API 24 的 Android API: `Stream.ofNullable`/`takeWhile`/`dropWhile`/`mapMulti*`、完整 `java.nio.file` 等会直接在编译期失败.
- lambda 表达式与方法引用的运行时支持 (`java.lang.invoke` 不在 API 24 桩内); POJO、record 与枚举不能作为返回值.
- 注解处理器、任意 Maven 传递依赖与第三方 JAR.

******

### 安全与隔离

******

插件按「默认拒绝」原则设计, 以下限制始终生效:

- 编译器与 worker 运行于独立进程, 从不进入 AutoJs6 进程; 服务仅接受同签名宿主调用.
- 每项宿主能力按请求逐项授权, 剪贴板读与写为两项独立授权; 未授权调用在派发前即被双侧白名单拒绝.
- 源码、产物、输出、诊断与返回值均有硬上限, 超限即失败而非静默截断; DEX 在执行前经过完整结构校验与只读发布规则.
- 对外错误只含稳定错误码、失败阶段与净化文案; 运行时异常仅回传 `java.*`/`javax.*` 类名或统一的 `UserException`, 异常 message 与堆栈不出进程边界.
- 编译缓存带鉴权校验, 缓存密钥仅在编译器进程内有效; 工具链版本变更会使全部旧缓存自动失效.

******

### 发行历史

******

# v0.8.0

###### 2026/09/12

* `优化` 围绕使用方法, 示例及能力边界整理 README 与发行历史, 并从 JSON 源统一生成十语言文档
* `优化` 构建阶段阻止意外引入原生依赖, 并输出 JSON 校验报告

# v0.8.0-m9

###### 2026/08/27

* `提示` 会话超时维持默认 30 秒 / 硬上限 120 秒, 并发会话维持 1 个 (第二个会话返回可重试的 `BUSY`) - 两项放开评估均判定现阶段不实施
* `提示` ECJ 升级评估结论: 新版 ECJ (3.42/3.46) 无法在 Android 运行, 正式线继续固定 ECJ 3.26.0 与 Java 8
* `新增` 多文件源码包 (Protocol 1.6): 单次请求可提交含 2-32 个 `.java` 文件的规范归档, 文件路径须与 `package` 声明精确对应; 路径穿越, 压缩条目, 符号链接与重复项等全部拒绝
* `新增` 入口类简名端到端放开: 调用方可显式指定 ASCII 入口简名 (默认仍为 `Main`), 非默认入口示例 `arbitrary-entry.java`
* `优化` 明确 Kotlin/JVM 支持由姊妹插件 Kotlin Runtime 独立承担; 两仓共享冻结协议与一致性测试, 不建立运行依赖
* `依赖` 运行时 D8/R8 升级: 8.13.17 → 8.13.23; 编译缓存键随工具链版本自动失效

# v0.7.0-m9

###### 2026/08/26

* `提示` 观测数据不含源码, 参数, 路径或进程身份; Release 构建不导出累计缓存计数
* `新增` 每次执行结束后回传有界观测摘要 (Protocol 1.5): 编译/执行/清理各阶段耗时, 当次缓存命中结果, 冷暖启动与最多六项资源采样, 由 AutoJs6 控制台统一展示

##### 更多发行历史可参阅

* [CHANGELOG-zh-Hans.md](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.changelog/CHANGELOG-zh-Hans.md)

******

### 构建

******

仓库自带冻结的协议 AAR (`protocol/`), 无需检出 AutoJs6 源码即可离线构建. 推荐 JDK 21, Android SDK 需提供 platforms 24 / 26 / 30 / 34 / 36 (低版本平台用于生成受控编译桩). Debug 构建:

```powershell
.\gradlew.bat :app:assembleDebug --offline
```

Release 构建:

```powershell
.\gradlew.bat :app:assembleRelease --offline
```

构建参数集中于 `version.properties`: 当前版本 0.8.0-m9 (build 8), minSdk 24, targetSdk 36. 提交前完整门禁可运行一键脚本 `scripts/verify.ps1` / `scripts/verify.sh` (Debug/Release 单测 + Lint + Debug APK, 全程离线).

Release/debug APK 必须与 AutoJs6 同证书签名才能被宿主接受; 本地签名材料位于被版本控制忽略的 `sign.properties` 与 `app/sm003.jks`.

******

### 资源结构

******

```text
.readme/lang_*.json
.readme/template_readme.md
.changelog/lang_*.json
.changelog/template_changelog.md
.python/generate_markdown.py
app/src/main/res/values*/strings.xml
```

`strings.xml` 提供插件名称与描述的本地化; README 与 CHANGELOG 由 `.python/generate_markdown.py` 根据 JSON 源文件生成. 修改文档请编辑 JSON 源文件而非生成的 Markdown.

******

### 相关链接

******

- AutoJs6 文档: https://docs.autojs6.com
- AutoJs6 项目主页: https://github.com/SuperMonster003/AutoJs6
- 姊妹插件 Kotlin Runtime: https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime
- Eclipse JDT Core (ECJ) 项目: https://github.com/eclipse-jdt/eclipse.jdt.core
- Java Context API 与运行边界 (方法表/上限/错误码): https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/docs/context-api.zh-CN.md
- 示例目录: https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/tree/main/samples
- 项目路线图 (含各里程碑验证记录): https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/ROADMAP.md
- 第三方组件声明: https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/THIRD_PARTY_NOTICES.md


[16 KB page alignment and build verification](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/master/docs/16kb.md)
