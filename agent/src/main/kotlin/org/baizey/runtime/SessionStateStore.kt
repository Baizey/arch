package org.baizey.runtime

import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ChatMessageDeserializer
import dev.langchain4j.data.message.ChatMessageSerializer
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.data.message.ChatMessageType
import dev.langchain4j.data.message.ToolExecutionResultMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.store.memory.chat.ChatMemoryStore
import kotlinx.serialization.Serializable
import org.baizey.harness.HarnessActivityEntry
import org.baizey.harness.HarnessActivityType
import org.baizey.harness.HarnessChatEntry
import org.baizey.harness.HarnessSnapshot
import org.baizey.utils.IO.fromJson
import org.baizey.utils.IO.toJson
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.Comparator
import kotlin.io.path.createDirectories
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.notExists

@Serializable
data class SessionState(
    val sessionId: String,
    val kind: SessionKind,
    val parentSessionId: String? = null,
    val rootSessionId: String? = null,
    val updatedAtMs: Long,
    val systemPrompt: String? = null,
    val snapshot: HarnessSnapshot
)

class SessionStateStore(
    private val recordsDir: Path = SystemPath.sessionRecordsDir
) {
    fun createSubAgentSession(parentSessionId: UUID, systemPrompt: String): UUID {
        val sessionId = UUID.randomUUID()
        val parentState = load(parentSessionId)
        val rootSessionId = parentState?.rootSessionId
            ?: parentState?.parentSessionId
            ?: parentSessionId.toString()
        save(
            SessionState(
                sessionId = sessionId.toString(),
                kind = SessionKind.SUB_AGENT,
                parentSessionId = parentSessionId.toString(),
                rootSessionId = rootSessionId,
                updatedAtMs = System.currentTimeMillis(),
                systemPrompt = systemPrompt,
                snapshot = emptySnapshot()
            )
        )
        return sessionId
    }

    fun load(sessionId: UUID): SessionState? {
        val path = statePath(sessionId)
        if (path.notExists()) return null
        return try {
            Files.readString(path).fromJson<SessionState>()
        } catch (_: Exception) {
            null
        }
    }

    fun listSessions(): List<SessionState> {
        if (!recordsDir.isDirectory()) return emptyList()
        return recordsDir.listDirectoryEntries()
            .mapNotNull { directory ->
                val sessionId = runCatching { UUID.fromString(directory.fileName.toString()) }.getOrNull() ?: return@mapNotNull null
                load(sessionId)
            }
    }

    fun save(state: SessionState) {
        val sessionId = UUID.fromString(state.sessionId)
        val path = statePath(sessionId)
        path.createParentDirectories()
        Files.writeString(path, state.toJson())
    }

    fun deleteChatMemory(sessionId: UUID) {
        val path = chatMemoryPath(sessionId)
        if (path.exists()) {
            Files.delete(path)
        }
    }

    fun readChatMemory(sessionId: UUID): List<ChatMessage> {
        val path = chatMemoryPath(sessionId)
        if (path.notExists()) return emptyList()
        return try {
            ChatMessageDeserializer.messagesFromJson(Files.readString(path))
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun writeChatMemory(sessionId: UUID, messages: List<ChatMessage>) {
        val path = chatMemoryPath(sessionId)
        path.createParentDirectories()
        Files.writeString(path, ChatMessageSerializer.messagesToJson(messages))
    }

    fun persistSnapshotFromChatMemory(sessionId: UUID, selectedModelId: String, modelLabel: String) {
        val existing = load(sessionId) ?: return
        val messages = readChatMemory(sessionId)
        val snapshotMessages = mutableListOf<HarnessChatEntry>()
        val snapshotActivity = mutableListOf<HarnessActivityEntry>()
        var nextId = 1L

        messages.forEach { message ->
            when (message.type()) {
                ChatMessageType.SYSTEM -> Unit
                ChatMessageType.USER -> {
                    val userMessage = message as UserMessage
                    snapshotMessages += HarnessChatEntry(
                        id = nextId++,
                        role = "user",
                        text = if (userMessage.hasSingleText()) userMessage.singleText() else userMessage.toString(),
                        timestampMs = System.currentTimeMillis()
                    )
                }
                ChatMessageType.AI -> {
                    val aiMessage = message as AiMessage
                    aiMessage.thinking()
                        ?.takeIf { it.isNotBlank() }
                        ?.let { thinking ->
                            snapshotActivity += HarnessActivityEntry(
                                id = nextId++,
                                type = HarnessActivityType.THINKING,
                                title = "Reasoning",
                                detail = thinking,
                                timestampMs = System.currentTimeMillis()
                            )
                        }
                    aiMessage.text()
                        ?.takeIf { it.isNotBlank() }
                        ?.let { text ->
                            snapshotMessages += HarnessChatEntry(
                                id = nextId++,
                                role = "assistant",
                                text = text,
                                timestampMs = System.currentTimeMillis()
                            )
                        }
                }
                ChatMessageType.TOOL_EXECUTION_RESULT -> {
                    val toolResult = message as ToolExecutionResultMessage
                    snapshotActivity += HarnessActivityEntry(
                        id = nextId++,
                        type = HarnessActivityType.TOOL_RESULT,
                        title = "Tool used: ${toolResult.toolName()}",
                        detail = toolResult.text(),
                        timestampMs = System.currentTimeMillis(),
                        correlationId = toolResult.id()
                    )
                }
                ChatMessageType.CUSTOM -> {
                    snapshotActivity += HarnessActivityEntry(
                        id = nextId++,
                        type = HarnessActivityType.CUSTOM,
                        title = "Custom message",
                        detail = message.toString(),
                        timestampMs = System.currentTimeMillis()
                    )
                }
            }
        }

        save(
            existing.copy(
                updatedAtMs = System.currentTimeMillis(),
                snapshot = HarnessSnapshot(
                    running = false,
                    selectedModelId = selectedModelId,
                    modelLabel = modelLabel,
                    supportedModels = existing.snapshot.supportedModels,
                    activeContextSize = existing.snapshot.activeContextSize,
                    messages = snapshotMessages,
                    activity = snapshotActivity,
                    pendingMessages = emptyList()
                )
            )
        )
    }

    fun deleteSessionTree(sessionId: UUID): Boolean {
        val existing = load(sessionId) ?: return false
        listSessions()
            .filter { child -> child.parentSessionId == existing.sessionId }
            .forEach { child -> deleteSessionTree(UUID.fromString(child.sessionId)) }

        val dir = sessionDir(sessionId)
        if (dir.notExists()) {
            return false
        }
        Files.walk(dir).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach { path ->
                Files.deleteIfExists(path)
            }
        }
        return true
    }

    private fun statePath(sessionId: UUID): Path {
        return sessionDir(sessionId).resolve("session.json")
    }

    private fun chatMemoryPath(sessionId: UUID): Path {
        return sessionDir(sessionId).resolve("chat_memory.json")
    }

    private fun sessionDir(sessionId: UUID): Path {
        val dir = recordsDir.resolve(sessionId.toString())
        dir.createDirectories()
        return dir
    }

    private fun emptySnapshot(): HarnessSnapshot {
        return HarnessSnapshot(
            running = false,
            selectedModelId = "unselected",
            modelLabel = "unselected",
            supportedModels = emptyList(),
            activeContextSize = 0,
            messages = emptyList(),
            activity = emptyList(),
            pendingMessages = emptyList()
        )
    }
}

internal class SessionChatMemoryStore(
    private val stateStore: SessionStateStore
) : ChatMemoryStore {
    override fun getMessages(memoryId: Any): List<ChatMessage> {
        return stateStore.readChatMemory(memoryId.asSessionId())
    }

    override fun updateMessages(memoryId: Any, messages: List<ChatMessage>) {
        stateStore.writeChatMemory(memoryId.asSessionId(), messages)
    }

    override fun deleteMessages(memoryId: Any) {
        stateStore.deleteChatMemory(memoryId.asSessionId())
    }

    private fun Any.asSessionId(): UUID {
        return when (this) {
            is UUID -> this
            is String -> UUID.fromString(this)
            else -> UUID.fromString(toString())
        }
    }
}
