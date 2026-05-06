package org.baizey.harness.tools.git

import dev.langchain4j.agent.tool.P
import dev.langchain4j.agent.tool.Tool
import org.baizey.harness.policy.UserGitPolicyLogic
import org.baizey.harness.policy.git.GitAccessType.MODIFY_LOCAL
import org.baizey.runtime.ErrorLog

class GitAddTool(
    private val gitPolicyLogic: UserGitPolicyLogic
) {
    @Tool(
        name = "git_add",
        value = ["""Stage files for commit in a git repository.
Provide the starting path to locate the git repository root (scans upward).
Accepts individual file paths relative to the repository root, or '.' to stage all.
Example: git_add(path=".", files=["src/main/kotlin/org/baizey/harness/tools/git/GitTools.kt"])"""]
    )
    fun add(
        @P("Starting path to locate the git repository root.")
        path: String = ".",
        @P("File paths relative to repo root to stage. Use '.' or empty to stage all.")
        files: List<String>? = null,
        @P("Whether to also stage untracked files (add -A). Default false (add modifies only tracked).")
        addUntracked: Boolean? = null
    ): String {
        val gitDir = GitCommandSupport.findGitRoot(path) ?: return "Not a git repository (or any parent up to mount point): $path"
        gitPolicyLogic.evaluate(gitDir, MODIFY_LOCAL).toDenyReasonOrNull()?.let { return it }
        val args = mutableListOf("add")
        if (addUntracked == true) args.add("-A")
        else args.add("-u")

        if (!files.isNullOrEmpty()) {
            if (files.contains(".")) {
                // all files — pass nothing more, the add/-u/-A handles it
            } else {
                args.addAll(files.map { f -> "--" + f })
            }
        }

        return GitCommandSupport.execute(gitDir, *args.toTypedArray())
            .getOrElse { buildAddError("add", it.message ?: "unknown") }
    }

    private fun buildAddError(action: String, error: String): String {
        val msg = "Git $action failed: $error"
        ErrorLog.log(source = "git_add", exception = RuntimeException(msg))
        return msg
    }
}
