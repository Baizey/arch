package org.baizey.harness.policy.path

import org.baizey.harness.policy.shared.PolicyLifetime

data class PathPolicyResult(
    val pattern: String,
    val path: String,
    val isAllowed: Boolean,
    val reason: String,
    val lifetime: PolicyLifetime,
    val accessTypes: List<FsAccessType>
) {
    val isDenied: Boolean = !isAllowed

    fun toDenyReasonOrNull(): String? {
        if (isAllowed) return null
        val userReason = reason.ifBlank { "Unspecified" }
        return """
            You are disallowed from accessing '$path'
            Access types blocked are: ${accessTypes.joinToString(", ") { it.name }}
            This decision was for the lifetime: ${lifetime.name}
            Due to a policy on the pattern: '$pattern'
            Users reason for policy: $userReason
        """.trimIndent()
    }

}