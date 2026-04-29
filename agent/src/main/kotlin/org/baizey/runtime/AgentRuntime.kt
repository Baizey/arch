package org.baizey.runtime

import dev.langchain4j.model.chat.listener.ChatModelListener
import org.baizey.commands.utils.ModelSelection
import org.baizey.harness.HarnessContext
import org.baizey.harness.HarnessRuntime
import org.baizey.harness.SystemPrompt
import org.baizey.harness.tools.AgentTools
import org.baizey.runtime.agent.AgentConfig
import org.baizey.runtime.agent.AgentInstance

class AgentRuntime(
    private val agentContext: HarnessContext,
    private val shouldInterruptBeforeToolExecution: () -> Boolean = { false },
    private val listenersProvider: () -> List<ChatModelListener>,
    private val toolFilterRevisionProvider: () -> Int = { 0 },
    private val toolFilterProfileProvider: () -> ToolFilterProfile? = { null },
    private val reloadContextBeforeBuild: Boolean = true,
    private val agentInstanceFactory: (AgentConfig) -> AgentInstance = { AgentInstance.create(it) }
) : HarnessRuntime {
    private var activeModelRevision = ModelSelection.currentRevision()
    private var activeToolFilterRevision = toolFilterRevisionProvider()
    private var activeConfig = buildAgentConfig()
    private var agentInstance = buildAgentInstance(activeConfig)

    override val currentModel: String get() = agentInstance.displayName()

    override val builtInToolCount: Int get() = activeConfig.tools.size

    override fun chat(prompt: String): String? = agentInstance.chat(prompt)

    override fun resetConversation() {
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
        activeConfig = buildAgentConfig()
        agentInstance = buildAgentInstance(activeConfig)
    }

    private fun buildAgentConfig(): AgentConfig {
        if (reloadContextBeforeBuild) {
            agentContext.reloadFromPersistence()
        }
        val toolFilterProfile = toolFilterProfileProvider() ?: everythingToolFilterProfile()
        val tools = AgentTools.create(agentContext, toolFilterProfile)
        return AgentConfig(
            modelName = ModelSelection.current.name,
            provider = ModelSelection.current.provider,
            systemPrompt = SystemPrompt.text(agentContext),
            tools = tools,
            context = agentContext,
            toolFilterProfile = toolFilterProfile,
            listeners = listenersProvider(),
            shouldInterruptBeforeToolExecution = shouldInterruptBeforeToolExecution
        )
    }

    private fun buildAgentInstance(config: AgentConfig): AgentInstance = agentInstanceFactory(config)

    private fun everythingToolFilterProfile(): ToolFilterProfile {
        return ToolFilterProfile(
            id = ToolFilterProfileStore.EVERYTHING_PROFILE_ID,
            name = "Everything",
            isBuiltIn = true,
            rules = emptyMap()
        )
    }
}

class AgentRunInterruptedException(
    message: String
) : RuntimeException(message)
