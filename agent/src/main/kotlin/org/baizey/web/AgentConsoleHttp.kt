package org.baizey.web

import com.sun.net.httpserver.HttpExchange
import kotlinx.serialization.json.*
import org.baizey.harness.HarnessActivityEntry
import org.baizey.harness.HarnessChatEntry
import org.baizey.harness.HarnessSnapshot
import org.baizey.harness.policy.shared.PolicyLifetime
import org.baizey.runtime.ActivityFilterProfile
import org.baizey.runtime.ActivityFilterProfileSnapshot
import org.baizey.runtime.ToolFilterCatalogSnapshot
import org.baizey.runtime.ToolFilterProfile
import org.baizey.runtime.ToolFilterProfileSnapshot
import org.baizey.utils.IO.json
import java.nio.charset.StandardCharsets

data class QuestionDecisionRequest(
    val isAccepted: Boolean,
    val selection: String,
    val selectionIndex: Int
)

data class PermissionRequestDecision(
    val isAllowed: Boolean,
    val lifetime: PolicyLifetime,
    val scope: String,
    val reason: String = ""
)

internal fun HttpExchange.readJsonObject(): JsonObject {
    val body = String(requestBody.readAllBytes(), StandardCharsets.UTF_8)
    if (body.isBlank()) return JsonObject(emptyMap())
    return json.parseToJsonElement(body).jsonObject
}

internal fun HttpExchange.respondResource(resourcePath: String, contentType: String) {
    val bytes = Thread.currentThread().contextClassLoader.getResourceAsStream(resourcePath)?.readAllBytes()
        ?: return respondText(HttpStatus.NOT_FOUND, "Resource not found: $resourcePath")
    responseHeaders.add("Content-Type", contentType)
    sendResponseHeaders(HttpStatus.OK.code, bytes.size.toLong())
    responseBody.use { it.write(bytes) }
}

internal fun HttpExchange.respondText(status: HttpStatus, text: String) {
    val bytes = text.toByteArray(StandardCharsets.UTF_8)
    responseHeaders.add("Content-Type", "text/plain; charset=utf-8")
    sendResponseHeaders(status.code, bytes.size.toLong())
    responseBody.use { it.write(bytes) }
}

internal fun HttpExchange.respondNoContent() {
    sendResponseHeaders(HttpStatus.NO_CONTENT.code, -1)
}

internal fun HttpExchange.respondJson(value: JsonObject, status: HttpStatus = HttpStatus.OK) {
    val bytes = value.toString().toByteArray(StandardCharsets.UTF_8)
    responseHeaders.add("Content-Type", "application/json; charset=utf-8")
    sendResponseHeaders(status.code, bytes.size.toLong())
    responseBody.use { it.write(bytes) }
}

internal fun actionJson(ok: Boolean, message: String? = null): JsonObject {
    return buildJsonObject {
        putBoolean("ok", ok)
        if (message != null) {
            putString("message", message)
        }
    }
}

internal fun buildStateJson(
    session: HarnessSnapshot,
    pendingQuestions: List<PendingQuestionView>,
    pendingPermissionRequests: List<PendingPermissionRequestView>,
    filterProfiles: ActivityFilterProfileSnapshot,
    toolFilterProfiles: ToolFilterProfileSnapshot,
    toolFilterCatalog: ToolFilterCatalogSnapshot
): JsonObject {
    return buildJsonObject {
        put("session", session.toJson())
        put("pendingAskUsers", buildJsonArray {
            pendingQuestions.forEach { question ->
                add(
                    buildJsonObject {
                        putString("id", question.id)
                        putString("question", question.question)
                        put("options", buildJsonArray { question.options.forEach { addString(it) } })
                        putLong("createdAtMs", question.createdAtMs)
                    }
                )
            }
        })
        put("pendingPermissions", buildJsonArray {
            pendingPermissionRequests.forEach { permission ->
                add(
                    buildJsonObject {
                        putString("id", permission.id)
                        putString("path", permission.path)
                        putString("accessType", permission.accessType)
                        put("scopeOptions", buildJsonArray { permission.scopeOptions.forEach { addString(it) } })
                        putLong("createdAtMs", permission.createdAtMs)
                    }
                )
            }
        })
        put("filterProfiles", filterProfiles.toJson())
        put("toolFilterProfiles", toolFilterProfiles.toJson())
        put("toolFilterCatalog", toolFilterCatalog.toJson())
    }
}

