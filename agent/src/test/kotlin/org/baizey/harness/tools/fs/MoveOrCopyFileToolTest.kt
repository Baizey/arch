package org.baizey.harness.tools.fs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class MoveOrCopyFileToolTest : FsToolTestSupport() {
    private val tool = MoveOrCopyPathTool(pathPolicyLogic)

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `copies a file without removing the source`() {
        val source = Files.writeString(tempDir.resolve("source.txt"), "alpha")
        val destination = tempDir.resolve("nested").resolve("copy.txt")

        val result = tool.moveOrCopyPath(source.toString(), destination.toString(), shouldCopy = true)

        assertTrue(result.contains("Copied"), result)
        assertEquals("alpha", Files.readString(source))
        assertEquals("alpha", Files.readString(destination))
    }

    @Test
    fun `moves a file and removes the source`() {
        val source = Files.writeString(tempDir.resolve("source.txt"), "alpha")
        val destination = tempDir.resolve("moved.txt")

        val result = tool.moveOrCopyPath(source.toString(), destination.toString())

        assertTrue(result.contains("Moved"), result)
        assertFalse(Files.exists(source))
        assertEquals("alpha", Files.readString(destination))
    }

    @Test
    fun `does not overwrite existing destination when disabled`() {
        val source = Files.writeString(tempDir.resolve("source.txt"), "alpha")
        val destination = Files.writeString(tempDir.resolve("dest.txt"), "beta")

        val result = tool.moveOrCopyPath(
            source.toString(),
            destination.toString(),
            shouldCopy = true,
            shouldOverwriteExistingFiles = false
        )

        assertTrue(result.contains("overwrite flag is false"), result)
        assertEquals("alpha", Files.readString(source))
        assertEquals("beta", Files.readString(destination))
    }

    @Test
    fun `returns policy denial before moving`() {
        denyPermissions("Move denied")

        val source = Files.writeString(tempDir.resolve("source.txt"), "alpha")
        val destination = tempDir.resolve("moved.txt")
        val result = tool.moveOrCopyPath(source.toString(), destination.toString())

        assertTrue(result.contains("Move denied"), result)
        assertTrue(Files.exists(source))
        assertFalse(Files.exists(destination))
    }

    @Test
    fun `returns a usable failure response when destination parent cannot be created`() {
        val source = Files.writeString(tempDir.resolve("source.txt"), "alpha")
        val blockingFile = Files.writeString(tempDir.resolve("blocker.txt"), "occupied")
        val destination = blockingFile.resolve("copy.txt")

        val result = tool.moveOrCopyPath(source.toString(), destination.toString(), shouldCopy = true)

        assertTrue(result.contains("Path copy failed."), result)
        assertTrue(result.contains("Source: $source"), result)
        assertTrue(result.contains("Destination: ${destination.toAbsolutePath().normalize()}"), result)
        assertTrue(result.contains("Next steps:"), result)
    }
}
