package org.baizey.harness.tools.web.summary

import org.baizey.commands.utils.ModelSelection
import org.baizey.harness.HarnessInteractionPort
import org.baizey.harness.PermissionDecision
import org.baizey.harness.PermissionRequest
import org.baizey.harness.policy.UserGitPolicyLogic
import org.baizey.harness.policy.UserPathPolicyLogic
import org.baizey.harness.policy.shared.PolicyLifetime
import org.baizey.harness.tools.fetch.WebsiteDocument
import org.baizey.harness.tools.search.FetchedSearchResultContent
import org.baizey.harness.tools.search.WebSearchResponse
import org.baizey.runtime.ToolFilterProfileStore
import org.baizey.runtime.agentic.instance.AgenticInstanceContext
import org.baizey.runtime.agentic.instance.AgentInstance
import org.baizey.runtime.agentic.instance.CoreContext
import org.baizey.runtime.agentic.instance.PolicyContext
import org.baizey.runtime.agentic.instance.ProviderType
import org.baizey.runtime.agentic.instance.ToolContext

internal class CurrentModelContentSummarizer(
    private val parentContext: AgenticInstanceContext
) : ContentSummarizer {
    private val interactionPort = object : HarnessInteractionPort {
        override fun askUserQuestion(question: String, options: List<String>) =
            error("CurrentModelContentSummarizer does not ask user questions")

        override fun requestPermission(request: PermissionRequest): PermissionDecision =
            PermissionDecision(
                isAllowed = false,
                lifetime = PolicyLifetime.ONCE,
                scope = request.path,
                reason = "CurrentModelContentSummarizer does not request tool permissions"
            )
    }

    private val policies = PolicyContext(
        git = UserGitPolicyLogic(interactionPort),
        path = UserPathPolicyLogic(interactionPort)
    )

    override fun summarizeSearchResults(
        query: String,
        queryGoal: String?,
        searchResponse: WebSearchResponse,
        fetchedContent: List<FetchedSearchResultContent>
    ): String {
        val prompt = buildString {
            appendLine("Summarize these search results for an agent.")
            appendLine("Search query: $query")
            appendLine("Summary goal: ${queryGoal ?: "none provided"}")
            searchResponse.provider?.let {
                appendLine("Search provider: ${it.displayName} (${it.id})")
            }
            appendLine("Return a short actionable summary that covers everything the goal could encompass")
            appendLine("Use fetched page content when available; do not rely only on titles and snippets.")
            appendLine("If one result looks clearly best for the goal, say so.")
            appendLine("Search results:")
            searchResponse.results.forEachIndexed { index, result ->
                appendLine("${index + 1}. Title: ${result.title}")
                appendLine("URL: ${result.url}")
                appendLine("Snippet: ${result.snippet.ifBlank { "<empty>" }}")
            }
            if (fetchedContent.isNotEmpty()) {
                appendLine("Fetched top result content:")
                fetchedContent.forEachIndexed { index, content ->
                    appendLine("${index + 1}. Result URL: ${content.result.url}")
                    content.resolvedUrl?.let { appendLine("Resolved URL: $it") }
                    content.pageTitle?.let { appendLine("Page title: $it") }
                    content.fetchError?.let {
                        appendLine("Fetch error: $it")
                    } ?: run {
                        appendLine("Excerpt truncated: ${content.excerptTruncated}")
                        appendLine("Excerpt:")
                        appendLine(content.excerpt ?: "<empty>")
                    }
                }
            }
        }
        return chat(prompt)
    }

    override fun summarizeWebsiteContent(
        page: WebsiteDocument,
        startChar: Int,
        endCharExclusive: Int,
        returnedText: String,
        returnedTextTruncated: Boolean,
        queryGoal: String?
    ): String {
        val prompt = buildString {
            appendLine("Summarize this fetched website content for an agent.")
            appendLine("Summary goal: ${queryGoal ?: "none provided"}")
            appendLine("Requested URL: ${page.requestedUrl}")
            appendLine("Resolved URL: ${page.resolvedUrl}")
            appendLine("Status code: ${page.statusCode}")
            appendLine("Content-Type: ${page.contentType}")
            if (!page.title.isNullOrBlank()) {
                appendLine("Title: ${page.title}")
            }
            appendLine("Available characters in cleaned source: ${page.text.length}")
            appendLine("Returned range: [$startChar..$endCharExclusive[")
            appendLine("Returned characters: ${returnedText.length}")
            appendLine("Returned content truncated: $returnedTextTruncated")
            appendLine("Source truncated before paging: ${page.sourceTruncated}")
            appendLine("Instructions:")
            appendLine("- Answer toward the summary goal when one was provided.")
            appendLine("- Mention if the returned slice may be incomplete.")
            appendLine("- Mention the source URL when useful.")
            appendLine("Fetched content:")
            append(returnedText.ifBlank { "<empty>" })
        }
        return chat(prompt)
    }

    private fun chat(prompt: String): String {
        val agent = getAgent()
        var response = agent.chat(prompt)
        while (response.isNullOrBlank() || response == "null") {
            response = agent.chat("Please provide your response, or continue to ponder")
        }
        parentContext.core.sessionStateStore.persistSimpleTranscript(
            sessionId = agent.sessionId,
            selectedModelId = ModelSelection.BEST_SMALL,
            modelLabel = agent.displayName(),
            userMessage = prompt,
            assistantMessage = response
        )
        return response
    }

    private fun getAgent(): AgentInstance {
        val systemPrompt =
            """You summarize web tool results for another coding agent.
Return only the useful answer, not chain of thought.
Stay grounded in the provided data.
Prefer concise factual summaries.
If the requested goal cannot be satisfied from the provided data, say what is missing.
Include relevant URLs when they materially help the next step."""
        val parentSessionId = parentContext.core.sessionId
        val sessionId = parentSessionId?.let { parentContext.core.sessionStateStore.createSubAgentSession(it, systemPrompt) }
        return AgentInstance.create(
            AgenticInstanceContext(
                core = CoreContext(
                    sessionId = sessionId,
                    sessionStateStore = parentContext.core.sessionStateStore,
                    sessionParentId = parentSessionId,
                    modelName = ModelSelection.BEST_SMALL,
                    type = ProviderType.OLLAMA,
                    systemPrompt = systemPrompt,
                    listeners = emptyList(),
                    userInteraction = interactionPort
                ),
                policies = policies,
                tools = ToolContext(
                    profile = ToolFilterProfileStore.nothingProfile(),
                    sandbox = parentContext.tools.sandbox
                )
            )
        )
    }
}
