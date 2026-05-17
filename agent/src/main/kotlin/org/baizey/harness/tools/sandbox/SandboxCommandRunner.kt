package org.baizey.harness.tools.sandbox

import org.baizey.runtime.sandbox.DockerSandboxService

internal class SandboxCommandRunner(
    private val sandboxService: DockerSandboxService
) {
    fun run(command: String, timeoutSeconds: Int?): String {
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
