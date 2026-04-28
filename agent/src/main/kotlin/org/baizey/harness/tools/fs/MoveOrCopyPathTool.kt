package org.baizey.harness.tools.fs

import dev.langchain4j.agent.tool.P
import dev.langchain4j.agent.tool.Tool
import org.baizey.harness.policy.PathPolicyLogic
import org.baizey.harness.policy.path.FsAccessType.DELETE
import org.baizey.harness.policy.path.FsAccessType.READ
import org.baizey.harness.policy.path.FsAccessType.WRITE
import org.baizey.runtime.ErrorLog
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.notExists

class MoveOrCopyPathTool(
    private val pathPolicyLogic: PathPolicyLogic
) {
    @Tool(
        name = "move_or_copy_path",
        value = ["""Move or copy a file or directory. Defaults to move; set shouldCopy=true to copy instead.
Directories are handled recursively. Creates destination parent dirs as needed.
If shouldCopy is omitted, behaves as false. If shouldOverwriteExistingFiles is omitted, behaves as true.
When shouldOverwriteExistingFiles=false, conflicting files are skipped silently (check skipped count in output).
Example: move_or_copy_path(source="C:/repo/src", destination="C:/repo-copy/src", shouldCopy=true, shouldOverwriteExistingFiles=false)"""]
    )
    fun moveOrCopyPath(
        @P("Absolute or relative path to the source file or directory.")
        source: String,
        @P("Absolute or relative path to the destination file or directory.")
        destination: String,
        @P("Whether to copy instead of move. If omitted, behaves as false.")
        shouldCopy: Boolean? = null,
        @P("Whether to overwrite existing files at the destination. If omitted, behaves as true.")
        shouldOverwriteExistingFiles: Boolean? = null
    ): String {
        val actualIsCopying = shouldCopy ?: false
        val actualOverwriteExistingFiles = shouldOverwriteExistingFiles ?: true
        val sourcePath = try {
            FsToolSupport.normalizePath(source)
        } catch (_: Exception) {
            return "Path is invalid: $source"
        }
        val destinationPath = try {
            FsToolSupport.normalizePath(destination)
        } catch (_: Exception) {
            return "Path is invalid: $destination"
        }

        pathPolicyLogic.evaluate(sourcePath.toString(), if (actualIsCopying) READ else DELETE).toDenyReasonOrNull()
            ?.let { return it }
        pathPolicyLogic.evaluate(destinationPath.toString(), WRITE).toDenyReasonOrNull()?.let { return it }

        val type = if (actualIsCopying) "Copied" else "Moved"
        val src = sourcePath
        val dst = destinationPath
        return try {
            if (src.notExists()) return "Source not found: $src"
            val skipped = mutableListOf<String>()
            if (src.isDirectory()) {
                Files.walk(src).use { sourcePaths ->
                    sourcePaths.forEach { srcPath ->
                        val dstPath = dst.resolve(src.relativize(srcPath))
                        if (srcPath.isDirectory()) {
                            Files.createDirectories(dstPath)
                        } else {
                            Files.createDirectories(dstPath.parent)
                            if (!actualOverwriteExistingFiles && dstPath.exists()) {
                                skipped.add("Destination already exists and overwrite flag is false: $dstPath")
                            } else if (actualIsCopying) {
                                Files.copy(srcPath, dstPath, StandardCopyOption.REPLACE_EXISTING)
                            } else {
                                Files.move(srcPath, dstPath, StandardCopyOption.REPLACE_EXISTING)
                            }
                        }
                    }
                }
                if (!actualIsCopying) {
                    Files.walk(src).use { remainingPaths ->
                        remainingPaths.sorted(Comparator.reverseOrder()).forEach { Files.delete(it) }
                    }
                }
                """$type directory $src to $dst
Skipped: ${skipped.size}
---
Skip the following:
${if (skipped.isEmpty()) "Nothing" else skipped.joinToString("\n")}
"""
            } else {
                dst.parent?.let { Files.createDirectories(it) }
                if (!actualOverwriteExistingFiles && dst.exists()) {
                    return "Destination already exists and overwrite flag is false: $dst"
                }
                if (actualIsCopying) Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING)
                else Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING)
                "$type $src to $dst"
            }
        } catch (e: Exception) {
            ErrorLog.log(
                source = "move_or_copy_path.transfer",
                exception = e,
                context = mapOf(
                    "source" to src.toString(),
                    "destination" to dst.toString(),
                    "shouldCopy" to actualIsCopying.toString(),
                    "shouldOverwriteExistingFiles" to actualOverwriteExistingFiles.toString()
                )
            )
            buildMoveOrCopyFailureResponse(src, dst, actualIsCopying, e)
        }
    }

    private fun buildMoveOrCopyFailureResponse(
        sourcePath: Path,
        destinationPath: Path,
        isCopying: Boolean,
        exception: Exception
    ): String {
        return buildString {
            appendLine(if (isCopying) "Path copy failed." else "Path move failed.")
            appendLine("Source: $sourcePath")
            appendLine("Destination: $destinationPath")
            appendLine("Reason: ${exception.message ?: exception::class.java.simpleName}")
            appendLine("Next steps:")
            appendLine("- Use list_directory on the source and destination parents to inspect any partial changes.")
            appendLine("- Use read_file on important files before retrying if content preservation matters.")
            appendLine("- Retry only after checking whether some files were already moved or copied.")
        }.trimEnd()
    }
}
