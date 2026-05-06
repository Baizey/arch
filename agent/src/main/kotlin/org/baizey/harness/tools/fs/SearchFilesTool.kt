package org.baizey.harness.tools.fs

import dev.langchain4j.agent.tool.P
import dev.langchain4j.agent.tool.Tool
import org.baizey.harness.policy.UserPathPolicyLogic
import org.baizey.harness.policy.path.FsAccessType.READ
import org.baizey.runtime.ErrorLog
import org.baizey.utils.IO.canReadAsText
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.notExists

class SearchFilesTool(
    private val userPathPolicyLogic: UserPathPolicyLogic
) {
    @Tool(
        name = "search_files",
        value = ["""Search one file directly or one directory recursively for text. Returns matching files with 0-based line numbers.
Use glob to narrow which files are searched inside a directory. If query is omitted or blank, skips content search and returns files matched by path and glob.
If shouldUseRegex is omitted, behaves as false. If shouldIgnoreCase is omitted, behaves as false. If maxMatches is omitted, behaves as 100. If contextLines is omitted, behaves as 0.
Example: search_files(path="C:/repo", query="PathPolicyLogic", glob="*.kt")"""]
    )
    fun searchFiles(
        @P("Absolute or relative path to one file or one directory.")
        path: String,
        @P("Optional text to search for, or a regex pattern if shouldUseRegex is true. If omitted or blank, returns files matched by path and glob without reading contents.")
        query: String? = null,
        @P("Optional glob filter when path is a directory, for example *.kt.")
        glob: String? = null,
        @P("Whether to interpret query as a regex pattern. If omitted, behaves as false.")
        shouldUseRegex: Boolean? = null,
        @P("Whether matching should ignore case. If omitted, behaves as false.")
        shouldIgnoreCase: Boolean? = null,
        @P("Maximum matches to return across all files. If omitted, behaves as 100.")
        maxMatches: Int? = null,
        @P("Context lines to include before and after each match. If omitted, behaves as 0.")
        contextLines: Int? = null
    ): String {
        val normalizedPath = try {
            FsToolSupport.normalizePath(path)
        } catch (_: Exception) {
            return "Path is invalid: $path"
        }

        val actualGlob = glob?.trim().orEmpty()
        val actualQuery = query?.takeIf { it.isNotBlank() }
        val actualRegex = shouldUseRegex ?: false
        val actualIgnoreCase = shouldIgnoreCase ?: false
        val actualMaxMatches = maxMatches ?: FsToolSupport.DEFAULT_MAX_SEARCH_MATCHES
        val actualContextLines = contextLines ?: 0

        if (actualMaxMatches <= 0) return "maxMatches must be greater than 0"
        if (actualContextLines < 0) return "contextLines must be at least 0"

        userPathPolicyLogic.evaluate(normalizedPath.toString(), READ).toDenyReasonOrNull()?.let { return it }
        val matcher = try {
            actualQuery?.let { buildMatcher(it, actualRegex, actualIgnoreCase) }
        } catch (_: IllegalArgumentException) {
            return "Regex query is invalid: $actualQuery"
        }

        return try {
            if (normalizedPath.notExists()) return "Path not found: $normalizedPath"

            val files = when {
                normalizedPath.isRegularFile() -> listOf(normalizedPath)
                normalizedPath.isDirectory() -> collectFiles(normalizedPath, actualGlob)
                else -> return "Not a file or directory: $normalizedPath"
            }

            if (files.isEmpty()) {
                return buildString {
                    appendLine("Path: $normalizedPath")
                    appendLine("Query: ${renderQuery(actualQuery)}")
                    if (actualGlob.isNotBlank()) appendLine("Glob: $actualGlob")
                    appendLine("Mode: ${searchModeLabel(actualQuery)}")
                    appendLine("Matches: 0")
                    appendLine("Files with matches: 0")
                    append("Truncated: false")
                }
            }

            if (actualQuery == null) {
                return renderFileNameMatches(
                    path = normalizedPath,
                    query = actualQuery,
                    glob = actualGlob,
                    files = files,
                    maxMatches = actualMaxMatches
                )
            }
            val contentMatcher = matcher ?: error("Content matcher must exist when query is present")

            val fileBlocks = mutableListOf<String>()
            var totalMatches = 0
            var truncated = false

            for (file in files.filter { it.canReadAsText() }) {
                if (totalMatches >= actualMaxMatches) {
                    truncated = true
                    break
                }
                val lines = try {
                    Files.readAllLines(file)
                } catch (e: Exception) {
                    fileBlocks += buildString {
                        appendLine("File: ${file.toAbsolutePath().normalize()}")
                        appendLine("Matches: 0")
                        append("Error: ${e.message ?: e::class.simpleName.orEmpty()}")
                    }
                    continue
                }

                val matches = findMatches(lines, contentMatcher, actualContextLines, actualMaxMatches - totalMatches)
                if (matches.isEmpty()) continue

                totalMatches += matches.size
                if (totalMatches >= actualMaxMatches &&
                    hasMoreMatches(lines, contentMatcher, matches.last().matchLine)
                ) {
                    truncated = true
                }
                fileBlocks += buildString {
                    appendLine("File: ${file.toAbsolutePath().normalize()}")
                    appendLine("Matches: ${matches.size}")
                    appendLine("---")
                    append(matches.joinToString("\n---\n") { it.render() })
                }
            }

            buildString {
                appendLine("Path: $normalizedPath")
                appendLine("Query: ${renderQuery(actualQuery)}")
                if (actualGlob.isNotBlank()) appendLine("Glob: $actualGlob")
                appendLine("Mode: ${searchModeLabel(actualQuery)}")
                appendLine("Regex: $actualRegex")
                appendLine("Ignore case: $actualIgnoreCase")
                appendLine("Context lines: $actualContextLines")
                appendLine("Matches: $totalMatches")
                appendLine("Files with matches: ${fileBlocks.count { it.startsWith("File: ") }}")
                appendLine("Truncated: $truncated")
                append("---")
                if (fileBlocks.isNotEmpty()) {
                    appendLine()
                    append(fileBlocks.joinToString("\n---\n"))
                }
            }
        } catch (_: IllegalArgumentException) {
            "Glob is invalid: $actualGlob"
        } catch (e: Exception) {
            ErrorLog.log(
                source = "search_files.search",
                exception = e,
                context = mapOf(
                    "path" to normalizedPath.toString(),
                    "query" to (actualQuery ?: "<none>"),
                    "glob" to actualGlob.ifBlank { "<none>" },
                    "regex" to actualRegex.toString(),
                    "ignoreCase" to actualIgnoreCase.toString(),
                    "maxMatches" to actualMaxMatches.toString(),
                    "contextLines" to actualContextLines.toString()
                )
            )
            buildSearchFailureResponse(normalizedPath, actualQuery, e)
        }
    }

    private fun collectFiles(root: Path, glob: String): List<Path> {
        val matcher = if (glob.isNotBlank()) root.fileSystem.getPathMatcher("glob:**/$glob") else null
        return Files.walk(root).use { paths ->
            paths
                .filter { it.isRegularFile() }
                .filter { matcher == null || matcher.matches(it) || matcher.matches(root.relativize(it)) }
                .sorted()
                .toList()
        }
    }

    private fun buildMatcher(query: String, regex: Boolean, ignoreCase: Boolean): (String) -> Boolean {
        return if (regex) {
            val compiled = if (ignoreCase) query.toRegex(RegexOption.IGNORE_CASE) else query.toRegex()
            ({ line: String -> compiled.containsMatchIn(line) })
        } else {
            ({ line: String -> line.contains(query, ignoreCase) })
        }
    }

    private fun renderFileNameMatches(
        path: Path,
        query: String?,
        glob: String,
        files: List<Path>,
        maxMatches: Int
    ): String {
        val matchingFiles = files.take(maxMatches)
        val truncated = files.size > maxMatches
        return buildString {
            appendLine("Path: $path")
            appendLine("Query: ${renderQuery(query)}")
            if (glob.isNotBlank()) appendLine("Glob: $glob")
            appendLine("Mode: ${searchModeLabel(query)}")
            appendLine("Matches: ${matchingFiles.size}")
            appendLine("Files with matches: ${matchingFiles.size}")
            appendLine("Truncated: $truncated")
            append("---")
            if (matchingFiles.isNotEmpty()) {
                appendLine()
                append(
                    matchingFiles.joinToString("\n---\n") { file ->
                        "File: ${file.toAbsolutePath().normalize()}"
                    }
                )
            }
        }
    }

    private fun renderQuery(query: String?): String {
        return query?.let { "\"$it\"" } ?: "<none>"
    }

    private fun searchModeLabel(query: String?): String {
        return if (query == null) "file_names" else "content"
    }

    private fun findMatches(
        lines: List<String>,
        matcher: (String) -> Boolean,
        contextLines: Int,
        remainingMatches: Int
    ): List<SearchMatchBlock> {
        val results = mutableListOf<SearchMatchBlock>()
        for ((lineIndex, line) in lines.withIndex()) {
            if (!matcher(line)) continue
            val fromLine = maxOf(0, lineIndex - contextLines)
            val toLineExclusive = minOf(lines.size, lineIndex + contextLines + 1)
            results += SearchMatchBlock(
                matchLine = lineIndex,
                fromLine = fromLine,
                toLineExclusive = toLineExclusive,
                lines = lines.subList(fromLine, toLineExclusive)
            )
            if (results.size >= remainingMatches) break
        }
        return results
    }

    private fun hasMoreMatches(lines: List<String>, matcher: (String) -> Boolean, fromLine: Int): Boolean {
        return lines.drop(fromLine + 1).any(matcher)
    }

    private data class SearchMatchBlock(
        val matchLine: Int,
        val fromLine: Int,
        val toLineExclusive: Int,
        val lines: List<String>
    ) {
        fun render(): String {
            return buildString {
                appendLine("Range: [$fromLine..$toLineExclusive[")
                append(lines.mapIndexed { index, line ->
                    "%4d | %s".format(fromLine + index, line)
                }.joinToString("\n"))
            }
        }
    }

    private fun buildSearchFailureResponse(path: Path, query: String?, exception: Exception): String {
        return buildString {
            appendLine("File search failed.")
            appendLine("Path: $path")
            appendLine("Query: ${renderQuery(query)}")
            appendLine("Reason: ${exception.message ?: exception::class.java.simpleName}")
            appendLine("Next steps:")
            appendLine("- Retry search_files with a narrower path or smaller scope.")
            appendLine("- If you used regex=true or glob, simplify the pattern and retry.")
            appendLine("- Use list_directory or read_file directly if you already know the likely file to inspect.")
        }.trimEnd()
    }
}
