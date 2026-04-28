package org.baizey.harness.policy.shell

import kotlinx.serialization.Serializable

@Serializable
data class ShellPersistedPolicy(
    val policies: List<ShellPolicy>
)