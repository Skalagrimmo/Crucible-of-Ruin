package com.example

import com.example.game.EnemyArchetype
import com.example.game.GameEngine
import com.example.game.Hazard
import com.example.game.HazardType
import com.example.game.Platform
import com.example.game.PlatformStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Characterization tests for the current hard-coded world layout.
 *
 * These checks protect representative landmarks and object counts before level
 * construction is moved out of GameEngine.initLevel().
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GameLevelLayoutTest {

    @Test
    fun initialWorldContainsExpectedLandmarksAcrossSections() {
        val engine = GameEngine()

        assertTrue(
            "Starting floor should remain at the start of section one",
            engine.platforms.contains(Platform(0f, 460f, 600f, 80f, PlatformStyle.STONE))
        )
        assertTrue(
            "The first platform gap should retain its acid hazard",
            engine.hazards.contains(Hazard(600f, 500f, 80f, 40f, HazardType.ACID))
        )
        assertTrue(
            "Section two should remain present",
            engine.platforms.contains(Platform(1920f, 420f, 380f, 120f, PlatformStyle.STONE))
        )
        assertTrue(
            "Section three should remain present",
            engine.platforms.contains(Platform(4360f, 420f, 320f, 120f, PlatformStyle.STONE))
        )
        assertTrue(
            "Boss arena gates should remain part of the level geometry",
            engine.platforms.contains(Platform(5270f, 100f, 30f, 350f, PlatformStyle.BOSS_GATE))
        )
        assertTrue(
            "The final approach should still contain its golem encounter",
            engine.enemies.any { it.x == 5120f && it.type == EnemyArchetype.GOLEM }
        )
    }

    @Test
    fun initialWorldObjectCountsRemainStable() {
        val engine = GameEngine()

        assertEquals("Unexpected number of platforms", 28, engine.platforms.size)
        assertEquals("Unexpected number of hazards", 6, engine.hazards.size)
        assertEquals("Unexpected number of enemies", 12, engine.enemies.size)
        assertEquals("Unexpected number of breakable urns", 4, engine.breakables.size)
    }
}
