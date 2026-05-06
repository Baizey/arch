package org.baizey.harness.tools.fs

import dev.langchain4j.agent.tool.P
import dev.langchain4j.agent.tool.Tool
import org.baizey.harness.policy.PathPolicyLogic
import org.baizey.harness.policy.path.FsAccessType.WRITE
import org.baizey.runtime.ErrorLog
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.notExists

class EditFileTool(
    private val pathPolicyLogic: PathPolicyLogic
) {
    @Tool(
        name = "edit_file",
        value = ["""Replace the 0-based half-open line range [startLine..endLineExclusive[ with newText.
startLine is inclusive, endLineExclusive is exclusive. newText may contain multiple lines.
Pass an empty newText to delete the selected lines.
Use read_file first, then pass the exact line range back here. Use write_file only when replacing the whole file.
Example: edit_file(path="C:/repo/app.kt", startLine=10, endLineExclusive=12, newText="updated text", expectedOldText="old text")"""]
    )
    fun editFile(
        @P("Absolute or relative path to the file.")
        path: String,
        @P("0-based line index to start replacing at, inclusive.")
        startLine: Int,
        @P("0-based line index to stop replacing before, exclusive.")
        endLineExclusive: Int,
        @P("Replacement text to insert. Use an empty string to delete the selected lines.")
        newText: String,
        @P("Optional exact old text for the selected range. If provided, the edit is rejected unless the current selected text matches exactly.")
        expectedOldText: String? = null
    ): String {
        val file = try {
            FsToolSupport.normalizePath(path)
        } catch (_: Exception) {
            return "Path is invalid: $path"
        }

        pathPolicyLogic.evaluate(file.toString(), WRITE).toDenyReasonOrNull()?.let { return it }
        if (startLine < 0) return "startLine must be at least 0"
        if (endLineExclusive < startLine) return "endLineExclusive must be >= startLine"

        return try {
            if (file.notExists()) return "File not found: $file"
            if (!file.isRegularFile()) return "Not a file: $file"

            val lines = Files.readAllLines(file).toMutableList()
            if (startLine > lines.size) return "startLine must be at most ${lines.size}"
            if (endLineExclusive > lines.size) return "endLineExclusive must be at most ${lines.size}"

            val currentRangeText = lines.subList(startLine, endLineExclusive).joinToString("\n")
            if (expectedOldText != null) {
                val normalizedExpectedOldText = expectedOldText
                    .replace("\r\n", "\n")
                    .replace('\r', '\n')
                if (normalizedExpectedOldText != currentRangeText) {
                    return "Range text mismatch for [$startLine..$endLineExclusive["
                }
            }

            val replacementLines = if (newText.isEmpty()) {
                emptyList()
            } else {
                newText
                    .replace("\r\n", "\n")
                    .replace('\r', '\n')
                    .split("\n")
            }

            lines.subList(startLine, endLineExclusive).clear()
            lines.addAll(startLine, replacementLines)
            Files.writeString(file, lines.joinToString(System.lineSeparator()))
            """
            |File: $file
            |Replaced lines: [$startLine..$endLineExclusive[
            |Inserted lines: ${replacementLines.size}
            |New total lines: ${lines.size}
            |""".trimMargin()
        } catch (e: Exception) {
            ErrorLog.log(
                source = "edit_file.edit",
                exception = e,
                context = mapOf(
                    "path" to file.toString(),
                    "startLine" to startLine.toString(),
                    "endLineExclusive" to endLineExclusive.toString(),
                    "hasExpectedOldText" to (expectedOldText != null).toString()
                )
            )
            buildEditFailureResponse(file, startLine, endLineExclusive, e)
        }
    }

    private fun buildEditFailureResponse(
        file: Path,
        startLine: Int,
        endLineExclusive: Int,
        exception: Exception
    ): String {
        return buildString {
            appendLine("File edit failed.")
            appendLine("File: $file")
            appendLine("Requested range: [$startLine..$endLineExclusive[")
            appendLine("Reason: ${exception.message ?: exception::class.java.simpleName}")
            appendLine("Next steps:")
            appendLine("- Re-run read_file to inspect the current contents before retrying.")
            appendLine("- Confirm the file is still readable text before retrying the edit.")
            appendLine("- Retry edit_file only after checking whether the file changed or was partially rewritten.")
        }.trimEnd()
    }
}
