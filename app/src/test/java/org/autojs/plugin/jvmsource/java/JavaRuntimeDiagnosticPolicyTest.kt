package org.autojs.plugin.jvmsource.java

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JavaRuntimeDiagnosticPolicyTest {
    @Test
    fun extractsOnlyValidatedMainOrInnerClassLine() {
        assertEquals(17, JavaRuntimeDiagnosticPolicy.sourceLine(failure("Main", "Main.java", 17)))
        assertEquals(23, JavaRuntimeDiagnosticPolicy.sourceLine(failure("Main\$Inner", "Main.java", 23)))
        assertEquals(
            29,
            JavaRuntimeDiagnosticPolicy.sourceLine(
                failure("com.example.Main\$Inner", "Main.java", 29),
                entryClassName = "com.example.Main",
                sourceFileName = "Main.java",
            ),
        )
    }

    @Test
    fun rejectsPathsOtherClassesAndInvalidLines() {
        assertNull(JavaRuntimeDiagnosticPolicy.sourceLine(failure("Other", "Main.java", 17)))
        assertNull(JavaRuntimeDiagnosticPolicy.sourceLine(failure("Main", "/private/Main.java", 17)))
        assertNull(JavaRuntimeDiagnosticPolicy.sourceLine(failure("Main", "Main.java", -1)))
        assertNull(JavaRuntimeDiagnosticPolicy.sourceLine(failure("Main", "Main.java", Int.MAX_VALUE)))
    }

    @Test
    fun boundedCauseTraversalFindsSafeLineWithoutUsingMessages() {
        val root = IllegalStateException("C:\\private\\secret signer=${"a".repeat(64)}")
        root.stackTrace = arrayOf(StackTraceElement("Other", "run", "Other.java", 1))
        val cause = failure("Main", "Main.java", 31)
        root.initCause(cause)

        assertEquals(31, JavaRuntimeDiagnosticPolicy.sourceLine(root))
    }

    private fun failure(className: String, fileName: String, line: Int): Throwable =
        IllegalStateException("sensitive text").apply {
            stackTrace = arrayOf(StackTraceElement(className, "run", fileName, line))
        }
}
