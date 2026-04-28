package org.baizey.harness.tools.fs

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class FsDeniedPolicyStateTest : FsToolTestSupport() {
    private val writeTool = WriteFileTool(pathPolicyLogic)
    private val editTool = EditFileTool(pathPolicyLogic)
    private val deleteTool = DeletePathTool(pathPolicyLogic)
    private val moveOrCopyTool = MoveOrCopyPathTool(pathPolicyLogic)

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `write file is denied by policy`() {
        denyPermissions("Writing denied")
        val file = tempDir.resolve("sample.txt")

        val result = writeTool.writeFile(file.toString(), "alpha")

        assertTrue(result.contains("Writing denied"), result)
        assertFalse(Files.exists(file))
    }

    @Test
    fun `edit file is denied by policy`() {
        denyPermissions("Editing denied")
        val file = Files.writeString(tempDir.resolve("sample.txt"), "one\ntwo")

        val result = editTool.editFile(file.toString(), startLine = 0, endLineExclusive = 1, newText = "ONE")

        assertTrue(result.contains("Editing denied"), result)
        assertTrue(Files.readString(file).contains("one"))
    }

    @Test
    fun `delete path is denied by policy`() {
        denyPermissions("Delete denied")
        val file = Files.writeString(tempDir.resolve("sample.txt"), "alpha")

        val result = deleteTool.deletePath(file.toString())

        assertTrue(result.contains("Delete denied"), result)
        assertTrue(Files.exists(file))
    }

    @Test
    fun `move path is denied by policy`() {
        denyPermissions("Move denied")
        val source = Files.writeString(tempDir.resolve("source.txt"), "alpha")
        val destination = tempDir.resolve("moved.txt")

        val result = moveOrCopyTool.moveOrCopyPath(source.toString(), destination.toString())

        assertTrue(result.contains("Move denied"), result)
        assertTrue(Files.exists(source))
        assertFalse(Files.exists(destination))
    }

    @Test
    fun `copy path is denied by policy`() {
        denyPermissions("Copy denied")
        val source = Files.writeString(tempDir.resolve("source.txt"), "alpha")
        val destination = tempDir.resolve("copied.txt")

        val result = moveOrCopyTool.moveOrCopyPath(source.toString(), destination.toString(), shouldCopy = true)

        assertTrue(result.contains("Copy denied"), result)
        assertTrue(Files.exists(source))
        assertFalse(Files.exists(destination))
    }
}
