package org.baizey.harness.tools.web

import org.baizey.harness.tools.fetch.WebPageFetcher
import org.baizey.harness.tools.fetch.WebsiteDocument
import org.baizey.harness.tools.search.FetchedSearchResultContent
import org.baizey.harness.tools.search.WebSearchResponse
import org.baizey.harness.tools.web.summary.ContentSummarizer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FetchWebsiteToolTest {
    @Test
    fun `returns paged content with metadata`() {
        val content = (1..600).joinToString(separator = "") { "x" }.replaceRange(2, 7, "cdefg")
        val tool = FetchWebsiteTool(
            webPageFetcher = object : WebPageFetcher {
                override fun fetch(url: String): WebsiteDocument {
                    assertEquals("https://example.com", url)
                    return WebsiteDocument(
                        requestedUrl = url,
                        resolvedUrl = url,
                        statusCode = 200,
                        contentType = "text/html",
                        title = "Example",
                        text = content,
                        originalCharacterCount = content.length,
                        sourceTruncated = false
                    )
                }
            },
            webContentSummarizer = unusedSummarizer()
        )

        val result = tool.fetchWebsite("https://example.com", startChar = 2, maxCharacters = 500, shouldSummarizeWithAi = false)

        assertTrue(result.contains("Requested URL: https://example.com"), result)
        assertTrue(result.contains("Available characters: 600"), result)
        assertTrue(result.contains("Returned range: [2..502["), result)
        assertTrue(result.contains("Returned characters: 500"), result)
        assertTrue(result.contains("Truncated: true"), result)
        assertTrue(result.contains("Content:"), result)
        assertTrue(result.contains("cdefg"), result)
    }

    @Test
    fun `reports source truncation explicitly`() {
        val tool = FetchWebsiteTool(
            webPageFetcher = object : WebPageFetcher {
                override fun fetch(url: String): WebsiteDocument {
                    return WebsiteDocument(
                        requestedUrl = url,
                        resolvedUrl = url,
                        statusCode = 200,
                        contentType = "text/html",
                        title = null,
                        text = "abc",
                        originalCharacterCount = 200_000,
                        sourceTruncated = true
                    )
                }
            },
            webContentSummarizer = unusedSummarizer()
        )

        val result = tool.fetchWebsite("https://example.com", startChar = 0, maxCharacters = 500, shouldSummarizeWithAi = false)

        assertTrue(result.contains("Source truncated: true"), result)
        assertTrue(result.contains("Truncated: true"), result)
    }

    @Test
    fun `rejects invalid start char`() {
        val tool = FetchWebsiteTool(
            webPageFetcher = object : WebPageFetcher {
                override fun fetch(url: String): WebsiteDocument {
                    return WebsiteDocument(
                        requestedUrl = url,
                        resolvedUrl = url,
                        statusCode = 200,
                        contentType = "text/plain",
                        title = null,
                        text = "abc",
                        originalCharacterCount = 3,
                        sourceTruncated = false
                    )
                }
            },
            webContentSummarizer = unusedSummarizer()
        )

        assertEquals("startChar must be at least 0.", tool.fetchWebsite("https://example.com", startChar = -1))
        assertEquals("startChar must be at most 3.", tool.fetchWebsite("https://example.com", startChar = 4))
    }

    @Test
    fun `uses ai summary by default and omits raw content`() {
        var capturedPage: WebsiteDocument? = null
        var capturedReturnedText: String? = null
        var capturedRange: String? = null
        var capturedGoal: String? = null
        val tool = FetchWebsiteTool(
            webPageFetcher = object : WebPageFetcher {
                override fun fetch(url: String): WebsiteDocument {
                    return WebsiteDocument(
                        requestedUrl = url,
                        resolvedUrl = "$url/resolved",
                        statusCode = 200,
                        contentType = "text/html",
                        title = "Example",
                        text = "abcdef",
                        originalCharacterCount = 6,
                        sourceTruncated = false
                    )
                }
            },
            webContentSummarizer = object : ContentSummarizer {
                override fun summarizeSearchResults(
                    query: String,
                    queryGoal: String?,
                    searchResponse: WebSearchResponse,
                    fetchedContent: List<FetchedSearchResultContent>
                ): String = error("not used")

                override fun summarizeWebsiteContent(
                    page: WebsiteDocument,
                    startChar: Int,
                    endCharExclusive: Int,
                    returnedText: String,
                    returnedTextTruncated: Boolean,
                    queryGoal: String?
                ): String {
                    capturedPage = page
                    capturedReturnedText = returnedText
                    capturedRange = "$startChar..$endCharExclusive"
                    capturedGoal = queryGoal
                    return "summary only"
                }
            }
        )

        val result = tool.fetchWebsite(
            url = "https://example.com",
            startChar = 1,
            maxCharacters = 500,
            queryGoal = "extract the main claim"
        )

        assertEquals("summary only", result)
        assertEquals("bcdef", capturedReturnedText)
        assertEquals("1..6", capturedRange)
        assertEquals("extract the main claim", capturedGoal)
        assertEquals("https://example.com/resolved", capturedPage?.resolvedUrl)
        assertTrue(!result.contains("Content:"), result)
    }

    @Test
    fun `falls back to raw content when website summary fails`() {
        val tool = FetchWebsiteTool(
            webPageFetcher = object : WebPageFetcher {
                override fun fetch(url: String): WebsiteDocument {
                    return WebsiteDocument(
                        requestedUrl = url,
                        resolvedUrl = url,
                        statusCode = 200,
                        contentType = "text/html",
                        title = "Example",
                        text = "abcdef",
                        originalCharacterCount = 6,
                        sourceTruncated = false
                    )
                }
            },
            webContentSummarizer = object : ContentSummarizer {
                override fun summarizeSearchResults(
                    query: String,
                    queryGoal: String?,
                    searchResponse: WebSearchResponse,
                    fetchedContent: List<FetchedSearchResultContent>
                ): String = error("not used")

                override fun summarizeWebsiteContent(
                    page: WebsiteDocument,
                    startChar: Int,
                    endCharExclusive: Int,
                    returnedText: String,
                    returnedTextTruncated: Boolean,
                    queryGoal: String?
                ): String {
                    error("summary unavailable")
                }
            }
        )

        val result = tool.fetchWebsite("https://example.com", startChar = 1, maxCharacters = 500)

        assertTrue(result.contains("Website summary failed. Returning raw fetched content instead."), result)
        assertTrue(result.contains("Requested URL: https://example.com"), result)
        assertTrue(result.contains("Content:"), result)
        assertTrue(result.contains("bcdef"), result)
    }

    @Test
    fun `returns actionable message when fetch fails`() {
        val tool = FetchWebsiteTool(
            webPageFetcher = object : WebPageFetcher {
                override fun fetch(url: String): WebsiteDocument {
                    error("connection refused")
                }
            },
            webContentSummarizer = unusedSummarizer()
        )

        val result = tool.fetchWebsite("https://example.com", shouldSummarizeWithAi = false)

        assertTrue(result.contains("Website fetch failed."), result)
        assertTrue(result.contains("Requested URL: https://example.com"), result)
        assertTrue(result.contains("Use search_web to find alternate sources"), result)
    }

    @Test
    fun `uses the default summary goal when none is provided`() {
        var capturedGoal: String? = null
        val tool = FetchWebsiteTool(
            webPageFetcher = object : WebPageFetcher {
                override fun fetch(url: String): WebsiteDocument {
                    return WebsiteDocument(
                        requestedUrl = url,
                        resolvedUrl = url,
                        statusCode = 200,
                        contentType = "text/html",
                        title = null,
                        text = "abcdef",
                        originalCharacterCount = 6,
                        sourceTruncated = false
                    )
                }
            },
            webContentSummarizer = object : ContentSummarizer {
                override fun summarizeSearchResults(
                    query: String,
                    queryGoal: String?,
                    searchResponse: WebSearchResponse,
                    fetchedContent: List<FetchedSearchResultContent>
                ): String = error("not used")

                override fun summarizeWebsiteContent(
                    page: WebsiteDocument,
                    startChar: Int,
                    endCharExclusive: Int,
                    returnedText: String,
                    returnedTextTruncated: Boolean,
                    queryGoal: String?
                ): String {
                    capturedGoal = queryGoal
                    return "summary only"
                }
            }
        )

        val result = tool.fetchWebsite("https://example.com")

        assertEquals("summary only", result)
        assertEquals("Summarize website content.", capturedGoal)
    }

    private fun unusedSummarizer(): ContentSummarizer = object : ContentSummarizer {
        override fun summarizeSearchResults(
            query: String,
            queryGoal: String?,
            searchResponse: WebSearchResponse,
            fetchedContent: List<FetchedSearchResultContent>
        ): String {
            error("summary should not be used")
        }

        override fun summarizeWebsiteContent(
            page: WebsiteDocument,
            startChar: Int,
            endCharExclusive: Int,
            returnedText: String,
            returnedTextTruncated: Boolean,
            queryGoal: String?
        ): String {
            error("summary should not be used")
        }
    }
}
