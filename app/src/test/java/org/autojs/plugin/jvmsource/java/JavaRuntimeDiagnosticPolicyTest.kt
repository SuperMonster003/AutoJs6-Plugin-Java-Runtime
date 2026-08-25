package org.autojs.plugin.jvmsource.java

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class JavaRuntimeDiagnosticPolicyTest {
    @Test
    fun extractsOnlyValidatedMainOrInnerClassLineAndPlatformClass() {
        assertEquals(
            JavaRuntimeDiagnostic(17, "java.lang.IllegalStateException"),
            JavaRuntimeDiagnosticPolicy.extract(failure("Main", "Main.java", 17)),
        )
        assertEquals(
            JavaRuntimeDiagnostic(23, "java.lang.IllegalStateException"),
            JavaRuntimeDiagnosticPolicy.extract(failure("Main\$Inner", "Main.java", 23)),
        )
        assertEquals(
            JavaRuntimeDiagnostic(29, "java.lang.IllegalStateException"),
            JavaRuntimeDiagnosticPolicy.extract(
                failure("com.example.Main\$Inner", "Main.java", 29),
                entryClassName = "com.example.Main",
                sourceFileName = "Main.java",
            ),
        )
    }

    @Test
    fun rejectsPathsOtherClassesAndInvalidLines() {
        assertNull(JavaRuntimeDiagnosticPolicy.extract(failure("Other", "Main.java", 17)))
        assertNull(JavaRuntimeDiagnosticPolicy.extract(failure("Main", "/private/Main.java", 17)))
        assertNull(JavaRuntimeDiagnosticPolicy.extract(failure("Main", "Main.java", -1)))
        assertNull(JavaRuntimeDiagnosticPolicy.extract(failure("Main", "Main.java", Int.MAX_VALUE)))
    }

    @Test
    fun boundedCauseTraversalUsesTheThrowableOwningTheSafeFrame() {
        val root = IllegalStateException("C:\\private\\secret signer=${"a".repeat(64)}")
        root.stackTrace = arrayOf(StackTraceElement("Other", "run", "Other.java", 1))
        val cause = failure(
            "Main",
            "Main.java",
            31,
            IllegalArgumentException("cause secret"),
        )
        root.initCause(cause)

        val diagnostic = JavaRuntimeDiagnosticPolicy.extract(root)

        assertEquals(JavaRuntimeDiagnostic(31, "java.lang.IllegalArgumentException"), diagnostic)
        assertFalse(diagnostic.toString().contains("secret"))
        assertFalse(diagnostic.toString().contains("private"))
        assertFalse(diagnostic.toString().contains("signer"))
    }

    @Test
    fun mapsEveryNonPlatformNamespaceToTheSinglePublicSentinel() {
        listOf(
            "org.example.SecretFailure",
            "android.os.DeadObjectException",
            "kotlin.KotlinNullPointerException",
            "java.lang.IllegalStateException[]",
            "",
            "java." + "x".repeat(256),
        ).forEach { className ->
            assertEquals(
                "UserException",
                JavaRuntimeDiagnosticPolicy.sanitizeExceptionClassName(className),
            )
        }
        assertEquals(
            "java.lang.IllegalStateException",
            JavaRuntimeDiagnosticPolicy.sanitizeExceptionClassName("java.lang.IllegalStateException"),
        )
        assertEquals(
            "javax.net.ssl.SSLException",
            JavaRuntimeDiagnosticPolicy.sanitizeExceptionClassName("javax.net.ssl.SSLException"),
        )

        val diagnostic = JavaRuntimeDiagnosticPolicy.extract(
            failure("Main", "Main.java", 41, SecretFailure("M9-3 secret")),
        )
        assertEquals(JavaRuntimeDiagnostic(41, "UserException"), diagnostic)
        assertFalse(diagnostic.toString().contains("SecretFailure"))
        assertFalse(diagnostic.toString().contains("M9-3 secret"))
    }

    @Test
    fun causeTraversalStopsBeforeAnUnboundedTail() {
        var chain = failure("Main", "Main.java", 51)
        repeat(8) { index ->
            chain = IllegalStateException("wrapper secret $index").apply {
                stackTrace = arrayOf(StackTraceElement("Other", "run", "Other.java", index + 1))
                initCause(chain)
            }
        }

        assertNull(JavaRuntimeDiagnosticPolicy.extract(chain))
    }

    private fun failure(
        className: String,
        fileName: String,
        line: Int,
        error: Throwable = IllegalStateException("sensitive text"),
    ): Throwable = error.apply {
        stackTrace = arrayOf(StackTraceElement(className, "run", fileName, line))
    }

    private class SecretFailure(message: String) : RuntimeException(message)
}
