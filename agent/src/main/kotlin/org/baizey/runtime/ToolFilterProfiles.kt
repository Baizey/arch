package org.baizey.runtime

import kotlinx.serialization.Serializable
import org.baizey.utils.IO.fromJson
import org.baizey.utils.IO.toJson
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.createParentDirectories
import kotlin.io.path.notExists

@Serializable
enum class ToolFilterMode {
    ENABLED,
    DISABLED
}

@Serializable
data class ToolFilterProfile(
    val id: String,
    val name: String,
    val isBuiltIn: Boolean,
    val rules: Map<String, ToolFilterMode>
) {
    companion object {
        val NONE: ToolFilterProfile = ToolFilterProfile(
            id = "none",
            name = "None",
            isBuiltIn = true,
            rules = emptyMap()
        )
    }
}

@Serializable
data class ToolFilterProfilePersistence(
    val activeProfileId: String,
    val customProfiles: List<ToolFilterProfile>
)

data class ToolFilterProfileSnapshot(
    val activeProfileId: String,
    val profiles: List<ToolFilterProfile>
)

data class ToolFilterGroupSnapshot(
    val id: String,
    val label: String,
    val description: String
)

data class ToolFilterSubgroupSnapshot(
    val id: String,
    val groupId: String,
    val label: String,
    val description: String
)

data class ToolFilterToolSnapshot(
    val id: String,
    val toolName: String,
    val groupId: String,
    val subgroupId: String?,
    val label: String,
    val description: String
)

data class ToolFilterCatalogSnapshot(
    val groups: List<ToolFilterGroupSnapshot>,
    val subgroups: List<ToolFilterSubgroupSnapshot>,
    val tools: List<ToolFilterToolSnapshot>
)

internal class ToolFilterProfileStore(
    private val path: Path = SystemPath.toolFilterProfilesFile
) {
    private val lock = Any()
    private val customProfiles = mutableListOf<ToolFilterProfile>()
    private val builtInProfiles = listOf(everythingProfile(), noneProfile())

    private var activeProfileId = EVERYTHING_PROFILE_ID

    init {
        reloadFromPersistence()
    }

    fun snapshot(): ToolFilterProfileSnapshot {
        synchronized(lock) {
            return ToolFilterProfileSnapshot(
                activeProfileId = normalizedActiveProfileId(),
                profiles = builtInProfiles + customProfiles
            )
        }
    }

    fun activeProfile(): ToolFilterProfile {
        synchronized(lock) {
            return findProfile(normalizedActiveProfileId()) ?: everythingProfile()
        }
    }

    fun catalogSnapshot(): ToolFilterCatalogSnapshot = BuiltInToolCatalog.snapshot()

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

    fun createProfile(name: String, baseProfileId: String?): ToolFilterProfile? {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            return null
        }

        synchronized(lock) {
            val baseProfile =
                findProfile(baseProfileId) ?: findProfile(normalizedActiveProfileId()) ?: everythingProfile()
            val profile = ToolFilterProfile(
                id = "custom-${UUID.randomUUID()}",
                name = trimmedName,
                isBuiltIn = false,
                rules = normalizeRules(baseProfile.rules)
            )
            customProfiles += profile
            updatePersistence()
            return profile
        }
    }

    fun updateProfile(profileId: String, name: String?, rules: Map<String, ToolFilterMode>?): ToolFilterProfile? {
        synchronized(lock) {
            val index = customProfiles.indexOfFirst { it.id == profileId }
            if (index < 0) {
                return null
            }

            val existing = customProfiles[index]
            val updated = existing.copy(
                name = name?.trim()?.takeIf { it.isNotEmpty() } ?: existing.name,
                rules = normalizeRules(rules ?: existing.rules)
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

    fun reloadFromPersistence(): ToolFilterProfileSnapshot {
        synchronized(lock) {
            path.createParentDirectories()
            customProfiles.clear()

            if (path.notExists()) {
                activeProfileId = EVERYTHING_PROFILE_ID
                return snapshot()
            }

            val stored = try {
                Files.readString(path).fromJson<ToolFilterProfilePersistence>()
            } catch (_: Exception) {
                ToolFilterProfilePersistence(activeProfileId = EVERYTHING_PROFILE_ID, customProfiles = emptyList())
            }

            customProfiles += stored.customProfiles.map { profile ->
                profile.copy(
                    isBuiltIn = false,
                    rules = normalizeRules(profile.rules)
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

    private fun findProfile(profileId: String?): ToolFilterProfile? {
        if (profileId.isNullOrBlank()) {
            return null
        }
        return (builtInProfiles + customProfiles).firstOrNull { it.id == profileId }
    }

    private fun updatePersistence() {
        path.createParentDirectories()
        val stored = ToolFilterProfilePersistence(
            activeProfileId = normalizedActiveProfileId(),
            customProfiles = customProfiles.map { profile ->
                profile.copy(isBuiltIn = false, rules = normalizeRules(profile.rules))
            }
        )
        Files.writeString(path, stored.toJson())
    }

    private fun normalizeRules(input: Map<String, ToolFilterMode>): Map<String, ToolFilterMode> {
        val knownIds = BuiltInToolCatalog.allRuleTargetIds()
        return knownIds.associateWith { input[it] ?: ToolFilterMode.ENABLED }
    }

    companion object {
        const val EVERYTHING_PROFILE_ID = "everything"
        const val NONE_PROFILE_ID = "none"

        fun everythingProfile(): ToolFilterProfile {
            val knownIds = BuiltInToolCatalog.allRuleTargetIds()
            return ToolFilterProfile(
                id = EVERYTHING_PROFILE_ID,
                name = "Everything",
                isBuiltIn = true,
                rules = knownIds.associateWith { ToolFilterMode.ENABLED }
            )
        }

        fun noneProfile(): ToolFilterProfile {
            val knownIds = BuiltInToolCatalog.allRuleTargetIds()
            return ToolFilterProfile(
                id = NONE_PROFILE_ID,
                name = "None",
                isBuiltIn = true,
                rules = knownIds.associateWith { ToolFilterMode.DISABLED }
            )
        }
    }
}
