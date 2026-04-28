package org.baizey.harness.tools.search.providers.duckduckgo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class DuckDuckGoSearchProviderTest {
    @Test
    fun `reports unsupported official api`() {
        val availability = DuckDuckGoSearchProvider().availability()

        assertFalse(availability.available)
        assertEquals("no official DuckDuckGo general web search API is wired here", availability.reason)
    }
}
