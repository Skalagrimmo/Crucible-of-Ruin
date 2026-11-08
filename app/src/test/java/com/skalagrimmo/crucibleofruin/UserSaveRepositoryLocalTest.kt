package com.skalagrimmo.crucibleofruin

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.skalagrimmo.crucibleofruin.game.data.UserSave
import com.skalagrimmo.crucibleofruin.game.data.UserSaveRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UserSaveRepositoryLocalTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun freshDeviceReturnsDefaultSave() {
        val repository = UserSaveRepository(context)

        val save = repository.getUserSave()

        assertEquals("Alchemist", save.displayName)
        assertEquals(0, save.highScore)
        assertEquals(0, save.lastCheckpoint)
        assertEquals(3, save.savedSulfur)
        assertEquals(2, save.savedMercury)
        assertEquals(1, save.savedVitriol)
        assertTrue(save.hapticsEnabled)
    }

    @Test
    fun saveUserSaveAdvancesProgressTimestamp() {
        val repository = UserSaveRepository(context)

        val persisted = repository.saveUserSave(UserSave(savedAt = 123L, foesSlain = 9))

        assertTrue(persisted.savedAt > 123L)
        assertEquals(9, repository.getUserSave().foesSlain)
        assertEquals(persisted.savedAt, repository.getUserSave().savedAt)
    }

    @Test
    fun saveLocalUserSavePreservesProgressTimestamp() {
        val repository = UserSaveRepository(context)
        repository.saveLocalUserSave(UserSave(savedAt = 123L, hapticsEnabled = true))

        val local = repository.getLocalUserSave()
        val saved = repository.saveLocalUserSave(local.copy(hapticsEnabled = false))

        assertEquals(123L, saved.savedAt)
        assertEquals(123L, repository.getLocalUserSave().savedAt)
        assertEquals(false, repository.getLocalUserSave().hapticsEnabled)
    }

    @Test
    fun adoptsFreshestLegacySnapshotOnce() {
        // Two Firebase-era snapshots survive on the device: an older guest save
        // and a newer per-account save. The repository must adopt the freshest.
        context.getSharedPreferences("crucible_save_prefs_guest", Context.MODE_PRIVATE)
            .edit()
            .putInt("foes_slain", 2)
            .putLong("saved_at", 500L)
            .commit()
        context.getSharedPreferences("crucible_save_prefs_account-a", Context.MODE_PRIVATE)
            .edit()
            .putString("display_name", "Legacy Alchemist")
            .putInt("foes_slain", 9)
            .putLong("saved_at", 900L)
            .commit()

        val repository = UserSaveRepository(context)
        val adopted = repository.getUserSave()

        assertEquals("Legacy Alchemist", adopted.displayName)
        assertEquals(9, adopted.foesSlain)
        assertEquals(900L, adopted.savedAt)
    }

    @Test
    fun secondSaveOverwritesAdoptedLegacySnapshot() {
        context.getSharedPreferences("crucible_save_prefs", Context.MODE_PRIVATE)
            .edit()
            .putInt("foes_slain", 4)
            .putLong("saved_at", 700L)
            .commit()

        val repository = UserSaveRepository(context)
        assertEquals(4, repository.getUserSave().foesSlain)

        repository.saveUserSave(UserSave(foesSlain = 12))
        assertEquals(12, repository.getUserSave().foesSlain)
    }
}
