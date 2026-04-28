package org.baizey.harness.tools.fetch

internal data class WebsiteDocument(
    val requestedUrl: String,
    val resolvedUrl: String,
    val statusCode: Int,
    val contentType: String,
    val title: String?,
    val text: String,
    val originalCharacterCount: Int,
    val sourceTruncated: Boolean
)
