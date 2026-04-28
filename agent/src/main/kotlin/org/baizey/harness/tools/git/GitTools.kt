package org.baizey.harness.tools.git

import org.baizey.harness.HarnessContext

internal object GitTools {
    fun create(context: HarnessContext): List<Any> = listOf(
        GitStatusTool(context.gitPolicyLogic),
        GitAddTool(context.gitPolicyLogic),
        GitCommitTool(context.gitPolicyLogic),
        GitPushTool(context.gitPolicyLogic),
        GitPullTool(context.gitPolicyLogic),
        GitFetchTool(context.gitPolicyLogic),
        GitDiffTool(context.gitPolicyLogic),
        GitLogTool(context.gitPolicyLogic),
        GitCheckoutTool(context.gitPolicyLogic)
    )
}
