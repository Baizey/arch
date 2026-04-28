package org.baizey.harness.tools.fetch

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

internal fun decodeBody(bytes: ByteArray, contentType: String?): String {
    val charsetName = contentType
        ?.split(";")
        ?.drop(1)
        ?.map { it.trim() }
        ?.firstOrNull { it.startsWith("charset=", ignoreCase = true) }
        ?.substringAfter('=')
        ?.trim()
        ?.trim('"')
    val charset = runCatching {
        if (charsetName.isNullOrBlank()) StandardCharsets.UTF_8 else Charset.forName(charsetName)
    }.getOrDefault(StandardCharsets.UTF_8)
    return bytes.toString(charset)
}

internal fun normalizeLineContent(raw: String): String {
    return raw.lines()
        .map { it.replace(Regex("\\s+"), " ").trim() }
        .fold(mutableListOf<String>()) { acc, line ->
            if (line.isBlank()) {
                if (acc.isNotEmpty() && acc.last().isNotBlank()) {
                    acc += ""
                }
            } else {
                acc += line
            }
            acc
        }
        .joinToString("\n")
        .trim()
}

internal fun truncateFetchedText(text: String, maxCharacters: Int): TruncatedText {
    if (text.length <= maxCharacters) {
        return TruncatedText(text = text, truncated = false)
    }
    val provisional = text.take(maxCharacters)
    val breakpoint = maxOf(provisional.lastIndexOf('\n'), provisional.lastIndexOf(' '))
    val trimmed = if (breakpoint >= maxCharacters / 2) {
        provisional.substring(0, breakpoint)
    } else {
        provisional
    }.trimEnd()
    return TruncatedText(text = trimmed, truncated = true)
}

internal data class TruncatedText(
    val text: String,
    val truncated: Boolean
)
