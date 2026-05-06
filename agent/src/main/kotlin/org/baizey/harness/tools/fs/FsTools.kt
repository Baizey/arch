package org.baizey.harness.tools.fs

import org.baizey.harness.HarnessContext

internal object FsTools {
    fun create(context: HarnessContext): List<Any> = listOf(
        InspectPathAccessTool(context.pathPolicyLogic),
        ListDirectoryTool(context.pathPolicyLogic),
        SearchFilesTool(context.pathPolicyLogic),
        ReadFileTool(context.pathPolicyLogic),
        EditFileTool(context.pathPolicyLogic),
        WriteFileTool(context.pathPolicyLogic),
        MoveOrCopyPathTool(context.pathPolicyLogic),
        DeletePathTool(context.pathPolicyLogic)
    )
}
