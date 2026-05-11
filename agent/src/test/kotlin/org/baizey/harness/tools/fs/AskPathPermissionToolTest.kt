package org.baizey.harness.tools.fs

import org.baizey.harness.PermissionDecision
import org.baizey.harness.PermissionRequest
import org.baizey.harness.policy.path.FsAccessType
import org.baizey.harness.policy.shared.PolicyLifetime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class AskPathPermissionToolTest : FsToolTestSupport() {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `returns existing allow without refreshing policy`() {
        val file = Files.writeString(tempDir.resolve("sample.txt"), "hello")
        seedPolicy(file, FsAccessType.READ, isAllowed = true)
        var refreshCount = 0
        val tool = AskPathPermissionTool(pathPolicyLogic) { refreshCount++ }

        val result = tool.askPathPermission(file.toString(), "READ")

        assertTrue(result.contains("Decision: ALLOW"), result)
        assertEquals(0, refreshCount)
    }

    @Test
    fun `returns existing deny without refreshing policy`() {
        val file = Files.writeString(tempDir.resolve("sample.txt"), "hello")
        seedPolicy(file, FsAccessType.READ, isAllowed = false)
        var refreshCount = 0
        val tool = AskPathPermissionTool(pathPolicyLogic) { refreshCount++ }

        val result = tool.askPathPermission(file.toString(), "READ")

        assertTrue(result.contains("Decision: DENY"), result)
        assertEquals(0, refreshCount)
    }

    @Test
    fun `prompts and refreshes policy when access is unknown`() {
        val file = Files.writeString(tempDir.resolve("sample.txt"), "hello")
        permissionDecisionProvider = { request ->
            PermissionDecision(
                isAllowed = true,
                lifetime = PolicyLifetime.SESSION,
                scope = request.path,
                reason = "Allowed for test"
            )
        }
        var refreshCount = 0
        val tool = AskPathPermissionTool(pathPolicyLogic) { refreshCount++ }

        val result = tool.askPathPermission(file.toString(), "READ")

        assertTrue(result.contains("Decision: ALLOW"), result)
        assertTrue(result.contains("Sandbox policy refreshed."), result)
        assertEquals(1, refreshCount)
    }

    @Test
    fun `rejects unknown access types`() {
        val file = Files.writeString(tempDir.resolve("sample.txt"), "hello")
        val tool = AskPathPermissionTool(pathPolicyLogic)

        val result = tool.askPathPermission(file.toString(), "FLY")

        assertTrue(result.contains("Unknown accessType 'FLY'"), result)
    }

    private fun seedPolicy(path: Path, accessType: FsAccessType, isAllowed: Boolean) {
        permissionDecisionProvider = { request: PermissionRequest ->
            PermissionDecision(
                isAllowed = isAllowed,
                lifetime = PolicyLifetime.SESSION,
                scope = request.path,
                reason = if (isAllowed) "Allowed for seed" else "Denied for seed"
            )
        }
        pathPolicyLogic.evaluate(path.toString(), accessType)
    }
}
