package org.baizey.harness.tools.fetch

import java.io.StringReader
import javax.swing.text.MutableAttributeSet
import javax.swing.text.html.HTML
import javax.swing.text.html.HTMLEditorKit
import javax.swing.text.html.parser.ParserDelegator

internal object HtmlTextExtractor {
    private val suppressedTagNames = setOf("script", "style", "noscript", "iframe", "option")
    private val blockTagNames = setOf(
        "p",
        "div",
        "li",
        "ul",
        "ol",
        "br",
        "tr",
        "table",
        "pre",
        "blockquote",
        "h1",
        "h2",
        "h3",
        "h4",
        "h5",
        "h6"
    )

    fun extractDocument(html: String): ExtractedWebText {
        val textBuilder = StringBuilder()
        val titleBuilder = StringBuilder()
        var suppressDepth = 0
        var inTitle = false
        ParserDelegator().parse(StringReader(html), object : HTMLEditorKit.ParserCallback() {
            override fun handleStartTag(tag: HTML.Tag, attributes: MutableAttributeSet, position: Int) {
                val tagName = tag.toString().lowercase()
                if (tag == HTML.Tag.TITLE) {
                    inTitle = true
                    return
                }
                if (tagName in suppressedTagNames) {
                    suppressDepth += 1
                    return
                }
                if (tagName in blockTagNames) {
                    appendParagraphBreak(textBuilder)
                }
            }

            override fun handleEndTag(tag: HTML.Tag, position: Int) {
                val tagName = tag.toString().lowercase()
                if (tag == HTML.Tag.TITLE) {
                    inTitle = false
                    return
                }
                if (tagName in suppressedTagNames && suppressDepth > 0) {
                    suppressDepth -= 1
                    return
                }
                if (tagName in blockTagNames) {
                    appendParagraphBreak(textBuilder)
                }
            }

            override fun handleSimpleTag(tag: HTML.Tag, attributes: MutableAttributeSet, position: Int) {
                if (tag.toString().lowercase() in blockTagNames) {
                    appendParagraphBreak(textBuilder)
                }
            }

            override fun handleText(data: CharArray, position: Int) {
                val raw = String(data)
                if (inTitle) {
                    appendText(titleBuilder, raw)
                    return
                }
                if (suppressDepth > 0) return
                appendText(textBuilder, raw)
            }
        }, true)
        return ExtractedWebText(
            title = normalizeLineContent(titleBuilder.toString()).ifBlank { null },
            text = normalizeLineContent(textBuilder.toString())
        )
    }

    fun extractPlainText(fragment: String): String {
        return extractDocument(fragment).text.ifBlank { normalizeLineContent(fragment) }
    }
}

internal data class ExtractedWebText(
    val title: String?,
    val text: String
)

private fun appendText(target: StringBuilder, raw: String) {
    val normalized = raw.replace(Regex("\\s+"), " ").trim()
    if (normalized.isBlank()) return
    val needsSpace = target.isNotEmpty() && !target.last().isWhitespace()
    if (needsSpace) target.append(' ')
    target.append(normalized)
}

private fun appendParagraphBreak(target: StringBuilder) {
    if (target.isEmpty()) return
    if (!target.endsWith("\n\n")) {
        if (!target.endsWith("\n")) target.append('\n')
        target.append('\n')
    }
}
