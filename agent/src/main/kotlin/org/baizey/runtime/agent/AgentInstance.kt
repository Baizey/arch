package org.baizey.runtime.agent

import dev.langchain4j.mcp.McpToolProvider
import dev.langchain4j.model.chat.listener.ChatModelListener
import org.baizey.harness.HarnessContext
import org.baizey.runtime.ToolFilterProfile
import org.baizey.runtime.agentic.instance.ProviderType

interface AgentInstance {
    companion object {
        fun create(config: AgentConfig): AgentInstance {
            return when (config.provider) {
                ProviderType.OPENAI -> OpenAiAgentInstance(
                    config.modelName,
                    config.systemPrompt,
                    config.context,
                    config.toolFilterProfile,
                    config.listeners,
                    config.shouldInterruptBeforeToolExecution
                )
                ProviderType.OLLAMA -> OllamaAgentInstance(
                    config.modelName,
                    config.systemPrompt,
                    config.context,
                    config.toolFilterProfile,
                    config.listeners,
                    config.shouldInterruptBeforeToolExecution
                )
            }
        }
    }

    val provider: ProviderType
    val modelName: String

    fun chat(prompt: String): String?
    fun displayName(): String
}

data class RuntimeResources(
    val tools: List<Any>,
    val mcpToolProvider: McpToolProvider?
)

data class AgentConfig(
    val modelName: String,
    val provider: ProviderType,
    val systemPrompt: String,
    val tools: List<Any>,
    val context: HarnessContext,
    val toolFilterProfile: ToolFilterProfile,
    val listeners: List<ChatModelListener>,
    val shouldInterruptBeforeToolExecution: () -> Boolean = { false }
)
