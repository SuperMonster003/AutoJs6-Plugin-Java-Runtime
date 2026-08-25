package org.autojs.plugin.jvmsource.java

import org.autojs.plugin.jvmsource.api.JvmSourceContract
import java.util.Collections
import java.util.IdentityHashMap

/** The complete provider-internal runtime projection. It cannot retain a Throwable or its text. */
internal data class JavaRuntimeDiagnostic(
    val sourceLine: Int,
    val exceptionClassName: String,
)

/** Extracts only a bounded requested-source line and public class name from a runtime failure. */
internal object JavaRuntimeDiagnosticPolicy {
    private val SAFE_EXCEPTION_CLASS_NAME = Regex(
        "(?:java|javax)(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)+",
    )

    fun extract(
        error: Throwable,
        entryClassName: String = "Main",
        sourceFileName: String = "Main.java",
    ): JavaRuntimeDiagnostic? {
        val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
        var current: Throwable? = error
        repeat(MAX_CAUSE_DEPTH) {
            val value = current ?: return null
            if (!seen.add(value)) return null
            value.stackTrace.asSequence().take(MAX_STACK_FRAMES).firstOrNull { frame ->
                (frame.className == entryClassName || frame.className.startsWith("$entryClassName\$")) &&
                    frame.fileName == sourceFileName && frame.lineNumber in 1..MAX_SOURCE_POSITION
            }?.let { frame ->
                return JavaRuntimeDiagnostic(
                    sourceLine = frame.lineNumber,
                    exceptionClassName = sanitizeExceptionClassName(value.javaClass.name),
                )
            }
            current = value.cause
        }
        return null
    }

    /** Independent provider-side profile; shared protocol and Host validation run afterwards. */
    internal fun sanitizeExceptionClassName(value: String): String =
        value.takeIf { className ->
            className.toByteArray(Charsets.UTF_8).size <=
                JvmSourceContract.MAX_RUNTIME_EXCEPTION_CLASS_NAME_BYTES &&
                SAFE_EXCEPTION_CLASS_NAME.matches(className)
        } ?: JvmSourceContract.USER_RUNTIME_EXCEPTION_CLASS_NAME

    private const val MAX_CAUSE_DEPTH = 8
    private const val MAX_STACK_FRAMES = 256
    private const val MAX_SOURCE_POSITION = 1_000_000
}
