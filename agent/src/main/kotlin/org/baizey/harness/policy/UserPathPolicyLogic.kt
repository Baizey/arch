package org.baizey.harness.policy

import org.baizey.harness.HarnessInteractionPort
import org.baizey.harness.PermissionRequest
import org.baizey.harness.policy.path.PathAccessDecision
import org.baizey.harness.policy.path.PathAccessDecisionEntry
import org.baizey.harness.policy.path.PathAccessInspection
import org.baizey.harness.policy.path.FsAccessType
import org.baizey.harness.policy.path.PathPersistedPolicy
import org.baizey.harness.policy.path.PathPolicy
import org.baizey.harness.policy.path.PathPolicyResult
import org.baizey.harness.policy.shared.PolicyLifetime
import org.baizey.runtime.AuditLog
import org.baizey.runtime.SystemPath
import org.baizey.utils.IO.fromJson
import org.baizey.utils.IO.toJson
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.notExists

class UserPathPolicyLogic(
    private val interactionPort: HarnessInteractionPort
) : org.baizey.harness.policy.PathPolicy {
    private val activePolicies = mutableListOf<PathPolicy>()
    private val inaccessibleDir = SystemPath.disallowBotDir.toAbsolutePath().normalize().toString()

    override fun inspectPath(rawFilePath: String): PathAccessInspection {
        val cleanPath = Path(rawFilePath).toAbsolutePath().normalize()
        val path = cleanPath.toString()
        return PathAccessInspection(
            path = path,
            decisions = FsAccessType.entries.map { inspectAccess(path, it) }
        )
    }

    override fun renderAgentPolicySummary(): String {
        val explicitAllowPolicies = activePolicies
            .filter { it.isAllowed }
            .sortedWith(compareBy<PathPolicy> { it.pattern.lowercase() }.thenBy { accessTypesLabel(it.accessTypes) })
        val explicitDenyPolicies = activePolicies
            .filter { it.isDenied }
            .sortedWith(compareBy<PathPolicy> { it.pattern.lowercase() }.thenBy { accessTypesLabel(it.accessTypes) })

        return buildString {
            appendLine("**Filesystem Policy:**")
            appendLine("- Closest ancestor path match wins.")
            appendLine("- Paths not listed below are not pre-approved. Access if them if necessary.")
            appendLine("- Explicitly allowed without asking:")
            if (explicitAllowPolicies.isEmpty()) {
                appendLine("  - none")
            } else {
                explicitAllowPolicies.forEach { policy ->
                    appendLine("  - ${policy.pattern}: ${accessTypesLabel(policy.accessTypes)}${reasonSuffix(policy.reason)}")
                }
            }
            appendLine("- Explicitly denied:")
            appendLine("  - $inaccessibleDir: ${accessTypesLabel(FsAccessType.entries)} (system protected)")
            if (explicitDenyPolicies.isEmpty()) {
                append("  - none beyond the system protected directory")
            } else {
                explicitDenyPolicies.forEach { policy ->
                    appendLine()
                    append("  - ${policy.pattern}: ${accessTypesLabel(policy.accessTypes)}${reasonSuffix(policy.reason)}")
                }
            }
        }
    }

    override fun evaluate(rawFilePath: String, accessType: FsAccessType): PathPolicyResult {
        val cleanPath = Path(rawFilePath).toAbsolutePath().normalize()
        val path = cleanPath.toString()

        // Never allow access to the directory which system logs and files are stored in
        // This could allow breaking self-modification of constraints for the agent or other unintended consequences...
        if (path.startsWith(inaccessibleDir)) {
            return logAndReturn(
                PathPolicyResult(
                    pattern = inaccessibleDir,
                    path = path,
                    isAllowed = false,
                    reason = "This directory is inaccessible for you and has been denied by the user",
                    lifetime = PolicyLifetime.FOREVER,
                    accessTypes = listOf(accessType)
                ),
                decisionSource = AuditLog.DecisionSource.SYSTEM_INACCESSIBLE_DIRECTORY
            )
        }

        val relevantPolicies = findRelevantPolicies(path).sortedByDescending { it.pattern.length }
        val firstRelevantPolicy = relevantPolicies.firstOrNull { it.accessTypes.contains(accessType) }
        if (firstRelevantPolicy == null) {
            val newPolicy = createOrUpdatePolicy(cleanPath, accessType)
            return logAndReturn(
                PathPolicyResult(
                    pattern = newPolicy.pattern,
                    path = path,
                    isAllowed = newPolicy.isAllowed,
                    reason = newPolicy.reason,
                    lifetime = newPolicy.lifetime,
                    accessTypes = listOf(accessType)
                ),
                decisionSource = AuditLog.DecisionSource.USER_PROMPT,
                lifetime = newPolicy.lifetime.name,
                matchedPolicyPattern = newPolicy.pattern
            )
        }

        return logAndReturn(
            PathPolicyResult(
                pattern = firstRelevantPolicy.pattern,
                path = path,
                isAllowed = firstRelevantPolicy.isAllowed,
                reason = firstRelevantPolicy.reason,
                lifetime = firstRelevantPolicy.lifetime,
                accessTypes = listOf(accessType)
            ),
            decisionSource = AuditLog.DecisionSource.ACTIVE_POLICY_MATCH,
            lifetime = firstRelevantPolicy.lifetime.name,
            matchedPolicyPattern = firstRelevantPolicy.pattern
        )
    }

    fun createOrUpdatePolicy(path: Path, accessType: FsAccessType): PathPolicy {
        val decision = interactionPort.requestPermission(
            PermissionRequest(
                path = path.toString(),
                accessType = accessType.name,
                scopeOptions = buildScopeOptions(path)
            )
        )
        val oldPolicy = activePolicies.find { it.pattern == decision.scope }

        val policy = if (oldPolicy == null) {
            val policy = PathPolicy(
                pattern = decision.scope,
                accessTypes = mutableListOf(accessType),
                lifetime = decision.lifetime,
                isAllowed = decision.isAllowed,
                reason = decision.reason
            )
            activePolicies.add(policy)
            policy
        } else {
            if (accessType !in oldPolicy.accessTypes) {
                oldPolicy.accessTypes.add(accessType)
            }
            oldPolicy
        }

        if (policy.lifetime == PolicyLifetime.FOREVER) {
            updatePersistence()
        }
        return policy
    }

    fun reloadFromPersistence(): List<PathPolicy> {
        val path = SystemPath.policyFile
        path.createParentDirectories()
        if (path.notExists()) return listOf()
        val stored = Files.readString(path).fromJson<PathPersistedPolicy>()
        val current = this.activePolicies.filter { it.lifetime != PolicyLifetime.FOREVER }
        activePolicies.clear()
        activePolicies.addAll(stored.policies)
        activePolicies.addAll(current)
        return activePolicies.toList()
    }

    fun findRelevantPolicies(path: String): List<PathPolicy> {
        return activePolicies.filter { path.startsWith(it.pattern) }
    }

    fun clearPolicies() {
        activePolicies.clear()
    }

    private fun updatePersistence() {
        val path = SystemPath.policyFile
        path.createParentDirectories()

        val savedPolicies = activePolicies.filter { it.lifetime == PolicyLifetime.FOREVER }
        val json = PathPersistedPolicy(savedPolicies).toJson()
        Files.writeString(path, json)
    }

    private fun logAndReturn(
        result: PathPolicyResult,
        decisionSource: AuditLog.DecisionSource,
        lifetime: String = "SYSTEM",
        matchedPolicyPattern: String = result.pattern
    ): PathPolicyResult {
        AuditLog.logPolicyDecision(
            policyType = AuditLog.PolicyType.PATH,
            subject = result.path,
            isAllowed = result.isAllowed,
            decisionSource = decisionSource,
            context = mapOf(
                "accessTypes" to result.accessTypes.joinToString(",") { it.name },
                "lifetime" to lifetime,
                "matchedPolicyPattern" to matchedPolicyPattern,
                "reason" to result.reason
            )
        )
        return result
    }

    private fun inspectAccess(path: String, accessType: FsAccessType): PathAccessDecisionEntry {
        if (path.startsWith(inaccessibleDir)) {
            return PathAccessDecisionEntry(
                accessType = accessType,
                decision = PathAccessDecision.DENY,
                pattern = inaccessibleDir,
                reason = "This directory is inaccessible for you and has been denied by the user."
            )
        }

        val matchedPolicy = findRelevantPolicies(path)
            .sortedByDescending { it.pattern.length }
            .firstOrNull { it.accessTypes.contains(accessType) }

        if (matchedPolicy != null) {
            return PathAccessDecisionEntry(
                accessType = accessType,
                decision = if (matchedPolicy.isAllowed) PathAccessDecision.ALLOW else PathAccessDecision.DENY,
                pattern = matchedPolicy.pattern,
                reason = matchedPolicy.reason.ifBlank {
                    if (matchedPolicy.isAllowed) "Allowed by an active policy." else "Denied by an active policy."
                }
            )
        }

        return PathAccessDecisionEntry(
            accessType = accessType,
            decision = PathAccessDecision.ASK_PERMISSION,
            pattern = null,
            reason = "No matching policy exists yet. Ask permission before using this path."
        )
    }

    private fun buildScopeOptions(path: Path): List<String> {
        val scopes = mutableListOf<String>()
        var current: Path? = path
        while (current != null) {
            scopes += current.normalize().toString()
            current = current.parent
        }
        return scopes
    }

    private fun accessTypesLabel(accessTypes: List<FsAccessType>): String {
        return accessTypes.distinct().sortedBy { it.ordinal }.joinToString(", ") { it.name }
    }

    private fun reasonSuffix(reason: String): String {
        return reason.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""
    }
}
