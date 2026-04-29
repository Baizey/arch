package org.baizey.runtime

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

class AppConfigTest {
    @Test
    fun `dotenv loader ignores comments and unwraps quoted values`(@TempDir tempDir: Path) {
        val envFile = tempDir.resolve(".env")
        envFile.writeText(
            """
            # comment
            AGENT_CONSOLE_HOST=127.0.0.1
            OLLAMA_BASE_URL="http://localhost:11434"
            export BRAVE_SEARCH_API_KEY='secret-value'
            INVALID_LINE
            """.trimIndent()
        )

        val values = DotEnvFile.load(envFile)

        assertEquals("127.0.0.1", values.requiredString("AGENT_CONSOLE_HOST"))
        assertEquals("http://localhost:11434", values.requiredAbsoluteUri("OLLAMA_BASE_URL"))
        assertEquals("secret-value", values.optionalString("BRAVE_SEARCH_API_KEY"))
        assertNull(values.optionalString("INVALID_LINE"))
    }

    @Test
    fun `config loads only from dotenv with nullable optional fields`(@TempDir tempDir: Path) {
        tempDir.resolve(".env").writeText(
            """
            AGENT_CONSOLE_HOST=127.0.0.1
            AGENT_CONSOLE_PORT=8420
            OLLAMA_BASE_URL=http://localhost:11434
            OPENAI_BASE_URL=https://api.openai.com/v1
            OPENAI_API_KEY=test-key
            ARCH_HOME=
            BING_SEARCH_API_KEY=
            BRAVE_SEARCH_API_KEY=
            GOOGLE_CUSTOM_SEARCH_API_KEY=
            GOOGLE_CUSTOM_SEARCH_ENGINE_ID=
            """.trimIndent()
        )

        val config = AppConfigInstance.load(tempDir)

        assertEquals("127.0.0.1", config.console.host)
        assertEquals(8420, config.console.port)
        assertEquals("http://localhost:11434", config.providers.ollama.baseUrl)
        assertEquals("https://api.openai.com/v1", config.providers.openai.baseUrl)
        assertEquals("test-key", config.providers.openai.apiKey)
        assertNull(config.webSearch.bing.apiKey)
        assertNull(config.webSearch.brave.apiKey)
        assertNull(config.webSearch.google.apiKey)
        assertNull(config.webSearch.google.searchEngineId)
    }

    @Test
    fun `config finds dotenv in parent directory when started from module directory`(@TempDir tempDir: Path) {
        tempDir.resolve(".env").writeText(
            """
            AGENT_CONSOLE_HOST=127.0.0.1
            AGENT_CONSOLE_PORT=8420
            OLLAMA_BASE_URL=http://localhost:11434
            """.trimIndent()
        )
        val moduleDir = java.nio.file.Files.createDirectories(tempDir.resolve("agent"))

        val config = AppConfigInstance.load(moduleDir)

        assertEquals("127.0.0.1", config.console.host)
        assertEquals(8420, config.console.port)
        assertEquals("http://localhost:11434", config.providers.ollama.baseUrl)
    }

    @Test
    fun `config rejects missing required values`(@TempDir tempDir: Path) {
        tempDir.resolve(".env").writeText(
            """
            AGENT_CONSOLE_HOST=127.0.0.1
            OLLAMA_BASE_URL=http://localhost:11434
            """.trimIndent()
        )

        assertThrows(IllegalArgumentException::class.java) {
            AppConfigInstance.load(tempDir)
        }
    }

    @Test
    fun `config rejects invalid port values`(@TempDir tempDir: Path) {
        tempDir.resolve(".env").writeText(
            """
            AGENT_CONSOLE_HOST=127.0.0.1
            AGENT_CONSOLE_PORT=not-a-number
            OLLAMA_BASE_URL=http://localhost:11434
            """.trimIndent()
        )

        assertThrows(IllegalArgumentException::class.java) {
            AppConfigInstance.load(tempDir)
        }
    }

    @Test
    fun `config rejects missing dotenv after searching parent directories`(@TempDir tempDir: Path) {
        val moduleDir = java.nio.file.Files.createDirectories(tempDir.resolve("agent"))

        assertThrows(IllegalArgumentException::class.java) {
            AppConfigInstance.load(moduleDir)
        }
    }
}
