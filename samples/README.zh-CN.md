# Java 样例与预期结果

样例文件名可以任意，但每个成功编译的文件都声明固定入口简单名 <code>Main</code>。在 AutoJs6
编辑器中显式选择 JVM Source/Java 运行方式；不要把故意失败的样例当作普通 Java 工程源码一起编译。

| 样例 | 目的 | 预期结果 |
|---|---|---|
| [m5-capabilities.java](m5-capabilities.java) | M5 完整 capability 集 | stdout 出现 <code>M5 Java log: 你好</code>，显示 toast，约 500 ms 后 stderr 出现耗时行，返回 JSON <code>5</code>。 |
| [capability-set-2-clipboard.java](capability-set-2-clipboard.java) | M9 Capability Set 2 剪贴板读写 | 读取原文本、写入并回读 <code>M9 clipboard 你好</code>，随后恢复原文本；需要分别授予 <code>CLIPBOARD_READ</code> 与 <code>CLIPBOARD_WRITE</code>，stdout 出现 <code>clipboard round-trip: M9 clipboard 你好; restored=true</code>，返回包含 <code>before</code>、<code>written</code>、<code>restored</code> 的对象。Android 10+ 执行期间 AutoJs6 必须位于前台。 |
| [core-library-desugaring.java](core-library-desugaring.java) | M8 受控 core library desugaring：<code>java.time</code> 与增强三参数 <code>Stream.iterate(...).toList()</code> | API 24+ 成功，返回字符串 <code>2024-03-01:CORE</code>。 |
| [cancellation-sleep.java](cancellation-sleep.java) | 取消中断 <code>sleep</code> | 看到 <code>cancellation sample: sleeping</code> 后停止脚本；会话以 <code>REQUESTED</code> 取消，绝不能输出 <code>unexpected completion</code>。不手动停止时会由默认 30 秒超时取消。 |
| [return-values.java](return-values.java) | 支持类型、嵌套 Map/Iterable/数组 | 成功，返回下方的确定性 JSON。 |
| [compile-error.java](compile-error.java) | 编译诊断的文件名、行列和原文可用性 | 失败码 <code>COMPILATION_FAILED</code>，诊断码 <code>ECJ_ERROR</code>，定位 <code>Main.java:7</code>，消息包含 <code>missingSymbol</code> 且不含 provider 私有绝对路径。 |
| [limit-result-json.java](limit-result-json.java) | 64 KiB 返回值边界 | 入口成功运行，但 JSON 字符串连同引号超过 65,536 bytes，最终以 <code>EXECUTION_FAILED</code> 拒绝。 |

<code>return-values.java</code> 的预期 JSON：

~~~json
{"nullValue":null,"boolean":true,"integers":[1,2,3,4,12345678901234567890],"decimals":[1.25,2.5,3.75],"text":"hello","character":"中","primitiveArray":[5,6],"objectArray":["x",false],"nested":{"items":[7,8]}}
~~~

## 自动设备回归

<code>JavaSampleLibraryInstrumentedTest</code> 将这些文件作为测试 APK asset，并在 Android
设备上运行真实 provider 内部流水线。剪贴板样例还会经过 Worker Context 的规范 wire 调用，并验证读写
权限各自默认拒绝。它不伪装成宿主 Binder 端到端证据；API 36 的独立 AutoJs6 production
Activity→Binder→worker 记录见
[M9-1 剪贴板能力实现与证据](../docs/capability-set-2-m9-1.zh-CN.md)，宿主规范测试的代码所有权仍在
AutoJs6 宿主仓。

~~~powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --offline
adb -s <serial> install -r app\build\outputs\apk\debug\app-debug.apk
adb -s <serial> install -r app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
adb -s <serial> shell am instrument -w -r -e class org.autojs.plugin.jvmsource.java.JavaSampleLibraryInstrumentedTest io.github.supermonster003.autojs6.plugin.java.runtime.test/androidx.test.runner.AndroidJUnitRunner
~~~

设备证据执行后记录在本表：

