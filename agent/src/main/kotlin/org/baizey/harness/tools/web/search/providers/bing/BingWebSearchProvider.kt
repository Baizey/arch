package org.baizey.harness.tools.search.providers.bing

import org.baizey.harness.tools.http.buildUri
import org.baizey.harness.tools.search.WebSearchProvider
import org.baizey.harness.tools.search.WebSearchProviderAvailability
import org.baizey.harness.tools.search.WebSearchProviderDescriptor
import org.baizey.harness.tools.search.WebSearchResult
import org.baizey.harness.tools.search.http.SearchApiHttpClient
import org.baizey.utils.IO.fromJson

internal class BingWebSearchProvider(
    private val searchApiHttpClient: SearchApiHttpClient,
    private val settings: BingWebSearchSettings = BingWebSearchSettings.fromConfig()
) : WebSearchProvider {
    override val descriptor = WebSearchProviderDescriptor(id = "bing", displayName = "Bing")

    override fun availability(): WebSearchProviderAvailability {
        return if (settings.isConfigured()) {
            WebSearchProviderAvailability(available = true)
        } else {
            WebSearchProviderAvailability(available = false, reason = "set BING_SEARCH_API_KEY")
        }
    }

    override fun selectionScore(query: String): Int {
        return if (query.contains("site:") || query.contains("filetype:")) 55 else 50
    }

    override fun search(query: String, maxResults: Int): List<WebSearchResult> {
        val response = searchApiHttpClient.getJson(
            provider = descriptor.displayName,
            uri = buildUri(
                settings.endpoint,
                mapOf(
                    "q" to query,
                    "count" to maxResults.toString(),
                    "responseFilter" to "Webpages",
                    "textFormat" to "Raw"
                )
            )
            ,
            deserialize = { body -> body.fromJson<BingWebSearchResponse>() }
        ) {
            header("Accept", "application/json")
            header("Ocp-Apim-Subscription-Key", settings.apiKey ?: missingConfiguration())
        }
        return response.toSearchResults(maxResults)
    }

    private fun missingConfiguration(): Nothing {
        throw IllegalStateException("Missing required configuration: BING_SEARCH_API_KEY")
    }
}
