package org.baizey.harness.tools.git

import dev.langchain4j.agent.tool.P
import dev.langchain4j.agent.tool.Tool
import org.baizey.harness.policy.GitPolicyLogic
import org.baizey.harness.policy.git.GitAccessType.READ
import org.baizey.runtime.ErrorLog

class GitLogTool(
    private val gitPolicyLogic: GitPolicyLogic
) {
    @Tool(
        name = "git_log",
        value = ["""Show commit history for a repository.
Provide the starting path to locate the git repository root (scans upward).
Controls how much history to show and in what format. Default shows recent commits with full details.
Example: git_log(path=".", maxCount=50, oneline=true)"""]
    )
    fun log(
        @P("Starting path to locate the git repository root.")
        path: String = ".",
        @P("Maximum number of commits to show. Default 20.")
        maxCount: Int? = null,
        @P("Show only commits touching these paths. Optional filter by path/file.")
        paths: List<String>? = null,
        @P("Use oneline format (one line per commit). Default false.")
        oneline: Boolean? = null,
        @P("Branch or ref to start from. Defaults to current branch HEAD.")
        branch: String? = null
    ): String {
        val gitDir = GitCommandSupport.findGitRoot(path) ?: return "Not a git repository (or any parent up to mount point): $path"
        gitPolicyLogic.evaluate(gitDir, READ).toDenyReasonOrNull()?.let { return it }

        val args = mutableListOf("log")
        if (oneline == true) {
            args.add("--oneline")
            args.add("--format=%h %s")
        } else {
            args.add("--format=%H%n%an <%ae>%n%ad%n%s%n%b%n---COMMIT_SEP---")
            args.add("--date=short")
        }
        maxCount?.let { args.add("-n"); args.add(it.toString()) }
        branch?.let { args.add(it) }
        paths?.let { args.addAll(it.map { p -> "--" + p }) }

        return GitCommandSupport.execute(gitDir, *args.toTypedArray())
            .getOrElse { buildLogError("log", it.message ?: "unknown") }
    }

    private fun buildLogError(action: String, error: String): String {
        val msg = "Git $action failed: $error"
        ErrorLog.log(source = "git_log", exception = RuntimeException(msg))
        return msg
    }
}
