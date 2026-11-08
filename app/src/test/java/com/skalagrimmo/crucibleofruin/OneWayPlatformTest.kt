package com.skalagrimmo.crucibleofruin

import com.skalagrimmo.crucibleofruin.game.EnemyArchetype
import com.skalagrimmo.crucibleofruin.game.EnemyEntity
import com.skalagrimmo.crucibleofruin.game.GameEngine
import com.skalagrimmo.crucibleofruin.game.Platform
import com.skalagrimmo.crucibleofruin.game.PlatformCollision
import com.skalagrimmo.crucibleofruin.game.PlatformStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Stage 3 (#35): thin girders and ledges are one-way surfaces for every
 * archetype — passable from below and from the side, solid only for a
 * descending actor whose feet crossed the top surface this tick.
 *
 * The player-side pass-through behavior is already pinned by
 * GameEnginePhysicsTest; these tests cover the remaining acceptance criteria:
 * enemies honor one-way semantics, and a side entry never teleports an
 * actor on top of the surface.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OneWayPlatformTest {

    /** Engine with an empty world; each test installs its own geometry. */
    private fun bareEngine(): GameEngine = GameEngine().apply {
        platforms.clear()
        hazards.clear()
        enemies.clear()
        // Park the player far from the test enemies so their AI never engages.
        player.x = 5000f
    }

    @Test
    fun playerSideEntryOntoOneWayGirderDoesNotTeleportOnTop() {
        val engine = bareEngine()
        engine.platforms.add(
            Platform(100f, 300f, 200f, 18f, PlatformStyle.IRON_GIRDER, PlatformCollision.ONE_WAY)
        )
        engine.rebuildLevelGrid()

        // The player floats beside the girder with feet well below its top and
        // walks right while falling — the overlap must not snap them on top.
        engine.player.x = 60f
        engine.player.y = 290f
        engine.player.vx = 0f
        engine.player.vy = 0f
        engine.player.grounded = false
        engine.inputRight = true

        // Air acceleration reaches ~113 px/s only after ten ticks; twenty ticks put
        // the player firmly inside the girder column while still inside its band.
        repeat(20) {
            engine.update(1f / 60f)
        }

        assertTrue("The player should have walked into the girder column", engine.player.x + engine.player.w > 100f)
        assertTrue(
            "A side entry must not teleport the player on top of the one-way girder",
            engine.player.y > 256f
        )
        assertTrue("No landing may be reported for a side entry", !engine.player.grounded)
    }

    @Test
    fun enemyRisingThroughOneWayGirderIsNotBlocked() {
        val engine = bareEngine()
        engine.platforms.add(
            Platform(100f, 300f, 200f, 18f, PlatformStyle.IRON_GIRDER, PlatformCollision.ONE_WAY)
        )
        engine.rebuildLevelGrid()
        val lurker = EnemyEntity(150f, 330f, EnemyArchetype.LURKER, 200f)
        lurker.vy = -300f
        engine.enemies.add(lurker)

        repeat(12) {
            engine.update(1f / 60f)
        }

        assertTrue("A rising enemy must pass through the girder's top surface", lurker.y < 300f)
        assertTrue("A one-way underside must not zero the enemy's vertical velocity", lurker.vy < 0f)
    }

    @Test
    fun enemyFallingFromAboveLandsOnOneWayGirder() {
        val engine = bareEngine()
        engine.platforms.add(
            Platform(100f, 300f, 200f, 18f, PlatformStyle.IRON_GIRDER, PlatformCollision.ONE_WAY)
        )
        engine.rebuildLevelGrid()
        val lurker = EnemyEntity(180f, 200f, EnemyArchetype.LURKER, 500f)
        engine.enemies.add(lurker)

        repeat(25) {
            engine.update(1f / 60f)
        }

        assertTrue("A descending enemy should land on the one-way top", lurker.grounded)
        assertEquals("The enemy's feet should rest exactly on the girder top", 300f, lurker.y + lurker.h, 0.5f)
    }

    @Test
    fun enemySideEntryOntoOneWayGirderDoesNotTeleportOnTop() {
        val engine = bareEngine()
        engine.platforms.add(
            Platform(100f, 300f, 200f, 18f, PlatformStyle.IRON_GIRDER, PlatformCollision.ONE_WAY)
        )
        engine.rebuildLevelGrid()
        val lurker = EnemyEntity(60f, 280f, EnemyArchetype.LURKER, 500f)
        lurker.facing = 1
        engine.enemies.add(lurker)

        repeat(10) {
            engine.update(1f / 60f)
        }

        assertTrue("The enemy should have reached the girder column", lurker.x + lurker.w > 100f)
        assertTrue(
            "A side entry must not teleport the enemy on top of the one-way girder",
            !lurker.grounded && lurker.y + lurker.h > 300f
        )
    }

    @Test
    fun enemyPatrolPassesThroughOneWayGirderColumn() {
        val engine = bareEngine()
        engine.platforms.add(Platform(0f, 460f, 400f, 80f, PlatformStyle.STONE))
        // A girder column at torso height: it must neither wall off the patrol
        // nor pop the enemy on top while it walks past on the floor.
        engine.platforms.add(
            Platform(150f, 430f, 60f, 18f, PlatformStyle.IRON_GIRDER, PlatformCollision.ONE_WAY)
        )
        engine.rebuildLevelGrid()
        val lurker = EnemyEntity(60f, 424f, EnemyArchetype.LURKER, 500f)
        lurker.facing = 1
        engine.enemies.add(lurker)

        repeat(80) {
            engine.update(1f / 60f)
        }

        assertTrue("A one-way girder must not wall off a patrolling enemy", lurker.x > 150f)
        assertTrue("The enemy must stay on the floor while passing the girder", lurker.y + lurker.h > 450f)
        assertTrue("The enemy must remain grounded on the solid floor", lurker.grounded)
    }
}
