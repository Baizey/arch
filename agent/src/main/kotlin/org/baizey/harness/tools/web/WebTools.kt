package org.baizey.harness.tools.web

import java.net.http.HttpClient
import java.time.Duration
import org.baizey.commands.utils.ModelSelection
import org.baizey.harness.tools.fetch.HttpWebPageFetcher
import org.baizey.harness.tools.search.engine.MultiProviderWebSearchEngine
import org.baizey.harness.tools.search.http.SearchApiHttpClient
import org.baizey.harness.tools.search.providers.bing.BingWebSearchProvider
import org.baizey.harness.tools.web.search.providers.brave.BraveWebSearchProvider
import org.baizey.harness.tools.search.providers.duckduckgo.DuckDuckGoSearchProvider
import org.baizey.harness.tools.search.providers.google.GoogleCustomSearchProvider
import org.baizey.harness.tools.summary.CurrentModelContentSummarizer

internal object WebTools {
    fun create(): List<Any> {
        val httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
        val webPageFetcher = HttpWebPageFetcher(httpClient)
        val searchApiHttpClient = SearchApiHttpClient(httpClient)
        val webContentSummarizer = CurrentModelContentSummarizer(
            modelStrategy = { ModelSelection.current() }
        )
        return listOf(
            SearchWebTool(
                searchEngine = MultiProviderWebSearchEngine(
                    listOf(
                        BraveWebSearchProvider(searchApiHttpClient),
                        BingWebSearchProvider(searchApiHttpClient),
                        GoogleCustomSearchProvider(searchApiHttpClient),
                        DuckDuckGoSearchProvider()
                    )
                ),
                webPageFetcher = webPageFetcher,
                webContentSummarizer = webContentSummarizer
            ),
            FetchWebsiteTool(webPageFetcher, webContentSummarizer)
        )
    }
}
