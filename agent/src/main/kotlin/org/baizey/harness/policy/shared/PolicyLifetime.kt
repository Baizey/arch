package org.baizey.harness.policy.shared

import kotlinx.serialization.Serializable

@Serializable
enum class PolicyLifetime {
    ONCE,
    SESSION,
    FOREVER
}