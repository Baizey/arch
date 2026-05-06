package org.baizey.harness.tools.git

import dev.langchain4j.agent.tool.P
import dev.langchain4j.agent.tool.Tool
import org.baizey.harness.policy.GitPolicyLogic
import org.baizey.harness.policy.git.GitAccessType.READ
import org.baizey.runtime.ErrorLog

class GitStatusTool(
    private val gitPolicyLogic: GitPolicyLogic
) {
    @Tool(
        name = "git_status",
        value = ["""Show the working tree status for a repository.
Provide the starting path to locate the git repository root (scans upward).
Show untracked files explicitly if requested. Short output mode gives compact one-line entries.
Default is normal format with full paths and staged/unstaged details.
Example: git_status(path=".")"""]
    )
    fun status(
        @P("Starting path to locate the git repository root.")
        path: String = ".",
        @P("Whether to show untracked files. Default false (normal mode hides them).")
        showUntracked: Boolean? = null,
        @P("Whether to use short output format. Default false.")
        shortFormat: Boolean? = null
    ): String {
        val gitDir = GitCommandSupport.findGitRoot(path) ?: return "Not a git repository (or any parent up to mount point): $path"
        gitPolicyLogic.evaluate(gitDir, READ).toDenyReasonOrNull()?.let { return it }
        val extraArgs = mutableListOf<String>()
        if (showUntracked == true) extraArgs.add("--untracked-files=normal")
        else extraArgs.add("--untracked-files=no")
        if (shortFormat == true) extraArgs.add("-s")

        return GitCommandSupport.execute(gitDir, "status", *extraArgs.toTypedArray())
            .getOrElse { buildStatusError("status", it.message ?: "unknown") }
    }

    private fun buildStatusError(action: String, error: String): String {
        val msg = "Git $action failed: $error"
        ErrorLog.log(source = "git_status", exception = RuntimeException(msg))
        return msg
    }
}
