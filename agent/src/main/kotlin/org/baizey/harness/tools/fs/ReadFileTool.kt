package org.baizey.harness.tools.fs

import dev.langchain4j.agent.tool.P
import dev.langchain4j.agent.tool.Tool
import org.baizey.harness.policy.UserPathPolicyLogic
import org.baizey.harness.policy.path.FsAccessType.READ
import org.baizey.runtime.ErrorLog
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.notExists

class ReadFileTool(
    private val userPathPolicyLogic: UserPathPolicyLogic
) {
    @Tool(
        name = "read_file",
        value = ["""Read a slice of a file by 0-based line numbers. Use startLine and endLineExclusive.
Output lines are numbered like "  42 | content" so the same range can be passed to edit_file.
Read small windows around the lines you care about; the header shows the total line count and the exact half-open range returned.
If startLine is omitted, behaves as 0. If endLineExclusive is omitted, behaves as the file end. If maxLines is omitted, behaves as 200.
Example: read_file(path="C:/repo/app.kt", startLine=40, endLineExclusive=80, maxLines=80)"""]
    )
    fun readFile(
        @P("Absolute or relative path to the file.")
        path: String,
        @P("0-based line index to start reading from. If omitted, behaves as 0.")
        startLine: Int? = null,
        @P("0-based line index to stop reading before. If omitted, behaves as the file end.")
        endLineExclusive: Int? = null,
        @P("Maximum lines to return. If omitted, behaves as 200.")
        maxLines: Int? = null
    ): String {
        val actualStartLine = startLine ?: 0
        val actualMaxLines = maxLines ?: FsToolSupport.DEFAULT_MAX_READ_LINES
        val file = try {
            FsToolSupport.normalizePath(path)
        } catch (_: Exception) {
            return "Path is invalid: $path"
        }

        userPathPolicyLogic.evaluate(file.toString(), READ).toDenyReasonOrNull()?.let { return it }
        if (actualStartLine < 0) return "startLine must be at least 0"
        if (actualMaxLines <= 0) return "maxLines must be greater than 0"

        return try {
            if (file.notExists()) return "File not found: $file"
            if (!file.isRegularFile()) return "Not a file: $file"

            val lines = Files.readAllLines(file)
            val total = lines.size
            if (actualStartLine > total) return "startLine must be at most $total"
            val requestedEndLineExclusive = minOf(endLineExclusive ?: total, total)
            if (requestedEndLineExclusive < actualStartLine) return "endLineExclusive must be >= startLine"

            val actualEndLineExclusive = minOf(requestedEndLineExclusive, actualStartLine + actualMaxLines)
            val sliced = lines.subList(actualStartLine, actualEndLineExclusive)
            val truncated = actualEndLineExclusive < requestedEndLineExclusive
            val numbered = if (sliced.isEmpty()) {
                "(no lines in range)"
            } else {
                sliced.mapIndexed { i, line -> "%4d | %s".format(actualStartLine + i, line) }.joinToString("\n")
            }
            """File: $file
Total lines: $total
Read range: [$actualStartLine..$actualEndLineExclusive[
Returned lines: ${sliced.size}
Truncated: $truncated
---
$numbered"""
        } catch (e: Exception) {
            ErrorLog.log(
                source = "read_file.read",
                exception = e,
                context = mapOf(
                    "path" to file.toString(),
                    "startLine" to actualStartLine.toString(),
                    "endLineExclusive" to (endLineExclusive?.toString() ?: "<file-end>"),
                    "maxLines" to actualMaxLines.toString()
                )
            )
            buildReadFailureResponse(file, e)
        }
    }

    private fun buildReadFailureResponse(file: Path, exception: Exception): String {
        return buildString {
            appendLine("File read failed.")
            appendLine("File: $file")
            appendLine("Reason: ${exception.message ?: exception::class.java.simpleName}")
            appendLine("Next steps:")
            appendLine("- Retry read_file if the failure may be transient.")
            appendLine("- Use list_directory on the parent path to confirm the file still exists.")
            appendLine("- If the file is binary or uses an unusual text encoding, avoid reading it as plain text.")
        }.trimEnd()
    }
}
