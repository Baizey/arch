package org.baizey.harness.tools.search.engine

internal fun queryLooksResearchHeavy(query: String): Boolean {
    val normalized = query.lowercase()
    return normalized.contains('?') ||
        normalized.split(Regex("\\s+")).size >= 7 ||
        RESEARCH_HINTS.any(normalized::contains)
}

private val RESEARCH_HINTS = listOf(
    "latest",
    "recent",
    "today",
    "current",
    "official",
    "release notes",
    "news"
)
