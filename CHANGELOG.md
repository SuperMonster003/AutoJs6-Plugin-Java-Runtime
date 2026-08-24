# Changelog

All notable changes to this project are recorded in this file. Versions follow Semantic Versioning
with an optional milestone suffix; Android package builds are tracked separately by
`VERSION_BUILD` in `version.properties`.

## [Unreleased]

### Added

- Added PowerShell and POSIX-shell offline pre-push verification entry points.
- Added a Chinese Java Context API guide covering the source profile, every Entry API 2 method,
  return-value types, resource limits, cancellation, diagnostics, and error semantics.
- Added cancellation, return-value, compiler-error, and result-limit samples with an automated
  provider pipeline suite verified on API 24 and API 37 loader branches.

## [0.3.0-m5] - 2026-08-25

### Added

- Established the independent `org.autojs.plugin.JVM_SOURCE` Java provider for AutoJs6 Protocol
  1.1 and Entry API 2.
- Added a Java 8 single-source pipeline backed by pinned ECJ 3.26.0 and D8 8.13.17, with API 24 as
  the minimum Android runtime.
- Added the M5 context capability set: `app().launch`, streaming `console().log/error`, cancellable
  `sleep`, `toast`, and the cancellation view.
- Added bounded JSON-profile return values, structured failure phases/error codes, compiler
  diagnostics, and stdout/stderr streaming.
- Added an authenticated compilation cache with bounded cleanup and local cache telemetry.

### Security

- Isolated the host, compiler service, and disposable execution worker into separate processes.
- Enforced host signature verification, negotiated capability checks, and matching provider/worker
  host-call allowlists.
- Added bounded source/output/result handling, session and worker watchdogs, cancellation, class/JAR
  validation, full DEX structure validation, and read-only private DEX publication rules.
- Pinned the complete local Protocol AAR set by module identity and SHA-256 digest.

### Changed

- Migrated project version/JDK/SDK handling to the shared AutoJs6 build convention plugins.
- Centralized pinned application dependencies in the Gradle version catalog while retaining
  independent literal-coordinate checks in `verifyPinnedInputs`.
- Propagated API 26 requirements from the D8 path API and in-memory DEX loader to their callers.
