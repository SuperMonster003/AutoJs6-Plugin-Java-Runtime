package org.autojs.plugin.jvmsource.java.service

import org.autojs.plugin.jvmsource.api.JvmClipboardPayload
import org.autojs.plugin.jvmsource.api.JvmHostResponse
import org.autojs.plugin.jvmsource.api.JvmRequestId
import org.autojs.plugin.jvmsource.api.JvmSourceContract
import org.autojs.plugin.jvmsource.api.JvmToastPayload
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class JavaHostCapabilityWirePolicyTest {
    @Test
    fun hardcodedAllowlistAdmitsOnlyCanonicalKnownRequests() {
        assertTrue(
            JavaHostCapabilityWirePolicy.acceptsRequest(
                JavaHostCapabilityWirePolicy.METHOD_APP_LAUNCH,
                "{\"packageName\":\"com.example.app\"}",
            ),
        )
        assertTrue(
            JavaHostCapabilityWirePolicy.acceptsRequest(
                JavaHostCapabilityWirePolicy.METHOD_CLIPBOARD_GET,
                JvmClipboardPayload.GET_REQUEST_JSON,
            ),
        )
        assertTrue(
            JavaHostCapabilityWirePolicy.acceptsRequest(
                JavaHostCapabilityWirePolicy.METHOD_CLIPBOARD_SET,
                JvmClipboardPayload.encodeText("M9 你好"),
            ),
        )
        assertTrue(
            JavaHostCapabilityWirePolicy.acceptsRequest(
                JavaHostCapabilityWirePolicy.METHOD_TOAST_SHOW,
                JvmToastPayload.encode("ready"),
            ),
        )
        assertFalse(JavaHostCapabilityWirePolicy.acceptsRequest("clipboard.clear", "{}"))
        assertFalse(
            JavaHostCapabilityWirePolicy.acceptsRequest(
                JavaHostCapabilityWirePolicy.METHOD_APP_LAUNCH,
                "{\"packageName\":\"com.example.app\",\"extra\":true}",
            ),
        )
        assertFalse(
            JavaHostCapabilityWirePolicy.acceptsRequest(
                JavaHostCapabilityWirePolicy.METHOD_CLIPBOARD_GET,
                "{ }",
            ),
        )
        assertFalse(
            JavaHostCapabilityWirePolicy.acceptsRequest(
                JavaHostCapabilityWirePolicy.METHOD_CLIPBOARD_SET,
                "{\"text\":\"safe\",\"extra\":true}",
            ),
        )
    }

    @Test
    fun clipboardRequestBoundaryIsEnforcedBeforeForwarding() {
        val exact = "x".repeat(JvmSourceContract.MAX_CLIPBOARD_TEXT_BYTES)
        assertTrue(
            JavaHostCapabilityWirePolicy.acceptsRequest(
                JavaHostCapabilityWirePolicy.METHOD_CLIPBOARD_SET,
                JvmClipboardPayload.encodeText(exact),
            ),
        )
        assertThrows(IllegalArgumentException::class.java) {
            JvmClipboardPayload.encodeText("x".repeat(JvmSourceContract.MAX_CLIPBOARD_TEXT_BYTES + 1))
        }
    }

    @Test
    fun successfulResponsesAreMethodSpecificallyValidatedAndRedactedFailuresPass() {
        val requestId = JvmRequestId.fromUuid(UUID.randomUUID())
        JavaHostCapabilityWirePolicy.validateResponse(
            JavaHostCapabilityWirePolicy.METHOD_APP_LAUNCH,
            JvmHostResponse(requestId, 0, true, "false"),
        )
        JavaHostCapabilityWirePolicy.validateResponse(
            JavaHostCapabilityWirePolicy.METHOD_CLIPBOARD_GET,
            JvmHostResponse(requestId, 1, true, JvmClipboardPayload.encodeText("value")),
        )
        JavaHostCapabilityWirePolicy.validateResponse(
            JavaHostCapabilityWirePolicy.METHOD_CLIPBOARD_SET,
            JvmHostResponse(requestId, 2, true, "true"),
        )
        JavaHostCapabilityWirePolicy.validateResponse(
            JavaHostCapabilityWirePolicy.METHOD_CLIPBOARD_GET,
            JvmHostResponse(
                requestId,
                3,
                succeeded = false,
                errorCode = "CLIPBOARD_GET_FAILED",
                errorMessage = "Unable to read clipboard text",
            ),
        )

        assertThrows(IllegalArgumentException::class.java) {
            JavaHostCapabilityWirePolicy.validateResponse(
                JavaHostCapabilityWirePolicy.METHOD_APP_LAUNCH,
                JvmHostResponse(requestId, 4, true, "0"),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            JavaHostCapabilityWirePolicy.validateResponse(
                JavaHostCapabilityWirePolicy.METHOD_CLIPBOARD_GET,
                JvmHostResponse(requestId, 5, true, "{\"text\":\"\\u0041\"}"),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            JavaHostCapabilityWirePolicy.validateResponse(
                JavaHostCapabilityWirePolicy.METHOD_CLIPBOARD_SET,
                JvmHostResponse(requestId, 6, true, "false"),
            )
        }
    }
}
