package org.autojs.plugin.jvmsource.java

import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.plugin.jvmsource.api.AutoJsJvmEntry
import org.autojs.plugin.jvmsource.api.JvmAppApi
import org.autojs.plugin.jvmsource.api.JvmCancellation
import org.autojs.plugin.jvmsource.api.JvmClipboardApi
import org.autojs.plugin.jvmsource.api.JvmConsoleApi
import org.autojs.plugin.jvmsource.api.JvmScriptContext
import org.autojs.plugin.jvmsource.java.worker.WorkerDexLoader
import org.autojs.plugin.jvmsource.java.worker.WorkerEntryFactory
import org.eclipse.jdt.core.compiler.batch.BatchCompiler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.util.UUID

/** M10-1 spike: proves ECJ itself and selected post-Java-8 class files on real ART. */
@RunWith(AndroidJUnit4::class)
class EcjUpgradeSpikeInstrumentedTest {
    @Test
    fun ecj342RunsJava8PipelineOnArt() = runPipeline(
        sourceLevel = "8",
        expectedClassMajor = 52,
        body = "int base = 40; return base + 2;",
    )

    @Test
    fun ecj342Source11Target11RunsThroughD8OnArt() = runPipeline(
        sourceLevel = "11",
        expectedClassMajor = 55,
        body = "var base = 40; return base + 2;",
    )

    @Test
    fun ecj342Source17Target17RunsThroughD8OnArt() = runPipeline(
        sourceLevel = "17",
        expectedClassMajor = 61,
        body = """
            Object candidate = "ok";
            int length = candidate instanceof String text ? text.length() : 0;
            int base = switch (length) { case 2 -> 40; default -> 0; };
            return base + 2;
        """.trimIndent(),
    )

    private fun runPipeline(sourceLevel: String, expectedClassMajor: Int, body: String) {
        assertEquals("3.42.0", BuildConfig.ECJ_VERSION)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val environment = JavaProviderEnvironment.get(context)
        val source = """
            public final class Main implements org.autojs.plugin.jvmsource.api.AutoJsJvmEntry {
                @Override
                public Object run(org.autojs.plugin.jvmsource.api.JvmScriptContext context) {
                    $body
                }
            }
        """.trimIndent()
        val normalizedSource = JavaSourcePolicy.decodeAndValidate(
            bytes = source.toByteArray(Charsets.UTF_8),
            sourceFileName = "Main.java",
            entryClassName = "Main",
        )

        PrivateSessionWorkspace.create(context, "Main.java").use { workspace ->
            FileOutputStream(workspace.sourceFile).use { output ->
                output.write(normalizedSource.toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            val diagnostics = StringWriter()
            val diagnosticWriter = PrintWriter(diagnostics, true)
            val compilerClasspath = if (sourceLevel == "8") {
                environment.compilerClasspath.ecjClasspath
            } else {
                environment.compilerClasspath.ecjBootClasspath +
                    File.pathSeparator + environment.compilerClasspath.ecjClasspath
            }
            val arguments = mutableListOf(
                "-source", sourceLevel,
                "-target", sourceLevel,
                "-proc:none",
                "-encoding", "UTF-8",
                "-g:lines,vars,source",
                "-classpath", compilerClasspath,
            )
            if (sourceLevel == "8") {
                arguments += listOf(
                    "-bootclasspath", environment.compilerClasspath.ecjBootClasspath,
                )
            }
            arguments += listOf(
                "-d", workspace.classesDirectory.absolutePath,
                workspace.sourceFile.absolutePath,
            )
            val succeeded = BatchCompiler.compile(
                arguments.toTypedArray(),
                diagnosticWriter,
                diagnosticWriter,
                null,
            )
            diagnosticWriter.flush()
            assertTrue("ECJ $sourceLevel failed on ART: $diagnostics", succeeded)

            val mainClass = File(workspace.classesDirectory, "Main.class")
            assertTrue("ECJ omitted Main.class", mainClass.isFile)
            assertEquals(expectedClassMajor, classMajor(mainClass.readBytes()))
            val classes = UserClassJarWriter.write(
                workspace.classesDirectory,
                workspace.programJar,
                "Main",
            )
            val dexFiles = D8JavaCompiler(environment.d8RuntimeLibraries).compile(
                workspace.programJar,
                workspace.d8OutputDirectory,
                minApi = 24,
                ensureActive = {},
            )
            val dexIdentity = ProviderDexSetIdentity.fromFiles(dexFiles)
            dexFiles.forEach { dex -> assertTrue("Unable to freeze ${dex.name}", dex.setReadOnly()) }
            val descriptors = dexFiles.map { dex ->
                ParcelFileDescriptor.open(dex, ParcelFileDescriptor.MODE_READ_ONLY)
            }
            val loader = WorkerDexLoader(context)
            val validated = loader.validateStructure(
                descriptors = descriptors,
                expectedIdentity = dexIdentity,
                expectedClassDescriptors = classes.dexDescriptors,
                requestMinApi = 24,
                ensureActive = {},
            )
            loader.createClassLoader(
                validated = validated,
                generation = 1L,
                requestId = UUID.randomUUID().toString(),
                parent = AutoJsJvmEntry::class.java.classLoader!!,
            ).use { loaded ->
                val entry = WorkerEntryFactory.instantiate(
                    WorkerEntryFactory.loadFromArt(loaded.classLoader, "Main"),
                )
                assertEquals(42, entry.run(NoHostCallsContext))
            }
        }
    }

    private fun classMajor(bytes: ByteArray): Int {
        require(bytes.size >= 8 && bytes.copyOfRange(0, 4).contentEquals(MAGIC))
        return ((bytes[6].toInt() and 0xff) shl 8) or (bytes[7].toInt() and 0xff)
    }

    private object NoHostCallsContext : JvmScriptContext {
        private val app = object : JvmAppApi {
            override fun launch(packageName: String): Boolean = error("Unexpected host call")
        }
        private val cancellation = object : JvmCancellation {
            override fun isCancellationRequested(): Boolean = false
            override fun throwIfCancellationRequested() = Unit
        }
        private val clipboard = object : JvmClipboardApi {
            override fun getText(): String = error("Unexpected host call")
            override fun setText(text: String) = error("Unexpected host call")
        }

        override fun app(): JvmAppApi = app
        override fun args(): Map<String, Any?> = emptyMap()
        override fun clipboard(): JvmClipboardApi = clipboard
        override fun console(): JvmConsoleApi = error("Unexpected host call")
        override fun cancellation(): JvmCancellation = cancellation
        override fun sleep(millis: Long) = error("Unexpected host call")
        override fun toast(message: String) = error("Unexpected host call")
    }

    private companion object {
        val MAGIC = byteArrayOf(0xca.toByte(), 0xfe.toByte(), 0xba.toByte(), 0xbe.toByte())
    }
}
