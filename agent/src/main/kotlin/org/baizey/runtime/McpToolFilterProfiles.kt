package org.baizey.runtime

import dev.langchain4j.agent.tool.ToolSpecification
import dev.langchain4j.mcp.client.DefaultMcpClient
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport
import kotlinx.serialization.Serializable
import org.baizey.utils.IO.fromJson
import org.baizey.utils.IO.readIfExists
import org.baizey.utils.IO.toJson
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.createParentDirectories
import kotlin.io.path.notExists

@Serializable
data class McpToolFilterProfile(
    val id: String,
    val name: String,
    val isBuiltIn: Boolean,
    val rules: Map<String, ToolFilterMode>
)

@Serializable
data class McpToolFilterProfilePersistence(
    val activeProfileId: String,
    val customProfiles: List<McpToolFilterProfile>
)

data class McpToolFilterProfileSnapshot(
    val activeProfileId: String,
    val profiles: List<McpToolFilterProfile>
)

data class McpToolFilterServerSnapshot(
    val id: String,
    val label: String,
    val description: String,
    val isAvailable: Boolean,
    val error: String? = null
)

data class McpToolFilterToolSnapshot(
    val id: String,
    val toolName: String,
    val serverId: String,
    val label: String,
    val description: String
)

data class McpToolFilterCatalogSnapshot(
    val servers: List<McpToolFilterServerSnapshot>,
    val tools: List<McpToolFilterToolSnapshot>
) {
    fun allRuleTargetIds(): Set<String> {
        return buildSet {
            servers.forEach { server -> add(McpToolCatalog.serverRuleTargetId(server.id)) }
            tools.forEach { tool -> add(McpToolCatalog.toolRuleTargetId(tool.serverId, tool.toolName)) }
        }
    }
}

internal class McpToolFilterProfileStore(
    private val path: Path = SystemPath.mcpToolFilterProfilesFile
) {
    private val lock = Any()
    private val customProfiles = mutableListOf<McpToolFilterProfile>()
    private val builtInProfiles = listOf(
        everythingProfile(),
        nothingProfile()
    )

    private var activeProfileId = EVERYTHING_PROFILE_ID

    init {
        reloadFromPersistence()
    }

    fun snapshot(): McpToolFilterProfileSnapshot {
        synchronized(lock) {
            val catalog = McpToolCatalog.snapshot()
            return McpToolFilterProfileSnapshot(
                activeProfileId = normalizedActiveProfileId(),
                profiles = (builtInProfiles + customProfiles).map { profile ->
                    profile.copy(rules = expandedRules(profile, catalog))
                }
            )
        }
    }

    fun activeProfile(): McpToolFilterProfile {
        synchronized(lock) {
            val catalog = McpToolCatalog.snapshot()
            val profile = findProfile(normalizedActiveProfileId()) ?: everythingProfile()
            return profile.copy(rules = expandedRules(profile, catalog))
        }
    }

    fun catalogSnapshot(): McpToolFilterCatalogSnapshot = McpToolCatalog.snapshot()

    fun selectProfile(profileId: String): Boolean {
        synchronized(lock) {
            if (findProfile(profileId) == null) {
                return false
            }
            activeProfileId = profileId
            updatePersistence()
            return true
        }
    }

    fun createProfile(name: String, baseProfileId: String?): McpToolFilterProfile? {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            return null
        }

        synchronized(lock) {
            val catalog = McpToolCatalog.snapshot()
            val baseProfile = findProfile(baseProfileId) ?: findProfile(normalizedActiveProfileId()) ?: everythingProfile()
            val profile = McpToolFilterProfile(
                id = "custom-${UUID.randomUUID()}",
                name = trimmedName,
                isBuiltIn = false,
                rules = expandedRules(baseProfile, catalog)
            )
            customProfiles += profile
            updatePersistence()
            return profile
        }
    }

    fun updateProfile(profileId: String, name: String?, rules: Map<String, ToolFilterMode>?): McpToolFilterProfile? {
        synchronized(lock) {
            val index = customProfiles.indexOfFirst { it.id == profileId }
            if (index < 0) {
                return null
            }

            val catalog = McpToolCatalog.snapshot()
            val existing = customProfiles[index]
            val updated = existing.copy(
                name = name?.trim()?.takeIf { it.isNotEmpty() } ?: existing.name,
                rules = normalizeRules(
                    input = rules ?: existing.rules,
                    catalog = catalog,
                    defaultMode = ToolFilterMode.ENABLED
                )
            )
            customProfiles[index] = updated
            updatePersistence()
            return updated
        }
    }

    fun deleteProfile(profileId: String): Boolean {
        synchronized(lock) {
            val removed = customProfiles.removeIf { it.id == profileId }
            if (!removed) {
                return false
            }
            if (activeProfileId == profileId) {
                activeProfileId = EVERYTHING_PROFILE_ID
            }
            updatePersistence()
            return true
        }
    }

    fun reloadFromPersistence(): McpToolFilterProfileSnapshot {
        synchronized(lock) {
            path.createParentDirectories()
            customProfiles.clear()

            if (path.notExists()) {
                activeProfileId = EVERYTHING_PROFILE_ID
                return snapshot()
            }

            val stored = try {
                Files.readString(path).fromJson<McpToolFilterProfilePersistence>()
            } catch (_: Exception) {
                McpToolFilterProfilePersistence(activeProfileId = EVERYTHING_PROFILE_ID, customProfiles = emptyList())
            }

            val catalog = McpToolCatalog.snapshot()
            customProfiles += stored.customProfiles.map { profile ->
                profile.copy(
                    isBuiltIn = false,
                    rules = normalizeRules(
                        input = profile.rules,
                        catalog = catalog,
                        defaultMode = ToolFilterMode.ENABLED
                    )
                )
            }
            activeProfileId = stored.activeProfileId
            if (findProfile(activeProfileId) == null) {
                activeProfileId = EVERYTHING_PROFILE_ID
            }
            return snapshot()
        }
    }

    private fun normalizedActiveProfileId(): String {
        return activeProfileId.takeIf { findProfile(it) != null } ?: EVERYTHING_PROFILE_ID
    }

    private fun findProfile(profileId: String?): McpToolFilterProfile? {
        if (profileId.isNullOrBlank()) {
            return null
        }
        return (builtInProfiles + customProfiles).firstOrNull { it.id == profileId }
    }

    private fun updatePersistence() {
        path.createParentDirectories()
        val stored = McpToolFilterProfilePersistence(
            activeProfileId = normalizedActiveProfileId(),
            customProfiles = customProfiles.map { profile ->
                profile.copy(isBuiltIn = false)
            }
        )
        Files.writeString(path, stored.toJson())
    }

    private fun expandedRules(profile: McpToolFilterProfile, catalog: McpToolFilterCatalogSnapshot): Map<String, ToolFilterMode> {
        val defaultMode = if (profile.id == NOTHING_PROFILE_ID) {
            ToolFilterMode.DISABLED
        } else {
            ToolFilterMode.ENABLED
        }
        return normalizeRules(profile.rules, catalog, defaultMode)
    }

    private fun normalizeRules(
        input: Map<String, ToolFilterMode>,
        catalog: McpToolFilterCatalogSnapshot,
        defaultMode: ToolFilterMode
    ): Map<String, ToolFilterMode> {
        return catalog.allRuleTargetIds().associateWith { input[it] ?: defaultMode }
    }

    companion object {
        const val EVERYTHING_PROFILE_ID = "everything"
        const val NOTHING_PROFILE_ID = "nothing"

        fun everythingProfile(): McpToolFilterProfile {
            return McpToolFilterProfile(
                id = EVERYTHING_PROFILE_ID,
                name = "Everything",
                isBuiltIn = true,
                rules = emptyMap()
            )
        }

        fun nothingProfile(): McpToolFilterProfile {
            return McpToolFilterProfile(
                id = NOTHING_PROFILE_ID,
                name = "Nothing",
                isBuiltIn = true,
                rules = emptyMap()
            )
        }
    }
}

