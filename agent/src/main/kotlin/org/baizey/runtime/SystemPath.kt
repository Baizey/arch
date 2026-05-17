package org.baizey.runtime

import java.nio.file.Path

object SystemPath {
    private val archRoot = AppConfig.storage.homeDirectory

    // Accessible by bot
    // Can be copied and pasted big data from user, plans or other larger items needing temporary storage
    val botDirArea: Path = archRoot.resolve("storage")
    val botTmpDir: Path = botDirArea.resolve("tmp")

    // Area where the agent should never get access
    // Can cause self-modification of constraints or straight up just break the system
    val disallowBotDir: Path = archRoot.resolve("system")
    private val configDir = disallowBotDir.resolve("config")
    val mcpConfigFile: Path = configDir.resolve("mcp_config.json")
    val policyFile: Path = configDir.resolve("path_policy.json")
    val gitPolicyFile: Path = configDir.resolve("git_policy.json")
    val activityFilterProfilesFile: Path = configDir.resolve("activity_filter_profiles.json")
    val toolFilterProfilesFile: Path = configDir.resolve("tool_filter_profiles.json")
    val mcpToolFilterProfilesFile: Path = configDir.resolve("mcp_tool_filter_profiles.json")

    private val sessionsDir = disallowBotDir.resolve("sessions")
    private val sessionControlDir = sessionsDir.resolve("control")
    val sessionControlFile: Path = sessionControlDir.resolve("session_store.json")
    val sessionRecordsDir: Path = sessionsDir.resolve("records")

    private val logDir = disallowBotDir.resolve("logs")
    val errorLogFile: Path = logDir.resolve("errors.log")
    val auditLogFile: Path = logDir.resolve("audit.log")
}
