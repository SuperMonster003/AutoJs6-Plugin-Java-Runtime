package org.autojs.plugin.jvmsource.java.worker

import org.autojs.plugin.jvmsource.api.IJvmHostBridge
import org.autojs.plugin.jvmsource.api.IJvmHostBridgeCallback
import org.autojs.plugin.jvmsource.api.JvmCancellationException
import org.autojs.plugin.jvmsource.api.JvmCancellationReason
import org.autojs.plugin.jvmsource.api.JvmClipboardPayload
import org.autojs.plugin.jvmsource.api.JvmHostCall
import org.autojs.plugin.jvmsource.api.JvmHostResponse
import org.autojs.plugin.jvmsource.api.JvmProtocolVersion
import org.autojs.plugin.jvmsource.api.JvmRequestId
import org.autojs.plugin.jvmsource.api.JvmScriptCapability
import org.autojs.plugin.jvmsource.api.JvmSha256
import org.autojs.plugin.jvmsource.api.JvmSourceContract
import org.autojs.plugin.jvmsource.api.JvmSourceLanguage
import org.autojs.plugin.jvmsource.api.JvmSourceCodec
import org.autojs.plugin.jvmsource.api.JvmSourceRequest
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class RemoteJvmScriptContextTest {
    @Test
    fun separatelyGrantedClipboardReadAndWriteRoundTripThroughCanonicalWireCalls() {
        var clipboard = "before"
        val calls = mutableListOf<JvmHostCall>()
        val bridge = respondingBridge { call ->
            calls += call
            when (call.method) {
                "clipboard.get" -> {
                    JvmClipboardPayload.validateGetRequest(call.payloadJson)
                    JvmHostResponse(call.requestId, call.callId, true, JvmClipboardPayload.encodeText(clipboard))
                }
                "clipboard.set" -> {
                    clipboard = JvmClipboardPayload.decodeText(call.payloadJson)
                    JvmHostResponse(call.requestId, call.callId, true, "true")
                }
                else -> error("Unexpected host method")
            }
        }
        val context = context(
            capabilities = listOf(
                JvmScriptCapability.CLIPBOARD_READ,
                JvmScriptCapability.CLIPBOARD_WRITE,
            ),
            bridge = bridge,
        )

        assertEquals("before", context.clipboard().getText())
        context.clipboard().setText("after 你好")

        assertEquals("after 你好", clipboard)
        assertEquals(listOf("clipboard.get", "clipboard.set"), calls.map(JvmHostCall::method))
        assertTrue(calls[0].payloadJson == JvmClipboardPayload.GET_REQUEST_JSON)
    }

    @Test
    fun clipboardReadAndWriteAreDeniedIndependentlyBeforeDispatch() {
        var dispatches = 0
        val bridge = respondingBridge { call ->
            dispatches += 1
            JvmHostResponse(call.requestId, call.callId, true, "true")
        }
        val writeOnly = context(listOf(JvmScriptCapability.CLIPBOARD_WRITE), bridge = bridge)
        val readOnly = context(listOf(JvmScriptCapability.CLIPBOARD_READ), bridge = bridge)

        assertThrows(IllegalArgumentException::class.java) { writeOnly.clipboard().getText() }
        assertThrows(IllegalArgumentException::class.java) { readOnly.clipboard().setText("blocked") }
        assertEquals(0, dispatches)
    }

    @Test
    fun malformedClipboardResponseIsRejectedWithoutExposingItAsScriptText() {
        val bridge = respondingBridge { call ->
            JvmHostResponse(call.requestId, call.callId, true, "{\"text\":\"\\u0041\"}")
        }
        val context = context(listOf(JvmScriptCapability.CLIPBOARD_READ), bridge = bridge)

        val error = assertThrows(IllegalStateException::class.java) { context.clipboard().getText() }
        assertEquals("Host returned an invalid clipboard.get response", error.message)
    }

    @Test
    fun consoleWritesUtf8LinesToOwnedWorkerStreams() {
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        val context = context(
            capabilities = listOf(JvmScriptCapability.CONSOLE_STREAM),
            stdout = stdout,
            stderr = stderr,
        )

        context.console().log("M5 log 你好")
        context.console().error("M5 error 错误")

        assertEquals("M5 log 你好${System.lineSeparator()}", stdout.toString(Charsets.UTF_8.name()))
        assertEquals("M5 error 错误${System.lineSeparator()}", stderr.toString(Charsets.UTF_8.name()))
    }

    @Test
    fun sleepIsInterruptedByWorkerCancellation() {
        val cancellation = WorkerCancellation().also { it.attach(Thread.currentThread()) }
        val context = context(
            capabilities = listOf(JvmScriptCapability.SLEEP),
            cancellation = cancellation,
        )
        val canceller = Thread {
            Thread.sleep(30L)
            cancellation.cancel(JvmCancellationReason.REQUESTED)
        }.also(Thread::start)

        assertThrows(JvmCancellationException::class.java) { context.sleep(5_000L) }
        canceller.join()
    }

    @Test
    fun ungrantedCapabilityFailsBeforeAnyHostDispatch() {
        val context = context(capabilities = listOf(JvmScriptCapability.SLEEP))

        assertThrows(IllegalArgumentException::class.java) { context.toast("not granted") }
    }

    @Test
    fun exposesADeeplyImmutableRequestArgumentSnapshotWithoutHostDispatch() {
        val context = context(
            capabilities = emptyList(),
            argsJson = "{\"count\":2,\"nested\":{\"items\":[\"before\",null]}}",
        )

        assertEquals(2L, context.args()["count"])
        val nested = context.args()["nested"] as Map<*, *>
        assertEquals(listOf("before", null), nested["items"])
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (context.args() as MutableMap<String, Any?>)["later"] = true
        }
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            ((nested["items"] as List<Any?>) as MutableList<Any?>).add("later")
        }
    }

    private fun context(
        capabilities: List<JvmScriptCapability>,
        argsJson: String = "{}",
        cancellation: WorkerCancellation = WorkerCancellation(),
        stdout: ByteArrayOutputStream = ByteArrayOutputStream(),
        stderr: ByteArrayOutputStream = ByteArrayOutputStream(),
        bridge: IJvmHostBridge = unexpectedBridge(),
    ): RemoteJvmScriptContext = RemoteJvmScriptContext(
        request = request(capabilities, argsJson),
        bridge = bridge,
        workerCancellation = cancellation,
        expectedCompilerPid = 0,
        expectedCompilerUid = 0,
        stdout = PrintStream(stdout, true, Charsets.UTF_8.name()),
        stderr = PrintStream(stderr, true, Charsets.UTF_8.name()),
    )

    private fun respondingBridge(response: (JvmHostCall) -> JvmHostResponse): IJvmHostBridge =
        object : IJvmHostBridge.Stub() {
            override fun dispatch(request: ByteArray?, callback: IJvmHostBridgeCallback?) {
                val call = JvmSourceCodec.decodeHostCall(requireNotNull(request))
                requireNotNull(callback).onResponse(JvmSourceCodec.encodeHostResponse(response(call)))
            }

            override fun destroy(reason: ByteArray?) = Unit
        }

    private fun unexpectedBridge(): IJvmHostBridge = object : IJvmHostBridge.Stub() {
        override fun dispatch(request: ByteArray?, callback: IJvmHostBridgeCallback?) {
            error("Host dispatch was not expected")
        }

        override fun destroy(reason: ByteArray?) = Unit
    }

    private fun request(
        capabilities: List<JvmScriptCapability>,
        argsJson: String,
    ): JvmSourceRequest {
        val source = "class Main".toByteArray()
        return JvmSourceRequest(
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
            expectedToolchainFingerprint = JvmSha256.digest("toolchain".toByteArray()),
            entryClassName = "Main",
            minApi = JvmSourceContract.MIN_ANDROID_API,
            timeoutMillis = JvmSourceContract.DEFAULT_TIMEOUT_MILLIS,
            maxStdoutBytes = JvmSourceContract.MAX_STDOUT_BYTES,
            maxStderrBytes = JvmSourceContract.MAX_STDERR_BYTES,
            diagnosticByteLimit = JvmSourceContract.MAX_DIAGNOSTIC_BYTES,
            allowedHostCalls = capabilities.mapNotNull(JvmScriptCapability::hostMethod),
            grantedCapabilities = capabilities,
        )
    }
}
