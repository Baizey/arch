package org.baizey.runtime.agent

import dev.langchain4j.mcp.McpToolProvider
import dev.langchain4j.mcp.client.DefaultMcpClient
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport
import dev.langchain4j.memory.chat.MessageWindowChatMemory
import dev.langchain4j.model.chat.listener.ChatModelListener
import dev.langchain4j.model.ollama.OllamaChatModel
import dev.langchain4j.service.AiServices
import org.baizey.harness.HarnessContext
import org.baizey.harness.tools.AgentTools
import org.baizey.runtime.AgentRunInterruptedException
import org.baizey.runtime.AppConfig
import org.baizey.runtime.Assistant
import org.baizey.runtime.McpConfig
import org.baizey.runtime.SystemPath
import org.baizey.runtime.ToolFilterProfile
import org.baizey.utils.IO.fromJson
import org.baizey.utils.IO.readIfExists
import java.time.Duration

class OllamaAgentInstance(
    private val modelName: String,
    private val systemPrompt: String,
    private val agentContext: HarnessContext,
    private val toolFilterProfile: ToolFilterProfile,
    private val listeners: List<ChatModelListener>,
    private val interrupted: () -> Boolean
) : AgentInstance {
    private var resources = prepareResources()
    private var chatMemory = buildChatMemory()
    private var assistant = buildAssistant(modelName)

    override fun chat(prompt: String): String? = assistant.chat(prompt)

    override fun modelName(): String = "$modelName [ollama]"

    private fun buildChatMemory() = MessageWindowChatMemory.builder().maxMessages(Int.MAX_VALUE).build()
    private fun buildAssistant(modelName: String): Assistant {
        val model = OllamaChatModel.builder()
            .baseUrl(AppConfig.providers.ollama.baseUrl)
            .modelName(modelName)
            .timeout(Duration.ofMinutes(2))
            .returnThinking(true)
            .listeners(listeners)
            .build()

        val builder = AiServices.builder(Assistant::class.java)
            .chatModel(model)
            .chatMemory(chatMemory)
            .tools(resources.tools)
            .beforeToolExecution {
                if (interrupted()) {
                    throw AgentRunInterruptedException("Interrupted before tool execution.")
                }
            }
            .systemMessageProvider { _ -> systemPrompt }

        if (resources.mcpToolProvider != null) {
            builder.toolProvider(resources.mcpToolProvider)
        }

        return builder.build()
    }

    private fun prepareResources(): RuntimeResources {
        agentContext.reloadFromPersistence()
        val mcpConfig = SystemPath.mcpConfigFile.readIfExists()?.fromJson<McpConfig>() ?: McpConfig(mapOf())
        val tools = AgentTools.create(agentContext, toolFilterProfile)
        val mcpClients = mcpConfig.servers.entries.map { (key, value) ->
            DefaultMcpClient.builder()
                .key(key)
                .transport(
                    StdioMcpTransport.builder()
                        .command(listOf(value.command) + value.args)
                        .environment(value.env)
                        .logEvents(true)
                        .build()
                )
                .build()
        }

        val mcpToolProvider = if (mcpClients.isEmpty()) {
            null
        } else {
            McpToolProvider.builder().mcpClients(mcpClients).build()
        }

        return RuntimeResources(
            tools = tools,
            mcpToolProvider = mcpToolProvider
        )
    }

}