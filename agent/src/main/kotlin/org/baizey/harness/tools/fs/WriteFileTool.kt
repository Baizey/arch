package org.baizey.harness.tools.fs

import dev.langchain4j.agent.tool.P
import dev.langchain4j.agent.tool.Tool
import org.baizey.harness.policy.UserPathPolicyLogic
import org.baizey.harness.policy.path.FsAccessType
import org.baizey.runtime.ErrorLog
import java.nio.file.Files
import java.nio.file.Path

class WriteFileTool(
    private val userPathPolicyLogic: UserPathPolicyLogic
) {
    @Tool(
        name = "write_file",
        value = ["""Write the full contents of one file. Creates missing parent directories if needed.
Use this for new files or full replacement only. Use edit_file for partial edits.
If shouldOverwriteExistingFile is omitted, behaves as true.
Example: write_file(path="C:/repo/notes.txt", content="hello", shouldOverwriteExistingFile=false)"""]
    )
    fun writeFile(
        @P("Absolute or relative path to the file.")
        path: String,
        @P("Full file content to write.")
        content: String,
        @P("Whether to replace an existing file. If omitted, behaves as true.")
        shouldOverwriteExistingFile: Boolean? = null
    ): String {
        val actualOverwrite = shouldOverwriteExistingFile ?: true
        val file = try {
            FsToolSupport.normalizePath(path)
        } catch (_: Exception) {
            return "Path is invalid: $path"
        }

        userPathPolicyLogic.evaluate(file.toString(), FsAccessType.WRITE).toDenyReasonOrNull()?.let { return it }
        return try {
            val existedBefore = Files.exists(file)
            if (existedBefore && !actualOverwrite) return "File already exists: $file"

            file.parent?.let { Files.createDirectories(it) }
            Files.writeString(file, content)
            val normalizedLineCount = FsToolSupport.countLines(content)
            """File: $file
Created: ${!existedBefore}
Lines written: $normalizedLineCount"""
        } catch (e: Exception) {
            ErrorLog.log(
                source = "write_file.write",
                exception = e,
                context = mapOf(
                    "path" to file.toString(),
                    "shouldOverwriteExistingFile" to actualOverwrite.toString()
                )
            )
            buildWriteFailureResponse(file, e)
        }
    }

    private fun buildWriteFailureResponse(file: Path, exception: Exception): String {
        return buildString {
            appendLine("File write failed.")
            appendLine("File: $file")
            appendLine("Reason: ${exception.message ?: exception::class.java.simpleName}")
            appendLine("Next steps:")
            appendLine("- Use list_directory on the parent path to confirm the destination path is valid.")
            appendLine("- Use read_file on the target before retrying, because the file may have changed or been partially written.")
            appendLine("- Retry write_file only after checking whether the current file contents should be replaced.")
        }.trimEnd()
    }
}
