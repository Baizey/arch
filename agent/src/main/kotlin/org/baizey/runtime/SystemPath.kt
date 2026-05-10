package org.baizey.runtime

object SystemPath {
    private val archRoot = AppConfig.storage.homeDirectory

    // Not used yet, should be location for anything the bot would want as long-term:
    // Examples are: planning, to-do list, or other 'mini' tools
    val botDirArea = archRoot.resolve("bot_info_storage")

    // Stuff the bot should never have any access to edit
    // It may be okay to allow reading, but never editing in any way
    val disallowBotDir = archRoot.resolve("system")
    private val configDir = disallowBotDir.resolve("config")
    val mcpConfigFile = configDir.resolve("mcp_config.json")
    val policyFile = configDir.resolve("path_policy.json")
    val shellPolicyFile = configDir.resolve("shell_policy.json")
    val gitPolicyFile = configDir.resolve("git_policy.json")
    val activityFilterProfilesFile = configDir.resolve("activity_filter_profiles.json")
    val toolFilterProfilesFile = configDir.resolve("tool_filter_profiles.json")
    val mcpToolFilterProfilesFile = configDir.resolve("mcp_tool_filter_profiles.json")

    private val logDir = disallowBotDir.resolve("logs")
    val errorLogFile = logDir.resolve("errors.log")
    val auditLogFile = logDir.resolve("audit.log")
}
