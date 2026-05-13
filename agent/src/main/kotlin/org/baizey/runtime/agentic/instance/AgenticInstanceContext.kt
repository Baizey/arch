package org.baizey.runtime.agentic.instance

import dev.langchain4j.agent.tool.ToolSpecifications
import dev.langchain4j.mcp.McpToolProvider
import dev.langchain4j.memory.chat.MessageWindowChatMemory
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.chat.listener.ChatModelListener
import dev.langchain4j.service.AiServices
import org.baizey.harness.HarnessInteractionPort
import org.baizey.harness.policy.GitPolicyLogic
import org.baizey.harness.policy.PathPolicyLogic
import org.baizey.harness.tools.AgentTools
import org.baizey.runtime.*
import org.baizey.runtime.agentic.instance.exceptions.AgentRunInterruptedException
import org.baizey.utils.IO.fromJson
import org.baizey.utils.IO.readIfExists
import java.util.*

abstract class AgentInstance(val context: AgenticInstanceContext) {
    val isNewSession: Boolean = context.core.sessionId == null
    val sessionId: UUID = context.core.sessionId ?: UUID.randomUUID()
    val chatMemory: MessageWindowChatMemory = buildChatMemory()
    val resources: RuntimeResources = context.prepareRuntimeResources()
    val assistant: Assistant = buildAssistant()

    abstract fun buildModel(): ChatModel

    fun chat(prompt: String): String? = assistant.chat(prompt)

    fun displayName(): String = "${context.core.modelName} (${context.core.type.displayName})"

    fun builtInToolCount(): Int = resources.tools.size

    fun storeChatMemory() {
        TODO("Not yet implemented")
    }

    private fun buildAssistant(): Assistant {
        val builder = AiServices.builder(Assistant::class.java)
            .chatModel(buildModel())
            .systemMessageProvider { context.core.systemPrompt }
            .chatMemory(chatMemory)
            .tools(resources.tools)
            .beforeToolExecution {
                if (context.tools.shouldInterruptBeforeToolExecution()) {
                    throw AgentRunInterruptedException("Interrupted before tool execution.")
                }
            }
            .afterToolExecution {
                if (context.tools.shouldInterruptAfterToolExecution()) {
                    throw AgentRunInterruptedException("Interrupted after tool execution.")
                }
            }
        if (resources.mcpToolProvider != null) {
            builder.toolProvider(resources.mcpToolProvider)
        }
        return builder.build()
    }

    private fun buildChatMemory(): MessageWindowChatMemory {
        if (isNewSession) {
            return MessageWindowChatMemory.builder().maxMessages(Int.MAX_VALUE).build()
        }
        TODO("Load old session state from disk.")
    }

    companion object {
        fun create(context: AgenticInstanceContext): AgentInstance {
            return when (context.core.type) {
                ProviderType.OPENAI -> OpenAiAgentInstance(context)
                ProviderType.OLLAMA -> OllamaAgentInstance(context)
            }
        }
    }

}

data class AgenticInstanceContext(
    val core: CoreContext,
    val policies: PolicyContext,
    val tools: ToolContext
) {

    fun createSubAgentContext(subAgentContext: AgenticInstanceContext): AgenticInstanceContext {
        // We need to take the given context and 'merge' it with the current context.
        // We should return something that only turns the "positive combo", eg only tools allowed in both, only policies allowed in both
        // We should return the non-user version of the policy instances, where any not-pre-approved is auto-denied
        TODO("Not yet implemented")
    }

    fun prepareRuntimeResources(): RuntimeResources {
        policies.reloadFromPersistence()
        val mcpConfig = SystemPath.mcpConfigFile.readIfExists()?.fromJson<McpConfig>() ?: McpConfig(mapOf())
        val tools = AgentTools.create(this)
        val registeredToolNames = tools
            .flatMap { tool -> ToolSpecifications.toolSpecificationsFrom(tool).map { it.name() } }
            .toSet()
        val mcpClients = mcpConfig.servers.entries.map { (key, value) ->
            McpToolCatalog.buildMcpClient(key, value)
        }
        val mcpToolProvider = if (mcpClients.isEmpty()) {
            null
        } else {
            McpToolProvider.builder()
                .mcpClients(mcpClients)
                .filter { client, tool ->
                    tool.name() !in registeredToolNames &&
                        McpToolCatalog.isEnabled(client.key(), tool.name(), this.tools.mcpProfile)
                }
                .build()
        }
        return RuntimeResources(
            tools = tools,
            mcpToolProvider = mcpToolProvider
        )
    }

    fun withRuntimeOverrides(
        core: CoreContext,
        tools: ToolContext
    ): AgenticInstanceContext {
        return AgenticInstanceContext(
            core = core,
            policies = policies,
            tools = tools
        )
    }
}

data class RuntimeResources(
    val tools: List<Any>,
    val mcpToolProvider: McpToolProvider?
)

data class ToolContext(
    val profile: ToolFilterProfile,
    val mcpProfile: McpToolFilterProfile = McpToolFilterProfileStore.everythingProfile(),
    val onPathPolicyChanged: () -> Unit = {},
    val shouldInterruptBeforeToolExecution: () -> Boolean = { false },
    val shouldInterruptAfterToolExecution: () -> Boolean = { false }
) {
    fun withRuntimeOverrides(
        profile: ToolFilterProfile,
        mcpProfile: McpToolFilterProfile,
        shouldInterruptBeforeToolExecution: () -> Boolean,
        shouldInterruptAfterToolExecution: () -> Boolean
    ): ToolContext {
        return ToolContext(
            profile = profile,
            mcpProfile = mcpProfile,
            onPathPolicyChanged = onPathPolicyChanged,
            shouldInterruptBeforeToolExecution = shouldInterruptBeforeToolExecution,
            shouldInterruptAfterToolExecution = shouldInterruptAfterToolExecution
        )
    }
}

data class PolicyContext(
    val path: PathPolicyLogic,
    val git: GitPolicyLogic
) {
    fun reloadFromPersistence() {
        path.reloadFromPersistence()
        git.reloadFromPersistence()
    }
}

data class CoreContext(
    val sessionId: UUID? = null,
    val systemPrompt: String,
    val type: ProviderType,
    val modelName: String,
    val listeners: List<ChatModelListener>,
    val userInteraction: HarnessInteractionPort
) {
    fun withRuntimeOverrides(
        systemPrompt: String,
        type: ProviderType,
        modelName: String,
        listeners: List<ChatModelListener>
    ): CoreContext {
        return CoreContext(
            sessionId = sessionId,
            systemPrompt = systemPrompt,
            type = type,
            modelName = modelName,
            listeners = listeners,
            userInteraction = userInteraction
        )
    }
}
