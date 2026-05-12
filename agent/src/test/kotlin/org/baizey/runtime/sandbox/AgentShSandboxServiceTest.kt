package org.baizey.runtime.sandbox

import org.baizey.harness.policy.PathPolicyLogic
import org.baizey.harness.policy.path.FsAccessType
import org.baizey.harness.policy.path.PathAccessInspection
import org.baizey.harness.policy.path.PathPolicy
import org.baizey.harness.policy.path.PathPolicyResult
import org.baizey.runtime.SandboxConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

class AgentShSandboxServiceTest {
    @Test
    fun `exec waits for sandbox readiness before creating a session`(@TempDir tempDir: Path) {
        val events = mutableListOf<String>()
        val execArgs = mutableListOf<String>()
        val docker = SequencedDockerRunner(events)
        val service = AgentShSandboxService(
            config = sandboxConfig(tempDir, Path.of("C:\\")),
            pathPolicyLogic = ServiceTestPathPolicyLogic(),
            agentId = "agent-1",
            hostWorkingDirectory = tempDir,
            docker = docker,
            readinessWaiter = { _, _ -> events += "ready" },
            dockerAllowFailureRunner = { _, args ->
                events += "exec"
                execArgs += args
                0 to """{"result":{"exit_code":0,"stdout":"ok","stderr":""},"events":{"blocked_operations":[]}}"""
            }
        )

        val result = service.exec("""ls -la /host/C:/Repositories/small_agent/agent""", 5.seconds)

        assertEquals(listOf("run", "ready", "session-create", "exec"), events)
        assertEquals(0, result.exitCode)
        assertEquals("ok", result.stdout)
        assertEquals(
            listOf(
                "/usr/bin/env",
                "-u",
                "BASH_ENV",
                "HOME=/tmp",
                "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
                "/usr/bin/bash.real",
                "--noprofile",
                "--norc",
                "-c",
                "ls -la /host/Repositories/small_agent/agent"
            ),
            execArgs.takeLast(10)
        )
    }

    @Test
    fun `failed readiness tears the container back down`(@TempDir tempDir: Path) {
        val events = mutableListOf<String>()
        val docker = SequencedDockerRunner(events)
        val service = AgentShSandboxService(
            config = sandboxConfig(tempDir),
            pathPolicyLogic = ServiceTestPathPolicyLogic(),
            agentId = "agent-1",
            hostWorkingDirectory = tempDir,
            docker = docker,
            readinessWaiter = { _, _ ->
                events += "ready-failed"
                throw IllegalStateException("not ready")
            },
            dockerAllowFailureRunner = { _, _ -> error("should not execute command when readiness fails") }
        )

        val exception = assertThrows(IllegalStateException::class.java) {
            service.exec("dir", 5.seconds)
        }

        assertEquals("not ready", exception.message)
        assertEquals(listOf("run", "ready-failed", "stop"), events)
    }
}

private class SequencedDockerRunner(
    private val events: MutableList<String>
) : DockerCommandRunner {
    override fun run(args: List<String>): String {
        return when {
            args.firstOrNull() == "run" -> {
                events += "run"
                "container-id"
            }

            args.take(2) == listOf("rm", "-f") -> {
                events += "stop"
                "removed"
            }

            args.contains("session") && args.contains("create") -> {
                events += "session-create"
                """{"id":"session-1"}"""
            }

            args.contains("session") && args.contains("destroy") -> "destroyed"
            else -> error("Unexpected docker args: $args")
        }
    }
}

private class ServiceTestPathPolicyLogic : PathPolicyLogic {
    override fun inspectPath(rawFilePath: String): PathAccessInspection = error("Not used in this test")

    override fun evaluate(rawFilePath: String, accessType: FsAccessType): PathPolicyResult = error("Not used in this test")

    override fun activePathPolicies(): List<PathPolicy> = emptyList()

    override fun renderAgentPolicySummary(): String = ""

    override fun reloadFromPersistence() = Unit
}

private fun sandboxConfig(tempDir: Path): SandboxConfig {
    return sandboxConfig(tempDir, tempDir)
}

private fun sandboxConfig(tempDir: Path, hostRoot: Path): SandboxConfig {
    return SandboxConfig(
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
        privileged = false
    )
}
