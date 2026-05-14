package org.baizey.harness.policy.path

import kotlinx.serialization.Serializable

@Serializable
data class PathPolicySnapshot(
    val policies: List<PathPolicy>,
    val deniedPathPrefixes: List<String>
)
