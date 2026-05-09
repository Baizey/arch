package org.baizey.harness.tools.git

import org.baizey.harness.AskUserAnswer
import org.baizey.harness.HarnessInteractionPort
import org.baizey.harness.PermissionDecision
import org.baizey.harness.PermissionRequest
import org.baizey.harness.policy.UserGitPolicyLogic
import org.baizey.harness.policy.UserPathPolicyLogic
import org.baizey.harness.policy.shared.PolicyLifetime
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

abstract class GitToolTestSupport {
    protected var permissionDecisionProvider: (PermissionRequest) -> PermissionDecision = { request ->
        PermissionDecision(
            isAllowed = true,
            lifetime = PolicyLifetime.ONCE,
            scope = request.path
        )
    }

    protected val interactionPort = object : HarnessInteractionPort {
        override fun askUserQuestion(question: String, options: List<String>): AskUserAnswer {
            return AskUserAnswer(
                isAccepted = true,
                selection = options.firstOrNull().orEmpty(),
                selectionIndex = 0
            )
        }

        override fun requestPermission(request: PermissionRequest): PermissionDecision {
            return permissionDecisionProvider(request)
        }
    }

    protected val pathPolicyLogic = UserPathPolicyLogic(interactionPort)
    protected val gitPolicyLogic = UserGitPolicyLogic(interactionPort)

    @BeforeEach
    fun setUpGitToolTestSupport() {
        gitPolicyLogic.clearPolicies()
    }

    @AfterEach
    fun tearDownGitToolTestSupport() {
        permissionDecisionProvider = { request ->
            PermissionDecision(
                isAllowed = true,
                lifetime = PolicyLifetime.ONCE,
                scope = request.path
            )
        }
        gitPolicyLogic.clearPolicies()
    }

    protected fun denyPermissions(reason: String = "Denied for test") {
        permissionDecisionProvider = { request ->
            PermissionDecision(
                isAllowed = false,
                lifetime = PolicyLifetime.ONCE,
                scope = request.path,
                reason = reason
            )
        }
    }
}
