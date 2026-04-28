package org.baizey.harness.tools.fs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class SearchFilesToolTest : FsToolTestSupport() {
    private val tool = SearchFilesTool(pathPolicyLogic)

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `searches a single file and returns matching line ranges`() {
        val file = Files.writeString(tempDir.resolve("sample.txt"), "alpha\nbeta\ngamma\nbeta")

        val result = tool.searchFiles(file.toString(), query = "beta", maxMatches = 2)

        assertTrue(result.contains("Path: $file"), result)
        assertTrue(result.contains("Query: \"beta\""), result)
        assertTrue(result.contains("Mode: content"), result)
        assertTrue(result.contains("Matches: 2"), result)
        assertTrue(result.contains("Files with matches: 1"), result)
        assertTrue(result.contains("Range: [1..2["), result)
        assertTrue(result.contains("   1 | beta"), result)
        assertTrue(result.contains("   3 | beta"), result)
    }

    @Test
    fun `searches directories recursively and filters by glob`() {
        val ktFile = Files.createDirectories(tempDir.resolve("src"))
            .resolve("Main.kt")
            .also { Files.writeString(it, "alpha\nbeta") }
        val txtFile = Files.writeString(tempDir.resolve("notes.txt"), "alpha")

        val result = tool.searchFiles(tempDir.toString(), query = "alpha", glob = "*.kt")

        assertTrue(result.contains("Path: ${tempDir.toAbsolutePath().normalize()}"), result)
        assertTrue(result.contains("Glob: *.kt"), result)
        assertTrue(result.contains("Files with matches: 1"), result)
        assertTrue(result.contains(ktFile.toAbsolutePath().toString()), result)
        assertFalse(result.contains(txtFile.toAbsolutePath().toString()), result)
    }

    @Test
    fun `treats null query as filename glob search`() {
        val ktFile = Files.createDirectories(tempDir.resolve("src"))
            .resolve("Main.kt")
            .also { Files.writeString(it, "alpha\nbeta") }
        val txtFile = Files.writeString(tempDir.resolve("notes.txt"), "alpha")

        val result = tool.searchFiles(tempDir.toString(), query = null, glob = "*.kt")

        assertTrue(result.contains("Query: <none>"), result)
        assertTrue(result.contains("Glob: *.kt"), result)
        assertTrue(result.contains("Mode: file_names"), result)
        assertTrue(result.contains("Matches: 1"), result)
        assertTrue(result.contains("Files with matches: 1"), result)
        assertTrue(result.contains("File: ${ktFile.toAbsolutePath().normalize()}"), result)
        assertFalse(result.contains(txtFile.toAbsolutePath().toString()), result)
        assertFalse(result.contains("Range: ["), result)
    }

    @Test
    fun `treats blank query as filename glob search`() {
        val txtFile = Files.writeString(tempDir.resolve("notes.txt"), "alpha")

        val result = tool.searchFiles(tempDir.toString(), query = "   ", glob = "*.txt")

        assertTrue(result.contains("Query: <none>"), result)
        assertTrue(result.contains("Mode: file_names"), result)
        assertTrue(result.contains("Matches: 1"), result)
        assertTrue(result.contains("File: ${txtFile.toAbsolutePath().normalize()}"), result)
        assertFalse(result.contains("Regex: "), result)
    }

    @Test
    fun `supports case insensitive regex search`() {
        val file = Files.writeString(tempDir.resolve("sample.txt"), "Alpha\nbeta")

        val result = tool.searchFiles(
            path = file.toString(),
            query = "alpha",
            shouldUseRegex = true,
            shouldIgnoreCase = true
        )

        assertTrue(result.contains("Regex: true"), result)
        assertTrue(result.contains("Ignore case: true"), result)
        assertTrue(result.contains("Matches: 1"), result)
        assertTrue(result.contains("Alpha"), result)
    }

    @Test
    fun `rejects invalid regex queries`() {
        val result = tool.searchFiles(
            path = tempDir.toString(),
            query = "[",
            shouldUseRegex = true
        )

        assertEquals("Regex query is invalid: [", result)
    }

    @Test
    fun `reports zero matches clearly`() {
        Files.writeString(tempDir.resolve("sample.txt"), "beta")

        val result = tool.searchFiles(tempDir.toString(), query = "alpha", glob = "*.txt")

        assertTrue(result.contains("Path: ${tempDir.toAbsolutePath().normalize()}"), result)
        assertTrue(result.contains("Query: \"alpha\""), result)
        assertTrue(result.contains("Glob: *.txt"), result)
        assertTrue(result.contains("Mode: content"), result)
        assertTrue(result.contains("Matches: 0"), result)
        assertTrue(result.contains("Files with matches: 0"), result)
        assertTrue(result.contains("Truncated: false"), result)
        assertFalse(result.contains("File: "), result)
    }

    @Test
    fun `returns policy denial before searching`() {
        denyPermissions("Search denied")

        val file = Files.writeString(tempDir.resolve("sample.txt"), "alpha")
        val result = tool.searchFiles(file.toString(), query = "alpha")

        assertTrue(result.contains("Search denied"), result)
        assertFalse(result.contains("File: "), result)
    }
}
