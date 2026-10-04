******

### Release History

******

# v0.8.4

###### 2026/10/04

* `Improvement` Application and Plugin Center icons use maintainer-supplied artwork, preserving colors and proportions with transparent padding and unified optical sizing to keep the complete silhouette visible, with Icon Studio adjustment and regeneration

# v0.8.3

###### 2026/09/19

* `Fix` Concurrent resource cleanup and worker exit can no longer finalize the same session twice or deliver a terminal callback before execution observations are ready, preventing intermittent host timeouts
* `Fix` SDK XML v4 parsing warnings with AGP 9.1 and APK native alignment checks incorrectly triggered by JVM unit-test assembly tasks, using shared build plugins 1.8.3

# v0.8.2

###### 2026/09/15

* `Improvement` Raise compileSdk and targetSdk to 37 (Android 17); the plugin's behavior does not depend on the new target

# v0.8.1

###### 2026/09/13

* `Fix` The plugin center can activate a newly installed provider through a protected entry; displayed metadata follows the installed package
* `Improvement` Host activation, plugin metadata, localized documentation and signed release collection follow the common plugin conventions

# v0.8.0

###### 2026/09/12

* `Improvement` Reorganize the README and release history around usage, examples, and capability boundaries, with consistent generation for ten languages from JSON sources
* `Improvement` Build verification rejects accidental native dependencies and produces a JSON report

# v0.8.0-m9

###### 2026/08/27

* `Hint` The session timeout stays at a 30-second default / 120-second hard cap, and concurrency stays at one active session (a second session receives a retryable `BUSY`) - both relaxation evaluations concluded not to proceed for now
* `Hint` ECJ upgrade evaluation: newer ECJ releases (3.42/3.46) cannot run on Android, so the release line stays pinned to ECJ 3.26.0 and Java 8
* `Feature` Multi-file source packages (Protocol 1.6): a single request may submit a canonical archive of 2-32 `.java` files whose paths must match their `package` declarations exactly; path traversal, compressed entries, symlinks, and duplicates are all rejected
* `Feature` Opened arbitrary entry class names end to end: callers may explicitly select an ASCII entry simple name (the default remains `Main`), with the new non-default-entry sample `arbitrary-entry.java`
* `Improvement` Confirmed Kotlin/JVM support stays with the sister plugin Kotlin Runtime; the two repositories share the frozen protocol and conformance tests without any runtime dependency
* `Dependency` Upgraded the runtime D8/R8 from 8.13.17 to 8.13.23; compilation cache keys invalidate automatically with the toolchain version

# v0.7.0-m9

###### 2026/08/26

* `Hint` Observations never contain source, arguments, paths, or process identities; release builds export no cumulative cache counters
* `Feature` Every execution now ends with a bounded observation summary (Protocol 1.5): per-phase compile/execute/cleanup timings, the per-request cache outcome, cold/warm starts, and up to six resource samples, displayed uniformly by the AutoJs6 console

# v0.6.0-m9

###### 2026/08/26

* `Feature` Runtime exceptions now surface a sanitized class name with the source line (Protocol 1.4): `java.*`/`javax.*` names stay as-is while every other class shows as `UserException`; exception messages and stacks never cross the process boundary
* `Feature` The runtime exception sample `runtime-exception.java` demonstrating the user-visible class-plus-line shape

# v0.5.0-m9

###### 2026/08/25

* `Hint` Arguments accept JSON-profile values only (null / booleans / numbers / strings / String-key Map / Iterable / arrays); passing POJOs or Android objects yields the stable error `JVM_SOURCE_INVALID_ARGUMENTS`
* `Feature` Script arguments (Protocol 1.3 / Entry API 4): `context.args()` returns a read-only snapshot of the host execution-config arguments (64 KiB cap, 64 nesting levels); existing argument-free scripts keep running unchanged
* `Feature` The script arguments sample `script-args.java` reading nested Maps/Lists, booleans, numbers, strings, and null

# v0.4.0-m9

###### 2026/08/25

* `Hint` On Android 10 and later, AutoJs6 must be in the foreground (with a resumed Activity) for clipboard calls; background calls fail reliably before the system clipboard is touched
* `Feature` Clipboard capabilities (Protocol 1.2 / Entry API 3): `clipboard().getText/setText` with read and write as two independent grants and a 16 KiB text cap
* `Feature` Multi-DEX artifacts: 1-4 contiguous `classesN.dex` files within 32 MiB total; a 65,536-method-reference sample passes on API 24/36 devices (API 26 stays single-DEX due to a platform limitation)
* `Feature` Controlled core library desugaring: `java.time` and the three-argument enhanced `Stream.iterate`/`toList` work from API 24 onward; unsupported methods fail at compile time instead of crashing older devices at runtime
* `Feature` The Chinese "Java Context API & runtime boundaries" guide: every method, the return-value type table, resource limits, and error-code semantics
* `Feature` The device-verified sample matrix for cancellation, return-value families, compiler errors, and result-limit behavior, plus the one-shot offline gate scripts `scripts/verify.ps1` / `verify.sh`
* `Fix` Diagnostic sanitization now replaces sensitive segments in place and keeps the rest of the compiler message, instead of downgrading the whole message to fixed fallback text
* `Fix` The compilation cache operation lane now recovers once after a cooldown following a timeout, instead of staying disabled until process restart
* `Improvement` Multiple ECJ problems from one compilation are streamed one by one in source order (sharing the 64 KiB diagnostic budget)
* `Improvement` Debug builds gained a private JSONL observation channel (five phase timings / worker cold start / cache counters); release builds keep zero export, with test assertions
* `Improvement` Completed the API 24/36 production-path performance baseline and the worker prewarm evaluation (the serial path stays); arbitrary entry class names became plugin-side ready
* `Improvement` Cross-process cache persistence was rejected after security review; the cache key remains valid only inside the compiler process

# v0.3.0-m5

###### 2026/08/25

* `Hint` First usable milestone release (covering M1-M5); same-signer host checks, per-capability authorization, disposable worker processes, and fail-closed validation enabled from the start
* `Feature` Standalone Java source compile/run plugin established: embedded ECJ 3.26.0 (Java 8) and D8 8.13.17, with compilation and execution in separate processes
* `Feature` Implemented the Protocol 1.1 single-file source profile: entry-class analysis, sanitized diagnostics, and complete failure-phase mapping
* `Feature` Implemented four individually authorized host capabilities: app launch, console stream, sleep, and toast
* `Feature` Implemented the authenticated compilation cache with local telemetry
* `Fix` Two API-requirement lint findings; API 26 requirements now propagate to callers via `@RequiresApi`
* `Improvement` Migrated the build to the shared platform-versions convention plugins, centralizing dependency coordinates in the version catalog while keeping the literal dual-source assertions
