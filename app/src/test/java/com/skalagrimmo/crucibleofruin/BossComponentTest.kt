package com.skalagrimmo.crucibleofruin

import androidx.compose.ui.graphics.Color
import com.skalagrimmo.crucibleofruin.game.GameEngine
import com.skalagrimmo.crucibleofruin.game.boss.BaseBoss
import com.skalagrimmo.crucibleofruin.game.boss.BossAttackPattern
import com.skalagrimmo.crucibleofruin.game.boss.BossComponent
import com.skalagrimmo.crucibleofruin.game.boss.TelegraphType
import com.skalagrimmo.crucibleofruin.game.boss.WeakPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Stage 9a (#62): multi-component boss system — damage routing, part
 * destruction hooks, attack stripping, and backward compatibility for
 * component-less bosses.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BossComponentTest {

    /** Test double: a small siege golem with one destructible arm + a core weak point. */
    private class ComponentTestBoss(spawnX: Float, spawnY: Float) : BaseBoss(
        x = spawnX,
        y = spawnY,
        w = 120f,
        h = 140f,
        maxHp = 300f,
        bossName = "Test Siege Golem",
        weakPoint = WeakPoint(
            id = "core",
            name = "Test Core",
            // Far from the arm zone: a 36px slash aimed mid-arm (x 510..540)
            // must not reach the core even after the arm is destroyed.
            offsetX = 90f,
            offsetY = 60f,
            width = 24f,
            height = 24f,
            damageMultiplier = 2.0f,
            maxStaggerHealth = 40f
        )
    ) {
        val arm = BossComponent(
            id = "arm_left",
            name = "Siege Arm",
            offsetX = 10f,
            offsetY = 40f,
            width = 30f,
            height = 60f,
            maxHp = 40f,
            damageMultiplier = 1.5f,
            powersAttackIds = setOf("smash"),
            accentColor = Color(0xFF4455FF)
        )

        override val components: List<BossComponent> = listOf(arm)

        var destroyedHookFiredFor: BossComponent? = null

        override fun onComponentDestroyed(component: BossComponent, engine: GameEngine) {
            super.onComponentDestroyed(component, engine)
            destroyedHookFiredFor = component
        }

        private val attackSmash = BossAttackPattern(
            id = "smash",
            name = "Arm Smash",
            type = TelegraphType.GROUND_SLAM,
            telegraphDuration = 0.1f,
            activeDuration = 0.1f,
            recoveryDuration = 0.1f,
            baseDamage = 10f,
            exposesWeakPoint = false
        )

        private val attackStomp = BossAttackPattern(
            id = "stomp",
            name = "Body Stomp",
            type = TelegraphType.GROUND_SLAM,
            telegraphDuration = 0.1f,
            activeDuration = 0.1f,
            recoveryDuration = 0.1f,
            baseDamage = 8f,
            exposesWeakPoint = false
        )

        override fun selectNextAttack(): BossAttackPattern =
            if (isAttackAvailable("smash")) attackSmash else attackStomp

        override fun onStartAttack(attack: BossAttackPattern, engine: GameEngine) {}
        override fun onUpdateAttack(attack: BossAttackPattern, dt: Float, engine: GameEngine) {}
    }

    /** Places the player so the melee slash box lands inside the arm's world bounds. */
    private fun aimPlayerAtArm(engine: GameEngine, boss: ComponentTestBoss) {
        val bounds = boss.arm.getWorldBounds(boss.x, boss.y, boss.w, boss.facing)
        val player = engine.player
        player.facing = 1
        // slashX = player.x + player.w - 4f -> land it mid-part horizontally
        player.x = (bounds[0] + bounds[2] / 2f) + 4f - player.w
        // slashY = player.y + 4f, slashH = player.h - 8f -> mid-part vertically
        player.y = (bounds[1] + bounds[3] / 2f) - player.h / 2f
    }

    @Test
    fun testComponentHitDamagesPartAndBody() {
        val boss = ComponentTestBoss(spawnX = 500f, spawnY = 300f)
        boss.active = true
        boss.facing = 1
        val engine = GameEngine()
        aimPlayerAtArm(engine, boss)

        val bodyHpBefore = boss.hp

        boss.takeDamage(20f, 7001L, engine.player.x, engine)

        // Component multiplier 1.5x: both the part AND the body take 30.
        assertEquals(30f, bodyHpBefore - boss.hp, 0.01f)
        assertEquals(10f, boss.arm.hp, 0.01f)
        assertFalse("One hit must not destroy a 40 HP part", boss.arm.destroyed)
        assertNull(boss.destroyedHookFiredFor)
    }

    @Test
    fun testComponentDestructionFiresHookAndStripsAttacks() {
        val boss = ComponentTestBoss(spawnX = 500f, spawnY = 300f)
        boss.active = true
        boss.facing = 1
        val engine = GameEngine()
        aimPlayerAtArm(engine, boss)

        // Smash is powered by the arm while it is attached.
        assertTrue(boss.isAttackAvailable("smash"))
        assertEquals("smash", boss.selectNextAttack().id)

        // Two 30-damage hits exceed the arm's 40 HP -> destroyed on the second.
        boss.takeDamage(20f, 7101L, engine.player.x, engine)
        boss.takeDamage(20f, 7102L, engine.player.x, engine)

        assertTrue("Arm should be destroyed", boss.arm.destroyed)
        assertEquals(0f, boss.arm.hp, 0.01f)
        assertEquals("Hook must fire exactly for the arm", boss.arm, boss.destroyedHookFiredFor)
        assertFalse("Boss body must still be alive", boss.dead)

        // Destroyed part no longer powers its attack.
        assertFalse(boss.isAttackAvailable("smash"))
        assertEquals("stomp", boss.selectNextAttack().id)
        assertTrue(boss.aliveComponents.isEmpty())
    }

    @Test
    fun testDestroyedComponentStopsInterceptingHits() {
        val boss = ComponentTestBoss(spawnX = 500f, spawnY = 300f)
        boss.active = true
        boss.facing = 1
        val engine = GameEngine()
        aimPlayerAtArm(engine, boss)

        boss.arm.applyDamage(999f)
        assertTrue(boss.arm.destroyed)

        val bounds = boss.arm.getWorldBounds(boss.x, boss.y, boss.w, boss.facing)
        assertFalse(
            "Destroyed part must not intercept hits",
            boss.arm.intersects(bounds[0], bounds[1], bounds[2], bounds[3], boss.x, boss.y, boss.w, boss.facing)
        )

        // A strike over the dead part's zone now routes to the body (plain damage).
        val bodyHpBefore = boss.hp
        boss.takeDamage(20f, 7201L, engine.player.x, engine)
        assertEquals(20f, bodyHpBefore - boss.hp, 0.01f)
    }

    @Test
    fun testComponentHurtFlashTicksDown() {
        val boss = ComponentTestBoss(spawnX = 500f, spawnY = 300f)
        boss.active = true
        boss.facing = 1
        val engine = GameEngine()
        aimPlayerAtArm(engine, boss)

        boss.takeDamage(5f, 7301L, engine.player.x, engine)
        assertTrue(boss.arm.hurtFlashTimer > 0f)

        boss.update(0.2f, engine)
        assertEquals(0f, boss.arm.hurtFlashTimer, 0.001f)
    }

    @Test
    fun testComponentlessBossKeepsSingleEntityBehavior() {
        val engine = GameEngine()
        val boss = engine.boss // CrucibleChimeraBoss — no components

        assertTrue("Chimera must remain component-less", boss.components.isEmpty())
        assertTrue(boss.aliveComponents.isEmpty())
        assertNull(boss.componentById("anything"))

        boss.active = true
        val hpBefore = boss.hp
        boss.takeDamage(20f, 7401L, 0f, engine)
        assertEquals(20f, hpBefore - boss.hp, 0.01f)
    }
}