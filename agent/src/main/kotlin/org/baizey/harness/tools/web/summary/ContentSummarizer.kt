package org.baizey.harness.tools.web.summary

import org.baizey.harness.tools.fetch.WebsiteDocument
import org.baizey.harness.tools.search.FetchedSearchResultContent
import org.baizey.harness.tools.search.WebSearchResponse

internal interface ContentSummarizer {
    fun summarizeSearchResults(
        query: String,
        queryGoal: String?,
        searchResponse: WebSearchResponse,
        fetchedContent: List<FetchedSearchResultContent>
    ): String

    fun summarizeWebsiteContent(
        page: WebsiteDocument,
        startChar: Int,
        endCharExclusive: Int,
        returnedText: String,
        returnedTextTruncated: Boolean,
        queryGoal: String?
    ): String
}

internal fun interface SummaryModelStrategy {
    fun currentModelName(): String
}

internal interface SummaryAssistant {
    fun summarize(message: String): String
}
