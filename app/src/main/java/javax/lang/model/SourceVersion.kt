package javax.lang.model

/**
 * Minimal Android runtime sentinel for ECJ's batch file-system bootstrap.
 *
 * Android does not ship the JDK compiler module, while ECJ 3.26 probes
 * `SourceVersion.valueOf("RELEASE_12")` even when annotation processing is disabled. Keeping only
 * the admitted Java 8 level makes that probe take ECJ's pre-JDK-12 path. This is deliberately not
 * a JSR-269 implementation; R1 always invokes ECJ with `-proc:none`.
 */
@Suppress("unused")
internal enum class SourceVersion {
    RELEASE_8,
}
