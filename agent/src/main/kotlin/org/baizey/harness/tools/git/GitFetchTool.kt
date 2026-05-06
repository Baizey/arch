package org.baizey.harness.tools.git

import dev.langchain4j.agent.tool.P
import dev.langchain4j.agent.tool.Tool
import org.baizey.harness.policy.UserGitPolicyLogic
import org.baizey.harness.policy.git.GitAccessType.PULL
import org.baizey.runtime.ErrorLog

class GitFetchTool(
    private val gitPolicyLogic: UserGitPolicyLogic
) {
    @Tool(
        name = "git_fetch",
        value = ["""Fetch updates from a remote repository without merging them.
Provide the starting path to locate the git repository root (scans upward).
Use this when you want to inspect remote progress before applying changes.
Example: git_fetch(path=".", remote="origin", branch="main")"""]
    )
    fun fetch(
        @P("Starting path to locate the git repository root.")
        path: String = ".",
        @P("Remote name to fetch from (e.g., 'origin'). Default is configured upstream remote.")
        remote: String? = null,
        @P("Branch name to fetch. Optional — fetches all refs if not specified.")
        branch: String? = null
    ): String {
        val gitDir = GitCommandSupport.findGitRoot(path) ?: return "Not a git repository (or any parent up to mount point): $path"
        gitPolicyLogic.evaluate(gitDir, PULL).toDenyReasonOrNull()?.let { return it }

        val args = mutableListOf("fetch")
        remote?.let { args.add(it) }
        branch?.let { args.add(it) }

        return GitCommandSupport.execute(gitDir, *args.toTypedArray())
            .getOrElse { buildFetchError("fetch", it.message ?: "unknown") }
    }

    private fun buildFetchError(action: String, error: String): String {
        val msg = "Git $action failed: $error"
        ErrorLog.log(source = "git_fetch", exception = RuntimeException(msg))
        return msg
    }
}
