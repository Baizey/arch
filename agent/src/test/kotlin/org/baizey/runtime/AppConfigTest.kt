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
        assertEquals(false, config.sandbox.enabled)
        assertEquals("arch-agentsh:latest", config.sandbox.image)
    }

    @Test
    fun `config loads sandbox settings from dotenv`(@TempDir tempDir: Path) {
        tempDir.resolve(".env").writeText(
            """
            AGENT_CONSOLE_HOST=127.0.0.1
            AGENT_CONSOLE_PORT=8420
            OLLAMA_BASE_URL=http://localhost:11434
            AGENT_SANDBOX_ENABLED=yes
            AGENT_SANDBOX_IMAGE=arch-agentsh:test
            AGENT_SANDBOX_DOCKER_COMMAND=docker.exe
            AGENT_SANDBOX_API_KEY=test-sandbox-key
            AGENT_SANDBOX_HOST_ROOT=C:\Users
            AGENT_SANDBOX_CONTAINER_HOST_ROOT=/host
            AGENT_SANDBOX_STATE_VOLUME_PREFIX=arch-test
            AGENT_SANDBOX_PORT_START=19000
            AGENT_SANDBOX_PRIVILEGED=false
            """.trimIndent()
        )

        val config = AppConfigInstance.load(tempDir)

        assertEquals(true, config.sandbox.enabled)
        assertEquals("arch-agentsh:test", config.sandbox.image)
        assertEquals("docker.exe", config.sandbox.dockerCommand)
        assertEquals("test-sandbox-key", config.sandbox.apiKey)
        assertEquals(Path.of("C:\\Users"), config.sandbox.hostRoot)
        assertEquals("/host", config.sandbox.containerHostRoot)
        assertEquals(config.storage.homeDirectory.resolve("system/sandbox/policies"), config.sandbox.policiesDirectory)
        assertEquals(config.storage.homeDirectory.resolve("system/sandbox/keys"), config.sandbox.keysDirectory)
        assertEquals(config.storage.homeDirectory.resolve("system/logs/agentsh"), config.sandbox.logsDirectory)
        assertEquals("arch-test", config.sandbox.stateVolumePrefix)
        assertEquals(19000, config.sandbox.portStart)
        assertEquals(false, config.sandbox.privileged)
    }

    @Test
    fun `arch home is the system root when customized`(@TempDir tempDir: Path) {
        val archHome = tempDir.resolve("custom-arch-root")
        tempDir.resolve(".env").writeText(
            """
            AGENT_CONSOLE_HOST=127.0.0.1
            AGENT_CONSOLE_PORT=8420
            OLLAMA_BASE_URL=http://localhost:11434
            ARCH_HOME=$archHome
            """.trimIndent()
        )

        val config = AppConfigInstance.load(tempDir)

        assertEquals(archHome, config.storage.homeDirectory)
        assertEquals(archHome.resolve("system/sandbox/policies"), config.sandbox.policiesDirectory)
        assertEquals(archHome.resolve("system/sandbox/keys"), config.sandbox.keysDirectory)
        assertEquals(archHome.resolve("system/logs/agentsh"), config.sandbox.logsDirectory)
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
