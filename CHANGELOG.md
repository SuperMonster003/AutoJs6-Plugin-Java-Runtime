# Changelog

All notable changes to this project are recorded in this file. Versions follow Semantic Versioning
with an optional milestone suffix; Android package builds are tracked separately by
`VERSION_BUILD` in `version.properties`.

## [Unreleased]

### Added

- Added a debug-only, provider-private, bounded JSONL observation channel for five phase timings,
  worker startup, per-session cache outcomes, and compiler-process cache counters.
- Added a reproducible API 24/API 36 production-Binder performance baseline, including raw
  schema-v1 JSONL evidence, pinned artifact digests, worker-startup share, and the M8-1 comparison
  contract.
- Added PowerShell and POSIX-shell offline pre-push verification entry points.
- Added a Chinese Java Context API guide covering the source profile, every Entry API 2 method,
  return-value types, resource limits, cancellation, diagnostics, and error semantics.
- Added cancellation, return-value, compiler-error, and result-limit samples with an automated
  provider pipeline suite verified on API 24 and API 37 loader branches.
- Added the bounded R4 multi-DEX artifact profile on API 24/25 and API 27+: one to four contiguous
  `classesN.dex` files, strict per-file and cross-file validation, and API 24/API 36 direct-loader
  plus disposable-worker device evidence using a generated 65,536-method fixture. API 26 remains
  single-DEX because its public in-memory loader has no multi-buffer constructor.

### Changed

- Changed the offline verification gate to run the unit suite against both Debug and Release build
  constants, including an assertion that Release observation export has zero file side effects.
- Changed ECJ diagnostic redaction to replace sensitive path, digest, Binder, process-identity,
  and metadata segments while preserving the remaining actionable compiler message.
- Changed ECJ batch reporting to emit each compiler problem as an ordered Protocol 1.1 diagnostic
  frame under the existing cumulative 64 KiB wire budget.
- Changed the compilation-cache operation lane to permit one cooldown-gated, quiescence-checked
  worker rebuild after a timeout; a second timeout remains permanently fail-closed.
- Closed the M8-1 worker-prewarming evaluation as no-go after API 36 interleaved measurements failed
  to reproduce the required median improvement without tail-latency regression; the serial runtime
  path remains unchanged and the rejected candidate's raw observation evidence is retained.
- Changed the provider-private compiler/worker handoff to carry ordered DEX metadata and descriptor
  arrays, while preserving the external Protocol 1.1 surface and legacy single-DEX result identity.
- Changed compilation-cache manifests to schema 2 and the implementation revision to `r4-cache-v3`;
  multi-DEX entries are authenticated, materialized, revalidated, and invalidated as one unit.

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
