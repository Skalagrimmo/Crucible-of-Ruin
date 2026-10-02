package com.example.game

import com.example.game.data.UserSave
import com.example.game.data.isProgressAfter
import com.example.game.data.progressRank
import org.junit.Assert.*
import org.junit.Test

class UserSaveProgressTest {

    @Test
    fun laterLevelOutranksHigherCheckpointOnEarlierLevel() {
        val lateLevel01 = UserSave(lastLevelId = "level_01", lastCheckpoint = 3)
        val earlyLevel02 = UserSave(lastLevelId = "level_02", lastCheckpoint = 0)

        assertTrue(earlyLevel02.isProgressAfter(lateLevel01))
        assertFalse(lateLevel01.isProgressAfter(earlyLevel02))
    }

    @Test
    fun checkpointOrderingAppliesWithinSameLevel() {
        val first = UserSave(lastLevelId = "level_02", lastCheckpoint = 1)
        val later = UserSave(lastLevelId = "level_02", lastCheckpoint = 3)

        assertTrue(later.isProgressAfter(first))
        assertTrue(later.progressRank() > first.progressRank())
    }

    @Test
    fun legacyOrMalformedLevelFallsBackToLevelOneOrdering() {
        val legacy = UserSave(lastLevelId = "unknown", lastCheckpoint = 2)
        val level02 = UserSave(lastLevelId = "level_02", lastCheckpoint = 0)

        assertTrue(level02.isProgressAfter(legacy))
    }
}
