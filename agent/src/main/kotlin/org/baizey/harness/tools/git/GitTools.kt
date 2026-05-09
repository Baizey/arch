package org.baizey.harness.tools.git

import org.baizey.harness.policy.GitPolicyLogic

internal object GitTools {
    fun create(context: GitPolicyLogic): List<Any> = listOf(
        GitStatusTool(context),
        GitAddTool(context),
        GitCommitTool(context),
        GitPushTool(context),
        GitPullTool(context),
        GitFetchTool(context),
        GitDiffTool(context),
        GitLogTool(context),
        GitCheckoutTool(context)
    )
}
