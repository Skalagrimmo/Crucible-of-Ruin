package com.skalagrimmo.crucibleofruin

import com.skalagrimmo.crucibleofruin.game.GameEngine
import com.skalagrimmo.crucibleofruin.game.GameLevelFactory
import com.skalagrimmo.crucibleofruin.game.GameLevelLayout
import com.skalagrimmo.crucibleofruin.game.GateTrigger
import com.skalagrimmo.crucibleofruin.game.Platform
import com.skalagrimmo.crucibleofruin.game.PlatformCollision
import com.skalagrimmo.crucibleofruin.game.PlatformStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Stage 8a (#56): the engine seams the Iron Garrison district is built on.
 *
 *  1. The boss encounter may only begin inside the corridor band (y < 540) —
 *     a player standing in the lower districts beyond the trigger x must not
 *     seal the arena from below.
 *  2. A PlatformStyle.IRON_GATE starts solid and blocks the street; crossing
 *     its layout trigger unlocks it with the same grid-synced swap the boss
 *     gates use, and a fresh layout re-seals it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ShortcutGateTest {

    private fun gateOf(engine: GameEngine): Platform =
        engine.platforms.first { it.style == PlatformStyle.IRON_GATE }

    /** A minimal fortress street: one floor, one sealed gate, one far-side trigger. */
    private fun fortressStreetLayout(): GameLevelLayout = GameLevelLayout(
        platforms = listOf(
            Platform(0f, 460f, 600f, 80f, PlatformStyle.STONE),
            Platform(
                x = 300f, y = 340f, w = 30f, h = 120f,
                style = PlatformStyle.IRON_GATE, collision = PlatformCollision.SOLID,
                gateId = "street_gate"
            )
        ),
        hazards = emptyList(),
        breakables = emptyList(),
        enemies = emptyList(),
        boss = GameLevelFactory.createBoss(),
        gateTriggers = listOf(
            GateTrigger(gateId = "street_gate", x = 420f, y = 440f, w = 60f, h = 60f)
        )
    )

    @Test
    fun theBossDoesNotActivateFromBelowTheCorridor() {
        val engine = GameEngine()
        engine.enemies.forEach { it.dead = true }
        // A player standing in the lower band under the arena (the Stage 8 space).
        engine.player.x = 5400f
        engine.player.y = 700f

        repeat(10) { engine.update(1f / 60f) }

        assertFalse("The fight must not begin from below the corridor", engine.boss.active)
        assertTrue(
            "The arena gates must stay open while the player is below the corridor",
            engine.platforms.filter { it.style == PlatformStyle.BOSS_GATE }
                .all { it.collision == PlatformCollision.OPEN }
        )
    }

    @Test
    fun theBossStillActivatesInsideTheCorridorBand() {
        val engine = GameEngine()
        engine.enemies.forEach { it.dead = true }
        engine.player.x = 5400f
        engine.player.y = 400f

        var frames = 0
        while (!engine.boss.active && frames < 30) {
            engine.update(1f / 60f)
            frames++
        }

        assertTrue("Crossing the trigger x inside the corridor must start the fight", engine.boss.active)
    }

    @Test
    fun theSealedGateBlocksTheStreet() {
        val engine = GameEngine()
        engine.initLevel(fortressStreetLayout())

        assertEquals(PlatformCollision.SOLID, gateOf(engine).collision)
        assertTrue(
            "The sealed gate must rasterize as solid cells",
            engine.levelGrid.rectTouchesCollision(
                gateOf(engine).x, gateOf(engine).y, gateOf(engine).w, gateOf(engine).h
            )
        )

        engine.inputRight = true
        repeat(150) { engine.update(1f / 60f) }

        assertTrue("The sealed gate must stop the player at its bars", engine.player.x < 295f)
    }

    @Test
    fun crossingTheFarSideTriggerOpensTheGate() {
        val engine = GameEngine()
        engine.initLevel(fortressStreetLayout())

        // Step into the far-side trigger rectangle (x 420..480, y 440..500).
        engine.player.x = 440f
        engine.player.y = 416f
        engine.update(1f / 60f)

        assertEquals(PlatformCollision.OPEN, gateOf(engine).collision)
        assertFalse(
            "The unlocked gate must no longer collide",
            engine.levelGrid.rectTouchesCollision(
                gateOf(engine).x, gateOf(engine).y, gateOf(engine).w, gateOf(engine).h
            )
        )

        // Staying inside the trigger is safe — the unlock is idempotent.
        repeat(10) { engine.update(1f / 60f) }
        assertEquals(PlatformCollision.OPEN, gateOf(engine).collision)
    }

    @Test
    fun theOpenedGateLetsThePlayerPass() {
        val engine = GameEngine()
        engine.initLevel(fortressStreetLayout())

        engine.player.x = 440f
        engine.player.y = 416f
        engine.update(1f / 60f)
        assertEquals(PlatformCollision.OPEN, gateOf(engine).collision)

        engine.player.x = 200f
        engine.player.y = 416f
        engine.inputRight = true
        repeat(90) { engine.update(1f / 60f) }

        assertTrue("The opened gate must let the player walk the street freely", engine.player.x > 400f)
    }

    @Test
    fun aFreshLayoutReSealsTheGate() {
        val engine = GameEngine()
        engine.initLevel(fortressStreetLayout())

        engine.player.x = 440f
        engine.player.y = 416f
        engine.update(1f / 60f)
        assertEquals(PlatformCollision.OPEN, gateOf(engine).collision)

        engine.initLevel(fortressStreetLayout())
        assertEquals(
            "A fresh layout must re-seal the gate for the new run",
            PlatformCollision.SOLID,
            gateOf(engine).collision
        )

        // And the re-sealed gate unlocks again when its trigger is crossed.
        engine.player.x = 440f
        engine.player.y = 416f
        engine.update(1f / 60f)
        assertEquals(PlatformCollision.OPEN, gateOf(engine).collision)
    }
}
