package org.baizey.harness.tools.summary

import dev.langchain4j.model.ollama.OllamaChatModel
import dev.langchain4j.service.AiServices
import org.baizey.harness.tools.fetch.WebsiteDocument
import org.baizey.harness.tools.search.FetchedSearchResultContent
import org.baizey.harness.tools.search.WebSearchResponse
import org.baizey.harness.tools.web.summary.ContentSummarizer
import org.baizey.harness.tools.web.summary.SummaryModelStrategy
import org.baizey.harness.tools.web.summary.SummaryAssistant
import org.baizey.runtime.AppConfig
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

internal class CurrentModelContentSummarizer(
    private val modelStrategy: SummaryModelStrategy,
    private val assistantFactory: (String) -> SummaryAssistant = ::buildSummaryAssistant
) : ContentSummarizer {
    private val assistantsByModel = ConcurrentHashMap<String, SummaryAssistant>()

    override fun summarizeSearchResults(
        query: String,
        queryGoal: String?,
        searchResponse: WebSearchResponse,
        fetchedContent: List<FetchedSearchResultContent>
    ): String {
        val prompt = buildString {
            appendLine("Summarize these search results for an agent.")
            appendLine("Search query: $query")
            appendLine("Summary goal: ${queryGoal ?: "none provided"}")
            searchResponse.provider?.let {
                appendLine("Search provider: ${it.displayName} (${it.id})")
            }
            appendLine("Return a short actionable summary that covers everything the goal could encompass")
            appendLine("Use fetched page content when available; do not rely only on titles and snippets.")
            appendLine("If one result looks clearly best for the goal, say so.")
            appendLine("Search results:")
            searchResponse.results.forEachIndexed { index, result ->
                appendLine("${index + 1}. Title: ${result.title}")
                appendLine("URL: ${result.url}")
                appendLine("Snippet: ${result.snippet.ifBlank { "<empty>" }}")
            }
            if (fetchedContent.isNotEmpty()) {
                appendLine("Fetched top result content:")
                fetchedContent.forEachIndexed { index, content ->
                    appendLine("${index + 1}. Result URL: ${content.result.url}")
                    content.resolvedUrl?.let { appendLine("Resolved URL: $it") }
                    content.pageTitle?.let { appendLine("Page title: $it") }
                    content.fetchError?.let {
                        appendLine("Fetch error: $it")
                    } ?: run {
                        appendLine("Excerpt truncated: ${content.excerptTruncated}")
                        appendLine("Excerpt:")
                        appendLine(content.excerpt ?: "<empty>")
                    }
                }
            }
        }
        return assistantForCurrentModel().summarize(prompt).trim()
    }

    override fun summarizeWebsiteContent(
        page: WebsiteDocument,
        startChar: Int,
        endCharExclusive: Int,
        returnedText: String,
        returnedTextTruncated: Boolean,
        queryGoal: String?
    ): String {
        val prompt = buildString {
            appendLine("Summarize this fetched website content for an agent.")
            appendLine("Summary goal: ${queryGoal ?: "none provided"}")
            appendLine("Requested URL: ${page.requestedUrl}")
            appendLine("Resolved URL: ${page.resolvedUrl}")
            appendLine("Status code: ${page.statusCode}")
            appendLine("Content-Type: ${page.contentType}")
            if (!page.title.isNullOrBlank()) {
                appendLine("Title: ${page.title}")
            }
            appendLine("Available characters in cleaned source: ${page.text.length}")
            appendLine("Returned range: [$startChar..$endCharExclusive[")
            appendLine("Returned characters: ${returnedText.length}")
            appendLine("Returned content truncated: $returnedTextTruncated")
            appendLine("Source truncated before paging: ${page.sourceTruncated}")
            appendLine("Instructions:")
            appendLine("- Answer toward the summary goal when one was provided.")
            appendLine("- Mention if the returned slice may be incomplete.")
            appendLine("- Mention the source URL when useful.")
            appendLine("Fetched content:")
            append(returnedText.ifBlank { "<empty>" })
        }
        return assistantForCurrentModel().summarize(prompt).trim()
    }

    private fun assistantForCurrentModel(): SummaryAssistant {
        val modelName = modelStrategy.currentModelName()
        return assistantsByModel.computeIfAbsent(modelName, assistantFactory)
    }
}

private fun buildSummaryAssistant(modelName: String): SummaryAssistant {
    return AiServices.builder(SummaryAssistant::class.java)
        .chatModel(
            OllamaChatModel.builder()
                .baseUrl(AppConfig.providers.ollama.baseUrl)
                .modelName(modelName)
                .timeout(Duration.ofMinutes(2))
                .returnThinking(false)
                .build()
        )
        .systemMessage(
            """You summarize web tool results for another coding agent.
Return only the useful answer, not chain of thought.
Stay grounded in the provided data.
Prefer concise factual summaries.
If the requested goal cannot be satisfied from the provided data, say what is missing.
Include relevant URLs when they materially help the next step."""
        )
        .build()
}
