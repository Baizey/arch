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
                "HOME=/tmp",
                "PATH=/usr/local/bin:/usr/bin:/bin",
                "/usr/bin/bash.real",
                "--noprofile",
                "--norc",
                "-c",
                "ls -la /host/Repositories/small_agent/agent"
            ),
            execArgs.takeLast(8)
        )
    }

    @Test
    fun `exec rewrites forward slash Windows host paths before running command`(@TempDir tempDir: Path) {
        val execArgs = mutableListOf<String>()
        val service = AgentShSandboxService(
            config = sandboxConfig(tempDir, Path.of("C:\\")),
            pathPolicyLogic = ServiceTestPathPolicyLogic(),
            agentId = "agent-1",
            hostWorkingDirectory = tempDir,
            docker = SequencedDockerRunner(mutableListOf()),
            readinessWaiter = { _, _ -> },
            dockerAllowFailureRunner = { _, args ->
                execArgs += args
                0 to """{"result":{"exit_code":0,"stdout":"ARCH TEST FILE FROM SHELL ONLY","stderr":""},"events":{"blocked_operations":[]}}"""
            }
        )

        val result = service.exec(
            """echo "ARCH TEST FILE FROM SHELL ONLY" > "C:/Repositories/small_agent/agent/example.txt" && cat "C:/Repositories/small_agent/agent/example.txt"""",
            5.seconds
        )

        assertEquals("ARCH TEST FILE FROM SHELL ONLY", result.stdout)
        assertEquals(
            """echo "ARCH TEST FILE FROM SHELL ONLY" > "/host/Repositories/small_agent/agent/example.txt" && cat "/host/Repositories/small_agent/agent/example.txt"""",
            execArgs.last()
        )
    }

    @Test
    fun `exec rewrites Windows type nul empty file idiom before running command`(@TempDir tempDir: Path) {
        val execArgs = mutableListOf<String>()
        val service = AgentShSandboxService(
            config = sandboxConfig(tempDir, Path.of("C:\\")),
            pathPolicyLogic = ServiceTestPathPolicyLogic(),
            agentId = "agent-1",
            hostWorkingDirectory = tempDir,
            docker = SequencedDockerRunner(mutableListOf()),
            readinessWaiter = { _, _ -> },
            dockerAllowFailureRunner = { _, args ->
                execArgs += args
                0 to """{"result":{"exit_code":0,"stdout":"","stderr":""},"events":{"blocked_operations":[]}}"""
            }
        )

        val result = service.exec(
            """type nul > "C:/Repositories/small_agent/agent/src/test/kotlin/org/baizey/runtime/sandbox/example.txt"""",
            5.seconds
        )

        assertEquals("", result.stdout)
        assertEquals(
            """: > "/host/Repositories/small_agent/agent/src/test/kotlin/org/baizey/runtime/sandbox/example.txt"""",
            execArgs.last()
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

    @Test
    fun `refreshPolicy recreates the session with a revisioned policy name`(@TempDir tempDir: Path) {
        val events = mutableListOf<String>()
        val docker = SequencedDockerRunner(events)
        val service = AgentShSandboxService(
            config = sandboxConfig(tempDir),
            pathPolicyLogic = ServiceTestPathPolicyLogic(),
            agentId = "agent-1",
            hostWorkingDirectory = tempDir,
            docker = docker,
            readinessWaiter = { _, _ -> events += "ready" },
            dockerAllowFailureRunner = { _, _ ->
                events += "exec"
                0 to """{"result":{"exit_code":0,"stdout":"","stderr":""},"events":{"blocked_operations":[]}}"""
            }
        )

        service.exec("pwd", 5.seconds)
        service.refreshPolicy()

        val createdPolicies = docker.commands
            .filter { it.contains("session") && it.contains("create") }
            .map { args -> args[args.indexOf("--policy") + 1] }

        assertEquals(listOf("agent-1-policy-0", "agent-1-policy-1"), createdPolicies)
        assertEquals(
            listOf("run", "ready", "session-create", "exec", "session-destroy", "session-create"),
            events
        )
    }
}

private class SequencedDockerRunner(
    private val events: MutableList<String>
) : DockerCommandRunner {
    val commands = mutableListOf<List<String>>()

    override fun run(args: List<String>): String {
        commands.add(args)
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

            args.contains("session") && args.contains("destroy") -> {
                events += "session-destroy"
                "destroyed"
            }
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
