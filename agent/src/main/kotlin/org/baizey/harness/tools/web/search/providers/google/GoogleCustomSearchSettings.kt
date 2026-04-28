package org.baizey.harness.tools.search.providers.google

import org.baizey.runtime.AppConfig
import java.net.URI

internal data class GoogleCustomSearchSettings(
    val apiKey: String?,
    val searchEngineId: String?,
    val endpoint: URI = URI.create("https://www.googleapis.com/customsearch/v1")
) {
    fun isConfigured(): Boolean = !apiKey.isNullOrBlank() && !searchEngineId.isNullOrBlank()

    companion object {
        fun fromConfig(): GoogleCustomSearchSettings {
            return GoogleCustomSearchSettings(
                apiKey = AppConfig.webSearch.google.apiKey,
                searchEngineId = AppConfig.webSearch.google.searchEngineId
            )
        }
    }
}
