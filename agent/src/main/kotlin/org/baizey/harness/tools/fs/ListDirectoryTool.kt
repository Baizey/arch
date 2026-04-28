package org.baizey.harness.tools.fs

import dev.langchain4j.agent.tool.P
import dev.langchain4j.agent.tool.Tool
import org.baizey.harness.policy.PathPolicyLogic
import org.baizey.harness.policy.path.FsAccessType
import org.baizey.runtime.ErrorLog
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.notExists

class ListDirectoryTool(
    private val pathPolicyLogic: PathPolicyLogic
) {
    @Tool(
        name = "list_directory",
        value = ["""List one directory as a compact tree using normalized absolute paths. Use glob to filter what appears.
This is the main discovery tool; use search_files for content search or filename glob search and read_file for file contents.
If depth is omitted, behaves as 1. If shouldIncludeFiles is omitted, behaves as true. If shouldIncludeDirectories is omitted, behaves as true. If maxEntries is omitted, behaves as 200.
Example: list_directory(path="C:/repo", depth=2, glob="*.kt")"""]
    )
    fun listDirectory(
        @P("Absolute or relative path to the directory.")
        path: String,
        @P("Levels deep to recurse. If omitted, behaves as 1.")
        depth: Int? = null,
        @P("Optional glob filter, for example *.kt.")
        glob: String? = null,
        @P("Whether to include files in the results. If omitted, behaves as true.")
        shouldIncludeFiles: Boolean? = null,
        @P("Whether to include directories in the results. If omitted, behaves as true.")
        shouldIncludeDirectories: Boolean? = null,
        @P("Maximum entries to return. If omitted, behaves as 200.")
        maxEntries: Int? = null
    ): String {
        val actualDepth = depth ?: 1
        val actualGlob = glob?.trim().orEmpty()
        val actualIncludeFiles = shouldIncludeFiles ?: true
        val actualIncludeDirs = shouldIncludeDirectories ?: true
        val actualMaxEntries = maxEntries ?: FsToolSupport.DEFAULT_MAX_DIRECTORY_ENTRIES

        val directory = try {
            FsToolSupport.normalizePath(path)
        } catch (_: Exception) {
            return "Path is invalid: $path"
        }

        pathPolicyLogic.evaluate(directory.toString(), FsAccessType.READ).toDenyReasonOrNull()?.let { return it }
        if (actualDepth < 1) return "Depth must be at least 1"
        if (!actualIncludeFiles && !actualIncludeDirs) return "At least one of shouldIncludeFiles or shouldIncludeDirectories must be true"
        if (actualMaxEntries <= 0) return "maxEntries must be greater than 0"

        val collector = try {
            EntryCollector(
                root = directory,
                glob = actualGlob,
                includeFiles = actualIncludeFiles,
                includeDirs = actualIncludeDirs,
                maxEntries = actualMaxEntries
            )
        } catch (_: IllegalArgumentException) {
            return "Glob is invalid: $actualGlob"
        }

        return try {
            if (directory.notExists()) return "Path not found: $directory"
            if (!directory.isDirectory()) return "Not a directory: $directory"

            val output = mutableListOf<String>()
            collectEntries(directory, actualDepth, 0, output, collector)
            val tree = if (output.isEmpty()) "<empty>" else output.joinToString("\n")
            """Directory: $directory
Depth: $actualDepth
${if (actualGlob.isNotBlank()) "Glob: $actualGlob\n" else ""}Returned entries: ${output.size}
Truncated: ${collector.truncated}
Next step: use read_file for contents or search_files for text or filename search
Tree:
$tree"""
        } catch (e: Exception) {
            ErrorLog.log(
                source = "list_directory.list",
                exception = e,
                context = mapOf(
                    "path" to directory.toString(),
                    "depth" to actualDepth.toString(),
                    "glob" to if (actualGlob.isBlank()) "<none>" else actualGlob,
                    "includeFiles" to actualIncludeFiles.toString(),
                    "includeDirs" to actualIncludeDirs.toString(),
                    "maxEntries" to actualMaxEntries.toString()
                )
            )
            buildListFailureResponse(directory, e)
        }
    }

    private fun collectEntries(
        dir: Path,
        maxDepth: Int,
        currentDepth: Int,
        result: MutableList<String>,
        collector: EntryCollector
    ) {
        if (collector.truncated) return
        val entries = Files.newDirectoryStream(dir).use { stream ->
            stream.asSequence().sorted().toList()
        }
        entries.forEach { entry ->
            if (collector.truncated) return@forEach
            val isDir = entry.isDirectory()
            if (collector.shouldInclude(entry, isDir)) {
                collector.recordOrTruncate(entry, isDir, currentDepth, result)
            }
            if (isDir && currentDepth + 1 < maxDepth) {
                collectEntries(entry, maxDepth, currentDepth + 1, result, collector)
            }
        }
    }

    private class EntryCollector(
        private val root: Path,
        glob: String,
        private val includeFiles: Boolean,
        private val includeDirs: Boolean,
        private val maxEntries: Int
    ) {
        private val matcher = glob.takeIf { it.isNotBlank() }?.let {
            FileSystems.getDefault().getPathMatcher("glob:$it")
        }
        private var count = 0
        var truncated: Boolean = false
            private set

        fun shouldInclude(entry: Path, isDirectory: Boolean): Boolean {
            if (isDirectory && !includeDirs) return false
            if (!isDirectory && !includeFiles) return false
            if (matcher == null) return true
            val relative = root.relativize(entry)
            return matcher.matches(relative) || matcher.matches(entry.fileName)
        }

        fun recordOrTruncate(entry: Path, isDirectory: Boolean, currentDepth: Int, result: MutableList<String>) {
            if (count >= maxEntries) {
                truncated = true
                return
            }
            count++
            val marker = if (isDirectory) "[DIR]" else "[FILE]"
            val indent = "  ".repeat(currentDepth)
            result += "$indent$marker ${entry.toAbsolutePath().normalize()}"
        }
    }

    private fun buildListFailureResponse(directory: Path, exception: Exception): String {
        return buildString {
            appendLine("Directory listing failed.")
            appendLine("Directory: $directory")
            appendLine("Reason: ${exception.message ?: exception::class.java.simpleName}")
            appendLine("Next steps:")
            appendLine("- Retry list_directory if the failure may be transient.")
            appendLine("- Use a shallower depth or a narrower glob if the directory is large.")
            appendLine("- Use list_directory on the parent path to confirm the target still exists.")
        }.trimEnd()
    }
}
