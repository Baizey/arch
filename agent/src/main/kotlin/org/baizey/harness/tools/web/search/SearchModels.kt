package org.baizey.harness.tools.search

internal data class WebSearchProviderDescriptor(
    val id: String,
    val displayName: String
)

internal data class WebSearchProviderAvailability(
    val available: Boolean,
    val reason: String? = null
)

internal data class WebSearchResponse(
    val provider: WebSearchProviderDescriptor?,
    val results: List<WebSearchResult>
)

internal data class WebSearchResult(
    val title: String,
    val url: String,
    val snippet: String
)

internal data class FetchedSearchResultContent(
    val result: WebSearchResult,
    val resolvedUrl: String?,
    val pageTitle: String?,
    val excerpt: String?,
    val excerptTruncated: Boolean,
    val fetchError: String?
)
