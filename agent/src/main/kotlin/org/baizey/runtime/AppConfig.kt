package org.baizey.runtime

import java.net.URI
import java.net.ServerSocket
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.readLines

object AppConfig {
    private val config by lazy { AppConfigInstance.load() }

    val console: ConsoleConfig
        get() = config.console

    val providers: ProviderConfig
        get() = config.providers

    val storage: StorageConfig
        get() = config.storage

    val webSearch: WebSearchConfig
        get() = config.webSearch

    val sandbox: SandboxConfig
        get() = config.sandbox
}

data class AppConfigInstance(
    val console: ConsoleConfig,
    val providers: ProviderConfig,
    val storage: StorageConfig,
    val webSearch: WebSearchConfig,
    val sandbox: SandboxConfig
) {
    companion object {
        fun load(workingDirectory: Path = Path(System.getProperty("user.dir"))): AppConfigInstance {
            val values = DotEnvFile.loadFromWorkingDirectory(workingDirectory)

            val storage = StorageConfig(
                homeDirectory = values.optionalPath("ARCH_HOME")
                    ?: Path(System.getProperty("user.home")).resolve(".arch")
            )

            return AppConfigInstance(
                console = ConsoleConfig(
                    host = values.requiredString("AGENT_CONSOLE_HOST"),
                    port = values.requiredInt("AGENT_CONSOLE_PORT")
                ),
                providers = ProviderConfig(
                    ollama = OllamaConfig(
                        baseUrl = values.optionalAbsoluteUri("OLLAMA_BASE_URL")
                    ),
                    openai = OpenAiConfig(
                        baseUrl = values.optionalAbsoluteUri("OPENAI_BASE_URL"),
                        apiKey = values.optionalString("OPENAI_API_KEY")
                    )
                ),
                storage = storage,
                webSearch = WebSearchConfig(
                    bing = BingSearchConfig(
                        apiKey = values.optionalString("BING_SEARCH_API_KEY")
                    ),
                    brave = BraveSearchConfig(
                        apiKey = values.optionalString("BRAVE_SEARCH_API_KEY")
                    ),
                    google = GoogleSearchConfig(
                        apiKey = values.optionalString("GOOGLE_SEARCH_API_KEY"),
                        searchEngineId = values.optionalString("GOOGLE_SEARCH_ENGINE_ID")
                    )
                ),
                sandbox = SandboxConfig(
                    dockerCommand = values.optionalString("AGENT_SANDBOX_DOCKER_COMMAND") ?: "docker",
                    image = values.optionalString("AGENT_SANDBOX_IMAGE") ?: "arch-sandbox:latest",
                    workingDirectoryHostPath = values.optionalPath("AGENT_SANDBOX_WORKING_DIRECTORY_HOST_PATH")
                        ?: values.optionalPath("AGENT_SANDBOX_WORKSPACE_HOST_PATH")
                        ?: Path(System.getProperty("user.dir")).toAbsolutePath().normalize(),
                    backingContainerPath = values.optionalString("AGENT_SANDBOX_BACKING_CONTAINER_PATH") ?: "/arch/backing",
                    workspaceContainerPath = values.optionalString("AGENT_SANDBOX_WORKSPACE_CONTAINER_PATH") ?: "/arch/workspace",
                    containerNamePrefix = values.optionalString("AGENT_SANDBOX_CONTAINER_NAME_PREFIX") ?: "arch-agent",
                    hostPortStart = values.optionalInt("AGENT_SANDBOX_PORT_START") ?: 18080,
                    containerPort = values.optionalInt("AGENT_SANDBOX_CONTAINER_PORT") ?: 18080
                )
            )
        }
    }
}

data class ConsoleConfig(
    val host: String,
    val port: Int
) {
    init {
        require(host.isNotBlank()) { "AGENT_CONSOLE_HOST cannot be blank." }
        require(port in 1..65535) { "AGENT_CONSOLE_PORT must be between 1 and 65535." }
    }
}

data class ProviderConfig(
    val ollama: OllamaConfig,
    val openai: OpenAiConfig
)

interface AgentProviderConfig

data class OpenAiConfig(
    val baseUrl: String?,
    val apiKey: String?
) : AgentProviderConfig

data class OllamaConfig(
    val baseUrl: String?
) : AgentProviderConfig

data class StorageConfig(
    val homeDirectory: Path
)

data class WebSearchConfig(
    val bing: BingSearchConfig,
    val brave: BraveSearchConfig,
    val google: GoogleSearchConfig
)

