package org.baizey.runtime.sandbox

import org.baizey.harness.policy.PathPolicyLogic
import org.baizey.runtime.SandboxConfig
import java.net.ServerSocket
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText

class AgentSandboxManager(
    private val config: SandboxConfig,
    private val docker: DockerCommandRunner = ProcessDockerCommandRunner(config.dockerCommand)
) {
    fun start(
        agentId: String,
        policyName: String = "default",
        hostPort: Int? = null,
        pathPolicyLogic: PathPolicyLogic? = null
    ): AgentSandboxHandle {
        val safeAgentId = agentId.toAgentContainerToken()
        val containerName = "arch-agentsh-$safeAgentId"
        val port = hostPort ?: findAvailablePort(config.portStart)
        val logDirectory = config.logsDirectory.resolve(safeAgentId)
        val effectivePolicyName = if (pathPolicyLogic == null) {
            policyName
        } else if (policyName == "default") {
            "agent-$safeAgentId"
        } else {
            policyName.toContainerToken()
        }

        config.policiesDirectory.createDirectories()
        config.keysDirectory.createDirectories()
        ensureDefaultPolicy()
        ensureApiKeysFile()
        if (pathPolicyLogic != null) {
            writePathPolicy(effectivePolicyName, pathPolicyLogic)
        }
        logDirectory.createDirectories()

        docker.run(
            buildList {
                add("run")
                add("-d")
                add("--init")
                add("--name")
                add(containerName)
                if (config.privileged) {
                    add("--privileged")
                }
                add("-p")
                add("$port:18080")
                add("--mount")
                add(bindMount(config.hostRoot, config.containerHostRoot))
                add("--mount")
                add(bindMount(config.policiesDirectory, "/etc/agentsh/policies", readOnly = true))
                add("--mount")
                add(bindMount(config.keysDirectory.resolve("api_keys.yaml"), "/etc/agentsh/api_keys.yaml", readOnly = true))
                add("--mount")
                add(bindMount(logDirectory, "/var/log/agentsh"))
                add("--mount")
                add("type=volume,source=${config.stateVolumePrefix}-$safeAgentId-state,target=/var/lib/agentsh")
                add("-e")
                add("AGENTSH_POLICY_NAME=$effectivePolicyName")
                add(config.image)
            }
        )

        return AgentSandboxHandle(
            agentId = agentId,
            containerName = containerName,
            endpoint = "http://127.0.0.1:$port",
            hostPort = port,
            hostRoot = config.hostRoot,
            containerHostRoot = config.containerHostRoot,
            policyName = effectivePolicyName,
            logDirectory = logDirectory
        )
    }

    fun stop(handle: AgentSandboxHandle) {
        docker.run(listOf("rm", "-f", handle.containerName))
    }

    fun syncPathPolicy(policyName: String, pathPolicyLogic: PathPolicyLogic): String {
        val effectivePolicyName = policyName.toContainerToken()
        writePathPolicy(effectivePolicyName, pathPolicyLogic)
        return effectivePolicyName
    }

    private fun bindMount(source: Path, target: String, readOnly: Boolean = false): String {
        val suffix = if (readOnly) ",readonly" else ""
        return "type=bind,source=${source.toAbsolutePath()},target=$target$suffix"
    }

    private fun ensureDefaultPolicy() {
        val defaultPolicy = config.policiesDirectory.resolve("default.yaml")
        if (!defaultPolicy.exists()) {
            defaultPolicy.writeText(DEFAULT_POLICY)
        }
    }

    private fun ensureApiKeysFile() {
        val apiKeysFile = config.keysDirectory.resolve("api_keys.yaml")
        apiKeysFile.writeText(
            """
            - id: "arch-runtime"
              key: "${config.apiKey}"
              description: "Arch runtime AgentSH API key."
              allowed_sessions: 100
            """.trimIndent()
        )
    }

    private fun writePathPolicy(policyName: String, pathPolicyLogic: PathPolicyLogic) {
        val policy = AgentShPathPolicyRenderer(
            hostRoot = config.hostRoot,
            containerHostRoot = config.containerHostRoot
        ).render(policyName, pathPolicyLogic)
        config.policiesDirectory.resolve("$policyName.yaml").writeText(policy)
    }

    private fun findAvailablePort(start: Int): Int {
        for (port in start..65535) {
            if (isAvailable(port)) return port
        }
        throw IllegalStateException("No available sandbox port at or above $start.")
    }

    private fun isAvailable(port: Int): Boolean {
        return try {
            ServerSocket(port).use { true }
        } catch (_: Exception) {
            false
        }
    }
}

data class AgentSandboxHandle(
    val agentId: String,
    val containerName: String,
    val endpoint: String,
    val hostPort: Int,
    val hostRoot: Path,
    val containerHostRoot: String,
    val policyName: String,
    val logDirectory: Path
)

interface DockerCommandRunner {
    fun run(args: List<String>): String
}

class ProcessDockerCommandRunner(private val dockerCommand: String) : DockerCommandRunner {
    override fun run(args: List<String>): String {
        val command = listOf(dockerCommand) + args
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.readAllBytes().decodeToString().trim()
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw IllegalStateException("Docker command exited with code $exitCode: $output")
        }
        return output
    }
}

internal fun String.toContainerToken(): String {
    val token = lowercase()
        .replace(Regex("[^a-z0-9_.-]+"), "-")
        .trim('-', '.', '_')
    return token.ifBlank { "agent" }
}

internal fun String.toAgentContainerToken(): String {
    return toContainerToken()
        .removePrefix("agent-")
        .ifBlank { "agent" }
}

private val DEFAULT_POLICY = """
version: 1
name: default
description: Audit-first local agent policy. Override per agent with AGENTSH_POLICY_NAME.

file_rules:
  - name: audit-all-files
    paths:
      - "**"
    operations:
      - "*"
    decision: audit

network_rules:
  - name: audit-all-network
    domains:
      - "*"
    decision: audit

command_rules:
  - name: audit-all-commands
    commands:
      - "*"
    decision: audit

resource_limits:
  max_memory_mb: 4096
  pids_max: 256
  command_timeout: 30m
  session_timeout: 8h
  idle_timeout: 30m

audit:
  log_allowed: true
  log_denied: true
  log_approved: true
  include_stdout: false
  include_stderr: true
  include_file_content: false
""".trimIndent()
