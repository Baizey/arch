package org.baizey.harness.policy.git

import kotlinx.serialization.Serializable
import org.baizey.harness.policy.shared.PolicyLifetime

@Serializable
data class GitPolicy(
    val gitRoot: String,
    val accessTypes: List<GitAccessType>,
    val lifetime: PolicyLifetime,
    val isAllowed: Boolean,
    val reason: String
) {
    val isDenied = !isAllowed
}
