<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-java-runtime-ic-launcher" border="0" width="128" />
  </p>

  <p>用於 AutoJs6 的 Java 8 原始碼 (單檔案 / 多檔案原始碼包) 編譯與執行插件</p>

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

### 語言 (Languages)

******

目前 README.md 支援以下語言:

- [简体中文 [zh-Hans]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-zh-Hans.md)
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-zh-Hant-HK.md)
- 繁體中文 (台灣) [zh-Hant-TW] # 目前
- [English [en]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-en.md)
- [Français [fr]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-fr.md)
- [Español [es]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-es.md)
- [日本語 [ja]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-ja.md)
- [한국어 [ko]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-ko.md)
- [Русский [ru]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-ru.md)
- [العربية [ar]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-ar.md)

******

### 簡介

******

AutoJs6 Java Runtime 插件讓 AutoJs6 可以直接編譯並執行 Java 原始碼 (`.java`), 支援單檔案與 2–32 個檔案的有界原始碼包. 插件內建 Eclipse 編譯器 ECJ 3.26.0 (語言等級 Java 8) 與 D8 8.13.23 位元組碼轉換器, 編譯產物在一次性 worker 程序中執行; 編譯器與指令碼都不會進入 AutoJs6 程序.

本插件與 [Kotlin Runtime](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime) 互為姊妹插件, 可同時安裝並各自服務於 Java / Kotlin 原始碼; AutoJs6 會按語言分別記住所選的編譯元件.

******

### 功能

******

- 提供 `org.autojs.plugin.JVM_SOURCE` 編譯/執行服務與 `org.autojs.plugin.INFO` 插件中心發現服務, 均受簽章保護並執行於獨立輔助程序.
- 內建 Eclipse 編譯器 ECJ 3.26.0 (語言等級 Java 8), 經 D8 8.13.23 轉換為 DEX 後執行; 編譯產物支援 1–4 個連續 `classesN.dex` (總量 32 MiB 內, API 26 裝置保持單 DEX).
- 支援六項逐次授權的宿主能力橋: 主控台即時輸出 `console().log/error`, 應用啟動 `app().launch`, 可中斷休眠 `sleep`, 訊息浮窗 `toast`, 以及獨立授權的剪貼簿讀 `clipboard().getText` 與寫 `clipboard().setText`.
- 支援宿主指令碼參數: `context.args()` 回傳執行設定的唯讀 JSON 參數快照, 無需額外授權; 既有無參數指令碼無需修改.
- 支援單檔案原始碼與 2–32 個檔案的規範原始碼包; 進入點類別簡名可由呼叫方明確指定, 預設 `Main`.
- 受控 core library desugaring: 最低 API 24 即可使用 `java.time` 與增強 Stream 的部分方法; 不支援的方法在編譯期報錯, 不會留到舊裝置執行時崩潰.
- 帶鑑權的編譯快取: 相同原始碼重複執行直接命中快取並跳過編譯; 工具鏈版本變更會使全部舊快取自動失效.
- 編譯錯誤按 ECJ 原始碼順序逐條回傳, 帶安全的檔案名與行列位置; 每次執行結束附帶階段耗時/快取命中/資源取樣的有界觀測摘要.
- README 與 CHANGELOG 支援簡體中文/繁體中文 (香港/台灣)/英語/法語/西班牙語/日語/韓語/俄語/阿拉伯語十種語言.

******

### 快速上手

******

- **怎麼裝** — 從 [Releases](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/releases) 下載 APK 並安裝, 或按下方「建置」小節本地建置. 注意: 插件必須與 AutoJs6 使用相同憑證簽章; 宿主 AutoJs6 版本號需不低於 5281.
- **怎麼啟用** — JVM 原始碼執行目前是 AutoJs6 的實驗性功能: 需在宿主中開啟該實驗開關, 並為 Java 語言明確選擇本插件作為編譯元件. 未開啟或未選擇時, 執行會分別提示穩定錯誤碼 `JVM_SOURCE_EXPERIMENT_DISABLED` 與 `JVM_SOURCE_PROVIDER_NOT_SELECTED`; 若同時啟用了多個 Java 編譯元件, AutoJs6 也會要求先明確選擇其一.
- **怎麼跑** — 在 AutoJs6 編輯器中新建 `.java` 檔案, 編寫一個實作 `AutoJsJvmEntry` 介面的進入點類別後點擊執行 (見下方「使用範例」). 實體檔案名可以任意; 進入點類別簡名預設為 `Main`, 指令碼呼叫方也可透過明確進入點 API 指定其他 ASCII 簡名. 可以省略 `package`, 也可以使用合法的 ASCII 套件名.
- **出錯了看哪裡** — 編譯失敗時主控台會顯示 ECJ 診斷與安全的檔案名/行列位置, 多個錯誤會逐條回傳; 執行失敗給出穩定錯誤碼與失敗階段 (如 `SOURCE_TOO_LARGE`、`TIMEOUT`、`BUSY`), 執行時例外只顯示脫敏類別名與原始碼行. 各範例的預期輸出與講解見 [範例指南](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/samples/README.zh-CN.md), 完整錯誤碼表見 [Java Context API 文件](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/docs/context-api.zh-CN.md).

