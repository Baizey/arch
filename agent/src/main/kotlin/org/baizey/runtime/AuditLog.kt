package org.baizey.runtime

object AuditLog {
    enum class PolicyType(
        val wireName: String
    ) {
        PATH("path_policy"),
        SHELL("shell_policy"),
        GIT("git_policy")
    }

    enum class DecisionSource(
        val wireName: String
    ) {
        SYSTEM_INACCESSIBLE_DIRECTORY("system_inaccessible_dir"),
        USER_PROMPT("user_prompt"),
        ACTIVE_POLICY_MATCH("active_policy_match")
    }

    fun logPolicyDecision(
        policyType: PolicyType,
        subject: String,
        isAllowed: Boolean,
        decisionSource: DecisionSource,
        context: Map<String, String> = emptyMap()
    ) {
        AppLog.writeAudit(
            event = "${policyType.wireName}.${if (isAllowed) "allow" else "deny"}",
            context = linkedMapOf(
                "decisionSource" to decisionSource.wireName,
                "subject" to subject
            ).apply {
                putAll(context)
            }
        )
    }
}
