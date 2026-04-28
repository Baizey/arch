package org.baizey.harness.tools.fetch

internal interface WebPageFetcher {
    fun fetch(url: String): WebsiteDocument
}
