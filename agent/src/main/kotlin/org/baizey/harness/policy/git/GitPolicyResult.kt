package org.baizey.harness.policy.git

import org.baizey.harness.policy.shared.PolicyLifetime

data class GitPolicyResult(
    val gitRoot: String,
    val isAllowed: Boolean,
    val reason: String,
    val lifetime: PolicyLifetime,
    val accessTypes: List<GitAccessType>
) {
    val isDenied: Boolean = !isAllowed

    fun toDenyReasonOrNull(): String? {
        if (isAllowed) return null
        val userReason = reason.ifBlank { "Unspecified" }
        return """
            You are disallowed from your git action
            Due to the git root in question: '$gitRoot'
            This decision was for the lifetime: ${lifetime.name}
            Access types blocked are: ${accessTypes.joinToString(", ") { it.name }}
            Users reason for policy: $userReason
        """.trimIndent()
    }
}
