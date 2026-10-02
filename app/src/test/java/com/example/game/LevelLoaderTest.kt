package com.example.game

import org.junit.Assert.*
import org.junit.Test

class LevelLoaderTest {

    @Test
    fun level01_matchesOriginalLayoutCountsAndBounds() {
        val def = LevelCatalog.level01

        assertEquals(6200f, def.width, 0.001f)
        assertEquals(540f, def.height, 0.001f)
        assertEquals(28, def.platforms.size)
        assertEquals(6, def.hazards.size)
        assertEquals(12, def.enemies.size)
        assertEquals(4, def.breakables.size)
        assertEquals(3, def.checkpoints.size)
        assertEquals(5750f, def.boss.x, 0.001f)
        assertEquals(360f, def.boss.y, 0.001f)
    }

    @Test
    fun loader_createsFreshMutableRuntimeObjects() {
        val def = LevelCatalog.level01

        val firstCheckpoints = LevelLoader.checkpoints(def)
        val secondCheckpoints = LevelLoader.checkpoints(def)
        firstCheckpoints[0].activated = true

        assertTrue(firstCheckpoints[0].activated)
        assertFalse(secondCheckpoints[0].activated)

        val firstEnemies = LevelLoader.enemies(def)
        val secondEnemies = LevelLoader.enemies(def)
        assertNotSame(firstEnemies[0], secondEnemies[0])

        val firstBoss = LevelLoader.boss(def)
        val secondBoss = LevelLoader.boss(def)
        assertNotSame(firstBoss, secondBoss)
    }

    @Test
    fun level01_checkpointIdentityAndOrder_areStable() {
        val checkpoints = LevelCatalog.level01.checkpoints

        assertEquals(listOf(0, 1, 2), checkpoints.map { it.id })
        assertEquals("Section 1: Ruined Bastion", checkpoints[0].name)
        assertEquals("Section 2: Crucible Laboratories", checkpoints[1].name)
        assertEquals("Section 3: Sanctum Approach", checkpoints[2].name)
    }
}