******

### 使用範例

******

下面是一個可直接執行的最小範例, 演示主控台輸出、浮窗、可中斷休眠與應用啟動四項基礎能力:

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

`console().log/error` 在指令碼執行期間逐行即時回傳; `sleep` 可被停止操作即時中斷. 更多範例見 [samples](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/tree/main/samples) 目錄: 能力冒煙 `m5-capabilities.java`, 剪貼簿 `capability-set-2-clipboard.java`, 指令碼參數 `script-args.java`, 多檔案原始碼包 `multi-file-source-package/`, 非預設進入點 `arbitrary-entry.java` 等, 均附真機驗證記錄.

******

### 能力邊界

******

為保證安全與行為可預期, 目前版本刻意保持以下邊界:

- 單檔案 Java 原始碼上限 4 MiB; 多檔案原始碼包須為含 2–32 個 `.java` 檔案的規範歸檔, 檔案路徑與 `package` 宣告精確對應, 與單檔案共用 4 MiB 原始碼總量上限. 不支援 class 檔案、JAR 或 DEX 輸入.
- 進入點類別須為 public 具體類別並實作 `AutoJsJvmEntry` (Entry API 4), 且帶 public 無參數建構子; 編譯結果中只允許恰好一個具體實作.
- 語言等級固定 Java 8 (ECJ `-source 8 -target 8`); 註解處理器停用; 原始碼必須為嚴格 UTF-8 且禁止 `\uXXXX` Unicode 跳脫 (包括註解與字串).
- lambda 運算式暫不屬於可執行 profile (API 24 編譯樁不含 `java.lang.invoke`), 請使用匿名類別; 不解析任何 Maven 或第三方相依.
- 工作階段預設逾時 30 秒 (含編譯與執行全程), 硬上限 120 秒; 同一時刻只允許一個作用中工作階段, 第二個工作階段會得到可重試的 `BUSY`.
- worker 程序一次性使用, 每次執行後即退役; 背景執行緒不會在 `run` 回傳後存活.

******

### 指令碼執行函式庫

******

指令碼編譯與執行可用的 API 面是一份精確鎖定的白名單:

#### 可用

- Android framework: 以 API 24 class-only 編譯樁提供編譯期符號; 執行期行為仍取決於裝置系統版本.
- AutoJs6 JVM Entry API 4: `JvmScriptContext` 是唯一受支援的宿主橋 (主控台/應用啟動/休眠/浮窗/剪貼簿/參數快照/取消檢視).
- 受控 core library desugaring: `java.time` (API 26 表面) 與增強 Stream 的三參數 `iterate` 及 `toList`, 在 API 24 裝置即可執行.
- 回傳值與參數使用 JSON profile: `null`/布林/數字/字串/String-key `Map`/`Iterable`/陣列; 回傳值上限 64 KiB, 巢狀深度 64.

#### 不可用

- 完整桌面 JDK 8 類別庫與高於 API 24 的 Android API: `Stream.ofNullable`/`takeWhile`/`dropWhile`/`mapMulti*`、完整 `java.nio.file` 等會直接在編譯期失敗.
- lambda 運算式與方法參考的執行時支援 (`java.lang.invoke` 不在 API 24 樁內); POJO、record 與列舉不能作為回傳值.
- 註解處理器、任意 Maven 遞移相依與第三方 JAR.

******

### 安全與隔離

******

插件按「預設拒絕」原則設計, 以下限制始終生效:

- 編譯器與 worker 執行於獨立程序, 從不進入 AutoJs6 程序; 服務只接受同簽章宿主呼叫.
- 每項宿主能力按請求逐項授權, 剪貼簿讀與寫為兩項獨立授權; 未授權呼叫在派發前即被雙側白名單拒絕.
- 原始碼、產物、輸出、診斷與回傳值均有硬上限, 超限即失敗而非靜默截斷; DEX 在執行前經過完整結構校驗與唯讀發佈規則.
- 對外錯誤只含穩定錯誤碼、失敗階段與淨化文案; 執行時例外只回傳 `java.*`/`javax.*` 類別名或統一的 `UserException`, 例外 message 與堆疊不出程序邊界.
- 編譯快取帶鑑權校驗, 快取金鑰只在編譯器程序內有效; 工具鏈版本變更會使全部舊快取自動失效.

