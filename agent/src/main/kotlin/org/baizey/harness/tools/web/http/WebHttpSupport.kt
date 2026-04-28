package org.baizey.harness.tools.http

import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal const val DEFAULT_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36 small-agent/1.0"

internal fun buildUri(base: URI, params: Map<String, String>): URI {
    val encoded = params.entries.joinToString("&") {
        "${URLEncoder.encode(it.key, StandardCharsets.UTF_8)}=${
            URLEncoder.encode(it.value, StandardCharsets.UTF_8)
        }"
    }
    val separator = if (base.toString().contains("?")) "&" else "?"
    return URI.create(base.toString() + separator + encoded)
}
