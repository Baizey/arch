package org.baizey.harness.policy.shell

import kotlinx.serialization.Serializable
import org.baizey.harness.policy.shared.PolicyLifetime

@Serializable
data class ShellPolicy(
    val signature: String,
    val lifetime: PolicyLifetime,
    val isAllowed: Boolean,
    val reason: String
) {
    val isDenied = !isAllowed
}