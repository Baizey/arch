package org.baizey.harness.tools.search.providers.duckduckgo

import org.baizey.harness.tools.search.WebSearchProviderAvailability
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DuckDuckGoSearchProviderTest {
    @Test
    fun `reports unsupported official api`() {
        val availability = DuckDuckGoSearchProvider().availability()

        assertEquals(
            WebSearchProviderAvailability(
                available = false,
                reason = "no official DuckDuckGo general web search API is wired here"
            ),
            availability
        )
    }
}
