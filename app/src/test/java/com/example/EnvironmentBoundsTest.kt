package com.example

import com.example.game.EnemyArchetype
import com.example.game.EnemyEntity
import com.example.game.GameEngine
import com.example.game.Platform
import com.example.game.PlatformStyle
import com.example.game.boss.BossAttackPattern
import com.example.game.boss.CrucibleChimeraBoss
import com.example.game.boss.TelegraphType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Stage 4 (#36): environment sensing and out-of-bounds cleanup.
 *
 * Grounded archetypes probe for ground before stepping so pits no longer
 * depend on hand-tuned patrol ranges; actors that fall out of the world are
 * removed; the boss leap slam carries a tunneling-safe speed cap.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EnvironmentBoundsTest {

    /** Engine with an empty world; each test installs its own geometry. */
    private fun bareEngine(): GameEngine = GameEngine().apply {
        platforms.clear()
        hazards.clear()
        enemies.clear()
        // Park the player far away so enemy AI never engages, and give it a
        // platform so it never dies and respawns into the test scene.
        player.x = 5000f
        platforms.add(Platform(4900f, 460f, 200f, 80f, PlatformStyle.STONE))
        // Stage 6b (#38): the tile grid must mirror the test geometry.
        rebuildLevelGrid()
    }

    @Test
    fun patrollingEnemyTurnsAroundAtPitEdgeInsteadOfFalling() {
        val engine = bareEngine()
        // A short floor with nothing after x=200: a pit.
        engine.platforms.add(Platform(0f, 460f, 200f, 80f, PlatformStyle.STONE))
        // Stage 6b (#38): the tile grid must mirror the test geometry.
        engine.rebuildLevelGrid()
        val lurker = EnemyEntity(120f, 424f, EnemyArchetype.LURKER, 500f)
        lurker.facing = 1
        engine.enemies.add(lurker)

        repeat(240) { // four seconds of patrolling along the edge
            engine.update(1f / 60f)
        }

        assertTrue(
            "The enemy must stay on its platform beside the pit",
            lurker.x + lurker.w <= 205f
        )
        assertTrue("The enemy must not fall into the pit", !lurker.dead && lurker.y + lurker.h < 470f)
        assertTrue("The enemy must keep patrolling on the ledge", lurker.grounded)
    }

    @Test
    fun enemyFallingOutOfTheWorldDiesWithinTwoSeconds() {
        val engine = bareEngine()
        // Nothing below the spawn point: the enemy falls out of the world.
        val lurker = EnemyEntity(100f, 600f, EnemyArchetype.LURKER, 100f)
        engine.enemies.add(lurker)

        val defeatedBefore = engine.enemiesDefeated
        repeat(120) { // two seconds
            engine.update(1f / 60f)
        }

        assertTrue("An enemy below the level must be removed within two seconds", lurker.dead)
        assertEquals("The pit death must count as a defeat", defeatedBefore + 1, engine.enemiesDefeated)
    }

    @Test
    fun pickupFallingOutOfTheWorldDespawnsWithinTwoSeconds() {
        val engine = bareEngine()
        engine.pickups.spawn(100f, 600f, "health")
        assertEquals(1, engine.pickups.activeCount)

        repeat(120) { // two seconds
            engine.update(1f / 60f)
        }

        assertEquals("A pickup below the level must despawn within two seconds", 0, engine.pickups.activeCount)
    }

    @Test
    fun groundSlamHorizontalSpeedIsCappedAtTunnelingSafeVelocity() {
        val engine = GameEngine()
        val boss = engine.boss as CrucibleChimeraBoss
        val slam = BossAttackPattern(
            id = "ground_slam",
            name = "Cataclysmic Leap Slam",
            type = TelegraphType.GROUND_SLAM,
            telegraphDuration = 0.75f,
            activeDuration = 1.10f,
            recoveryDuration = 0.90f,
            baseDamage = 32f,
            exposesWeakPoint = false
        )

        // Maximum arena distance used to scale vx to ~765 px/s; it must now clamp.
        engine.player.x = boss.x - 900f
        boss.active = true
        boss.onStartAttack(slam, engine)

        assertEquals("The leap slam's horizontal speed must be capped at 420 px/s", -420f, boss.vx, 0.01f)
    }
}
