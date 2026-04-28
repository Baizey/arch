package org.baizey.harness.policy.git

import kotlinx.serialization.Serializable

@Serializable
enum class GitAccessType(val isModifying: Boolean) {
    READ(isModifying = false),
    MODIFY_LOCAL(isModifying = true),
    PUSH(isModifying = true),
    PULL(isModifying = true)
}
