package org.baizey.runtime

import dev.langchain4j.mcp.McpToolProvider
import dev.langchain4j.mcp.client.DefaultMcpClient
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport
import dev.langchain4j.memory.chat.MessageWindowChatMemory
import dev.langchain4j.model.chat.listener.ChatModelListener
import dev.langchain4j.model.ollama.OllamaChatModel
import dev.langchain4j.service.AiServices
import org.baizey.commands.utils.ModelSelection
import org.baizey.harness.HarnessContext
import org.baizey.harness.HarnessRuntime
import org.baizey.harness.tools.AgentTools
import org.baizey.harness.SystemPrompt
import org.baizey.utils.IO.fromJson
import org.baizey.utils.IO.readIfExists
import java.time.Duration
import kotlin.collections.listOf

class AgentRuntime(
    private val toolContext: HarnessContext,
    private val shouldInterruptBeforeToolExecution: () -> Boolean = { false },
    private val listenersProvider: () -> List<ChatModelListener>,
    private val toolFilterRevisionProvider: () -> Int = { 0 },
    private val toolFilterProfileProvider: () -> ToolFilterProfile? = { null }
) : HarnessRuntime {
    private var resources = prepareResources()
    private var activeModelRevision = ModelSelection.currentRevision()
    private var activeToolFilterRevision = toolFilterRevisionProvider()
    private var chatMemory = buildChatMemory()
    private var assistant = buildAssistant(ModelSelection.current())

    override val currentModel: String get() = ModelSelection.current()

    override val builtInToolCount: Int get() = resources.tools.size

    override fun chat(prompt: String): String? = assistant.chat(prompt)

    override fun resetConversation() {
        resources = prepareResources()
        chatMemory = buildChatMemory()
        assistant = buildAssistant(ModelSelection.current())
        activeModelRevision = ModelSelection.currentRevision()
        activeToolFilterRevision = toolFilterRevisionProvider()
    }

    override fun refreshIfNeeded(): String? {
        val modelChanged = activeModelRevision != ModelSelection.currentRevision()
        val toolsChanged = activeToolFilterRevision != toolFilterRevisionProvider()
        if (!modelChanged && !toolsChanged) return null

        resources = prepareResources()
        assistant = buildAssistant(ModelSelection.current())
        activeModelRevision = ModelSelection.currentRevision()
        activeToolFilterRevision = toolFilterRevisionProvider()
        return ModelSelection.current()
    }

    private fun buildAssistant(modelName: String): Assistant {
        val model = OllamaChatModel.builder()
            .baseUrl(AppConfig.ollama.baseUrl)
            .modelName(modelName)
            .timeout(Duration.ofMinutes(2))
            .returnThinking(true)
            .listeners(listenersProvider())
            .build()

        val builder = AiServices.builder(Assistant::class.java)
            .chatModel(model)
            .chatMemory(chatMemory)
            .tools(resources.tools)
            .beforeToolExecution {
                if (shouldInterruptBeforeToolExecution()) {
                    throw AgentRunInterruptedException("Interrupted before tool execution.")
                }
            }
            .systemMessageProvider { _ -> SystemPrompt.text(toolContext) }

        if (resources.mcpToolProvider != null) {
            builder.toolProvider(resources.mcpToolProvider)
        }

        return builder.build()
    }

    private fun buildChatMemory() = MessageWindowChatMemory.builder().maxMessages(Int.MAX_VALUE).build()

    private fun prepareResources(): RuntimeResources {
        toolContext.reloadFromPersistence()
        val mcpConfig = SystemPath.mcpConfigFile.readIfExists()?.fromJson<McpConfig>() ?: McpConfig(mapOf())
        val tools = AgentTools.create(toolContext, toolFilterProfileProvider())
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

    private data class RuntimeResources(
        val tools: List<Any>,
        val mcpToolProvider: McpToolProvider?
    )
}

class AgentRunInterruptedException(
    message: String
) : RuntimeException(message)
