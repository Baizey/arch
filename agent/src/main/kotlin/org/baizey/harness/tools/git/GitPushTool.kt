package org.baizey.harness.tools.git

import dev.langchain4j.agent.tool.P
import dev.langchain4j.agent.tool.Tool
import org.baizey.harness.policy.UserGitPolicyLogic
import org.baizey.harness.policy.git.GitAccessType.PUSH
import org.baizey.runtime.ErrorLog

class GitPushTool(
    private val gitPolicyLogic: UserGitPolicyLogic
) {
    @Tool(
        name = "git_push",
        value = ["""Push commits to a remote repository.
Provide the starting path to locate the git repository root (scans upward).
Defaults to pushing the current branch to its configured upstream.
Use dryRun=true to simulate without actually pushing.
Example: git_push(path=".", remote="origin", branch="main")"""]
    )
    fun push(
        @P("Starting path to locate the git repository root.")
        path: String = ".",
        @P("Remote name to push to (e.g., 'origin'). Default is configured upstream remote.")
        remote: String? = null,
        @P("Branch name to push. Optional — pushes current branch if not specified.")
        branch: String? = null,
        @P("Whether to use dry-run mode (simulate without pushing). Default false.")
        dryRun: Boolean? = null
    ): String {
        val gitDir = GitCommandSupport.findGitRoot(path) ?: return "Not a git repository (or any parent up to mount point): $path"
        gitPolicyLogic.evaluate(gitDir, PUSH).toDenyReasonOrNull()?.let { return it }

        val args = mutableListOf("push")
        if (dryRun == true) args.add("--dry-run")
        remote?.let { args.add(it) }
        branch?.let { args.add(it) }

        return GitCommandSupport.execute(gitDir, *args.toTypedArray())
            .getOrElse { buildPushError("push", it.message ?: "unknown") }
    }

    private fun buildPushError(action: String, error: String): String {
        val msg = "Git $action failed: $error"
        ErrorLog.log(source = "git_push", exception = RuntimeException(msg))
        return msg
    }
}
