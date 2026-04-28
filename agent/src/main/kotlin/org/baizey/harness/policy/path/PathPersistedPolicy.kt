package org.baizey.harness.policy.path

import kotlinx.serialization.Serializable

@Serializable
data class PathPersistedPolicy(
    val policies: List<PathPolicy>
)