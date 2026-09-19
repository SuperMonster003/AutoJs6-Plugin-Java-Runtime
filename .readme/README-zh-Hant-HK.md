<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-java-runtime-ic-launcher" border="0" width="128" />
  </p>

  <p>用於 AutoJs6 的 Java 8 源碼 (單檔案 / 多檔案源碼包) 編譯與運行插件</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-Java-Runtime?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-Java-Runtime?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/commit/7caffea0b8d593ac3f4bf722d7b12edd78d6b94b"><img alt="Created" src="https://img.shields.io/date/1787396606?color=2e7d32&label=Created"/></a>
    <br>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-Java-Runtime?color=534BAE&label=License"/></a>
  </p>
</div>

******

### 語言 (Languages)

******

目前 README.md 支援以下語言:

- [简体中文 [zh-Hans]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-zh-Hans.md)
- 繁體中文 (香港) [zh-Hant-HK] # 目前
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-zh-Hant-TW.md)
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

AutoJs6 Java Runtime 插件讓 AutoJs6 可以直接編譯並運行 Java 源碼 (`.java`), 支援單檔案與 2–32 個檔案的有界源碼包. 插件內置 Eclipse 編譯器 ECJ 3.26.0 (語言級別 Java 8) 與 D8 8.13.23 位元組碼轉換器, 編譯產物在一次性 worker 進程中執行; 編譯器與腳本都不會進入 AutoJs6 進程.

本插件與 [Kotlin Runtime](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime) 互為姊妹插件, 可同時安裝並各自服務於 Java / Kotlin 源碼; AutoJs6 會按語言分別記住所選的編譯組件.

******

### 功能

******

- 提供 `org.autojs.plugin.JVM_SOURCE` 編譯/執行服務與 `org.autojs.plugin.INFO` 插件中心發現服務, 均受簽名保護並運行於獨立輔助進程.
- 內置 Eclipse 編譯器 ECJ 3.26.0 (語言級別 Java 8), 經 D8 8.13.23 轉換為 DEX 後執行; 編譯產物支援 1–4 個連續 `classesN.dex` (總量 32 MiB 內, API 26 裝置保持單 DEX).
- 支援六項逐次授權的宿主能力橋: 控制枱即時輸出 `console().log/error`, 應用啟動 `app().launch`, 可中斷休眠 `sleep`, 訊息浮窗 `toast`, 以及獨立授權的剪貼簿讀 `clipboard().getText` 與寫 `clipboard().setText`.
- 支援宿主腳本入參: `context.args()` 返回執行配置的唯讀 JSON 參數快照, 無需額外授權; 既有無參腳本無需修改.
- 支援單檔案源碼與 2–32 個檔案的規範源碼包; 入口類簡名可由調用方明確指定, 預設 `Main`.
- 受控 core library desugaring: 最低 API 24 即可使用 `java.time` 與增強 Stream 的部分方法; 不支援的方法在編譯期報錯, 不會留到舊裝置運行時崩潰.
- 帶鑒權的編譯緩存: 相同源碼重複運行直接命中緩存並跳過編譯; 工具鏈版本變更會使全部舊緩存自動失效.
- 編譯錯誤按 ECJ 源碼順序逐條回傳, 帶安全的檔案名與行列位置; 每次運行結束附帶階段耗時/緩存命中/資源採樣的有界觀測摘要.
- README 與 CHANGELOG 支援簡體中文/繁體中文 (香港/台灣)/英語/法語/西班牙語/日語/韓語/俄語/阿拉伯語十種語言.

******

### 快速上手

******

