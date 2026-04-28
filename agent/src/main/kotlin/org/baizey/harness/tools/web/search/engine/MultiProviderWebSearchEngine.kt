package org.baizey.harness.tools.search.engine

import org.baizey.harness.tools.search.WebSearchEngine
import org.baizey.harness.tools.search.WebSearchProvider
import org.baizey.harness.tools.search.WebSearchResponse

internal class MultiProviderWebSearchEngine(
    providers: List<WebSearchProvider>
) : WebSearchEngine {
    private val providers = providers.ifEmpty {
        error("At least one web search provider is required.")
    }

    override fun search(query: String, maxResults: Int): WebSearchResponse {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) return WebSearchResponse(provider = null, results = emptyList())

        val candidates = availableCandidatesFor(normalizedQuery)
        if (candidates.isEmpty()) {
            throw IllegalStateException(
                "No configured search providers are available. ${providerAvailabilitySummary()}"
            )
        }

        val failures = mutableListOf<String>()
        candidates.forEach { candidate ->
            try {
                val results = candidate.provider.search(normalizedQuery, maxResults)
                if (results.isNotEmpty()) {
                    return WebSearchResponse(provider = candidate.provider.descriptor, results = results)
                }
            } catch (e: Exception) {
                failures += "${candidate.provider.descriptor.id}: ${e.message ?: e::class.java.simpleName}"
            }
        }

        if (failures.size == candidates.size) {
            throw IllegalStateException("All configured search providers failed: ${failures.joinToString("; ")}")
        }
        return WebSearchResponse(provider = candidates.firstOrNull()?.provider?.descriptor, results = emptyList())
    }

    private fun availableCandidatesFor(query: String): List<SearchProviderCandidate> {
        return providers.mapIndexedNotNull { index, provider ->
            val availability = provider.availability()
            if (!availability.available) return@mapIndexedNotNull null
            SearchProviderCandidate(
                index = index,
                provider = provider,
                score = provider.selectionScore(query)
            )
        }.sortedWith(compareByDescending<SearchProviderCandidate> { it.score }.thenBy { it.index })
    }

    private fun providerAvailabilitySummary(): String {
        return providers.joinToString("; ") { provider ->
            val availability = provider.availability()
            val reason = availability.reason ?: "not configured"
            "${provider.descriptor.id}: $reason"
        }
    }
}

private data class SearchProviderCandidate(
    val index: Int,
    val provider: WebSearchProvider,
    val score: Int
)
