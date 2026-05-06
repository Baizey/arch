package org.baizey.harness

import dev.langchain4j.data.message.ChatMessageType
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.ToolExecutionResultMessage
import dev.langchain4j.model.chat.listener.ChatModelErrorContext
import dev.langchain4j.model.chat.listener.ChatModelListener
import dev.langchain4j.model.chat.listener.ChatModelRequestContext
import dev.langchain4j.model.chat.listener.ChatModelResponseContext
import kotlinx.serialization.Serializable
import org.baizey.commands.utils.ModelSelection
import org.baizey.commands.utils.ModelSelectionResult
import org.baizey.harness.policy.PolicyCollection
import org.baizey.harness.policy.UserGitPolicyLogic
import org.baizey.harness.policy.UserPathPolicyLogic
import org.baizey.runtime.AgentRunInterruptedException
import org.baizey.runtime.AgentRuntime
import org.baizey.runtime.ToolFilterProfile
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

@Serializable
enum class HarnessActivityType(
    val wireName: String
) {
    SYSTEM("system"),
    CONTROL("control"),
    THINKING("thinking"),
    TOOL_REQUEST("tool-request"),
    TOOL_RESULT("tool-result"),
    ERROR("error"),
    CUSTOM("custom")
}

@Serializable
data class HarnessChatEntry(
    val id: Long,
    val role: String,
    val text: String,
    val timestampMs: Long
)

@Serializable
data class HarnessActivityEntry(
    val id: Long,
    val type: HarnessActivityType,
    val title: String,
    val detail: String,
    val timestampMs: Long,
    val correlationId: String? = null
)

@Serializable
data class HarnessSnapshot(
    val running: Boolean,
    val selectedModelId: String,
    val modelLabel: String,
    val supportedModels: List<HarnessSupportedModel>,
    val activeContextSize: Int,
    val messages: List<HarnessChatEntry>,
    val activity: List<HarnessActivityEntry>,
    val pendingMessages: List<PendingHarnessMessage>
)

@Serializable
data class HarnessSupportedModel(
    val id: String,
    val name: String,
    val provider: String,
    val providerLabel: String
)

@Serializable
data class PendingHarnessMessage(
    val id: String,
    val text: String,
    val createdAtMs: Long
)

data class SubmitMessageResult(
    val ok: Boolean,
    val message: String
)

interface HarnessRuntime {
    val currentModel: String
    val builtInToolCount: Int

    fun chat(prompt: String): String?

    fun resetConversation()

    fun refreshIfNeeded(): String?
}

