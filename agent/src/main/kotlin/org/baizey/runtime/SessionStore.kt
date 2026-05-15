package org.baizey.runtime

import kotlinx.serialization.Serializable
import org.baizey.harness.HarnessSnapshot
import org.baizey.utils.IO.fromJson
import org.baizey.utils.IO.toJson
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.createParentDirectories
import kotlin.io.path.notExists

data class SessionStoreSnapshot(
    val activeSessionId: String?,
    val sessions: List<SessionSummary>
)

data class SessionSummary(
    val sessionId: String,
    val kind: SessionKind,
    val parentSessionId: String?,
    val rootSessionId: String?,
    val updatedAtMs: Long,
    val messageCount: Int,
    val pendingMessageCount: Int,
    val running: Boolean,
    val label: String
)

enum class SessionKind {
    ROOT,
    SUB_AGENT
}

@Serializable
data class SessionStorePersistence(
    val activeSessionId: String? = null
)

internal class SessionStore(
    private val controlPath: Path = SystemPath.sessionControlFile,
    private val stateStore: SessionStateStore = SessionStateStore()
) {
    private val lock = Any()
    private var activeSessionId: String? = null

    init {
        reloadFromPersistence()
    }

    fun snapshot(): SessionStoreSnapshot {
        synchronized(lock) {
            val sessions = stateStore.listSessions()
                .map { state -> state.toSummary() }
                .sortedByDescending { it.updatedAtMs }
            val normalizedActiveSessionId = activeSessionId?.takeIf { active ->
                sessions.any { it.sessionId == active }
            }
            return SessionStoreSnapshot(
                activeSessionId = normalizedActiveSessionId,
                sessions = sessions
            )
        }
    }

    fun selectedSessionId(): UUID? {
        synchronized(lock) {
            return activeSessionId?.let(UUID::fromString)
        }
    }

    fun createSessionId(kind: SessionKind = SessionKind.ROOT, parentSessionId: String? = null, rootSessionId: String? = null): UUID {
        synchronized(lock) {
            val sessionId = UUID.randomUUID()
            val normalizedParentSessionId = parentSessionId?.let { UUID.fromString(it).toString() }
            val parentState = normalizedParentSessionId
                ?.let(UUID::fromString)
                ?.let(stateStore::load)
            val normalizedRootSessionId = when (kind) {
                SessionKind.ROOT -> null
                SessionKind.SUB_AGENT -> rootSessionId
                    ?.let { UUID.fromString(it).toString() }
                    ?: parentState?.rootSessionId
                    ?: normalizedParentSessionId
                    ?: sessionId.toString()
            }
            stateStore.save(
                SessionState(
                    sessionId = sessionId.toString(),
                    kind = kind,
                    parentSessionId = normalizedParentSessionId,
                    rootSessionId = normalizedRootSessionId,
                    updatedAtMs = System.currentTimeMillis(),
                    systemPrompt = null,
                    snapshot = emptySnapshot()
                )
            )
            activeSessionId = sessionId.toString()
            updatePersistence()
            return sessionId
        }
    }

    fun selectSession(sessionId: String): Boolean {
        synchronized(lock) {
            val normalized = runCatching { UUID.fromString(sessionId).toString() }.getOrNull() ?: return false
            if (stateStore.load(UUID.fromString(normalized)) == null) {
                return false
            }
            activeSessionId = normalized
            updatePersistence()
            return true
        }
    }

    fun deleteSession(sessionId: String): Boolean {
        synchronized(lock) {
            val normalized = runCatching { UUID.fromString(sessionId).toString() }.getOrNull() ?: return false
            val deletedSessionIds = sessionTreeIds(UUID.fromString(normalized))
            val deleted = stateStore.deleteSessionTree(UUID.fromString(normalized))
            if (!deleted) {
                return false
            }
            if (activeSessionId != null && activeSessionId in deletedSessionIds) {
                activeSessionId = null
                updatePersistence()
            }
            return true
        }
    }

    fun reloadFromPersistence(): SessionStoreSnapshot {
        synchronized(lock) {
            controlPath.createParentDirectories()
            if (controlPath.notExists()) {
                activeSessionId = null
                return snapshot()
            }

            val stored = try {
                Files.readString(controlPath).fromJson<SessionStorePersistence>()
            } catch (_: Exception) {
                SessionStorePersistence()
            }
            activeSessionId = stored.activeSessionId
            return snapshot()
        }
    }

    private fun updatePersistence() {
        controlPath.createParentDirectories()
        Files.writeString(controlPath, SessionStorePersistence(activeSessionId).toJson())
    }

    private fun sessionTreeIds(sessionId: UUID): Set<String> {
        val rootId = sessionId.toString()
        val childrenByParent = stateStore.listSessions()
            .groupBy { child -> child.parentSessionId }

        val collected = linkedSetOf(rootId)
        val pending = ArrayDeque<String>()
        pending += rootId
        while (pending.isNotEmpty()) {
            val parentId = pending.removeFirst()
            childrenByParent[parentId].orEmpty().forEach { child ->
                if (collected.add(child.sessionId)) {
                    pending += child.sessionId
                }
            }
        }
        return collected
    }

    private fun SessionState.toSummary(): SessionSummary {
        val lastUserMessage = snapshot.messages.lastOrNull { it.role == "user" }?.text
        return SessionSummary(
            sessionId = sessionId,
            kind = kind,
            parentSessionId = parentSessionId,
            rootSessionId = rootSessionId,
            updatedAtMs = updatedAtMs,
            messageCount = snapshot.messages.size,
            pendingMessageCount = snapshot.pendingMessages.size,
            running = snapshot.running,
            label = lastUserMessage?.lineSequence()?.firstOrNull()?.take(72)
                ?: "Session ${sessionId.take(8)}"
        )
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
