# Session finalization regression, 2026-09-17

Version: 0.8.3-m9, build 58. This retains the M9 milestone designation.

## Failure and correction

The AutoJs6 temporary-branch integration tests intermittently timed out after a Java worker had already exited. The host thread dump showed its script thread waiting for a terminal callback, while the main and Binder threads were idle. An attempted IDE remote-debugger attachment to the owned Android emulator did not finish attaching; it supplied no breakpoint evidence. The diagnosis instead uses the thread dump, callback control flow and a deterministic concurrency regression.

`ProviderResourceCleanupBarrier.resourcesClosed()` and `observeWorkerDeath()` could both return true. This allowed two callers to enter `finishWorkerCleanup()` concurrently. The second caller could skip observation publication after the first caller set its publication flag, retire the terminal delivery, and attempt a callback before the first caller populated the observation. Constructing the terminal payload then failed, leaving the host waiting until its timeout.

Both transitions now claim finalization under the barrier's existing monitor. Exactly one caller can publish the observation and terminal result. Resource closure and worker-exit ordering retain their original requirements.

## Verification

- Before the correction, both newly added barrier regressions failed (4 tests, 2 failures). One uses latches to keep the first finalizer busy while worker death arrives; the other checks repeated worker-death notifications.
- After the correction, all 167 plugin JVM tests passed with no failures or skips.
- Debug, Android test and signed release APKs built successfully. `lintDebug` reported 0 errors and 33 warnings. Signed release collection and native alignment checks completed successfully.
- The Markdown generator and its `--check` completed; all 3 Python generator tests passed.
- The AutoJs6 host's 9 JVM integration tests passed with both the debug plugin (22.557 seconds) and the signed release plugin (14.082 seconds), using the owned Android 13 / API 33 x86_64 emulator. The suite includes 12 consecutive canonical Java executions, named entries, multiple source files, JSON arguments, foreground clipboard access, safe exception details, execution observations and compatibility with the separate legacy Kotlin provider.
- The host tests pin D8 8.13.23 for this provider version. The integration build enables experimental JVM source execution explicitly. The host's existing checked-in `gradle.properties` also enables it; this session preserves that setting. Its Gradle provider fallback is false only when the property is absent.

Build command:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :app:appendDigestToReleasedFiles --console=plain
```

Local raw logs and archived JUnit reports are under the AutoJs6 workspace's `build/verification/path-display-branch-audit/`, including `java-plugin-race-before.log`, `java-plugin-build.log`, `jvm-device-fixed.log`, `jvm-device-signed-release.log` and `jvm-timeout-threads.txt`. No attached physical phone was used or modified. No push or publication was performed.
