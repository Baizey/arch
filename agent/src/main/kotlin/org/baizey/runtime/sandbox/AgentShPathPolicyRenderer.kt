package org.baizey.runtime.sandbox

import org.baizey.harness.policy.PathPolicyLogic
import org.baizey.harness.policy.path.FsAccessType
import org.baizey.harness.policy.path.PathPolicy
import org.baizey.runtime.SystemPath
import java.nio.file.Path

class AgentShPathPolicyRenderer(
    private val hostRoot: Path,
    private val containerHostRoot: String
) {
    fun render(policyName: String, pathPolicyLogic: PathPolicyLogic): String {
        val hostRoot = hostRoot.toAbsolutePath().normalize()
        val rules = buildList {
            systemProtectedRule(hostRoot)?.let { add(it) }
            addAll(
                pathPolicyLogic.activePathPolicies()
                    .sortedByDescending { it.pattern.length }
                    .mapNotNull { policy -> policy.toRule(hostRoot) }
            )
            add(
                FileRule(
                    name = "default-deny-files",
                    paths = listOf("**"),
                    operations = listOf("*"),
                    decision = "deny",
                    message = "No matching PathPolicyLogic policy exists for this filesystem operation."
                )
            )
        }

        return buildString {
            appendLine("version: 1")
            appendLine("name: ${yamlScalar(policyName)}")
            appendLine("description: ${yamlScalar("Generated from PathPolicyLogic. Unknown paths default to deny.")}")
            appendLine()
            appendLine("file_rules:")
            rules.forEach { appendFileRule(it) }
            appendLine()
            appendLine("command_rules:")
            appendLine("  - name: audit-all-commands")
            appendLine("    commands:")
            appendLine("      - ${yamlScalar("*")}")
            appendLine("    decision: audit")
            appendLine()
            appendLine("network_rules:")
            appendLine("  - name: audit-all-network")
            appendLine("    domains:")
            appendLine("      - ${yamlScalar("*")}")
            appendLine("    decision: audit")
            appendLine()
            appendLine("audit:")
            appendLine("  log_allowed: true")
            appendLine("  log_denied: true")
            appendLine("  log_approved: true")
            appendLine("  include_stdout: false")
            appendLine("  include_stderr: true")
            appendLine("  include_file_content: false")
        }.trimEnd()
    }

    private fun systemProtectedRule(hostRoot: Path): FileRule? {
        val containerPath = mapHostPath(SystemPath.disallowBotDir, hostRoot) ?: return null
        return FileRule(
            name = "deny-system-protected-directory",
            paths = pathPatterns(containerPath),
            operations = listOf("*"),
            decision = "deny",
            message = "SystemPath.disallowBotDir is protected by PathPolicyLogic."
        )
    }

    private fun PathPolicy.toRule(hostRoot: Path): FileRule? {
        val containerPath = mapHostPath(Path.of(pattern), hostRoot) ?: return null
        return FileRule(
            name = "path-policy-${Integer.toUnsignedString(pattern.hashCode(), 36)}",
            paths = pathPatterns(containerPath),
            operations = accessTypes.flatMap { it.agentShOperations() }.distinct(),
            decision = if (isAllowed) "allow" else "deny",
            message = reason.ifBlank { null }
        )
    }

    private fun mapHostPath(path: Path, hostRoot: Path): String? {
        val cleanPath = path.toAbsolutePath().normalize()
        if (!cleanPath.startsWith(hostRoot)) return null
        val relativePath = hostRoot.relativize(cleanPath).toString().replace('\\', '/')
        val root = containerHostRoot.trimEnd('/')
        return if (relativePath.isBlank()) {
            root.ifBlank { "/" }
        } else {
            "$root/$relativePath"
        }
    }

    private fun pathPatterns(containerPath: String): List<String> {
        val normalized = containerPath.replace('\\', '/').trimEnd('/')
        return if (normalized == "/") {
            listOf("/", "/**")
        } else {
            listOf(normalized, "$normalized/**")
        }
    }

    private fun FsAccessType.agentShOperations(): List<String> {
        return when (this) {
            FsAccessType.READ -> listOf("read", "open", "stat", "list", "readlink")
            FsAccessType.WRITE,
            FsAccessType.EDIT -> listOf("write", "create", "mkdir", "chmod", "rename")
            FsAccessType.DELETE -> listOf("delete", "rmdir")
            FsAccessType.EXECUTE -> listOf("execute")
        }
    }

    private fun StringBuilder.appendFileRule(rule: FileRule) {
        appendLine("  - name: ${yamlScalar(rule.name)}")
        rule.message?.let { appendLine("    message: ${yamlScalar(it)}") }
        appendLine("    paths:")
        rule.paths.forEach { appendLine("      - ${yamlScalar(it)}") }
        appendLine("    operations:")
        rule.operations.forEach { appendLine("      - ${yamlScalar(it)}") }
        appendLine("    decision: ${rule.decision}")
    }

    private fun yamlScalar(value: String): String {
        return "\"" + value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"") + "\""
    }

    private data class FileRule(
        val name: String,
        val paths: List<String>,
        val operations: List<String>,
        val decision: String,
        val message: String? = null
    )
}
