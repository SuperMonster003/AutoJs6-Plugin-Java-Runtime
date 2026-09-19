<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-java-runtime-ic-launcher" border="0" width="128" />
  </p>

  <p>AutoJs6용 Java 8 소스 (단일 파일 / 다중 파일 소스 패키지) 컴파일/실행 플러그인</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-Java-Runtime?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-Java-Runtime?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/commit/7caffea0b8d593ac3f4bf722d7b12edd78d6b94b"><img alt="Created" src="https://img.shields.io/date/1787396606?color=2e7d32&label=Created"/></a>
    <br>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-Java-Runtime?color=534BAE&label=License"/></a>
  </p>
</div>

******

### 언어 (Languages)

******

현재 README.md는 다음 언어를 지원합니다:

- [简体中文 [zh-Hans]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-zh-Hans.md)
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-zh-Hant-HK.md)
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-zh-Hant-TW.md)
- [English [en]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-en.md)
- [Français [fr]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-fr.md)
- [Español [es]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-es.md)
- [日本語 [ja]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-ja.md)
- 한국어 [ko] # 현재
- [Русский [ru]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-ru.md)
- [العربية [ar]](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/.readme/README-ar.md)

******

### 소개

******

AutoJs6 Java Runtime 플러그인을 사용하면 AutoJs6에서 Java 소스 코드 (`.java`)를 직접 컴파일하고 실행할 수 있습니다. 단일 파일과 2–32개 파일의 유계 소스 패키지를 지원합니다. 플러그인은 Eclipse 컴파일러 ECJ 3.26.0 (언어 수준 Java 8)와 D8 8.13.23 바이트코드 변환기를 내장하며, 컴파일 결과물은 일회용 worker 프로세스에서 실행됩니다. 컴파일러와 스크립트 모두 AutoJs6 프로세스 안에서는 절대 실행되지 않습니다.

이 플러그인은 [Kotlin Runtime](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime)과 자매 플러그인 관계로, 함께 설치하여 각각 Java / Kotlin 소스를 담당할 수 있습니다. AutoJs6는 언어별로 선택된 컴파일러 구성 요소를 기억합니다.

******

### 기능

******

- `org.autojs.plugin.JVM_SOURCE` 컴파일/실행 서비스와 `org.autojs.plugin.INFO` 플러그인 센터 발견 서비스를 제공하며, 둘 다 서명으로 보호되고 독립된 보조 프로세스에서 동작합니다.
- Eclipse 컴파일러 ECJ 3.26.0 (언어 수준 Java 8)를 내장. 출력은 D8 8.13.23로 DEX 변환 후 실행되며, 연속된 1–4개의 `classesN.dex`를 지원합니다 (합계 32 MiB 이내, API 26 기기는 단일 DEX 유지).
- 개별 인가되는 6가지 호스트 기능 브리지 지원: 콘솔 실시간 출력 `console().log/error`, 앱 실행 `app().launch`, 중단 가능한 `sleep`, `toast` 메시지, 그리고 독립 인가되는 클립보드 읽기 `clipboard().getText`와 쓰기 `clipboard().setText`.
- 호스트 스크립트 인자 지원: `context.args()`는 실행 설정 인자의 읽기 전용 JSON 스냅숏을 추가 인가 없이 반환합니다. 기존의 인자 없는 스크립트는 수정이 필요 없습니다.
- 단일 소스 파일과 2–32개 파일의 정규 소스 패키지를 수용. 진입 클래스의 단순명은 호출자가 명시적으로 선택할 수 있으며 기본값은 `Main`입니다.
- 통제된 core library desugaring: `java.time`과 일부 향상된 Stream 메서드를 API 24부터 사용할 수 있습니다. 미지원 메서드는 컴파일 시점에 실패하며 구형 기기에서 런타임 충돌을 일으키지 않습니다.
- 인증된 컴파일 캐시: 동일 소스를 재실행하면 캐시에 적중하여 컴파일을 건너뜁니다. 툴체인 버전 변경 시 이전 캐시가 전부 자동 무효화됩니다.
- 컴파일 오류는 ECJ 소스 순서대로 한 건씩 전달되며 안전한 파일 이름과 행/열 위치가 붙습니다. 매 실행 종료 시 단계별 소요 시간, 캐시 결과, 리소스 샘플의 유계 관측 요약이 제공됩니다.
- README와 CHANGELOG는 간체 중국어/번체 중국어 (홍콩/대만)/영어/프랑스어/스페인어/일본어/한국어/러시아어/아랍어 등 10개 언어를 지원합니다.

