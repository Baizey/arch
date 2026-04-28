package org.baizey.harness.tools.web

import org.baizey.harness.tools.fetch.WebPageFetcher
import org.baizey.harness.tools.fetch.WebsiteDocument
import org.baizey.harness.tools.search.FetchedSearchResultContent
import org.baizey.harness.tools.search.WebSearchEngine
import org.baizey.harness.tools.search.WebSearchProviderDescriptor
import org.baizey.harness.tools.search.WebSearchResponse
import org.baizey.harness.tools.search.WebSearchResult
import org.baizey.harness.tools.web.summary.ContentSummarizer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SearchWebToolTest {
    @Test
    fun `returns compact search results with chosen provider and fetch hint`() {
        val tool = SearchWebTool(
            searchEngine = object : WebSearchEngine {
                override fun search(query: String, maxResults: Int): WebSearchResponse {
                    assertEquals("kotlin release notes", query)
                    assertEquals(2, maxResults)
                    return WebSearchResponse(
                        provider = WebSearchProviderDescriptor("bing", "Bing"),
                        results = listOf(
                            WebSearchResult(
                                title = "Kotlin Releases",
                                url = "https://kotlinlang.org/docs/releases.html",
                                snippet = "Latest Kotlin release notes."
                            ),
                            WebSearchResult(
                                title = "GitHub Releases",
                                url = "https://github.com/JetBrains/kotlin/releases",
                                snippet = ""
                            )
                        )
                    )
                }
            },
            webPageFetcher = unusedPageFetcher(),
            webContentSummarizer = unusedSummarizer()
        )

        val result = tool.searchWeb("kotlin release notes", maxResults = 2, shouldSummarizeWithAi = false)

        assertTrue(result.contains("Query: kotlin release notes"), result)
        assertTrue(result.contains("Provider: Bing (bing)"), result)
        assertTrue(result.contains("Results: 2"), result)
        assertTrue(result.contains("Next step: use fetch_website on one result URL"), result)
        assertTrue(result.contains("1. Kotlin Releases"), result)
        assertTrue(result.contains("URL: https://kotlinlang.org/docs/releases.html"), result)
        assertTrue(result.contains("Snippet: Latest Kotlin release notes."), result)
        assertTrue(result.contains("Snippet: <empty>"), result)
    }

    @Test
    fun `rejects blank queries`() {
        val tool = SearchWebTool(
            searchEngine = object : WebSearchEngine {
                override fun search(query: String, maxResults: Int): WebSearchResponse {
                    return WebSearchResponse(provider = null, results = emptyList())
                }
            },
            webPageFetcher = unusedPageFetcher(),
            webContentSummarizer = unusedSummarizer()
        )

        assertEquals("Query cannot be blank.", tool.searchWeb("   "))
    }

    @Test
    fun `uses ai summary by default and fetches top result content`() {
        var capturedQuery: String? = null
        var capturedQueryGoal: String? = null
        var capturedSearchResponse: WebSearchResponse? = null
        var capturedFetchedContent: List<FetchedSearchResultContent>? = null
        val tool = SearchWebTool(
            searchEngine = object : WebSearchEngine {
                override fun search(query: String, maxResults: Int): WebSearchResponse {
                    return WebSearchResponse(
                        provider = WebSearchProviderDescriptor("brave", "Brave"),
                        results = listOf(
                            WebSearchResult(
                                title = "Official docs",
                                url = "https://example.com/docs",
                                snippet = "Primary source."
                            )
                        )
                    )
                }
            },
            webPageFetcher = object : WebPageFetcher {
                override fun fetch(url: String): WebsiteDocument {
                    assertEquals("https://example.com/docs", url)
                    return WebsiteDocument(
                        requestedUrl = url,
                        resolvedUrl = "$url/resolved",
                        statusCode = 200,
                        contentType = "text/html",
                        title = "Example Docs",
                        text = "alpha beta gamma",
                        originalCharacterCount = 16,
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
                ): String {
                    capturedQuery = query
                    capturedQueryGoal = queryGoal
                    capturedSearchResponse = searchResponse
                    capturedFetchedContent = fetchedContent
                    return "summary only"
                }

                override fun summarizeWebsiteContent(
                    page: WebsiteDocument,
                    startChar: Int,
                    endCharExclusive: Int,
                    returnedText: String,
                    returnedTextTruncated: Boolean,
                    queryGoal: String?
                ): String = error("not used")
            }
        )

        val result = tool.searchWeb(
            query = " kotlin release notes ",
            maxResults = 1,
            queryGoal = "find the official source"
        )

        assertEquals("summary only", result)
        assertEquals("kotlin release notes", capturedQuery)
        assertEquals("find the official source", capturedQueryGoal)
        assertEquals("brave", capturedSearchResponse?.provider?.id)
        assertEquals(1, capturedFetchedContent?.size)
        assertEquals("https://example.com/docs/resolved", capturedFetchedContent?.single()?.resolvedUrl)
        assertEquals("Example Docs", capturedFetchedContent?.single()?.pageTitle)
        assertEquals("alpha beta gamma", capturedFetchedContent?.single()?.excerpt)
        assertTrue(!result.contains("URL:"), result)
    }

    @Test
    fun `captures fetch errors for summary instead of failing the search`() {
        var capturedFetchedContent: List<FetchedSearchResultContent>? = null
        val tool = SearchWebTool(
            searchEngine = object : WebSearchEngine {
                override fun search(query: String, maxResults: Int): WebSearchResponse {
                    return WebSearchResponse(
                        provider = WebSearchProviderDescriptor("google", "Google"),
                        results = listOf(
                            WebSearchResult(
                                title = "Official docs",
                                url = "https://example.com/docs",
                                snippet = "Primary source."
                            )
                        )
                    )
                }
            },
            webPageFetcher = object : WebPageFetcher {
                override fun fetch(url: String): WebsiteDocument {
                    error("timeout")
                }
            },
            webContentSummarizer = object : ContentSummarizer {
                override fun summarizeSearchResults(
                    query: String,
                    queryGoal: String?,
                    searchResponse: WebSearchResponse,
                    fetchedContent: List<FetchedSearchResultContent>
                ): String {
                    capturedFetchedContent = fetchedContent
                    return "summary only"
                }

                override fun summarizeWebsiteContent(
                    page: WebsiteDocument,
                    startChar: Int,
                    endCharExclusive: Int,
                    returnedText: String,
                    returnedTextTruncated: Boolean,
                    queryGoal: String?
                ): String = error("not used")
            }
        )

        assertEquals("summary only", tool.searchWeb("kotlin release notes"))
        assertEquals("timeout", capturedFetchedContent?.single()?.fetchError)
        assertNull(capturedFetchedContent?.single()?.excerpt)
    }

    @Test
    fun `falls back to raw results when search summary fails`() {
        val tool = SearchWebTool(
            searchEngine = object : WebSearchEngine {
                override fun search(query: String, maxResults: Int): WebSearchResponse {
                    return WebSearchResponse(
                        provider = WebSearchProviderDescriptor("bing", "Bing"),
                        results = listOf(
                            WebSearchResult(
                                title = "Kotlin Releases",
                                url = "https://kotlinlang.org/docs/releases.html",
                                snippet = "Latest Kotlin release notes."
                            )
                        )
                    )
                }
            },
            webPageFetcher = unusedPageFetcher(),
            webContentSummarizer = object : ContentSummarizer {
                override fun summarizeSearchResults(
                    query: String,
                    queryGoal: String?,
                    searchResponse: WebSearchResponse,
                    fetchedContent: List<FetchedSearchResultContent>
                ): String {
                    error("summary unavailable")
                }

                override fun summarizeWebsiteContent(
                    page: WebsiteDocument,
                    startChar: Int,
                    endCharExclusive: Int,
                    returnedText: String,
                    returnedTextTruncated: Boolean,
                    queryGoal: String?
                ): String = error("not used")
            }
        )

        val result = tool.searchWeb("kotlin release notes")

        assertTrue(result.contains("Search summary failed. Returning raw results instead."), result)
        assertTrue(result.contains("Provider: Bing (bing)"), result)
        assertTrue(result.contains("URL: https://kotlinlang.org/docs/releases.html"), result)
    }

    @Test
    fun `returns actionable message when search fails`() {
        val tool = SearchWebTool(
            searchEngine = object : WebSearchEngine {
                override fun search(query: String, maxResults: Int): WebSearchResponse {
                    error("provider outage")
                }
            },
            webPageFetcher = unusedPageFetcher(),
            webContentSummarizer = unusedSummarizer()
        )

        val result = tool.searchWeb("kotlin release notes", shouldSummarizeWithAi = false)

        assertTrue(result.contains("Search failed for query: kotlin release notes"), result)
        assertTrue(result.contains("Retry search_web with a narrower or broader query."), result)
        assertTrue(result.contains("use fetch_website directly"), result)
    }

    @Test
    fun `returns actionable message when no results are found`() {
        val tool = SearchWebTool(
            searchEngine = object : WebSearchEngine {
                override fun search(query: String, maxResults: Int): WebSearchResponse {
                    return WebSearchResponse(
                        provider = WebSearchProviderDescriptor("bing", "Bing"),
                        results = emptyList()
                    )
                }
            },
            webPageFetcher = unusedPageFetcher(),
            webContentSummarizer = unusedSummarizer()
        )

        val result = tool.searchWeb("kotlin release notes", shouldSummarizeWithAi = false)

        assertTrue(result.contains("No search results found."), result)
        assertTrue(result.contains("Provider: Bing"), result)
        assertTrue(result.contains("If you know a URL already, use fetch_website directly."), result)
    }

    @Test
    fun `fetches at most three search results for ai summary`() {
        val fetchedUrls = mutableListOf<String>()
        val tool = SearchWebTool(
            searchEngine = object : WebSearchEngine {
                override fun search(query: String, maxResults: Int): WebSearchResponse {
                    return WebSearchResponse(
                        provider = WebSearchProviderDescriptor("bing", "Bing"),
                        results = (1..4).map { index ->
                            WebSearchResult(
                                title = "Result $index",
                                url = "https://example.com/$index",
                                snippet = "Snippet $index"
                            )
                        }
                    )
                }
            },
            webPageFetcher = object : WebPageFetcher {
                override fun fetch(url: String): WebsiteDocument {
                    fetchedUrls += url
                    return WebsiteDocument(
                        requestedUrl = url,
                        resolvedUrl = url,
                        statusCode = 200,
                        contentType = "text/html",
                        title = "Title",
                        text = "content",
                        originalCharacterCount = 7,
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
                ): String = "summary only"

                override fun summarizeWebsiteContent(
                    page: WebsiteDocument,
                    startChar: Int,
                    endCharExclusive: Int,
                    returnedText: String,
                    returnedTextTruncated: Boolean,
                    queryGoal: String?
                ): String = error("not used")
            }
        )

        val result = tool.searchWeb("kotlin release notes")

        assertEquals("summary only", result)
        assertEquals(
            listOf("https://example.com/1", "https://example.com/2", "https://example.com/3"),
            fetchedUrls
        )
    }

    private fun unusedPageFetcher(): WebPageFetcher = object : WebPageFetcher {
        override fun fetch(url: String): WebsiteDocument {
            error("page fetch should not be used")
        }
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
