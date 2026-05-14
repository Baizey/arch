package org.baizey.web

import com.sun.net.httpserver.HttpServer
import org.baizey.harness.HarnessSession
import org.baizey.runtime.AppConfig
import java.net.InetSocketAddress
import java.util.concurrent.Executors

fun main() {
    val interactionPort = AgentConsoleInteractionPort()
    val server = HttpServer.create(InetSocketAddress(AppConfig.console.host, AppConfig.console.port), 0)
    var session: HarnessSession? = null
    try {
        session = HarnessSession(interactionPort)
        val app = AgentConsoleServer(server, session, interactionPort)
        server.executor = Executors.newCachedThreadPool()
        server.createContext("/") { exchange -> app.handle(exchange) }
        Runtime.getRuntime().addShutdownHook(
            Thread {
                runCatching { server.stop(0) }
                runCatching { session.close() }
            }
        )
        server.start()
        println("Agent console running at http://${AppConfig.console.host}:${AppConfig.console.port}")
    } catch (exception: Exception) {
        runCatching { server.stop(0) }
        runCatching { session?.close() }
        throw exception
    }
}
