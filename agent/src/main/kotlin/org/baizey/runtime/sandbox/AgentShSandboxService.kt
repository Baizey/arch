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
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class AgentShSandboxService(
    private val config: SandboxConfig,
    private val pathPolicyLogic: PathPolicyLogic,
    private val agentId: String,
    private val hostWorkingDirectory: Path = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize(),
    private val docker: DockerCommandRunner = ProcessDockerCommandRunner(config.dockerCommand),
    private val readinessWaiter: (AgentSandboxHandle, String) -> Unit = ::waitForSandboxReadiness,
    private val dockerAllowFailureRunner: (String, List<String>) -> Pair<Int, String> = ::runDockerAllowFailure
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
        try {
            readinessWaiter(created, config.apiKey)
        } catch (exception: Exception) {
            runCatching { manager.stop(created) }
                .onFailure { stopException -> logSandboxError("sandbox_container_stop_after_start_failure", stopException) }
            throw exception
        }
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
        val sandboxCommand = rewriteHostPathsForSandbox(command, handle)
        val (exitCode, response) = dockerAllowFailureRunner(
            config.dockerCommand,
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
                "/usr/bin/env",
                "-u",
                "BASH_ENV",
                "HOME=/tmp",
                "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
                "/usr/bin/bash.real",
                "--noprofile",
                "--norc",
                "-c",
                sandboxCommand
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

    private fun rewriteHostPathsForSandbox(command: String, handle: AgentSandboxHandle): String {
        var rewritten = rewriteMalformedMountedWindowsPaths(command, handle)
        rewritten = rewriteQuotedWindowsPaths(rewritten, handle, '"')
        rewritten = rewriteQuotedWindowsPaths(rewritten, handle, '\'')
        return WINDOWS_UNQUOTED_PATH_REGEX.replace(rewritten) { match ->
            mapWindowsPathToSandbox(match.value, handle) ?: match.value
        }
    }

    private fun rewriteMalformedMountedWindowsPaths(command: String, handle: AgentSandboxHandle): String {
        val containerRoot = handle.containerHostRoot.trimEnd('/')
        val hostRoot = handle.hostRoot.toAbsolutePath().normalize()
        val drive = hostRoot.root?.toString()?.firstOrNull()?.uppercaseChar() ?: return command
        val patterns = listOf(
            Regex("""${Regex.escape(containerRoot)}/$drive:/""", RegexOption.IGNORE_CASE),
            Regex("""${Regex.escape(containerRoot)}/$drive/""", RegexOption.IGNORE_CASE)
        )
        return patterns.fold(command) { current, pattern ->
            pattern.replace(current, "$containerRoot/")
        }
    }

    private fun rewriteQuotedWindowsPaths(command: String, handle: AgentSandboxHandle, quote: Char): String {
        val pattern = when (quote) {
            '"' -> WINDOWS_DOUBLE_QUOTED_PATH_REGEX
            '\'' -> WINDOWS_SINGLE_QUOTED_PATH_REGEX
            else -> error("Unsupported quote: $quote")
        }
        return pattern.replace(command) { match ->
            val rawPath = match.groupValues[1]
            val mapped = mapWindowsPathToSandbox(rawPath, handle) ?: rawPath
            "$quote$mapped$quote"
        }
    }

    private fun mapWindowsPathToSandbox(rawPath: String, handle: AgentSandboxHandle): String? {
        return runCatching { Path.of(rawPath) }
            .getOrNull()
            ?.let { path ->
                runCatching { mapHostPath(path, handle) }.getOrNull()
            }
    }

    private fun logSandboxError(source: String, exception: Throwable) {
        ErrorLog.log(
            source = source,
            exception = exception,
            context = mapOf("agentId" to agentId)
        )
    }
}

private val WINDOWS_DOUBLE_QUOTED_PATH_REGEX = Regex("\"([A-Za-z]:\\\\[^\"]*)\"")
private val WINDOWS_SINGLE_QUOTED_PATH_REGEX = Regex("'([A-Za-z]:\\\\[^']*)'")
private val WINDOWS_UNQUOTED_PATH_REGEX = Regex("""(?<![\w/])([A-Za-z]:\\[^\s"'`|&;()<>]+)""")

private fun runDockerAllowFailure(dockerCommand: String, args: List<String>): Pair<Int, String> {
    val process = ProcessBuilder(listOf(dockerCommand) + args)
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.readAllBytes().decodeToString().trim()
    val exitCode = process.waitFor()
    return exitCode to output
}

private fun waitForSandboxReadiness(
    handle: AgentSandboxHandle,
    apiKey: String,
    timeout: Duration = 10.seconds,
    pollInterval: Duration = 200.milliseconds
) {
    val deadline = System.nanoTime() + timeout.inWholeNanoseconds
    var lastFailure: Exception? = null

    while (System.nanoTime() < deadline) {
        try {
            val connection = openHealthConnection(handle.endpoint, apiKey)
            try {
                if (connection.responseCode in 200..299) {
                    return
                }
            } finally {
                connection.disconnect()
            }
        } catch (exception: Exception) {
            lastFailure = exception
        }
        Thread.sleep(pollInterval.inWholeMilliseconds)
    }

    throw IllegalStateException(
        "AgentSH sandbox at ${handle.endpoint} did not become ready within ${timeout.inWholeSeconds}s.",
        lastFailure
    )
}

private fun openHealthConnection(endpoint: String, apiKey: String): HttpURLConnection {
    return (URI("$endpoint/health").toURL().openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 1_000
        readTimeout = 1_000
        setRequestProperty("X-API-Key", apiKey)
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
