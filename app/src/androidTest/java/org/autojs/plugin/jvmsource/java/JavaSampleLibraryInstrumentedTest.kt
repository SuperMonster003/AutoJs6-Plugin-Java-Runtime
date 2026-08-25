package org.autojs.plugin.jvmsource.java

import android.os.ParcelFileDescriptor
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.plugin.jvmsource.api.AutoJsJvmEntry
import org.autojs.plugin.jvmsource.api.IJvmHostBridge
import org.autojs.plugin.jvmsource.api.IJvmHostBridgeCallback
import org.autojs.plugin.jvmsource.api.JvmAppApi
import org.autojs.plugin.jvmsource.api.JvmCancellation
import org.autojs.plugin.jvmsource.api.JvmCancellationException
import org.autojs.plugin.jvmsource.api.JvmCancellationReason
import org.autojs.plugin.jvmsource.api.JvmClipboardApi
import org.autojs.plugin.jvmsource.api.JvmClipboardPayload
import org.autojs.plugin.jvmsource.api.JvmConsoleApi
import org.autojs.plugin.jvmsource.api.JvmHostCall
import org.autojs.plugin.jvmsource.api.JvmHostResponse
import org.autojs.plugin.jvmsource.api.JvmProtocolVersion
import org.autojs.plugin.jvmsource.api.JvmRequestId
import org.autojs.plugin.jvmsource.api.JvmScriptCapability
import org.autojs.plugin.jvmsource.api.JvmScriptContext
import org.autojs.plugin.jvmsource.api.JvmSha256
import org.autojs.plugin.jvmsource.api.JvmSourceContract
import org.autojs.plugin.jvmsource.api.JvmSourceCodec
import org.autojs.plugin.jvmsource.api.JvmSourceErrorCode
import org.autojs.plugin.jvmsource.api.JvmSourceLanguage
import org.autojs.plugin.jvmsource.api.JvmSourceRequest
import org.autojs.plugin.jvmsource.java.worker.RemoteJvmScriptContext
import org.autojs.plugin.jvmsource.java.worker.WorkerCancellation
import org.autojs.plugin.jvmsource.java.worker.WorkerDexLoader
import org.autojs.plugin.jvmsource.java.worker.WorkerEntryFactory
import org.autojs.plugin.jvmsource.java.worker.WorkerJsonValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.io.PrintStream
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Device evidence for repository samples. Canonical host-to-provider Binder evidence remains in the
 * host repository; this suite exercises the real provider compiler, verifier, ART loader, context,
 * cancellation, and result encoder without weakening the production caller boundary.
 */
