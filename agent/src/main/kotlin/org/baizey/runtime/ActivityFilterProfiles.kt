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
enum class ActivityFilterMode {
    FULL,
    MINIFIED,
    HIDDEN
}

@Serializable
enum class ActivityFilterCategory(
    val wireName: String
) {
    USER_MESSAGE("user-message"),
    ASSISTANT_MESSAGE("assistant-message"),
    SYSTEM("system"),
    CONTROL("control"),
    MODEL("model"),
    THINKING("thinking"),
    TOOL("tool"),
    ERROR("error"),
    CUSTOM("custom");

    companion object {
        fun fromWireName(wireName: String): ActivityFilterCategory? {
            return entries.firstOrNull { it.wireName == wireName }
        }
    }
}

@Serializable
data class ActivityFilterProfile(
    val id: String,
    val name: String,
    val isBuiltIn: Boolean,
    val rules: Map<ActivityFilterCategory, ActivityFilterMode>
)

@Serializable
data class ActivityFilterProfilePersistence(
    val activeProfileId: String,
    val customProfiles: List<ActivityFilterProfileRecord>
)

@Serializable
data class ActivityFilterProfileRecord(
    val id: String,
    val name: String,
    val isBuiltIn: Boolean,
    val rules: Map<String, ActivityFilterMode>
)

data class ActivityFilterProfileSnapshot(
    val activeProfileId: String,
    val profiles: List<ActivityFilterProfile>
)

internal class ActivityFilterProfileStore(
    private val path: Path = SystemPath.activityFilterProfilesFile
) {
    private val lock = Any()
    private val customProfiles = mutableListOf<ActivityFilterProfile>()
    private val builtInProfiles = listOf(everythingProfile())

    private var activeProfileId = EVERYTHING_PROFILE_ID

    init {
        reloadFromPersistence()
    }

    fun snapshot(): ActivityFilterProfileSnapshot {
        synchronized(lock) {
            return ActivityFilterProfileSnapshot(
                activeProfileId = normalizedActiveProfileId(),
                profiles = builtInProfiles + customProfiles
            )
        }
    }

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

    fun createProfile(name: String, baseProfileId: String?): ActivityFilterProfile? {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            return null
        }

        synchronized(lock) {
            val baseProfile = findProfile(baseProfileId) ?: findProfile(normalizedActiveProfileId()) ?: everythingProfile()
            val profile = ActivityFilterProfile(
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

    fun updateProfile(profileId: String, name: String?, rules: Map<ActivityFilterCategory, ActivityFilterMode>?): ActivityFilterProfile? {
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

    fun reloadFromPersistence(): ActivityFilterProfileSnapshot {
        synchronized(lock) {
            path.createParentDirectories()
            customProfiles.clear()

            if (path.notExists()) {
                activeProfileId = EVERYTHING_PROFILE_ID
                return snapshot()
            }

            val stored = try {
                Files.readString(path).fromJson<ActivityFilterProfilePersistence>()
            } catch (_: Exception) {
                ActivityFilterProfilePersistence(activeProfileId = EVERYTHING_PROFILE_ID, customProfiles = emptyList())
            }

            customProfiles += stored.customProfiles.mapNotNull { profile ->
                profile.toDomainProfile()
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

    private fun findProfile(profileId: String?): ActivityFilterProfile? {
        if (profileId.isNullOrBlank()) {
            return null
        }
        return (builtInProfiles + customProfiles).firstOrNull { it.id == profileId }
    }

    private fun updatePersistence() {
        path.createParentDirectories()
        val stored = ActivityFilterProfilePersistence(
            activeProfileId = normalizedActiveProfileId(),
            customProfiles = customProfiles.map { profile -> profile.toPersistenceRecord() }
        )
        Files.writeString(path, stored.toJson())
    }

    private fun everythingProfile(): ActivityFilterProfile {
        return ActivityFilterProfile(
            id = EVERYTHING_PROFILE_ID,
            name = "Everything",
            isBuiltIn = true,
            rules = normalizeRules(ActivityFilterCategory.entries.associateWith { ActivityFilterMode.FULL })
        )
    }

    private fun normalizeRules(input: Map<ActivityFilterCategory, ActivityFilterMode>): Map<ActivityFilterCategory, ActivityFilterMode> {
        return ActivityFilterCategory.entries.associateWith { category ->
            input[category] ?: ActivityFilterMode.FULL
        }
    }

    private fun ActivityFilterProfile.toPersistenceRecord(): ActivityFilterProfileRecord {
        return ActivityFilterProfileRecord(
            id = id,
            name = name,
            isBuiltIn = isBuiltIn,
            rules = rules.mapKeys { (category, _) -> category.wireName }
        )
    }

    private fun ActivityFilterProfileRecord.toDomainProfile(): ActivityFilterProfile? {
        val normalizedRules = rules.mapNotNull { (category, mode) ->
            ActivityFilterCategory.fromWireName(category)?.let { it to mode }
        }.toMap()
        return ActivityFilterProfile(
            id = id,
            name = name,
            isBuiltIn = false,
            rules = normalizeRules(normalizedRules)
        )
    }

    companion object {
        const val EVERYTHING_PROFILE_ID = "everything"
    }
}
