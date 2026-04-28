package org.baizey.harness.tools.fs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ReadFileToolTest : FsToolTestSupport() {
    private val tool = ReadFileTool(pathPolicyLogic)

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `reads the full file by default`() {
        val file = Files.writeString(tempDir.resolve("sample.txt"), "alpha\nbeta\ngamma")

        val result = tool.readFile(file.toString())

        assertTrue(result.contains("File: $file"), result)
        assertTrue(result.contains("Total lines: 3"), result)
        assertTrue(result.contains("Read range: [0..3["), result)
        assertTrue(result.contains("Returned lines: 3"), result)
        assertTrue(result.contains("Truncated: false"), result)
        assertTrue(result.contains("   0 | alpha"), result)
        assertTrue(result.contains("   1 | beta"), result)
        assertTrue(result.contains("   2 | gamma"), result)
    }

    @Test
    fun `reads the requested half open line range`() {
        val file = Files.writeString(tempDir.resolve("sample.txt"), "alpha\nbeta\ngamma")

        val result = tool.readFile(file.toString(), startLine = 1, endLineExclusive = 3)

        assertTrue(result.contains("File: $file"), result)
        assertTrue(result.contains("Read range: [1..3["))
        assertTrue(result.contains("1 | beta"))
        assertTrue(result.contains("2 | gamma"))
        assertTrue(result.contains("Returned lines: 2"))
        assertTrue(result.contains("Truncated: false"))
        assertFalse(result.contains("0 | alpha"), result)
    }

    @Test
    fun `truncates reads that exceed max lines`() {
        val file = Files.writeString(tempDir.resolve("sample.txt"), "one\ntwo\nthree")

        val result = tool.readFile(file.toString(), startLine = 0, endLineExclusive = 3, maxLines = 2)

        assertTrue(result.contains("Read range: [0..2["), result)
        assertTrue(result.contains("Returned lines: 2"), result)
        assertTrue(result.contains("Truncated: true"), result)
        assertFalse(result.contains("2 | three"), result)
    }

    @Test
    fun `returns no lines marker when the requested range is empty`() {
        val file = Files.writeString(tempDir.resolve("sample.txt"), "one\ntwo")

        val result = tool.readFile(file.toString(), startLine = 2, endLineExclusive = 2)

        assertTrue(result.contains("Read range: [2..2["), result)
        assertTrue(result.contains("Returned lines: 0"), result)
        assertTrue(result.contains("(no lines in range)"), result)
    }

    @Test
    fun `rejects invalid range arguments`() {
        val file = Files.writeString(tempDir.resolve("sample.txt"), "one\ntwo")

        assertEquals("startLine must be at least 0", tool.readFile(file.toString(), startLine = -1))
        assertEquals("maxLines must be greater than 0", tool.readFile(file.toString(), maxLines = 0))
        assertEquals("startLine must be at most 2", tool.readFile(file.toString(), startLine = 3))
        assertEquals(
            "endLineExclusive must be >= startLine",
            tool.readFile(file.toString(), startLine = 1, endLineExclusive = 0)
        )
    }

    @Test
    fun `returns policy denial before reading`() {
        denyPermissions("Reading denied")
        val file = Files.writeString(tempDir.resolve("sample.txt"), "one\ntwo")

        val result = tool.readFile(file.toString())

        assertTrue(result.contains("You are disallowed from accessing"), result)
        assertTrue(result.contains("Access types blocked are: READ"), result)
        assertTrue(result.contains("Users reason for policy: Reading denied"), result)
    }

    @Test
    fun `returns a usable failure response when the file cannot be read as text`() {
        val file = tempDir.resolve("sample.txt")
        Files.write(file, byteArrayOf(0xC3.toByte(), 0x28))

        val result = tool.readFile(file.toString())

        assertTrue(result.contains("File read failed."), result)
        assertTrue(result.contains("File: $file"), result)
        assertTrue(result.contains("Next steps:"), result)
    }
}
