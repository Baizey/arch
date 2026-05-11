package org.baizey.runtime.sandbox

import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.baizey.harness.policy.PathPolicyLogic
import org.baizey.runtime.ErrorLog
import org.baizey.runtime.SandboxConfig
import org.baizey.utils.IO.json
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.time.Duration

class AgentShSandboxService(
    private val config: SandboxConfig,
    private val pathPolicyLogic: PathPolicyLogic,
    private val agentId: String,
    private val hostWorkingDirectory: Path = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize(),
    private val docker: DockerCommandRunner = ProcessDockerCommandRunner(config.dockerCommand)
) : AutoCloseable {
    private val lock = Any()
    private val manager = AgentSandboxManager(config, docker)

    private var handle: AgentSandboxHandle? = null
    private var sessionId: String? = null

    fun exec(command: String, timeout: Duration): SandboxShellResult {
        require(command.isNotBlank()) { "Command cannot be blank." }
        require(timeout.isPositive()) { "timeoutSeconds must be greater than 0." }

        synchronized(lock) {
            val activeHandle = ensureSandbox()
            val activeSessionId = ensureSession(activeHandle)
            return runExec(activeHandle, activeSessionId, command, timeout)
        }
    }

    fun refreshPolicy() {
        synchronized(lock) {
            val activeHandle = handle ?: return
            manager.syncPathPolicy(activeHandle, pathPolicyLogic)
            recreateSession(activeHandle)
        }
    }

    override fun close() {
        synchronized(lock) {
            val activeHandle = handle
            val activeSessionId = sessionId
            sessionId = null
            handle = null

            if (activeHandle != null && activeSessionId != null) {
                runCatching {
                    docker.run(
                        listOf(
                            "exec",
                            activeHandle.containerName,
                            "/usr/bin/agentsh",
                            "--api-key",
                            config.apiKey,
                            "session",
                            "destroy",
                            activeSessionId
                        )
                    )
                }.onFailure { exception ->
                    logSandboxError("sandbox_session_destroy", exception)
                }
            }

            if (activeHandle != null) {
                runCatching { manager.stop(activeHandle) }
                    .onFailure { exception -> logSandboxError("sandbox_container_stop", exception) }
            }
        }
    }

    private fun ensureSandbox(): AgentSandboxHandle {
        val existing = handle
        if (existing != null) return existing

        val created = manager.start(
            agentId = agentId,
            pathPolicyLogic = pathPolicyLogic
        )
        handle = created
        return created
    }

    private fun ensureSession(handle: AgentSandboxHandle): String {
        val existing = sessionId
        if (existing != null) return existing
        val created = createSession(handle)
        sessionId = created
        return created
    }

    private fun recreateSession(handle: AgentSandboxHandle) {
        sessionId?.let { activeSessionId ->
            runCatching {
                docker.run(
                    listOf(
                        "exec",
                        handle.containerName,
                        "/usr/bin/agentsh",
                        "--api-key",
                        config.apiKey,
                        "session",
                        "destroy",
                        activeSessionId
                    )
                )
            }.onFailure { exception ->
                logSandboxError("sandbox_session_destroy", exception)
            }
        }
        sessionId = createSession(handle)
    }

    private fun createSession(handle: AgentSandboxHandle): String {
        val workspace = mapHostPath(hostWorkingDirectory, handle)
        val response = docker.run(
            listOf(
                "exec",
                handle.containerName,
                "/usr/bin/agentsh",
                "--api-key",
                config.apiKey,
                "session",
                "create",
                "--json",
                "--workspace",
                workspace,
                "--policy",
                handle.policyName
            )
        )
        return json.parseToJsonElement(response).jsonObject["id"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalStateException("AgentSH session create response did not include an id.")
    }

    private fun runExec(
        handle: AgentSandboxHandle,
        sessionId: String,
        command: String,
        timeout: Duration
    ): SandboxShellResult {
        val (exitCode, response) = runDockerAllowFailure(
            listOf(
                "exec",
                handle.containerName,
                "/usr/bin/agentsh",
                "--api-key",
                config.apiKey,
                "exec",
                sessionId,
                "--output",
                "json",
                "--events",
                "all",
                "--timeout",
                "${timeout.inWholeSeconds}s",
                "--",
                "sh",
                "-lc",
                command
            )
        )

        val root = json.parseToJsonElement(response).jsonObject
        val result = root["result"]?.jsonObject
        val events = root["events"]?.jsonObject
        val blockedOperations = events?.get("blocked_operations")
            ?.jsonArray
            ?.mapNotNull { blocked ->
                val fields = blocked.jsonObject["fields"]?.jsonObject
                val operation = blocked.jsonObject["operation"]?.jsonPrimitive?.contentOrNull
                    ?: blocked.jsonObject["type"]?.jsonPrimitive?.contentOrNull
                    ?: "blocked"
                val path = fields?.get("path")?.jsonPrimitive?.contentOrNull
                    ?: fields?.get("target")?.jsonPrimitive?.contentOrNull
                    ?: fields?.get("file")?.jsonPrimitive?.contentOrNull
                val rule = blocked.jsonObject["policy"]?.jsonObject?.get("rule")?.jsonPrimitive?.contentOrNull
                listOfNotNull(operation, path, rule?.let { "rule=$it" }).joinToString(" ")
            }
            ?: emptyList()

        val stderr = result?.get("stderr")?.jsonPrimitive?.contentOrNull.orEmpty()
        val guidanceReason = root["guidance"]?.jsonObject?.get("reason")?.jsonPrimitive?.contentOrNull
        val maybePolicyDenied = blockedOperations.isNotEmpty() ||
            stderr.contains("denied", ignoreCase = true) ||
            stderr.contains("policy", ignoreCase = true) ||
            guidanceReason?.contains("policy", ignoreCase = true) == true

        return SandboxShellResult(
            exitCode = result?.get("exit_code")?.jsonPrimitive?.intOrNull ?: exitCode,
            stdout = result?.get("stdout")?.jsonPrimitive?.contentOrNull.orEmpty(),
            stderr = stderr,
            blockedOperations = blockedOperations,
            maybePolicyDenied = maybePolicyDenied,
            rawJson = response
        )
    }

    private fun runDockerAllowFailure(args: List<String>): Pair<Int, String> {
        val process = ProcessBuilder(listOf(config.dockerCommand) + args)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.readAllBytes().decodeToString().trim()
        val exitCode = process.waitFor()
        return exitCode to output
    }

    private fun mapHostPath(path: Path, handle: AgentSandboxHandle): String {
        val cleanPath = path.toAbsolutePath().normalize()
        val hostRoot = handle.hostRoot.toAbsolutePath().normalize()
        require(cleanPath.startsWith(hostRoot)) {
            "Working directory $cleanPath is outside sandbox host root $hostRoot."
        }
        val relativePath = hostRoot.relativize(cleanPath).toString().replace('\\', '/')
        val root = handle.containerHostRoot.trimEnd('/')
        return if (relativePath.isBlank()) root else "$root/$relativePath"
    }

    private fun logSandboxError(source: String, exception: Throwable) {
        ErrorLog.log(
            source = source,
            exception = exception,
            context = mapOf("agentId" to agentId)
        )
    }
}

data class SandboxShellResult(
    val exitCode: Int?,
    val stdout: String,
    val stderr: String,
    val blockedOperations: List<String>,
    val maybePolicyDenied: Boolean,
    val rawJson: String
)
