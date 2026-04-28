package org.baizey.web

import org.baizey.harness.AskUserAnswer
import org.baizey.harness.HarnessInteractionPort
import org.baizey.harness.PermissionDecision
import org.baizey.harness.PermissionRequest
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

data class PendingQuestionView(
    val id: String,
    val question: String,
    val options: List<String>,
    val createdAtMs: Long
)

data class PendingPermissionRequestView(
    val id: String,
    val path: String,
    val accessType: String,
    val scopeOptions: List<String>,
    val createdAtMs: Long
)

class AgentConsoleInteractionPort : HarnessInteractionPort {
    private val questionRequests = ConcurrentHashMap<String, PendingQuestion>()
    private val permissionRequests = ConcurrentHashMap<String, PendingPermissionRequest>()

    override fun askUserQuestion(question: String, options: List<String>): AskUserAnswer {
        val id = UUID.randomUUID().toString()
        val future = CompletableFuture<AskUserAnswer>()
        questionRequests[id] = PendingQuestion(
            id = id,
            question = question,
            options = options,
            createdAtMs = System.currentTimeMillis(),
            future = future
        )
        return try {
            future.get()
        } finally {
            questionRequests.remove(id)
        }
    }

    override fun requestPermission(request: PermissionRequest): PermissionDecision {
        val id = UUID.randomUUID().toString()
        val future = CompletableFuture<PermissionDecision>()
        permissionRequests[id] = PendingPermissionRequest(
            id = id,
            request = request,
            createdAtMs = System.currentTimeMillis(),
            future = future
        )
        return try {
            future.get()
        } finally {
            permissionRequests.remove(id)
        }
    }

    fun pendingQuestions(): List<PendingQuestionView> {
        return questionRequests.values
            .sortedBy { it.createdAtMs }
            .map { PendingQuestionView(it.id, it.question, it.options, it.createdAtMs) }
    }

    fun pendingPermissionRequests(): List<PendingPermissionRequestView> {
        return permissionRequests.values
            .sortedBy { it.createdAtMs }
            .map {
                PendingPermissionRequestView(
                    id = it.id,
                    path = it.request.path,
                    accessType = it.request.accessType,
                    scopeOptions = it.request.scopeOptions,
                    createdAtMs = it.createdAtMs
                )
            }
    }

    fun answerQuestion(id: String, request: QuestionDecisionRequest): Boolean {
        val pending = questionRequests[id] ?: return false
        pending.future.complete(
            AskUserAnswer(
                isAccepted = request.isAccepted,
                selection = request.selection,
                selectionIndex = request.selectionIndex
            )
        )
        return true
    }

    fun answerPermissionRequest(id: String, request: PermissionRequestDecision): Boolean {
        val pending = permissionRequests[id] ?: return false
        pending.future.complete(
            PermissionDecision(
                isAllowed = request.isAllowed,
                lifetime = request.lifetime,
                scope = request.scope,
                reason = request.reason
            )
        )
        return true
    }

    private data class PendingQuestion(
        val id: String,
        val question: String,
        val options: List<String>,
        val createdAtMs: Long,
        val future: CompletableFuture<AskUserAnswer>
    )

    private data class PendingPermissionRequest(
        val id: String,
        val request: PermissionRequest,
        val createdAtMs: Long,
        val future: CompletableFuture<PermissionDecision>
    )
}