- **怎麼裝** — 從 [Releases](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/releases) 下載 APK 並安裝, 或按下方「構建」小節本地構建. 注意: 插件必須與 AutoJs6 使用相同證書簽名; 宿主 AutoJs6 版本號需不低於 5281.
- **怎麼啟用** — JVM 源碼運行目前是 AutoJs6 的實驗性功能: 需在宿主中開啟該實驗開關, 並為 Java 語言明確選擇本插件作為編譯組件. 未開啟或未選擇時, 運行會分別提示穩定錯誤碼 `JVM_SOURCE_EXPERIMENT_DISABLED` 與 `JVM_SOURCE_PROVIDER_NOT_SELECTED`; 若同時啟用了多個 Java 編譯組件, AutoJs6 也會要求先明確選擇其一.
- **怎麼跑** — 在 AutoJs6 編輯器中新建 `.java` 檔案, 編寫一個實現 `AutoJsJvmEntry` 介面的入口類後點擊運行 (見下方「使用示例」). 物理檔案名可以任意; 入口類簡名預設為 `Main`, 腳本調用方也可透過明確入口 API 指定其他 ASCII 簡名. 可以省略 `package`, 也可以使用合法的 ASCII 包名.
- **出錯了看哪裏** — 編譯失敗時控制枱會顯示 ECJ 診斷與安全的檔案名/行列位置, 多個錯誤會逐條回傳; 運行失敗給出穩定錯誤碼與失敗階段 (如 `SOURCE_TOO_LARGE`、`TIMEOUT`、`BUSY`), 運行時異常只顯示脫敏類名與源碼行. 各示例的預期輸出與講解見 [示例指南](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/samples/README.zh-CN.md), 完整錯誤碼表見 [Java Context API 文檔](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/docs/context-api.zh-CN.md).

******

### 使用示例

******

下面是一個可直接運行的最小示例, 演示控制枱輸出、浮窗、可中斷休眠與應用啟動四項基礎能力:

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

`console().log/error` 在腳本運行期間逐行即時回傳; `sleep` 可被停止操作即時中斷. 更多示例見 [samples](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/tree/main/samples) 目錄: 能力冒煙 `m5-capabilities.java`, 剪貼簿 `capability-set-2-clipboard.java`, 腳本入參 `script-args.java`, 多檔案源碼包 `multi-file-source-package/`, 非預設入口 `arbitrary-entry.java` 等, 均附真機驗證記錄.

******

### 能力邊界

******

為保證安全與行為可預期, 目前版本刻意保持以下邊界:

- 單檔案 Java 源碼上限 4 MiB; 多檔案源碼包須為含 2–32 個 `.java` 檔案的規範歸檔, 檔案路徑與 `package` 聲明精確對應, 與單檔案共用 4 MiB 源碼總量上限. 不支援 class 檔案、JAR 或 DEX 輸入.
- 入口類須為 public 具體類並實現 `AutoJsJvmEntry` (Entry API 4), 且帶 public 無參構造器; 編譯結果中只允許恰好一個具體實現.
- 語言級別固定 Java 8 (ECJ `-source 8 -target 8`); 註解處理器禁用; 源碼必須為嚴格 UTF-8 且禁止 `\uXXXX` Unicode 轉義 (包括註釋與字串).
- lambda 表達式暫不屬於可運行 profile (API 24 編譯樁不含 `java.lang.invoke`), 請使用匿名類; 不解析任何 Maven 或第三方依賴.
- 會話預設超時 30 秒 (含編譯與執行全程), 硬上限 120 秒; 同一時刻只允許一個活動會話, 第二個會話會得到可重試的 `BUSY`.
- worker 進程一次性使用, 每次執行後即退休; 後台線程不會在 `run` 返回後存活.

******

### 腳本運行庫

******

腳本編譯與運行可用的 API 面是一份精確鎖定的白名單:

#### 可用

- Android framework: 以 API 24 class-only 編譯樁提供編譯期符號; 運行期行為仍取決於裝置系統版本.
- AutoJs6 JVM Entry API 4: `JvmScriptContext` 是唯一受支援的宿主橋 (控制枱/應用啟動/休眠/浮窗/剪貼簿/參數快照/取消視圖).
- 受控 core library desugaring: `java.time` (API 26 表面) 與增強 Stream 的三參數 `iterate` 及 `toList`, 在 API 24 裝置即可運行.
- 返回值與參數使用 JSON profile: `null`/布林/數字/字串/String-key `Map`/`Iterable`/陣列; 返回值上限 64 KiB, 嵌套深度 64.

#### 不可用

- 完整桌面 JDK 8 類庫與高於 API 24 的 Android API: `Stream.ofNullable`/`takeWhile`/`dropWhile`/`mapMulti*`、完整 `java.nio.file` 等會直接在編譯期失敗.
- lambda 表達式與方法引用的運行時支援 (`java.lang.invoke` 不在 API 24 樁內); POJO、record 與枚舉不能作為返回值.
- 註解處理器、任意 Maven 傳遞依賴與第三方 JAR.

