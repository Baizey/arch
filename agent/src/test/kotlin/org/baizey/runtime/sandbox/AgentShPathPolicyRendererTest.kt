package org.baizey.runtime.sandbox

import org.baizey.harness.policy.PathPolicyLogic
import org.baizey.harness.policy.path.FsAccessType
import org.baizey.harness.policy.path.PathAccessInspection
import org.baizey.harness.policy.path.PathPolicy
import org.baizey.harness.policy.path.PathPolicyResult
import org.baizey.harness.policy.shared.PolicyLifetime
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class AgentShPathPolicyRendererTest {
    @Test
    fun `renders active PathPolicyLogic policies as AgentSH filesystem rules`(@TempDir tempDir: Path) {
        val hostRoot = tempDir.resolve("host").toAbsolutePath().normalize()
        val workspace = hostRoot.resolve("workspace")
        val privateWorkspace = workspace.resolve("private")
        val outsideHostRoot = tempDir.resolve("outside")

        val yaml = AgentShPathPolicyRenderer(
            hostRoot = hostRoot,
            containerHostRoot = "/host"
        ).render(
            policyName = "agent-1",
            pathPolicyLogic = FixedPathPolicyLogic(
                listOf(
                    PathPolicy(
                        pattern = workspace.toString(),
                        accessTypes = mutableListOf(FsAccessType.READ),
                        lifetime = PolicyLifetime.SESSION,
                        isAllowed = true,
                        reason = "workspace read"
                    ),
                    PathPolicy(
                        pattern = privateWorkspace.toString(),
                        accessTypes = mutableListOf(FsAccessType.WRITE, FsAccessType.DELETE),
                        lifetime = PolicyLifetime.SESSION,
                        isAllowed = false,
                        reason = "private workspace is immutable"
                    ),
                    PathPolicy(
                        pattern = outsideHostRoot.toString(),
                        accessTypes = mutableListOf(FsAccessType.READ),
                        lifetime = PolicyLifetime.SESSION,
                        isAllowed = true,
                        reason = "outside host root"
                    )
                )
            )
        )

        assertTrue(yaml.contains("name: \"agent-1\""), yaml)
        assertTrue(yaml.contains("- \"/host/workspace\""), yaml)
        assertTrue(yaml.contains("- \"/host/workspace/**\""), yaml)
        assertTrue(yaml.contains("- \"/host/workspace/private\""), yaml)
        assertTrue(yaml.contains("- \"read\""), yaml)
        assertTrue(yaml.contains("- \"open\""), yaml)
        assertTrue(yaml.contains("- \"write\""), yaml)
        assertTrue(yaml.contains("- \"delete\""), yaml)
        assertTrue(yaml.contains("decision: allow"), yaml)
        assertTrue(yaml.contains("decision: deny"), yaml)
        assertTrue(yaml.contains("name: \"allow-container-runtime-executables\""), yaml)
        assertTrue(yaml.contains("- \"/usr/bin/**\""), yaml)
        assertTrue(yaml.contains("- \"access\""), yaml)
        assertTrue(yaml.contains("name: \"allow-container-root-metadata\""), yaml)
        assertTrue(yaml.contains("- \"/\""), yaml)
        assertTrue(yaml.contains("name: \"allow-container-runtime-libraries\""), yaml)
        assertTrue(yaml.contains("- \"/lib/**\""), yaml)
        assertTrue(yaml.contains("- \"/dev/tty\""), yaml)
        assertTrue(yaml.contains("name: \"allow-agentsh-session-metadata\""), yaml)
        assertTrue(yaml.contains("- \"/var/lib/agentsh/sessions/**\""), yaml)
        assertTrue(yaml.contains("name: \"allow-agentsh-global-stat\""), yaml)
        assertTrue(yaml.contains("name: \"default-deny-files\""), yaml)
        assertFalse(yaml.contains("outside"), yaml)
        assertTrue(
            yaml.indexOf("/host/workspace/private") < yaml.indexOf("/host/workspace\""),
            yaml
        )
        assertTrue(
            yaml.indexOf("allow-container-runtime-libraries") < yaml.indexOf("default-deny-files"),
            yaml
        )
        assertTrue(
            yaml.indexOf("/host/workspace/private") < yaml.indexOf("allow-agentsh-global-stat"),
            yaml
        )
        assertTrue(
            yaml.indexOf("allow-agentsh-global-stat") < yaml.indexOf("default-deny-files"),
            yaml
        )
    }
}

private class FixedPathPolicyLogic(
    private val policies: List<PathPolicy>
) : PathPolicyLogic {
    override fun inspectPath(rawFilePath: String): PathAccessInspection {
        error("Not used by this test")
    }

    override fun evaluate(rawFilePath: String, accessType: FsAccessType): PathPolicyResult {
        error("Not used by this test")
    }

    override fun activePathPolicies(): List<PathPolicy> = policies

    override fun renderAgentPolicySummary(): String = ""

    override fun reloadFromPersistence() = Unit
}