| 日期 | 设备/API/ABI | Provider APK | 结果 |
|---|---|---|---|
| 2026-08-25（M9-1） | Sony G8441 真机，Android 9 / API 28 / arm64-v8a，fingerprint <code>Sony/G8441/G8441:9/47.2.A.4.45/3677320370:user/release-keys</code> | SHA-256 <code>B5D29A9D87F5B715748BC452F3DDA10951349FEF26B2B4180CC9EB9FAF2DB72B</code>；AndroidTest SHA-256 <code>A2764EC265AC8FEA22D1AA7D3E6A9CCEAFA2CB6C1DD61DA0340A20D29C0C6B2C</code> | 8/8 通过，8.062 s；新增剪贴板实际 ART 往返/恢复，以及读写独立默认拒绝且零 dispatch。测试后仅卸载本轮新增的 provider/test，既有 AutoJs6 未改动。 |
| 2026-08-25（M9-1） | Android Emulator，Android 7.0 / API 24 / x86，fingerprint <code>google/sdk_google_phone_x86/generic_x86:7.0/NYC/6696031:userdebug/dev-keys</code> | SHA-256 <code>B5D29A9D87F5B715748BC452F3DDA10951349FEF26B2B4180CC9EB9FAF2DB72B</code>；AndroidTest SHA-256 <code>A2764EC265AC8FEA22D1AA7D3E6A9CCEAFA2CB6C1DD61DA0340A20D29C0C6B2C</code> | 8/8 通过，4.836 s；同一最终 APK 覆盖私有 <code>DexClassLoader</code> 路径。 |
| 2026-08-25（M8-3） | Android Emulator，Android 7.0 / API 24 / x86，fingerprint <code>google/sdk_google_phone_x86/generic_x86:7.0/NYC/6696031:userdebug/dev-keys</code> | SHA-256 <code>7C89DC7D50682FEE2A41F46E96D924E80747F8D59EC51CAE960C615A853E5613</code> | 6/6 通过，3.160 s；新增 <code>java.time</code> + 三参数 <code>Stream.iterate(...).toList()</code> 正向 ART 执行，以及不支持的 <code>Stream.ofNullable</code> 编译期拒绝。 |
| 2026-08-25 | Android Emulator，Android 7.0 / API 24 / x86，fingerprint <code>google/sdk_google_phone_x86/generic_x86:7.0/NYC/6696031:userdebug/dev-keys</code> | SHA-256 <code>4315ECEE334088213D24EB8AE7842CB2B880CB37FE18CF01A507A8435AEFCF87</code> | 4/4 通过，2.177 s；覆盖私有文件 <code>DexClassLoader</code> 分支。 |
| 2026-08-25 | Sony G8441 真机，Android 9 / API 28 / arm64-v8a，fingerprint <code>Sony/G8441/G8441:9/47.2.A.4.45/3677320370:user/release-keys</code> | SHA-256 <code>4315ECEE334088213D24EB8AE7842CB2B880CB37FE18CF01A507A8435AEFCF87</code> | 4/4 通过，4.669 s；覆盖 <code>InMemoryDexClassLoader</code> 分支。 |
| 2026-08-25 | Xiaomi 23046RP50C 真机，Android 15 / API 35 / arm64-v8a，fingerprint <code>Xiaomi/liuqin/liuqin:15/AQ3A.241006.001/OS2.0.9.0.VMYCNXM:user/release-keys</code> | SHA-256 <code>4315ECEE334088213D24EB8AE7842CB2B880CB37FE18CF01A507A8435AEFCF87</code> | 4/4 通过，1.734 s；覆盖 <code>InMemoryDexClassLoader</code> 分支。 |
| 2026-08-25 | Android Emulator，Android 17 / API 37 / x86_64，fingerprint <code>google/sdk_gphone16k_x86_64/emu64xa16k:17/CE2A.260420.019/15611780:userdebug/dev-keys</code> | SHA-256 <code>4315ECEE334088213D24EB8AE7842CB2B880CB37FE18CF01A507A8435AEFCF87</code> | 4/4 通过，1.833 s；覆盖 <code>InMemoryDexClassLoader</code> 分支。 |

原 M6-7 四次执行使用同一个测试 APK，SHA-256 为
<code>3CBB45A6D4C415B07E651F691736E1BA55A2E08AF4391C77708979738D4EE2A2</code>。M8-3 的 API 24
复测使用测试 APK SHA-256
<code>03D1CED11199AD24B749213022C14573AC2EEB1E3C015FCD75C446BF0DB2F700</code>。每轮证据收集后均清理
临时安装的 provider 与 test package；没有覆盖预先存在的 provider。
