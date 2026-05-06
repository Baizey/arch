package org.baizey.runtime.eventfilter

import org.baizey.runtime.ActivityFilterMode
import org.baizey.runtime.ActivityFilterCategory
import org.baizey.runtime.ActivityFilterProfile
import org.baizey.runtime.ActivityFilterProfileSnapshot
import org.baizey.runtime.ActivityFilterProfileStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class ActivityFilterProfileStoreTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `starts with built in profiles`() {
        val store = ActivityFilterProfileStore(tempDir.resolve("activity_filter_profiles.json"))

        assertEquals(
            ActivityFilterProfileSnapshot(
                activeProfileId = ActivityFilterProfileStore.EVERYTHING_PROFILE_ID,
                profiles = listOf(
                    ActivityFilterProfile(
                        id = ActivityFilterProfileStore.EVERYTHING_PROFILE_ID,
                        name = "Everything",
                        isBuiltIn = true,
                        rules = ActivityFilterCategory.entries.associateWith { ActivityFilterMode.FULL }
                    )
                )
            ),
            store.snapshot()
        )
    }

    @Test
    fun `persists custom profiles and active selection`() {
        val path = tempDir.resolve("activity_filter_profiles.json")
        val store = ActivityFilterProfileStore(path)

        val created = store.createProfile("Tool focus", ActivityFilterProfileStore.EVERYTHING_PROFILE_ID)
        assertNotNull(created)

        val updated = store.updateProfile(
            profileId = created!!.id,
            name = "Tool focus",
            rules = created.rules +
                (ActivityFilterCategory.TOOL to ActivityFilterMode.FULL) +
                (ActivityFilterCategory.THINKING to ActivityFilterMode.MINIFIED)
        )
        assertNotNull(updated)
        store.selectProfile(created.id)

        val reloaded = ActivityFilterProfileStore(path).snapshot()
        assertEquals(created.id, reloaded.activeProfileId)
        assertEquals(updated, reloaded.profiles.firstOrNull { it.id == created.id })
    }
}
