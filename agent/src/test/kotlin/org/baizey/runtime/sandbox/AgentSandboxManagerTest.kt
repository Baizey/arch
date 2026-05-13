package org.baizey.runtime.sandbox

import org.baizey.harness.policy.PathPolicyLogic
import org.baizey.harness.policy.path.FsAccessType
import org.baizey.harness.policy.path.PathAccessInspection
import org.baizey.harness.policy.path.PathPolicy
import org.baizey.harness.policy.path.PathPolicyResult
import org.baizey.harness.policy.shared.PolicyLifetime
import org.baizey.runtime.SandboxConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

class AgentSandboxManagerTest {
    @Test
    fun `starts one docker container for an agent`(@TempDir tempDir: Path) {
        val runner = RecordingDockerCommandRunner()
        val config = SandboxConfig(
            dockerCommand = "docker",
            image = "arch-agentsh:test",
            apiKey = "sandbox-key",
            hostRoot = tempDir.resolve("host"),
            containerHostRoot = "/host",
            policiesDirectory = tempDir.resolve("policies"),
            keysDirectory = tempDir.resolve("keys"),
            logsDirectory = tempDir.resolve("logs"),
            stateVolumePrefix = "arch-test",
            portStart = 18080,
            privileged = true
        )

        val handle = AgentSandboxManager(config, runner).start(
            agentId = "Agent 1",
            policyName = "agent-policy",
            hostPort = 19000
        )

        assertEquals("arch-agentsh-1", handle.containerName)
        assertEquals("http://127.0.0.1:19000", handle.endpoint)
        assertTrue(config.policiesDirectory.exists())
        assertTrue(config.keysDirectory.resolve("api_keys.yaml").exists())
        assertTrue(config.policiesDirectory.resolve("default.yaml").exists())
        assertTrue(tempDir.resolve("logs/1").exists())

        val args = runner.commands.single()
        assertEquals("run", args[0])
        assertTrue(args.contains("-d"))
        assertTrue(args.contains("--init"))
        assertTrue(args.contains("--privileged"))
        assertTrue(args.contains("arch-agentsh-1"))
        assertTrue(args.contains("19000:18080"))
        assertTrue(args.contains("AGENTSH_POLICY_NAME=agent-policy"))
        assertTrue(args.contains("arch-agentsh:test"))
    }

    @Test
    fun `writes generated PathPolicyLogic policy when starting an agent sandbox`(@TempDir tempDir: Path) {
        val runner = RecordingDockerCommandRunner()
        val hostRoot = tempDir.resolve("host").toAbsolutePath().normalize()
        val workspace = hostRoot.resolve("workspace")
        val config = SandboxConfig(
            dockerCommand = "docker",
            image = "arch-agentsh:test",
            apiKey = "sandbox-key",
            hostRoot = hostRoot,
            containerHostRoot = "/host",
            policiesDirectory = tempDir.resolve("policies"),
            keysDirectory = tempDir.resolve("keys"),
            logsDirectory = tempDir.resolve("logs"),
            stateVolumePrefix = "arch-test",
            portStart = 18080,
            privileged = true
        )
        val pathPolicyLogic = ManagerFixedPathPolicyLogic(
            listOf(
                PathPolicy(
                    pattern = workspace.toString(),
                    accessTypes = mutableListOf(FsAccessType.READ),
                    lifetime = PolicyLifetime.SESSION,
                    isAllowed = true,
                    reason = "workspace read"
                )
            )
        )

        val handle = AgentSandboxManager(config, runner).start(
            agentId = "Agent 1",
            hostPort = 19000,
            pathPolicyLogic = pathPolicyLogic
        )

        val generatedPolicy = config.policiesDirectory.resolve("agent-1.yaml")
        val generatedPolicyText = Files.readString(generatedPolicy)

        assertEquals("agent-1", handle.policyName)
        assertTrue(generatedPolicy.exists())
        assertTrue(config.policiesDirectory.resolve("default.yaml").exists())
        assertTrue(generatedPolicyText.contains("name: \"agent-1\""), generatedPolicyText)
        assertTrue(generatedPolicyText.contains("- \"/host/workspace\""), generatedPolicyText)
        assertTrue(generatedPolicyText.contains("- \"read\""), generatedPolicyText)
        assertTrue(generatedPolicyText.contains("name: \"default-deny-files\""), generatedPolicyText)
        assertTrue(runner.commands.single().contains("AGENTSH_POLICY_NAME=agent-1"))
    }

    @Test
    fun `starts without an enabled flag because sandboxing is always configured`(@TempDir tempDir: Path) {
        val runner = RecordingDockerCommandRunner()
        val config = SandboxConfig(
            dockerCommand = "docker",
            image = "arch-agentsh:test",
            apiKey = "sandbox-key",
            hostRoot = tempDir,
            containerHostRoot = "/host",
            policiesDirectory = tempDir.resolve("policies"),
            keysDirectory = tempDir.resolve("keys"),
            logsDirectory = tempDir.resolve("logs"),
            stateVolumePrefix = "arch-test",
            portStart = 18080,
            privileged = false
        )

        val handle = AgentSandboxManager(config, runner).start("agent")

        assertEquals("arch-agentsh-agent", handle.containerName)
        assertTrue(handle.endpoint.startsWith("http://127.0.0.1:"))
        assertEquals("run", runner.commands.single()[0])
    }
}

private class RecordingDockerCommandRunner : DockerCommandRunner {
    val commands = mutableListOf<List<String>>()

    override fun run(args: List<String>): String {
        commands.add(args)
        return "container-id"
    }
}

private class ManagerFixedPathPolicyLogic(
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