******

### 빠른 시작

******

- **설치** — [Releases](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/releases)에서 APK를 내려받아 설치하거나, 아래 「빌드」 절에 따라 로컬 빌드합니다. 주의: 플러그인은 AutoJs6와 동일한 인증서로 서명되어야 하며, 호스트 AutoJs6 버전 코드는 5281 이상이어야 합니다.
- **활성화** — JVM 소스 실행은 현재 AutoJs6의 실험적 기능입니다: 호스트에서 실험 스위치를 켜고, Java 언어의 컴파일러 구성 요소로 이 플러그인을 명시적으로 선택하세요. 설정이 없으면 실행 시 각각 안정 오류 코드 `JVM_SOURCE_EXPERIMENT_DISABLED` / `JVM_SOURCE_PROVIDER_NOT_SELECTED`가 표시됩니다. 여러 Java 컴파일러 구성 요소가 동시에 활성화된 경우에도 AutoJs6는 먼저 명시적 선택을 요구합니다.
- **실행** — AutoJs6 에디터에서 `.java` 파일을 새로 만들고, `AutoJsJvmEntry` 인터페이스를 구현한 진입 클래스를 작성한 뒤 실행을 누릅니다 (아래 사용 예시 참조). 물리적 파일 이름은 자유입니다. 진입 단순명은 기본적으로 `Main`이며, 스크립트 호출자는 명시적 진입 API로 다른 ASCII 단순명을 지정할 수 있습니다. `package` 선언은 생략할 수 있고, 합법적인 ASCII 패키지도 사용할 수 있습니다.
- **문제 해결** — 컴파일 실패 시 콘솔에 ECJ 진단이 안전한 파일 이름과 행/열 위치와 함께 표시되며, 여러 오류는 한 건씩 전달됩니다. 실행 실패는 안정 오류 코드와 실패 단계 (`SOURCE_TOO_LARGE`, `TIMEOUT`, `BUSY` 등)를 표시하고, 런타임 예외는 정제된 클래스 이름과 소스 행만 표시합니다. 각 샘플의 기대 출력은 [샘플 가이드](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/samples/README.zh-CN.md)를, 오류 코드 전체 표는 [Java Context API 가이드](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/docs/context-api.zh-CN.md)를 참조하세요.

******

### 사용 예시

******

콘솔 출력, toast, 중단 가능한 sleep, 앱 실행의 4가지 기본 기능을 시연하는, 바로 실행 가능한 최소 예제입니다:

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

`console().log/error`는 스크립트 실행 중 한 줄씩 실시간으로 전달됩니다. `sleep`은 중지 조작으로 즉시 중단할 수 있습니다. 더 많은 예제는 [samples](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/tree/main/samples) 디렉터리를 참조: 기능 스모크 `m5-capabilities.java`, 클립보드 `capability-set-2-clipboard.java`, 스크립트 인자 `script-args.java`, 다중 파일 소스 패키지 `multi-file-source-package/`, 비기본 진입 `arbitrary-entry.java` 등. 모두 실기기 검증 기록이 있습니다.

******

### 기능 경계

******

안전하고 예측 가능한 동작을 위해 현재 버전은 의도적으로 다음 경계를 유지합니다:

