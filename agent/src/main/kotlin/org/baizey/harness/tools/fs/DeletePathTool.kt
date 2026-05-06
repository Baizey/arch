package org.baizey.harness.tools.fs

import dev.langchain4j.agent.tool.P
import dev.langchain4j.agent.tool.Tool
import org.baizey.harness.policy.PathPolicyLogic
import org.baizey.harness.policy.path.FsAccessType
import org.baizey.runtime.ErrorLog
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.notExists

class DeletePathTool(
    private val pathPolicyLogic: PathPolicyLogic
) {
    @Tool(
        name = "delete_path",
        value = ["""Delete a file or directory. Non-empty directories are refused unless shouldDeleteRecursively=true.
If shouldDeleteRecursively is omitted, behaves as false.
shouldDeleteRecursively=true deletes all contents permanently — use with caution.
Example: delete_path(path="C:/repo/build", shouldDeleteRecursively=true)"""]
    )
    fun deletePath(
        @P("Absolute or relative path to the file or directory to delete.")
        path: String,
        @P("Whether to delete directory contents recursively. If omitted, behaves as false.")
        shouldDeleteRecursively: Boolean? = null
    ): String {
        val actualRecursive = shouldDeleteRecursively ?: false
        val target = try {
            FsToolSupport.normalizePath(path)
        } catch (_: Exception) {
            return "Path is invalid: $path"
        }

        pathPolicyLogic.evaluate(target.toString(), FsAccessType.DELETE).toDenyReasonOrNull()?.let { return it }
        return try {
            if (target.notExists()) return "Path not found: $target"

            if (target.isDirectory()) {
                val entries = Files.list(target).use { it.count() }
                if (entries > 0 && !actualRecursive) {
                    return "Directory is not empty: $target (use shouldDeleteRecursively=true to delete contents)"
                }
                if (actualRecursive) {
                    Files.walk(target).use { paths ->
                        paths.sorted(Comparator.reverseOrder()).forEach { Files.delete(it) }
                    }
                    return "Deleted directory and all contents: $target"
                }
            }

            Files.delete(target)
            "Deleted: $target"
        } catch (e: Exception) {
            ErrorLog.log(
                source = "delete_path.delete",
                exception = e,
                context = mapOf(
                    "path" to target.toString(),
                    "recursive" to actualRecursive.toString()
                )
            )
            buildDeleteFailureResponse(target, actualRecursive, e)
        }
    }

    private fun buildDeleteFailureResponse(target: Path, recursive: Boolean, exception: Exception): String {
        return buildString {
            appendLine("Path deletion failed.")
            appendLine("Path: $target")
            appendLine("Recursive: $recursive")
            appendLine("Reason: ${exception.message ?: exception::class.java.simpleName}")
            appendLine("Next steps:")
            appendLine("- Use list_directory on the parent path to inspect what still exists.")
            appendLine("- If recursive=true, assume the directory may be only partially deleted and verify state before retrying.")
            appendLine("- Retry delete_path only after confirming whether the target or some children remain.")
        }.trimEnd()
    }
}