object McpToolCatalog {
    private val lock = Any()
    private var cachedConfigText: String? = null
    private var cachedSnapshot: McpToolFilterCatalogSnapshot? = null

    fun snapshot(): McpToolFilterCatalogSnapshot {
        val configText = SystemPath.mcpConfigFile.readIfExists().orEmpty()
        synchronized(lock) {
            cachedSnapshot?.let { snapshot ->
                if (cachedConfigText == configText) {
                    return snapshot
                }
            }
        }

        val mcpConfig = configText.takeIf { it.isNotBlank() }?.fromJson<McpConfig>() ?: McpConfig(mapOf())
        val servers = mutableListOf<McpToolFilterServerSnapshot>()
        val tools = mutableListOf<McpToolFilterToolSnapshot>()

        mcpConfig.servers.entries.sortedBy { it.key }.forEach { (serverId, config) ->
            val client = try {
                buildMcpClient(serverId, config)
            } catch (e: Exception) {
                servers += serverSnapshot(serverId, false, e.message)
                return@forEach
            }

            try {
                val toolSpecs = client.listTools()
                servers += serverSnapshot(serverId, true, null)
                tools += toolSpecs
                    .sortedBy { it.name() }
                    .map { spec -> toolSnapshot(serverId, spec) }
            } catch (e: Exception) {
                servers += serverSnapshot(serverId, false, e.message)
            } finally {
                runCatching { client.close() }
            }
        }

        val snapshot = McpToolFilterCatalogSnapshot(
            servers = servers,
            tools = tools
        )
        synchronized(lock) {
            cachedConfigText = configText
            cachedSnapshot = snapshot
        }
        return snapshot
    }

    fun serverRuleTargetId(serverId: String): String = "server:$serverId"

    fun toolRuleTargetId(serverId: String, toolName: String): String = "tool:$serverId::$toolName"

    fun isEnabled(serverId: String, toolName: String, profile: McpToolFilterProfile?): Boolean {
        val rules = profile?.rules ?: emptyMap()
        return rules[serverRuleTargetId(serverId)] != ToolFilterMode.DISABLED &&
            rules[toolRuleTargetId(serverId, toolName)] != ToolFilterMode.DISABLED
    }

    internal fun buildMcpClient(serverId: String, config: McpStdioConfigItem): dev.langchain4j.mcp.client.McpClient {
        return DefaultMcpClient.builder()
            .key(serverId)
            .transport(
                StdioMcpTransport.builder()
                    .command(listOf(config.command) + config.args)
                    .environment(config.env)
                    .logEvents(true)
                    .build()
            )
            .build()
    }

    private fun serverSnapshot(serverId: String, isAvailable: Boolean, error: String?): McpToolFilterServerSnapshot {
        return McpToolFilterServerSnapshot(
            id = serverId,
            label = serverId,
            description = if (isAvailable) {
                "MCP server from local config."
            } else {
                "MCP server from local config. Tool listing failed."
            },
            isAvailable = isAvailable,
            error = error
        )
    }

    private fun toolSnapshot(serverId: String, spec: ToolSpecification): McpToolFilterToolSnapshot {
        return McpToolFilterToolSnapshot(
            id = toolRuleTargetId(serverId, spec.name()),
            toolName = spec.name(),
            serverId = serverId,
            label = spec.name(),
            description = spec.description().ifBlank { "MCP tool provided by $serverId." }
        )
    }
}
