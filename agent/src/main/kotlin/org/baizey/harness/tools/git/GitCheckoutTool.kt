package org.baizey.harness.tools.git

import dev.langchain4j.agent.tool.P
import dev.langchain4j.agent.tool.Tool
import org.baizey.harness.policy.UserGitPolicyLogicLogic
import org.baizey.harness.policy.git.GitAccessType.MODIFY_LOCAL
import org.baizey.runtime.ErrorLog

class GitCheckoutTool(
    private val gitPolicyLogic: UserGitPolicyLogicLogic
) {
    @Tool(
        name = "git_checkout",
        value = ["""Switch branches or restore working tree files in a git repository.
Provide the starting path to locate the git repository root (scans upward).
To create a new branch, use newBranch=true along with the branch name.
Example: git_checkout(path=".", branch="feature-branch")"""]
    )
    fun checkout(
        @P("Starting path to locate the git repository root.")
        path: String = ".",
        @P("Branch name to check out or create.")
        branch: String,
        @P("Create the branch if it does not exist. Default false.")
        newBranch: Boolean? = null
    ): String {
        val gitDir = GitCommandSupport.findGitRoot(path) ?: return "Not a git repository (or any parent up to mount point): $path"
        gitPolicyLogic.evaluate(gitDir, MODIFY_LOCAL).toDenyReasonOrNull()?.let { return it }

        if (branch.isBlank()) return "Branch name cannot be blank."

        val args = mutableListOf("checkout")
        if (newBranch == true) args.add("-b")
        args.add(branch)

        return GitCommandSupport.execute(gitDir, *args.toTypedArray())
            .getOrElse { buildCheckoutError("checkout", it.message ?: "unknown") }
    }

    private fun buildCheckoutError(action: String, error: String): String {
        val msg = "Git $action failed: $error"
        ErrorLog.log(source = "git_checkout", exception = RuntimeException(msg))
        return msg
    }
}
