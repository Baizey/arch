package org.baizey.web

import com.sun.net.httpserver.HttpServer
import org.baizey.runtime.AgentRuntime
import org.baizey.runtime.AppConfig
import java.net.InetSocketAddress
import java.util.concurrent.Executors

fun main() {
    val interactionPort = AgentConsoleInteractionPort()
    val server = HttpServer.create(InetSocketAddress(AppConfig.console.host, AppConfig.console.port), 0)
    var sessionManager: ActiveHarnessSessionManager? = null
    try {
        sessionManager = ActiveHarnessSessionManager(interactionPort) { agentContext, shouldInterruptBeforeToolExecution, listenersProvider, toolFilterRevisionProvider, toolFilterProfileProvider, mcpToolFilterProfileProvider ->
            AgentRuntime(
                agentContext = agentContext,
                shouldInterruptBeforeToolExecution = shouldInterruptBeforeToolExecution,
                listenersProvider = listenersProvider,
                toolFilterRevisionProvider = toolFilterRevisionProvider,
                toolFilterProfileProvider = toolFilterProfileProvider,
                mcpToolFilterProfileProvider = mcpToolFilterProfileProvider
            )
        }
        val app = AgentConsoleServer(server, sessionManager, interactionPort)
        server.executor = Executors.newCachedThreadPool()
        server.createContext("/") { exchange -> app.handle(exchange) }
        Runtime.getRuntime().addShutdownHook(
            Thread {
                runCatching { server.stop(0) }
                runCatching { sessionManager.close() }
            }
        )
        server.start()
        println("Agent console running at http://${AppConfig.console.host}:${AppConfig.console.port}")
    } catch (exception: Exception) {
        runCatching { server.stop(0) }
        runCatching { sessionManager?.close() }
        throw exception
    }
}
