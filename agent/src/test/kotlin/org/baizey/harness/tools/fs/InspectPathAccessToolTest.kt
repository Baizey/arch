package org.baizey.harness.tools.fs

import org.baizey.harness.PermissionDecision
import org.baizey.harness.policy.shared.PolicyLifetime
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class InspectPathAccessToolTest : FsToolTestSupport() {
    private val tool = InspectPathAccessTool(pathPolicyLogic)

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `reports ask permission for unmatched paths`() {
        val file = tempDir.resolve("unmatched.txt")

        val result = tool.inspectPathAccess(file.toString())

        assertTrue(result.contains("Path: ${file.toAbsolutePath().normalize()}"), result)
        assertTrue(result.contains("- READ: ASK_PERMISSION"), result)
        assertTrue(result.contains("- WRITE: ASK_PERMISSION"), result)
        assertTrue(result.contains("No matching policy exists yet. Ask permission before using this path."), result)
    }

    @Test
    fun `reports explicit allow and unmatched access separately`() {
        val parent = Files.createDirectories(tempDir.resolve("docs"))
        val file = parent.resolve("notes.txt")
        permissionDecisionProvider = {
            PermissionDecision(
                isAllowed = true,
                lifetime = PolicyLifetime.SESSION,
                scope = parent.toAbsolutePath().normalize().toString(),
                reason = "workspace read access"
            )
        }
        pathPolicyLogic.createOrUpdatePolicy(file, org.baizey.harness.policy.path.FsAccessType.READ)

        val result = tool.inspectPathAccess(file.toString())

        assertTrue(result.contains("- READ: ALLOW (pattern: ${parent.toAbsolutePath().normalize()})"), result)
        assertTrue(result.contains("Reason: workspace read access"), result)
        assertTrue(result.contains("- WRITE: ASK_PERMISSION"), result)
    }

}
