package org.baizey.runtime

import org.baizey.commands.utils.ModelSelection
import org.baizey.harness.AskUserAnswer
import org.baizey.harness.HarnessContext
import org.baizey.harness.HarnessInteractionPort
import org.baizey.harness.PermissionDecision
import org.baizey.harness.PermissionRequest
import org.baizey.harness.policy.shared.PolicyLifetime
import org.baizey.runtime.agent.AgentConfig
import org.baizey.runtime.agent.AgentInstance
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ModelSelection")
class AgentRuntimeTest {
    private val originalModel = ModelSelection.current()

    @AfterEach
    fun restoreModelSelection() {
        ModelSelection.select(originalModel)
    }

    @Test
    fun `reset conversation recreates agent instance`() {
        val createdModels = mutableListOf<String>()
        val runtime = createRuntime(createdModels)

        assertEquals("instance-1", runtime.currentModel)
        assertEquals(1, createdModels.size)

        runtime.resetConversation()

        assertEquals(2, createdModels.size)
        assertEquals("instance-2", runtime.currentModel)
    }

    @Test
    fun `refresh recreates agent instance when model changes`() {
        val createdModels = mutableListOf<String>()
        val runtime = createRuntime(createdModels)
        val nextModel = otherSupportedModel()

        assertNull(runtime.refreshIfNeeded())

        ModelSelection.select(nextModel)
        val refreshedModel = runtime.refreshIfNeeded()

        assertEquals("instance-2", refreshedModel)
        assertEquals(listOf(ModelSelection.DEFAULT_MODEL, nextModel), createdModels)
        assertEquals("instance-2", runtime.currentModel)
    }

    private fun createRuntime(createdModels: MutableList<String>): AgentRuntime {
        ModelSelection.select(ModelSelection.DEFAULT_MODEL)
        return AgentRuntime(
            agentContext = HarnessContext(FakeInteractionPort()),
            listenersProvider = { emptyList() },
            reloadContextBeforeBuild = false,
            agentInstanceFactory = { config: AgentConfig, _: AgentProviderConfig ->
                createdModels += config.modelName
                FakeAgentInstance("instance-${createdModels.size}")
            }
        )
    }

    private fun otherSupportedModel(): String {
        return ModelSelection.supportedModels.first { it != ModelSelection.DEFAULT_MODEL }
    }

    private class FakeAgentInstance(
        private val name: String
    ) : AgentInstance {
        override fun chat(prompt: String): String? = null

        override fun modelName(): String = name
    }

    private class FakeInteractionPort : HarnessInteractionPort {
        override fun askUserQuestion(question: String, options: List<String>): AskUserAnswer {
            return AskUserAnswer(
                isAccepted = false,
                selection = "",
                selectionIndex = -1
            )
        }

        override fun requestPermission(request: PermissionRequest): PermissionDecision {
            return PermissionDecision(
                isAllowed = false,
                lifetime = PolicyLifetime.ONCE,
                scope = "",
                reason = "not used in test"
            )
        }
    }
}
