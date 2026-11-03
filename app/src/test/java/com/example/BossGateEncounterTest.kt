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
        engine.player.x = 5000f
        engine.player.y = 430f - engine.player.h
        engine.inputRight = true
        var frames = 0
        while (!engine.boss.active && frames < 600) {
            engine.update(1f / 60f)
            frames++
        }
        assertTrue("The boss trigger must be reachable by walking from section three", engine.boss.active)
    }

    @Test
    fun arenaGatesStartOpenForEntry() {
        val engine = GameEngine()

        assertTrue("The arena should have exactly two gates", arenaGates(engine).size == 2)
        assertTrue(
            "Gates must start open so the arena is reachable",
            arenaGates(engine).all { it.collision == PlatformCollision.OPEN }
        )
    }

    @Test
    fun reachingTheBossTriggerSealsTheGates() {
        val engine = GameEngine()

        walkRightIntoTheArena(engine)

        assertTrue(
            "Both gates must be solid once the boss activates",
            arenaGates(engine).all { it.collision == PlatformCollision.SOLID }
        )
    }

    @Test
    fun sealedGatesBlockLeavingTheArena() {
        val engine = GameEngine()

        walkRightIntoTheArena(engine)
        engine.inputRight = false
        engine.inputLeft = true
        repeat(90) { engine.update(1f / 60f) }

        assertTrue(
            "The sealed left gate must keep the player inside the arena",
            engine.player.x >= 5270f
        )
    }

    @Test
    fun respawnAfterABossDeathReopensGatesAndResetsTheEncounter() {
        val engine = GameEngine()

        walkRightIntoTheArena(engine)

        engine.player.takeDamage(1000f, engine.player.x + 1f, engine)
        assertTrue("The player should die during the boss fight", engine.player.dead)

        repeat(300) {
            engine.update(1f / 60f)
            if (!engine.player.dead) break
        }
        assertFalse("The player should have respawned at a checkpoint", engine.player.dead)

        assertTrue(
            "Gates must reopen after a boss-fight death",
            arenaGates(engine).all { it.collision == PlatformCollision.OPEN }
        )
        assertFalse("The encounter must reset so the trigger can fire again", engine.boss.active)
    }
}
