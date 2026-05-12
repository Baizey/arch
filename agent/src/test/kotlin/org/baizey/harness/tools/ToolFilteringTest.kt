package org.baizey.harness.tools

import dev.langchain4j.agent.tool.ToolSpecifications
import org.baizey.harness.AskUserAnswer
import org.baizey.harness.HarnessInteractionPort
import org.baizey.harness.PermissionDecision
import org.baizey.harness.PermissionRequest
import org.baizey.harness.policy.UserGitPolicyLogic
import org.baizey.harness.policy.UserPathPolicyLogic
import org.baizey.harness.policy.shared.PolicyLifetime
import org.baizey.runtime.AppConfig
import org.baizey.runtime.ToolFilterMode
import org.baizey.runtime.ToolFilterProfile
import org.baizey.runtime.agentic.instance.AgenticInstanceContext
import org.baizey.runtime.agentic.instance.CoreContext
import org.baizey.runtime.agentic.instance.PolicyContext
import org.baizey.runtime.agentic.instance.ProviderType
import org.baizey.runtime.agentic.instance.ToolContext
import org.baizey.runtime.sandbox.AgentShSandboxService
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ToolFilteringTest {
    private val interactionPort = object : HarnessInteractionPort {
        override fun askUserQuestion(question: String, options: List<String>): AskUserAnswer {
            return AskUserAnswer(true, options.firstOrNull().orEmpty(), 0)
        }

        override fun requestPermission(request: PermissionRequest): PermissionDecision {
            return PermissionDecision(true, PolicyLifetime.ONCE, request.path)
        }
    }
    private val pathPolicyLogic = UserPathPolicyLogic(interactionPort)
    private val gitPolicyLogic = UserGitPolicyLogic(interactionPort)
    private val sandboxService = AgentShSandboxService(AppConfig.sandbox, pathPolicyLogic, "tool-filtering-test")

    @Test
    fun `disabling a group removes all tools in that group`() {
        val profile = ToolFilterProfile(
            id = "custom",
            name = "No filesystem",
            isBuiltIn = false,
            rules = mapOf(
                "tool:ask_path_permission" to ToolFilterMode.DISABLED,
                "tool:inspect_path_access" to ToolFilterMode.DISABLED,
                "tool:list_directory" to ToolFilterMode.DISABLED,
                "tool:search_files" to ToolFilterMode.DISABLED,
                "tool:read_file" to ToolFilterMode.DISABLED,
                "tool:edit_file" to ToolFilterMode.DISABLED,
                "tool:write_file" to ToolFilterMode.DISABLED,
                "tool:move_or_copy_path" to ToolFilterMode.DISABLED,
                "tool:delete_path" to ToolFilterMode.DISABLED
            )
        )
        val toolNames = AgentTools.create(
            agenticContext(profile)
        ).flatMap { tool -> ToolSpecifications.toolSpecificationsFrom(tool).map { it.name() } }

        assertFalse(toolNames.contains("read_file"))
        assertFalse(toolNames.contains("delete_path"))
        assertTrue(toolNames.contains("search_web"))
    }

    @Test
    fun `subgroup and tool rules can override broader group rules`() {
        val profile = ToolFilterProfile(
            id = "custom",
            name = "Selective tools",
            isBuiltIn = false,
            rules = mapOf(
                "tool:ask_path_permission" to ToolFilterMode.DISABLED,
                "tool:inspect_path_access" to ToolFilterMode.DISABLED,
                "tool:list_directory" to ToolFilterMode.ENABLED,
                "tool:search_files" to ToolFilterMode.ENABLED,
                "tool:read_file" to ToolFilterMode.ENABLED,
                "tool:edit_file" to ToolFilterMode.DISABLED,
                "tool:write_file" to ToolFilterMode.ENABLED,
                "tool:move_or_copy_path" to ToolFilterMode.DISABLED,
                "tool:delete_path" to ToolFilterMode.DISABLED,
                "tool:git_status" to ToolFilterMode.DISABLED,
                "tool:git_diff" to ToolFilterMode.DISABLED,
                "tool:git_log" to ToolFilterMode.DISABLED,
                "tool:git_add" to ToolFilterMode.DISABLED,
                "tool:git_commit" to ToolFilterMode.DISABLED,
                "tool:git_checkout" to ToolFilterMode.DISABLED,
                "tool:git_fetch" to ToolFilterMode.ENABLED,
                "tool:git_pull" to ToolFilterMode.ENABLED,
                "tool:git_push" to ToolFilterMode.DISABLED
            )
        )
        val toolNames = AgentTools.create(
            agenticContext(profile)
        ).flatMap { tool -> ToolSpecifications.toolSpecificationsFrom(tool).map { it.name() } }

        assertTrue(toolNames.contains("read_file"))
        assertTrue(toolNames.contains("search_files"))
        assertTrue(toolNames.contains("write_file"))
        assertFalse(toolNames.contains("delete_path"))
        assertTrue(toolNames.contains("git_fetch"))
        assertTrue(toolNames.contains("git_pull"))
        assertFalse(toolNames.contains("git_push"))
        assertFalse(toolNames.contains("git_status"))
    }

    private fun agenticContext(profile: ToolFilterProfile): AgenticInstanceContext {
        return AgenticInstanceContext(
            core = CoreContext(
                systemPrompt = "",
                type = ProviderType.OLLAMA,
                modelName = "test",
                listeners = emptyList(),
                userInteraction = interactionPort
            ),
            policies = PolicyContext(
                path = pathPolicyLogic,
                git = gitPolicyLogic
            ),
            tools = ToolContext(
                profile = profile,
                sandbox = sandboxService
            )
        )
    }
}
