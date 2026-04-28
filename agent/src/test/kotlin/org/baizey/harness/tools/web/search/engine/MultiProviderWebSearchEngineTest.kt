package org.baizey.harness.tools.search.engine

import org.baizey.harness.tools.search.WebSearchProvider
import org.baizey.harness.tools.search.WebSearchProviderAvailability
import org.baizey.harness.tools.search.WebSearchProviderDescriptor
import org.baizey.harness.tools.search.WebSearchResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MultiProviderWebSearchEngineTest {
    @Test
    fun `engine skips unavailable providers and uses next configured provider`() {
        val engine = MultiProviderWebSearchEngine(
            listOf(
                fakeProvider(
                    id = "duckduckgo",
                    displayName = "DuckDuckGo",
                    available = false,
                    reason = "not wired"
                ),
                fakeProvider(
                    id = "bing",
                    displayName = "Bing",
                    score = 10,
                    results = listOf(
                        WebSearchResult(
                            title = "Kotlin Releases",
                            url = "https://kotlinlang.org/docs/releases.html",
                            snippet = "Latest Kotlin release notes."
                        )
                    )
                )
            )
        )

        val response = engine.search("kotlin release notes", maxResults = 5)

        assertEquals("bing", response.provider?.id)
        assertEquals(1, response.results.size)
    }

    @Test
    fun `engine prefers higher scored provider for the query`() {
        val engine = MultiProviderWebSearchEngine(
            listOf(
                fakeProvider(
                    id = "bing",
                    displayName = "Bing",
                    score = 20,
                    results = listOf(
                        WebSearchResult(
                            title = "Bing Result",
                            url = "https://example.com/bing",
                            snippet = "bing"
                        )
                    )
                ),
                fakeProvider(
                    id = "brave",
                    displayName = "Brave",
                    score = 50,
                    results = listOf(
                        WebSearchResult(
                            title = "Brave Result",
                            url = "https://example.com/brave",
                            snippet = "brave"
                        )
                    )
                )
            )
        )

        val response = engine.search("what is the latest kotlin release?", maxResults = 5)

        assertEquals("brave", response.provider?.id)
        assertEquals("Brave Result", response.results.single().title)
    }

    private fun fakeProvider(
        id: String,
        displayName: String,
        available: Boolean = true,
        reason: String? = null,
        score: Int = 0,
        results: List<WebSearchResult> = emptyList()
    ): WebSearchProvider {
        return object : WebSearchProvider {
            override val descriptor = WebSearchProviderDescriptor(id = id, displayName = displayName)

            override fun availability(): WebSearchProviderAvailability {
                return WebSearchProviderAvailability(available = available, reason = reason)
            }

            override fun selectionScore(query: String): Int = score

            override fun search(query: String, maxResults: Int): List<WebSearchResult> = results
        }
    }
}
