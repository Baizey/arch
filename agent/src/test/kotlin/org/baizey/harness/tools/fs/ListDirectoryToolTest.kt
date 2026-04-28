package org.baizey.harness.tools.fs

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ListDirectoryToolTest : FsToolTestSupport() {
    private val tool = ListDirectoryTool(pathPolicyLogic)

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `lists only immediate children when depth is one`() {
        val nestedDir = Files.createDirectories(tempDir.resolve("nested"))
        Files.writeString(tempDir.resolve("root.txt"), "root")
        Files.writeString(nestedDir.resolve("child.txt"), "child")

        val result = tool.listDirectory(tempDir.toString(), depth = 1)

        assertTrue(result.contains("Directory: ${tempDir.toAbsolutePath()}"), result)
        assertTrue(result.contains("Depth: 1"), result)
        assertTrue(result.contains("Returned entries: 2"), result)
        assertTrue(result.contains("Truncated: false"), result)
        assertTrue(result.contains("Tree:"), result)
        assertTrue(result.contains("[FILE] ${tempDir.toAbsolutePath().resolve("root.txt").normalize()}"), result)
        assertTrue(result.contains("[DIR] ${tempDir.toAbsolutePath().resolve("nested").normalize()}"), result)
        assertFalse(result.contains("child.txt"))
    }

    @Test
    fun `recurses into nested directories when depth is increased`() {
        val nestedDir = Files.createDirectories(tempDir.resolve("nested"))
        Files.writeString(nestedDir.resolve("child.txt"), "child")

        val result = tool.listDirectory(tempDir.toString(), depth = 2)

        assertTrue(
            result.contains("  [FILE] ${nestedDir.resolve("child.txt").toAbsolutePath().normalize()}"),
            result
        )
    }

    @Test
    fun `lists entries in sorted path order`() {
        Files.writeString(tempDir.resolve("b.txt"), "b")
        Files.writeString(tempDir.resolve("a.txt"), "a")

        val result = tool.listDirectory(tempDir.toString(), depth = 1)
        val aIndex = result.indexOf("[FILE] ${tempDir.toAbsolutePath().resolve("a.txt").normalize()}")
        val bIndex = result.indexOf("[FILE] ${tempDir.toAbsolutePath().resolve("b.txt").normalize()}")

        assertTrue(aIndex >= 0, result)
        assertTrue(bIndex >= 0, result)
        assertTrue(aIndex < bIndex, result)
    }

    @Test
    fun `returns empty tree for empty directory`() {
        val result = tool.listDirectory(tempDir.toString(), depth = 1)

        val expected = """
            Directory: ${tempDir.toAbsolutePath()}
            Depth: 1
            Returned entries: 0
            Truncated: false
            Next step: use read_file for contents or search_files for text or filename search
            Tree:
            <empty>
        """.trimIndent()
        assertEquals(expected, result)
    }

    @Test
    fun `rejects invalid depth`() {
        val result = tool.listDirectory(tempDir.toString(), depth = 0)

        assertTrue(result.contains("Depth must be at least 1"), result)
    }

    @Test
    fun `reports file path instead of directory`() {
        val file = Files.writeString(tempDir.resolve("file.txt"), "text")

        val result = tool.listDirectory(file.toString(), depth = 1)

        assertTrue(result.contains("Not a directory: $file"), result)
    }

    @Test
    fun `filters entries by glob`() {
        val sourceDir = Files.createDirectories(tempDir.resolve("src"))
        val ktFile = Files.writeString(sourceDir.resolve("Main.kt"), "fun main() = Unit")
        val txtFile = Files.writeString(tempDir.resolve("notes.txt"), "notes")

        val result = tool.listDirectory(tempDir.toString(), depth = 2, glob = "*.kt")

        assertTrue(result.contains("Glob: *.kt"), result)
        assertTrue(result.contains(ktFile.toAbsolutePath().toString()), result)
        assertFalse(result.contains(txtFile.toAbsolutePath().toString()), result)
    }

    @Test
    fun `rejects invalid glob patterns`() {
        val result = tool.listDirectory(tempDir.toString(), depth = 1, glob = "[")

        assertEquals("Glob is invalid: [", result)
    }

    @Test
    fun `can exclude directories from results`() {
        val nestedDir = Files.createDirectories(tempDir.resolve("nested"))
        Files.writeString(nestedDir.resolve("child.txt"), "child")

        val result = tool.listDirectory(tempDir.toString(), depth = 2, shouldIncludeDirectories = false)

        assertFalse(result.contains("[DIR] ${nestedDir.toAbsolutePath().normalize()}"), result)
        assertTrue(result.contains("[FILE] ${nestedDir.resolve("child.txt").toAbsolutePath().normalize()}"), result)
    }

    @Test
    fun `returns policy denial before listing`() {
        denyPermissions("Listing denied")

        val result = tool.listDirectory(tempDir.toString(), depth = 1)

        assertTrue(result.contains("You are disallowed from accessing"), result)
        assertTrue(result.contains("Access types blocked are: READ"), result)
        assertTrue(result.contains("Users reason for policy: Listing denied"), result)
    }
}
