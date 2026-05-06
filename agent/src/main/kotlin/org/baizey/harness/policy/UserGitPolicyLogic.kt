package org.baizey.harness.policy

import org.baizey.harness.HarnessInteractionPort
import org.baizey.harness.PermissionRequest
import org.baizey.harness.policy.git.GitAccessType
import org.baizey.harness.policy.git.GitPersistedPolicy
import org.baizey.harness.policy.git.GitPolicy
import org.baizey.harness.policy.git.GitPolicyResult
import org.baizey.harness.policy.shared.PolicyLifetime
import org.baizey.runtime.AuditLog
import org.baizey.runtime.SystemPath
import org.baizey.utils.IO.fromJson
import org.baizey.utils.IO.toJson
import java.nio.file.Files
import kotlin.io.path.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.notExists

class UserGitPolicyLogic(
    private val interactionPort: HarnessInteractionPort
) : GitPolicyLogic {
    private val activePolicies = mutableListOf<GitPolicy>()
    private val inaccessibleDir = SystemPath.disallowBotDir.toAbsolutePath().normalize().toString()

    override fun evaluate(rawGitRootPath: String, accessType: GitAccessType): GitPolicyResult {
        val cleanPath = Path(rawGitRootPath).toAbsolutePath().normalize()
        val gitRoot = cleanPath.toString()

        if (gitRoot.startsWith(inaccessibleDir)) {
            return logAndReturn(
                GitPolicyResult(
                    gitRoot = gitRoot,
                    isAllowed = false,
                    reason = "This directory is inaccessible for you and has been denied by the user",
                    lifetime = PolicyLifetime.FOREVER,
                    accessTypes = listOf(accessType)
                ),
                decisionSource = AuditLog.DecisionSource.SYSTEM_INACCESSIBLE_DIRECTORY
            )
        }

        val relevantPolicies = findRelevantPolicies(gitRoot, accessType)
        if (relevantPolicies.isEmpty()) {
            val newPolicy = createNewPolicy(gitRoot, accessType)
            return logAndReturn(
                GitPolicyResult(
                    gitRoot = newPolicy.gitRoot,
                    isAllowed = newPolicy.isAllowed,
                    reason = newPolicy.reason,
                    lifetime = newPolicy.lifetime,
                    accessTypes = listOf(accessType)
                ),
                decisionSource = AuditLog.DecisionSource.USER_PROMPT,
                lifetime = newPolicy.lifetime.name,
                matchedGitRoot = newPolicy.gitRoot
            )
        }

        val mostRelevantPolicy = relevantPolicies.first()
        return logAndReturn(
            GitPolicyResult(
                gitRoot = mostRelevantPolicy.gitRoot,
                isAllowed = mostRelevantPolicy.isAllowed,
                reason = mostRelevantPolicy.reason,
                lifetime = mostRelevantPolicy.lifetime,
                accessTypes = listOf(accessType)
            ),
            decisionSource = AuditLog.DecisionSource.ACTIVE_POLICY_MATCH,
            lifetime = mostRelevantPolicy.lifetime.name,
            matchedGitRoot = mostRelevantPolicy.gitRoot
        )
    }

    override fun renderAgentPolicySummary(): String {
        val explicitAllowPolicies = activePolicies
            .filter { it.isAllowed }
            .sortedWith(compareBy<GitPolicy> { it.gitRoot.lowercase() }.thenBy { accessTypesLabel(it.accessTypes) })
        val explicitDenyPolicies = activePolicies
            .filter { it.isDenied }
            .sortedWith(compareBy<GitPolicy> { it.gitRoot.lowercase() }.thenBy { accessTypesLabel(it.accessTypes) })

        return buildString {
            appendLine("**Git Policy:**")
            appendLine("- Exact repository root match is required.")
            appendLine("- Repositories not listed below are not pre-approved. Access them if necessary.")
            appendLine("- Explicitly allowed without asking:")
            if (explicitAllowPolicies.isEmpty()) {
                appendLine("  - none")
            } else {
                explicitAllowPolicies.forEach { policy ->
                    appendLine("  - ${policy.gitRoot}: ${accessTypesLabel(policy.accessTypes)}${reasonSuffix(policy.reason)}")
                }
            }
            appendLine("- Explicitly denied:")
            appendLine("  - repositories under $inaccessibleDir: ${accessTypesLabel(GitAccessType.entries)} (system protected)")
            if (explicitDenyPolicies.isEmpty()) {
                append("  - none beyond the system protected directory")
            } else {
                explicitDenyPolicies.forEach { policy ->
                    appendLine()
                    append("  - ${policy.gitRoot}: ${accessTypesLabel(policy.accessTypes)}${reasonSuffix(policy.reason)}")
                }
            }
        }
    }

    fun createNewPolicy(gitRoot: String, accessType: GitAccessType): GitPolicy {
        val decision = interactionPort.requestPermission(
            PermissionRequest(
                path = gitRoot,
                accessType = accessType.name,
                scopeOptions = listOf(gitRoot)
            )
        )
        val newPolicy = GitPolicy(
            gitRoot = decision.scope,
            accessTypes = listOf(accessType),
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

    override fun reloadFromPersistence() {
        val path = SystemPath.gitPolicyFile
        path.createParentDirectories()
        if (path.notExists()) return
        val stored = Files.readString(path).fromJson<GitPersistedPolicy>()
        val current = activePolicies.filter { it.lifetime != PolicyLifetime.FOREVER }
        activePolicies.clear()
        activePolicies.addAll(stored.policies)
        activePolicies.addAll(current)
    }

    fun clearPolicies() {
        activePolicies.clear()
    }

    private fun findRelevantPolicies(gitRoot: String, accessType: GitAccessType): List<GitPolicy> {
        return activePolicies.filter { it.gitRoot == gitRoot && accessType in it.accessTypes }
    }

    private fun updatePersistence() {
        val path = SystemPath.gitPolicyFile
        path.createParentDirectories()

        val savedPolicies = activePolicies.filter { it.lifetime == PolicyLifetime.FOREVER }
        val json = GitPersistedPolicy(savedPolicies).toJson()
        Files.writeString(path, json)
    }

    private fun logAndReturn(
        result: GitPolicyResult,
        decisionSource: AuditLog.DecisionSource,
        lifetime: String = "SYSTEM",
        matchedGitRoot: String = result.gitRoot
    ): GitPolicyResult {
        AuditLog.logPolicyDecision(
            policyType = AuditLog.PolicyType.GIT,
            subject = result.gitRoot,
            isAllowed = result.isAllowed,
            decisionSource = decisionSource,
            context = mapOf(
                "accessTypes" to result.accessTypes.joinToString(",") { it.name },
                "lifetime" to lifetime,
                "matchedGitRoot" to matchedGitRoot,
                "reason" to result.reason
            )
        )
        return result
    }

    private fun accessTypesLabel(accessTypes: List<GitAccessType>): String {
        return accessTypes.distinct().sortedBy { it.ordinal }.joinToString(", ") { it.name }
    }

    private fun reasonSuffix(reason: String): String {
        return reason.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""
    }
}
