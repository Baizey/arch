package org.baizey.harness.tools.fs

import org.baizey.harness.policy.PathPolicyLogic

internal object FsTools {
    fun create(context: PathPolicyLogic): List<Any> = listOf(
        InspectPathAccessTool(context),
        ListDirectoryTool(context),
        SearchFilesTool(context),
        ReadFileTool(context),
        EditFileTool(context),
        WriteFileTool(context),
        MoveOrCopyPathTool(context),
        DeletePathTool(context)
    )
}