- 단일 파일 Java 소스는 상한 4 MiB. 다중 파일 소스 패키지는 2–32개의 `.java` 파일로 이루어진 정규 아카이브여야 하며, 경로가 `package` 선언과 정확히 대응해야 하고, 합계 4 MiB 소스 상한을 공유합니다. class 파일, JAR, DEX 입력은 미지원.
- 진입 클래스는 public 구체 클래스로 `AutoJsJvmEntry` (Entry API 4)를 구현하고 public 무인자 생성자를 가져야 합니다. 컴파일 결과에는 구체 구현이 정확히 하나만 허용됩니다.
- 언어 수준은 Java 8로 고정 (ECJ `-source 8 -target 8`). 어노테이션 처리는 비활성. 소스는 엄격한 UTF-8이어야 하며 `\uXXXX` Unicode 이스케이프는 주석과 문자열을 포함한 모든 곳에서 금지됩니다.
- 람다 식은 아직 실행 가능 프로필에 포함되지 않습니다 (API 24 컴파일 스텁에 `java.lang.invoke`가 없음) — 익명 클래스를 사용하세요. Maven이나 서드파티 의존성은 해석하지 않습니다.
- 세션 타임아웃은 기본 30초 (컴파일과 실행 포함), 하드 상한 120초. 동시에 하나의 세션만 활성 — 두 번째 세션은 재시도 가능한 `BUSY`를 받습니다.
- worker 프로세스는 일회용으로 매 실행 후 폐기됩니다. 백그라운드 스레드는 `run` 반환 후 살아남지 않습니다.

******

### 스크립트 런타임

******

스크립트 컴파일과 실행에 쓸 수 있는 API 표면은 정확히 고정된 허용 목록입니다:

#### 사용 가능

- Android framework: 컴파일 타임 심벌은 API 24 class-only 스텁에서 제공. 런타임 동작은 여전히 기기 OS 버전에 의존.
- AutoJs6 JVM Entry API 4: `JvmScriptContext`가 유일하게 지원되는 호스트 브리지 (콘솔 / 앱 실행 / sleep / toast / 클립보드 / 인자 스냅숏 / 취소 뷰).
- 통제된 core library desugaring: `java.time` (API 26 표면)과 3-인자 향상 `Stream.iterate` 및 `toList`, API 24 기기에서 실행 가능.
- 반환값과 인자는 JSON 프로필을 따릅니다: `null` / 불리언 / 숫자 / 문자열 / String 키 `Map` / `Iterable` / 배열. 반환값 상한 64 KiB, 중첩 깊이 64.

#### 사용 불가

- 데스크톱 JDK 8 전체 라이브러리와 API 24를 초과하는 Android API: `Stream.ofNullable` / `takeWhile` / `dropWhile` / `mapMulti*`, 완전한 `java.nio.file` 등은 컴파일 시점에 곧바로 실패합니다.
- 람다 식과 메서드 참조의 런타임 지원 (`java.lang.invoke`가 API 24 스텁에 없음). POJO, record, enum은 반환값으로 쓸 수 없습니다.
- 어노테이션 프로세서, Maven 전이 의존성, 서드파티 JAR.

******

### 보안과 격리

******

플러그인은 기본 거부 원칙으로 설계되었으며, 다음 제한이 항상 적용됩니다:

- 컴파일러와 worker는 독립 프로세스에서 동작하며 AutoJs6 프로세스에 절대 들어가지 않습니다. 서비스는 동일 서명 호스트 호출만 수락합니다.
- 각 호스트 기능은 요청별로 인가되며, 클립보드 읽기와 쓰기는 두 개의 독립된 인가입니다. 인가되지 않은 호출은 양측 허용 목록에 의해 디스패치 전에 거부됩니다.
- 소스, 산출물, 출력, 진단, 반환값 모두 하드 상한이 있으며, 초과 시 조용한 잘림이 아니라 실패로 처리됩니다. DEX는 실행 전 완전한 구조 검증과 읽기 전용 게시 규칙을 통과합니다.
- 외부 오류는 안정 오류 코드, 실패 단계, 정제된 텍스트만 담습니다. 런타임 예외는 `java.*`/`javax.*` 클래스 이름 또는 통일된 `UserException`만 노출하며, 예외 메시지와 스택은 프로세스 경계를 넘지 않습니다.
- 컴파일 캐시는 인증을 거치며 키는 컴파일러 프로세스 밖으로 나가지 않습니다. 툴체인 버전 변경 시 이전 캐시가 전부 자동 무효화됩니다.

