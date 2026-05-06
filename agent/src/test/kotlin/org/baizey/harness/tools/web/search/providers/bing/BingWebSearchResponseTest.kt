package org.baizey.harness.tools.search.providers.bing

import org.baizey.harness.tools.search.WebSearchResult
import org.baizey.utils.IO.fromJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BingWebSearchResponseTest {
    @Test
    fun `deserializes and maps bing web search response`() {
        val response = """
            {
              "webPages": {
                "value": [
                  {
                    "name": "Kotlin Releases",
                    "url": "https://kotlinlang.org/docs/releases.html",
                    "snippet": "Latest Kotlin release notes."
                  }
                ]
              }
            }
        """.trimIndent().fromJson<BingWebSearchResponse>()

        val results = response.toSearchResults(maxResults = 5)

        assertEquals(
            listOf(
                WebSearchResult(
                    title = "Kotlin Releases",
                    url = "https://kotlinlang.org/docs/releases.html",
                    snippet = "Latest Kotlin release notes."
                )
            ),
            results
        )
    }
}
