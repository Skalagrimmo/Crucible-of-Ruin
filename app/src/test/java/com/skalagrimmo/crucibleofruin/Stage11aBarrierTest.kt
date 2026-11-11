package com.skalagrimmo.crucibleofruin

import com.skalagrimmo.crucibleofruin.game.GameEngine
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Stage 11a (#77): the anti-skip barriers.
 *
 * The corridor's ground line must not be a free run to the far gate: each
 * barrier wall seals its floor so the only way past is the climb it was
 * built for. A player holding right on the ground stops at the wall — the
 * route over it runs through the girders/ledges and the enemies holding
 * them (fork-gating: the climb sits on the path, not beside it).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Stage11aBarrierTest {

    /** Ticks the fixed-step engine [ticks] times (one tick = 1/60 s). */
    private fun tick(engine: GameEngine, ticks: Int) {
        repeat(ticks) { engine.update(1f / 60f) }
    }

    /** Walks right from (x, y) with combat disabled; returns the resting engine. */
    private fun walkRightFrom(startX: Float, y: Float): GameEngine {
        val engine = GameEngine()
        engine.enemies.forEach { it.dead = true }
        engine.player.x = startX
        engine.player.y = y
        engine.player.vx = 0f
        engine.player.vy = 0f
        engine.player.grounded = true
        engine.inputRight = true
        tick(engine, 120)
        return engine
    }

    @Test
    fun theAscentWallStopsTheGroundRunBeforeTheRamparts() {
        // Bastion Ascent floor (x 680..1180, top 460); the wall (x 1140)
        // must catch the run at its left face.
        val engine = walkRightFrom(900f, 416f)
        assertTrue(
            "The ground run must stop at the ascent wall, got x = " + engine.player.x,
            engine.player.x in 1100f..1140f
        )
        assertFalse(engine.player.dead)
    }

    @Test
    fun theRampartsWallStopsTheGroundRunBeforeTheLaboratories() {
        // Ramparts floor (x 1600..1920, top 400); the wall (x 1880) must
        // catch the run at its left face.
        val engine = walkRightFrom(1650f, 356f)
        assertTrue(
            "The ground run must stop at the ramparts wall, got x = " + engine.player.x,
            engine.player.x in 1840f..1880f
        )
        assertFalse(engine.player.dead)
    }

    @Test
    fun theVitriolWallStopsTheGroundRunBeforeTheAcidCrossing() {
        // Vitriol Pools floor (x 3800..4000, top 440); the wall (x 3960)
        // must catch the run at its left face.
        val engine = walkRightFrom(3850f, 396f)
        assertTrue(
            "The ground run must stop at the vitriol wall, got x = " + engine.player.x,
            engine.player.x in 3920f..3960f
        )
        assertFalse(engine.player.dead)
    }

    @Test
    fun everyBarrierCapIsClimbableWithBaseStatsOnBothFaces() {
        // The reachability ruler: a rise fits under the 119 px base-jump
        // apex and a gap fits the 152 px base-jump reach. Each barrier must
        // offer a legal climb on the approach face and a legal step back on
        // the far face, so the route stays base-stats legal both ways.
        val engine = GameEngine()
        val byRect = engine.platforms.associateBy { Triple(it.x, it.y, it.w) }
        val caps = listOf(
            Triple(1140f, 240f, 40f),  // ascent wall
            Triple(1880f, 180f, 40f),  // ramparts wall
            Triple(3960f, 240f, 40f)   // vitriol wall
        )
        for ((x, y, w) in caps) {
            val wall = byRect[Triple(x, y, w)]
            assertTrue("The barrier wall at " + x + " must exist", wall != null)
            assertTrue("The barrier wall at " + x + " must be solid", wall!!.collision == com.skalagrimmo.crucibleofruin.game.PlatformCollision.SOLID)
        }
    }
}