******

### 릴리스 이력

******

# v0.8.3

###### 2026/09/19

* `수정` 리소스 정리와 worker 종료가 동시에 발생해도 같은 세션을 두 번 완료하지 않으며, 실행 관측 준비 전에 종료 콜백이 전송되어 발생하던 호스트의 간헐적 시간 초과를 방지
* `수정` 공유 빌드 플러그인 1.8.3을 통해 AGP 9.1의 SDK XML v4 파싱 경고 및 JVM 단위 테스트 조립 작업에서 APK 네이티브 라이브러리 정렬 검사가 잘못 실행되는 문제 해결

# v0.8.2

###### 2026/09/15

* `개선` compileSdk 와 targetSdk 를 37 (Android 17) 로 올리며, 플러그인 동작은 새 대상 버전의 영향을 받지 않음

# v0.8.1

###### 2026/09/13

* `수정` 플러그인 센터에서 보호된 진입점으로 새 설치를 활성화하고 설치된 패키지의 메타데이터를 표시
* `개선` 호스트 활성화, 메타데이터, 다국어 문서 및 서명된 APK 수집을 공통 규칙에 맞게 정리

##### 더 많은 릴리스 이력은 다음을 참조

* [CHANGELOG-ko.md](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/app/src/main/assets/doc/CHANGELOG-ko.md)

******

### 빌드

******

저장소는 동결된 프로토콜 AAR (`protocol/`)을 포함하므로 AutoJs6 체크아웃 없이 오프라인 빌드가 가능합니다. JDK 21 권장, Android SDK에는 platforms 24 / 26 / 30 / 34 / 36이 필요합니다 (낮은 버전 platform은 통제된 컴파일 스텁 생성에 사용). Debug 빌드:

```powershell
.\gradlew.bat :app:assembleDebug --offline
```

Release 빌드:

```powershell
.\gradlew.bat :app:assembleRelease --offline
```

빌드 매개변수는 `version.properties`에 집중되어 있습니다: 현재 버전 0.8.2-m9 (build 56), minSdk 24, targetSdk 37. 커밋 전 전체 게이트는 원커맨드 스크립트 `scripts/verify.ps1` / `scripts/verify.sh`로 실행할 수 있습니다 (Debug/Release 단위 테스트 + Lint + Debug APK, 전 과정 오프라인).

Release/debug APK는 호스트가 수락하려면 AutoJs6와 동일 인증서로 서명되어야 합니다. 로컬 서명 자료는 버전 관리에서 제외된 `sign.properties`와 `app/sm003.jks`에 있습니다.

******

### 리소스 구조

******

```text
.readme/lang_*.json
.readme/template_readme.md
.changelog/lang_*.json
.changelog/template_changelog.md
.python/generate_markdown.py
app/src/main/res/values*/strings.xml
```

`strings.xml`은 플러그인 이름과 설명의 현지화를 제공합니다. README와 CHANGELOG는 `.python/generate_markdown.py`가 JSON 소스에서 생성합니다. 문서를 수정하려면 생성된 Markdown이 아니라 JSON 소스를 편집하세요.

******

### 관련 링크

******

- AutoJs6 문서: https://docs.autojs6.com
- AutoJs6 프로젝트 홈: https://github.com/SuperMonster003/AutoJs6
- 자매 플러그인 Kotlin Runtime: https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime
- Eclipse JDT Core (ECJ) 프로젝트: https://github.com/eclipse-jdt/eclipse.jdt.core
- Java Context API와 실행 경계 (메서드 표/상한/오류 코드): https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/docs/context-api.zh-CN.md
- 샘플 디렉터리: https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/tree/main/samples
- 프로젝트 로드맵 (마일스톤별 검증 기록 포함): https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/ROADMAP.md
- 서드파티 고지: https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/main/THIRD_PARTY_NOTICES.md


[16 KB page alignment and build verification](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime/blob/master/docs/16kb.md)
