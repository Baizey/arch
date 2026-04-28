package org.baizey.harness.tools.search.providers.google

import org.baizey.utils.IO.fromJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GoogleCustomSearchResponseTest {
    @Test
    fun `deserializes and maps google custom search response`() {
        val response = """
            {
              "items": [
                {
                  "title": "Kotlin Releases",
                  "link": "https://kotlinlang.org/docs/releases.html",
                  "snippet": "Latest Kotlin release notes."
                }
              ]
            }
        """.trimIndent().fromJson<GoogleCustomSearchResponse>()

        val results = response.toSearchResults(maxResults = 5)

        assertEquals("Kotlin Releases", results.single().title)
        assertEquals("https://kotlinlang.org/docs/releases.html", results.single().url)
        assertEquals("Latest Kotlin release notes.", results.single().snippet)
    }
}
