package org.baizey.runtime.agent

import dev.langchain4j.mcp.McpToolProvider
import dev.langchain4j.mcp.client.DefaultMcpClient
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport
import org.baizey.harness.HarnessContext
import org.baizey.harness.tools.AgentTools
import org.baizey.runtime.McpConfig
import org.baizey.runtime.SystemPath
import org.baizey.runtime.ToolFilterProfile
import org.baizey.utils.IO.fromJson
import org.baizey.utils.IO.readIfExists

internal fun prepareRuntimeResources(
    agentContext: HarnessContext,
    toolFilterProfile: ToolFilterProfile
): RuntimeResources {
    agentContext.reloadFromPersistence()
    val mcpConfig = SystemPath.mcpConfigFile.readIfExists()?.fromJson<McpConfig>() ?: McpConfig(mapOf())
    val tools = AgentTools.create(agentContext, toolFilterProfile)
    val mcpClients = mcpConfig.servers.entries.map { (key, value) ->
        DefaultMcpClient.builder()
            .key(key)
            .transport(
                StdioMcpTransport.builder()
                    .command(listOf(value.command) + value.args)
                    .environment(value.env)
                    .logEvents(true)
                    .build()
            )
            .build()
    }

    val mcpToolProvider = if (mcpClients.isEmpty()) {
        null
    } else {
        McpToolProvider.builder().mcpClients(mcpClients).build()
    }

    return RuntimeResources(
        tools = tools,
        mcpToolProvider = mcpToolProvider
    )
}
