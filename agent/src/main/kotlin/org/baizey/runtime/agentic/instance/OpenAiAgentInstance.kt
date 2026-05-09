package org.baizey.runtime.agentic.instance

import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.openai.OpenAiChatModel
import org.baizey.runtime.AppConfig
import java.time.Duration

class OpenAiAgentInstance(context: AgenticInstanceContext) : AgentInstance(context) {
    override fun buildModel(): ChatModel {
        val core = context.core
        val providerConfig = AppConfig.providers.openai
        val model = OpenAiChatModel.builder()
            .apiKey(requireNotNull(providerConfig.apiKey) { "OPENAI_API_KEY is required for OpenAI chat." })
            .baseUrl(requireNotNull(providerConfig.baseUrl) { "OPENAI_BASE_URL is required for OpenAI chat." })
            .modelName(core.modelName)
            .timeout(Duration.ofMinutes(2))
            .listeners(core.listeners)
            .build()
        return model
    }
}
