package org.baizey.harness.tools.git

import dev.langchain4j.agent.tool.ToolSpecifications
import org.baizey.harness.AskUserAnswer
import org.baizey.harness.HarnessInteractionPort
import org.baizey.harness.PermissionDecision
import org.baizey.harness.PermissionRequest
import org.baizey.harness.policy.UserGitPolicyLogic
import org.baizey.harness.policy.shared.PolicyLifetime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GitToolSchemaTest {
    private val interactionPort = object : HarnessInteractionPort {
        override fun askUserQuestion(question: String, options: List<String>): AskUserAnswer {
            return AskUserAnswer(
                isAccepted = true,
                selection = options.firstOrNull().orEmpty(),
                selectionIndex = 0
            )
        }

        override fun requestPermission(request: PermissionRequest): PermissionDecision {
            return PermissionDecision(
                isAllowed = true,
                lifetime = PolicyLifetime.ONCE,
                scope = request.path
            )
        }
    }
    private val gitPolicyLogic = UserGitPolicyLogic(interactionPort)

    @Test
    fun `git tools expose the simplified command surface`() {
        val schemas = GitTools.create(gitPolicyLogic)
            .flatMap { ToolSpecifications.toolSpecificationsFrom(it) }

        val schemaByName = schemas.associateBy { it.name() }

        assertTrue(schemaByName.containsKey("git_fetch"))
        assertEquals(listOf("path", "remote", "branch"), schemaByName.getValue("git_fetch").parameters().properties().keys.toList())
        assertEquals(listOf("path", "message", "authorName", "authorEmail"), schemaByName.getValue("git_commit").parameters().properties().keys.toList())
        assertEquals(listOf("path", "remote", "branch", "dryRun"), schemaByName.getValue("git_push").parameters().properties().keys.toList())
        assertEquals(listOf("path", "branch", "newBranch"), schemaByName.getValue("git_checkout").parameters().properties().keys.toList())
        assertEquals(listOf("path", "remote", "branch"), schemaByName.getValue("git_pull").parameters().properties().keys.toList())
    }
}
