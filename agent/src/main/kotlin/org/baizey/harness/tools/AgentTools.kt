package org.baizey.harness.tools

import org.baizey.harness.HarnessContext
import org.baizey.harness.tools.git.GitTools
import org.baizey.harness.tools.fs.FsTools
import org.baizey.harness.tools.web.WebTools
import org.baizey.runtime.BuiltInToolCatalog
import org.baizey.runtime.ToolFilterProfile

object AgentTools {
    fun create(toolContext: HarnessContext, toolFilterProfile: ToolFilterProfile? = null): List<Any> {
        return buildList {
            if (BuiltInToolCatalog.isEnabled("ask_user", toolFilterProfile)) {
                add(AskUserTool(toolContext.interactionPort))
            }
            addAll(FsTools.create(toolContext).filterBuiltInTools(toolFilterProfile))
            addAll(WebTools.create().filterBuiltInTools(toolFilterProfile))
            addAll(GitTools.create(toolContext).filterBuiltInTools(toolFilterProfile))
        }
    }

    private fun List<Any>.filterBuiltInTools(toolFilterProfile: ToolFilterProfile?): List<Any> {
        return filter { tool ->
            val toolNames = dev.langchain4j.agent.tool.ToolSpecifications.toolSpecificationsFrom(tool).map { it.name() }
            toolNames.isEmpty() || toolNames.any { BuiltInToolCatalog.isEnabled(it, toolFilterProfile) }
        }
    }
}
