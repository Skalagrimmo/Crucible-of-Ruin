package com.example

import com.example.game.GameEngine
import com.example.game.Platform
import com.example.game.PlatformStyle
import com.example.game.PhysicsTuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Stage 5 (#37): the level is reachable by measurement, not by hand-tuning.
 *
 * A BFS over the world's platforms uses the base-jump ruler derived from
 * PhysicsTuning: a rise is legal only when it fits under the jump apex exactly
 * (a hard physical ceiling), while horizontal gaps get a 10 percent margin for
 * run-up momentum. Every standable platform, checkpoint, and the boss trigger
 * must be reachable with base stats — CI fails otherwise.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LevelReachabilityTest {

    /** Standable surfaces: every platform except the boss-arena gate doors. */
    private fun standables(engine: GameEngine): List<Platform> =
        engine.platforms.filter { it.style != PlatformStyle.BOSS_GATE }

    /** The platform a player standing at (x, feetY) would rest on. */
    private fun supportAt(x: Float, feetY: Float, platforms: List<Platform>): Platform? =
        platforms.firstOrNull { p ->
            x >= p.x && x <= p.x + p.w && p.y >= feetY - 2f && p.y <= feetY + 60f
        }

    /** Horizontal distance that must be cleared to step from a onto b. */
    private fun gapBetween(a: Platform, b: Platform): Float = when {
        b.x >= a.x + a.w -> b.x - (a.x + a.w)
        b.x + b.w <= a.x -> a.x - (b.x + b.w)
        else -> 0f
    }

    /** BFS from [start] using the base-jump ruler from [PhysicsTuning]. */
    private fun reachableFrom(start: Platform, platforms: List<Platform>): Set<Platform> {
        val visited = HashSet<Platform>()
        val queue = ArrayDeque<Platform>()
        visited.add(start)
        queue.add(start)
        while (queue.isNotEmpty()) {
            val a = queue.removeFirst()
            for (b in platforms) {
                if (b === a || b in visited) continue
                val rise = a.y - b.y
                if (rise <= PhysicsTuning.JUMP_HEIGHT &&
                    gapBetween(a, b) <= PhysicsTuning.JUMP_REACH * 1.10f
                ) {
                    visited.add(b)
                    queue.add(b)
                }
            }
        }
        return visited
    }

    /** Checkpoint zero spawns the player at (80, 380); feet at 424. */
    private fun spawnPlatform(platforms: List<Platform>): Platform {
        val support = supportAt(80f, 380f + 44f, platforms)
        assertNotNull("The spawn point must stand on a platform", support)
        return support!!
    }

    @Test
    fun everyPlatformIsReachableFromSpawnByABaseJump() {
        val engine = GameEngine()
        val platforms = standables(engine)
        val visited = reachableFrom(spawnPlatform(platforms), platforms)

        val unreachable = platforms.filter { it !in visited }
        assertTrue(
            "Unreachable platforms (base jump from PhysicsTuning): " +
                unreachable.joinToString { platform ->
                    "(" + platform.x + ", " + platform.y + ")"
                },
            unreachable.isEmpty()
        )
    }

    @Test
    fun everyCheckpointRestsOnAReachablePlatform() {
        val engine = GameEngine()
        val platforms = standables(engine)
        val visited = reachableFrom(spawnPlatform(platforms), platforms)

        for (cp in engine.checkpoints) {
            val support = supportAt(cp.x, cp.y + 44f, platforms)
            assertNotNull("Checkpoint " + cp.id + " must rest on a platform", support)
            assertTrue(
                "Checkpoint " + cp.id + " must be reachable with base stats",
                visited.contains(support!!)
            )
        }
    }

    @Test
    fun bossTriggerIsReachableWithBaseStats() {
        val engine = GameEngine()
        val platforms = standables(engine)
        val visited = reachableFrom(spawnPlatform(platforms), platforms)

        // The fight begins when the player crosses x = 5340 inside the arena.
        assertTrue(
            "Some reachable platform must let the player stand past the boss trigger line",
            visited.any { p -> 5340f >= p.x && 5340f <= p.x + p.w }
        )
    }

    @Test
    fun arenaGirdersSitWithinBaseJumpHeightOfTheArenaFloor() {
        val engine = GameEngine()
        val arenaFloorTop = 450f
        // Stage 8b (#56): the Iron Garrison's rooftop girder also lives past
        // x 5270 — the arena check stays scoped to the corridor band.
        val girders = engine.platforms.filter {
            it.style == PlatformStyle.IRON_GIRDER && it.x > 5270f && it.y < 540f
        }

        assertEquals("The arena should contain exactly two girders", 2, girders.size)
        for (g in girders) {
            assertTrue(
                "Arena girders must be reachable with base stats (top no higher than the " +
                    "jump apex above the arena floor)",
                g.y >= arenaFloorTop - PhysicsTuning.JUMP_HEIGHT
            )
        }
    }

    /**
     * Stage 7b (#51): the Undercroft's way home. The shaft lift's top stop must
     * let a rider step back onto the split gallery floor with base stats — a
     * rise within the jump apex and a gap within the base-jump reach.
     */
    @Test
    fun undercroftLiftTopStopStepsBackOntoTheGalleryFloor() {
        val engine = GameEngine()
        val lift = engine.platforms.first { it.motion != null && it.motion.dy < 0f && it.y > 900f }
        val galleryFloor = engine.platforms.first { it.x == 3450f && it.y == 430f }

        val topStopTop = lift.y + lift.motion!!.dy
        assertEquals("The lift's top stop is pinned to the design position", 460f, topStopTop, 0.01f)
        assertTrue(
            "The step up to the gallery floor must fit under the jump apex",
            topStopTop - galleryFloor.y in 0f..PhysicsTuning.JUMP_HEIGHT
        )
        assertTrue(
            "The step-off gap onto the gallery floor must fit the base jump reach",
            galleryFloor.x - (lift.x + lift.w) in 0f..PhysicsTuning.JUMP_REACH
        )
    }
}
