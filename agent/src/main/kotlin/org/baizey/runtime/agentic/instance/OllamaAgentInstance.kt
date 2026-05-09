package org.baizey.runtime.agentic.instance

import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.ollama.OllamaChatModel
import org.baizey.runtime.AppConfig
import java.time.Duration

class OllamaAgentInstance(context: AgenticInstanceContext) : AgentInstance(context) {
    override fun buildModel(): ChatModel {
        val coreContext = context.core
        return OllamaChatModel.builder()
            .baseUrl(AppConfig.providers.ollama.baseUrl)
            .modelName(coreContext.modelName)
            .timeout(Duration.ofMinutes(2))
            .returnThinking(true)
            .maxRetries(3)
            .listeners(coreContext.listeners)
            .build()
    }
}