class HarnessSession(
    interactionPort: HarnessInteractionPort,
    runtimeFactory: (HarnessContext, () -> Boolean, () -> List<ChatModelListener>, () -> Int, () -> ToolFilterProfile?) -> HarnessRuntime =
        { toolContext, shouldInterruptBeforeToolExecution, listenersProvider, toolFilterRevisionProvider, toolFilterProfileProvider ->
            AgentRuntime(
                agentContext = toolContext,
                shouldInterruptBeforeToolExecution = shouldInterruptBeforeToolExecution,
                listenersProvider = listenersProvider,
                toolFilterRevisionProvider = toolFilterRevisionProvider,
                toolFilterProfileProvider = toolFilterProfileProvider
            )
        }
) {
    private val toolContext = HarnessContext(
        interactionPort = interactionPort,
        policies = PolicyCollection(
            git = UserGitPolicyLogic(interactionPort),
            path = UserPathPolicyLogic(interactionPort)
        )
    )
    private val lock = Any()
    private val ids = AtomicLong(0)
    private val messages = mutableListOf<HarnessChatEntry>()
    private val activity = mutableListOf<HarnessActivityEntry>()
    private val pendingMessages = ArrayDeque<PendingHarnessMessage>()
    private val runtime = runtimeFactory(
        toolContext,
        ::shouldInterruptCurrentRun,
        ::buildListeners,
        ::currentToolFilterRevision,
        ::currentToolFilterProfile
    )

    @Volatile
    private var running = false

    @Volatile
    private var stopAtNextBreakRequested = false

    @Volatile
    private var activeContextSize = 0

    @Volatile
    private var clearContextRequested = false

    @Volatile
    private var interruptRequested = false

    @Volatile
    private var toolFilterRevision = 0

    @Volatile
    private var toolFilterProfile: ToolFilterProfile? = null

    init {
        recordActivity(
            type = HarnessActivityType.SYSTEM,
            title = "Session ready",
            detail = "Model ${runtime.currentModel} with ${runtime.builtInToolCount} built-in tools."
        )
    }

    fun snapshot(): HarnessSnapshot {
        synchronized(lock) {
            return HarnessSnapshot(
                running = running,
                selectedModelId = ModelSelection.current.id,
                modelLabel = runtime.currentModel,
                supportedModels = ModelSelection.supportedModels.map { model ->
                    HarnessSupportedModel(
                        id = model.id,
                        name = model.name,
                        provider = model.provider.name.lowercase(),
                        providerLabel = model.provider.displayName
                    )
                },
                activeContextSize = activeContextSize,
                messages = messages.toList(),
                activity = activity.toList(),
                pendingMessages = pendingMessages.toList()
            )
        }
    }

    fun submitUserMessage(text: String): SubmitMessageResult {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return SubmitMessageResult(
                ok = false,
                message = "Message cannot be empty."
            )
        }

        synchronized(lock) {
            if (running) {
                val existingPendingMessage = if (pendingMessages.isEmpty()) null else pendingMessages.removeLast()
                if (existingPendingMessage != null) {
                    pendingMessages.addLast(
                        existingPendingMessage.copy(
                            text = "${existingPendingMessage.text}\n$trimmed"
                        )
                    )
                    interruptRequested = true
                    return SubmitMessageResult(
                        ok = true,
                        message = "Message appended to queued interrupting message."
                    )
                }
                pendingMessages.addLast(
                    PendingHarnessMessage(
                        id = UUID.randomUUID().toString(),
                        text = trimmed,
                        createdAtMs = now()
                    )
                )
                interruptRequested = true
                return SubmitMessageResult(
                    ok = true,
                    message = "Message queued and interrupt requested."
                )
            }
            running = true
            stopAtNextBreakRequested = false
            interruptRequested = false
            messages += HarnessChatEntry(nextId(), "user", trimmed, now())
        }

        Thread.ofVirtual().name("harness-session").start {
            runConversation(trimmed)
        }
        return SubmitMessageResult(
            ok = true,
            message = "Message sent."
        )
    }

    fun cancelPendingMessage(id: String): Boolean {
        synchronized(lock) {
            val iterator = pendingMessages.iterator()
            while (iterator.hasNext()) {
                if (iterator.next().id == id) {
                    iterator.remove()
                    if (pendingMessages.isEmpty()) {
                        interruptRequested = false
                    }
                    return true
                }
            }
        }
        return false
    }

    fun clearContext(): String {
        synchronized(lock) {
            pendingMessages.clear()
            interruptRequested = false
            if (running) {
                requestStopAtNextBreak(
                    title = "Context clear requested",
                    detail = "The current step cannot be cancelled mid-call. Context will clear before the next continue cycle."
                )
                clearContextRequested = true
                return "Context clear requested. Waiting for the current step to reach a break."
            }
        }

        resetConversationState()
        return "Context cleared."
    }

    fun setToolFilterProfile(profile: ToolFilterProfile) {
        synchronized(lock) {
            toolFilterProfile = profile
            toolFilterRevision++
        }
        recordActivity(
            type = HarnessActivityType.CONTROL,
            title = "Tool filter switched to ${profile.name}",
            detail = "Built-in tool availability will refresh on the next model turn."
        )
    }

    fun setModel(model: String): String {
        if (!running) {
            runtime.refreshIfNeeded()
        }
        val selectionResult = ModelSelection.select(model)
        val message = when (selectionResult) {
            is ModelSelectionResult.Changed -> {
                recordActivity(
                    type = HarnessActivityType.CONTROL,
                    title = "Model switched to ${selectionResult.model.label}",
                    detail = ""
                )
                "Model switched to ${selectionResult.model.label}"
            }

            is ModelSelectionResult.Unchanged -> {
                "Model already set to ${selectionResult.model.label}"
            }

            is ModelSelectionResult.Unknown -> {
                "Unknown model '${selectionResult.requested}'. Available models: ${
                    selectionResult.supportedModels.joinToString(
                        ", "
                    ) { supportedModel -> supportedModel.label }
                }"
            }
        }
        return message
    }

    fun requestStopForShutdown() {
        requestStopAtNextBreak(
            title = "Shutdown requested",
            detail = "The current step cannot be cancelled mid-call. Shutdown will continue before the next continue cycle."
        )
    }

    private fun runConversation(initialPrompt: String) {
        var nextPrompt: String? = initialPrompt
        try {
            while (nextPrompt != null) {
                runSingleConversation(nextPrompt)
                nextPrompt = resolveNextPromptAfterBreak()
            }
        } catch (e: Exception) {
            recordActivity(
                type = HarnessActivityType.ERROR,
                title = "Session error",
                detail = e.message ?: "Unknown error"
            )
        } finally {
            synchronized(lock) {
                running = false
                stopAtNextBreakRequested = false
            }
        }
    }

    private fun runSingleConversation(initialPrompt: String) {
        var latestPrompt = initialPrompt
        while (true) {
            if (stopAtNextBreakRequested) {
                recordActivity(
                    type = HarnessActivityType.CONTROL,
                    title = "Run stopped",
                    detail = "Stopped before sending another continue prompt to the model."
                )
                return
            }
            runtime.refreshIfNeeded()
            val agentResponse = try {
                runtime.chat(latestPrompt)
            } catch (_: AgentRunInterruptedException) {
                recordActivity(
                    type = HarnessActivityType.CONTROL,
                    title = "Run interrupted",
                    detail = "A newer user message preempted the current run before the next tool execution."
                )
                latestPrompt = dequeuePendingMessageTextAndRecordAsUserMessage()
                    ?: "continue; if you think you're done say so."
                continue
            }
            if (consumeInterruptIfPendingMessageExists()) {
                recordActivity(
                    type = HarnessActivityType.CONTROL,
                    title = "Run interrupted",
                    detail = "A newer user message preempted the current run before the assistant response was committed."
                )
                latestPrompt = dequeuePendingMessageTextAndRecordAsUserMessage()
                    ?: "continue; if you think you're done say so."
                continue
            }
            if (agentResponse != null && agentResponse != "null" && agentResponse != "") {
                synchronized(lock) {
                    messages += HarnessChatEntry(nextId(), SystemPrompt.agentName, agentResponse, now())
                }
                return
            }
            if (stopAtNextBreakRequested) {
                recordActivity(
                    type = HarnessActivityType.CONTROL,
                    title = "Run stopped",
                    detail = "The model yielded control before a final answer, so the run stopped cleanly."
                )
                return
            }
            latestPrompt = dequeuePendingMessageTextAndRecordAsUserMessage()
                ?: "continue; if you think you're done say so."
        }
    }

    private fun resolveNextPromptAfterBreak(): String? {
        if (consumeClearContextRequested()) {
            resetConversationState()
            return null
        }
        synchronized(lock) {
            stopAtNextBreakRequested = false
            interruptRequested = false
            val pendingMessage = if (pendingMessages.isEmpty()) null else pendingMessages.removeFirst()
            if (pendingMessage == null) {
                running = false
                return null
            }
            messages += HarnessChatEntry(nextId(), "user", pendingMessage.text, now())
            return pendingMessage.text
        }
    }

    private fun consumeClearContextRequested(): Boolean {
        synchronized(lock) {
            val shouldClear = clearContextRequested
            clearContextRequested = false
            return shouldClear
        }
    }

    private fun dequeuePendingMessageTextAndRecordAsUserMessage(): String? {
        synchronized(lock) {
            val pendingMessage = if (pendingMessages.isEmpty()) null else pendingMessages.removeFirst()
            if (pendingMessage == null) {
                interruptRequested = false
                return null
            }
            interruptRequested = false
            messages += HarnessChatEntry(nextId(), "user", pendingMessage.text, now())
            return pendingMessage.text
        }
    }

    private fun resetConversationState() {
        runtime.resetConversation()
        synchronized(lock) {
            running = false
            stopAtNextBreakRequested = false
            clearContextRequested = false
            interruptRequested = false
            activeContextSize = 0
            messages.clear()
            activity.clear()
            pendingMessages.clear()
        }
        recordActivity(
            type = HarnessActivityType.SYSTEM,
            title = "Session ready",
            detail = "Model ${runtime.currentModel} with ${runtime.builtInToolCount} built-in tools."
        )
    }

    private fun recordActivity(
        type: HarnessActivityType,
        title: String,
        detail: String,
        correlationId: String? = null
    ) {
        synchronized(lock) {
            activity += HarnessActivityEntry(
                id = nextId(),
                type = type,
                title = title,
                detail = detail,
                timestampMs = now(),
                correlationId = correlationId
            )
        }
    }

    private fun requestStopAtNextBreak(
        title: String,
        detail: String
    ) {
        stopAtNextBreakRequested = true
        recordActivity(
            type = HarnessActivityType.CONTROL,
            title = title,
            detail = detail
        )
    }

    private fun updateActiveContextSize(nextActiveContextSize: Int) {
        if (nextActiveContextSize <= 0) {
            return
        }
        synchronized(lock) {
            activeContextSize = nextActiveContextSize
        }
    }

    private fun shouldInterruptCurrentRun(): Boolean {
        synchronized(lock) {
            return interruptRequested && pendingMessages.isNotEmpty()
        }
    }

    private fun currentToolFilterRevision(): Int = toolFilterRevision

    private fun currentToolFilterProfile(): ToolFilterProfile? = toolFilterProfile

    private fun consumeInterruptIfPendingMessageExists(): Boolean {
        synchronized(lock) {
            if (!interruptRequested || pendingMessages.isEmpty()) {
                return false
            }
            interruptRequested = false
            return true
        }
    }

    private fun buildListeners(): List<ChatModelListener> = listOf(HarnessModelListener())

    private fun nextId(): Long = ids.incrementAndGet()

    private fun now(): Long = System.currentTimeMillis()

    private inner class HarnessModelListener : ChatModelListener {
        private var systemSeen = false

        override fun onRequest(context: ChatModelRequestContext) {
            val messages = context.chatRequest().messages()

            // Record all trailing results (AiServices sends them in batches after tools run)
            messages.takeLastWhile { it.type() == ChatMessageType.TOOL_EXECUTION_RESULT }
                .forEach { rawMessage ->
                    val message = rawMessage as ToolExecutionResultMessage
                    recordActivity(
                        type = HarnessActivityType.TOOL_RESULT,
                        title = "Tool used: ${message.toolName()}",
                        detail = message.text(),
                        correlationId = message.id()
                    )
                }

            val lastMessage = messages.lastOrNull() ?: return
            if (lastMessage.type() == ChatMessageType.SYSTEM && !systemSeen) {
                val message = lastMessage as SystemMessage
                systemSeen = true
                recordActivity(
                    type = HarnessActivityType.SYSTEM,
                    title = "System prompt loaded",
                    detail = "${message.text().length} characters."
                )
            } else if (lastMessage.type() == ChatMessageType.CUSTOM) {
                recordActivity(
                    type = HarnessActivityType.CUSTOM,
                    title = "Custom message",
                    detail = "Custom model event."
                )
            }
        }

        override fun onResponse(context: ChatModelResponseContext) {
            val tokenUsage = context.chatResponse().metadata().tokenUsage()
            val activeContext = tokenUsage.inputTokenCount()
            updateActiveContextSize(activeContext)

            val aiMessage = context.chatResponse().aiMessage()
            val thinking = aiMessage.thinking() ?: ""
            if (thinking.isNotEmpty()) {
                recordActivity(
                    type = HarnessActivityType.THINKING,
                    title = "Thinking...",
                    detail = thinking
                )
            }

            if (aiMessage.hasToolExecutionRequests()) {
                aiMessage.toolExecutionRequests().forEach { request ->
                    recordActivity(
                        type = HarnessActivityType.TOOL_REQUEST,
                        title = "Tool used: ${request.name()}",
                        detail = request.arguments(),
                        correlationId = request.id()
                    )
                }
            }
        }

        override fun onError(context: ChatModelErrorContext) {
            recordActivity(
                type = HarnessActivityType.ERROR,
                title = "Model error",
                detail = context.error().message ?: "Unknown error"
            )
        }
    }
}
