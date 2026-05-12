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
            addAll(containerRuntimeRules())
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
            appendLine("description: ${yamlScalar("Generated from PathPolicyLogic. Unknown non-runtime paths default to deny.")}")
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

    private fun containerRuntimeRules(): List<FileRule> {
        return listOf(
            FileRule(
                name = "allow-container-runtime-executables",
                paths = listOf(
                    "/bin",
                    "/bin/**",
                    "/usr/bin",
                    "/usr/bin/**",
                    "/usr/local/bin",
                    "/usr/local/bin/**"
                ),
                operations = listOf("read", "open", "stat", "list", "readlink", "execute"),
                decision = "allow",
                message = "Allow sandbox commands to execute container runtime binaries."
            ),
            FileRule(
                name = "allow-container-runtime-libraries",
                paths = listOf(
                    "/etc",
                    "/etc/**",
                    "/lib",
                    "/lib/**",
                    "/lib64",
                    "/lib64/**",
                    "/usr/lib",
                    "/usr/lib/**",
                    "/usr/local/lib",
                    "/usr/local/lib/**"
                ),
                operations = listOf("read", "open", "stat", "list", "readlink"),
                decision = "allow",
                message = "Allow dynamically linked sandbox binaries to load their runtime files."
            ),
            FileRule(
                name = "allow-container-runtime-devices",
                paths = listOf(
                    "/dev/null",
                    "/dev/zero",
                    "/dev/random",
                    "/dev/urandom"
                ),
                operations = listOf("read", "open", "stat", "readlink", "write"),
                decision = "allow",
                message = "Allow standard device files required by common command-line tools."
            ),
            FileRule(
                name = "allow-container-runtime-proc",
                paths = listOf(
                    "/proc",
                    "/proc/**"
                ),
                operations = listOf("read", "open", "stat", "list", "readlink"),
                decision = "allow",
                message = "Allow read-only process metadata needed by common command-line tools."
            ),
            FileRule(
                name = "allow-container-runtime-temp",
                paths = listOf(
                    "/tmp",
                    "/tmp/**",
                    "/var/tmp",
                    "/var/tmp/**"
                ),
                operations = listOf("*"),
                decision = "allow",
                message = "Allow temporary files inside the disposable sandbox container."
            )
        )
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
