package org.baizey.harness.tools.summary

import org.baizey.harness.tools.web.summary.SummaryAssistant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WebSummaryAssistantContractTest {
    @Test
    fun `summary assistant exposes message parameter name expected by ai services`() {
        val method = SummaryAssistant::class.java.getMethod("summarize", String::class.java)

        assertEquals("message", method.parameters.single().name)
    }
}
