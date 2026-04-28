package org.baizey.runtime.toolfilter

import org.baizey.runtime.ToolFilterMode
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
    fun `starts with built in everything profile`() {
        val store = ToolFilterProfileStore(tempDir.resolve("tool_filter_profiles.json"))

        val snapshot = store.snapshot()

        assertEquals(ToolFilterProfileStore.EVERYTHING_PROFILE_ID, snapshot.activeProfileId)
        assertEquals(
            listOf(ToolFilterProfileStore.EVERYTHING_PROFILE_ID),
            snapshot.profiles.filter { it.isBuiltIn }.map { it.id }
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
        val persisted = reloaded.profiles.firstOrNull { it.id == created.id }

        assertEquals(created.id, reloaded.activeProfileId)
        assertNotNull(persisted)
        assertEquals(ToolFilterMode.ENABLED, persisted!!.rules["tool:list_directory"])
        assertEquals(ToolFilterMode.ENABLED, persisted.rules["tool:read_file"])
    }
}
