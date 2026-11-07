package com.example

import com.example.game.ComboIndicator
import com.example.game.GameEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Stage 7 (#27): the combo meter's semantics, pinned.
 *
 * The recording that opened #27 showed "FINISHER!" followed by "COMBO X2" and
 * looked mismatched; the confirmed intent is that the badge labels the NEXT
 * AVAILABLE attack. The engine's comboIndex is that next stage by
 * construction, so these tests pin the stage transitions across a full chain,
 * the 0.65 s reset window, the inputs that must NOT touch the chain (jump and
 * dash), the guards (active swing, knockback, respawn), and the badge mapping
 * the HUD renders.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ComboIndicatorTest {

    /** Ticks the fixed-step engine [ticks] times (one tick = 1/60 s). */
    private fun tick(engine: GameEngine, ticks: Int) {
        repeat(ticks) { engine.update(1f / 60f) }
    }

    @Test
    fun firstSwingOpensTheWindowAndLabelsHitTwoAsNext() {
        val engine = GameEngine()
        assertEquals(0, engine.player.comboIndex)
        assertEquals(0f, engine.player.comboResetTimer, 0f)

        engine.onAttackPressed()

        assertTrue("Hit 1 must be the swing in flight", engine.player.attacking)
        assertEquals("The swing in flight is hit 1", 1, engine.player.lastComboHit)
        assertEquals("The next available stage after hit 1 is hit 2", 1, engine.player.comboIndex)
        assertEquals("The 0.65 s reset window opens with the swing", 0.65f, engine.player.comboResetTimer, 0.001f)
        assertEquals("Hit 1 is the quick jab", 0.22f, engine.player.attackDuration, 0.001f)

        val badge = ComboIndicator.badgeFor(engine.player.comboIndex, engine.player.comboResetTimer)
        assertTrue("The badge is visible while the window is open", badge.visible)
        assertFalse("After one hit the finisher is not yet available", badge.isFinisherReady)
        assertEquals("The badge labels the NEXT attack (hit 2)", "COMBO x2", badge.label)
        assertEquals("Pips light through the next stage", 2, badge.filledPips)
    }

    @Test
    fun secondSwingQueuesTheFinisherLabel() {
        val engine = GameEngine()
        engine.onAttackPressed()
        tick(engine, 14) // 0.233 s — hit 1 (0.22 s) must fully finish first

        engine.onAttackPressed()

        assertEquals("The swing in flight is hit 2", 2, engine.player.lastComboHit)
        assertEquals("The next available stage after hit 2 is the finisher", 2, engine.player.comboIndex)
        assertEquals("Hit 2 is the horizontal cleave", 0.26f, engine.player.attackDuration, 0.001f)
        assertFalse("The finisher flash fires only on the finisher itself", engine.player.comboFinisherFlashTimer > 0f)

        val badge = ComboIndicator.badgeFor(engine.player.comboIndex, engine.player.comboResetTimer)
        assertTrue("After two hits the finisher is the next available attack", badge.isFinisherReady)
        assertEquals("FINISHER!", badge.label)
        assertEquals(3, badge.filledPips)
    }

    @Test
    fun finisherSwingsHeavyAndWrapsTheChainBackToHitOne() {
        val engine = GameEngine()
        engine.onAttackPressed()
        tick(engine, 14)
        engine.onAttackPressed()
        tick(engine, 16) // 0.267 s — hit 2 (0.26 s) finishes

        engine.onAttackPressed()

        assertEquals("The finisher is hit 3", 3, engine.player.lastComboHit)
        assertEquals("The chain wraps: the next available stage is hit 1 again", 0, engine.player.comboIndex)
        assertEquals("The finisher swings heavy", 0.35f, engine.player.attackDuration, 0.001f)
        assertEquals("The finisher flash fires on the finisher", 0.35f, engine.player.comboFinisherFlashTimer, 0.001f)

        val badge = ComboIndicator.badgeFor(engine.player.comboIndex, engine.player.comboResetTimer)
        assertTrue("The window is still open after the wrap", badge.visible)
        assertEquals("After the finisher the badge reads hit 1 again", "COMBO x1", badge.label)
        assertEquals(1, badge.filledPips)
    }

    @Test
    fun anExpiredWindowResetsTheChainToHitOne() {
        val engine = GameEngine()
        engine.onAttackPressed()
        assertEquals(1, engine.player.comboIndex)

        tick(engine, 45) // 0.75 s > the 0.65 s window

        assertEquals("The chain resets to hit 1 once the window expires", 0, engine.player.comboIndex)
        assertEquals(0f, engine.player.comboResetTimer, 0.001f)
        assertFalse(
            "The badge hides once the window closes",
            ComboIndicator.badgeFor(engine.player.comboIndex, engine.player.comboResetTimer).visible
        )
    }

    @Test
    fun jumpAndDashDoNotDisturbTheChain() {
        val engine = GameEngine()
        engine.onAttackPressed()
        tick(engine, 16) // the player lands at spawn and hit 1 (0.22 s) finishes

        engine.onJumpPressed()
        tick(engine, 8)
        assertFalse("Sanity: the buffered jump must fire", engine.player.grounded)
        engine.onDashPressed()
        assertTrue("Sanity: the dash must fire", engine.player.isDashing)
        tick(engine, 4)

        assertEquals("A jump must not advance the combo stage", 1, engine.player.comboIndex)
        assertEquals("A dash must not advance the combo stage", 1, engine.player.comboIndex)
        assertEquals("The swing in flight is still hit 1", 1, engine.player.lastComboHit)
        assertTrue("The window is still open (well under 0.65 s)", engine.player.comboResetTimer > 0f)
        assertEquals(
            "The badge still labels hit 2 as the next attack",
            "COMBO x2",
            ComboIndicator.badgeFor(engine.player.comboIndex, engine.player.comboResetTimer).label
        )
    }

    @Test
    fun aSwingInFlightSwallowsTheNextPress() {
        val engine = GameEngine()
        engine.onAttackPressed()
        val attackId = engine.player.attackId

        engine.onAttackPressed()

        assertEquals("No new swing starts while the previous is active", attackId, engine.player.attackId)
        assertEquals("The stage does not advance while the swing is active", 1, engine.player.comboIndex)
        assertEquals("The swing in flight is still hit 1", 1, engine.player.lastComboHit)
    }

    @Test
    fun knockbackSuppressesNewSwings() {
        val engine = GameEngine()
        engine.player.knockbackTimer = 0.5f

        engine.onAttackPressed()

        assertFalse("A reeling player cannot start a swing", engine.player.attacking)
        assertEquals(0, engine.player.comboIndex)
        assertEquals(0f, engine.player.comboResetTimer, 0.001f)
    }

    @Test
    fun respawnClearsTheChain() {
        val engine = GameEngine()
        engine.onAttackPressed()
        tick(engine, 14)
        engine.onAttackPressed()
        assertEquals(2, engine.player.comboIndex)

        engine.player.takeDamage(1000f, engine.player.x, engine)
        assertTrue(engine.player.dead)

        tick(engine, 90) // 1.5 s > the 1.4 s respawn timer

        assertFalse(engine.player.dead)
        assertEquals("Respawn resets the chain to hit 1", 0, engine.player.comboIndex)
        assertEquals(0f, engine.player.comboResetTimer, 0f)
        assertEquals(1, engine.player.lastComboHit)
    }
}
