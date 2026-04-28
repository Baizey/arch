package org.baizey.harness.tools.web

import dev.langchain4j.agent.tool.P
import dev.langchain4j.agent.tool.Tool
import org.baizey.harness.tools.fetch.WebPageFetcher
import org.baizey.harness.tools.web.summary.ContentSummarizer
import org.baizey.runtime.ErrorLog

class FetchWebsiteTool internal constructor(
    private val webPageFetcher: WebPageFetcher,
    private val webContentSummarizer: ContentSummarizer
) {
    @Tool(
        name = "fetch_website",
        value = ["""Fetch one website directly by URL and return cleaned readable text.
Do not use this for search; use search_web to find candidate pages first.
Enable shouldSummarizeWithAi to return only a goal-oriented summary and avoid sending raw page text back into the agent context.
If startChar is omitted, behaves as 0. If maxCharacters is omitted, behaves as 4000. If shouldSummarizeWithAi is omitted, behaves as true.
Example: fetch_website(url="https://kotlinlang.org", startChar=0, maxCharacters=4000, shouldSummarizeWithAi=true, queryGoal="extract the latest stable Kotlin version")"""],
        metadata = """{"title":"Fetch Website","readOnlyHint":true,"destructiveHint":false,"idempotentHint":true,"openWorldHint":true}"""
    )
    fun fetchWebsite(
        @P("Full website URL, including http or https.")
        url: String,
        @P("0-based character index to start returning from. Default 0.")
        startChar: Int? = null,
        @P("Maximum number of cleaned content characters to return. Allowed range is 500 to 20000. Default 4000.")
        maxCharacters: Int? = null,
        @P("Whether to summarize the fetched content with AI instead of returning raw content. If omitted, behaves as true.")
        shouldSummarizeWithAi: Boolean? = null,
        @P("Optional goal for the AI summary, such as what answer, source type, or angle you want it to prioritize.")
        queryGoal: String? = null
    ): String {
        val actualStartChar = startChar ?: 0
        val actualMaxCharacters = maxCharacters ?: 4_000
        val shouldSummarize = shouldSummarizeWithAi ?: true
        val normalizedQueryGoal = queryGoal?.trim()?.ifBlank { null } ?: "Summarize website content."
        if (actualStartChar < 0) return "startChar must be at least 0."
        if (actualMaxCharacters !in 500..20_000) return "maxCharacters must be between 500 and 20000."
        val page = try {
            webPageFetcher.fetch(url)
        } catch (e: Exception) {
            ErrorLog.log(
                source = "fetch_website.fetch",
                exception = e,
                context = mapOf(
                    "url" to url.trim(),
                    "startChar" to actualStartChar.toString(),
                    "maxCharacters" to actualMaxCharacters.toString()
                )
            )
            return buildFetchFailureResponse(url.trim(), e)
        }
        if (actualStartChar > page.text.length) return "startChar must be at most ${page.text.length}."

        val returnedEndExclusive = minOf(page.text.length, actualStartChar + actualMaxCharacters)
        val returnedText = page.text.substring(actualStartChar, returnedEndExclusive)
        val truncated = returnedEndExclusive < page.text.length || page.sourceTruncated

        if (shouldSummarize) {
            return try {
                webContentSummarizer.summarizeWebsiteContent(
                    page = page,
                    startChar = actualStartChar,
                    endCharExclusive = returnedEndExclusive,
                    returnedText = returnedText,
                    returnedTextTruncated = truncated,
                    queryGoal = normalizedQueryGoal
                )
            } catch (e: Exception) {
                ErrorLog.log(
                    source = "fetch_website.summary",
                    exception = e,
                    context = mapOf(
                        "url" to page.requestedUrl,
                        "resolvedUrl" to page.resolvedUrl,
                        "startChar" to actualStartChar.toString(),
                        "returnedEndExclusive" to returnedEndExclusive.toString()
                    )
                )
                buildSummaryFallbackResponse(
                    page = page,
                    actualStartChar = actualStartChar,
                    returnedEndExclusive = returnedEndExclusive,
                    returnedText = returnedText,
                    truncated = truncated
                )
            }
        }

        return buildRawFetchResponse(
            page = page,
            actualStartChar = actualStartChar,
            returnedEndExclusive = returnedEndExclusive,
            returnedText = returnedText,
            truncated = truncated
        )
    }

    private fun buildFetchFailureResponse(url: String, exception: Exception): String {
        return buildString {
            appendLine("Website fetch failed.")
            appendLine("Requested URL: $url")
            appendLine("Reason: ${exception.message ?: exception::class.java.simpleName}")
            appendLine("Next steps:")
            appendLine("- Retry fetch_website if the URL is likely correct.")
            appendLine("- Use search_web to find alternate sources or the canonical URL.")
            appendLine("- Check whether the site blocks automated requests or returned an unsupported format.")
        }.trimEnd()
    }

    private fun buildSummaryFallbackResponse(
        page: org.baizey.harness.tools.fetch.WebsiteDocument,
        actualStartChar: Int,
        returnedEndExclusive: Int,
        returnedText: String,
        truncated: Boolean
    ): String {
        return buildString {
            appendLine("Website summary failed. Returning raw fetched content instead.")
            appendLine()
            append(
                buildRawFetchResponse(
                    page = page,
                    actualStartChar = actualStartChar,
                    returnedEndExclusive = returnedEndExclusive,
                    returnedText = returnedText,
                    truncated = truncated
                )
            )
        }.trimEnd()
    }

    private fun buildRawFetchResponse(
        page: org.baizey.harness.tools.fetch.WebsiteDocument,
        actualStartChar: Int,
        returnedEndExclusive: Int,
        returnedText: String,
        truncated: Boolean
    ): String {
        return buildString {
            append(buildRawFetchMetadata(page, actualStartChar, returnedEndExclusive, returnedText.length, truncated))
            appendLine("Next step: if you need more content, call fetch_website again with startChar=$returnedEndExclusive")
            appendLine("---")
            appendLine("Content:")
            append(returnedText.ifBlank { "<empty>" })
        }.trimEnd()
    }

    private fun buildRawFetchMetadata(
        page: org.baizey.harness.tools.fetch.WebsiteDocument,
        actualStartChar: Int,
        returnedEndExclusive: Int,
        returnedCharacterCount: Int,
        truncated: Boolean
    ): String {
        return buildString {
            appendLine("Requested URL: ${page.requestedUrl}")
            appendLine("Resolved URL: ${page.resolvedUrl}")
            appendLine("Status code: ${page.statusCode}")
            appendLine("Content-Type: ${page.contentType}")
            if (!page.title.isNullOrBlank()) {
                appendLine("Title: ${page.title}")
            }
            appendLine("Available characters: ${page.text.length}")
            appendLine("Source characters: ${page.originalCharacterCount}")
            appendLine("Returned range: [$actualStartChar..$returnedEndExclusive[")
            appendLine("Returned characters: $returnedCharacterCount")
            appendLine("Truncated: $truncated")
            if (page.sourceTruncated) {
                appendLine("Source truncated: true")
            }
        }
    }
}
