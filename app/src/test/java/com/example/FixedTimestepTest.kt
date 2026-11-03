package com.example

import com.example.game.Camera2D
import com.example.game.GameEngine
import com.example.game.Platform
import com.example.game.PlatformCollision
import com.example.game.PlatformStyle
import kotlin.math.min
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Stage 1 (#33): the simulation must advance in fixed 1/60 s ticks regardless of the
 * frame-rate cap the app offers (30 or 60 FPS), with a clamped time debt so stalls
 * cannot spiral into catch-up bursts.
 *
 * Acceptance (from issue #33):
 * - a terminal-velocity drop through an 18 px platform simulated at rawDt = 1/30 lands;
 * - jump apex height is identical (plus/minus 1 px) at the 30 and 60 FPS caps;
 * - camera and shake decay rates no longer depend on the tick rate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FixedTimestepTest {

    private fun flatFloorEngine(): GameEngine {
        val engine = GameEngine()
        engine.platforms.clear()
        engine.platforms.add(Platform(0f, 460f, 400f, 80f, PlatformStyle.STONE))
        engine.hazards.clear()
        engine.enemies.clear()

        engine.player.x = 200f
        engine.player.y = 460f - engine.player.h
        engine.player.vx = 0f
        engine.player.vy = 0f
        engine.player.grounded = true
        return engine
    }

    @Test
    fun terminalVelocityDropLandsOnThinPlatformAtThirtyFps() {
        val engine = GameEngine()
        engine.platforms.clear()
        // 18 px-thick solid girder: at the old variable dt a 1/30 s step at terminal
        // velocity (750 px/s) moved the player 25 px — past the whole platform body.
        engine.platforms.add(
            Platform(100f, 300f, 200f, 18f, PlatformStyle.IRON_GIRDER, PlatformCollision.SOLID)
        )
        engine.hazards.clear()
        engine.enemies.clear()

        engine.player.x = 150f
        engine.player.y = 100f
        engine.player.vx = 0f
        engine.player.vy = 750f // terminal fall speed
        engine.player.grounded = false

        repeat(30) { engine.update(1f / 30f) }

        assertTrue("A terminal-velocity drop must not tunnel through a thin platform", engine.player.grounded)
        assertEquals("Feet must rest exactly on the platform top", 256f, engine.player.y, 0f)
        assertEquals("Landing must clear vertical velocity", 0f, engine.player.vy, 0f)
    }

    @Test
    fun jumpApexIsIdenticalAtThirtyAndSixtyFps() {
        val engine30 = flatFloorEngine()
        val engine60 = flatFloorEngine()

        engine30.onJumpPressed()
        engine60.onJumpPressed()

        var apex30 = Float.MAX_VALUE
        var apex60 = Float.MAX_VALUE
        repeat(30) {
            engine30.update(1f / 30f) // two fixed ticks per call
            apex30 = min(apex30, engine30.player.y)
        }
        repeat(60) {
            engine60.update(1f / 60f) // one fixed tick per call
            apex60 = min(apex60, engine60.player.y)
        }

        assertTrue("Sanity: the player should actually have jumped", apex30 < 416f)
        assertEquals("Jump apex must not depend on the frame-rate cap", apex60, apex30, 1f)
    }

    @Test
    fun updateRunsAtMostFiveFixedTicksAndClampsTimeDebt() {
        val engine = flatFloorEngine()

        // A single huge delta (debugger stall, resume from pause) must never simulate
        // more than MAX_TICKS_PER_FRAME fixed ticks...
        engine.update(1f)
        assertEquals(5f * GameEngine.FIXED_DT, engine.timeElapsed, 1e-4f)

        // ...and the leftover debt is clamped to a single tick, so the next call only
        // replays one tick's worth of the stall instead of the whole burst.
        engine.update(GameEngine.FIXED_DT)
        assertEquals(7f * GameEngine.FIXED_DT, engine.timeElapsed, 1e-4f)
    }

    @Test
    fun cameraSmoothingAndShakeDecayAreFrameRateIndependent() {
        val cam60 = Camera2D()
        val cam30 = Camera2D()
        cam60.shake(24f)
        cam30.shake(24f)

        // One simulated second, delivered as 60 single ticks vs. 30 double ticks.
        repeat(60) { cam60.update(1000f, 300f, 1, 2000f, 1000f, GameEngine.FIXED_DT) }
        repeat(30) { cam30.update(1000f, 300f, 1, 2000f, 1000f, 1f / 30f) }

        assertEquals("Camera shake must decay at the same rate per second", cam60.shakeIntensity, cam30.shakeIntensity, 0.01f)
        assertEquals("Camera follow must converge identically per second", cam60.x, cam30.x, 0.5f)
        assertEquals("Camera vertical follow must converge identically per second", cam60.y, cam30.y, 0.5f)
    }
}
