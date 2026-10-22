package com.example

import com.example.game.pendingCounterDelta
import com.example.game.data.UserSave
import com.example.game.data.mergeUserSaves
import org.junit.Assert.assertEquals
import org.junit.Test

class SaveSyncRegressionTest {

    @Test
    fun newerLocalSnapshotKeepsConsumedReagentCounts() {
        val local = UserSave(
            userId = "account-a",
            savedAt = 200L,
            savedSulfur = 1,
            savedMercury = 0,
            savedVitriol = 2,
            highScore = 50
        )
        val cloud = UserSave(
            userId = "account-a",
            savedAt = 100L,
            savedSulfur = 5,
            savedMercury = 4,
            savedVitriol = 3,
            highScore = 40
        )

        val merged = mergeUserSaves(local, cloud, "account-a", localHasSave = true)

        assertEquals(1, merged.savedSulfur)
        assertEquals(0, merged.savedMercury)
        assertEquals(2, merged.savedVitriol)
        assertEquals(50, merged.highScore)
    }

    @Test
    fun newerCloudSnapshotWinsForConsumablesButKeepsLocalDeviceSettings() {
        val local = UserSave(
            userId = "account-a",
            savedAt = 100L,
            savedSulfur = 1,
            savedMercury = 1,
            savedVitriol = 0,
            targetFps = 60,
            soundMuted = true,
            hapticsEnabled = false
        )
        val cloud = UserSave(
            userId = "account-a",
            savedAt = 200L,
            savedSulfur = 2,
            savedMercury = 3,
            savedVitriol = 4,
            targetFps = 30,
            soundMuted = false,
            hapticsEnabled = true
        )

        val merged = mergeUserSaves(local, cloud, "account-a", localHasSave = true)

        assertEquals(2, merged.savedSulfur)
        assertEquals(3, merged.savedMercury)
        assertEquals(4, merged.savedVitriol)
        assertEquals(60, merged.targetFps)
        assertEquals(true, merged.soundMuted)
        assertEquals(false, merged.hapticsEnabled)
    }

    @Test
    fun cloudSnapshotSuppliesSettingsWhenNoLocalSnapshotExists() {
        val local = UserSave(userId = "account-a", savedAt = 0L, targetFps = 30)
        val cloud = UserSave(
            userId = "account-a",
            savedAt = 200L,
            targetFps = 60,
            soundMuted = true,
            hapticsEnabled = false
        )

        val merged = mergeUserSaves(local, cloud, "account-a", localHasSave = false)

        assertEquals(60, merged.targetFps)
        assertEquals(true, merged.soundMuted)
        assertEquals(false, merged.hapticsEnabled)
    }

    @Test
    fun bestTimeKeepsLowerPositiveValueAcrossSnapshots() {
        val local = UserSave(userId = "account-a", bestTimeSeconds = 85f, highScore = 10)
        val cloud = UserSave(userId = "account-a", bestTimeSeconds = 90f, highScore = 20)

        val merged = mergeUserSaves(local, cloud, "account-a", localHasSave = true)

        assertEquals(85f, merged.bestTimeSeconds, 0.001f)
        assertEquals(20, merged.highScore)
    }

    @Test
    fun checkpointDeltasDoNotCountPreviouslySavedEventsTwice() {
        assertEquals(3, pendingCounterDelta(current = 8, previouslySaved = 5))
        assertEquals(0, pendingCounterDelta(current = 5, previouslySaved = 5))
    }

    @Test
    fun counterDeltaHandlesEngineRestart() {
        // A lower counter means the run was restarted; start accumulating from the new run.
        assertEquals(2, pendingCounterDelta(current = 2, previouslySaved = 7))
    }
}
