package org.baizey.harness

import org.baizey.harness.policy.path.FsAccessType
import org.baizey.harness.tools.fs.FsToolTestSupport
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class SystemPromptTest : FsToolTestSupport() {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `includes filesystem policy summary with fallback guidance`() {
        val allowedRoot = Files.createDirectories(tempDir.resolve("allowed"))
        val deniedRoot = Files.createDirectories(tempDir.resolve("denied"))
        permissionDecisionProvider = {
            when (it.path) {
                allowedRoot.resolve("file.txt").toAbsolutePath().normalize().toString() ->
                    org.baizey.harness.PermissionDecision(
                        isAllowed = true,
                        lifetime = org.baizey.harness.policy.shared.PolicyLifetime.SESSION,
                        scope = allowedRoot.toAbsolutePath().normalize().toString(),
                        reason = "allowed workspace"
                    )

                deniedRoot.resolve("secret.txt").toAbsolutePath().normalize().toString() ->
                    org.baizey.harness.PermissionDecision(
                        isAllowed = false,
                        lifetime = org.baizey.harness.policy.shared.PolicyLifetime.SESSION,
                        scope = deniedRoot.toAbsolutePath().normalize().toString(),
                        reason = "blocked area"
                    )

                else -> error("Unexpected permission request: ${it.path}")
            }
        }

        pathPolicyLogic.createOrUpdatePolicy(allowedRoot.resolve("file.txt"), FsAccessType.READ)
        pathPolicyLogic.createOrUpdatePolicy(deniedRoot.resolve("secret.txt"), FsAccessType.WRITE)

        val prompt = SystemPrompt.text(toolContext)

        assertTrue(prompt.contains("**Filesystem Policy:**"), prompt)
        assertTrue(prompt.contains("${allowedRoot.toAbsolutePath().normalize()}: READ (allowed workspace)"), prompt)
        assertTrue(prompt.contains("${deniedRoot.toAbsolutePath().normalize()}: WRITE (blocked area)"), prompt)
    }
}
