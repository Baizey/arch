package org.baizey.harness.tools.search.providers.brave

import org.baizey.runtime.AppConfig
import java.net.URI

internal data class BraveWebSearchSettings(
    val apiKey: String?,
    val endpoint: URI = URI.create("https://api.search.brave.com/res/v1/web/search")
) {
    fun isConfigured(): Boolean = !apiKey.isNullOrBlank()

    companion object {
        fun fromConfig(): BraveWebSearchSettings {
            return BraveWebSearchSettings(
                apiKey = AppConfig.webSearch.brave.apiKey
            )
        }
    }
}
