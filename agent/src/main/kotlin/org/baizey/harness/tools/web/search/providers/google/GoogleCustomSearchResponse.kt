package org.baizey.harness.tools.search.providers.google

import kotlinx.serialization.Serializable

@Serializable
internal data class GoogleCustomSearchResponse(
    val items: List<GoogleCustomSearchItem> = emptyList()
)

@Serializable
internal data class GoogleCustomSearchItem(
    val title: String = "",
    val link: String = "",
    val snippet: String = ""
)

internal fun GoogleCustomSearchResponse.toSearchResults(maxResults: Int): List<org.baizey.harness.tools.search.WebSearchResult> {
    return org.baizey.harness.tools.search.finalizeSearchResults(
        items.map { item ->
            org.baizey.harness.tools.search.WebSearchResult(
                title = item.title,
                url = item.link,
                snippet = item.snippet
            )
        },
        maxResults
    )
}
