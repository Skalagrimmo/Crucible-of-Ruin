package com.example

import com.example.game.GameEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Baseline tests for the existing simulation/physics behavior.
 *
 * These tests intentionally exercise GameEngine through its public update loop.
 * They do not change physics rules; they provide a safety net for future extraction
 * of movement and collision responsibilities out of GameEngine.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GameEnginePhysicsTest {

    @Test
    fun playerSettlesOnStartingPlatform() {
        val engine = GameEngine()

        repeat(120) {
            engine.update(1f / 60f)
        }

        assertTrue("Player should be grounded after falling to the starting floor", engine.player.grounded)
        assertEquals("Player should rest on the platform top", 416f, engine.player.y, 0.5f)
        assertEquals("Landing on the starting floor should not cause damage", 100f, engine.player.hp, 0.001f)
    }

    @Test
    fun fallingBelowLevelStartsPlayerDeathAndRespawnTimer() {
        val engine = GameEngine()
        // The gap at this x-coordinate has no solid floor. Start just above
        // the level's lower death boundary to isolate the pit-fall handling.
        engine.player.x = 630f
        engine.player.y = engine.levelHeight + 99f
        engine.player.vx = 0f
        engine.player.vy = 0f

        engine.update(0.05f)

        assertTrue("Falling below the level should mark the player dead", engine.player.dead)
        assertEquals("Pit-fall damage should exhaust player health", 0f, engine.player.hp, 0.001f)
        assertTrue("Death should start the automatic respawn countdown", engine.player.respawnTimer > 0f)
    }
}
