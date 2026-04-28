package org.baizey.web

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.baizey.harness.HarnessSession
import org.baizey.runtime.ActivityFilterCategory
import org.baizey.runtime.ActivityFilterMode
import org.baizey.runtime.ActivityFilterProfileStore
import org.baizey.runtime.ToolFilterMode
import org.baizey.runtime.ToolFilterProfileStore
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

private const val SERVICE_ID = "small-agent-web-harness"
private const val SHUTDOWN_WAIT_MS = 5_000L

internal class AgentConsoleServer(
    private val server: HttpServer,
    private val session: HarnessSession,
    private val interactionPort: AgentConsoleInteractionPort
) {
    private val shutdownRequested = AtomicBoolean(false)
    private val filterProfiles = ActivityFilterProfileStore()
    private val toolFilterProfiles = ToolFilterProfileStore()

    private val router = httpRouter {
        get("/") { exchange, _ ->
            exchange.respondResource("web/index.html", "text/html; charset=utf-8")
        }
        get("/styles.css") { exchange, _ ->
            exchange.respondResource("web/styles.css", "text/css; charset=utf-8")
        }
        get("/app.js") { exchange, _ ->
            exchange.respondResource("web/app.js", "application/javascript; charset=utf-8")
        }
        get("/fonts/{name}") { exchange, pathParams ->
            exchange.respondResource(
                resourcePath = "web/fonts/${pathParams.getValue("name")}",
                contentType = "font/woff2"
            )
        }
        get("/favicon.ico") { exchange, _ ->
            exchange.respondNoContent()
        }
        get("/api/state") { exchange, _ ->
            exchange.respondJson(
                buildStateJson(
                    session = session.snapshot(),
                    pendingQuestions = interactionPort.pendingQuestions(),
                    pendingPermissionRequests = interactionPort.pendingPermissionRequests(),
                    filterProfiles = filterProfiles.snapshot(),
                    toolFilterProfiles = toolFilterProfiles.snapshot(),
                    toolFilterCatalog = toolFilterProfiles.catalogSnapshot()
                )
            )
        }
        get("/api/meta") { exchange, _ ->
            exchange.respondJson(
                buildJsonObject {
                    putBoolean("ok", true)
                    putString("service", SERVICE_ID)
                }
            )
        }
        post("/api/message") { exchange, _ ->
            val body = exchange.readJsonObject()
            val result = session.submitUserMessage(body.string("text").orEmpty())
            exchange.respondJson(
                actionJson(
                    ok = result.ok,
                    message = result.message
                )
            )
        }
        post("/api/message/{id}/cancel") { exchange, pathParams ->
            exchange.respondJson(
                actionJson(
                    ok = session.cancelPendingMessage(pathParams.getValue("id")),
                    message = null
                )
            )
        }
        post("/api/model") { exchange, _ ->
            val body = exchange.readJsonObject()
            val message = session.setModel(body.string("model").orEmpty())
            exchange.respondJson(
                actionJson(
                    ok = true,
                    message = message
                )
            )
        }
        post("/api/context/clear") { exchange, _ ->
            exchange.respondJson(
                actionJson(
                    ok = true,
                    message = session.clearContext()
                )
            )
        }
        post("/api/filter-profiles/select") { exchange, _ ->
            val body = exchange.readJsonObject()
            val ok = filterProfiles.selectProfile(body.string("profileId").orEmpty())
            exchange.respondJson(
                actionJson(
                    ok = ok,
                    message = if (ok) null else "Unknown filter profile."
                )
            )
        }
        post("/api/filter-profiles") { exchange, _ ->
            val body = exchange.readJsonObject()
            val created = filterProfiles.createProfile(
                name = body.string("name").orEmpty(),
                baseProfileId = body.string("baseProfileId")
            )
            exchange.respondJson(
                buildJsonObject {
                    putBoolean("ok", created != null)
                    created?.let { profile ->
                        putString("profileId", profile.id)
                    }
                    if (created == null) {
                        putString("message", "Profile name cannot be empty.")
                    }
                }
            )
        }
        post("/api/filter-profiles/{id}") { exchange, pathParams ->
            val body = exchange.readJsonObject()
            val rules = body.objectValue("rules")?.entries?.mapNotNull { entry ->
                val mode = entry.value.jsonPrimitive.contentOrNull?.let { raw ->
                    enumValues<ActivityFilterMode>().firstOrNull { it.name == raw }
                } ?: return@mapNotNull null
                ActivityFilterCategory.fromWireName(entry.key)?.let { it to mode }
            }?.toMap()

            val updated = filterProfiles.updateProfile(
                profileId = pathParams.getValue("id"),
                name = body.string("name"),
                rules = rules
            )
            exchange.respondJson(
                actionJson(
                    ok = updated != null,
                    message = if (updated == null) "Unable to update profile." else null
                )
            )
        }
        post("/api/filter-profiles/{id}/delete") { exchange, pathParams ->
            val ok = filterProfiles.deleteProfile(pathParams.getValue("id"))
            exchange.respondJson(
                actionJson(
                    ok = ok,
                    message = if (ok) null else "Unable to delete profile."
                )
            )
        }
        post("/api/tool-filter-profiles/select") { exchange, _ ->
            val body = exchange.readJsonObject()
            val ok = toolFilterProfiles.selectProfile(body.string("profileId").orEmpty())
            if (ok) {
                session.setToolFilterProfile(toolFilterProfiles.activeProfile())
            }
            exchange.respondJson(
                actionJson(
                    ok = ok,
                    message = if (ok) null else "Unknown tool filter profile."
                )
            )
        }
        post("/api/tool-filter-profiles") { exchange, _ ->
            val body = exchange.readJsonObject()
            val created = toolFilterProfiles.createProfile(
                name = body.string("name").orEmpty(),
                baseProfileId = body.string("baseProfileId")
            )
            exchange.respondJson(
                buildJsonObject {
                    putBoolean("ok", created != null)
                    created?.let { profile ->
                        putString("profileId", profile.id)
                    }
                    if (created == null) {
                        putString("message", "Profile name cannot be empty.")
                    }
                }
            )
        }
        post("/api/tool-filter-profiles/{id}") { exchange, pathParams ->
            val body = exchange.readJsonObject()
            val rules = body.objectValue("rules")?.entries?.mapNotNull { entry ->
                val mode = entry.value.jsonPrimitive.contentOrNull?.let { raw ->
                    enumValues<ToolFilterMode>().firstOrNull { it.name == raw }
                } ?: return@mapNotNull null
                entry.key to mode
            }?.toMap()

            val updated = toolFilterProfiles.updateProfile(
                profileId = pathParams.getValue("id"),
                name = body.string("name"),
                rules = rules
            )
            if (updated != null && toolFilterProfiles.snapshot().activeProfileId == updated.id) {
                session.setToolFilterProfile(toolFilterProfiles.activeProfile())
            }
            exchange.respondJson(
                actionJson(
                    ok = updated != null,
                    message = if (updated == null) "Unable to update tool filter profile." else null
                )
            )
        }
        post("/api/tool-filter-profiles/{id}/delete") { exchange, pathParams ->
            val ok = toolFilterProfiles.deleteProfile(pathParams.getValue("id"))
            if (ok) {
                session.setToolFilterProfile(toolFilterProfiles.activeProfile())
            }
            exchange.respondJson(
                actionJson(
                    ok = ok,
                    message = if (ok) null else "Unable to delete tool filter profile."
                )
            )
        }
        post("/api/shutdown") { exchange, _ ->
            exchange.respondJson(
                actionJson(
                    ok = true,
                    message = requestShutdown()
                )
            )
        }
        post("/api/asks/{id}") { exchange, pathParams ->
            val body = exchange.readJsonObject()
            val request = QuestionDecisionRequest(
                isAccepted = body.boolean("isAccepted"),
                selection = body.string("selection").orEmpty(),
                selectionIndex = body.int("selectionIndex")
            )
            exchange.respondJson(
                actionJson(
                    ok = interactionPort.answerQuestion(
                        pathParams.getValue("id"),
                        request
                    )
                )
            )
        }
        post("/api/permissions/{id}") { exchange, pathParams ->
            val body = exchange.readJsonObject()
            val request = PermissionRequestDecision(
                isAllowed = body.boolean("isAllowed"),
                lifetime = enumValueOf(body.string("lifetime") ?: "ONCE"),
                scope = body.string("scope").orEmpty(),
                reason = body.string("reason").orEmpty()
            )
            exchange.respondJson(
                actionJson(
                    ok = interactionPort.answerPermissionRequest(
                        pathParams.getValue("id"),
                        request
                    )
                )
            )
        }
    }

    init {
        session.setToolFilterProfile(toolFilterProfiles.activeProfile())
    }

    fun handle(exchange: HttpExchange) {
        try {
            if (!router.handle(exchange)) {
                exchange.respondText(HttpStatus.NOT_FOUND, "Not found")
            }
        } catch (e: Exception) {
            exchange.respondJson(
                actionJson(
                    ok = false,
                    message = e.message ?: "Unknown error"
                ),
                HttpStatus.INTERNAL_SERVER_ERROR
            )
        } finally {
            exchange.close()
        }
    }

    private fun requestShutdown(): String {
        val running = session.snapshot().running
        val isFirstRequest = shutdownRequested.compareAndSet(false, true)
        if (running) {
            session.requestStopForShutdown()
        }
        if (isFirstRequest) {
            scheduleShutdown()
        }
        return when {
            !isFirstRequest -> "Shutdown already in progress."
            running -> "Shutdown requested. Waiting briefly for the current run to stop."
            else -> "Shutdown requested."
        }
    }

    private fun scheduleShutdown() {
        Thread.ofPlatform().name("web-harness-shutdown").start {
            Thread.sleep(100)
            val deadline = System.currentTimeMillis() + SHUTDOWN_WAIT_MS
            while (session.snapshot().running && System.currentTimeMillis() < deadline) {
                Thread.sleep(100)
            }
            server.stop(1)
            exitProcess(0)
        }
    }
}
