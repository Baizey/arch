package org.baizey.harness.tools.search.providers.bing

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class BingWebSearchResponse(
    @SerialName("webPages")
    val webPages: BingWebPages? = null
)

@Serializable
internal data class BingWebPages(
    val value: List<BingWebPage> = emptyList()
)

@Serializable
internal data class BingWebPage(
    val name: String = "",
    val url: String = "",
    val snippet: String = ""
)

internal fun BingWebSearchResponse.toSearchResults(maxResults: Int): List<org.baizey.harness.tools.search.WebSearchResult> {
    return org.baizey.harness.tools.search.finalizeSearchResults(
        webPages.orEmpty().map { page ->
            org.baizey.harness.tools.search.WebSearchResult(
                title = page.name,
                url = page.url,
                snippet = page.snippet
            )
        },
        maxResults
    )
}

private fun BingWebPages?.orEmpty(): List<BingWebPage> {
    return this?.value.orEmpty()
}
