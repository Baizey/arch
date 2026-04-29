package org.baizey.harness.policy.path

import kotlinx.serialization.Serializable

@Serializable
enum class FsAccessType {
    DELETE,
    WRITE,
    EDIT,
    EXECUTE,
    READ;

    val isModifying get() = this != READ
}