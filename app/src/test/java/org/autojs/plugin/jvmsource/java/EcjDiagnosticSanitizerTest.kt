package org.autojs.plugin.jvmsource.java

import org.eclipse.jdt.core.compiler.batch.BatchCompiler
import org.autojs.plugin.jvmsource.api.JvmDiagnosticSeverity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.PrintWriter
import java.io.StringWriter

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
        assertTrue(diagnostic.message.contains("The method bad() is undefined"))
        assertTrue(diagnostic.message.contains("<redacted>"))
        assertFalse(diagnostic.message == "Java compilation error")
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
    fun mixedSensitiveContentRetainsEveryActionableSegment() {
        val workspace = temporaryFolder.newFolder("provider-private-mixed-root")
        val source = workspace.resolve("Main.java")
        val androidJar = workspace.resolve("android.jar")
        val digest = "a".repeat(64)
        val raw = """
            ----------
            1. ERROR in ${source.absolutePath} (at line 6)
                    callMissing();
                    ^^^^^^^^^^^
            The method callMissing() is undefined for the type Main
            Provider detail: classpath=${androidJar.absolutePath}
            Resolver metadata: signer=$digest Binder@42 uid=1000 pid=2000
            Try declaring callMissing() or correcting the invocation.
            ----------
        """.trimIndent()

        val diagnostic = requireNotNull(
            EcjDiagnosticSanitizer.sanitize(
                raw = raw,
                succeeded = false,
                byteLimit = 2_048,
                privateFiles = listOf(workspace, androidJar),
                sourceFile = source,
                sourceFileName = "Main.java",
            ),
        )

        val actionableSegments = listOf(
            "1. ERROR in Main.java (at line 6)",
            "callMissing();",
            "The method callMissing() is undefined for the type Main",
            "Provider detail: <redacted>",
            "Resolver metadata: <redacted>",
            "Try declaring callMissing() or correcting the invocation.",
        )
        val retainedSegments = actionableSegments.count(diagnostic.message::contains)
        assertEquals("Every non-sensitive segment must survive redaction", actionableSegments.size, retainedSegments)
        assertFalse(diagnostic.message.contains(workspace.absolutePath))
        assertFalse(diagnostic.message.contains(androidJar.absolutePath))
        assertFalse(diagnostic.message.contains(digest))
        listOf("classpath=", "signer=", "Binder@", "uid=", "pid=").forEach {
            assertFalse(it in diagnostic.message)
        }
    }

    @Test
    fun multiErrorSourceProducesDistinctOrderedDiagnostics() {
        val workspace = temporaryFolder.newFolder("ecj-multiple-diagnostics")
        val source = workspace.resolve("Main.java").apply {
            writeText(
                """
                    public final class Main {
                        public Object run() {
                            int first = missingFirst;
                            int second = missingSecond;
                            return first + second;
                        }
                    }
                """.trimIndent(),
                Charsets.UTF_8,
            )
        }
        val output = workspace.resolve("classes").apply { check(mkdir()) }
        val compilerOutput = StringWriter()
        val writer = PrintWriter(compilerOutput, true)

        val succeeded = BatchCompiler.compile(
            arrayOf(
                "-source", "8",
                "-target", "8",
                "-proc:none",
                "-encoding", "UTF-8",
                "-d", output.absolutePath,
                source.absolutePath,
            ),
            writer,
            writer,
            null,
        )
        assertFalse("The intentionally invalid source unexpectedly compiled", succeeded)

        val diagnostics = EcjDiagnosticSanitizer.sanitizeAll(
            raw = compilerOutput.toString(),
            succeeded = false,
            byteLimit = 4_096,
            privateFiles = listOf(workspace, output),
            sourceFile = source,
            sourceFileName = "Main.java",
        )

        assertEquals(2, diagnostics.size)
        assertEquals(listOf(3, 4), diagnostics.map { it.line })
        assertTrue(diagnostics[0].message.contains("missingFirst"))
        assertFalse(diagnostics[0].message.contains("missingSecond"))
        assertTrue(diagnostics[1].message.contains("missingSecond"))
        assertFalse(diagnostics[1].message.contains("missingFirst"))
        diagnostics.forEach {
            assertEquals(JvmDiagnosticSeverity.ERROR, it.severity)
            assertEquals("ECJ_ERROR", it.code)
            assertTrue((it.column ?: 0) > 0)
            assertFalse(it.message.contains(workspace.absolutePath))
        }
    }

    @Test
    fun multiFileDiagnosticsRetainOnlyCanonicalLogicalSourcePaths() {
        val workspace = temporaryFolder.newFolder("ecj-package-diagnostics")
        val main = workspace.resolve("sources/demo/Main.java")
        val helper = workspace.resolve("sources/demo/Helper.java")
        val raw = """
            1. ERROR in ${helper.absolutePath} (at line 3)
                missingHelper();
                ^^^^^^^^^^^^^
            The method missingHelper() is undefined
            ----------
            2. ERROR in ${main.absolutePath} (at line 7)
                missingMain();
                ^^^^^^^^^^^
            The method missingMain() is undefined
        """.trimIndent()

        val diagnostics = EcjDiagnosticSanitizer.sanitizeAll(
            raw = raw,
            succeeded = false,
            byteLimit = 4_096,
            privateFiles = listOf(workspace),
            sourceFiles = linkedMapOf(
                helper to "demo/Helper.java",
                main to "demo/Main.java",
            ),
        )

        assertEquals(listOf("demo/Helper.java", "demo/Main.java"), diagnostics.map { it.sourceFileName })
        assertTrue(diagnostics[0].message.contains("demo/Helper.java"))
        assertTrue(diagnostics[1].message.contains("demo/Main.java"))
        diagnostics.forEach { assertFalse(it.message.contains(workspace.absolutePath)) }
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
        assertTrue(diagnostic.message.contains("bad();"))
        assertTrue(diagnostic.message.contains("<redacted>"))
        assertFalse(diagnostic.message == "Java compilation error")
        listOf("private", "classpath", "signer", "Binder", "uid", "pid", "component").forEach {
            assertFalse(it in diagnostic.message)
        }
    }
}
