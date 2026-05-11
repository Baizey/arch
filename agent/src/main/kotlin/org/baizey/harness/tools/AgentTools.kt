package org.baizey.harness.tools

import org.baizey.harness.tools.git.GitTools
import org.baizey.harness.tools.fs.FsTools
import org.baizey.harness.tools.web.WebTools
import org.baizey.runtime.BuiltInToolCatalog
import org.baizey.runtime.ToolFilterProfile
import org.baizey.runtime.agentic.instance.AgenticInstanceContext

object AgentTools {
    fun create(agentContext: AgenticInstanceContext): List<Any> {
        return buildList {
            if (BuiltInToolCatalog.isEnabled("ask_user", agentContext.tools.profile)) {
                add(AskUserTool(agentContext.core.userInteraction))
            }
            addAll(
                FsTools.create(
                    context = agentContext.policies.path,
                    onPolicyChanged = agentContext.tools.onPathPolicyChanged
                ).filterBuiltInTools(agentContext.tools.profile)
            )
            addAll(WebTools.create().filterBuiltInTools(agentContext.tools.profile))
            addAll(GitTools.create(agentContext.policies.git).filterBuiltInTools(agentContext.tools.profile))
            addAll(agentContext.tools.tools.filterBuiltInTools(agentContext.tools.profile))
        }
    }

    private fun List<Any>.filterBuiltInTools(toolFilterProfile: ToolFilterProfile?): List<Any> {
        return filter { tool ->
            val toolNames = dev.langchain4j.agent.tool.ToolSpecifications.toolSpecificationsFrom(tool).map { it.name() }
            toolNames.isEmpty() || toolNames.any { BuiltInToolCatalog.isEnabled(it, toolFilterProfile) }
        }
    }
}
