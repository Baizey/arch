package org.baizey.harness.tools.search.providers.duckduckgo

import org.baizey.harness.tools.search.WebSearchProvider
import org.baizey.harness.tools.search.WebSearchProviderAvailability
import org.baizey.harness.tools.search.WebSearchProviderDescriptor
import org.baizey.harness.tools.search.WebSearchResult

internal class DuckDuckGoSearchProvider : WebSearchProvider {
    override val descriptor = WebSearchProviderDescriptor(id = "duckduckgo", displayName = "DuckDuckGo")

    override fun availability(): WebSearchProviderAvailability {
        return WebSearchProviderAvailability(
            available = false,
            reason = "no official DuckDuckGo general web search API is wired here"
        )
    }

    override fun search(query: String, maxResults: Int): List<WebSearchResult> {
        throw UnsupportedOperationException("DuckDuckGo is not available in this build.")
    }
}
