package com.skalagrimmo.crucibleofruin

import com.skalagrimmo.crucibleofruin.game.GameEngine
import com.skalagrimmo.crucibleofruin.game.PlatformCollision
import com.skalagrimmo.crucibleofruin.game.PlatformStyle
import com.skalagrimmo.crucibleofruin.game.boss.BossPhase
import com.skalagrimmo.crucibleofruin.game.boss.CrucibleChimeraBoss
import com.skalagrimmo.crucibleofruin.game.boss.SiegeGolemBoss
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Stage 9c (#62): the Siege Golem — the first multi-component boss encounter.
 * Pins the encounter chain (golem mid-boss then Chimera finale), component
 * attack stripping, the parts-driven enrage, and the mid-boss defeat flow.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SiegeGolemEncounterTest {

    private fun tick(engine: GameEngine, ticks: Int) {
        repeat(ticks) { engine.update(1f / 60f) }
    }

    private fun armGolem(engine: GameEngine) {
        // Isolate traversal: neutralize the enemies on the approach stretch.
        engine.enemies.forEach { it.dead = true }
        // The Foundry Yard floor (x 3100..3360, top y=430); walking right
        // crosses the golem's arming line at x 3200.
        engine.player.x = 3150f
        engine.player.y = 430f - engine.player.h
        engine.player.vx = 0f
        engine.player.vy = 0f
        engine.inputRight = true
        var frames = 0
        while (!engine.boss.active && frames < 300) {
            engine.update(1f / 60f)
            frames++
        }
        assertTrue("Crossing the yard line must arm the Siege Golem", engine.boss.active)
        engine.inputRight = false
    }

    @Test
    fun stockLayoutOpensWithTheGolemEncounter() {
        val engine = GameEngine()

        assertTrue("The run must open with the Siege Golem", engine.boss is SiegeGolemBoss)
        assertEquals("Two destructible arm cannons", 2, engine.boss.components.size)
        assertEquals(
            "Two arenas give four boss gates",
            4,
            engine.platforms.count { it.style == PlatformStyle.BOSS_GATE }
        )
    }

    @Test
    fun walkingIntoTheFoundryYardSealsItsGates() {
        val engine = GameEngine()
        armGolem(engine)

        assertTrue(
            "All boss gates seal while the golem fight is on",
            engine.platforms.all {
                it.style != PlatformStyle.BOSS_GATE || it.collision == PlatformCollision.SOLID
            }
        )
    }

    @Test
    fun destroyingACannonStripsItsAttack() {
        val golem = SiegeGolemBoss(500f, 300f)

        assertTrue(golem.isAttackAvailable("sweep_volley"))
        assertTrue(golem.isAttackAvailable("magma_mortar"))

        golem.componentById("cannon_near")!!.applyDamage(999f)

        assertFalse("The near cannon's volley must be stripped", golem.isAttackAvailable("sweep_volley"))
        assertTrue("The far cannon's mortar must keep firing", golem.isAttackAvailable("magma_mortar"))
        repeat(8) { assertNotEquals("sweep_volley", golem.selectNextAttack().id) }
    }

    @Test
    fun destroyingBothCannonsOverloadsIntoBerserkSlamMode() {
        val engine = GameEngine()
        val golem = engine.boss as SiegeGolemBoss
        golem.active = true
        golem.facing = -1

        // Near cannon (world x 600..630 with facing -1): aim the slash mid-part.
        engine.player.x = 593f
        engine.player.y = 360f
        golem.takeDamage(30f, 9101L, engine.player.x, engine)
        golem.takeDamage(30f, 9102L, engine.player.x, engine)
        assertTrue("The near cannon must be destroyed", golem.cannonNear.destroyed)
        assertEquals("One cannon down is not yet the overload", BossPhase.PHASE_1, golem.currentPhase)

        // Far cannon (world x 500..530): aim the slash mid-part, clear of the core.
        engine.player.x = 488f
        golem.takeDamage(30f, 9103L, engine.player.x, engine)
        golem.takeDamage(30f, 9104L, engine.player.x, engine)

        assertTrue("The far cannon must be destroyed", golem.cannonFar.destroyed)
        assertEquals(
            "Both cannons gone must force the PHASE_3 overload",
            BossPhase.PHASE_3,
            golem.currentPhase
        )
        assertFalse(golem.isAttackAvailable("sweep_volley"))
        assertFalse(golem.isAttackAvailable("magma_mortar"))
        assertEquals("Berserk mode leaves only the body slam", "ground_slam", golem.selectNextAttack().id)
        assertFalse("The body must survive the cannon teardown", golem.dead)
    }

    @Test
    fun golemDefeatReopensTheArenaAndArmsTheChimera() {
        val engine = GameEngine()
        armGolem(engine)

        // Strike the golem down through its body HP.
        engine.boss.takeDamage(9999f, 9201L, engine.player.x, engine)
        assertTrue(engine.boss.dead)
        assertNotEquals("A mid-boss defeat must not end the run", "victory", engine.state)

        tick(engine, 2)

        assertTrue("The Chimera must wait as the next encounter", engine.boss is CrucibleChimeraBoss)
        assertFalse("The Chimera stays dormant until its own trigger", engine.boss.active)
        assertTrue(
            "The arena must reopen after the mid-boss falls",
            engine.platforms.all {
                it.style != PlatformStyle.BOSS_GATE || it.collision == PlatformCollision.OPEN
            }
        )

        // Walk the corridor into the Grand Sanctum: crossing 5340 arms the Chimera.
        engine.enemies.forEach { it.dead = true }
        engine.player.x = 5100f
        engine.player.y = 430f - engine.player.h
        engine.player.vx = 0f
        engine.player.vy = 0f
        engine.inputRight = true
        var frames = 0
        while (!engine.boss.active && frames < 400) {
            engine.update(1f / 60f)
            frames++
        }
        assertTrue(engine.boss.active)
        assertTrue(
            "Crossing the Sanctum line must arm the Chimera",
            engine.boss is CrucibleChimeraBoss
        )
    }
}
