package com.example

import com.example.game.GameEngine
import com.example.game.Platform
import com.example.game.PlatformCollision
import com.example.game.PlatformStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Stage 1 (#33): the simulation advances in fixed 1/60 s ticks regardless of the
 * display frame rate, so a 30 FPS cap simply pays two 60 Hz ticks per frame.
 *
 * Acceptance criteria from the issue:
 *  - a terminal-velocity drop onto an 18 px platform simulated at rawDt = 1/30 lands;
 *  - the jump apex is identical (±1 px) at 30 and 60 FPS;
 *  - a stalled frame's time debt is clamped instead of fast-forwarding the world.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FixedTimestepTest {

    @Test
    fun terminalVelocityDropOntoThinPlatformAtThirtyFpsLands() {
        val engine = GameEngine()
        engine.platforms.clear()
        engine.platforms.add(
            Platform(100f, 200f, 200f, 18f, PlatformStyle.IRON_GIRDER, PlatformCollision.ONE_WAY)
        )
        engine.hazards.clear()
        engine.enemies.clear()

        engine.player.x = 150f
        engine.player.y = -244f // feet at -200: 400 px above the 18 px-thick girder
        engine.player.vx = 0f
        engine.player.vy = 0f
        engine.player.grounded = false

        // A 30 FPS cap: each display frame pays two 60 Hz ticks, so the largest
        // per-tick fall step stays 12.5 px — well under the 18 px thickness.
        repeat(40) {
            engine.update(1f / 30f)
        }

        assertTrue("A terminal-velocity drop must land on the 18 px girder", engine.player.grounded)
        assertEquals("Feet must snap exactly to the girder top", 156f, engine.player.y, 0f)
        assertEquals("Landing must clear vertical velocity", 0f, engine.player.vy, 0f)
    }

    @Test
    fun jumpApexIsIdenticalAtThirtyAndSixtyFps() {
        fun jumpApex(rawDt: Float, frames: Int): Float {
            val engine = GameEngine()
            engine.platforms.clear()
            engine.platforms.add(Platform(0f, 460f, 400f, 80f, PlatformStyle.STONE))
            engine.hazards.clear()
            engine.enemies.clear()

            engine.player.x = 100f
            engine.player.y = 416f
            engine.player.vx = 0f
            engine.player.vy = 0f
            engine.player.grounded = true
            engine.player.coyoteTime = 0.12f
            engine.onJumpPressed()

            var apex = engine.player.y
            repeat(frames) {
                engine.update(rawDt)
                if (engine.player.y < apex) apex = engine.player.y
            }
            return apex
        }

        // One second of gameplay: 60 frames at the 60 FPS cap, 30 at the 30 FPS cap.
        val apexAtSixty = jumpApex(1f / 60f, 60)
        val apexAtThirty = jumpApex(1f / 30f, 30)

        assertEquals("Jump apex must be identical at both FPS caps", apexAtSixty, apexAtThirty, 1f)
        assertTrue("The recorded apex must be a real jump height", apexAtSixty < 350f)
    }

    @Test
    fun stalledFrameDebtIsClampedToFiveTicks() {
        val engine = GameEngine()
        engine.platforms.clear()
        engine.hazards.clear()
        engine.enemies.clear()
        engine.player.x = 100f
        engine.player.y = 100f
        engine.player.vx = 0f
        engine.player.vy = 0f
        engine.player.grounded = false

        // A multi-second stall (GC pause, app switch) must not fast-forward the
        // simulation by five real seconds.
        engine.update(5f)

        assertEquals("A stalled frame must pay at most five ticks of time", 5 * (1f / 60f), engine.timeElapsed, 0.0001f)
        assertTrue("The player must not have fallen for five simulated seconds", engine.player.y < 200f)
    }

    @Test
    fun pickupFallSpeedClampsAtTerminalVelocity() {
        val engine = GameEngine()
        engine.platforms.clear()
        engine.hazards.clear()
        engine.enemies.clear()

        val pk = engine.pickups.spawn(150f, 100f, "health")
        pk.vy = 5000f

        engine.update(1f / 60f)

        assertEquals(
            "Pickup fall speed must clamp at the shared terminal velocity",
            GameEngine.MAX_PICKUP_FALL_SPEED,
            pk.vy,
            0.001f
        )
    }

    @Test
    fun longPickupFallLandsOnThinGirderInsteadOfTunneling() {
        val engine = GameEngine()
        engine.platforms.clear()
        engine.platforms.add(Platform(100f, 200f, 200f, 18f, PlatformStyle.IRON_GIRDER))
        engine.hazards.clear()
        engine.enemies.clear()

        val pk = engine.pickups.spawn(150f, -300f, "health")
        pk.vy = 0f // free fall from 500 px above the 18 px girder

        repeat(40) {
            engine.update(1f / 30f)
        }

        assertEquals("The pickup must rest on the girder top", 184f, pk.y, 0.001f)
        assertEquals("Resting must clear the pickup's fall speed", 0f, pk.vy, 0.001f)
    }
}
