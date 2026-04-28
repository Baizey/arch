package org.baizey.harness.tools.fs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class WriteFileToolTest : FsToolTestSupport() {
    private val tool = WriteFileTool(pathPolicyLogic)

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `writes content and creates parent directories`() {
        val file = tempDir.resolve("nested").resolve("sample.txt")

        val result = tool.writeFile(file.toString(), "alpha\nbeta")

        assertTrue(result.contains("File: ${file.toAbsolutePath().normalize()}"), result)
        assertTrue(result.contains("Created: true"), result)
        assertTrue(result.contains("Lines written: 2"), result)
        assertEquals("alpha\nbeta", Files.readString(file))
    }

    @Test
    fun `rejects overwrite when disabled`() {
        val file = Files.writeString(tempDir.resolve("sample.txt"), "alpha")

        val result = tool.writeFile(file.toString(), "beta", shouldOverwriteExistingFile = false)

        assertEquals("File already exists: ${file.toAbsolutePath().normalize()}", result)
        assertEquals("alpha", Files.readString(file))
    }

    @Test
    fun `replaces existing file when overwrite is allowed`() {
        val file = Files.writeString(tempDir.resolve("sample.txt"), "alpha")

        val result = tool.writeFile(file.toString(), "beta")

        assertTrue(result.contains("Created: false"), result)
        assertEquals("beta", Files.readString(file))
    }

    @Test
    fun `returns policy denial before writing`() {
        denyPermissions("Writing denied")

        val file = tempDir.resolve("sample.txt")
        val result = tool.writeFile(file.toString(), "alpha")

        assertTrue(result.contains("Writing denied"), result)
        assertFalse(Files.exists(file))
    }

    @Test
    fun `returns a usable failure response when parent directories cannot be created`() {
        val blockingFile = Files.writeString(tempDir.resolve("blocker.txt"), "occupied")
        val file = blockingFile.resolve("sample.txt")

        val result = tool.writeFile(file.toString(), "alpha")

        assertTrue(result.contains("File write failed."), result)
        assertTrue(result.contains("File: ${file.toAbsolutePath().normalize()}"), result)
        assertTrue(result.contains("Next steps:"), result)
    }
}
