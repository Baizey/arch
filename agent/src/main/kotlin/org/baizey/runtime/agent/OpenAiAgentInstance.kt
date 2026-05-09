package org.baizey.runtime.agent

import dev.langchain4j.memory.chat.MessageWindowChatMemory
import dev.langchain4j.model.chat.listener.ChatModelListener
import dev.langchain4j.model.openai.OpenAiChatModel
import dev.langchain4j.service.AiServices
import org.baizey.harness.HarnessContext
import org.baizey.runtime.AgentRunInterruptedException
import org.baizey.runtime.AppConfig
import org.baizey.runtime.Assistant
import org.baizey.runtime.ToolFilterProfile
import org.baizey.runtime.agentic.instance.ProviderType
import java.time.Duration

class OpenAiAgentInstance(
    override val modelName: String,
    private val systemPrompt: String,
    private val agentContext: HarnessContext,
    private val toolFilterProfile: ToolFilterProfile,
    private val listeners: List<ChatModelListener>,
    private val interrupted: () -> Boolean,
) : AgentInstance {
    override val provider: ProviderType = ProviderType.OPENAI
    private var resources = prepareRuntimeResources(agentContext, toolFilterProfile)
    private var chatMemory = buildChatMemory()
    private var assistant = buildAssistant(modelName)

    override fun chat(prompt: String): String? = assistant.chat(prompt)

    override fun displayName(): String = "$modelName (${ProviderType.OPENAI.displayName})"

    private fun buildChatMemory() = MessageWindowChatMemory.builder().maxMessages(Int.MAX_VALUE).build()

    private fun buildAssistant(modelName: String): Assistant {
        val providerConfig = AppConfig.providers.openai
        val model = OpenAiChatModel.builder()
            .apiKey(requireNotNull(providerConfig.apiKey) { "OPENAI_API_KEY is required for OpenAI chat." })
            .baseUrl(requireNotNull(providerConfig.baseUrl) { "OPENAI_BASE_URL is required for OpenAI chat." })
            .modelName(modelName)
            .timeout(Duration.ofMinutes(2))
            .listeners(listeners)
            .build()

        val builder = AiServices.builder(Assistant::class.java)
        builder.chatModel(model)
        builder.chatMemory(chatMemory)
        builder.tools(resources.tools)
        builder.beforeToolExecution { _ ->
            if (interrupted()) {
                throw AgentRunInterruptedException("Interrupted before tool execution.")
            }
        }
        builder.systemMessageProvider { _ -> systemPrompt }

        if (resources.mcpToolProvider != null) {
            builder.toolProvider(resources.mcpToolProvider)
        }

        return builder.build()
    }
}
