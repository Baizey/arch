package org.baizey.harness.tools.fs

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class DeleteFileToolTest : FsToolTestSupport() {
    private val tool = DeletePathTool(pathPolicyLogic)

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `deletes a file`() {
        val file = Files.writeString(tempDir.resolve("sample.txt"), "alpha")

        val result = tool.deletePath(file.toString())

        assertTrue(result.contains("Deleted:"), result)
        assertFalse(Files.exists(file))
    }

    @Test
    fun `refuses to delete non-empty directories without recursive flag`() {
        val dir = Files.createDirectories(tempDir.resolve("nested"))
        Files.writeString(dir.resolve("child.txt"), "alpha")

        val result = tool.deletePath(dir.toString())

        assertTrue(result.contains("Directory is not empty"), result)
        assertTrue(Files.exists(dir))
    }

    @Test
    fun `recursively deletes non-empty directories when requested`() {
        val dir = Files.createDirectories(tempDir.resolve("nested"))
        Files.writeString(dir.resolve("child.txt"), "alpha")

        val result = tool.deletePath(dir.toString(), shouldDeleteRecursively = true)

        assertTrue(result.contains("Deleted directory and all contents"), result)
        assertFalse(Files.exists(dir))
    }

    @Test
    fun `returns policy denial before deleting`() {
        denyPermissions("Delete denied")

        val file = Files.writeString(tempDir.resolve("sample.txt"), "alpha")
        val result = tool.deletePath(file.toString())

        assertTrue(result.contains("Delete denied"), result)
        assertTrue(Files.exists(file))
    }
}
