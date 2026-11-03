package com.example

import com.example.game.GameEngine
import com.example.game.PlatformCollision
import com.example.game.PlatformStyle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression coverage for the boss-arena gate encounter state.
 *
 * The arena gates start open so the approach is walkable, seal when the boss
 * activates, and reopen with a fresh encounter after a death during the fight.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BossGateEncounterTest {

    private fun arenaGates(engine: GameEngine) =
        engine.platforms.filter { it.style == PlatformStyle.BOSS_GATE }

    private fun walkRightIntoTheArena(engine: GameEngine) {
        // Isolate traversal: neutralize the enemies on the approach stretch.
        engine.enemies.forEach { it.dead = true }
        // Start standing on the section-three approach floor (x: 5070..5270, top y=430).
        // Starting further left (e.g. x=5000) hovers over the acid gap and falls into the pit.
        engine.player.x = 5100f
        engine.player.y = 430f - engine.player.h
        engine.inputRight = true
        var frames = 0
        while (!engine.boss.active && frames < 600) {
            engine.update(1f / 60f)
            frames++
        }
        assertTrue("The boss trigger must be reachable by walking from section three", engine.boss.active)
    }
