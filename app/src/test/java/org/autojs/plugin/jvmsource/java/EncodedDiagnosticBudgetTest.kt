package org.autojs.plugin.jvmsource.java

import org.autojs.plugin.jvmsource.api.JvmDiagnosticSeverity
import org.autojs.plugin.jvmsource.api.JvmRequestId
import org.autojs.plugin.jvmsource.api.JvmSourceCodec
import org.autojs.plugin.jvmsource.api.JvmSourceDiagnostic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EncodedDiagnosticBudgetTest {
    private val diagnostic = JvmSourceDiagnostic(
        requestId = JvmRequestId.fromBytes(ByteArray(JvmRequestId.BYTE_COUNT)),
        severity = JvmDiagnosticSeverity.ERROR,
        code = "ECJ_ERROR",
        message = "错".repeat(1_000),
        sourceFileName = "Main.java",
        line = 1,
        column = 1,
    )

    @Test
    fun budgetsTheCompleteEncodedDiagnosticRatherThanOnlyItsMessage() {
        val encoded = requireNotNull(EncodedDiagnosticBudget.encodeWithin(diagnostic, 256))

        assertTrue(encoded.size <= 256)
        assertNull(EncodedDiagnosticBudget.encodeWithin(diagnostic, 1))
    }

    @Test
    fun multipleFramesFitTheSharedBudgetWithoutChangingTheWireSchema() {
        val first = diagnostic.copy(message = "missingFirst", line = 3, column = 21)
        val second = diagnostic.copy(message = "missingSecond", line = 4, column = 22)
        val exactBudget = JvmSourceCodec.encodeDiagnostic(first).size +
            JvmSourceCodec.encodeDiagnostic(second).size
        var remaining = exactBudget

        val encoded = listOf(first, second).map { value ->
            requireNotNull(EncodedDiagnosticBudget.encodeWithin(value, remaining)).also {
                remaining -= it.size
            }
        }

        assertEquals(0, remaining)
        assertEquals(listOf(first, second), encoded.map(JvmSourceCodec::decodeDiagnostic))
    }
}
