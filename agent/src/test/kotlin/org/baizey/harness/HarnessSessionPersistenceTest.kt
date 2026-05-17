package org.baizey.harness

import org.baizey.runtime.SessionKind
import org.baizey.runtime.SessionState
import org.baizey.runtime.SessionStateStore
import org.baizey.runtime.SessionStore
import org.baizey.runtime.ToolFilterProfile
import org.baizey.runtime.McpToolFilterProfile
import org.baizey.runtime.agentic.instance.AgenticInstanceContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID

class HarnessSessionPersistenceTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `restores persisted session state on restart`() {
        val controlPath = tempDir.resolve("control").resolve("session_store.json")
        val recordsDir = tempDir.resolve("records")
        val sessionStore = SessionStore(controlPath, SessionStateStore(recordsDir))
        val sessionId = sessionStore.createSessionId()
        val stateStore = SessionStateStore(recordsDir)
        val session = HarnessSession(
            interactionPort = FakeInteractionPort(),
            sessionStore = sessionStore,
            sessionStateStore = stateStore,
            sessionId = sessionId,
            sandboxServiceFactory = { null }
        ) { _, _, _, _, _, _ ->
            FakeRuntime { prompt -> "answer: $prompt" }
        }

        session.submitUserMessage("hello")
        waitUntil { !session.snapshot().running }
        val beforeRestart = session.snapshot()

        val restored = HarnessSession(
            interactionPort = FakeInteractionPort(),
            sessionStore = SessionStore(controlPath, SessionStateStore(recordsDir)),
            sessionStateStore = SessionStateStore(recordsDir),
            sessionId = sessionId,
            sandboxServiceFactory = { null }
        ) { _, _, _, _, _, _ ->
            FakeRuntime { prompt -> "answer: $prompt" }
        }

        val afterRestart = restored.snapshot()

