package org.baizey.harness.tools.web.search.providers.brave

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.baizey.harness.tools.search.WebSearchResult
import org.baizey.harness.tools.search.finalizeSearchResults

@Serializable
internal data class BraveWebSearchResponse(
    val web: BraveWebSection? = null
)

@Serializable
internal data class BraveWebSection(
    val results: List<BraveWebResult> = emptyList()
)

@Serializable
internal data class BraveWebResult(
    val title: String = "",
    val url: String = "",
    val description: String = "",
    @SerialName("extra_snippets")
    val extraSnippets: List<String> = emptyList()
)

internal fun BraveWebSearchResponse.toSearchResults(maxResults: Int): List<WebSearchResult> {
    return finalizeSearchResults(
        web.orEmpty().map { result ->
            WebSearchResult(
                title = result.title,
                url = result.url,
                snippet = listOf(result.description, result.extraSnippets.joinToString(" "))
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
            )
        },
        maxResults
    )
}

private fun BraveWebSection?.orEmpty(): List<BraveWebResult> {
    return this?.results.orEmpty()
}
