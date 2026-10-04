package com.example.game.data

import org.junit.Assert.assertEquals
import org.junit.Test

class UserSaveProgressRegressionTest {

    @Test
    fun earlierLevelCheckpoint_cannotRegressLaterLevelProgress() {
        val saved = UserSave(lastLevelId = "level_02", lastCheckpoint = 2)

        val result = saved.withProgressAtLeast("level_01", 2)

        assertEquals("level_02", result.lastLevelId)
        assertEquals(2, result.lastCheckpoint)
    }

    @Test
    fun laterCheckpointOnSameLevel_advancesProgress() {
        val saved = UserSave(lastLevelId = "level_02", lastCheckpoint = 1)

        val result = saved.withProgressAtLeast("level_02", 3)

        assertEquals("level_02", result.lastLevelId)
        assertEquals(3, result.lastCheckpoint)
    }

    @Test
    fun laterLevel_advancesProgress() {
        val saved = UserSave(lastLevelId = "level_01", lastCheckpoint = 2)

        val result = saved.withProgressAtLeast("level_02", 0)

        assertEquals("level_02", result.lastLevelId)
        assertEquals(0, result.lastCheckpoint)
    }
}
