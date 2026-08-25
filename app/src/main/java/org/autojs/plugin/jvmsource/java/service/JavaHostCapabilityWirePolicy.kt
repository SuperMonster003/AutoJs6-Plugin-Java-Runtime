package org.autojs.plugin.jvmsource.java.service

import org.autojs.plugin.jvmsource.api.JvmClipboardPayload
import org.autojs.plugin.jvmsource.api.JvmHostResponse
import org.autojs.plugin.jvmsource.api.JvmToastPayload

/** Provider-side, hardcoded wire allowlist for every host-backed script capability. */
internal object JavaHostCapabilityWirePolicy {
    const val METHOD_APP_LAUNCH = "app.launch"
    const val METHOD_CLIPBOARD_GET = "clipboard.get"
    const val METHOD_CLIPBOARD_SET = "clipboard.set"
    const val METHOD_TOAST_SHOW = "toast.show"

    val supportedMethods = setOf(
        METHOD_APP_LAUNCH,
        METHOD_CLIPBOARD_GET,
        METHOD_CLIPBOARD_SET,
        METHOD_TOAST_SHOW,
    )

    fun acceptsRequest(method: String, payloadJson: String): Boolean = when (method) {
        METHOD_APP_LAUNCH -> APP_LAUNCH_PAYLOAD.matches(payloadJson)
        METHOD_CLIPBOARD_GET -> runCatching {
            JvmClipboardPayload.validateGetRequest(payloadJson)
        }.isSuccess
        METHOD_CLIPBOARD_SET -> runCatching {
            JvmClipboardPayload.decodeText(payloadJson)
        }.isSuccess
        METHOD_TOAST_SHOW -> runCatching {
            JvmToastPayload.decode(payloadJson)
        }.isSuccess
        else -> false
    }

    fun validateResponse(method: String, response: JvmHostResponse) {
        require(method in supportedMethods) { "Host method is outside the provider allowlist" }
        if (!response.succeeded) return
        val payload = requireNotNull(response.payloadJson) { "Successful host response is missing its payload" }
        when (method) {
            METHOD_APP_LAUNCH -> require(payload == "true" || payload == "false") {
                "app.launch response is not a JSON boolean"
            }
            METHOD_CLIPBOARD_GET -> JvmClipboardPayload.decodeText(payload)
            METHOD_CLIPBOARD_SET, METHOD_TOAST_SHOW -> require(payload == "true") {
                "$method response is not the canonical acknowledgement"
            }
        }
    }

    private val APP_LAUNCH_PAYLOAD = Regex(
        "\\{\\\"packageName\\\":\\\"[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+\\\"\\}",
    )
}
