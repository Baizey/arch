package org.baizey.harness.tools.git

import dev.langchain4j.agent.tool.P
import dev.langchain4j.agent.tool.Tool
import org.baizey.harness.policy.GitPolicyLogic
import org.baizey.harness.policy.git.GitAccessType.PULL
import org.baizey.runtime.ErrorLog

class GitPullTool(
    private val gitPolicyLogic: GitPolicyLogic
) {
    @Tool(
        name = "git_pull",
        value = ["""Pull changes from a remote repository into the current branch.
Provide the starting path to locate the git repository root (scans upward).
Defaults to pulling from configured upstream.
Example: git_pull(path=".", remote="origin", branch="main")"""]
    )
    fun pull(
        @P("Starting path to locate the git repository root.")
        path: String = ".",
        @P("Remote name to pull from (e.g., 'origin'). Default is configured upstream remote.")
        remote: String? = null,
        @P("Branch name to pull. Optional — pulls current branch if not specified.")
        branch: String? = null
    ): String {
        val gitDir = GitCommandSupport.findGitRoot(path) ?: return "Not a git repository (or any parent up to mount point): $path"
        gitPolicyLogic.evaluate(gitDir, PULL).toDenyReasonOrNull()?.let { return it }

        val args = mutableListOf("pull")
        remote?.let { args.add(it) }
        branch?.let { args.add(it) }

        return GitCommandSupport.execute(gitDir, *args.toTypedArray())
            .getOrElse { buildPullError("pull", it.message ?: "unknown") }
    }

    private fun buildPullError(action: String, error: String): String {
        val msg = "Git $action failed: $error"
        ErrorLog.log(source = "git_pull", exception = RuntimeException(msg))
        return msg
    }
}
