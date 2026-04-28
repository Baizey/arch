package org.baizey.harness.tools.fs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class EditFileToolTest : FsToolTestSupport() {
    private val tool = EditFileTool(pathPolicyLogic)

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `replaces an exact line range`() {
        val file = Files.writeString(tempDir.resolve("sample.txt"), "one\ntwo\nthree\nfour")

        val result = tool.editFile(
            file.toString(),
            startLine = 1,
            endLineExclusive = 3,
            newText = "TWO\nTHREE",
            expectedOldText = "two\nthree"
        )

        assertTrue(result.contains("Replaced lines: [1..3["))
        assertTrue(result.contains("Inserted lines: 2"))
        assertEquals(listOf("one", "TWO", "THREE", "four"), Files.readAllLines(file))
    }

    @Test
    fun `inserts new lines when the range is empty`() {
        val file = Files.writeString(tempDir.resolve("sample.txt"), "one\nthree")

        val result = tool.editFile(file.toString(), startLine = 1, endLineExclusive = 1, newText = "two")

        assertTrue(result.contains("Replaced lines: [1..1["))
        assertEquals(listOf("one", "two", "three"), Files.readAllLines(file))
    }

    @Test
    fun `rejects edits when the expected version is stale`() {
        val file = Files.writeString(tempDir.resolve("sample.txt"), "one\ntwo")

        val result = tool.editFile(
            file.toString(),
            startLine = 0,
            endLineExclusive = 1,
            newText = "ONE",
            expectedOldText = "stale"
        )

        assertTrue(result.contains("Range text mismatch"), result)
        assertEquals(listOf("one", "two"), Files.readAllLines(file))
    }

    @Test
    fun `returns policy denial before editing`() {
        denyPermissions("Editing denied")
        val file = Files.writeString(tempDir.resolve("sample.txt"), "one\ntwo")

        val result = tool.editFile(file.toString(), startLine = 0, endLineExclusive = 1, newText = "ONE")

        assertTrue(result.contains("Editing denied"))
        assertEquals(listOf("one", "two"), Files.readAllLines(file))
    }

    @Test
    fun `returns a usable failure response when the file cannot be read as text`() {
        val file = tempDir.resolve("sample.txt")
        Files.write(file, byteArrayOf(0xC3.toByte(), 0x28))

        val result = tool.editFile(file.toString(), startLine = 0, endLineExclusive = 0, newText = "ONE")

        assertTrue(result.contains("File edit failed."), result)
        assertTrue(result.contains("File: $file"), result)
        assertTrue(result.contains("Next steps:"), result)
    }

    @Test
    fun `normalizes expected old text and replacement text line endings`() {
        val file = Files.writeString(tempDir.resolve("sample.txt"), "one\ntwo\nthree\nfour")

        val result = tool.editFile(
            file.toString(),
            startLine = 1,
            endLineExclusive = 3,
            newText = "TWO\r\nUPDATED",
            expectedOldText = "two\r\nthree"
        )

        assertTrue(result.contains("Inserted lines: 2"), result)
        assertEquals(listOf("one", "TWO", "UPDATED", "four"), Files.readAllLines(file))
    }
}