******

### 發行歷史

******

# v0.8.0

###### 2026/09/12

* `優化` 圍繞使用方法, 範例及能力邊界整理 README 與發行歷史, 並從 JSON 來源統一產生十語言文件
* `優化` 建置階段阻止意外引入原生相依套件, 並輸出 JSON 校驗報告

# v0.8.0-m9

###### 2026/08/27

* `提示` 工作階段逾時維持預設 30 秒 / 硬上限 120 秒, 並行工作階段維持 1 個 (第二個工作階段回傳可重試的 `BUSY`) — 兩項放開評估均判定現階段不實施
* `提示` ECJ 升級評估結論: 新版 ECJ (3.42/3.46) 無法在 Android 執行, 正式線繼續固定 ECJ 3.26.0 與 Java 8
* `新增` 多檔案原始碼包 (Protocol 1.6): 單次請求可提交含 2–32 個 `.java` 檔案的規範歸檔, 檔案路徑須與 `package` 宣告精確對應; 路徑穿越, 壓縮項目, 符號連結與重複項等全部拒絕
* `新增` 進入點類別簡名端到端放開: 呼叫方可明確指定 ASCII 進入點簡名 (預設仍為 `Main`), 非預設進入點範例 `arbitrary-entry.java`
* `優化` 明確 Kotlin/JVM 支援由姊妹插件 Kotlin Runtime 獨立承擔; 兩倉共享凍結協定與一致性測試, 不建立執行相依
* `相依` 執行時 D8/R8 升級: 8.13.17 → 8.13.23; 編譯快取鍵隨工具鏈版本自動失效

# v0.7.0-m9

###### 2026/08/26

* `提示` 觀測資料不含原始碼, 參數, 路徑或程序身分; Release 建置不匯出累計快取計數
* `新增` 每次執行結束後回傳有界觀測摘要 (Protocol 1.5): 編譯/執行/清理各階段耗時, 當次快取命中結果, 冷暖啟動與最多六項資源取樣, 由 AutoJs6 主控台統一顯示

##### 更多發行歷史可參閱

* [CHANGELOG-zh-Hant-TW.md](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.changelog/CHANGELOG-zh-Hant-TW.md)

******

### 建置

******

儲存庫自帶凍結的協定 AAR (`protocol/`), 無需檢出 AutoJs6 原始碼即可離線建置. 建議 JDK 21, Android SDK 需提供 platforms 24 / 26 / 30 / 34 / 36 (低版本平台用於產生受控編譯樁). Debug 建置:

```powershell
.\gradlew.bat :app:assembleDebug --offline
```

Release 建置:

```powershell
.\gradlew.bat :app:assembleRelease --offline
```

建置參數集中於 `version.properties`: 目前版本 0.8.0-m9 (build 8), minSdk 24, targetSdk 36. 提交前完整門禁可執行一鍵指令碼 `scripts/verify.ps1` / `scripts/verify.sh` (Debug/Release 單元測試 + Lint + Debug APK, 全程離線).

Release/debug APK 必須與 AutoJs6 同憑證簽章才能被宿主接受; 本地簽章材料位於被版本控制忽略的 `sign.properties` 與 `app/sm003.jks`.

******

### 資源結構

******

```text
.readme/lang_*.json
.readme/template_readme.md
.changelog/lang_*.json
.changelog/template_changelog.md
.python/generate_markdown.py
app/src/main/res/values*/strings.xml
```

`strings.xml` 提供插件名稱與描述的本地化; README 與 CHANGELOG 由 `.python/generate_markdown.py` 根據 JSON 來源檔案產生. 修改文件請編輯 JSON 來源檔案而非產生的 Markdown.

******

### 相關連結

******

- AutoJs6 文件: https://docs.autojs6.com
- AutoJs6 專案主頁: https://github.com/SuperMonster003/AutoJs6
- 姊妹插件 Kotlin Runtime: https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime
- Eclipse JDT Core (ECJ) 專案: https://github.com/eclipse-jdt/eclipse.jdt.core
- Java Context API 與執行邊界 (方法表/上限/錯誤碼): https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/docs/context-api.zh-CN.md
- 範例目錄: https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/tree/main/samples
- 專案路線圖 (含各里程碑驗證記錄): https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/ROADMAP.md
- 第三方元件聲明: https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/THIRD_PARTY_NOTICES.md


[16 KB page alignment and build verification](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/master/docs/16kb.md)
