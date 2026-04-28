package org.baizey.harness.tools.search.providers.bing

import org.baizey.runtime.AppConfig
import java.net.URI

internal data class BingWebSearchSettings(
    val apiKey: String?,
    val endpoint: URI = URI.create("https://api.bing.microsoft.com/v7.0/search")
) {
    fun isConfigured(): Boolean = !apiKey.isNullOrBlank()

    companion object {
        fun fromConfig(): BingWebSearchSettings {
            return BingWebSearchSettings(
                apiKey = AppConfig.webSearch.bing.apiKey
            )
        }
    }
}
