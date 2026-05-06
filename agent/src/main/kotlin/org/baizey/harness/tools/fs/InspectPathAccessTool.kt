package org.baizey.harness.tools.fs

import dev.langchain4j.agent.tool.P
import dev.langchain4j.agent.tool.Tool
import org.baizey.harness.policy.UserPathPolicyLogic

class InspectPathAccessTool(
    private val userPathPolicyLogic: UserPathPolicyLogic
) {
    @Tool(
        name = "inspect_path_access",
        value = ["""Inspect current filesystem access for one path without prompting the user.
Use this before acting on a path when you need to know whether access is already allowed, explicitly denied, or would require asking permission.
Example: inspect_path_access(path="C:/repo/src/Main.kt")"""]
    )
    fun inspectPathAccess(
        @P("Absolute or relative path to inspect.")
        path: String
    ): String {
        val normalizedPath = try {
            FsToolSupport.normalizePath(path)
        } catch (_: Exception) {
            return "Path is invalid: $path"
        }

        val inspection = userPathPolicyLogic.inspectPath(normalizedPath.toString())
        return buildString {
            appendLine("Path: ${inspection.path}")
            appendLine("Policy semantics:")
            appendLine("- Closest ancestor path match wins.")
            appendLine("- Paths not listed in an explicit policy are not pre-approved. Ask permission before using them.")
            appendLine("Access:")
            inspection.decisions.forEach { decision ->
                append("- ${decision.accessType.name}: ${decision.decision.name}")
                decision.pattern?.let { append(" (pattern: $it)") }
                appendLine()
                appendLine("  Reason: ${decision.reason}")
            }
        }.trimEnd()
    }
}
