package org.baizey.harness.policy.path

import kotlinx.serialization.Serializable

@Serializable
enum class FsAccessType(val isModifyingFile: Boolean) {
    DELETE(isModifyingFile = true),
    WRITE(isModifyingFile = true),
    EDIT(isModifyingFile = true),
    EXECUTE(isModifyingFile = true),
    READ(isModifyingFile = false);


    val isModifying get() = this != READ
}