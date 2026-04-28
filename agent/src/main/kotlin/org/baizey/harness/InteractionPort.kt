package org.baizey.harness

import org.baizey.harness.policy.shared.PolicyLifetime

data class AskUserAnswer(
    val isAccepted: Boolean,
    val selection: String,
    val selectionIndex: Int
)

data class PermissionRequest(
    val path: String,
    val accessType: String,
    val scopeOptions: List<String>
)

data class PermissionDecision(
    val isAllowed: Boolean,
    val lifetime: PolicyLifetime,
    val scope: String,
    val reason: String = ""
)

interface HarnessInteractionPort {
    fun askUserQuestion(question: String, options: List<String>): AskUserAnswer

    fun requestPermission(request: PermissionRequest): PermissionDecision
}
