package com.example

import com.example.game.ActorPhysics
import com.example.game.ActorPhysicsConfig
import com.example.game.GameEngine
import com.example.game.LevelGrid
import com.example.game.PhysicsActor
import com.example.game.Platform
import com.example.game.PlatformCollision
import com.example.game.PlatformFilter
import com.example.game.PlatformStyle
import com.example.game.VerticalResolveMode
import com.example.game.aabbIntersects
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

/**
 * Stage 6b (#38): the tile-grid resolver and the legacy list resolver must be
 * behaviorally indistinguishable on the real world.
 *
 * Every test drives twin actors through both resolvers with the same config
 * and asserts identical outcomes — positions and velocities with an exact
 * 0 f delta, and the same Platform instance through the landing hook. The
 * list overload of ActorPhysics.moveAndResolve stays in the codebase as the
 * reference implementation for exactly these tests (see ARCHITECTURE.md).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LevelGridParityTest {

    /** Minimal actor used to drive the resolver in isolation. */
    private class TestActor(
        override var x: Float,
        override var y: Float,
        override val w: Float,
        override val h: Float
    ) : PhysicsActor {
        override var vx: Float = 0f
        override var vy: Float = 0f
        override var grounded: Boolean = false
        override var facing: Int = 1
        var landedOn: Platform? = null

        override fun onLanded(platform: Platform) {
            landedOn = platform
        }
    }

    // Player-like config (the constants only need to be plausible, not exact).
    private val playerConfig = ActorPhysicsConfig(
        gravity = 1500f,
        terminalVelocity = 750f,
        horizontalFilter = PlatformFilter.SOLID_ONLY,
        verticalMode = VerticalResolveMode.SWEPT_LANDING,
        landingFilter = PlatformFilter.NOT_OPEN,
        overlapFilter = PlatformFilter.SOLID_ONLY
    )

    // Enemy/boss-like config: overlap resolve against everything, flips on walls.
    private val enemyConfig = ActorPhysicsConfig(
        gravity = 950f,
        terminalVelocity = 650f,
        horizontalFilter = PlatformFilter.ALL,
        flipOnWall = true,
        verticalMode = VerticalResolveMode.OVERLAP_LAND_OR_BLOCK,
        landingFilter = PlatformFilter.ALL
    )

    // Gravity-free wall probe: pure horizontal push and flip checks.
    private val wallProbeConfig = ActorPhysicsConfig(
        gravity = 0f,
        terminalVelocity = 650f,
        horizontalFilter = PlatformFilter.ALL,
        flipOnWall = true,
        verticalMode = VerticalResolveMode.OVERLAP_LAND_OR_BLOCK,
        landingFilter = PlatformFilter.ALL
    )

    // Pickup-like config: Y-only snap onto the highest surface.
    private val pickupConfig = ActorPhysicsConfig(
        gravity = 800f,
        terminalVelocity = 750f,
        horizontalMotion = false,
        verticalMode = VerticalResolveMode.OVERLAP_SNAP_TO_TOP,
        landingFilter = PlatformFilter.NOT_OPEN
    )

    @Test
    fun everyFactoryPlatformAndHazardSnapsExactlyOntoTheTileGrid() {
        val engine = GameEngine()
        val tile = 10f
        for (p in engine.platforms) {
            assertTrue("platform x must be tile aligned (" + p.x + ")", p.x % tile == 0f)
            assertTrue("platform y must be tile aligned (" + p.y + ")", p.y % tile == 0f)
            assertTrue("platform w must be tile aligned (" + p.w + ")", p.w % tile == 0f)
            assertTrue("platform h must be tile aligned (" + p.h + ")", p.h % tile == 0f)
        }
        for (h in engine.hazards) {
            assertTrue("hazard x must be tile aligned (" + h.x + ")", h.x % tile == 0f)
            assertTrue("hazard y must be tile aligned (" + h.y + ")", h.y % tile == 0f)
            assertTrue("hazard w must be tile aligned (" + h.w + ")", h.w % tile == 0f)
            assertTrue("hazard h must be tile aligned (" + h.h + ")", h.h % tile == 0f)
        }
    }

    @Test
    fun fallingOntoEveryPlatformParitiesTheListResolverExactly() {
        val engine = GameEngine()
        val dt = 1f / 60f
        engine.platforms.forEachIndexed { index, platform ->
            val spawnX = platform.x + platform.w / 2f - 13f
            val spawnY = platform.y - 104f
            val list = TestActor(spawnX, spawnY, 26f, 44f)
            val grid = TestActor(spawnX, spawnY, 26f, 44f)
            repeat(70) {
                ActorPhysics.moveAndResolve(list, engine.platforms, playerConfig, dt)
                ActorPhysics.moveAndResolve(grid, engine.levelGrid, playerConfig, dt)
            }
            assertEquals("x parity on platform " + index, list.x, grid.x, 0f)
            assertEquals("y parity on platform " + index, list.y, grid.y, 0f)
            assertEquals("vx parity on platform " + index, list.vx, grid.vx, 0f)
            assertEquals("vy parity on platform " + index, list.vy, grid.vy, 0f)
            assertEquals("grounded parity on platform " + index, list.grounded, grid.grounded)
            assertEquals("facing parity on platform " + index, list.facing, grid.facing)
            assertSame("landedOn identity parity on platform " + index, list.landedOn, grid.landedOn)
        }
    }

    /**
     * Finds a spawn y for a wall probe: the actor's band overlaps the target's
     * side and nothing else occupies the run-up lane. Returns null when the
     * side is fully occluded — no actor can reach it in gameplay either.
     */
    private fun clearApproachY(target: Platform, others: List<Platform>, lanes: List<Float>): Float? {
        var y = target.y - 42f
        while (y < target.y + target.h) {
            val bandOverlapsTarget = y + 44f > target.y && y < target.y + target.h
            val laneClear = lanes.all { lane ->
                others.all { o ->
                    !aabbIntersects(lane, y, 26f, 44f, o.x, o.y, o.w, o.h)
                }
            }
            if (bandOverlapsTarget && laneClear) return y
            y += 2f
        }
        return null
    }

    @Test
    fun wallPushParitiesTheListResolverAtEverySolidPlatform() {
        val engine = GameEngine()
        val dt = 1f / 60f
        engine.platforms.forEachIndexed { index, target ->
            if (target.collision != PlatformCollision.SOLID) return@forEachIndexed
            val others = engine.platforms.filterIndexed { i, p -> i != index }

            // Approach the target's left side from the left.
            val rightLanes = listOf(target.x - 60f, target.x - 45f, target.x - 30f, target.x - 26f)
            val yRight = clearApproachY(target, others, rightLanes)
                ?: return@forEachIndexed // side unreachable for any actor
            val rightList = TestActor(target.x - 60f, yRight, 26f, 44f)
            val rightGrid = TestActor(target.x - 60f, yRight, 26f, 44f)
            rightList.vx = 190f
            rightGrid.vx = 190f
            repeat(26) {
                ActorPhysics.moveAndResolve(rightList, engine.platforms, wallProbeConfig, dt)
                ActorPhysics.moveAndResolve(rightGrid, engine.levelGrid, wallProbeConfig, dt)
            }
            assertEquals("x parity pushing right on platform " + index, rightList.x, rightGrid.x, 0f)
            assertEquals("vx parity pushing right on platform " + index, rightList.vx, rightGrid.vx, 0f)
            assertEquals("facing parity pushing right on platform " + index, rightList.facing, rightGrid.facing)
            assertEquals("pinned to the left edge on platform " + index, target.x - 26f, rightGrid.x, 0f)
            assertEquals("facing flips left after a right wall hit", -1, rightGrid.facing)

            // Approach the target's right side from the right.
            val leftLanes = listOf(target.x + target.w + 4f, target.x + target.w + 30f, target.x + target.w + 45f)
            val yLeft = clearApproachY(target, others, leftLanes)
                ?: return@forEachIndexed // side unreachable for any actor
            val leftList = TestActor(target.x + target.w + 34f, yLeft, 26f, 44f)
            val leftGrid = TestActor(target.x + target.w + 34f, yLeft, 26f, 44f)
            leftList.vx = -190f
            leftGrid.vx = -190f
            repeat(26) {
                ActorPhysics.moveAndResolve(leftList, engine.platforms, wallProbeConfig, dt)
                ActorPhysics.moveAndResolve(leftGrid, engine.levelGrid, wallProbeConfig, dt)
            }
            assertEquals("x parity pushing left on platform " + index, leftList.x, leftGrid.x, 0f)
            assertEquals("vx parity pushing left on platform " + index, leftList.vx, leftGrid.vx, 0f)
            assertEquals("facing parity pushing left on platform " + index, leftList.facing, leftGrid.facing)
            assertEquals("pinned to the right edge on platform " + index, target.x + target.w, leftGrid.x, 0f)
            assertEquals("facing flips right after a left wall hit", 1, leftGrid.facing)
        }
    }

    @Test
    fun oneWaySideEntryFallsThroughIdenticallyInBothResolvers() {
        val engine = GameEngine()
        val girder = engine.platforms.first { it.style == PlatformStyle.IRON_GIRDER && it.x == 800f }
        // Feet start below the girder top: the enemy must fall through it.
        val list = TestActor(girder.x + 20f, girder.y + 16f, 26f, 44f)
        val grid = TestActor(girder.x + 20f, girder.y + 16f, 26f, 44f)
        repeat(30) {
            ActorPhysics.moveAndResolve(list, engine.platforms, enemyConfig, 1f / 60f)
            ActorPhysics.moveAndResolve(grid, engine.levelGrid, enemyConfig, 1f / 60f)
        }
        assertFalse("A side entry must not land on the one-way girder", list.landedOn === girder)
        assertFalse(list.landedOn === girder)
        assertSame(list.landedOn, grid.landedOn)
        assertEquals("x parity", list.x, grid.x, 0f)
        assertEquals("y parity", list.y, grid.y, 0f)
        assertEquals("The enemy settles on the floor below", 416f, grid.y, 0f)
        assertTrue(grid.grounded)
    }

    @Test
    fun oneWayCrossedTopParitiesOnSyntheticGirder() {
        // Synthetic world: a single 18 px one-way girder at (800, 380) — the
        // unaligned-thickness shape unit tests install.
        val girder = Platform(800f, 380f, 120f, 18f, PlatformStyle.IRON_GIRDER, PlatformCollision.ONE_WAY)
        val world = listOf(girder)
        val grid = LevelGrid(6200f, 540f)
        grid.buildFrom(world, emptyList())

        // Side entry: feet already below the top — falls through both resolvers.
        val sideList = TestActor(820f, 396f, 26f, 44f)
        val sideGrid = TestActor(820f, 396f, 26f, 44f)
        repeat(10) {
            ActorPhysics.moveAndResolve(sideList, world, enemyConfig, 1f / 60f)
            ActorPhysics.moveAndResolve(sideGrid, grid, enemyConfig, 1f / 60f)
        }
        assertFalse("Side entry falls through the girder", sideList.grounded)
        assertEquals("Side entry parity (y)", sideList.y, sideGrid.y, 0f)
        assertTrue("Side entry keeps falling", sideGrid.y > 396f)

        // Crossed top: feet start above, gravity carries them across — lands.
        val topList = TestActor(820f, 320f, 26f, 44f)
        val topGrid = TestActor(820f, 320f, 26f, 44f)
        repeat(20) {
            ActorPhysics.moveAndResolve(topList, world, enemyConfig, 1f / 60f)
            ActorPhysics.moveAndResolve(topGrid, grid, enemyConfig, 1f / 60f)
        }
        assertEquals("Crossed-top landing (list)", 336f, topList.y, 0f)
        assertEquals("Crossed-top landing (grid)", 336f, topGrid.y, 0f)
        assertTrue(topGrid.grounded)
        assertSame("Landing hook receives the girder instance", girder, topGrid.landedOn)
    }

    @Test
    fun sealedBossGatesBlockIdenticallyInBothResolvers() {
        val engine = GameEngine()
        // Seal the arena exactly the way setBossGatesCollision would.
        engine.platforms.replaceAll { p ->
            if (p.style == PlatformStyle.BOSS_GATE) p.copy(collision = PlatformCollision.SOLID) else p
        }
        engine.rebuildLevelGrid()

        val list = TestActor(5340f, 250f, 26f, 44f)
        val grid = TestActor(5340f, 250f, 26f, 44f)
        list.vx = -190f
        grid.vx = -190f
        repeat(26) {
            ActorPhysics.moveAndResolve(list, engine.platforms, wallProbeConfig, 1f / 60f)
            ActorPhysics.moveAndResolve(grid, engine.levelGrid, wallProbeConfig, 1f / 60f)
        }
        assertEquals("Pinned to the sealed gate's right edge (list)", 5300f, list.x, 0f)
        assertEquals("Pinned to the sealed gate's right edge (grid)", 5300f, grid.x, 0f)
        assertEquals("vx zeroed (list)", 0f, list.vx, 0f)
        assertEquals("vx zeroed (grid)", 0f, grid.vx, 0f)
        assertEquals("Flip after hitting the wall from the right (list)", 1, list.facing)
        assertEquals("Flip after hitting the wall from the right (grid)", 1, grid.facing)
    }

    @Test
    fun openGatesLetThePlayerWalkIntoTheArenaPastTheBossTrigger() {
        val engine = GameEngine()
        engine.enemies.clear()
        engine.player.x = 5200f
        engine.player.y = 386f
        engine.player.vx = 0f
        engine.player.vy = 0f
        engine.player.grounded = true
        engine.inputRight = true
        repeat(180) { engine.update(1f / 60f) }
        assertTrue("The player must cross the boss trigger line", engine.player.x > 5340f)
        assertTrue("The player must stay alive during the walk", !engine.player.dead)
        assertTrue("The boss must have activated", engine.boss.active)
    }

    @Test
    fun headBumpParitiesOnSyntheticCeiling() {
        val ceiling = Platform(0f, 100f, 200f, 20f, PlatformStyle.STONE, PlatformCollision.SOLID)
        val world = listOf(ceiling)
        val grid = LevelGrid(6200f, 540f)
        grid.buildFrom(world, emptyList())

        val list = TestActor(80f, 150f, 26f, 44f)
        val gridActor = TestActor(80f, 150f, 26f, 44f)
        list.vy = -400f
        gridActor.vy = -400f
        repeat(6) {
            ActorPhysics.moveAndResolve(list, world, playerConfig, 1f / 60f)
            ActorPhysics.moveAndResolve(gridActor, grid, playerConfig, 1f / 60f)
        }
        assertEquals("Rising motion stops under the ceiling (list)", 120f, list.y, 0f)
        assertEquals("Rising motion stops under the ceiling (grid)", 120f, gridActor.y, 0f)
        assertEquals("vy zeroed (list)", 0f, list.vy, 0f)
        assertEquals("vy zeroed (grid)", 0f, gridActor.vy, 0f)
    }

    @Test
    fun pickupSnapParitiesOnSyntheticFloor() {
        val floor = Platform(80f, 460f, 200f, 80f, PlatformStyle.STONE, PlatformCollision.SOLID)
        val world = listOf(floor)
        val grid = LevelGrid(6200f, 540f)
        grid.buildFrom(world, emptyList())

        val list = TestActor(100f, 445f, 16f, 16f)
        val gridActor = TestActor(100f, 445f, 16f, 16f)
        ActorPhysics.moveAndResolve(list, world, pickupConfig, 1f / 60f)
        ActorPhysics.moveAndResolve(gridActor, grid, pickupConfig, 1f / 60f)
        assertEquals("Pickup snaps onto the floor top (list)", 444f, list.y, 0f)
        assertEquals("Pickup snaps onto the floor top (grid)", 444f, gridActor.y, 0f)
        assertEquals("vy zeroed (list)", 0f, list.vy, 0f)
        assertEquals("vy zeroed (grid)", 0f, gridActor.vy, 0f)
    }

    @Test
    fun projectileRectCheckParitiesTheListScanAcrossTheWholeWorld() {
        val engine = GameEngine()
        var x = 0f
        while (x < 6200f - 16f) {
            var y = 0f
            while (y < 540f - 16f) {
                val listHit = engine.platforms.any { p ->
                    p.collision != PlatformCollision.OPEN &&
                        aabbIntersects(x, y, 16f, 16f, p.x, p.y, p.w, p.h)
                }
                val gridHit = engine.levelGrid.rectTouchesCollision(x, y, 16f, 16f)
                assertEquals("projectile parity at " + x + "," + y, listHit, gridHit)
                y += 7f
            }
            x += 7f
        }
    }

    @Test
    fun ledgeProbeParitiesTheLegacyListScan() {
        val engine = GameEngine()
        for (probeX in listOf(
            60f, 106f, 200f, 206f, 2940f, 3100f, 5000f, 5270f,
            5285f, 5300f, 5340f, 5900f, 6170f
        )) {
            for (feetY in listOf(
                100f, 250f, 300f, 350f, 416f, 420f, 424f, 430f,
                458f, 460f, 462f, 490f
            )) {
                val legacy = engine.platforms.any { p ->
                    p.collision != PlatformCollision.OPEN &&
                        probeX >= p.x && probeX <= p.x + p.w &&
                        abs(p.y - feetY) <= 2f
                }
                val gridded = engine.levelGrid.hasStandableSurfaceNear(probeX, feetY)
                assertEquals("probe parity at " + probeX + "," + feetY, legacy, gridded)
            }
        }
    }

    @Test
    fun hazardCellsParityTheHazardRectList() {
        val engine = GameEngine()
        var x = 0f
        while (x < 6200f) {
            var y = 0f
            while (y < 540f) {
                val listHit = engine.hazards.any { h ->
                    x >= h.x && x < h.x + h.w && y >= h.y && y < h.y + h.h
                }
                assertEquals(
                    "hazard parity at " + x + "," + y,
                    listHit,
                    engine.levelGrid.hazardAt(x, y) != 0
                )
                y += 7f
            }
            x += 7f
        }
    }

    @Test
    fun syncPlatformSealsAndReopensGateCells() {
        val engine = GameEngine()
        val gate = engine.platforms.indexOfFirst { it.style == PlatformStyle.BOSS_GATE }
        assertTrue(gate >= 0)
        assertEquals("Gates start open (empty cells)", 0, engine.levelGrid.collisionAt(5285f, 300f))

        engine.platforms[gate] = engine.platforms[gate].copy(collision = PlatformCollision.SOLID)
        engine.levelGrid.syncPlatform(engine.platforms[gate], gate)
        assertEquals("Sealed gate paints solid cells", 1, engine.levelGrid.collisionAt(5285f, 300f))

        engine.platforms[gate] = engine.platforms[gate].copy(collision = PlatformCollision.OPEN)
        engine.levelGrid.syncPlatform(engine.platforms[gate], gate)
        assertEquals("Reopened gate clears the cells", 0, engine.levelGrid.collisionAt(5285f, 300f))
    }

    @Test
    fun engineUpdateSealsTheArenaGatesIntoTheGrid() {
        val engine = GameEngine()
        assertEquals("Open gates are empty before the fight", 0, engine.levelGrid.collisionAt(5280f, 200f))

        engine.player.x = 5500f
        engine.player.y = 400f
        engine.player.vx = 0f
        engine.player.vy = 0f
        engine.update(1f / 60f)

        assertEquals("Crossing x = 5340 seals the gates into the grid", 1, engine.levelGrid.collisionAt(5280f, 200f))
        assertFalse("The player stays alive inside the arena", engine.player.dead)
    }

    @Test
    fun enemyWallFlipParitiesAtTheTowerWall() {
        val engine = GameEngine()
        val tower = engine.platforms.first { it.x == 2940f && it.y == 260f }
        // A grounded 30x36 actor patrolling left on the section floor flips at
        // the tower's right side (x = 3100).
        val list = TestActor(3200f, 394f, 30f, 36f)
        val grid = TestActor(3200f, 394f, 30f, 36f)
        list.vx = -120f
        grid.vx = -120f
        list.facing = -1
        grid.facing = -1
        repeat(60) {
            ActorPhysics.moveAndResolve(list, engine.platforms, enemyConfig, 1f / 60f)
            ActorPhysics.moveAndResolve(grid, engine.levelGrid, enemyConfig, 1f / 60f)
        }
        assertEquals("Pinned to the tower wall (list)", 3100f, list.x, 0f)
        assertEquals("Pinned to the tower wall (grid)", 3100f, grid.x, 0f)
        assertEquals("vx zeroed (list)", 0f, list.vx, 0f)
        assertEquals("vx zeroed (grid)", 0f, grid.vx, 0f)
        assertEquals("Flip after hitting the wall from the right (list)", 1, list.facing)
        assertEquals("Flip after hitting the wall from the right (grid)", 1, grid.facing)
        assertEquals("Grounded on the floor after the flip (list)", true, list.grounded)
        assertEquals("Grounded on the floor after the flip (grid)", true, grid.grounded)
    }
}