        assertEquals(beforeRestart.messages, afterRestart.messages)
        assertEquals(beforeRestart.pendingMessages, afterRestart.pendingMessages)
        assertEquals(beforeRestart.activity, afterRestart.activity)
    }

    @Test
    fun `marks previously running session as interrupted when restored`() {
        val controlPath = tempDir.resolve("control").resolve("session_store.json")
        val recordsDir = tempDir.resolve("records")
        val stateStore = SessionStateStore(recordsDir)
        val sessionStore = SessionStore(controlPath, stateStore)
        val sessionId = sessionStore.createSessionId()
        stateStore.save(
            SessionState(
                sessionId = sessionId.toString(),
                kind = SessionKind.ROOT,
                updatedAtMs = 1,
                systemPrompt = "prompt",
                snapshot = HarnessSnapshot(
                    running = true,
                    selectedModelId = "qwen3.6:27b",
                    modelLabel = "fake",
                    supportedModels = emptyList(),
                    activeContextSize = 0,
                    messages = listOf(HarnessChatEntry(1, "user", "hello", 1)),
                    activity = emptyList(),
                    pendingMessages = emptyList()
                )
            )
        )

        val restored = HarnessSession(
            interactionPort = FakeInteractionPort(),
            sessionStore = sessionStore,
            sessionStateStore = stateStore,
            sessionId = sessionId,
            sandboxServiceFactory = { null }
        ) { _, _, _, _, _, _ ->
            FakeRuntime { "unused" }
        }

        val snapshot = restored.snapshot()
        assertFalse(snapshot.running)
        assertTrue(snapshot.activity.any { it.title == "Previous run interrupted" })
    }

    @Test
    fun `preserves session metadata when restoring and persisting`() {
        val controlPath = tempDir.resolve("control").resolve("session_store.json")
        val recordsDir = tempDir.resolve("records")
        val stateStore = SessionStateStore(recordsDir)
        val sessionStore = SessionStore(controlPath, stateStore)
        val rootSessionId = sessionStore.createSessionId()
        val subAgentSessionId = sessionStore.createSessionId(
            kind = SessionKind.SUB_AGENT,
            parentSessionId = rootSessionId.toString()
        )
        stateStore.save(
            SessionState(
                sessionId = subAgentSessionId.toString(),
                kind = SessionKind.SUB_AGENT,
                parentSessionId = rootSessionId.toString(),
                rootSessionId = rootSessionId.toString(),
                updatedAtMs = 1,
                systemPrompt = "prompt",
                snapshot = HarnessSnapshot(
                    running = false,
                    selectedModelId = "qwen3.6:27b",
                    modelLabel = "fake",
                    supportedModels = emptyList(),
                    activeContextSize = 0,
                    messages = listOf(HarnessChatEntry(1, "user", "hello", 1)),
                    activity = emptyList(),
                    pendingMessages = emptyList()
                )
            )
        )

        val restored = HarnessSession(
            interactionPort = FakeInteractionPort(),
            sessionStore = sessionStore,
            sessionStateStore = stateStore,
            sessionId = subAgentSessionId,
            sandboxServiceFactory = { null }
        ) { _, _, _, _, _, _ ->
            FakeRuntime { prompt -> "answer: $prompt" }
        }

        restored.submitUserMessage("hello from sub-agent")
        waitUntil { !restored.snapshot().running }

        val persisted = stateStore.load(subAgentSessionId)
        assertNotNull(persisted)
        assertEquals(SessionKind.SUB_AGENT, persisted!!.kind)
        assertEquals(rootSessionId.toString(), persisted.parentSessionId)
        assertEquals(rootSessionId.toString(), persisted.rootSessionId)
    }

    @Test
    fun `restored sub agent session keeps its parent id and stored system prompt`() {
        val controlPath = tempDir.resolve("control").resolve("session_store.json")
        val recordsDir = tempDir.resolve("records")
        val stateStore = SessionStateStore(recordsDir)
        val sessionStore = SessionStore(controlPath, stateStore)
        val rootSessionId = sessionStore.createSessionId()
        val subAgentSessionId = sessionStore.createSessionId(
            kind = SessionKind.SUB_AGENT,
            parentSessionId = rootSessionId.toString()
        )
        stateStore.save(
            SessionState(
                sessionId = subAgentSessionId.toString(),
                kind = SessionKind.SUB_AGENT,
                parentSessionId = rootSessionId.toString(),
                rootSessionId = rootSessionId.toString(),
                updatedAtMs = 1,
                systemPrompt = "sub-agent prompt",
                snapshot = HarnessSnapshot(
                    running = false,
                    selectedModelId = "qwen3.6:27b",
                    modelLabel = "fake",
                    supportedModels = emptyList(),
                    activeContextSize = 0,
                    messages = emptyList(),
                    activity = emptyList(),
                    pendingMessages = emptyList()
                )
            )
        )

        var capturedParentId: UUID? = null
        var capturedSystemPrompt: String? = null
        HarnessSession(
            interactionPort = FakeInteractionPort(),
            sessionStore = sessionStore,
            sessionStateStore = stateStore,
            sessionId = subAgentSessionId,
            sandboxServiceFactory = { null }
        ) { agentContext, _, _, _, _, _ ->
            capturedParentId = agentContext.core.sessionParentId
            capturedSystemPrompt = agentContext.core.systemPrompt
            FakeRuntime { "unused" }
        }

        assertEquals(rootSessionId, capturedParentId)
        assertEquals("sub-agent prompt", capturedSystemPrompt)
    }

    @Test
    fun `does not duplicate tool filter activity when reapplying same profiles`() {
        val controlPath = tempDir.resolve("control").resolve("session_store.json")
        val recordsDir = tempDir.resolve("records")
        val stateStore = SessionStateStore(recordsDir)
        val sessionStore = SessionStore(controlPath, stateStore)
        val sessionId = sessionStore.createSessionId()
        val session = HarnessSession(
            interactionPort = FakeInteractionPort(),
            sessionStore = sessionStore,
            sessionStateStore = stateStore,
            sessionId = sessionId,
            sandboxServiceFactory = { null }
        ) { _, _, _, _, _, _ ->
            FakeRuntime { "unused" }
        }

        val toolProfile = org.baizey.runtime.ToolFilterProfileStore.everythingProfile()
        val mcpProfile = org.baizey.runtime.McpToolFilterProfileStore.everythingProfile()

        session.setToolFilterProfile(toolProfile, emitActivity = false)
        session.setMcpToolFilterProfile(mcpProfile, emitActivity = false)
        session.setToolFilterProfile(toolProfile, emitActivity = false)
        session.setMcpToolFilterProfile(mcpProfile, emitActivity = false)

        val snapshot = session.snapshot()
        assertEquals(0, snapshot.activity.count { it.title.startsWith("Tool filter switched to ") })
        assertEquals(0, snapshot.activity.count { it.title.startsWith("MCP filter switched to ") })
    }

    @Test
    fun `deleting the active session falls back to another session`() {
        val controlPath = tempDir.resolve("control").resolve("session_store.json")
        val recordsDir = tempDir.resolve("records")
        val stateStore = SessionStateStore(recordsDir)
        val sessionStore = SessionStore(controlPath, stateStore)
        val firstSessionId = sessionStore.createSessionId()
        val secondSessionId = sessionStore.createSessionId()
        val manager = org.baizey.web.ActiveHarnessSessionManager(
            interactionPort = FakeInteractionPort(),
            sessionStateStore = stateStore,
            sessionStore = sessionStore,
            sandboxServiceFactory = { null }
        ) { _, _, _, _, _, _ ->
            FakeRuntime { prompt -> "answer: $prompt" }
        }

        manager.selectSession(firstSessionId.toString())
        assertTrue(manager.deleteSession(firstSessionId.toString()))
        assertEquals(secondSessionId.toString(), manager.snapshot().activeSessionId)
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            if (condition()) {
                return
            }
            Thread.sleep(10)
        }
        error("Timed out waiting for condition.")
    }

    private class FakeRuntime(
        private val chatHandler: (String) -> String?
    ) : HarnessRuntime {
        override val currentModel: String = "fake"
        override val builtInToolCount: Int = 0

        override fun chat(prompt: String): String? = chatHandler(prompt)

        override fun resetConversation() = Unit

        override fun refreshIfNeeded(): String? = null
    }

    private class FakeInteractionPort : HarnessInteractionPort {
        override fun askUserQuestion(question: String, options: List<String>): AskUserAnswer {
            error("Unexpected askUserQuestion call")
        }

        override fun requestPermission(request: PermissionRequest): PermissionDecision {
            error("Unexpected requestPermission call")
        }
    }
}