******

### 安全與隔離

******

插件按「預設拒絕」原則設計, 以下限制始終生效:

- 編譯器與 worker 運行於獨立進程, 從不進入 AutoJs6 進程; 服務只接受同簽名宿主調用.
- 每項宿主能力按請求逐項授權, 剪貼簿讀與寫為兩項獨立授權; 未授權調用在派發前即被雙側白名單拒絕.
- 源碼、產物、輸出、診斷與返回值均有硬上限, 超限即失敗而非靜默截斷; DEX 在執行前經過完整結構校驗與唯讀發佈規則.
- 對外錯誤只含穩定錯誤碼、失敗階段與淨化文案; 運行時異常只回傳 `java.*`/`javax.*` 類名或統一的 `UserException`, 異常 message 與堆疊不出進程邊界.
- 編譯緩存帶鑒權校驗, 緩存密鑰只在編譯器進程內有效; 工具鏈版本變更會使全部舊緩存自動失效.

******

### 發行歷史

******

# v0.8.3

###### 2026/09/19

* `修復` 資源清理與 worker 結束並行時不再重複完成同一工作階段, 避免執行觀測尚未就緒便傳送結束回呼而導致宿主間歇性逾時
* `修復` AGP 9.1 構建時的 SDK XML v4 解析警告及 JVM 單元測試組裝任務誤觸發 APK 原生程式庫對齊檢查的問題 (共用構建外掛 1.8.3)

# v0.8.2

###### 2026/09/15

* `優化` 將 compileSdk 與 targetSdk 提升到 37 (Android 17), 插件行為不受新目標版本影響

# v0.8.1

###### 2026/09/13

* `修復` 外掛中心可透過受保護入口啟用新安裝的外掛, 顯示的中繼資料與實際安裝套件一致
* `優化` 宿主啟用, 外掛中繼資料, 多語言文件與簽章發佈彙整遵循統一外掛規範

##### 更多發行歷史可參閱

* [CHANGELOG-zh-Hant-HK.md](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/app/src/main/assets/doc/CHANGELOG-zh-Hant-HK.md)

******

### 構建

******

倉庫自帶凍結的協議 AAR (`protocol/`), 無需檢出 AutoJs6 源碼即可離線構建. 建議 JDK 21, Android SDK 需提供 platforms 24 / 26 / 30 / 34 / 36 (低版本平台用於生成受控編譯樁). Debug 構建:

```powershell
.\gradlew.bat :app:assembleDebug --offline
```

Release 構建:

```powershell
.\gradlew.bat :app:assembleRelease --offline
```

構建參數集中於 `version.properties`: 目前版本 0.8.2-m9 (build 56), minSdk 24, targetSdk 37. 提交前完整門禁可運行一鍵腳本 `scripts/verify.ps1` / `scripts/verify.sh` (Debug/Release 單測 + Lint + Debug APK, 全程離線).

Release/debug APK 必須與 AutoJs6 同證書簽名才能被宿主接受; 本地簽名材料位於被版本控制忽略的 `sign.properties` 與 `app/sm003.jks`.

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

`strings.xml` 提供插件名稱與描述的本地化; README 與 CHANGELOG 由 `.python/generate_markdown.py` 根據 JSON 源檔案生成. 修改文檔請編輯 JSON 源檔案而非生成的 Markdown.

******

### 相關連結

******

- AutoJs6 文檔: https://docs.autojs6.com
- AutoJs6 項目主頁: https://github.com/SuperMonster003/AutoJs6
- 姊妹插件 Kotlin Runtime: https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime
- Eclipse JDT Core (ECJ) 項目: https://github.com/eclipse-jdt/eclipse.jdt.core
- Java Context API 與運行邊界 (方法表/上限/錯誤碼): https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/docs/context-api.zh-CN.md
- 示例目錄: https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/tree/main/samples
- 項目路線圖 (含各里程碑驗證記錄): https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/ROADMAP.md
- 第三方組件聲明: https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/THIRD_PARTY_NOTICES.md


[16 KB page alignment and build verification](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/master/docs/16kb.md)
