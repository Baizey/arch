package org.baizey.runtime

import org.baizey.harness.HarnessSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class SessionStoreTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `starts empty when no persisted sessions exist`() {
        val stateStore = SessionStateStore(tempDir.resolve("records"))
        val store = SessionStore(tempDir.resolve("control").resolve("session_store.json"), stateStore)

        assertEquals(SessionStoreSnapshot(activeSessionId = null, sessions = emptyList()), store.snapshot())
    }

    @Test
    fun `persists active selection and lists stored sessions`() {
        val recordsDir = tempDir.resolve("records")
        val controlPath = tempDir.resolve("control").resolve("session_store.json")
        val stateStore = SessionStateStore(recordsDir)
        val store = SessionStore(controlPath, stateStore)

        val firstSessionId = store.createSessionId()
        stateStore.save(
            SessionState(
                sessionId = firstSessionId.toString(),
                kind = SessionKind.ROOT,
                updatedAtMs = 10,
                systemPrompt = "system prompt",
                snapshot = emptySnapshot()
            )
        )

        val secondSessionId = store.createSessionId()
        stateStore.save(
            SessionState(
                sessionId = secondSessionId.toString(),
                kind = SessionKind.ROOT,
                updatedAtMs = 20,
                systemPrompt = null,
                snapshot = emptySnapshot(messageCount = 2)
            )
        )

        assertNotNull(store.selectSession(firstSessionId.toString()))

        val reloaded = SessionStore(controlPath, SessionStateStore(recordsDir)).snapshot()
        assertEquals(firstSessionId.toString(), reloaded.activeSessionId)
        assertEquals(listOf(secondSessionId.toString(), firstSessionId.toString()), reloaded.sessions.map { it.sessionId })
    }

    @Test
    fun `persists sub-agent metadata when creating sub-agent session`() {
        val recordsDir = tempDir.resolve("records")
        val controlPath = tempDir.resolve("control").resolve("session_store.json")
        val stateStore = SessionStateStore(recordsDir)
        val store = SessionStore(controlPath, stateStore)

        val rootSessionId = store.createSessionId()
        val subAgentSessionId = store.createSessionId(
            kind = SessionKind.SUB_AGENT,
            parentSessionId = rootSessionId.toString()
        )

        val subAgentState = stateStore.load(subAgentSessionId)
        assertNotNull(subAgentState)
        assertEquals(SessionKind.SUB_AGENT, subAgentState!!.kind)
        assertEquals(rootSessionId.toString(), subAgentState.parentSessionId)
        assertEquals(rootSessionId.toString(), subAgentState.rootSessionId)
    }

    @Test
    fun `derives nested sub-agent root session from parent session metadata`() {
        val recordsDir = tempDir.resolve("records")
        val controlPath = tempDir.resolve("control").resolve("session_store.json")
        val stateStore = SessionStateStore(recordsDir)
        val store = SessionStore(controlPath, stateStore)

        val rootSessionId = store.createSessionId()
        val parentSubAgentSessionId = store.createSessionId(
            kind = SessionKind.SUB_AGENT,
            parentSessionId = rootSessionId.toString()
        )
        val childSubAgentSessionId = store.createSessionId(
            kind = SessionKind.SUB_AGENT,
            parentSessionId = parentSubAgentSessionId.toString()
        )

        val childSubAgentState = stateStore.load(childSubAgentSessionId)
        assertNotNull(childSubAgentState)
        assertEquals(parentSubAgentSessionId.toString(), childSubAgentState!!.parentSessionId)
        assertEquals(rootSessionId.toString(), childSubAgentState.rootSessionId)
    }

    @Test
    fun `deletes session tree and clears active selection`() {
        val recordsDir = tempDir.resolve("records")
        val controlPath = tempDir.resolve("control").resolve("session_store.json")
        val stateStore = SessionStateStore(recordsDir)
        val store = SessionStore(controlPath, stateStore)

        val rootSessionId = store.createSessionId()
        val childSessionId = store.createSessionId(
            kind = SessionKind.SUB_AGENT,
            parentSessionId = rootSessionId.toString()
        )

        assertEquals(true, store.deleteSession(rootSessionId.toString()))
        assertEquals(null, store.selectedSessionId())
        assertEquals(null, stateStore.load(rootSessionId))
        assertEquals(null, stateStore.load(childSessionId))
    }

    private fun emptySnapshot(messageCount: Int = 0): HarnessSnapshot {
        return HarnessSnapshot(
            running = false,
            selectedModelId = "qwen3.6:27b",
            modelLabel = "model",
            supportedModels = emptyList(),
            activeContextSize = 0,
            messages = List(messageCount) { index ->
                org.baizey.harness.HarnessChatEntry(
                    id = index.toLong() + 1,
                    role = "user",
                    text = "message-$index",
                    timestampMs = index.toLong()
                )
            },
            activity = emptyList(),
            pendingMessages = emptyList()
        )
    }
}
