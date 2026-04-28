package org.baizey.harness.tools.search

import java.net.URI
import org.baizey.harness.tools.fetch.normalizeLineContent

internal fun finalizeSearchResults(results: List<WebSearchResult>, maxResults: Int): List<WebSearchResult> {
    return results.mapNotNull(::sanitizeSearchResult)
        .distinctBy { it.url }
        .take(maxResults)
}

private fun sanitizeSearchResult(result: WebSearchResult): WebSearchResult? {
    val normalizedUrl = normalizeSearchResultUrl(result.url) ?: return null
    val title = normalizeLineContent(result.title).ifBlank { normalizedUrl }
    val snippet = normalizeLineContent(result.snippet)
    return WebSearchResult(title = title, url = normalizedUrl, snippet = snippet)
}

private fun normalizeSearchResultUrl(rawUrl: String): String? {
    val candidate = rawUrl.trim()
    if (candidate.isBlank()) return null
    val uri = runCatching { URI.create(candidate) }.getOrNull() ?: return null
    return if (uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true)) {
        uri.toString()
    } else {
        null
    }
}
