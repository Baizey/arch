package org.baizey.commands.utils

import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.baizey.runtime.AppConfig
import org.baizey.runtime.ErrorLog
import org.baizey.runtime.OpenAiConfig
import org.baizey.runtime.OllamaConfig
import org.baizey.runtime.ProviderConfig
import org.baizey.runtime.agentic.instance.ProviderType
import org.baizey.utils.IO.json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

data class SupportedModelOption(
    val id: String,
    val name: String,
    val provider: ProviderType
) {
    val label: String
        get() = "$name (${provider.displayName})"

    companion object {
        fun create(provider: ProviderType, name: String): SupportedModelOption {
            val normalizedName = name.trim()
            require(normalizedName.isNotEmpty()) { "Model name cannot be blank." }
            return SupportedModelOption(
                id = "${provider.name.lowercase()}|$normalizedName",
                name = normalizedName,
                provider = provider
            )
        }
    }
}

internal class ProviderModelCatalog(
    private val configProvider: () -> ProviderConfig = { AppConfig.providers },
    private val ollamaModelsLoader: (OllamaConfig) -> List<String> = ::fetchOllamaModels,
    private val openAiModelsLoader: (OpenAiConfig) -> List<String> = ::fetchOpenAiModels
) {
    fun load(): List<SupportedModelOption> {
        val config = configProvider()
        return buildList {
            if (config.ollama.baseUrl != null) {
                addAll(loadProviderModels(ProviderType.OLLAMA) {
                    ollamaModelsLoader(config.ollama)
                })
            }
            if (config.openai.baseUrl != null && config.openai.apiKey != null) {
                addAll(loadProviderModels(ProviderType.OPENAI) {
                    openAiModelsLoader(config.openai)
                })
            }
        }
            .sortedWith(compareBy({ it.provider.name }, { it.name.lowercase() }))
    }

    private fun loadProviderModels(
        provider: ProviderType,
        loader: () -> List<String>
    ): List<SupportedModelOption> {
        return runCatching {
            loader()
                .asSequence()
                .map(String::trim)
                .filter(String::isNotEmpty)
                .distinct()
                .sorted()
                .map { SupportedModelOption.create(provider, it) }
                .toList()
        }.getOrElse { exception ->
            ErrorLog.log(
                source = "model-discovery-${provider.displayName.lowercase()}",
                exception = exception
            )
            emptyList()
        }
    }
}

private fun fetchOllamaModels(config: OllamaConfig): List<String> {
    val baseUrl = requireNotNull(config.baseUrl) { "OLLAMA_BASE_URL is required to fetch Ollama models." }
    val response = getJson(URI.create("${baseUrl.trimEnd('/')}/api/tags"))
    val root = json.parseToJsonElement(response).jsonObject
    return root["models"]
        ?.jsonArray
        ?.mapNotNull { entry ->
            entry.jsonObject["name"]?.jsonPrimitive?.contentOrNull
        }
        ?: emptyList()
}

private fun fetchOpenAiModels(config: OpenAiConfig): List<String> {
    val baseUrl = requireNotNull(config.baseUrl) { "OPENAI_BASE_URL is required to fetch OpenAI models." }
    val apiKey = requireNotNull(config.apiKey) { "OPENAI_API_KEY is required to fetch OpenAI models." }
    val response = getJson(
        uri = URI.create("${baseUrl.trimEnd('/')}/models")
    ) {
        header("Authorization", "Bearer $apiKey")
    }
    val root = json.parseToJsonElement(response).jsonObject
    return root["data"]
        ?.jsonArray
        ?.mapNotNull { entry ->
            entry.jsonObject["id"]?.jsonPrimitive?.contentOrNull
        }
        ?: emptyList()
}

private fun getJson(
    uri: URI,
    configure: HttpRequest.Builder.() -> Unit = {}
): String {
    val request = HttpRequest.newBuilder(uri)
        .timeout(Duration.ofSeconds(10))
        .header("Accept", "application/json")
        .GET()
        .apply(configure)
        .build()

    val response = httpClient().send(request, HttpResponse.BodyHandlers.ofString())
    if (response.statusCode() !in 200..299) {
        throw IllegalStateException("Model discovery failed for $uri with HTTP ${response.statusCode()}.")
    }
    return response.body()
}

private fun httpClient(): HttpClient {
    return HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()
}
