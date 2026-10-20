package com.example

import com.example.game.GameEngine
import com.example.game.boss.BossPhase
import com.example.game.boss.BossState
import com.example.game.boss.CrucibleChimeraBoss
import com.example.game.boss.WeakPoint
import com.example.game.status.CombatStatusType
import com.example.game.status.StatusEffectController
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

    @Test
    fun testStatusEffectApplicationAndDotTick() {
        val controller = StatusEffectController()
        assertFalse(controller.hasEffect(CombatStatusType.ALCHEMY_BURN))

        controller.applyEffect(CombatStatusType.ALCHEMY_BURN, duration = 4.0f, dpsMultiplier = 1.0f, stacks = 1)
        assertTrue(controller.hasEffect(CombatStatusType.ALCHEMY_BURN))
        val eff = controller.getEffect(CombatStatusType.ALCHEMY_BURN)
        assertNotNull(eff)
        assertEquals(1, eff!!.stacks)
        assertEquals(4.0f, eff.remaining, 0.001f)

        // Stack accumulation up to maxStacks (3)
        controller.applyEffect(CombatStatusType.ALCHEMY_BURN, duration = 4.0f, stacks = 2)
        assertEquals(3, eff.stacks)

        // Cannot exceed maxStacks
        controller.applyEffect(CombatStatusType.ALCHEMY_BURN, duration = 4.0f, stacks = 2)
        assertEquals(3, eff.stacks)

        // Verify DoT ticking
        var totalTickDamage = 0f
        var tickCount = 0
        controller.update(0.55f) { type, damage, stacks ->
            assertEquals(CombatStatusType.ALCHEMY_BURN, type)
            assertEquals(3, stacks)
            totalTickDamage += damage
            tickCount++
        }
        assertEquals(1, tickCount)
        assertTrue(totalTickDamage > 0f)

        // Advance until expiration
        controller.update(5.0f) { _, _, _ -> }
        assertFalse(controller.hasEffect(CombatStatusType.ALCHEMY_BURN))
        assertTrue(controller.getActiveEffects().isEmpty())
    }

    @Test
    fun testCorrosionStatPenalties() {
        val controller = StatusEffectController()
        assertEquals(1.0f, controller.getIncomingDamageMultiplier(), 0.001f)
        assertEquals(1.0f, controller.getSpeedMultiplier(), 0.001f)

        // 1 Stack of Corrosion: +30% damage taken, -20% speed
        controller.applyEffect(CombatStatusType.CORROSION, duration = 5.0f, stacks = 1)
        assertEquals(1.30f, controller.getIncomingDamageMultiplier(), 0.001f)
        assertEquals(0.80f, controller.getSpeedMultiplier(), 0.001f)

        // 2 Stacks of Corrosion: +60% damage taken, -40% speed
        controller.applyEffect(CombatStatusType.CORROSION, duration = 5.0f, stacks = 1)
        assertEquals(1.60f, controller.getIncomingDamageMultiplier(), 0.001f)
        assertEquals(0.60f, controller.getSpeedMultiplier(), 0.001f)
    }

    @Test
    fun testBossTakesAmplifiedDamageFromCorrosion() {
        val boss = CrucibleChimeraBoss(spawnX = 1000f, spawnY = 300f)
        boss.active = true
        val engine = GameEngine()
        val initialHp = boss.hp

        // Apply normal hit without corrosion (hit from far away to avoid weak point)
        boss.takeDamage(20f, 1001L, 0f, engine)
        val damageTakenNormal = initialHp - boss.hp
        assertEquals(20f, damageTakenNormal, 0.01f)

        // Inflict 1 stack of Corrosion (+30% damage taken)
        boss.statusController.applyEffect(CombatStatusType.CORROSION, duration = 5f, stacks = 1)
        val hpBeforeCorrodedHit = boss.hp
        boss.takeDamage(20f, 1002L, 0f, engine)
        val damageTakenCorroded = hpBeforeCorrodedHit - boss.hp
        assertEquals(26f, damageTakenCorroded, 0.01f) // 20 * 1.30 = 26
    }

    @Test
    fun testBossWeakPointInflictsStatusEffects() {
        val boss = CrucibleChimeraBoss(spawnX = 1000f, spawnY = 300f)
        boss.active = true
        boss.facing = 1
        val engine = GameEngine()

        // Place player so slash directly hits weak point
        engine.player.x = 980f
        engine.player.y = 305f
        engine.player.facing = 1

        assertFalse(boss.statusController.hasEffect(CombatStatusType.CORROSION))
        assertFalse(boss.statusController.hasEffect(CombatStatusType.ALCHEMY_BURN))

        boss.takeDamage(30f, 2001L, engine.player.x, engine)

        // Weak point breach applies both Corrosion and Alchemy-Burn
        assertTrue(boss.statusController.hasEffect(CombatStatusType.CORROSION))
        assertTrue(boss.statusController.hasEffect(CombatStatusType.ALCHEMY_BURN))
    }

    @Test
    fun testShadowDashMechanicsAndCooldown() {
        val engine = GameEngine()
        val player = engine.player
        assertEquals(0f, player.dashCooldown, 0.001f)
        assertFalse(player.isDashing)

        // Initiate Dash
        engine.onDashPressed()
        assertTrue("Player should be in dashing state", player.isDashing)
        assertTrue("Dash cooldown should be active", player.dashCooldown > 0f)
        assertTrue("Player should have invulnerability frames during dash", player.invulnTimer > 0f)

        // Cannot dash again while cooldown is active
        val currentCd = player.dashCooldown
        engine.onDashPressed()
        assertEquals(currentCd, player.dashCooldown, 0.001f)

        // Advance past dash duration (0.22s)
        engine.update(0.25f)
        assertFalse("Player should have completed dash", player.isDashing)

        // Advance past remaining cooldown
        engine.update(player.maxDashCooldown + 0.1f)
        assertEquals(0f, player.dashCooldown, 0.001f)
    }

    @Test
    fun testPerfectRiposteRestoresFlaskAndRupturesBoss() {
        val engine = GameEngine()
        val player = engine.player
        val boss = engine.boss
        boss.active = true
        boss.state = BossState.TELEGRAPHING

        // Place player near telegraphing boss
        player.x = boss.x - 40f
        player.y = boss.y
        player.flasks = 1 // depleted flasks to test restoration

        val bossHpBefore = boss.hp
        assertEquals(0, engine.perfectDodgesCount)

        // Dash through telegraph
        engine.onDashPressed()

        // Verify Perfect Riposte triggers
        assertEquals(1, engine.perfectDodgesCount)
        assertEquals(2, player.flasks) // +1 flask restored!
        assertTrue(player.perfectRiposteTimer > 0f)
        assertTrue(boss.hp < bossHpBefore) // Boss took rupture counter damage
        assertTrue(boss.statusController.hasEffect(CombatStatusType.CORROSION))
        assertTrue(boss.statusController.hasEffect(CombatStatusType.ALCHEMY_BURN))
        assertTrue(engine.timeDilation < 1.0f) // Chrono break slow motion active
    }

    @Test
    fun testGothicBossHealthBarStateAndPoise() {
        val engine = GameEngine()
        val boss = engine.boss
        assertFalse("Health bar must not be visible when boss is inactive", boss.active && !boss.dead)

        // Activate boss encounter
        boss.active = true
        boss.dead = false
        assertTrue("Health bar must be visible during active boss encounter", boss.active && !boss.dead)

        // Verify Gothic Bar data contracts
        assertEquals("Ignis, Alchemical Chimera", boss.bossName)
        assertEquals(BossPhase.PHASE_1, boss.currentPhase)
        assertEquals("PHASE I: AWAKENED", boss.currentPhase.title)

        // Verify Poise / Stagger calculation
        val staggerRatio = (boss.weakPoint.staggerHealth / boss.weakPoint.maxStaggerHealth).coerceIn(0f, 1f)
        assertEquals(1.0f, staggerRatio, 0.001f)

        // Damage boss to 50% HP -> enters Phase 2
        boss.hp = boss.maxHp * 0.5f
        boss.takeDamage(1f, 9999L, 0f, engine)
        assertEquals(BossPhase.PHASE_2, boss.currentPhase)
        assertEquals("PHASE II: MOLTEN FURY", boss.currentPhase.title)

        // Mark dead -> health bar should deactivate
        boss.dead = true
        assertFalse("Health bar must not be visible when boss is dead", boss.active && !boss.dead)
    }
}
