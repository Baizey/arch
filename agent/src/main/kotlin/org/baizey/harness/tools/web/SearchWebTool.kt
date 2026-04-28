package org.baizey.harness.tools.web

import dev.langchain4j.agent.tool.P
import dev.langchain4j.agent.tool.Tool
import org.baizey.harness.tools.fetch.WebPageFetcher
import org.baizey.harness.tools.search.FetchedSearchResultContent
import org.baizey.harness.tools.search.WebSearchEngine
import org.baizey.harness.tools.search.WebSearchResult
import org.baizey.harness.tools.web.summary.ContentSummarizer
import org.baizey.runtime.ErrorLog

class SearchWebTool internal constructor(
    private val searchEngine: WebSearchEngine,
    private val webPageFetcher: WebPageFetcher,
    private val webContentSummarizer: ContentSummarizer
) {
    @Tool(
        name = "search_web",
        value = ["""Search the public web for current or external information and return candidate pages only.
Do not use this to read page bodies; use fetch_website on one returned URL after you pick a candidate.
Enable shouldSummarizeWithAi to fetch and summarize the top result pages instead of returning only raw result listings.
The search engine chooses among configured providers behind the scenes.
If maxResults is omitted, behaves as 5. If shouldSummarizeWithAi is omitted, behaves as true.
Example: search_web(query="Kotlin coroutines release notes", maxResults=5, shouldSummarizeWithAi=true, queryGoal="find the official release notes page")"""],
        metadata = """{"title":"Search Web","readOnlyHint":true,"destructiveHint":false,"idempotentHint":true,"openWorldHint":true}"""
    )
    fun searchWeb(
        @P("Natural language search query.")
        query: String,
        @P("Maximum number of results to return. Allowed range is 1 to 10. Default 5.")
        maxResults: Int? = null,
        @P("Whether to summarize the search results with AI instead of returning the raw result list. If omitted, behaves as true.")
        shouldSummarizeWithAi: Boolean? = null,
        @P("Optional goal for the AI summary, such as what answer, source type, or angle you want it to prioritize.")
        queryGoal: String? = null
    ): String {
        val actualMaxResults = maxResults ?: 5
        val shouldSummarize = shouldSummarizeWithAi ?: true
        val normalizedQuery = query.trim()
        val normalizedQueryGoal = queryGoal?.trim()?.ifBlank { null }
        if (normalizedQuery.isBlank()) return "Query cannot be blank."
        if (actualMaxResults !in 1..10) return "maxResults must be between 1 and 10."

        val searchResponse = try {
            searchEngine.search(normalizedQuery, actualMaxResults)
        } catch (e: Exception) {
            ErrorLog.log(
                source = "search_search",
                exception = e,
                context = mapOf(
                    "query" to normalizedQuery,
                    "maxResults" to actualMaxResults.toString()
                )
            )
            return buildSearchFailureResponse(normalizedQuery, e)
        }
        val results = searchResponse.results
        if (results.isEmpty()) {
            return buildNoResultsResponse(normalizedQuery, searchResponse.provider?.displayName)
        }

        if (shouldSummarize) {
            val fetchedContent = fetchContentForSummary(results)
            return try {
                webContentSummarizer.summarizeSearchResults(
                    query = normalizedQuery,
                    queryGoal = normalizedQueryGoal,
                    searchResponse = searchResponse,
                    fetchedContent = fetchedContent
                )
            } catch (e: Exception) {
                ErrorLog.log(
                    source = "search_summary",
                    exception = e,
                    context = mapOf(
                        "query" to normalizedQuery,
                        "provider" to (searchResponse.provider?.id ?: "<none>"),
                        "maxResults" to actualMaxResults.toString()
                    )
                )
                buildSummaryFallbackResponse(normalizedQuery, searchResponse, results)
            }
        }

        return buildRawResultsResponse(normalizedQuery, searchResponse, results)
    }

    private fun buildSearchFailureResponse(query: String, exception: Exception): String {
        return buildString {
            appendLine("Search failed for query: $query")
            appendLine("Reason: ${exception.message ?: exception::class.java.simpleName}")
            appendLine("Next steps:")
            appendLine("- Retry search_web with a narrower or broader query.")
            appendLine("- If you already know a candidate URL, use fetch_website directly.")
        }.trimEnd()
    }

    private fun buildNoResultsResponse(query: String, providerName: String?): String {
        return buildString {
            appendLine("No search results found.")
            appendLine("Query: $query")
            providerName?.let { appendLine("Provider: $it") }
            appendLine("Next steps:")
            appendLine("- Rephrase the query with different keywords.")
            appendLine("- Broaden the query if it is too specific.")
            appendLine("- If you know a URL already, use fetch_website directly.")
        }.trimEnd()
    }

    private fun buildSummaryFallbackResponse(
        query: String,
        searchResponse: org.baizey.harness.tools.search.WebSearchResponse,
        results: List<WebSearchResult>
    ): String {
        return buildString {
            appendLine("Search summary failed. Returning raw results instead.")
            appendLine()
            append(buildRawResultsResponse(query, searchResponse, results))
        }.trimEnd()
    }

    private fun buildRawResultsResponse(
        query: String,
        searchResponse: org.baizey.harness.tools.search.WebSearchResponse,
        results: List<WebSearchResult>
    ): String {
        return buildString {
            appendLine("Query: $query")
            searchResponse.provider?.let { appendLine("Provider: ${it.displayName} (${it.id})") }
            appendLine("Results: ${results.size}")
            appendLine("Next step: use fetch_website on one result URL")
            appendLine("---")
            results.forEachIndexed { index, result ->
                if (index > 0) appendLine()
                appendLine("${index + 1}. ${result.title}")
                appendLine("URL: ${result.url}")
                appendLine("Snippet: ${result.snippet.ifBlank { "<empty>" }}")
            }
        }.trimEnd()
    }

    private fun fetchContentForSummary(results: List<WebSearchResult>): List<FetchedSearchResultContent> {
        return results.take(MAX_SUMMARY_FETCH_RESULTS).map { result ->
            try {
                val page = webPageFetcher.fetch(result.url)
                val excerpt = page.text.take(MAX_SUMMARY_FETCH_CHARACTERS).ifBlank { null }
                FetchedSearchResultContent(
                    result = result,
                    resolvedUrl = page.resolvedUrl,
                    pageTitle = page.title,
                    excerpt = excerpt,
                    excerptTruncated = page.sourceTruncated || page.text.length > MAX_SUMMARY_FETCH_CHARACTERS,
                    fetchError = null
                )
            } catch (e: Exception) {
                FetchedSearchResultContent(
                    result = result,
                    resolvedUrl = null,
                    pageTitle = null,
                    excerpt = null,
                    excerptTruncated = false,
                    fetchError = e.message ?: e::class.java.simpleName
                )
            }
        }
    }
}

private const val MAX_SUMMARY_FETCH_RESULTS = 3
private const val MAX_SUMMARY_FETCH_CHARACTERS = 3_000
