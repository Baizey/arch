package org.baizey.runtime

import kotlinx.serialization.Serializable

@Serializable
data class McpConfig(val servers: Map<String, McpStdioConfigItem>)

@Serializable
data class McpStdioConfigItem(
    val type: String,
    val env: Map<String, String>,
    val command: String,
    val args: List<String>,
)