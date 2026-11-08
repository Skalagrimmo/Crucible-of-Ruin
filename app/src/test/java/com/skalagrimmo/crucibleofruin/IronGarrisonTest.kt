package com.skalagrimmo.crucibleofruin

import com.skalagrimmo.crucibleofruin.game.GameEngine
import com.skalagrimmo.crucibleofruin.game.Platform
import com.skalagrimmo.crucibleofruin.game.PlatformCollision
import com.skalagrimmo.crucibleofruin.game.PlatformStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Stage 8b (#56): the Iron Garrison — the fortress district below the
 * Sanctum Approach.
 *
 * These tests pin the district's structure and loop semantics: the shaft in
 * the approach floor is the way down, the stacked stair under it is the way
 * back up (one base jump up to the corridor floor), the street bridges the
 * Undercroft's vault into the district, the fortress blocks wall the street
 * into roofless pockets, and the sealed keep gate opens only from the
 * elevated rooftop route — the #11 shortcut semantics.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class IronGarrisonTest {

    /** Ticks the fixed-step engine [ticks] times (one tick = 1/60 s). */
    private fun tick(engine: GameEngine, ticks: Int) {
        repeat(ticks) { engine.update(1f / 60f) }
    }

    @Test
    fun theApproachFloorSplitsAroundTheGarrisonShaft() {
        val engine = GameEngine()

        assertTrue(
            "The approach floor must keep its left segment",
            engine.platforms.contains(Platform(4360f, 420f, 140f, 120f, PlatformStyle.STONE))
        )
        assertTrue(
            "The approach floor must keep its right segment",
            engine.platforms.contains(Platform(4600f, 420f, 80f, 120f, PlatformStyle.STONE))
        )
        assertTrue(
            "No corridor geometry may block the shaft drop onto the stair (x 4500-4600)",
            engine.platforms.none {
                it.y < 520f && it.y + it.h > 430f &&
                    it.x < 4600f && it.x + it.w > 4500f &&
                    it.collision != PlatformCollision.OPEN
            }
        )
        assertTrue(
            "The district street must run the full width below the approach",
            engine.platforms.contains(Platform(4360f, 1000f, 1840f, 80f, PlatformStyle.STONE))
        )
    }

    @Test
    fun theShaftDropsOntoTheStairAndDownToTheStreet() {
        val engine = GameEngine()
        engine.enemies.forEach { it.dead = true }

        // Fall through the shaft from the corridor band — the left half of
        // the mouth lands on the stair's top step g5.
        engine.player.x = 4510f
        engine.player.y = 380f
        engine.player.vx = 0f
        engine.player.vy = 0f
        engine.player.grounded = false
        tick(engine, 60)

        assertTrue("The drop must land on the stair return's top step", engine.player.grounded)
        assertEquals("The top step sits one base jump below the corridor floor", 476f, engine.player.y, 0.5f)

        // Step off g5's right edge: the stacked g4 tier catches the drop
        // one stair step lower, so the shaft never traps the player.
        engine.player.x = 4560f
        engine.player.y = 476f
        engine.player.vx = 0f
        engine.player.vy = 0f
        engine.player.grounded = true
        engine.inputRight = true
        tick(engine, 7)
        engine.inputRight = false
        tick(engine, 35)

        assertTrue("The g5 walk-off must land on the g4 tier", engine.player.grounded)
        assertEquals("The g4 tier sits one stair rise below the top step", 576f, engine.player.y, 0.5f)
        assertTrue("The landing must stay on the g4 tier", engine.player.x > 4534f && engine.player.x < 4665f)

        // Walk off g4's left edge — the column beside the stair is clear all
        // the way down to the district street.
        engine.player.x = 4540f
        engine.player.y = 576f
        engine.player.vx = 0f
        engine.player.vy = 0f
        engine.player.grounded = true
        engine.inputLeft = true
        tick(engine, 7)
        engine.inputLeft = false
        tick(engine, 60)

        assertTrue("The stair walk-off must land on the district street", engine.player.grounded)
        assertEquals(956f, engine.player.y, 0.5f)
        assertTrue("The walk-off lands west of the stair column", engine.player.x > 4360f && engine.player.x < 4560f)
        assertEquals("The descent must stay safe", 100f, engine.player.hp, 0.001f)
    }

    @Test
    fun theStreetBridgesTheUndercroftVaultIntoTheDistrict() {
        val engine = GameEngine()
        engine.enemies.forEach { it.dead = true }

        // Stand on the Reliquary Vault floor and walk right — the street
        // floor continues at the same top past the shaft column.
        engine.player.x = 4330f
        engine.player.y = 956f
        engine.player.vx = 0f
        engine.player.vy = 0f
        engine.player.grounded = true
        engine.inputRight = true
        tick(engine, 150)

        assertTrue("The walk must cross onto the district street", engine.player.x > 4700f)
        assertTrue("The first fortress block must wall off the street", engine.player.x < 4780f)
        assertTrue("The walk stays on the street floor", engine.player.grounded)
        assertEquals(956f, engine.player.y, 0.5f)
        assertEquals("The pocket walk must stay safe", 100f, engine.player.hp, 0.001f)
    }

    @Test
    fun theSealedGateBlocksTheStreetWalkIntoTheKeep() {
        val engine = GameEngine()
        engine.enemies.forEach { it.dead = true }

        // Walk left from the keep's doorstep — the sealed gate must stop
        // the street walk between the pockets.
        engine.player.x = 5850f
        engine.player.y = 956f
        engine.player.vx = 0f
        engine.player.vy = 0f
        engine.player.grounded = true
        engine.inputLeft = true
        tick(engine, 120)

        assertTrue(
            "The sealed gate must stop the keep-side street walk",
            engine.player.x >= 5700f && engine.player.x <= 5730f
        )
        assertFalse(engine.player.dead)
    }

    @Test
    fun theElevatedRouteUnlocksTheSealedGate() {
        val engine = GameEngine()
        engine.enemies.forEach { it.dead = true }
        val gate = engine.platforms.first { it.style == PlatformStyle.IRON_GATE }
        assertEquals(PlatformCollision.SOLID, gate.collision)

        // Land on the elevated route's far side (L3, top 670) — the trigger
        // rectangle unlocks the gate below.
        engine.player.x = 5820f
        engine.player.y = 626f
        engine.player.vx = 0f
        engine.player.vy = 0f
        engine.player.grounded = true
        tick(engine, 2)

        // unlockGates replaces the immutable platform with an opened copy —
        // re-read the live instance instead of the pre-tick reference.
        val opened = engine.platforms.first { it.style == PlatformStyle.IRON_GATE }
        assertEquals("Crossing the far-side trigger must open the gate", PlatformCollision.OPEN, opened.collision)
        assertFalse(
            "The unlocked gate must no longer collide",
            engine.levelGrid.rectTouchesCollision(opened.x, opened.y, opened.w, opened.h)
        )

        // With the gate open, the street walk passes freely into the keep.
        engine.player.x = 5600f
        engine.player.y = 956f
        engine.player.vx = 0f
        engine.player.vy = 0f
        engine.player.grounded = true
        engine.inputRight = true
        tick(engine, 120)

        assertTrue("The opened gate must let the street walk into the keep", engine.player.x > 5800f)
    }

    @Test
    fun theStairReturnClimbsBackIntoTheCorridor() {
        val engine = GameEngine()
        engine.enemies.forEach { it.dead = true }

        // Stand on the stair's top step g5 (top 520) and jump left toward
        // the approach floor's left segment (top 420): the apex must clear
        // the corridor floor by the base-jump ruler.
        engine.player.x = 4520f
        engine.player.y = 476f
        engine.player.vx = 0f
        engine.player.vy = 0f
        engine.player.grounded = true
        engine.player.coyoteTime = 0.12f
        engine.inputLeft = true
        // Hold the jump for the full base-jump apex: without inputJump the
        // early-release cut shrinks the arc below the corridor floor's top.
        engine.inputJump = true
        engine.onJumpPressed()
        var minY = engine.player.y
        repeat(60) {
            engine.update(1f / 60f)
            minY = minOf(minY, engine.player.y)
        }

        assertTrue(
            "The jump from g5 must clear the corridor floor top (feet above 420)",
            minY < 376f
        )
        assertFalse(engine.player.dead)
        assertTrue("The player settles back on a standable surface", engine.player.grounded)
    }

    @Test
    fun checkpointFiveStandsOnTheKeepDoorstep() {
        val engine = GameEngine()
        assertEquals(5, engine.checkpoints.size)
        val cp = engine.checkpoints[4]
        assertEquals(4, cp.id)
        assertEquals(5850f, cp.x, 0f)
        assertEquals(956f, cp.y, 0f)
        assertFalse(cp.activated)

        // Proximity activation on the street just past the sealed gate.
        engine.enemies.forEach { it.dead = true }
        engine.player.x = 5850f
        engine.player.y = 956f
        engine.player.vx = 0f
        engine.player.vy = 0f
        tick(engine, 2)
        assertTrue(cp.activated)
        assertSame(engine.activeCheckpoint, cp)
    }

    @Test
    fun theGarrisonChartsOntoTheRoomGraph() {
        val engine = GameEngine()

        assertEquals("garrison_descent", engine.roomGraph.roomAt(4500f, 900f)!!.id)
        assertEquals("garrison_street", engine.roomGraph.roomAt(5000f, 900f)!!.id)
        assertEquals("garrison_keep", engine.roomGraph.roomAt(5850f, 700f)!!.id)

        // Starting at the keep checkpoint charts its room immediately.
        engine.setStartCheckpoint(4)
        assertEquals("garrison_keep", engine.currentRoom!!.id)

        // The vault now opens both ways — back through the Undercroft and
        // forward into the Garrison street — so the whole graph stays
        // walkable from either side of the loop.
        val vault = engine.roomGraph.roomById("reliquary_vault")!!
        assertEquals(
            "Every room stays walkable from the vault",
            engine.roomGraph.rooms.size,
            engine.roomGraph.reachableFrom(vault).size
        )
    }
}
