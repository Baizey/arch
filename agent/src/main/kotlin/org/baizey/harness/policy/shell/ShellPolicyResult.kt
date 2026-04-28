package org.baizey.harness.policy.shell

data class ShellPolicyResult(
    val signature: String,
    val subject: String,
    val isAllowed: Boolean,
    val reason: String
) {
    val isDenied: Boolean = !isAllowed

    fun toDenyReasonOrNull(): String? {
        if (isAllowed) return null
        val userReason = reason.ifBlank { "Unspecified" }
        return """
            You are disallowed from executing '$subject'
            Due to a policy on the command: '$signature'
            Users reason for policy: $userReason
        """.trimIndent()
    }
}