@RunWith(AndroidJUnit4::class)
class JavaSampleLibraryInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val targetContext = instrumentation.targetContext.applicationContext
    private val environment by lazy { JavaProviderEnvironment.get(targetContext) }

    @Test
    fun legacyNoArgsReturnValuesSampleStillCompilesAgainstEntryApi4AndRunsThroughArt() {
        withCompiledEntry(RETURN_VALUES_SAMPLE) { entry, _ ->
            assertEquals(EXPECTED_RETURN_JSON, WorkerJsonValue.encode(entry.run(NoCallsContext)))
        }
    }

    @Test
    fun scriptArgumentsSampleReadsTheProtocol13SnapshotThroughEntryApi4() {
        withCompiledEntry(SCRIPT_ARGS_SAMPLE) { entry, sourceBytes ->
            val context = RemoteJvmScriptContext(
                request = request(
                    source = sourceBytes,
                    capabilities = emptyList(),
                    argsJson = SCRIPT_ARGS_JSON,
                ),
                bridge = RejectingHostBridge,
                workerCancellation = WorkerCancellation(),
                expectedCompilerPid = Process.myPid(),
                expectedCompilerUid = Process.myUid(),
                stdout = PrintStream(ByteArrayOutputStream()),
                stderr = PrintStream(ByteArrayOutputStream()),
            )

            assertEquals(EXPECTED_SCRIPT_ARGS_RESULT_JSON, WorkerJsonValue.encode(entry.run(context)))
        }
    }

    @Test
    fun coreLibraryDesugaringSampleRunsJavaTimeAndEnhancedStreamThroughArt() {
        withCompiledEntry(CORE_LIBRARY_DESUGARING_SAMPLE) { entry, _ ->
            assertEquals("2024-03-01:CORE", entry.run(NoCallsContext))
        }
    }

    @Test
    fun clipboardCapabilitySet2SampleRunsThroughArtAndRestoresHostText() {
        withCompiledEntry(CLIPBOARD_SAMPLE) { entry, sourceBytes ->
            val stdout = ByteArrayOutputStream()
            val bridge = ClipboardHostBridge("device-before")
            val context = RemoteJvmScriptContext(
                request = request(
                    sourceBytes,
                    listOf(
                        JvmScriptCapability.CLIPBOARD_READ,
                        JvmScriptCapability.CLIPBOARD_WRITE,
                        JvmScriptCapability.CONSOLE_STREAM,
                    ),
                ),
                bridge = bridge,
                workerCancellation = WorkerCancellation(),
                expectedCompilerPid = Process.myPid(),
                expectedCompilerUid = Process.myUid(),
                stdout = PrintStream(stdout, true, Charsets.UTF_8.name()),
                stderr = PrintStream(ByteArrayOutputStream(), true, Charsets.UTF_8.name()),
            )

            assertEquals(
                "{\"before\":\"device-before\",\"written\":\"M9 clipboard 你好\",\"restored\":true}",
                WorkerJsonValue.encode(entry.run(context)),
            )
            assertEquals("device-before", bridge.text)
            assertEquals(
                listOf("clipboard.get", "clipboard.set", "clipboard.get", "clipboard.set", "clipboard.get"),
                bridge.calls.map(JvmHostCall::method),
            )
            assertEquals(
                "clipboard round-trip: M9 clipboard 你好; restored=true${System.lineSeparator()}",
                stdout.toString(Charsets.UTF_8.name()),
            )
        }
    }

    @Test
    fun clipboardReadAndWriteRemainDefaultDeniedOnDevice() {
        val sourceBytes = "class Main".toByteArray()
        val bridge = ClipboardHostBridge("private")
        fun context(capabilities: List<JvmScriptCapability>) = RemoteJvmScriptContext(
            request = request(sourceBytes, capabilities),
            bridge = bridge,
            workerCancellation = WorkerCancellation(),
            expectedCompilerPid = Process.myPid(),
            expectedCompilerUid = Process.myUid(),
            stdout = PrintStream(ByteArrayOutputStream()),
            stderr = PrintStream(ByteArrayOutputStream()),
        )

        assertThrows(IllegalArgumentException::class.java) {
            context(listOf(JvmScriptCapability.CLIPBOARD_WRITE)).clipboard().getText()
        }
        assertThrows(IllegalArgumentException::class.java) {
            context(listOf(JvmScriptCapability.CLIPBOARD_READ)).clipboard().setText("blocked")
        }
        assertTrue(bridge.calls.isEmpty())
        assertEquals("private", bridge.text)
    }

    @Test
    fun unsupportedStreamOfNullableStaysOutsideTheCompileTimeProfile() {
        PrivateSessionWorkspace.create(targetContext, "Main.java").use { workspace ->
            writeSource(workspace, UNSUPPORTED_STREAM_SOURCE.toByteArray(Charsets.UTF_8))
            val ecj = EcjJavaCompiler(environment.compilerClasspath).compile(
                sourceFile = workspace.sourceFile,
                outputDirectory = workspace.classesDirectory,
                diagnosticByteLimit = JvmSourceContract.MAX_DIAGNOSTIC_BYTES,
                ensureActive = {},
            )

            assertFalse("Unsupported Stream.ofNullable unexpectedly compiled", ecj.succeeded)
            assertTrue(ecj.diagnostics.contains("ofNullable"))
        }
    }

    @Test
    fun cancellationSampleSleepIsInterruptedBeforeUnexpectedCompletion() {
        withCompiledEntry(CANCELLATION_SAMPLE) { entry, sourceBytes ->
            val stdout = LineSignalingOutputStream()
            val stderr = ByteArrayOutputStream()
            val cancellation = WorkerCancellation()
            val failure = AtomicReference<Throwable?>()
            val finished = CountDownLatch(1)
            val context = RemoteJvmScriptContext(
                request = request(
                    sourceBytes,
                    listOf(JvmScriptCapability.CONSOLE_STREAM, JvmScriptCapability.SLEEP),
                ),
                bridge = RejectingHostBridge,
                workerCancellation = cancellation,
                expectedCompilerPid = 1,
                expectedCompilerUid = 1,
                stdout = PrintStream(stdout, true, Charsets.UTF_8.name()),
                stderr = PrintStream(stderr, true, Charsets.UTF_8.name()),
            )
            val executionThread = Thread {
                cancellation.attach(Thread.currentThread())
                try {
                    entry.run(context)
                } catch (error: Throwable) {
                    failure.set(error)
                } finally {
                    finished.countDown()
                }
            }

            executionThread.start()
            assertTrue("Cancellation sample did not reach its sleep", stdout.firstLine.await(5, TimeUnit.SECONDS))
            cancellation.cancel(JvmCancellationReason.REQUESTED)
            assertTrue(cancellation.isCancellationRequested())
            assertTrue("Cancellation sample did not stop", finished.await(5, TimeUnit.SECONDS))
            executionThread.join(5_000L)

            assertFalse(executionThread.isAlive)
            assertTrue(failure.get() is JvmCancellationException)
            val stdoutText = stdout.toString(Charsets.UTF_8.name())
            assertEquals("cancellation sample: sleeping" + System.lineSeparator(), stdoutText)
            assertFalse(stdoutText.contains("unexpected completion"))
            assertEquals("", stderr.toString(Charsets.UTF_8.name()))
        }
    }

    @Test
    fun compileErrorSampleProducesTheDocumentedSafeDiagnostic() {
        val sourceBytes = sampleBytes(COMPILE_ERROR_SAMPLE)
        PrivateSessionWorkspace.create(targetContext, "Main.java").use { workspace ->
            writeSource(workspace, sourceBytes)
            val ecj = EcjJavaCompiler(environment.compilerClasspath).compile(
                sourceFile = workspace.sourceFile,
                outputDirectory = workspace.classesDirectory,
                diagnosticByteLimit = JvmSourceContract.MAX_DIAGNOSTIC_BYTES,
                ensureActive = {},
            )
            assertFalse("The intentional compile-error sample unexpectedly compiled", ecj.succeeded)
            val diagnostic = EcjDiagnosticSanitizer.sanitize(
                raw = ecj.diagnostics,
                succeeded = ecj.succeeded,
                byteLimit = JvmSourceContract.MAX_DIAGNOSTIC_BYTES,
                privateFiles = listOf(
                    workspace.classesDirectory,
                    workspace.programJar,
                    workspace.d8OutputDirectory,
                    *environment.compilerClasspath.installedFiles.toTypedArray(),
                ),
                sourceFile = workspace.sourceFile,
                sourceFileName = "Main.java",
            )
            assertNotNull(diagnostic)
            val safeDiagnostic = checkNotNull(diagnostic)
            assertEquals("ECJ_ERROR", safeDiagnostic.code)
            assertEquals(7, safeDiagnostic.line)
            assertTrue(safeDiagnostic.column != null && safeDiagnostic.column!! > 0)
            assertTrue(safeDiagnostic.message.contains("missingSymbol"))
            assertFalse(safeDiagnostic.message.contains(checkNotNull(workspace.sourceFile.parentFile).absolutePath))
        }
    }

    @Test
    fun runtimeExceptionSampleProjectsOnlyThePlatformClassAndRequestedSourceLine() {
        withCompiledEntry(RUNTIME_EXCEPTION_SAMPLE) { entry, _ ->
            val failure = assertThrows(IllegalStateException::class.java) {
                entry.run(NoCallsContext)
            }

            val diagnostic = JavaRuntimeDiagnosticPolicy.extract(failure)

            assertEquals(JavaRuntimeDiagnostic(7, "java.lang.IllegalStateException"), diagnostic)
            assertFalse(diagnostic.toString().contains("M9-3 secret must never cross"))
        }
    }

    @Test
    fun resultLimitSampleRunsButIsRejectedByTheJsonBudget() {
        withCompiledEntry(RESULT_LIMIT_SAMPLE) { entry, _ ->
            val error = assertThrows(JavaProviderFailure::class.java) {
                WorkerJsonValue.encode(entry.run(NoCallsContext))
            }
            assertEquals(JvmSourceErrorCode.EXECUTION_FAILED, error.code)
        }
    }

    private fun <T> withCompiledEntry(
        sampleName: String,
        block: (AutoJsJvmEntry, ByteArray) -> T,
    ): T {
        val sourceBytes = sampleBytes(sampleName)
        return PrivateSessionWorkspace.create(targetContext, "Main.java").use { workspace ->
            writeSource(workspace, sourceBytes)
            val ecj = EcjJavaCompiler(environment.compilerClasspath).compile(
                sourceFile = workspace.sourceFile,
                outputDirectory = workspace.classesDirectory,
                diagnosticByteLimit = JvmSourceContract.MAX_DIAGNOSTIC_BYTES,
                ensureActive = {},
            )
            assertTrue("ECJ failed for " + sampleName + ": " + ecj.diagnostics, ecj.succeeded)
            val classes = UserClassJarWriter.write(
                workspace.classesDirectory,
                workspace.programJar,
            )
            val dexFiles = D8JavaCompiler(environment.d8RuntimeLibraries).compile(
                programJar = workspace.programJar,
                outputDirectory = workspace.d8OutputDirectory,
                minApi = JvmSourceContract.MIN_ANDROID_API,
                ensureActive = {},
            )
            val dexIdentity = ProviderDexSetIdentity.fromFiles(dexFiles)
            dexFiles.forEach { dexFile ->
                assertTrue("Generated ${dexFile.name} could not be made read-only", dexFile.setReadOnly())
            }
            val loader = WorkerDexLoader(targetContext)
            val validated = loader.validateStructure(
                descriptors = dexFiles.map { ParcelFileDescriptor.open(it, ParcelFileDescriptor.MODE_READ_ONLY) },
                expectedIdentity = dexIdentity,
                expectedClassDescriptors = classes.dexDescriptors,
                requestMinApi = JvmSourceContract.MIN_ANDROID_API,
                ensureActive = {},
            )
            loader.createClassLoader(
                validated = validated,
                generation = 1L,
                requestId = UUID.randomUUID().toString(),
                parent = AutoJsJvmEntry::class.java.classLoader!!,
            ).use { loaded ->
                val entry = WorkerEntryFactory.instantiate(
                    WorkerEntryFactory.loadFromArt(loaded.classLoader),
                )
                block(entry, sourceBytes)
            }
        }
    }

    private fun sampleBytes(name: String): ByteArray =
        instrumentation.context.assets.open(name).use { it.readBytes() }

    private fun writeSource(workspace: PrivateSessionWorkspace, bytes: ByteArray) {
        FileOutputStream(workspace.sourceFile).use { output ->
            output.write(bytes)
            output.fd.sync()
        }
    }

    private fun request(
        source: ByteArray,
        capabilities: List<JvmScriptCapability>,
        argsJson: String = "{}",
    ) =
        JvmSourceRequest(
            requestId = JvmRequestId.fromUuid(UUID.randomUUID()),
            protocolVersion = JvmProtocolVersion(
                JvmSourceContract.PROTOCOL_MAJOR,
                JvmSourceContract.PROTOCOL_MINOR,
            ),
            language = JvmSourceLanguage.JAVA,
            argsJson = argsJson,
            sourceFileName = "Main.java",
            sourceSizeBytes = source.size.toLong(),
            sourceSha256 = JvmSha256.digest(source),
            expectedToolchainFingerprint = JvmSha256.digest("sample-toolchain".toByteArray()),
            entryClassName = "Main",
            minApi = JvmSourceContract.MIN_ANDROID_API,
            timeoutMillis = JvmSourceContract.DEFAULT_TIMEOUT_MILLIS,
            maxStdoutBytes = JvmSourceContract.MAX_STDOUT_BYTES,
            maxStderrBytes = JvmSourceContract.MAX_STDERR_BYTES,
            diagnosticByteLimit = JvmSourceContract.MAX_DIAGNOSTIC_BYTES,
            allowedHostCalls = capabilities.mapNotNull(JvmScriptCapability::hostMethod),
            grantedCapabilities = capabilities,
        )

    private object RejectingHostBridge : IJvmHostBridge.Stub() {
        override fun dispatch(request: ByteArray?, callback: IJvmHostBridgeCallback?) {
            error("Sample must not dispatch a host call")
        }

        override fun destroy(reason: ByteArray?) = Unit
    }

    private object NoCallsContext : JvmScriptContext {
        private val appApi = object : JvmAppApi {
            override fun launch(packageName: String): Boolean = kotlin.error("Unexpected app.launch")
        }
        private val consoleApi = object : JvmConsoleApi {
            override fun log(message: String): Unit = kotlin.error("Unexpected console.log")

            override fun error(message: String): Unit = kotlin.error("Unexpected console.error")
        }
        private val cancellationView = object : JvmCancellation {
            override fun isCancellationRequested(): Boolean = false

            override fun throwIfCancellationRequested() = Unit
        }
        private val clipboardApi = object : JvmClipboardApi {
            override fun getText(): String = kotlin.error("Unexpected clipboard.get")

            override fun setText(text: String): Unit = kotlin.error("Unexpected clipboard.set")
        }

        override fun app(): JvmAppApi = appApi

        override fun args(): Map<String, Any?> = emptyMap()

        override fun clipboard(): JvmClipboardApi = clipboardApi

        override fun console(): JvmConsoleApi = consoleApi

        override fun cancellation(): JvmCancellation = cancellationView

        override fun sleep(millis: Long): Unit = kotlin.error("Unexpected sleep")

        override fun toast(message: String): Unit = kotlin.error("Unexpected toast")
    }

    private class ClipboardHostBridge(initialText: String) : IJvmHostBridge.Stub() {
        var text: String = initialText
        val calls = mutableListOf<JvmHostCall>()

        override fun dispatch(request: ByteArray?, callback: IJvmHostBridgeCallback?) {
            val call = JvmSourceCodec.decodeHostCall(requireNotNull(request))
            calls += call
            val response = when (call.method) {
                "clipboard.get" -> {
                    JvmClipboardPayload.validateGetRequest(call.payloadJson)
                    JvmHostResponse(call.requestId, call.callId, true, JvmClipboardPayload.encodeText(text))
                }
                "clipboard.set" -> {
                    text = JvmClipboardPayload.decodeText(call.payloadJson)
                    JvmHostResponse(call.requestId, call.callId, true, "true")
                }
                else -> error("Unexpected host method ${call.method}")
            }
            requireNotNull(callback).onResponse(JvmSourceCodec.encodeHostResponse(response))
        }

        override fun destroy(reason: ByteArray?) = Unit
    }

    private class LineSignalingOutputStream : ByteArrayOutputStream() {
        val firstLine = CountDownLatch(1)

        @Synchronized
        override fun write(value: Int) {
            super.write(value)
            if (value == '\n'.code) firstLine.countDown()
        }

        @Synchronized
        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            super.write(buffer, offset, length)
            if ((offset until offset + length).any { buffer[it] == '\n'.code.toByte() }) {
                firstLine.countDown()
            }
        }
    }

    private companion object {
        const val CANCELLATION_SAMPLE = "cancellation-sleep.java"
        const val CLIPBOARD_SAMPLE = "capability-set-2-clipboard.java"
        const val CORE_LIBRARY_DESUGARING_SAMPLE = "core-library-desugaring.java"
        const val RETURN_VALUES_SAMPLE = "return-values.java"
        const val SCRIPT_ARGS_SAMPLE = "script-args.java"
        const val COMPILE_ERROR_SAMPLE = "compile-error.java"
        const val RESULT_LIMIT_SAMPLE = "limit-result-json.java"
        const val RUNTIME_EXCEPTION_SAMPLE = "runtime-exception.java"
        const val SCRIPT_ARGS_JSON =
            "{\"enabled\":true,\"name\":\"AutoJs6\",\"nested\":{\"count\":3}," +
                "\"nullable\":null,\"tags\":[\"java\",\"m9\"]}"
        const val EXPECTED_SCRIPT_ARGS_RESULT_JSON =
            "{\"name\":\"AutoJs6\",\"enabled\":true,\"count\":3,\"firstTag\":\"java\"," +
                "\"tagCount\":2,\"nullValue\":null}"
        val UNSUPPORTED_STREAM_SOURCE = """
            import java.util.stream.Stream;
            import org.autojs.plugin.jvmsource.api.AutoJsJvmEntry;
            import org.autojs.plugin.jvmsource.api.JvmScriptContext;

            public final class Main implements AutoJsJvmEntry {
                @Override
                public Object run(JvmScriptContext context) {
                    return Stream.ofNullable("unsupported").toList();
                }
            }
        """.trimIndent()
        const val EXPECTED_RETURN_JSON =
            "{\"nullValue\":null,\"boolean\":true,\"integers\":[1,2,3,4,12345678901234567890]," +
                "\"decimals\":[1.25,2.5,3.75],\"text\":\"hello\",\"character\":\"中\"," +
                "\"primitiveArray\":[5,6],\"objectArray\":[\"x\",false]," +
                "\"nested\":{\"items\":[7,8]}}"
    }
}
