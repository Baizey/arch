package org.baizey.harness.tools.sandbox

import dev.langchain4j.agent.tool.P
import dev.langchain4j.agent.tool.Tool
import org.baizey.runtime.SystemPath
import org.baizey.runtime.sandbox.DockerSandboxService
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists

class ExecuteCodeTool(
    sandboxService: DockerSandboxService
) {
    private val commandRunner = SandboxCommandRunner(sandboxService)

    @Tool(
        name = "execute_code",
        value = ["""Execute code and get the output.
"""]
    )
    fun executeCode(
        @P("Code to write to the temporary script file")
        code: String,
        @P("Script language. Supported values: 'js', 'python'")
        language: String,
        @P("Command timeout in seconds. Must be greater than 0.")
        timeoutSeconds: Int? = null
    ): String {
        if (code.isBlank()) return "Code cannot be blank."
        val scriptLanguage = ScriptLanguage.from(language) ?: return "language must be one of: js, python"

        val scriptPath = createTempScript(scriptLanguage, code)
        val command = "${scriptLanguage.command} ${shellQuote(scriptPath.toAbsolutePath().normalize().toString())}"
        return try {
            commandRunner.run(command, timeoutSeconds)
        } finally {
            scriptPath.deleteIfExists()
        }
    }

    private fun createTempScript(language: ScriptLanguage, code: String): Path {
        SystemPath.botTmpDir.createDirectories()
        val fileName = "sandbox-${UUID.randomUUID()}.${language.extension}"
        val scriptPath = SystemPath.botTmpDir.resolve(fileName)
        Files.writeString(scriptPath, code)
        return scriptPath
    }
}

internal enum class ScriptLanguage(
    val command: String,
    val extension: String
) {
    JS("node", "js"),
    PYTHON("python3", "py");

    companion object {
        fun from(raw: String): ScriptLanguage? {
            return when (raw.trim().lowercase()) {
                "js" -> JS
                "python" -> PYTHON
                else -> null
            }
        }
    }
}

internal fun shellQuote(value: String): String {
    return "'${value.replace("'", "'\"'\"'")}'"
}
