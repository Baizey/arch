package org.baizey.harness.policy.git

import kotlinx.serialization.Serializable

@Serializable
data class GitPersistedPolicy(
    val policies: List<GitPolicy>
)
