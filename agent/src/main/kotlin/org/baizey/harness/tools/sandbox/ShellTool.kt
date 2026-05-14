package org.baizey.harness.tools.sandbox

import dev.langchain4j.agent.tool.P
import dev.langchain4j.agent.tool.Tool
import org.baizey.runtime.sandbox.DockerSandboxService

class ShellTool(
    private val sandboxService: DockerSandboxService
) {
    @Tool(
        name = "shell",
        value = ["""Execute a terminal command.
You are executing from a sandboxed ubuntu environment but if your agent is being run from windows use windows paths.
Example: cwd
Example: ls -la
Example: ls C:\\Repositories
"""]
    )
    fun shell(
        @P("Shell command to execute")
        command: String,
        @P("Command timeout in seconds. Must be greater than 0.")
        timeoutSeconds: Int? = null
    ): String {
        if (command.isBlank()) {
            return "Command cannot be blank."
        }
        val actualTimeoutSeconds = timeoutSeconds ?: 30
        if (actualTimeoutSeconds <= 0) {
            return "timeoutSeconds must be greater than 0"
        }

        val result = try {
            sandboxService.exec(command, actualTimeoutSeconds)
        } catch (exception: Exception) {
            return "Sandbox shell execution failed: ${exception.message ?: exception::class.simpleName}"
        }

        return buildString {
            appendLine("Command: $command")
            appendLine("Exit code: ${result.exitCode}")
            appendLine("Stdout:")
            appendLine(result.stdout.ifBlank { "(empty)" })
            appendLine("Stderr:")
            appendLine(result.stderr.ifBlank { "(empty)" })
            if (result.blockedOperations.isNotEmpty()) {
                appendLine("Blocked operations:")
                result.blockedOperations.forEach { blocked ->
                    appendLine("- $blocked")
                }
            }
        }
    }
}
