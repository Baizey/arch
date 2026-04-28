package org.baizey.harness.tools.search.providers.brave

import org.baizey.harness.tools.web.search.providers.brave.BraveWebSearchResponse
import org.baizey.harness.tools.web.search.providers.brave.toSearchResults
import org.baizey.utils.IO.fromJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BraveWebSearchResponseTest {
    @Test
    fun `deserializes and maps brave web search response`() {
        val response = """
            {
              "web": {
                "results": [
                  {
                    "title": "Kotlin Releases",
                    "url": "https://kotlinlang.org/docs/releases.html",
                    "description": "Latest Kotlin release notes.",
                    "extra_snippets": ["Stable releases and changelogs."]
                  }
                ]
              }
            }
        """.trimIndent().fromJson<BraveWebSearchResponse>()

        val results = response.toSearchResults(maxResults = 5)

        assertEquals("Kotlin Releases", results.single().title)
        assertEquals("https://kotlinlang.org/docs/releases.html", results.single().url)
        assertEquals("Latest Kotlin release notes. Stable releases and changelogs.", results.single().snippet)
    }
}
