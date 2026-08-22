package org.autojs.plugin.jvmsource.java

import org.autojs.plugin.jvmsource.api.JvmDiagnosticSeverity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EcjDiagnosticSanitizerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun extractsLocationAndRemovesEveryPrivateCompilerPath() {
        val workspace = temporaryFolder.newFolder("provider-private-root")
        val source = workspace.resolve("Main.java")
        val androidJar = workspace.resolve("android.jar")
        val raw = """
            ----------
            1. ERROR in ${source.absolutePath} (at line 7)
                bad();
                ^^^
            The method bad() is undefined; classpath=${androidJar.absolutePath}
            ----------
        """.trimIndent()

        val nullableDiagnostic = EcjDiagnosticSanitizer.sanitize(
            raw,
            succeeded = false,
            byteLimit = 1_024,
            privateFiles = listOf(source, workspace, androidJar),
        )
        assertNotNull(nullableDiagnostic)
        val diagnostic = requireNotNull(nullableDiagnostic)
        assertEquals(JvmDiagnosticSeverity.ERROR, diagnostic.severity)
        assertEquals(7, diagnostic.line)
        assertTrue((diagnostic.column ?: 0) > 0)
        assertEquals("ECJ_ERROR", diagnostic.code)
        assertEquals("Java compilation error", diagnostic.message)
        assertFalse(workspace.absolutePath in diagnostic.message)
        assertFalse(androidJar.absolutePath in diagnostic.message)
    }

    @Test
    fun logicalSourceRewriteKeepsActionableEcjMethodError() {
        val workspace = temporaryFolder.newFolder("provider-private-actionable-root")
        val source = workspace.resolve("Main.java")
        val raw = """
            ----------
            1. ERROR in ${source.absolutePath} (at line 4)
                    System.out.println2("hello");
                               ^^^^^^^^
            The method println2(String) is undefined for the type PrintStream
            ----------
            1 problem (1 error)
        """.trimIndent()

        val diagnostic = requireNotNull(
            EcjDiagnosticSanitizer.sanitize(
                raw = raw,
                succeeded = false,
                byteLimit = 1_024,
                privateFiles = listOf(workspace),
                sourceFile = source,
                sourceFileName = "Main.java",
            ),
        )

        assertEquals(JvmDiagnosticSeverity.ERROR, diagnostic.severity)
        assertEquals(4, diagnostic.line)
        assertTrue((diagnostic.column ?: 0) > 0)
        assertTrue(diagnostic.message.contains("Main.java"))
        assertTrue(diagnostic.message.contains("println2"))
        assertTrue(diagnostic.message.contains("undefined for the type PrintStream"))
        assertFalse(diagnostic.message.contains(workspace.absolutePath))
    }

    @Test
    fun multiByteDiagnosticRemainsInsideRequestedUtf8Budget() {
        val diagnostic = requireNotNull(
            EcjDiagnosticSanitizer.sanitize(
                "1. WARNING in Main.java (at line 1)\n" + "错".repeat(1_000),
                succeeded = true,
                byteLimit = 64,
                privateFiles = emptyList(),
            ),
        )

        assertTrue(diagnostic.message.toByteArray(Charsets.UTF_8).size <= 64)
    }

    @Test
    fun locationIsOmittedUnlessLineAndColumnAreBothAvailable() {
        val diagnostic = requireNotNull(
            EcjDiagnosticSanitizer.sanitize(
                "1. ERROR in Main.java (at line 9)\nNo caret is present",
                succeeded = false,
                byteLimit = 256,
                privateFiles = emptyList(),
            ),
        )

        assertEquals(null, diagnostic.line)
        assertEquals(null, diagnostic.column)
    }

    @Test
    fun providerTextCannotLeakPathsSignaturesBinderOrProcessIdentity() {
        val sensitive = """
            1. ERROR in C:\private\Main.java (at line 12)
                bad();
                ^
            classpath=/data/user/0/provider/files/android.jar
            signer=${"a".repeat(64)} Binder@42 uid=1000 pid=2000
            component=org.example.provider/.CompilerService
        """.trimIndent()

        val diagnostic = requireNotNull(
            EcjDiagnosticSanitizer.sanitize(
                sensitive,
                succeeded = false,
                byteLimit = 256,
                privateFiles = emptyList(),
            ),
        )

        assertEquals(12, diagnostic.line)
        assertTrue((diagnostic.column ?: 0) > 0)
        assertEquals("ECJ_ERROR", diagnostic.code)
        assertEquals("Java compilation error", diagnostic.message)
        listOf("private", "classpath", "signer", "Binder", "uid", "pid", "component").forEach {
            assertFalse(it in diagnostic.message)
        }
    }
}
