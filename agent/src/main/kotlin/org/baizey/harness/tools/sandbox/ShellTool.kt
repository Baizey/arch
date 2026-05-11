package org.baizey.harness.tools.sandbox

import dev.langchain4j.agent.tool.P
import dev.langchain4j.agent.tool.Tool
import org.baizey.runtime.sandbox.AgentShSandboxService
import kotlin.time.Duration.Companion.seconds

class ShellTool(
    private val sandboxService: AgentShSandboxService
) {
    @Tool(
        name = "shell",
        value = ["""Execute a shell command from a virtual sandbox.
If the sandbox blocks filesystem access, ask_path_permission for the path and retry the command.
Installed shell tools are:
    curl
    jq
    bash
    git
    ripgrep
    procps
    python3
    python3-pip
    python3-venv
    nodejs
    npm
    openjdk-25-jdk
    gradle
Example: shell(command="git status", timeoutSeconds=30)"""]
    )
    fun shell(
        @P("Shell command to execute inside the AgentSH sandbox.")
        command: String,
        @P("Command timeout in seconds. Must be greater than 0.")
        timeoutSeconds: Int? = null
    ): String {
        val timeout = (timeoutSeconds ?: 30).seconds
        if (command.isBlank()) {
            return "Command cannot be blank."
        }
        if (timeout.inWholeSeconds <= 0) {
            return "timeoutSeconds must be greater than 0"
        }

        val result = try {
            sandboxService.exec(command, timeout)
        } catch (exception: Exception) {
            return "Sandbox shell execution failed: ${exception.message ?: exception::class.simpleName}"
        }

        return buildString {
            appendLine("Command: $command")
            appendLine("Exit code: ${result.exitCode?.toString() ?: "(missing)"}")
            appendLine("Sandbox status: ${if (result.maybePolicyDenied) "policy-blocked-or-failed" else "completed"}")
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
            if (result.maybePolicyDenied) {
                append("If this command needs access to a path that is not already approved, call ask_path_permission(path=..., accessType=...) and then retry.")
            }
        }.trimEnd()
    }
}
