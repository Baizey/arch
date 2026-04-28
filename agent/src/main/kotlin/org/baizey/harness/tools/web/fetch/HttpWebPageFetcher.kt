package org.baizey.harness.tools.fetch

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import org.baizey.harness.tools.http.DEFAULT_USER_AGENT

internal class HttpWebPageFetcher(
    private val httpClient: HttpClient
) : WebPageFetcher {
    override fun fetch(url: String): WebsiteDocument {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isBlank()) throw IllegalArgumentException("URL cannot be blank.")
        val uri = try {
            URI.create(trimmedUrl)
        } catch (_: Exception) {
            throw IllegalArgumentException("Invalid URL: $url")
        }
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            throw IllegalArgumentException("Only http and https URLs are supported: $url")
        }

        val response = httpClient.send(
            HttpRequest.newBuilder(uri)
                .header("User-Agent", DEFAULT_USER_AGENT)
                .header(
                    "Accept",
                    "text/html, application/xhtml+xml, application/xml;q=0.9, text/plain;q=0.8, application/json;q=0.7, */*;q=0.2"
                )
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofByteArray()
        )
        if (response.statusCode() >= 400) {
            throw IllegalStateException("Website returned HTTP ${response.statusCode()}.")
        }
        val contentTypeHeader = response.headers().firstValue("Content-Type").orElse(null)
        val contentType = contentTypeHeader?.takeIf { it.isNotBlank() } ?: "unknown"
        val body = decodeBody(response.body(), contentTypeHeader)
        val normalizedContentType = contentType.lowercase()
        val extraction = when {
            normalizedContentType.contains("text/html") || normalizedContentType.contains("application/xhtml+xml") ->
                HtmlTextExtractor.extractDocument(body)

            normalizedContentType.startsWith("text/") ||
                normalizedContentType.contains("json") ||
                normalizedContentType.contains("xml") ->
                ExtractedWebText(title = null, text = normalizeLineContent(body))

            else -> throw IllegalStateException("Unsupported content type: $contentType")
        }
        val sourceText = truncateFetchedText(extraction.text, MAX_FETCHED_WEBSITE_CHARACTERS)
        return WebsiteDocument(
            requestedUrl = trimmedUrl,
            resolvedUrl = response.uri().toString(),
            statusCode = response.statusCode(),
            contentType = contentType,
            title = extraction.title,
            text = sourceText.text,
            originalCharacterCount = extraction.text.length,
            sourceTruncated = sourceText.truncated
        )
    }
}

private const val MAX_FETCHED_WEBSITE_CHARACTERS = 100_000
