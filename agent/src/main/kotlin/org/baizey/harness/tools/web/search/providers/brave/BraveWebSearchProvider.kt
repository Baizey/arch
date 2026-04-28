package org.baizey.harness.tools.web.search.providers.brave

import org.baizey.harness.tools.http.buildUri
import org.baizey.harness.tools.search.WebSearchProvider
import org.baizey.harness.tools.search.WebSearchProviderAvailability
import org.baizey.harness.tools.search.WebSearchProviderDescriptor
import org.baizey.harness.tools.search.WebSearchResult
import org.baizey.harness.tools.search.engine.queryLooksResearchHeavy
import org.baizey.harness.tools.search.http.SearchApiHttpClient
import org.baizey.harness.tools.search.providers.brave.BraveWebSearchSettings
import org.baizey.utils.IO.fromJson

internal class BraveWebSearchProvider(
    private val searchApiHttpClient: SearchApiHttpClient,
    private val settings: BraveWebSearchSettings = BraveWebSearchSettings.fromConfig()
) : WebSearchProvider {
    override val descriptor = WebSearchProviderDescriptor(id = "brave", displayName = "Brave")

    override fun availability(): WebSearchProviderAvailability {
        return if (settings.isConfigured()) {
            WebSearchProviderAvailability(available = true)
        } else {
            WebSearchProviderAvailability(available = false, reason = "set BRAVE_SEARCH_API_KEY")
        }
    }

    override fun selectionScore(query: String): Int {
        return if (queryLooksResearchHeavy(query)) 70 else 45
    }

    override fun search(query: String, maxResults: Int): List<WebSearchResult> {
        val response = searchApiHttpClient.getJson(
            provider = descriptor.displayName,
            uri = buildUri(
                settings.endpoint,
                mapOf(
                    "q" to query,
                    "count" to minOf(maxResults, 20).toString(),
                    "extra_snippets" to "true"
                )
            )
            ,
            deserialize = { body -> body.fromJson<BraveWebSearchResponse>() }
        ) {
            header("Accept", "application/json")
            header("X-Subscription-Token", settings.apiKey ?: missingConfiguration())
        }
        return response.toSearchResults(maxResults)
    }

    private fun missingConfiguration(): Nothing {
        throw IllegalStateException("Missing required configuration: BRAVE_SEARCH_API_KEY")
    }
}
