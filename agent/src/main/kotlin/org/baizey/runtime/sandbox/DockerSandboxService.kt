package org.baizey.runtime.sandbox

import kotlinx.serialization.Serializable
import org.baizey.harness.policy.PathPolicyLogic
import org.baizey.harness.policy.path.PathPolicy
import org.baizey.harness.policy.path.PathPolicySnapshot
import org.baizey.runtime.ErrorLog
import org.baizey.runtime.SandboxConfig
import org.baizey.runtime.findAvailablePort
import org.baizey.utils.IO.fromJson
import org.baizey.utils.IO.toJson
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.time.Duration
import java.util.UUID
import kotlin.io.path.exists

class DockerSandboxService(
    private val config: SandboxConfig,
    private val pathPolicyLogic: PathPolicyLogic,
    agentId: String = UUID.randomUUID().toString()
) : AutoCloseable {
    private val lock = Any()
    private val containerName = "${config.containerNamePrefix}-${agentId.toContainerToken()}"
    private val workspaceHostPath = config.workspaceHostPath.toAbsolutePath().normalize()
    private val hostPort = findAvailablePort(config.hostPortStart)
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()
    private val endpoint = URI("http://127.0.0.1:$hostPort")

    @Volatile
    private var started = false

    fun start() {
        synchronized(lock) {
            if (started) return
            require(workspaceHostPath.exists()) { "Sandbox workspace does not exist: $workspaceHostPath" }
            runDocker(
                listOf(
                    "run",
                    "-d",
                    "--init",
                    "--name",
                    containerName,
                    "--device",
                    "/dev/fuse",
                    "--cap-add",
                    "SYS_ADMIN",
                    "--security-opt",
                    "apparmor:unconfined",
                    "-p",
                    "$hostPort:${config.containerPort}",
                    "--mount",
                    bindMount(workspaceHostPath, config.backingContainerPath),
                    "--workdir",
                    config.workspaceContainerPath,
                    "-e",
                    "ARCH_WORKSPACE_ROOT=${config.workspaceContainerPath}",
                    "-e",
                    "ARCH_BACKING_ROOT=${config.backingContainerPath}",
                    "-e",
                    "ARCH_SANDBOX_PORT=${config.containerPort}",
                    config.image
                )
            )
            waitForHealth()
            started = true
        }
    }

    fun exec(command: String, timeoutSeconds: Int): SandboxShellResult {
        require(command.isNotBlank()) { "Command cannot be blank." }
        require(timeoutSeconds > 0) { "timeoutSeconds must be greater than 0." }

        synchronized(lock) {
            check(started) { "Sandbox container has not been started." }
            val requestBody = ExecRequest(
                command = command,
                cwd = config.workspaceContainerPath,
                timeoutSeconds = timeoutSeconds,
                env = emptyMap(),
                policySnapshot = toContainerSnapshot(pathPolicyLogic.snapshot()),
                execId = UUID.randomUUID().toString()
            )
            return postJson("/exec", requestBody.toJson()).fromJson<ExecResponse>().toResult()
        }
    }

    override fun close() {
        synchronized(lock) {
            if (!started) return
            runCatching { runDocker(listOf("rm", "-f", containerName)) }
                .onFailure { exception ->
                    ErrorLog.log(
                        source = "sandbox_container_stop",
                        exception = exception,
                        context = mapOf("containerName" to containerName)
                    )
                }
            started = false
        }
    }

    private fun waitForHealth() {
        repeat(50) {
            runCatching {
                val request = HttpRequest.newBuilder(endpoint.resolve("/health"))
                    .timeout(Duration.ofSeconds(1))
                    .GET()
                    .build()
                client.send(request, HttpResponse.BodyHandlers.ofString())
            }.getOrNull()?.let { response ->
                if (response.statusCode() == 200) return
            }
            Thread.sleep(200)
        }
        throw IllegalStateException("Sandbox daemon did not become healthy at $endpoint.")
    }

    private fun postJson(path: String, jsonBody: String): String {
        val request = HttpRequest.newBuilder(endpoint.resolve(path))
            .timeout(Duration.ofSeconds(120))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException("Sandbox daemon request failed: ${response.statusCode()} ${response.body()}")
        }
        return response.body()
    }

    private fun bindMount(source: Path, target: String): String {
        return "type=bind,source=${source.toAbsolutePath()},target=$target"
    }

    private fun runDocker(args: List<String>): String {
        val result = runDockerAllowFailure(args)
        if (result.exitCode != 0) {
            throw IllegalStateException(result.stderr.ifBlank { result.stdout }.ifBlank { "Docker command failed." })
        }
        return result.stdout
    }

    private fun runDockerAllowFailure(args: List<String>): DockerCommandResult {
        return try {
            val process = ProcessBuilder(listOf(config.dockerCommand) + args).start()
            val stdout = process.inputStream.readAllBytes().decodeToString().trim()
            val stderr = process.errorStream.readAllBytes().decodeToString().trim()
            val exitCode = process.waitFor()
            DockerCommandResult(exitCode, stdout, stderr)
        } catch (exception: Exception) {
            ErrorLog.log(
                source = "sandbox_docker_command",
                exception = exception,
                context = mapOf("command" to ((listOf(config.dockerCommand) + args).joinToString(" ")))
            )
            throw exception
        }
    }

    private fun toContainerSnapshot(snapshot: PathPolicySnapshot): PathPolicySnapshot {
        val translatedPolicies = snapshot.policies.mapNotNull { policy ->
            translatePath(policy.pattern)?.let { translatedPattern ->
                PathPolicy(
                    pattern = translatedPattern,
                    accessTypes = policy.accessTypes.toMutableList(),
                    lifetime = policy.lifetime,
                    isAllowed = policy.isAllowed,
                    reason = policy.reason
                )
            }
        }
        val translatedDeniedPrefixes = snapshot.deniedPathPrefixes.mapNotNull(::translatePath)
        return PathPolicySnapshot(
            policies = translatedPolicies,
            deniedPathPrefixes = translatedDeniedPrefixes
        )
    }

    private fun translatePath(rawPath: String): String? {
        return runCatching { Path.of(rawPath).toAbsolutePath().normalize() }
            .getOrNull()
            ?.takeIf { it.startsWith(workspaceHostPath) }
            ?.let { hostPath ->
                val relativePath = workspaceHostPath.relativize(hostPath).toString().replace('\\', '/')
                val containerRoot = config.backingContainerPath.trimEnd('/')
                if (relativePath.isBlank()) containerRoot else "$containerRoot/$relativePath"
            }
    }
}

data class SandboxShellResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val blockedOperations: List<String> = emptyList()
)

@Serializable
private data class ExecRequest(
    val command: String,
    val cwd: String,
    val timeoutSeconds: Int,
    val env: Map<String, String>,
    val policySnapshot: PathPolicySnapshot,
    val execId: String
)

@Serializable
private data class ExecResponse(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val blockedOperations: List<String> = emptyList()
) {
    fun toResult(): SandboxShellResult {
        return SandboxShellResult(
            exitCode = exitCode,
            stdout = stdout,
            stderr = stderr,
            blockedOperations = blockedOperations
        )
    }
}

private data class DockerCommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
)

private fun String.toContainerToken(): String {
    val token = lowercase()
        .replace(Regex("[^a-z0-9_.-]+"), "-")
        .trim('-', '.', '_')
    return token.ifBlank { "agent" }
}
