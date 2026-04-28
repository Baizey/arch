package org.baizey.harness.tools.git

import dev.langchain4j.agent.tool.P
import dev.langchain4j.agent.tool.Tool
import org.baizey.harness.policy.GitPolicyLogic
import org.baizey.harness.policy.git.GitAccessType.MODIFY_LOCAL
import org.baizey.runtime.ErrorLog

class GitCommitTool(
    private val gitPolicyLogic: GitPolicyLogic
) {
    @Tool(
        name = "git_commit",
        value = ["""Create a new commit in the repository.
Provide the starting path to locate the git repository root (scans upward).
Requires a message for the commit. Author info is optional.
Example: git_commit(path=".", message="fix: correct parsing logic")"""]
    )
    fun commit(
        @P("Starting path to locate the git repository root.")
        path: String = ".",
        @P("Commit message.")
        message: String,
        @P("Author name for the commit. Optional.")
        authorName: String? = null,
        @P("Author email for the commit. Optional.")
        authorEmail: String? = null
    ): String {
        val gitDir = GitCommandSupport.findGitRoot(path) ?: return "Not a git repository (or any parent up to mount point): $path"
        gitPolicyLogic.evaluate(gitDir, MODIFY_LOCAL).toDenyReasonOrNull()?.let { return it }
        if (message.isBlank()) return "Message cannot be blank."
        if (authorName != null && authorEmail.isNullOrBlank()) {
            return "Author email is required when authorName is provided."
        }

        val args = mutableListOf("commit", "-m", message)
        authorName?.let { args.addAll(listOf("--author", "$it <$authorEmail>")) }

        return GitCommandSupport.execute(gitDir, *args.toTypedArray())
            .getOrElse { buildCommitError("commit", it.message ?: "unknown") }
    }

    private fun buildCommitError(action: String, error: String): String {
        val msg = "Git $action failed: $error"
        ErrorLog.log(source = "git_commit", exception = RuntimeException(msg))
        return msg
    }
}
