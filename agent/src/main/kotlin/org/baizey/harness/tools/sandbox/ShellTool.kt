package org.baizey.harness.tools.sandbox

import dev.langchain4j.agent.tool.P
import dev.langchain4j.agent.tool.Tool
import org.baizey.runtime.sandbox.DockerSandboxService

class ShellTool(
    private val sandboxService: DockerSandboxService
) {
    @Tool(
        name = "shell",
        value = ["""Execute a shell command inside the long-lived Linux sandbox container.
Use Linux/POSIX commands relative to the mounted workspace.
The current effective path policy snapshot is sent alongside every execution for the sandbox runtime to consume.
Example: shell(command="ls -la", timeoutSeconds=30)"""]
    )
    fun shell(
        @P("Shell command to execute inside the sandbox container.")
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
