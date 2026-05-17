package org.baizey.harness.tools.sandbox

import org.baizey.runtime.sandbox.DockerSandboxService

internal object SandboxTools {
    fun create(sandboxService: DockerSandboxService): List<Any> = listOf(
        ShellTool(sandboxService),
        ExecuteCodeTool(sandboxService)
    )
}
