package org.baizey.harness.policy

import org.baizey.harness.AskUserAnswer
import org.baizey.harness.HarnessInteractionPort
import org.baizey.harness.PermissionDecision
import org.baizey.harness.PermissionRequest
import org.baizey.harness.policy.shared.PolicyLifetime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ShellPolicyLogicTest {
    private var permissionDecisionProvider: (PermissionRequest) -> PermissionDecision = { request ->
        PermissionDecision(
            isAllowed = true,
            lifetime = PolicyLifetime.SESSION,
            scope = request.scopeOptions.single()
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

    private val logic = ShellPolicyLogic(interactionPort)

    @Test
    fun `reuses a session policy for the same command signature`() {
        val first = logic.evaluate("git", listOf("status"), "C:/repo/a")
        val second = logic.evaluate("git", listOf("status"), "C:/repo/a")

        assertTrue(first.isAllowed)
        assertTrue(second.isAllowed)
        assertEquals(1, interactionPort.requests.size)
        assertEquals("git status", interactionPort.requests.single().scopeOptions.single())
    }

    @Test
    fun `matching is based on command signature rather than working directory`() {
        val first = logic.evaluate("git", listOf("status"), "C:/repo/a")
        val second = logic.evaluate("git", listOf("status"), "C:/repo/b")

        assertTrue(first.isAllowed)
        assertTrue(second.isAllowed)
        assertEquals(1, interactionPort.requests.size)
    }

    @Test
    fun `does not cache once lifetime decisions`() {
        permissionDecisionProvider = { request ->
            PermissionDecision(
                isAllowed = false,
                lifetime = PolicyLifetime.ONCE,
                scope = request.scopeOptions.single(),
                reason = "Denied for this call only"
            )
        }

        val first = logic.evaluate("git", listOf("status"), null)
        val second = logic.evaluate("git", listOf("status"), null)

        assertFalse(first.isAllowed)
        assertFalse(second.isAllowed)
        assertEquals(2, interactionPort.requests.size)
    }

    @Test
    fun `quotes command signatures the same way for empty and unsafe tokens`() {
        val signature = logic.formatCommandSignature(
            "cmd",
            listOf("", "hello world", "quote\"me", "plain")
        )

        assertEquals("""cmd "" "hello world" "quote\"me" plain""", signature)
    }
}
