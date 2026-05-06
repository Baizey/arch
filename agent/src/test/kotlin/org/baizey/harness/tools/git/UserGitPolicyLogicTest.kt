package org.baizey.harness.tools.git

import org.baizey.harness.AskUserAnswer
import org.baizey.harness.HarnessInteractionPort
import org.baizey.harness.PermissionDecision
import org.baizey.harness.PermissionRequest
import org.baizey.harness.policy.git.GitAccessType
import org.baizey.harness.policy.UserGitPolicyLogic
import org.baizey.harness.policy.shared.PolicyLifetime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class UserGitPolicyLogicTest {
    private var permissionDecisionProvider: (PermissionRequest) -> PermissionDecision = { request ->
        PermissionDecision(
            isAllowed = false,
            lifetime = PolicyLifetime.ONCE,
            scope = request.path,
            reason = "Denied for test"
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

    private val tool = UserGitPolicyLogic(interactionPort)

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `maps access types to git policy prompts`() {
        permissionDecisionProvider = { request ->
            PermissionDecision(
                isAllowed = false,
                lifetime = PolicyLifetime.ONCE,
                scope = request.path,
                reason = "Denied for test"
            )
        }
        val repoRoot = Files.createDirectories(tempDir.resolve(".git")).parent
        val expected = listOf(
            GitAccessType.READ,
            GitAccessType.MODIFY_LOCAL,
            GitAccessType.PUSH,
            GitAccessType.PULL
        )

        expected.forEach { accessType ->
            val result = tool.evaluate(repoRoot.toString(), accessType)
            assertTrue(result.toDenyReasonOrNull().orEmpty().contains(accessType.name), result.toDenyReasonOrNull())
        }

        assertEquals(expected.map { it.name }, interactionPort.requests.map { it.accessType })
    }

    @Test
    fun `git policies match the exact git root only`() {
        permissionDecisionProvider = { request ->
            PermissionDecision(
                isAllowed = true,
                lifetime = PolicyLifetime.SESSION,
                scope = request.path
            )
        }

        val repoOne = Files.createDirectories(tempDir.resolve("repo-one/.git")).parent
        val repoTwo = Files.createDirectories(tempDir.resolve("repo-two/.git")).parent

        val first = tool.evaluate(repoOne.toString(), GitAccessType.READ)
        val second = tool.evaluate(repoOne.toString(), GitAccessType.READ)
        val third = tool.evaluate(repoTwo.toString(), GitAccessType.READ)

        assertTrue(first.isAllowed, first.toDenyReasonOrNull())
        assertTrue(second.isAllowed, second.toDenyReasonOrNull())
        assertTrue(third.isAllowed, third.toDenyReasonOrNull())
        assertEquals(listOf(repoOne.toString(), repoTwo.toString()), interactionPort.requests.map { it.path })
    }
}
