package org.baizey.harness.tools.fs

import dev.langchain4j.agent.tool.P
import dev.langchain4j.agent.tool.Tool
import org.baizey.harness.policy.PathPolicyLogic
import org.baizey.harness.policy.path.FsAccessType
import org.baizey.harness.policy.path.PathAccessDecision

class AskPathPermissionTool(
    private val pathPolicyLogic: PathPolicyLogic,
    private val onPolicyChanged: () -> Unit = {}
) {
    @Tool(
        name = "ask_path_permission",
        value = ["""Request filesystem permission for one path and one access type.
Use this when a path is not already approved and you need the user to allow or deny access.
If the path is already allowed or denied, this returns the current decision without prompting.
Example: ask_path_permission(path="C:/repo/src/Main.kt", accessType="READ")"""]
    )
    fun askPathPermission(
        @P("Absolute or relative path to request permission for.")
        path: String,
        @P("Filesystem access type to request: READ, WRITE, EDIT, DELETE, or EXECUTE.")
        accessType: String
    ): String {
        val normalizedPath = try {
            FsToolSupport.normalizePath(path)
        } catch (_: Exception) {
            return "Path is invalid: $path"
        }
        val parsedAccessType = parseAccessType(accessType) ?: return invalidAccessType(accessType)
        val inspection = pathPolicyLogic.inspectPath(normalizedPath.toString())
        val decision = inspection.decisions.firstOrNull { it.accessType == parsedAccessType }
            ?: return "No policy inspection result was available for ${parsedAccessType.name}."

        return when (decision.decision) {
            PathAccessDecision.ALLOW -> buildString {
                appendLine("Path: ${inspection.path}")
                appendLine("Access type: ${parsedAccessType.name}")
                appendLine("Decision: ALLOW")
                decision.pattern?.let { appendLine("Matched policy: $it") }
                append("Reason: ${decision.reason}")
            }

            PathAccessDecision.DENY -> buildString {
                appendLine("Path: ${inspection.path}")
                appendLine("Access type: ${parsedAccessType.name}")
                appendLine("Decision: DENY")
                decision.pattern?.let { appendLine("Matched policy: $it") }
                append("Reason: ${decision.reason}")
            }

            PathAccessDecision.ASK_PERMISSION -> {
                val result = pathPolicyLogic.evaluate(inspection.path, parsedAccessType)
                val refreshMessage = try {
                    onPolicyChanged()
                    "Sandbox policy refreshed."
                } catch (exception: Exception) {
                    "Sandbox policy refresh failed: ${exception.message ?: exception::class.simpleName}"
                }
                buildString {
                    appendLine("Path: ${result.path}")
                    appendLine("Access type: ${parsedAccessType.name}")
                    appendLine("Decision: ${if (result.isAllowed) "ALLOW" else "DENY"}")
                    appendLine("Matched policy: ${result.pattern}")
                    appendLine("Lifetime: ${result.lifetime.name}")
                    appendLine("Reason: ${result.reason.ifBlank { "(none provided)" }}")
                    append(refreshMessage)
                }
            }
        }
    }

    private fun parseAccessType(raw: String): FsAccessType? {
        return FsAccessType.entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }
    }

    private fun invalidAccessType(raw: String): String {
        return "Unknown accessType '$raw'. Use one of: ${FsAccessType.entries.joinToString(", ") { it.name }}"
    }
}
