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
import java.nio.file.FileSystems
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
    private val workingDirectoryHostPath = config.workingDirectoryHostPath.toAbsolutePath().normalize()
    private val hostRoots = discoverHostRoots(workingDirectoryHostPath, config.backingContainerPath)
    private val containerPathMappings = buildContainerPathMappings(hostRoots, config)
    private val workingDirectoryContainerPath = translatePath(workingDirectoryHostPath, config.workspaceContainerPath)
        ?: error("Unable to translate working directory into sandbox path: $workingDirectoryHostPath")
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
            require(workingDirectoryHostPath.exists()) { "Sandbox working directory does not exist: $workingDirectoryHostPath" }
            runDocker(
                buildList {
                    addAll(
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
                    "--workdir",
                    config.workspaceContainerPath,
                    "-e",
                    "ARCH_WORKSPACE_ROOT=${config.workspaceContainerPath}",
                    "-e",
                    "ARCH_BACKING_ROOT=${config.backingContainerPath}",
                    "-e",
                    "ARCH_SANDBOX_PORT=${config.containerPort}"
                        )
                    )
                    hostRoots.forEach { mount ->
                        add("--mount")
                        add(bindMount(mount.hostPath, mount.containerPath))
                    }
                    add(config.image)
                }
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
                command = rewriteCommandHostPaths(command),
                cwd = workingDirectoryContainerPath,
                timeoutSeconds = timeoutSeconds,
                env = emptyMap(),
                policySnapshot = toContainerSnapshot(pathPolicyLogic.snapshot()),
                execId = UUID.randomUUID().toString()
            )
            return postJson("/exec", requestBody.toJson()).fromJson<ExecResponse>().toResult(containerPathMappings)
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
            translatePath(policy.pattern, config.backingContainerPath)?.let { translatedPattern ->
                PathPolicy(
                    pattern = translatedPattern,
                    accessTypes = policy.accessTypes.toMutableList(),
                    lifetime = policy.lifetime,
                    isAllowed = policy.isAllowed,
                    reason = policy.reason
                )
            }
        }
        val translatedDeniedPrefixes = snapshot.deniedPathPrefixes.mapNotNull { rawPath ->
            translatePath(rawPath, config.backingContainerPath)
        }
        return PathPolicySnapshot(
            policies = translatedPolicies,
            deniedPathPrefixes = translatedDeniedPrefixes
        )
    }

    private fun translatePath(rawPath: String, containerBasePath: String): String? {
        return runCatching { Path.of(rawPath).toAbsolutePath().normalize() }
            .getOrNull()
            ?.let { hostPath -> translatePath(hostPath, containerBasePath) }
    }

    private fun translatePath(hostPath: Path, containerBasePath: String): String? {
        val mount = hostRoots
            .filter { hostPath.startsWith(it.hostPath) }
            .maxByOrNull { it.hostPath.nameCount }
            ?: return null
        val relativePath = mount.hostPath.relativize(hostPath).toString().replace('\\', '/')
        val mountRelativePath = mount.containerPath.removePrefix(config.backingContainerPath).trim('/').takeIf { it.isNotBlank() }
        val containerRoot = buildString {
            append(containerBasePath.trimEnd('/'))
            if (mountRelativePath != null) {
                append('/')
                append(mountRelativePath)
            }
        }
        return if (relativePath.isBlank()) containerRoot else "$containerRoot/$relativePath"
    }

    private fun rewriteCommandHostPaths(command: String): String {
        if (!isWindowsHost()) return command
        return WINDOWS_ABSOLUTE_PATH_REGEX.replace(command) { match ->
            translatePath(match.value, config.workspaceContainerPath) ?: match.value
        }
    }

    private fun isWindowsHost(): Boolean {
        return FileSystems.getDefault().rootDirectories.any { root ->
            root.toString().contains(':')
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

private data class HostRootMount(
    val hostPath: Path,
    val containerPath: String
)

private data class ContainerPathMapping(
    val containerPrefix: String,
    val hostRoot: Path
)

private fun discoverHostRoots(workingDirectoryHostPath: Path, backingContainerPath: String): List<HostRootMount> {
    return FileSystems.getDefault().rootDirectories.map { root ->
        val hostRoot = root.toAbsolutePath().normalize()
        val token = root.toString()
            .trimEnd('\\', '/')
            .replace(':', '_')
            .replace(Regex("[^A-Za-z0-9._-]+"), "-")
            .trim('-', '.', '_')
            .ifBlank { "root" }
            .lowercase()
        val containerPath = if (hostRoot.root == hostRoot && hostRoot.toString() == "/") {
            backingContainerPath
        } else {
            "${backingContainerPath.trimEnd('/')}/$token"
        }
        HostRootMount(hostRoot, containerPath)
    }.sortedByDescending { mount ->
        if (workingDirectoryHostPath.startsWith(mount.hostPath)) 1 else 0
    }
}

private fun buildContainerPathMappings(hostRoots: List<HostRootMount>, config: SandboxConfig): List<ContainerPathMapping> {
    return hostRoots.flatMap { mount ->
        val mountRelativePath = mount.containerPath.removePrefix(config.backingContainerPath).trim('/').takeIf { it.isNotBlank() }
        val workspacePrefix = buildString {
            append(config.workspaceContainerPath.trimEnd('/'))
            if (mountRelativePath != null) {
                append('/')
                append(mountRelativePath)
            }
        }
        listOf(
            ContainerPathMapping(mount.containerPath, mount.hostPath),
            ContainerPathMapping(workspacePrefix, mount.hostPath)
        )
    }.sortedByDescending { it.containerPrefix.length }
}

private fun String.toContainerToken(): String {
    val token = lowercase()
        .replace(Regex("[^a-z0-9_.-]+"), "-")
        .trim('-', '.', '_')
    return token.ifBlank { "agent" }
}

private val WINDOWS_ABSOLUTE_PATH_REGEX = Regex("""(?i)[a-z]:\\(?:[^<>:"|?*\r\n]+\\?)*""")

private fun ExecResponse.toResult(containerPathMappings: List<ContainerPathMapping>): SandboxShellResult {
    return SandboxShellResult(
        exitCode = exitCode,
        stdout = stdout.translateContainerPaths(containerPathMappings),
        stderr = stderr.translateContainerPaths(containerPathMappings),
        blockedOperations = blockedOperations.map { blocked ->
            blocked.translateContainerPaths(containerPathMappings)
        }
    )
}

private fun String.translateContainerPaths(containerPathMappings: List<ContainerPathMapping>): String {
    var result = this
    for (mapping in containerPathMappings) {
        val containerPrefix = mapping.containerPrefix.trimEnd('/')
        val hostRoot = mapping.hostRoot.toString().trimEnd('\\', '/')
        result = result.replace(containerPrefix, hostRoot)
    }
    return result
}
