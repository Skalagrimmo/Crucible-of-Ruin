package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.game.data.UserSave
import com.example.game.data.UserSaveRepository
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UserSaveRepositoryLocalTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clearPreferences() {
        listOf(
            "crucible_save_prefs",
            "crucible_save_prefs_guest",
            "crucible_save_prefs_account-a",
            "crucible_save_prefs_account-b"
        ).forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @Test
    fun localSnapshotsAreIsolatedBetweenAccountsAndGuest() {
        val repository = UserSaveRepository(context)

        repository.saveLocalUserSave(
            UserSave(userId = "account-a", savedSulfur = 1, savedMercury = 0, savedVitriol = 2)
        )
        repository.saveLocalUserSave(
            UserSave(userId = "account-b", savedSulfur = 7, savedMercury = 6, savedVitriol = 5)
        )

        assertEquals(1, repository.getLocalUserSave("account-a").savedSulfur)
        assertEquals(0, repository.getLocalUserSave("account-a").savedMercury)
        assertEquals(7, repository.getLocalUserSave("account-b").savedSulfur)
        assertEquals(6, repository.getLocalUserSave("account-b").savedMercury)

        // A user without an account receives a fresh guest snapshot, not account A's data.
        assertEquals(3, repository.getLocalUserSave("").savedSulfur)
        assertEquals(2, repository.getLocalUserSave("").savedMercury)
    }

    @Test
    fun localDeviceSettingSaveDoesNotAdvanceProgressTimestamp() {
        val repository = UserSaveRepository(context)
        repository.saveLocalUserSave(
            UserSave(
                userId = "account-a",
                savedAt = 123L,
                savedSulfur = 1,
                hapticsEnabled = true
            )
        )

        val local = repository.getLocalUserSave("account-a")
        val saved = repository.saveLocalUserSave(local.copy(hapticsEnabled = false))

        assertEquals(123L, saved.savedAt)
        assertEquals(123L, repository.getLocalUserSave("account-a").savedAt)
        assertEquals(false, repository.getLocalUserSave("account-a").hapticsEnabled)
    }
}
