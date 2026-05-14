package org.baizey.runtime

import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.UserMessage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID

class SessionStateStoreTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `creates sub-agent session and persists transcript from chat memory`() {
        val stateStore = SessionStateStore(tempDir.resolve("records"))
        val parentSessionId = UUID.randomUUID()
        stateStore.save(
            SessionState(
                sessionId = parentSessionId.toString(),
                kind = SessionKind.ROOT,
                updatedAtMs = 1,
                systemPrompt = "parent prompt",
                snapshot = emptySnapshot()
            )
        )

        val childSessionId = stateStore.createSubAgentSession(
            parentSessionId = parentSessionId,
            systemPrompt = "child prompt"
        )
        stateStore.writeChatMemory(
            childSessionId,
            listOf(
                UserMessage.userMessage("Summarize these search results for an agent."),
                AiMessage.builder().thinking("Inspecting the fetched pages.").text("Use the official release notes page.").build()
            )
        )

        stateStore.persistSnapshotFromChatMemory(
            sessionId = childSessionId,
            selectedModelId = "qwen3.6:8b",
            modelLabel = "summary model"
        )

        val persisted = stateStore.load(childSessionId)
        assertNotNull(persisted)
        assertEquals(SessionKind.SUB_AGENT, persisted!!.kind)
        assertEquals(parentSessionId.toString(), persisted.parentSessionId)
        assertEquals(parentSessionId.toString(), persisted.rootSessionId)
        assertEquals("Summarize these search results for an agent.", persisted.snapshot.messages.first().text)
        assertEquals("Use the official release notes page.", persisted.snapshot.messages.last().text)
        assertEquals("Inspecting the fetched pages.", persisted.snapshot.activity.single().detail)
    }

    private fun emptySnapshot() = org.baizey.harness.HarnessSnapshot(
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
