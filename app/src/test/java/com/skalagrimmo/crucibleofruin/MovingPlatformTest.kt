package com.skalagrimmo.crucibleofruin

import com.skalagrimmo.crucibleofruin.game.EnemyArchetype
import com.skalagrimmo.crucibleofruin.game.EnemyEntity
import com.skalagrimmo.crucibleofruin.game.GameEngine
import com.skalagrimmo.crucibleofruin.game.LevelGrid
import com.skalagrimmo.crucibleofruin.game.Platform
import com.skalagrimmo.crucibleofruin.game.PlatformCollision
import com.skalagrimmo.crucibleofruin.game.PlatformMotion
import com.skalagrimmo.crucibleofruin.game.PlatformStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Stage 6c (#38): moving platforms are deterministic and safe to ride.
 *
 * A mover glides along a triangle wave parametrized by the engine's simulation
 * clock, re-rasterizes just its own cells in the tile grid, and carries the
 * actors standing on its true top. These tests pin the wave math, the grid
 * sync, rider carry for every archetype, one-way pass-through, and that the
 * factory's movers never clobber static collision cells.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MovingPlatformTest {

    /** Engine with an empty world plus a parking floor far from every mover. */
    private fun engineWith(vararg movers: Platform): GameEngine = GameEngine().apply {
        platforms.clear()
        hazards.clear()
        enemies.clear()
        // A solid parking floor under the idle player: parked ticks never kill
        // them and never engage enemy AI near the test movers.
        platforms.add(Platform(4800f, 460f, 400f, 80f, PlatformStyle.STONE))
        movers.forEach { platforms.add(it) }
        player.x = 4900f
        player.y = 416f
        player.vx = 0f
        player.vy = 0f
        player.grounded = true
        rebuildLevelGrid()
    }

    /** A vertical one-way mover: top slides 330 -> 450 and back, period 4 s. */
    private fun elevator(): Platform = Platform(
        610f, 330f, 60f, 20f, PlatformStyle.IRON_GIRDER, PlatformCollision.ONE_WAY,
        PlatformMotion(baseX = 610f, baseY = 330f, dx = 0f, dy = 120f, speed = 60f)
    )

    /** A horizontal one-way mover: x slides 600 -> 740 and back, period 4 s. */
    private fun ferry(): Platform = Platform(
        600f, 430f, 60f, 20f, PlatformStyle.IRON_GIRDER, PlatformCollision.ONE_WAY,
        PlatformMotion(baseX = 600f, baseY = 430f, dx = 140f, dy = 0f, speed = 70f)
    )

    /** The live platform instance — the engine replaces it with a copy every tick. */
    private fun mover(engine: GameEngine): Platform =
        engine.platforms.first { it.motion != null }

    /** Places the player standing on the surface at [topY]. */
    private fun parkPlayer(engine: GameEngine, x: Float, topY: Float) {
        engine.player.x = x
        engine.player.y = topY - engine.player.h
        engine.player.vx = 0f
        engine.player.vy = 0f
        engine.player.grounded = true
    }

    @Test
    fun triangleWavePhaseIsAPureFunctionOfTime() {
        val motion = PlatformMotion(baseX = 610f, baseY = 330f, dx = 0f, dy = 120f, speed = 60f)
        assertEquals("The period is 2 * distance / speed", 4f, motion.period, 1e-4f)
        assertEquals(0f, motion.phaseAt(0f), 1e-4f)
        assertEquals(0.5f, motion.phaseAt(1f), 1e-4f)
        assertEquals(1f, motion.phaseAt(2f), 1e-4f)
        assertEquals(0.5f, motion.phaseAt(3f), 1e-4f)
        assertEquals(0f, motion.phaseAt(4f), 1e-4f)
        assertEquals("The phase wraps by whole periods", 0.5f, motion.phaseAt(9f), 1e-4f)
        val phased = PlatformMotion(0f, 0f, 100f, 0f, 50f, startPhase = 0.25f)
        assertEquals("A start phase offsets the wave without reshaping it", 0.5f, phased.phaseAt(0f), 1e-4f)
        val degenerate = PlatformMotion(0f, 0f, 0f, 0f, 60f)
        assertEquals("Degenerate motion is inert", 0f, degenerate.period, 0f)
        assertEquals(0f, degenerate.phaseAt(123f), 0f)
    }

    @Test
    fun elevatorFollowsTheClockAndReturnsToBase() {
        val engine = engineWith(elevator())
        repeat(120) { engine.update(1f / 60f) }
        assertEquals("Half a period puts the elevator at the far end", 450f, mover(engine).y, 0.01f)
        assertEquals("The elevator never drifts horizontally", 610f, mover(engine).x, 0f)
        repeat(120) { engine.update(1f / 60f) }
        assertEquals("A full period returns the elevator to its base", 330f, mover(engine).y, 0.01f)
    }

    @Test
    fun gridFollowsTheMovingPlatform() {
        val engine = engineWith(elevator())
        repeat(120) { engine.update(1f / 60f) }
        assertEquals("The mover's cells moved down with it", 2, engine.levelGrid.collisionAt(640f, 455f))
        assertEquals("The vacated base cells are empty again", 0, engine.levelGrid.collisionAt(640f, 335f))
        repeat(120) { engine.update(1f / 60f) }
        assertEquals("The base cells are repainted on return", 2, engine.levelGrid.collisionAt(640f, 335f))
        assertEquals("The far-end cells are empty again", 0, engine.levelGrid.collisionAt(640f, 455f))
    }

    @Test
    fun playerIsCarriedHorizontallyWithThePlatform() {
        val engine = engineWith(ferry())
        parkPlayer(engine, 620f, 430f)
        repeat(60) { engine.update(1f / 60f) }
        val moved = mover(engine)
        assertEquals("The player displacement equals the platform displacement",
            moved.x - 600f, engine.player.x - 620f, 0.5f)
        assertTrue("A carried player stays grounded", engine.player.grounded)
        assertEquals("The player's feet stay glued to the true top", 430f, engine.player.y + engine.player.h, 0.01f)
    }

    @Test
    fun playerStaysGluedThroughDownAndUpStrokes() {
        val engine = engineWith(elevator())
        parkPlayer(engine, 615f, 330f)
        repeat(120) { engine.update(1f / 60f) }
        assertTrue("The player rides the down stroke", engine.player.grounded)
        assertEquals("The player is glued to the far-end top", 450f, engine.player.y + engine.player.h, 0.01f)
        repeat(120) { engine.update(1f / 60f) }
        assertTrue("The player rides the up stroke", engine.player.grounded)
        assertEquals("The player is glued back at the base top", 330f, engine.player.y + engine.player.h, 0.01f)
        assertEquals("A vertical mover never drags the player sideways", 615f, engine.player.x, 0.001f)
    }

    @Test
    fun fallingPlayerLandsOnADescendingPlatform() {
        val engine = engineWith(elevator())
        engine.player.x = 615f
        engine.player.y = 150f
        engine.player.vx = 0f
        engine.player.vy = 0f
        engine.player.grounded = false
        repeat(40) { engine.update(1f / 60f) }
        assertTrue("The player should have landed on the descending mover", engine.player.grounded)
        assertEquals("The player rests on the mover's true top",
            mover(engine).y, engine.player.y + engine.player.h, 0.5f)
    }

    @Test
    fun airborneActorIsNotDraggedByThePlatform() {
        val engine = engineWith(ferry())
        engine.player.x = 620f
        engine.player.y = 300f
        engine.player.vx = 0f
        engine.player.vy = 0f
        engine.player.grounded = false
        repeat(3) { engine.update(1f / 60f) }
        assertEquals("Only riders are carried — an airborne actor keeps its x", 620f, engine.player.x, 0.001f)
        assertTrue("The airborne actor has not landed yet", !engine.player.grounded)
    }

    @Test
    fun groundedEnemyRidesTheMovingPlatform() {
        val engine = engineWith(ferry())
        val cultist = EnemyEntity(615f, 388f, EnemyArchetype.CULTIST, 0f)
        cultist.grounded = true
        engine.enemies.add(cultist)
        repeat(60) { engine.update(1f / 60f) }
        val moved = mover(engine)
        assertEquals("The enemy displacement equals the platform displacement",
            moved.x - 600f, cultist.x - 615f, 0.5f)
        assertTrue("A carried enemy stays grounded", cultist.grounded)
        assertEquals("The enemy's feet stay glued to the true top", 430f, cultist.y + cultist.h, 0.01f)
    }

    @Test
    fun pickupGluesToTheMovingPlatform() {
        val engine = engineWith(elevator())
        engine.pickups.spawn(620f, 250f, "health")
        repeat(90) { engine.update(1f / 60f) }
        assertEquals("The pickup was never collected by the parked player", 1, engine.pickups.activeCount)
        val pickup = engine.pickups.get(0)
        assertEquals("The pickup rests on the mover's true top", mover(engine).y - 16f, pickup.y, 0.01f)
        assertEquals("The pickup never drifts sideways", 620f, pickup.x, 0.001f)
    }

    @Test
    fun risingActorPassesThroughTheOneWayMover() {
        val engine = engineWith(elevator())
        val lurker = EnemyEntity(615f, 360f, EnemyArchetype.LURKER, 0f)
        lurker.vy = -450f
        engine.enemies.add(lurker)
        repeat(20) { engine.update(1f / 60f) }
        assertTrue("A rising enemy passes through the one-way top",
            lurker.y + lurker.h < mover(engine).y)
        assertTrue("A one-way underside never zeroes a rising actor", lurker.vy < 0f)
        assertTrue("No landing is reported while passing through", !lurker.grounded)
    }

    @Test
    fun factoryGridStaysInSyncWithTheMovingPlatforms() {
        val engine = GameEngine()
        repeat(60) { engine.update(1f / 60f) }
        assertEquals("The factory elevator is a quarter period down", 390f,
            engine.platforms.first { it.motion != null && it.motion.dy > 0f }.y, 0.01f)
        assertEquals("The factory ferry is at its far end", 2860f,
            engine.platforms.first { it.motion != null && it.motion.dx > 0f }.x, 0.01f)
        repeat(180) { engine.update(1f / 60f) }

        // After full cycles the grid must match a fresh rasterization of the
        // current world exactly — syncPlatform never clobbers static cells.
        val fresh = LevelGrid(engine.levelWidth, engine.levelHeight)
        fresh.buildFrom(engine.platforms, engine.hazards)
        var collisionsMismatched = 0
        var hazardsMismatched = 0
        var y = 5f
        while (y < engine.levelHeight) {
            var x = 5f
            while (x < engine.levelWidth) {
                if (fresh.collisionAt(x, y) != engine.levelGrid.collisionAt(x, y)) collisionsMismatched++
                if (fresh.hazardAt(x, y) != engine.levelGrid.hazardAt(x, y)) hazardsMismatched++
                x += 10f
            }
            y += 10f
        }
        assertEquals("Every collision cell matches a fresh build", 0, collisionsMismatched)
        assertEquals("Every hazard cell matches a fresh build", 0, hazardsMismatched)
    }

    @Test
    fun motionIsDeterministicAcrossEngines() {
        val first = engineWith(elevator())
        val second = engineWith(elevator())
        parkPlayer(first, 615f, 330f)
        parkPlayer(second, 615f, 330f)
        repeat(137) {
            first.update(1f / 60f)
            second.update(1f / 60f)
        }
        assertEquals("Same ticks give identical player x", first.player.x, second.player.x, 0f)
        assertEquals("Same ticks give identical player y", first.player.y, second.player.y, 0f)
        assertEquals("Same ticks give identical mover x", first.platforms[1].x, second.platforms[1].x, 0f)
        assertEquals("Same ticks give identical mover y", first.platforms[1].y, second.platforms[1].y, 0f)
        assertTrue("The player still rides the elevator after 137 ticks", first.player.grounded)
        assertEquals("The rider stays glued to the true top",
            first.platforms[1].y, first.player.y + first.player.h, 0.01f)
    }
}
