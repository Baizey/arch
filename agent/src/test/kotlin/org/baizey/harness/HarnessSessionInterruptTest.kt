package org.baizey.harness

import org.baizey.runtime.agentic.instance.SystemPrompt
import org.baizey.runtime.agentic.instance.exceptions.AgentRunInterruptedException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class HarnessSessionInterruptTest {

    @Test
    fun `newer message discards stale assistant response`() {
        val firstCallStarted = CountDownLatch(1)
        val releaseFirstCall = CountDownLatch(1)
        val session = HarnessSession(FakeInteractionPort()) { _, shouldInterrupt, _, _, _ ->
            FakeRuntime { prompt ->
                when (prompt) {
                    "first question" -> {
                        firstCallStarted.countDown()
                        waitFor(firstCallStarted)
                        waitFor(releaseFirstCall)
                        if (shouldInterrupt()) {
                            "stale answer"
                        } else {
                            "unexpected"
                        }
                    }

                    "new question" -> "fresh answer"
                    else -> error("Unexpected prompt: $prompt")
                }
            }
        }

        session.submitUserMessage("first question")
        waitFor(firstCallStarted)

        val queued = session.submitUserMessage("new question")
        assertEquals("Message queued and interrupt requested.", queued.message)

        releaseFirstCall.countDown()
        waitForRunToFinish(session)

        val messages = session.snapshot().messages.map { it.role to it.text }
        assertEquals(
            listOf(
                "user" to "first question",
                "user" to "new question",
                SystemPrompt.AGENT_NAME to "fresh answer"
            ),
            messages
        )
        assertFalse(messages.any { it.second == "stale answer" })
    }

    @Test
    fun `newer message interrupts before next tool boundary`() {
        val firstCallStarted = CountDownLatch(1)
        val session = HarnessSession(FakeInteractionPort()) { _, shouldInterrupt, _, _, _ ->
            FakeRuntime { prompt ->
                when (prompt) {
                    "first question" -> {
                        firstCallStarted.countDown()
                        waitFor(firstCallStarted)
                        waitUntil { shouldInterrupt() }
                        throw AgentRunInterruptedException("Interrupted before tool execution.")
                    }

                    "new question" -> "fresh answer"
                    else -> error("Unexpected prompt: $prompt")
                }
            }
        }

        session.submitUserMessage("first question")
        waitFor(firstCallStarted)

        val queued = session.submitUserMessage("new question")
        assertEquals("Message queued and interrupt requested.", queued.message)

        waitForRunToFinish(session)

        val snapshot = session.snapshot()
        val messages = snapshot.messages.map { it.role to it.text }
        assertEquals(
            listOf(
                "user" to "first question",
                "user" to "new question",
                SystemPrompt.AGENT_NAME to "fresh answer"
            ),
            messages
        )
        assertTrue(snapshot.activity.any { it.title == "Run interrupted" })
    }

    private fun waitFor(latch: CountDownLatch) {
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Timed out waiting for latch.")
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

    private fun waitForRunToFinish(session: HarnessSession) {
        waitUntil { !session.snapshot().running }
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
