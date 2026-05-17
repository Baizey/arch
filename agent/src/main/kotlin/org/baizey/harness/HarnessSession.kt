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
import org.baizey.harness.policy.UserGitPolicyLogic
import org.baizey.harness.policy.UserPathPolicyLogic
import org.baizey.runtime.AgentRuntime
import org.baizey.runtime.AppConfig
import org.baizey.runtime.McpToolFilterProfile
import org.baizey.runtime.McpToolFilterProfileStore
import org.baizey.runtime.SessionKind
import org.baizey.runtime.SessionState
import org.baizey.runtime.SessionStateStore
import org.baizey.runtime.SessionStore
import org.baizey.runtime.ToolFilterProfile
import org.baizey.runtime.ToolFilterProfileStore
import org.baizey.runtime.agentic.instance.AgenticInstanceContext
import org.baizey.runtime.agentic.instance.CoreContext
import org.baizey.runtime.agentic.instance.PolicyContext
import org.baizey.runtime.agentic.instance.ProviderType
import org.baizey.runtime.agentic.instance.exceptions.AgentRunInterruptedException
import org.baizey.runtime.agentic.instance.SystemPrompt
import org.baizey.runtime.agentic.instance.ToolContext
import org.baizey.runtime.sandbox.DockerSandboxService
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

