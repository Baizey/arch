package org.baizey.runtime.eventfilter

import org.baizey.runtime.ActivityFilterMode
import org.baizey.runtime.ActivityFilterCategory
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

        val snapshot = store.snapshot()

        assertEquals(ActivityFilterProfileStore.EVERYTHING_PROFILE_ID, snapshot.activeProfileId)
        assertEquals(
            listOf(ActivityFilterProfileStore.EVERYTHING_PROFILE_ID),
            snapshot.profiles.filter { it.isBuiltIn }.map { it.id }
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
        val persisted = reloaded.profiles.firstOrNull { it.id == created.id }

        assertEquals(created.id, reloaded.activeProfileId)
        assertNotNull(persisted)
        assertEquals(ActivityFilterMode.FULL, persisted!!.rules[ActivityFilterCategory.TOOL])
        assertEquals(ActivityFilterMode.MINIFIED, persisted.rules[ActivityFilterCategory.THINKING])
    }
}
