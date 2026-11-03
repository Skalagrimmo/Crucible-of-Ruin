package com.example

import com.example.game.GameEngine
import com.example.game.Hazard
import com.example.game.HazardType
import com.example.game.Platform
import com.example.game.PlatformStyle
import com.example.game.PlatformCollision
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
 * They do not change physics rules; they provide a safety net for the remaining
 * movement/collision responsibilities and future refinements.
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
    fun walkingOffPlatformEdgeLeavesPlayerFallingAcrossGap() {
        val engine = GameEngine()
        engine.platforms.clear()
        engine.platforms.add(Platform(0f, 460f, 100f, 80f, PlatformStyle.STONE))
        engine.hazards.clear()
        engine.enemies.clear()

        engine.player.x = 80f
        engine.player.y = 416f
        engine.player.vx = 0f
        engine.player.vy = 0f
        engine.player.grounded = true
        engine.player.coyoteTime = 0.12f
        engine.inputRight = true

        repeat(30) {
            engine.update(1f / 60f)
        }

        assertTrue("Player should move beyond the edge of the short test platform", engine.player.x > 100f)
        assertTrue("Player should fall when no platform remains beneath them", engine.player.y > 460f)
        assertTrue("Falling across the gap should not be treated as grounded", !engine.player.grounded)
        assertEquals("A harmless gap without hazards should not damage the player", 100f, engine.player.hp, 0.001f)
    }

    @Test
    fun coyoteWindowAllowsJumpImmediatelyAfterLeavingPlatformEdge() {
        val engine = GameEngine()
        engine.platforms.clear()
        engine.platforms.add(Platform(0f, 460f, 100f, 80f, PlatformStyle.STONE))
        engine.hazards.clear()
        engine.enemies.clear()

        // The player's left edge has just cleared the platform, but the coyote
        // timer from the most recent landing has not expired.
        engine.player.x = 101f
        engine.player.y = 416f
        engine.player.vx = 0f
        engine.player.vy = 0f
        engine.player.grounded = false
        engine.player.coyoteTime = 0.12f
        engine.onJumpPressed()

        engine.update(1f / 60f)

        assertTrue("A buffered jump inside the coyote window should launch upward", engine.player.vy < -450f)
        assertEquals("Successful jump should consume the jump buffer", 0f, engine.player.jumpBuffer, 0f)
        assertEquals("Successful jump should consume the coyote timer", 0f, engine.player.coyoteTime, 0f)
    }

    @Test
    fun jumpPressedBeforeLandingIsBufferedUntilThePlayerTouchesThePlatform() {
        val engine = GameEngine()
        engine.player.x = 200f
        engine.player.y = 410f
        engine.player.vx = 0f
        engine.player.vy = 240f
        engine.player.grounded = false
        engine.inputJump = true
        engine.onJumpPressed()

        repeat(4) {
            engine.update(1f / 60f)
        }

        assertTrue("Buffered jump should launch the player after landing", engine.player.vy < 0f)
        assertTrue("Player should have left the platform upward after the buffered jump", engine.player.y < 416f)
        assertEquals("Successful jump should consume the jump buffer", 0f, engine.player.jumpBuffer, 0f)
    }

    @Test
    fun platformStylesDeclareTheirDefaultCollisionBehavior() {
        val stoneLedge = Platform(0f, 100f, 80f, 16f, PlatformStyle.STONE_LEDGE)
        val ironGirder = Platform(100f, 100f, 80f, 16f, PlatformStyle.IRON_GIRDER)
        val stoneFloor = Platform(200f, 100f, 80f, 16f, PlatformStyle.STONE)

        assertEquals(PlatformCollision.ONE_WAY, stoneLedge.collision)
        assertEquals(PlatformCollision.ONE_WAY, ironGirder.collision)
        assertEquals(PlatformCollision.SOLID, stoneFloor.collision)
    }

    @Test
    fun oneWayPlatformSnapsLandingToExactTopAtThirtyFps() {
        val engine = GameEngine()
        engine.platforms.clear()
        engine.platforms.add(
            Platform(100f, 200f, 200f, 18f, PlatformStyle.IRON_GIRDER, PlatformCollision.ONE_WAY)
        )
        engine.hazards.clear()
        engine.enemies.clear()

        engine.player.x = 150f
        engine.player.y = 100f
        engine.player.vx = 0f
        engine.player.vy = 0f
        engine.player.grounded = false

        repeat(20) {
            engine.update(1f / 30f)
        }

        assertTrue("A descending player should land on a one-way surface", engine.player.grounded)
        assertEquals("Feet should snap exactly to the top surface", 156f, engine.player.y, 0f)
        assertEquals("Landing should clear vertical velocity", 0f, engine.player.vy, 0f)
    }

    @Test
    fun oneWayPlatformCanBePassedThroughFromBelow() {
        val engine = GameEngine()
        engine.platforms.clear()
        engine.platforms.add(
            Platform(100f, 200f, 200f, 18f, PlatformStyle.STONE_LEDGE, PlatformCollision.ONE_WAY)
        )
        engine.hazards.clear()
        engine.enemies.clear()

        engine.player.x = 150f
        engine.player.y = 220f
        engine.player.vx = 0f
        engine.player.vy = -500f
        engine.player.grounded = false
        engine.inputJump = true

        repeat(20) {
            engine.update(1f / 60f)
        }

        assertTrue("The player should pass through the platform underside", engine.player.y + engine.player.h < 200f)
        assertTrue("The player should still be travelling upward", engine.player.vy < 0f)
    }

    @Test
    fun oneWayPlatformDoesNotBlockHorizontalMovementFromTheSide() {
        val engine = GameEngine()
        engine.platforms.clear()
        engine.platforms.add(
            Platform(100f, 200f, 200f, 18f, PlatformStyle.IRON_GIRDER, PlatformCollision.ONE_WAY)
        )
        engine.hazards.clear()
        engine.enemies.clear()

        engine.player.x = 60f
        engine.player.y = 180f
        engine.player.vx = 160f
        engine.player.vy = 0f
        engine.player.grounded = false

        repeat(12) {
            engine.update(1f / 60f)
        }

        assertTrue("A one-way platform should not behave like a side wall", engine.player.x > 70f)
    }

    @Test
    fun hazardContactDamagesPlayerOnceWhileInvulnerabilityPreventsRepeatDamage() {
        val engine = GameEngine()
        engine.platforms.clear()
        engine.hazards.clear()
        engine.hazards.add(Hazard(100f, 100f, 100f, 50f, HazardType.ACID))
        engine.enemies.clear()

        engine.player.x = 120f
        engine.player.y = 100f
        engine.player.vx = 0f
        engine.player.vy = 0f
        engine.player.invulnTimer = 0f

        engine.update(1f / 60f)

        assertEquals("First hazard contact should apply the configured damage once", 75f, engine.player.hp, 0.001f)
        assertEquals("Hazard contact should apply upward knockback", -280f, engine.player.vy, 0f)
        assertTrue("Hazard damage should start an invulnerability window", engine.player.invulnTimer > 0f)

        // The player remains inside the hazard for another frame. The overlap
        // may continue, but the active invulnerability window must prevent
        // another health deduction.
        engine.update(1f / 60f)

        assertEquals("Repeated overlap during invulnerability must not repeat damage", 75f, engine.player.hp, 0.001f)
        assertTrue("Invulnerability should still be active on the following frame", engine.player.invulnTimer > 0f)
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

        // The fixed-step accumulator drains at most five 1/60 s ticks per call, and a
        // 0.05 s delta only yields two ticks of float time (3 * 1/60 rounds above
        // 0.05f) — not enough fall distance to cross the death boundary from one
        // pixel above it. Run the same 0.05 s span as three explicit fixed ticks so
        // the pit fall still crosses the boundary.
        repeat(3) { engine.update(1f / 60f) }

        assertTrue("Falling below the level should mark the player dead", engine.player.dead)
        assertEquals("Pit-fall damage should exhaust player health", 0f, engine.player.hp, 0.001f)
        assertTrue("Death should start the automatic respawn countdown", engine.player.respawnTimer > 0f)
    }
}
