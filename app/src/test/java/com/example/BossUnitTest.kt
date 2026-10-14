package com.example

import com.example.game.GameEngine
import com.example.game.boss.BossPhase
import com.example.game.boss.BossState
import com.example.game.boss.CrucibleChimeraBoss
import com.example.game.boss.WeakPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BossUnitTest {

    @Test
    fun testWeakPointIntersectionAndDamage() {
        val weakPoint = WeakPoint(
            id = "test_core",
            name = "Test Core",
            offsetX = 20f,
            offsetY = 15f,
            width = 30f,
            height = 30f,
            damageMultiplier = 2.5f,
            maxStaggerHealth = 50f
        )

        // Boss at (100, 100), width 80, facing right (1) -> WeakPoint world is (120..150, 115..145)
        val intersects = weakPoint.intersects(
            boxX = 125f, boxY = 120f, boxW = 10f, boxH = 10f,
            bossX = 100f, bossY = 100f, bossW = 80f, bossFacing = 1
        )
        assertTrue("Attack box should intersect exposed weak point", intersects)

        // Missed attack
        val misses = weakPoint.intersects(
            boxX = 10f, boxY = 10f, boxW = 10f, boxH = 10f,
            bossX = 100f, bossY = 100f, bossW = 80f, bossFacing = 1
        )
        assertFalse("Attack box far away should not intersect", misses)

        // Stagger threshold check
        assertFalse("First hit should not break stagger", weakPoint.applyWeakPointDamage(30f))
        assertEquals(20f, weakPoint.staggerHealth)
        assertTrue("Second hit exceeding remaining health should break stagger", weakPoint.applyWeakPointDamage(25f))
        assertTrue(weakPoint.isBroken)
        assertEquals(0f, weakPoint.staggerHealth)

        // Reset
        weakPoint.resetStagger()
        assertFalse(weakPoint.isBroken)
        assertEquals(50f, weakPoint.staggerHealth)
    }

    @Test
    fun testBossPhasesAndTransitions() {
        val boss = CrucibleChimeraBoss(spawnX = 1000f, spawnY = 300f)
        boss.active = true

        assertEquals(BossPhase.PHASE_1, boss.currentPhase)
        assertFalse(boss.isPhase2)

        // Reduce HP to trigger Phase 2 (<= 66% maxHp = 380 * 0.66 = 250.8f)
        boss.hp = 240f
        val engine = GameEngine()
        boss.takeDamage(0.1f, 9999L, 0f, engine)

        assertEquals(BossPhase.PHASE_2, boss.currentPhase)
        assertTrue(boss.isPhase2)
        assertEquals(BossState.PHASE_TRANSITION, boss.state)

        // Update past phase transition duration (1.2s)
        boss.update(1.3f, engine)
        assertEquals(BossState.IDLE, boss.state)

        // Reduce HP to trigger Phase 3 (<= 33% maxHp = 380 * 0.33 = 125.4f)
        boss.hp = 110f
        boss.takeDamage(0.1f, 10000L, 0f, engine)
        assertEquals(BossPhase.PHASE_3, boss.currentPhase)
    }

    @Test
    fun testTelegraphedAttackLifecycle() {
        val boss = CrucibleChimeraBoss(spawnX = 5750f, spawnY = 360f)
        boss.active = true
        val engine = GameEngine()

        // Initially in IDLE with stateTimer > 0
        assertEquals(BossState.IDLE, boss.state)

        // Advance past idle stateTimer -> enters TELEGRAPHING
        boss.update(1.1f, engine)
        assertEquals(BossState.TELEGRAPHING, boss.state)
        assertNotNull(boss.currentAttack)
        assertTrue(boss.telegraphProgress in 0f..1f)

        // Advance through telegraph duration -> enters ATTACKING
        val telegraphDuration = boss.currentAttack!!.telegraphDuration
        boss.update(telegraphDuration + 0.05f, engine)
        assertEquals(BossState.ATTACKING, boss.state)

        // Advance through active attack duration -> enters RECOVERING
        val attackDuration = boss.currentAttack!!.activeDuration
        boss.update(attackDuration + 0.05f, engine)
        assertEquals(BossState.RECOVERING, boss.state)

        // Advance through recovery duration -> returns to IDLE
        val recoveryDuration = boss.currentAttack!!.recoveryDuration
        boss.update(recoveryDuration + 0.05f, engine)
        assertEquals(BossState.IDLE, boss.state)
    }
}
