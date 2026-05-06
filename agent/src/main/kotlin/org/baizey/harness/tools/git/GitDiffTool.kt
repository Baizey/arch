package org.baizey.harness.tools.git

import dev.langchain4j.agent.tool.P
import dev.langchain4j.agent.tool.Tool
import org.baizey.harness.policy.UserGitPolicyLogic
import org.baizey.harness.policy.git.GitAccessType.READ
import org.baizey.runtime.ErrorLog

class GitDiffTool(
    private val gitPolicyLogic: UserGitPolicyLogic
) {
    @Tool(
        name = "git_diff",
        value = ["""Show changes between commits, branches, or working tree.
Provide the starting path to locate the git repository root (scans upward).
Without arguments, shows unstaged changes in working tree.
Provide commit1 and commit2 for diff between two commits.
Example: git_diff(path=".", cached=true)"""]
    )
    fun diff(
        @P("Starting path to locate the git repository root.")
        path: String = ".",
        @P("Diff HEAD against the index (cached changes). Default false.")
        cached: Boolean? = null,
        @P("First commit SHA for commit-vs-commit diff. Optional.")
        commit1: String? = null,
        @P("Second commit SHA for commit-vs-commit diff. Optional.")
        commit2: String? = null,
        @P("File paths to restrict the diff to. Optional — shows all changes if not specified.")
        files: List<String>? = null
    ): String {
        val gitDir = GitCommandSupport.findGitRoot(path) ?: return "Not a git repository (or any parent up to mount point): $path"
        gitPolicyLogic.evaluate(gitDir, READ).toDenyReasonOrNull()?.let { return it }

        val args = mutableListOf("diff")
        if (cached == true) args.add("--cached")
        commit1?.let { args.add(it) }
        commit2?.let { args.add(it) }
        files?.let { args.addAll(it.map { f -> "--" + f }) }

        val output = GitCommandSupport.execute(gitDir, *args.toTypedArray())
        return when {
            output.isSuccess -> {
                val body = output.getOrNull().orEmpty()
                if (body.isBlank()) "No differences found." else body
            }
            else -> buildDiffError("diff", output.exceptionOrNull()?.message ?: "unknown")
        }
    }

    private fun buildDiffError(action: String, error: String): String {
        val msg = "Git $action failed: $error"
        ErrorLog.log(source = "git_diff", exception = RuntimeException(msg))
        return msg
    }
}
