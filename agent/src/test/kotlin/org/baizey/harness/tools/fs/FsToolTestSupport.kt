package org.baizey.harness.tools.fs

import org.baizey.harness.AskUserAnswer
import org.baizey.harness.HarnessInteractionPort
import org.baizey.harness.HarnessContext
import org.baizey.harness.PermissionDecision
import org.baizey.harness.PermissionRequest
import org.baizey.harness.policy.PolicyCollection
import org.baizey.harness.policy.UserGitPolicyLogic
import org.baizey.harness.policy.UserPathPolicyLogic
import org.baizey.harness.policy.shared.PolicyLifetime
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

abstract class FsToolTestSupport {
    protected var permissionDecisionProvider: (PermissionRequest) -> PermissionDecision =
        { PermissionDecision(isAllowed = true, lifetime = PolicyLifetime.ONCE, scope = it.path) }

    protected val interactionPort = object : HarnessInteractionPort {
        override fun askUserQuestion(question: String, options: List<String>): AskUserAnswer =
            AskUserAnswer(isAccepted = true, selection = options.firstOrNull().orEmpty(), selectionIndex = 0)

        override fun requestPermission(request: PermissionRequest): PermissionDecision {
            return permissionDecisionProvider(request)
        }
    }

    protected val pathPolicyLogic = UserPathPolicyLogic(interactionPort)
    protected val gitPolicyLogic = UserGitPolicyLogic(interactionPort)
    protected val toolContext = HarnessContext(
        interactionPort = interactionPort,
        policies = PolicyCollection(
            git = gitPolicyLogic,
            path = pathPolicyLogic
        )
    )

    @BeforeEach
    fun setUpFsToolTestSupport() {
        pathPolicyLogic.clearPolicies()
        gitPolicyLogic.clearPolicies()
    }

    @AfterEach
    fun tearDownFsToolTestSupport() {
        permissionDecisionProvider =
            { PermissionDecision(isAllowed = true, lifetime = PolicyLifetime.ONCE, scope = it.path) }
        pathPolicyLogic.clearPolicies()
        gitPolicyLogic.clearPolicies()
    }

    protected fun denyPermissions(reason: String = "Denied for test") {
        permissionDecisionProvider =
            { PermissionDecision(isAllowed = false, lifetime = PolicyLifetime.ONCE, scope = it.path, reason = reason) }
    }
}
