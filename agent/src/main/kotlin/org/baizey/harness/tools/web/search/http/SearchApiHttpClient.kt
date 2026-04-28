package org.baizey.harness.tools.search.http

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import org.baizey.harness.tools.fetch.decodeBody
import org.baizey.harness.tools.http.DEFAULT_USER_AGENT

internal class SearchApiHttpClient(
    private val httpClient: HttpClient
) {
    fun <T> getJson(
        provider: String,
        uri: URI,
        deserialize: (String) -> T,
        configure: HttpRequest.Builder.() -> Unit = {}
    ): T {
        val response = httpClient.send(
            HttpRequest.newBuilder(uri)
                .header("User-Agent", DEFAULT_USER_AGENT)
                .timeout(Duration.ofSeconds(20))
                .GET()
                .apply(configure)
                .build(),
            HttpResponse.BodyHandlers.ofByteArray()
        )
        if (response.statusCode() >= 400) {
            throw IllegalStateException("$provider search failed with HTTP ${response.statusCode()}.")
        }
        val body = decodeBody(response.body(), response.headers().firstValue("Content-Type").orElse(null))
        return deserialize(body)
    }
}
