package org.baizey.harness.tools.sandbox

import dev.langchain4j.agent.tool.P
import dev.langchain4j.agent.tool.Tool
import org.baizey.runtime.sandbox.DockerSandboxService

class ShellTool(
    sandboxService: DockerSandboxService
) {
    private val commandRunner = SandboxCommandRunner(sandboxService)

    @Tool(
        name = "execute_shell",
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
        return commandRunner.run(command, timeoutSeconds)
    }
}
