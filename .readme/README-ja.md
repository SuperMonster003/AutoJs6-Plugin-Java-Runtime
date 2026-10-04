<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <picture>
      <source media="(prefers-color-scheme: dark)" srcset="https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/app/src/main/res/mipmap-night/ic_launcher.png?raw=true" />
      <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-java-runtime-ic-launcher" border="0" width="128" />
    </picture>
  </p>

  <p>AutoJs6 向け Java 8 ソース (単一ファイル / 複数ファイルソースパッケージ) のコンパイル/実行プラグイン</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-Java-Runtime?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-Java-Runtime?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/commit/7caffea0b8d593ac3f4bf722d7b12edd78d6b94b"><img alt="Created" src="https://img.shields.io/date/1787396606?color=2e7d32&label=Created"/></a>
    <br>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-Java-Runtime?color=534BAE&label=License"/></a>
  </p>
</div>

******

### 言語 (Languages)

******

現在の README.md は以下の言語に対応しています:

- [简体中文 [zh-Hans]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-zh-Hans.md)
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-zh-Hant-HK.md)
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-zh-Hant-TW.md)
- [English [en]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-en.md)
- [Français [fr]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-fr.md)
- [Español [es]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-es.md)
- 日本語 [ja] # 現在
- [한국어 [ko]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-ko.md)
- [Русский [ru]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-ru.md)
- [العربية [ar]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-ar.md)

******

### はじめに

******

AutoJs6 Java Runtime プラグインを使うと, AutoJs6 で Java ソースコード (`.java`) を直接コンパイルして実行できます. 単一ファイルと 2–32 ファイルの有界ソースパッケージに対応します. プラグインは Eclipse コンパイラ ECJ 3.26.0 (言語レベル Java 8) と D8 8.13.23 バイトコード変換器を内蔵し, コンパイル成果物は使い捨ての worker プロセスで実行されます. コンパイラもスクリプトも AutoJs6 のプロセス内では決して動きません.

本プラグインは [Kotlin Runtime](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime) と姉妹プラグインの関係にあり, 同時にインストールしてそれぞれ Java / Kotlin ソースを担当できます. AutoJs6 は言語ごとに選択したコンパイラコンポーネントを記憶します.

******

### 機能

******

- `org.autojs.plugin.JVM_SOURCE` コンパイル/実行サービスと `org.autojs.plugin.INFO` プラグインセンター発見サービスを提供. いずれも署名保護され, 独立した補助プロセスで動作.
- Eclipse コンパイラ ECJ 3.26.0 (言語レベル Java 8) を内蔵. 出力は D8 8.13.23 で DEX に変換して実行し, 連続する 1–4 個の `classesN.dex` に対応 (合計 32 MiB 以内, API 26 端末は単一 DEX のまま).
- 個別に認可される 6 つのホスト能力ブリッジに対応: コンソールのリアルタイム出力 `console().log/error`, アプリ起動 `app().launch`, 中断可能な `sleep`, `toast` メッセージ, さらに独立認可のクリップボード読み取り `clipboard().getText` と書き込み `clipboard().setText`.
- ホストのスクリプト引数に対応: `context.args()` は実行設定引数の読み取り専用 JSON スナップショットを追加認可なしで返します. 既存の引数なしスクリプトは変更不要.
- 単一ソースファイルと 2–32 ファイルの正規ソースパッケージを受け付け. エントリクラスの単純名は呼び出し側が明示的に選択でき, 既定は `Main`.
- 制御された core library desugaring: `java.time` と一部の拡張 Stream メソッドが API 24 から利用可能. 未対応メソッドはコンパイル時に失敗し, 旧端末で実行時クラッシュを起こしません.
- 認証付きコンパイルキャッシュ: 同一ソースの再実行はキャッシュにヒットしてコンパイルをスキップ. ツールチェーンのバージョン変更で旧キャッシュは全て自動無効化.
- コンパイルエラーは ECJ のソース順に一件ずつ転送され, 安全なファイル名と行/列位置付き. 毎回の実行終了時にフェーズ別所要時間, キャッシュ結果, リソースサンプルの有界観測サマリーが付きます.
- README と CHANGELOG は簡体字中国語/繁体字中国語 (香港/台湾)/英語/フランス語/スペイン語/日本語/韓国語/ロシア語/アラビア語の 10 言語に対応.

******

### クイックスタート

******

