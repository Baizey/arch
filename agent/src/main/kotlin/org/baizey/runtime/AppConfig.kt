package org.baizey.runtime

import java.net.URI
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.readLines

internal object AppConfig {
    private val config by lazy { AgentConfig.load() }

    val console: ConsoleConfig
        get() = config.console

    val ollama: OllamaConfig
        get() = config.ollama

    val storage: StorageConfig
        get() = config.storage

    val webSearch: WebSearchConfig
        get() = config.webSearch
}

internal data class AgentConfig(
    val console: ConsoleConfig,
    val ollama: OllamaConfig,
    val storage: StorageConfig,
    val webSearch: WebSearchConfig
) {
    companion object {
        private val defaultHomeDirectory = Path(System.getProperty("user.home")).resolve(".arch")

        fun load(workingDirectory: Path = Path(System.getProperty("user.dir"))): AgentConfig {
            val values = DotEnvFile.loadFromWorkingDirectory(workingDirectory)

            return AgentConfig(
                console = ConsoleConfig(
                    host = values.requiredString("AGENT_CONSOLE_HOST"),
                    port = values.requiredInt("AGENT_CONSOLE_PORT")
                ),
                ollama = OllamaConfig(
                    baseUrl = values.requiredAbsoluteUri("OLLAMA_BASE_URL")
                ),
                storage = StorageConfig(
                    homeDirectory = values.optionalPath("ARCH_HOME") ?: defaultHomeDirectory
                ),
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
                )
            )
        }
    }
}

internal data class ConsoleConfig(
    val host: String,
    val port: Int
) {
    init {
        require(host.isNotBlank()) { "AGENT_CONSOLE_HOST cannot be blank." }
        require(port in 1..65535) { "AGENT_CONSOLE_PORT must be between 1 and 65535." }
    }
}

internal data class OllamaConfig(
    val baseUrl: String
) {
    init {
        require(baseUrl.isNotBlank()) { "OLLAMA_BASE_URL cannot be blank." }
        require(URI.create(baseUrl).scheme != null) { "OLLAMA_BASE_URL must be an absolute URI." }
    }
}

internal data class StorageConfig(
    val homeDirectory: Path
)

internal data class WebSearchConfig(
    val bing: BingSearchConfig,
    val brave: BraveSearchConfig,
    val google: GoogleSearchConfig
)

internal data class BingSearchConfig(
    val apiKey: String?
)

internal data class BraveSearchConfig(
    val apiKey: String?
)

internal data class GoogleSearchConfig(
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

    fun requiredInt(name: String): Int {
        val rawValue = requiredString(name)
        return rawValue.toIntOrNull()
            ?: throw IllegalArgumentException("$name must be an integer.")
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
}

private fun String?.normalize(): String? = this?.trim()?.ifBlank { null }
