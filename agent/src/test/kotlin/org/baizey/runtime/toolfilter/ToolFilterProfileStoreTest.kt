package org.baizey.runtime.toolfilter

import org.baizey.runtime.BuiltInToolCatalog
import org.baizey.runtime.ToolFilterMode
import org.baizey.runtime.ToolFilterProfile
import org.baizey.runtime.ToolFilterProfileSnapshot
import org.baizey.runtime.ToolFilterProfileStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class ToolFilterProfileStoreTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `starts with the expected built in profiles`() {
        val store = ToolFilterProfileStore(tempDir.resolve("tool_filter_profiles.json"))

        assertEquals(
            ToolFilterProfileSnapshot(
                activeProfileId = ToolFilterProfileStore.EVERYTHING_PROFILE_ID,
                profiles = listOf(
                    expectedProfile(
                        id = ToolFilterProfileStore.EVERYTHING_PROFILE_ID,
                        name = "Everything",
                        enabledTargets = BuiltInToolCatalog.allRuleTargetIds()
                    ),
                    expectedProfile(
                        id = ToolFilterProfileStore.NOTHING_PROFILE_ID,
                        name = "Nothing",
                        enabledTargets = emptySet()
                    ),
                    expectedProfile(
                        id = ToolFilterProfileStore.WEB_ONLY_PROFILE_ID,
                        name = "Web Only",
                        enabledTargets = BuiltInToolCatalog.toolRuleTargetIdsForGroup("web").toSet()
                    ),
                    expectedProfile(
                        id = ToolFilterProfileStore.READONLY_PROFILE_ID,
                        name = "Read Only",
                        enabledTargets = buildSet {
                            addAll(BuiltInToolCatalog.toolRuleTargetIdsForSubgroup("fs-inspect"))
                            addAll(BuiltInToolCatalog.toolRuleTargetIdsForSubgroup("fs-read"))
                            addAll(BuiltInToolCatalog.toolRuleTargetIdsForGroup("web"))
                            addAll(BuiltInToolCatalog.toolRuleTargetIdsForSubgroup("git-read"))
                        }
                    ),
                    expectedProfile(
                        id = ToolFilterProfileStore.FS_ONLY_PROFILE_ID,
                        name = "Filesystem Only",
                        enabledTargets = BuiltInToolCatalog.toolRuleTargetIdsForGroup("fs").toSet()
                    )
                )
            ),
            store.snapshot()
        )
    }

    @Test
    fun `persists custom profiles and active selection`() {
        val path = tempDir.resolve("tool_filter_profiles.json")
        val store = ToolFilterProfileStore(path)

        val created = store.createProfile("Filesystem tools", ToolFilterProfileStore.EVERYTHING_PROFILE_ID)
        assertNotNull(created)

        val updated = store.updateProfile(
            profileId = created!!.id,
            name = "Filesystem tools",
            rules = mapOf(
                "tool:list_directory" to ToolFilterMode.ENABLED,
                "tool:read_file" to ToolFilterMode.ENABLED
            )
        )
        assertNotNull(updated)
        store.selectProfile(created.id)

        val reloaded = ToolFilterProfileStore(path).snapshot()
        assertEquals(created.id, reloaded.activeProfileId)
        assertEquals(
            updated,
            reloaded.profiles.firstOrNull { it.id == created.id }
        )
    }

    private fun expectedProfile(
        id: String,
        name: String,
        enabledTargets: Set<String>
    ): ToolFilterProfile {
        return ToolFilterProfile(
            id = id,
            name = name,
            isBuiltIn = true,
            rules = BuiltInToolCatalog.allRuleTargetIds().associateWith { ruleTarget ->
                if (ruleTarget in enabledTargets) ToolFilterMode.ENABLED else ToolFilterMode.DISABLED
            }
        )
    }
}
