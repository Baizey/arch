package org.baizey.harness.policy

import org.baizey.harness.AskUserAnswer
import org.baizey.harness.HarnessInteractionPort
import org.baizey.harness.PermissionDecision
import org.baizey.harness.PermissionRequest
import org.baizey.harness.policy.git.GitAccessType
import org.baizey.harness.policy.shared.PolicyLifetime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UserGitPolicyLogicTest {
    private var permissionDecisionProvider: (PermissionRequest) -> PermissionDecision = { request ->
        PermissionDecision(
            isAllowed = true,
            lifetime = PolicyLifetime.SESSION,
            scope = request.path
        )
    }

    private val interactionPort = object : HarnessInteractionPort {
        val requests = mutableListOf<PermissionRequest>()

        override fun askUserQuestion(question: String, options: List<String>): AskUserAnswer {
            return AskUserAnswer(
                isAccepted = true,
                selection = options.firstOrNull().orEmpty(),
                selectionIndex = 0
            )
        }

        override fun requestPermission(request: PermissionRequest): PermissionDecision {
            requests += request
            return permissionDecisionProvider(request)
        }
    }

    private val logic = UserGitPolicyLogic(interactionPort)

    @Test
    fun `reuses a session policy for the same repo and access type`() {
        val gitRoot = "C:/repo/project"

        val first = logic.evaluate(gitRoot, GitAccessType.MODIFY_LOCAL)
        val second = logic.evaluate(gitRoot, GitAccessType.MODIFY_LOCAL)

        assertTrue(first.isAllowed)
        assertTrue(second.isAllowed)
        assertEquals(1, interactionPort.requests.size)
        assertEquals(interactionPort.requests.single().path, interactionPort.requests.single().scopeOptions.single())
    }

    @Test
    fun `prompts again for a different access type on the same repo`() {
        val gitRoot = "C:/repo/project"

        logic.evaluate(gitRoot, GitAccessType.READ)
        logic.evaluate(gitRoot, GitAccessType.MODIFY_LOCAL)

        assertEquals(2, interactionPort.requests.size)
        assertEquals("READ", interactionPort.requests[0].accessType)
        assertEquals("MODIFY_LOCAL", interactionPort.requests[1].accessType)
    }

    @Test
    fun `does not cache once lifetime decisions`() {
        val gitRoot = "C:/repo/project"
        permissionDecisionProvider = { request ->
            PermissionDecision(
                isAllowed = false,
                lifetime = PolicyLifetime.ONCE,
                scope = request.path,
                reason = "Denied for this call only"
            )
        }

        val first = logic.evaluate(gitRoot, GitAccessType.MODIFY_LOCAL)
        val second = logic.evaluate(gitRoot, GitAccessType.MODIFY_LOCAL)

        assertFalse(first.isAllowed)
        assertFalse(second.isAllowed)
        assertEquals(2, interactionPort.requests.size)
    }
}
