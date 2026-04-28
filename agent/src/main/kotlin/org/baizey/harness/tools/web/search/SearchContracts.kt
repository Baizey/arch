package org.baizey.harness.tools.search

internal interface WebSearchEngine {
    fun search(query: String, maxResults: Int): WebSearchResponse
}

internal interface WebSearchProvider {
    val descriptor: WebSearchProviderDescriptor

    fun availability(): WebSearchProviderAvailability

    fun selectionScore(query: String): Int = 0

    fun search(query: String, maxResults: Int): List<WebSearchResult>
}
