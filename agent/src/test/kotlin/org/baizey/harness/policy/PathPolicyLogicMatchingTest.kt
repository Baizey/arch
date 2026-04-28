package org.baizey.harness.policy

import org.baizey.harness.AskUserAnswer
import org.baizey.harness.HarnessInteractionPort
import org.baizey.harness.PermissionDecision
import org.baizey.harness.PermissionRequest
import org.baizey.harness.policy.path.FsAccessType
import org.baizey.harness.policy.shared.PolicyLifetime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class PathPolicyLogicMatchingTest {
    private var permissionDecisionProvider: (PermissionRequest) -> PermissionDecision = { request ->
        PermissionDecision(
            isAllowed = true,
            lifetime = PolicyLifetime.ONCE,
            scope = request.path
        )
    }

    private val interactionPort = object : HarnessInteractionPort {
        override fun askUserQuestion(question: String, options: List<String>): AskUserAnswer {
            return AskUserAnswer(
                isAccepted = true,
                selection = options.firstOrNull().orEmpty(),
                selectionIndex = 0
            )
        }

        override fun requestPermission(request: PermissionRequest): PermissionDecision {
            return permissionDecisionProvider(request)
        }
    }

    private val tool = PathPolicyLogic(interactionPort)

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `read access on the exact file allows read_file`() {
        val file = Files.writeString(tempDir.resolve("exact.txt"), "alpha\nbeta")
        seedPolicy(file, FsAccessType.READ, file)

        val result = tool.evaluate(file.toString(), FsAccessType.READ)

        assertTrue(result.isAllowed, result.toString())
        assertEquals(file.toAbsolutePath().normalize().toString(), result.pattern)
    }

    @Test
    fun `read access on the parent directory allows child reads and search`() {
        val parent = Files.createDirectories(tempDir.resolve("docs"))
        val file = Files.writeString(parent.resolve("notes.txt"), "alpha\nbeta\ngamma")
        seedPolicy(file, FsAccessType.READ, parent)

        val readResult = tool.evaluate(file.toString(), FsAccessType.READ)

        assertTrue(readResult.isAllowed, readResult.toString())
        assertEquals(parent.toAbsolutePath().normalize().toString(), readResult.pattern)
    }

    @Test
    fun `delete access on the parent directory allows delete_path only`() {
        val parent = Files.createDirectories(tempDir.resolve("trash"))
        val file = Files.writeString(parent.resolve("remove.txt"), "alpha")
        seedPolicy(file, FsAccessType.DELETE, parent)

        val deleteResult = tool.evaluate(file.toString(), FsAccessType.DELETE)
        val readResult = tool.evaluate(file.toString(), FsAccessType.READ)

        assertTrue(deleteResult.isAllowed, deleteResult.toString())
        assertFalse(readResult.isAllowed, readResult.toString())
        assertEquals(parent.toAbsolutePath().normalize().toString(), deleteResult.pattern)
    }

    @Test
    fun `read on parent and delete on grandparent are matched independently`() {
        val grandParent = Files.createDirectories(tempDir.resolve("workspace"))
        val parent = Files.createDirectories(grandParent.resolve("project"))
        val file = Files.writeString(parent.resolve("story.txt"), "alpha\nbeta")
        seedPolicy(file, FsAccessType.READ, parent)
        seedPolicy(file, FsAccessType.DELETE, grandParent)

        val readResult = tool.evaluate(file.toString(), FsAccessType.READ)
        val deleteResult = tool.evaluate(file.toString(), FsAccessType.DELETE)

        assertTrue(readResult.isAllowed, readResult.toString())
        assertTrue(deleteResult.isAllowed, deleteResult.toString())
        assertEquals(parent.toAbsolutePath().normalize().toString(), readResult.pattern)
        assertEquals(grandParent.toAbsolutePath().normalize().toString(), deleteResult.pattern)
    }

    @Test
    fun `non read tools stay denied when only read access exists`() {
        val parent = Files.createDirectories(tempDir.resolve("workspace"))
        val source = Files.writeString(parent.resolve("source.txt"), "alpha")
        seedPolicy(source, FsAccessType.READ, parent)

        val writeResult = tool.evaluate(source.toString(), FsAccessType.WRITE)
        val editResult = tool.evaluate(source.toString(), FsAccessType.EDIT)
        val deleteResult = tool.evaluate(source.toString(), FsAccessType.DELETE)
        val executeResult = tool.evaluate(source.toString(), FsAccessType.EXECUTE)

        assertFalse(writeResult.isAllowed, writeResult.toString())
        assertFalse(editResult.isAllowed, editResult.toString())
        assertFalse(deleteResult.isAllowed, deleteResult.toString())
        assertFalse(executeResult.isAllowed, executeResult.toString())
    }

    @Test
    fun `write access prefers the parent directory over the grandparent directory`() {
        val grandParent = Files.createDirectories(tempDir.resolve("workspace"))
        val parent = Files.createDirectories(grandParent.resolve("project"))
        val file = parent.resolve("note.txt")
        seedPolicy(file, FsAccessType.WRITE, grandParent)
        seedPolicy(file, FsAccessType.WRITE, parent)

        val policyResult = tool.evaluate(file.toString(), FsAccessType.WRITE)

        assertTrue(policyResult.isAllowed, policyResult.toString())
        assertEquals(parent.toAbsolutePath().normalize().toString(), policyResult.pattern)
    }

    private fun seedPolicy(path: Path, accessType: FsAccessType, scope: Path) {
        val scopeString = scope.toAbsolutePath().normalize().toString()
        permissionDecisionProvider = {
            PermissionDecision(
                isAllowed = true,
                lifetime = PolicyLifetime.SESSION,
                scope = scopeString
            )
        }
        tool.createOrUpdatePolicy(path, accessType)
        permissionDecisionProvider = {
            PermissionDecision(
                isAllowed = false,
                lifetime = PolicyLifetime.ONCE,
                scope = it.path,
                reason = "Denied for test"
            )
        }
    }
}