class HarnessSession private constructor(
    interactionPort: HarnessInteractionPort,
    private val dependencies: SessionDependencies,
    private val sandboxServiceFactory: (UserPathPolicyLogic) -> DockerSandboxService?,
    runtimeFactory: (AgenticInstanceContext, () -> Boolean, () -> List<ChatModelListener>, () -> Int, () -> ToolFilterProfile?, () -> McpToolFilterProfile?) -> HarnessRuntime
) {
    constructor(interactionPort: HarnessInteractionPort) : this(
        interactionPort = interactionPort,
        dependencies = defaultDependencies(),
        sandboxServiceFactory = defaultSandboxServiceFactory(),
        runtimeFactory = defaultRuntimeFactory()
    )

    constructor(
        interactionPort: HarnessInteractionPort,
        runtimeFactory: (AgenticInstanceContext, () -> Boolean, () -> List<ChatModelListener>, () -> Int, () -> ToolFilterProfile?, () -> McpToolFilterProfile?) -> HarnessRuntime
    ) : this(
        interactionPort = interactionPort,
        dependencies = defaultDependencies(),
        sandboxServiceFactory = defaultSandboxServiceFactory(),
        runtimeFactory = runtimeFactory
    )

    internal constructor(
        interactionPort: HarnessInteractionPort,
        sessionStore: SessionStore,
        sessionStateStore: SessionStateStore,
        sessionId: UUID,
        sandboxServiceFactory: (UserPathPolicyLogic) -> DockerSandboxService?,
        runtimeFactory: (AgenticInstanceContext, () -> Boolean, () -> List<ChatModelListener>, () -> Int, () -> ToolFilterProfile?, () -> McpToolFilterProfile?) -> HarnessRuntime
    ) : this(
        interactionPort = interactionPort,
        dependencies = SessionDependencies(sessionStore, sessionStateStore, sessionId),
        sandboxServiceFactory = sandboxServiceFactory,
        runtimeFactory = runtimeFactory
    )

    private val restoredState = dependencies.sessionStateStore.load(dependencies.sessionId)?.also { restored ->
        restored.snapshot.selectedModelId
            .takeIf { it.isNotBlank() }
            ?.let(ModelSelection::select)
    }
    private val sessionKind = restoredState?.kind ?: SessionKind.ROOT
    private val parentSessionId = restoredState?.parentSessionId
    private val rootSessionId = restoredState?.rootSessionId
    private val pathPolicyLogic = UserPathPolicyLogic(interactionPort)
    private val gitPolicyLogic = UserGitPolicyLogic(interactionPort)
    private val sandboxService = sandboxServiceFactory(pathPolicyLogic)
    private val agentContext = AgenticInstanceContext(
        core = CoreContext(
            sessionId = dependencies.sessionId,
            sessionStateStore = dependencies.sessionStateStore,
            sessionParentId = parentSessionId?.let(UUID::fromString),
            systemPrompt = restoredState?.systemPrompt.orEmpty(),
            type = ProviderType.OLLAMA,
            modelName = "unselected",
            listeners = emptyList(),
            userInteraction = interactionPort
        ),
        policies = PolicyContext(
            git = gitPolicyLogic,
            path = pathPolicyLogic
        ),
        tools = ToolContext(
            profile = ToolFilterProfileStore.everythingProfile(),
            mcpProfile = McpToolFilterProfileStore.everythingProfile(),
            sandbox = sandboxService,
            onPathPolicyChanged = {},
        )
    )
    private val lock = Any()
    private val ids = AtomicLong(0)
    private val messages = mutableListOf<HarnessChatEntry>()
    private val activity = mutableListOf<HarnessActivityEntry>()
    private val pendingMessages = ArrayDeque<PendingHarnessMessage>()
    private val runtime = runtimeFactory(
        agentContext,
        ::shouldInterruptCurrentRun,
        ::buildListeners,
        ::currentToolFilterRevision,
        ::currentToolFilterProfile,
        ::currentMcpToolFilterProfile
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

    @Volatile
    private var mcpToolFilterProfile: McpToolFilterProfile? = null

    @Volatile
    private var persistedSystemPrompt: String? = restoredState?.systemPrompt

    init {
        if (restoredState != null) {
            restoreState(restoredState)
        } else {
            recordActivity(
                type = HarnessActivityType.SYSTEM,
                title = "Session ready",
                detail = "Model ${runtime.currentModel} with ${runtime.builtInToolCount} built-in tools."
            )
        }
    }

    fun snapshot(): HarnessSnapshot {
        synchronized(lock) {
            return buildSnapshotLocked()
        }
    }

    fun sessionId(): UUID = dependencies.sessionId

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
                    persistStateLocked()
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
                persistStateLocked()
                return SubmitMessageResult(
                    ok = true,
                    message = "Message queued and interrupt requested."
                )
            }
            running = true
            stopAtNextBreakRequested = false
            interruptRequested = false
            messages += HarnessChatEntry(nextId(), "user", trimmed, now())
            persistStateLocked()
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
                    persistStateLocked()
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
            persistStateLocked()
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

    fun setToolFilterProfile(profile: ToolFilterProfile, emitActivity: Boolean = true) {
        synchronized(lock) {
            if (toolFilterProfile?.id == profile.id) {
                return
            }
            toolFilterProfile = profile
            toolFilterRevision++
            persistStateLocked()
        }
        if (emitActivity) {
            recordActivity(
                type = HarnessActivityType.CONTROL,
                title = "Tool filter switched to ${profile.name}",
                detail = "Built-in tool availability will refresh on the next model turn."
            )
        }
    }

    fun setMcpToolFilterProfile(profile: McpToolFilterProfile, emitActivity: Boolean = true) {
        synchronized(lock) {
            if (mcpToolFilterProfile?.id == profile.id) {
                return
            }
            mcpToolFilterProfile = profile
            toolFilterRevision++
            persistStateLocked()
        }
        if (emitActivity) {
            recordActivity(
                type = HarnessActivityType.CONTROL,
                title = "MCP filter switched to ${profile.name}",
                detail = "MCP tool availability will refresh on the next model turn."
            )
        }
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

    fun close() {
        sandboxService?.close()
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
                persistStateLocked()
            }
        }
    }

    private fun runSingleConversation(initialPrompt: String) {
        var latestUserMessage = initialPrompt
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
                latestUserMessage = dequeuePendingMessageTextAndRecordAsUserMessage() ?: latestUserMessage
                latestPrompt = continuePrompt(latestUserMessage)
                continue
            }
            if (consumeInterruptIfPendingMessageExists()) {
                recordActivity(
                    type = HarnessActivityType.CONTROL,
                    title = "Run interrupted",
                    detail = "A newer user message preempted the current run before the assistant response was committed."
                )
                latestUserMessage = dequeuePendingMessageTextAndRecordAsUserMessage() ?: latestUserMessage
                latestPrompt = continuePrompt(latestUserMessage)
                continue
            }
            if (agentResponse != null && agentResponse != "null" && agentResponse != "") {
                synchronized(lock) {
                    messages += HarnessChatEntry(nextId(), SystemPrompt.AGENT_NAME, agentResponse, now())
                    persistStateLocked()
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
            latestUserMessage = dequeuePendingMessageTextAndRecordAsUserMessage() ?: latestUserMessage
            latestPrompt = continuePrompt(latestUserMessage)
        }
    }

    private fun continuePrompt(lastPrompt: String): String {
        return """You have paused, this is an automated message;
The users last message was: '$lastPrompt';
If you have a final result for the user provide it, or ask any questions you need clarified, otherwise keep working on the given task.
"""
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
                persistStateLocked()
                return null
            }
            messages += HarnessChatEntry(nextId(), "user", pendingMessage.text, now())
            persistStateLocked()
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
                persistStateLocked()
                return null
            }
            interruptRequested = false
            messages += HarnessChatEntry(nextId(), "user", pendingMessage.text, now())
            persistStateLocked()
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
            persistedSystemPrompt = null
            persistStateLocked()
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
            persistStateLocked()
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
            persistStateLocked()
        }
    }

    private fun shouldInterruptCurrentRun(): Boolean {
        synchronized(lock) {
            return interruptRequested && pendingMessages.isNotEmpty()
        }
    }

    private fun currentToolFilterRevision(): Int = toolFilterRevision

    private fun currentToolFilterProfile(): ToolFilterProfile? = toolFilterProfile

    private fun currentMcpToolFilterProfile(): McpToolFilterProfile? = mcpToolFilterProfile

    private fun loadModelCatalogSnapshot(): HarnessModelCatalogSnapshot {
        return try {
            HarnessModelCatalogSnapshot(
                selectedModelId = ModelSelection.current.id,
                supportedModels = ModelSelection.supportedModels.map { model ->
                    HarnessSupportedModel(
                        id = model.id,
                        name = model.name,
                        provider = model.provider.name.lowercase(),
                        providerLabel = model.provider.displayName
                    )
                }
            )
        } catch (_: Throwable) {
            HarnessModelCatalogSnapshot(
                selectedModelId = runtime.currentModel,
                supportedModels = emptyList()
            )
        }
    }

    private fun consumeInterruptIfPendingMessageExists(): Boolean {
        synchronized(lock) {
            if (!interruptRequested || pendingMessages.isEmpty()) {
                return false
            }
            interruptRequested = false
            return true
        }
    }

    private fun restoreState(state: SessionState) {
        synchronized(lock) {
            running = false
            stopAtNextBreakRequested = false
            clearContextRequested = false
            interruptRequested = false
            activeContextSize = state.snapshot.activeContextSize
            messages.clear()
            messages += state.snapshot.messages
            activity.clear()
            activity += state.snapshot.activity
            pendingMessages.clear()
            pendingMessages.addAll(state.snapshot.pendingMessages)
            val maxId = maxOf(
                messages.maxOfOrNull { it.id } ?: 0L,
                activity.maxOfOrNull { it.id } ?: 0L
            )
            ids.set(maxId)
            if (state.snapshot.running) {
                activity += HarnessActivityEntry(
                    id = nextId(),
                    type = HarnessActivityType.CONTROL,
                    title = "Previous run interrupted",
                    detail = "The process stopped while a run was in progress. Continue from the restored session state.",
                    timestampMs = now()
                )
            }
            persistStateLocked()
        }
    }

    private fun persistState() {
        synchronized(lock) {
            persistStateLocked()
        }
    }

    private fun persistStateLocked() {
        dependencies.sessionStateStore.save(
            SessionState(
                sessionId = dependencies.sessionId.toString(),
                kind = sessionKind,
                parentSessionId = parentSessionId,
                rootSessionId = rootSessionId,
                updatedAtMs = now(),
                systemPrompt = persistedSystemPrompt,
                snapshot = buildSnapshotLocked()
            )
        )
    }

    private fun buildSnapshotLocked(): HarnessSnapshot {
        val modelCatalogSnapshot = loadModelCatalogSnapshot()
        return HarnessSnapshot(
            running = running,
            selectedModelId = modelCatalogSnapshot.selectedModelId,
            modelLabel = runtime.currentModel,
            supportedModels = modelCatalogSnapshot.supportedModels,
            activeContextSize = activeContextSize,
            messages = messages.toList(),
            activity = activity.toList(),
            pendingMessages = pendingMessages.toList()
        )
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
                persistedSystemPrompt = message.text()
                persistState()
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

    companion object {
        private fun defaultDependencies(): SessionDependencies {
            val sessionStateStore = SessionStateStore()
            val sessionStore = SessionStore(stateStore = sessionStateStore)
            val sessionId = sessionStore.selectedSessionId() ?: sessionStore.createSessionId()
            return SessionDependencies(
                sessionStore = sessionStore,
                sessionStateStore = sessionStateStore,
                sessionId = sessionId
            )
        }

        private fun defaultSandboxServiceFactory(): (UserPathPolicyLogic) -> DockerSandboxService? = { pathPolicyLogic ->
            DockerSandboxService(
                config = AppConfig.sandbox,
                pathPolicyLogic = pathPolicyLogic
            ).also { it.start() }
        }

        private fun defaultRuntimeFactory():
            (AgenticInstanceContext, () -> Boolean, () -> List<ChatModelListener>, () -> Int, () -> ToolFilterProfile?, () -> McpToolFilterProfile?) -> HarnessRuntime =
            { agentContext, shouldInterruptBeforeToolExecution, listenersProvider, toolFilterRevisionProvider, toolFilterProfileProvider, mcpToolFilterProfileProvider ->
                AgentRuntime(
                    agentContext = agentContext,
                    shouldInterruptBeforeToolExecution = shouldInterruptBeforeToolExecution,
                    listenersProvider = listenersProvider,
                    toolFilterRevisionProvider = toolFilterRevisionProvider,
                    toolFilterProfileProvider = toolFilterProfileProvider,
                    mcpToolFilterProfileProvider = mcpToolFilterProfileProvider
                )
            }
    }
}

private data class HarnessModelCatalogSnapshot(
    val selectedModelId: String,
    val supportedModels: List<HarnessSupportedModel>
)

private data class SessionDependencies(
    val sessionStore: SessionStore,
    val sessionStateStore: SessionStateStore,
    val sessionId: UUID
)
