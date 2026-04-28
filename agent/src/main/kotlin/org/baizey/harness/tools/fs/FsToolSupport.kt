package org.baizey.harness.tools.fs

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object FsToolSupport {
    const val DEFAULT_MAX_DIRECTORY_ENTRIES = 200
    const val DEFAULT_MAX_SEARCH_MATCHES = 100
    const val DEFAULT_MAX_READ_LINES = 200

    fun normalizePath(path: String): Path = Paths.get(path).toAbsolutePath().normalize()

    fun versionToken(path: Path): String {
        if (!Files.exists(path)) return "missing"
        return "${Files.size(path)}:${Files.getLastModifiedTime(path).toMillis()}"
    }

    fun countLines(content: String): Int {
        if (content.isEmpty()) return 0
        val normalized = content.replace("\r\n", "\n").replace('\r', '\n')
        return normalized.count { it == '\n' } + 1
    }
}