internal fun HarnessSnapshot.toJson(): JsonObject {
    return buildJsonObject {
        putBoolean("running", running)
        putString("model", model)
        put("supportedModels", buildJsonArray { supportedModels.forEach { addString(it) } })
        putLong("activeContextSize", activeContextSize.toLong())
        put("messages", buildJsonArray { messages.forEach { add(it.toJson()) } })
        put("activity", buildJsonArray { activity.forEach { add(it.toJson()) } })
        put("pendingMessages", buildJsonArray {
            pendingMessages.forEach { pendingMessage ->
                add(
                    buildJsonObject {
                        putString("id", pendingMessage.id)
                        putString("text", pendingMessage.text)
                        putLong("createdAtMs", pendingMessage.createdAtMs)
                    }
                )
            }
        })
    }
}

internal fun HarnessChatEntry.toJson(): JsonObject {
    return buildJsonObject {
        putLong("id", id)
        putString("role", role)
        putString("text", text)
        putLong("timestampMs", timestampMs)
    }
}

internal fun HarnessActivityEntry.toJson(): JsonObject {
    return buildJsonObject {
        putLong("id", id)
        putString("type", type.wireName)
        putString("title", title)
        putString("detail", detail)
        putLong("timestampMs", timestampMs)
        correlationId?.let { putString("correlationId", it) }
    }
}

internal fun ActivityFilterProfileSnapshot.toJson(): JsonObject {
    return buildJsonObject {
        putString("activeProfileId", activeProfileId)
        put("profiles", buildJsonArray { profiles.forEach { add(it.toJson()) } })
    }
}

internal fun ActivityFilterProfile.toJson(): JsonObject {
    return buildJsonObject {
        putString("id", id)
        putString("name", name)
        putBoolean("isBuiltIn", isBuiltIn)
        put(
            "rules",
            buildJsonObject {
                rules.forEach { (category, mode) ->
                    putString(category.wireName, mode.name)
                }
            }
        )
    }
}

internal fun ToolFilterProfileSnapshot.toJson(): JsonObject {
    return buildJsonObject {
        putString("activeProfileId", activeProfileId)
        put("profiles", buildJsonArray { profiles.forEach { add(it.toJson()) } })
    }
}

internal fun ToolFilterProfile.toJson(): JsonObject {
    return buildJsonObject {
        putString("id", id)
        putString("name", name)
        putBoolean("isBuiltIn", isBuiltIn)
        put(
            "rules",
            buildJsonObject {
                rules.forEach { (ruleTarget, mode) ->
                    putString(ruleTarget, mode.name)
                }
            }
        )
    }
}

internal fun ToolFilterCatalogSnapshot.toJson(): JsonObject {
    return buildJsonObject {
        put(
            "groups",
            buildJsonArray {
                groups.forEach { group ->
                    add(
                        buildJsonObject {
                            putString("id", group.id)
                            putString("label", group.label)
                            putString("description", group.description)
                        }
                    )
                }
            }
        )
        put(
            "subgroups",
            buildJsonArray {
                subgroups.forEach { subgroup ->
                    add(
                        buildJsonObject {
                            putString("id", subgroup.id)
                            putString("groupId", subgroup.groupId)
                            putString("label", subgroup.label)
                            putString("description", subgroup.description)
                        }
                    )
                }
            }
        )
        put(
            "tools",
            buildJsonArray {
                tools.forEach { tool ->
                    add(
                        buildJsonObject {
                            putString("id", tool.id)
                            putString("toolName", tool.toolName)
                            putString("groupId", tool.groupId)
                            tool.subgroupId?.let { putString("subgroupId", it) }
                            putString("label", tool.label)
                            putString("description", tool.description)
                        }
                    )
                }
            }
        )
    }
}

internal fun JsonObject.string(field: String): String? {
    return this[field]?.jsonPrimitive?.contentOrNull
}

internal fun JsonObject.objectValue(field: String): JsonObject? {
    return this[field]?.jsonObject
}

internal fun JsonObject.boolean(field: String): Boolean {
    return this[field]?.jsonPrimitive?.booleanOrNull ?: false
}

internal fun JsonObject.int(field: String): Int {
    return this[field]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
}

internal fun JsonObjectBuilder.putString(key: String, value: String) {
    put(key, JsonPrimitive(value))
}

internal fun JsonObjectBuilder.putBoolean(key: String, value: Boolean) {
    put(key, JsonPrimitive(value))
}

internal fun JsonObjectBuilder.putLong(key: String, value: Long) {
    put(key, JsonPrimitive(value))
}

internal fun JsonArrayBuilder.addString(value: String) {
    add(JsonPrimitive(value))
}
