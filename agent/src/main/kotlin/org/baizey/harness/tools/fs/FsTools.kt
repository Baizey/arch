package org.baizey.harness.tools.fs

import org.baizey.harness.HarnessContext

internal object FsTools {
    fun create(context: HarnessContext): List<Any> = listOf(
        InspectPathAccessTool(context.userPathPolicyLogic),
        ListDirectoryTool(context.userPathPolicyLogic),
        SearchFilesTool(context.userPathPolicyLogic),
        ReadFileTool(context.userPathPolicyLogic),
        EditFileTool(context.userPathPolicyLogic),
        WriteFileTool(context.userPathPolicyLogic),
        MoveOrCopyPathTool(context.userPathPolicyLogic),
        DeletePathTool(context.userPathPolicyLogic)
    )
}
