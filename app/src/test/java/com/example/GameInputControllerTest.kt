package com.example

import com.example.game.GameEngine
import com.example.game.GameInputController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GameInputControllerTest {

    @Test
    fun directionalHoldCallbacksSetAndClearMovementFlags() {
        val engine = GameEngine()
        val input = GameInputController(engine)

        input.setLeftHeld(true)
        input.setRightHeld(true)
        assertTrue(engine.inputLeft)
        assertTrue(engine.inputRight)

        input.setLeftHeld(false)
        input.setRightHeld(false)
        assertFalse(engine.inputLeft)
        assertFalse(engine.inputRight)
    }

    @Test
    fun jumpHoldMirrorsHeldStateAndQueuesJumpOnPress() {
        val engine = GameEngine()
        val input = GameInputController(engine)

        input.setJumpHeld(true)

        assertTrue(engine.inputJump)
        assertEquals(0.15f, engine.player.jumpBuffer, 0f)

        input.setJumpHeld(false)

        assertFalse(engine.inputJump)
        // Releasing the button does not erase the already queued jump buffer.
        assertEquals(0.15f, engine.player.jumpBuffer, 0f)
    }

    @Test
    fun actionTapsDelegateToExistingEngineActions() {
        val flaskEngine = GameEngine()
        GameInputController(flaskEngine).tapFlask()
        assertEquals(2, flaskEngine.player.flasks)
        assertEquals(1, flaskEngine.projectiles.activeCount)

        val dashEngine = GameEngine()
        val dashInput = GameInputController(dashEngine)
        dashInput.setLeftHeld(true)
        dashInput.tapDash()
        assertTrue(dashEngine.player.isDashing)
        assertEquals(-1, dashEngine.player.dashDir)

        val attackEngine = GameEngine()
        GameInputController(attackEngine).tapAttack()
        assertTrue(attackEngine.player.attacking)
        assertEquals(1, attackEngine.player.lastComboHit)
    }
}
