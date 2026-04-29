package org.baizey.runtime.agent

import dev.langchain4j.mcp.McpToolProvider
import dev.langchain4j.model.chat.listener.ChatModelListener
import org.baizey.harness.HarnessContext
import org.baizey.runtime.*


interface AgentInstance {
    companion object {
        fun create(config: AgentConfig, provider: AgentProviderConfig): AgentInstance {
            when (provider) {
                is OpenAiConfig -> TODO()
                is OllamaConfig -> {
                    return OllamaAgentInstance(
                        config.modelName,
                        config.systemPrompt,
                        config.context,
                        config.toolFilterProfile,
                        config.listeners,
                        config.shouldInterruptBeforeToolExecution
                    )
                }

                else -> throw IllegalStateException("Unsupported provider type: ${provider::class.simpleName}")
            }
        }
    }

    fun chat(prompt: String): String?
    fun modelName(): String
}

data class RuntimeResources(
    val tools: List<Any>,
    val mcpToolProvider: McpToolProvider?
)

data class AgentConfig(
    val modelName: String,
    val systemPrompt: String,
    val tools: List<Any>,
    val context: HarnessContext,
    val toolFilterProfile: ToolFilterProfile,
    val listeners: List<ChatModelListener>,
    val shouldInterruptBeforeToolExecution: () -> Boolean = { false }
)