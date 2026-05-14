package org.baizey.web

import dev.langchain4j.model.chat.listener.ChatModelListener
import org.baizey.harness.HarnessInteractionPort
import org.baizey.harness.HarnessRuntime
import org.baizey.harness.HarnessSession
import org.baizey.harness.policy.UserPathPolicyLogic
import org.baizey.runtime.AppConfig
import org.baizey.runtime.McpToolFilterProfile
import org.baizey.runtime.SessionStateStore
import org.baizey.runtime.SessionStore
import org.baizey.runtime.SessionStoreSnapshot
import org.baizey.runtime.ToolFilterProfile
import org.baizey.runtime.agentic.instance.AgenticInstanceContext
import org.baizey.runtime.sandbox.DockerSandboxService
import java.util.UUID

internal class ActiveHarnessSessionManager(
    private val interactionPort: HarnessInteractionPort,
    private val sessionStateStore: SessionStateStore = SessionStateStore(),
    private val sessionStore: SessionStore = SessionStore(stateStore = sessionStateStore),
    private val runtimeFactory: (AgenticInstanceContext, () -> Boolean, () -> List<ChatModelListener>, () -> Int, () -> ToolFilterProfile?, () -> McpToolFilterProfile?) -> HarnessRuntime
) {
    private val lock = Any()

    @Volatile
    private var currentSession: HarnessSession = buildSession(
        sessionStore.selectedSessionId() ?: sessionStore.createSessionId()
    )

    fun current(): HarnessSession = currentSession

    fun snapshot(): SessionStoreSnapshot = sessionStore.snapshot()

    fun createSession(): Boolean {
        synchronized(lock) {
            if (currentSession.snapshot().running) {
                return false
            }
            val newSession = buildSession(sessionStore.createSessionId())
            val previous = currentSession
            currentSession = newSession
            previous.close()
            return true
        }
    }

    fun selectSession(sessionId: String): Boolean {
        synchronized(lock) {
            if (currentSession.snapshot().running) {
                return false
            }
            if (!sessionStore.selectSession(sessionId)) {
                return false
            }
            val newSession = buildSession(UUID.fromString(sessionId))
            val previous = currentSession
            currentSession = newSession
            previous.close()
            return true
        }
    }

    fun close() {
        currentSession.close()
    }

    private fun buildSession(sessionId: UUID): HarnessSession {
        return HarnessSession(
            interactionPort = interactionPort,
            sessionStore = sessionStore,
            sessionStateStore = sessionStateStore,
            sessionId = sessionId,
            sandboxServiceFactory = { pathPolicyLogic: UserPathPolicyLogic ->
                DockerSandboxService(
                    config = AppConfig.sandbox,
                    pathPolicyLogic = pathPolicyLogic
                ).also { it.start() }
            },
            runtimeFactory = runtimeFactory
        )
    }
}