- **インストール** — [Releases](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/releases) から APK をダウンロードしてインストールするか, 下記「ビルド」節に従ってローカルビルドします. 注意: プラグインは AutoJs6 と同一証明書で署名されている必要があり, ホスト AutoJs6 のバージョンコードは 5281 以上が必要です.
- **有効化** — JVM ソース実行は現在 AutoJs6 の実験的機能です: ホストで実験スイッチを有効にし, Java 言語のコンパイラコンポーネントとして本プラグインを明示的に選択してください. 未設定の場合, 実行時にそれぞれ安定エラーコード `JVM_SOURCE_EXPERIMENT_DISABLED` / `JVM_SOURCE_PROVIDER_NOT_SELECTED` が表示されます. 複数の Java コンパイラコンポーネントが同時に有効な場合も, AutoJs6 は先に明示的な選択を求めます.
- **実行** — AutoJs6 エディタで `.java` ファイルを新規作成し, `AutoJsJvmEntry` インターフェースを実装したエントリクラスを書いて実行をタップします (下記の使用例参照). 物理ファイル名は自由です. エントリの単純名は既定で `Main` ですが, スクリプト呼び出し側は明示エントリ API で別の ASCII 単純名を指定できます. `package` 宣言は省略可能で, 合法な ASCII パッケージも使えます.
- **トラブルシューティング** — コンパイル失敗時はコンソールに ECJ 診断が安全なファイル名と行/列位置付きで表示され, 複数エラーは一件ずつ転送されます. 実行時の失敗は安定エラーコードと失敗フェーズ (`SOURCE_TOO_LARGE`, `TIMEOUT`, `BUSY` など) を表示し, ランタイム例外はサニタイズ済みクラス名とソース行のみを表示します. 各サンプルの期待出力は [サンプルガイド](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/samples/README.zh-CN.md) を, エラーコードの完全な一覧は [Java Context API ガイド](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/docs/context-api.zh-CN.md) を参照してください.

******

### 使用例

******

コンソール出力, toast, 中断可能な sleep, アプリ起動の 4 つの基本能力を実演する, すぐ実行できる最小例です:

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

`console().log/error` はスクリプト実行中に一行ずつリアルタイムで転送されます. `sleep` は停止操作で即座に中断できます. その他の例は [samples](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/tree/main/samples) ディレクトリを参照: 能力スモーク `m5-capabilities.java`, クリップボード `capability-set-2-clipboard.java`, スクリプト引数 `script-args.java`, 複数ファイルソースパッケージ `multi-file-source-package/`, 非既定エントリ `arbitrary-entry.java` など. いずれも実機検証記録付きです.

******

### 機能境界

******

安全で予測可能な動作を保つため, 現行バージョンは意図的に以下の境界を維持しています:

- 単一ファイルの Java ソースは上限 4 MiB. 複数ファイルソースパッケージは 2–32 個の `.java` ファイルからなる正規アーカイブで, パスは `package` 宣言と正確に対応する必要があり, 合計 4 MiB のソース上限を共有します. class ファイル, JAR, DEX 入力は未対応.
- エントリクラスは public な具象クラスで `AutoJsJvmEntry` (Entry API 4) を実装し, public な引数なしコンストラクタを持つ必要があります. コンパイル結果に具象実装はちょうど 1 つだけ許されます.
- 言語レベルは Java 8 固定 (ECJ `-source 8 -target 8`). アノテーション処理は無効. ソースは厳格な UTF-8 で, `\uXXXX` Unicode エスケープはコメントや文字列を含む全箇所で禁止.
- ラムダ式はまだ実行可能プロファイルに含まれません (API 24 コンパイルスタブに `java.lang.invoke` がないため) — 匿名クラスを使ってください. Maven やサードパーティ依存は一切解決されません.
- セッションタイムアウトは既定 30 秒 (コンパイルと実行を含む), ハード上限 120 秒. 同時に 1 セッションのみ有効 — 2 つ目のセッションは再試行可能な `BUSY` を受け取ります.
- worker プロセスは使い捨てで毎回の実行後に破棄されます. バックグラウンドスレッドは `run` の戻り後に生存しません.

******

### スクリプトランタイム

******

スクリプトのコンパイルと実行で使える API 面は, 厳密に固定されたホワイトリストです:

#### 利用可能

- Android framework: コンパイル時シンボルは API 24 の class-only スタブ由来. 実行時の挙動はデバイスの OS バージョンに依存.
- AutoJs6 JVM Entry API 4: `JvmScriptContext` が唯一サポートされるホストブリッジ (コンソール / アプリ起動 / sleep / toast / クリップボード / 引数スナップショット / キャンセルビュー).
- 制御された core library desugaring: `java.time` (API 26 表面) と 3 引数の拡張 `Stream.iterate` および `toList` が API 24 端末で実行可能.
- 戻り値と引数は JSON プロファイルに従います: `null` / 真偽値 / 数値 / 文字列 / String キーの `Map` / `Iterable` / 配列. 戻り値は上限 64 KiB, ネスト深さ 64.

#### 利用不可

- デスクトップ JDK 8 の完全なライブラリと API 24 を超える Android API: `Stream.ofNullable` / `takeWhile` / `dropWhile` / `mapMulti*`, 完全な `java.nio.file` などはコンパイル時に直接失敗.
- ラムダ式とメソッド参照の実行時サポート (`java.lang.invoke` は API 24 スタブに含まれません). POJO, record, enum は戻り値にできません.
- アノテーションプロセッサ, Maven 推移的依存, サードパーティ JAR.

******

### セキュリティと分離

******

プラグインはデフォルト拒否で設計されており, 以下の制限が常に有効です:

- コンパイラと worker は独立プロセスで動作し, AutoJs6 のプロセスには決して入りません. サービスは同一署名ホストの呼び出しのみ受け付けます.
- 各ホスト能力はリクエストごとに認可され, クリップボードの読み取りと書き込みは独立した 2 つの認可です. 未認可の呼び出しは両側ホワイトリストによりディスパッチ前に拒否されます.
- ソース, 成果物, 出力, 診断, 戻り値にはすべてハード上限があり, 超過は静かな切り詰めではなく失敗になります. DEX は実行前に完全な構造検証と読み取り専用公開ルールを通過します.
- 外部向けエラーは安定エラーコード, 失敗フェーズ, サニタイズ済みテキストのみ. ランタイム例外は `java.*`/`javax.*` のクラス名か統一された `UserException` のみを表示し, 例外メッセージとスタックはプロセス境界を越えません.
- コンパイルキャッシュは認証付きで, 鍵はコンパイラプロセスの外に出ません. ツールチェーンのバージョン変更で旧キャッシュは全て自動無効化されます.

******

### リリース履歴

******

# v0.8.4

###### 2026/10/04

* `改善` アプリとプラグインセンターのアイコンに管理者提供の図稿を使用し, 色と比率を維持, 共通の視覚基準と透明な余白で輪郭全体を表示, Icon Studio による調整と再生成に対応
* `改善` プラグインセンターのアイコンに Icon Studio で調整したサイズ, 位置, 明暗の図稿と円形背景を適用し, 再生成可能な原稿とパラメーターを保持

# v0.8.3

###### 2026/09/19

* `修正` リソースの解放と worker の終了が同時に発生しても同一セッションの終了処理を重複させず, 実行観測の準備前に終了コールバックを送信することによるホストの断続的なタイムアウトを防止
* `修正` 共有ビルドプラグイン 1.8.3 により, AGP 9.1 での SDK XML v4 解析警告と, JVM 単体テストの組み立て時に APK ネイティブライブラリのアラインメント検証が誤って実行される問題

# v0.8.2

###### 2026/09/15

* `改善` compileSdk と targetSdk を 37 (Android 17) に引き上げ, プラグインの動作は新しいターゲットの影響を受けない

##### さらに詳しい履歴はこちら

* [CHANGELOG-ja.md](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/app/src/main/assets/doc/CHANGELOG-ja.md)

******

### ビルド

******

リポジトリは凍結済みプロトコル AAR (`protocol/`) を同梱しており, AutoJs6 のチェックアウトなしでオフラインビルドできます. JDK 21 推奨, Android SDK には platforms 24 / 26 / 30 / 34 / 36 が必要です (低いバージョンの platform は制御されたコンパイルスタブの生成に使われます). Debug ビルド:

```powershell
.\gradlew.bat :app:assembleDebug --offline
```

Release ビルド:

```powershell
.\gradlew.bat :app:assembleRelease --offline
```

ビルドパラメータは `version.properties` に集約: 現在のバージョン 0.8.4-m9 (build 64), minSdk 24, targetSdk 37. コミット前の完全ゲートはワンショットスクリプト `scripts/verify.ps1` / `scripts/verify.sh` で実行できます (Debug/Release 単体テスト + Lint + Debug APK, 全てオフライン).

Release/debug APK はホストに受け入れられるために AutoJs6 と同一証明書での署名が必須です. ローカル署名素材はバージョン管理対象外の `sign.properties` と `app/sm003.jks` にあります.

******

### リソース構成

******

```text
.readme/lang_*.json
.readme/template_readme.md
.changelog/lang_*.json
.changelog/template_changelog.md
.python/generate_markdown.py
app/src/main/res/values*/strings.xml
```

`strings.xml` はプラグイン名と説明のローカライズを提供します. README と CHANGELOG は `.python/generate_markdown.py` が JSON ソースから生成します. ドキュメントの変更は生成済み Markdown ではなく JSON ソースを編集してください.

アイコンの[原稿](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.python/icons/java-runtime.svg)は管理者から提供されました. `.icons/recipe.json` に調整値を保存し, `python .python/generate_icon_studio.py --check` でレシピとリソースの一致を確認できます.

******

### 関連リンク

******

- AutoJs6 ドキュメント: https://docs.autojs6.com
- AutoJs6 プロジェクトホーム: https://github.com/SuperMonster003/AutoJs6
- 姉妹プラグイン Kotlin Runtime: https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime
- Eclipse JDT Core (ECJ) プロジェクト: https://github.com/eclipse-jdt/eclipse.jdt.core
- Java Context API と実行境界 (メソッド表/上限/エラーコード): https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/docs/context-api.zh-CN.md
- サンプルディレクトリ: https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/tree/main/samples
- プロジェクトロードマップ (マイルストーンごとの検証記録付き): https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/ROADMAP.md
- サードパーティ通知: https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/THIRD_PARTY_NOTICES.md


[16 KB page alignment and build verification](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/master/docs/16kb.md)
