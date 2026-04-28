package org.baizey.harness.policy.path

import kotlinx.serialization.Serializable
import org.baizey.harness.policy.shared.PolicyLifetime

@Serializable
data class PathPolicy(
    val pattern: String,
    val accessTypes: MutableList<FsAccessType>,
    val lifetime: PolicyLifetime,
    val isAllowed: Boolean,
    val reason: String
) {
    val isDenied = !isAllowed
}