data class SandboxConfig(
    val dockerCommand: String,
    val image: String,
    val workingDirectoryHostPath: Path,
    val backingContainerPath: String,
    val workspaceContainerPath: String,
    val containerNamePrefix: String,
    val hostPortStart: Int,
    val containerPort: Int
) {
    init {
        require(dockerCommand.isNotBlank()) { "AGENT_SANDBOX_DOCKER_COMMAND cannot be blank." }
        require(image.isNotBlank()) { "AGENT_SANDBOX_IMAGE cannot be blank." }
        require(backingContainerPath.startsWith("/")) { "AGENT_SANDBOX_BACKING_CONTAINER_PATH must be absolute." }
        require(workspaceContainerPath.startsWith("/")) { "AGENT_SANDBOX_WORKSPACE_CONTAINER_PATH must be absolute." }
        require(containerNamePrefix.isNotBlank()) { "AGENT_SANDBOX_CONTAINER_NAME_PREFIX cannot be blank." }
        require(hostPortStart in 1..65535) { "AGENT_SANDBOX_PORT_START must be between 1 and 65535." }
        require(containerPort in 1..65535) { "AGENT_SANDBOX_CONTAINER_PORT must be between 1 and 65535." }
    }
}

internal fun findAvailablePort(start: Int): Int {
    for (port in start..65535) {
        if (isPortAvailable(port)) return port
    }
    throw IllegalStateException("No available port at or above $start.")
}

private fun isPortAvailable(port: Int): Boolean {
    return try {
        ServerSocket(port).use { true }
    } catch (_: Exception) {
        false
    }
}

data class BingSearchConfig(
    val apiKey: String?
)

data class BraveSearchConfig(
    val apiKey: String?
)

data class GoogleSearchConfig(
    val apiKey: String?,
    val searchEngineId: String?
)

internal object DotEnvFile {
    fun loadFromWorkingDirectory(workingDirectory: Path): DotEnvValues {
        val directory = if (workingDirectory.isDirectory()) workingDirectory else workingDirectory.parent
        val envPath = findInParents(directory ?: workingDirectory)
            ?: throw IllegalArgumentException("Missing .env file for working directory $workingDirectory")
        return load(envPath)
    }

    fun load(path: Path): DotEnvValues {
        require(path.exists()) { "Missing .env file at $path" }

        return DotEnvValues(
            path.readLines()
                .mapNotNull(::parseLine)
                .toMap(LinkedHashMap())
        )
    }

    fun parseLine(line: String): Pair<String, String>? {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return null

        val declaration = trimmed.removePrefix("export ").trim()
        val delimiterIndex = declaration.indexOf('=')
        if (delimiterIndex <= 0) return null

        val key = declaration.substring(0, delimiterIndex).trim()
        if (key.isEmpty()) return null

        val rawValue = declaration.substring(delimiterIndex + 1).trim()
        return key to rawValue.unquote()
    }

    private fun findInParents(startDirectory: Path): Path? {
        var current: Path? = startDirectory
        while (current != null) {
            val candidate = current.resolve(".env")
            if (candidate.exists()) {
                return candidate
            }
            current = current.parent
        }
        return null
    }

    private fun String.unquote(): String {
        return if (length >= 2 && first() == last() && (first() == '"' || first() == '\'')) {
            substring(1, lastIndex)
        } else {
            this
        }
    }
}

internal class DotEnvValues(
    private val values: Map<String, String>
) {
    fun requiredString(name: String): String {
        return optionalString(name)
            ?: throw IllegalArgumentException(".env must define $name.")
    }

    fun optionalString(name: String): String? = values[name].normalize()

    fun rejectFalseBoolean(name: String, message: String) {
        val rawValue = optionalString(name) ?: return
        when (rawValue.lowercase()) {
            "true", "1", "yes", "y", "on" -> return
            "false", "0", "no", "n", "off" -> throw IllegalArgumentException(message)
            else -> throw IllegalArgumentException("$name must be a boolean.")
        }
    }

    fun requiredInt(name: String): Int {
        val rawValue = requiredString(name)
        return rawValue.toIntOrNull()
            ?: throw IllegalArgumentException("$name must be an integer.")
    }

    fun optionalInt(name: String): Int? {
        val rawValue = optionalString(name) ?: return null
        return rawValue.toIntOrNull()
            ?: throw IllegalArgumentException("$name must be an integer.")
    }

    fun optionalBoolean(name: String): Boolean? {
        val rawValue = optionalString(name) ?: return null
        return when (rawValue.lowercase()) {
            "true", "1", "yes", "y", "on" -> true
            "false", "0", "no", "n", "off" -> false
            else -> throw IllegalArgumentException("$name must be a boolean.")
        }
    }

    fun optionalPath(name: String): Path? {
        return optionalString(name)?.let(Path::of)
    }

    fun requiredAbsoluteUri(name: String): String {
        val rawValue = requiredString(name)
        val uri = URI.create(rawValue)
        require(uri.scheme != null) { "$name must be an absolute URI." }
        return uri.toString()
    }

    fun optionalAbsoluteUri(name: String): String? {
        return optionalString(name)?.let { uri ->
            val uri = URI.create(uri)
            require(uri.scheme != null) { "$name must be an absolute URI." }
            uri.toString()
        }
    }
}

private fun String?.normalize(): String? = this?.trim()?.ifBlank { null }
