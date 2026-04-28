package org.baizey.web

import com.sun.net.httpserver.HttpServer
import org.baizey.harness.HarnessSession
import org.baizey.runtime.AppConfig
import java.net.InetSocketAddress
import java.util.concurrent.Executors

fun main() {
    val interactionPort = AgentConsoleInteractionPort()
    val session = HarnessSession(interactionPort)
    val server = HttpServer.create(InetSocketAddress(AppConfig.console.host, AppConfig.console.port), 0)
    val app = AgentConsoleServer(server, session, interactionPort)
    server.executor = Executors.newCachedThreadPool()
    server.createContext("/") { exchange -> app.handle(exchange) }
    server.start()
    println("Agent console running at http://${AppConfig.console.host}:${AppConfig.console.port}")
}
