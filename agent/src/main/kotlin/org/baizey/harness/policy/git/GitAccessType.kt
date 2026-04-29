package org.baizey.harness.policy.git

import kotlinx.serialization.Serializable

@Serializable
enum class GitAccessType {
    READ,
    MODIFY_LOCAL,
    PUSH,
    PULL;

    val isModifyingFile get() = this != READ
}
