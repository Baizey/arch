package org.baizey.harness.policy

import org.baizey.harness.HarnessInteractionPort
import org.baizey.harness.PermissionRequest
import org.baizey.harness.policy.path.FsAccessType
import org.baizey.harness.policy.shared.PolicyLifetime
import org.baizey.harness.policy.shell.ShellPersistedPolicy
import org.baizey.harness.policy.shell.ShellPolicy
import org.baizey.harness.policy.shell.ShellPolicyResult
import org.baizey.runtime.AuditLog
import org.baizey.runtime.SystemPath
import org.baizey.utils.IO.fromJson
import org.baizey.utils.IO.toJson
import java.nio.file.Files
import kotlin.io.path.createParentDirectories
import kotlin.io.path.notExists

class ShellPolicyLogic(
    private val interactionPort: HarnessInteractionPort
) {
    private val activePolicies = mutableListOf<ShellPolicy>()

    fun evaluate(command: String, arguments: List<String>, workingDirectory: String?): ShellPolicyResult {
        val signature = formatCommandSignature(command, arguments)
        val subject = formatCommandSubject(signature, workingDirectory)

        val existingPolicy = activePolicies.firstOrNull { it.signature == signature }
        val policy = existingPolicy ?: createNewPolicy(signature, subject)
        return logAndReturn(
            ShellPolicyResult(
                signature = signature,
                subject = subject,
                isAllowed = policy.isAllowed,
                reason = policy.reason
            ),
            decisionSource = if (existingPolicy != null) AuditLog.DecisionSource.ACTIVE_POLICY_MATCH else AuditLog.DecisionSource.USER_PROMPT,
            lifetime = policy.lifetime.name
        )
    }

    fun reloadFromPersistence(): List<ShellPolicy> {
        val path = SystemPath.shellPolicyFile
        path.createParentDirectories()
        val current = activePolicies.filter { it.lifetime != PolicyLifetime.FOREVER }
        activePolicies.clear()
        if (path.notExists()) {
            activePolicies.addAll(current)
            return activePolicies.toList()
        }
        val stored = Files.readString(path).fromJson<ShellPersistedPolicy>()
        activePolicies.addAll(stored.policies)
        activePolicies.addAll(current)
        return activePolicies.toList()
    }

    fun clearPolicies() {
        activePolicies.clear()
    }

    fun formatCommandSignature(command: String, arguments: List<String>): String {
        return (listOf(command) + arguments).joinToString(" ") { token ->
            when {
                token.isEmpty() -> "\"\""
                safeShellToken.matches(token) -> token
                else -> "\"${token.replace("\\", "\\\\").replace("\"", "\\\"")}\""
            }
        }
    }

    private fun createNewPolicy(signature: String, subject: String): ShellPolicy {
        val decision = interactionPort.requestPermission(
            PermissionRequest(
                path = subject,
                accessType = FsAccessType.EXECUTE.name,
                scopeOptions = listOf(signature)
            )
        )
        val newPolicy = ShellPolicy(
            signature = signature,
            lifetime = decision.lifetime,
            isAllowed = decision.isAllowed,
            reason = decision.reason
        )
        if (newPolicy.lifetime != PolicyLifetime.ONCE) {
            activePolicies.add(newPolicy)
            if (newPolicy.lifetime == PolicyLifetime.FOREVER) {
                updatePersistence()
            }
        }
        return newPolicy
    }

    private fun formatCommandSubject(signature: String, workingDirectory: String?): String {
        return if (workingDirectory.isNullOrBlank()) signature else "$signature (cwd: $workingDirectory)"
    }

    private fun updatePersistence() {
        val path = SystemPath.shellPolicyFile
        path.createParentDirectories()

        val savedPolicies = activePolicies.filter { it.lifetime == PolicyLifetime.FOREVER }
        val json = ShellPersistedPolicy(savedPolicies).toJson()
        Files.writeString(path, json)
    }

    private fun logAndReturn(
        result: ShellPolicyResult,
        decisionSource: AuditLog.DecisionSource,
        lifetime: String = "SYSTEM"
    ): ShellPolicyResult {
        AuditLog.logPolicyDecision(
            policyType = AuditLog.PolicyType.SHELL,
            subject = result.subject,
            isAllowed = result.isAllowed,
            decisionSource = decisionSource,
            context = mapOf(
                "signature" to result.signature,
                "lifetime" to lifetime,
                "reason" to result.reason
            )
        )
        return result
    }

    private val safeShellToken = Regex("[A-Za-z0-9_./\\\\:-]+")
}
