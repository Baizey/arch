package org.baizey.runtime

import dev.langchain4j.model.chat.listener.ChatModelListener
import org.baizey.commands.utils.ModelSelection
import org.baizey.harness.HarnessRuntime
import org.baizey.runtime.agentic.instance.AgentInstance
import org.baizey.runtime.agentic.instance.AgenticInstanceContext
import org.baizey.runtime.agentic.instance.SystemPrompt

class AgentRuntime(
    private val agentContext: AgenticInstanceContext,
    private val shouldInterruptBeforeToolExecution: () -> Boolean = { false },
    private val listenersProvider: () -> List<ChatModelListener>,
    private val toolFilterRevisionProvider: () -> Int = { 0 },
    private val toolFilterProfileProvider: () -> ToolFilterProfile? = { null },
    private val mcpToolFilterProfileProvider: () -> McpToolFilterProfile? = { null },
    private val reloadContextBeforeBuild: Boolean = true,
    private val agentInstanceFactory: (AgenticInstanceContext) -> AgentInstance = { AgentInstance.create(it) }
) : HarnessRuntime {
    private var activeModelRevision = ModelSelection.currentRevision()
    private var activeToolFilterRevision = toolFilterRevisionProvider()
    private var activeContext = buildAgenticInstanceContext()
    private var agentInstance = buildAgentInstance(activeContext)

    override val currentModel: String get() = agentInstance.displayName()

    override val builtInToolCount: Int get() = agentInstance.builtInToolCount()

    override fun chat(prompt: String): String? = agentInstance.chat(prompt)

    override fun resetConversation() {
        agentInstance.clearStoredChatMemory()
        replaceAgentInstance()
        activeModelRevision = ModelSelection.currentRevision()
        activeToolFilterRevision = toolFilterRevisionProvider()
    }

    override fun refreshIfNeeded(): String? {
        val modelChanged = activeModelRevision != ModelSelection.currentRevision()
        val toolsChanged = activeToolFilterRevision != toolFilterRevisionProvider()
        if (!modelChanged && !toolsChanged) return null

        replaceAgentInstance()
        activeModelRevision = ModelSelection.currentRevision()
        activeToolFilterRevision = toolFilterRevisionProvider()
        return currentModel
    }

    private fun replaceAgentInstance() {
        activeContext = buildAgenticInstanceContext()
        agentInstance = buildAgentInstance(activeContext)
    }

    private fun buildAgenticInstanceContext(): AgenticInstanceContext {
        if (reloadContextBeforeBuild) {
            agentContext.policies.reloadFromPersistence()
        }
        val toolFilterProfile = toolFilterProfileProvider() ?: everythingToolFilterProfile()
        val mcpToolFilterProfile = mcpToolFilterProfileProvider() ?: McpToolFilterProfileStore.everythingProfile()
        return agentContext.withRuntimeOverrides(
            core = agentContext.core.withRuntimeOverrides(
                modelName = ModelSelection.current.name,
                type = ModelSelection.current.provider,
                systemPrompt = SystemPrompt.text(agentContext.policies),
                listeners = listenersProvider(),
            ),
            tools = agentContext.tools.withRuntimeOverrides(
                profile = toolFilterProfile,
                mcpProfile = mcpToolFilterProfile,
                shouldInterruptBeforeToolExecution = shouldInterruptBeforeToolExecution,
                shouldInterruptAfterToolExecution = agentContext.tools.shouldInterruptAfterToolExecution
            )
        )
    }

    private fun buildAgentInstance(context: AgenticInstanceContext): AgentInstance = agentInstanceFactory(context)

    private fun everythingToolFilterProfile(): ToolFilterProfile {
        return ToolFilterProfile(
            id = ToolFilterProfileStore.EVERYTHING_PROFILE_ID,
            name = "Everything",
            isBuiltIn = true,
            rules = emptyMap()
        )
    }
}
