package org.baizey.harness.tools.search.providers.google

import org.baizey.harness.tools.http.buildUri
import org.baizey.harness.tools.search.WebSearchProvider
import org.baizey.harness.tools.search.WebSearchProviderAvailability
import org.baizey.harness.tools.search.WebSearchProviderDescriptor
import org.baizey.harness.tools.search.WebSearchResult
import org.baizey.harness.tools.search.http.SearchApiHttpClient
import org.baizey.utils.IO.fromJson

internal class GoogleCustomSearchProvider(
    private val searchApiHttpClient: SearchApiHttpClient,
    private val settings: GoogleCustomSearchSettings = GoogleCustomSearchSettings.fromConfig()
) : WebSearchProvider {
    override val descriptor = WebSearchProviderDescriptor(id = "google", displayName = "Google")

    override fun availability(): WebSearchProviderAvailability {
        return if (settings.isConfigured()) {
            WebSearchProviderAvailability(available = true)
        } else {
            WebSearchProviderAvailability(
                available = false,
                reason = "set GOOGLE_CUSTOM_SEARCH_API_KEY and GOOGLE_CUSTOM_SEARCH_ENGINE_ID"
            )
        }
    }

    override fun selectionScore(query: String): Int {
        return if (query.contains("site:") || query.contains("filetype:")) 65 else 35
    }

    override fun search(query: String, maxResults: Int): List<WebSearchResult> {
        val response = searchApiHttpClient.getJson(
            provider = descriptor.displayName,
            uri = buildUri(
                settings.endpoint,
                mapOf(
                    "q" to query,
                    "key" to (settings.apiKey ?: missingConfiguration("GOOGLE_CUSTOM_SEARCH_API_KEY")),
                    "cx" to (settings.searchEngineId ?: missingConfiguration("GOOGLE_CUSTOM_SEARCH_ENGINE_ID")),
                    "num" to minOf(maxResults, 10).toString()
                )
            )
            ,
            deserialize = { body -> body.fromJson<GoogleCustomSearchResponse>() }
        ) {
            header("Accept", "application/json")
        }
        return response.toSearchResults(maxResults)
    }

    private fun missingConfiguration(name: String): Nothing {
        throw IllegalStateException("Missing required configuration: $name")
    }
}
