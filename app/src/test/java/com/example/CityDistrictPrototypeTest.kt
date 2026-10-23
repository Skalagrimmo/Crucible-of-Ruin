package com.example

import com.example.game.GameEngine
import com.example.game.PlatformStyle
import com.example.game.RuinedBastionDistrict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CityDistrictPrototypeTest {
    @Test
    fun `district includes street rooftops and an underpass return route`() {
        val engine = GameEngine()

        assertTrue(engine.platforms.any { it.x == 0f && it.y == 460f && it.w == 520f })
        assertTrue(engine.platforms.any { it.x == 260f && it.y == 220f && it.style == PlatformStyle.CITY_LEDGE })
        assertTrue(engine.platforms.any { it.x == 440f && it.y == 700f })
        assertTrue(engine.platforms.any { it.x == 850f && it.y == 490f && it.style == PlatformStyle.CITY_LEDGE })
        assertTrue(engine.platforms.contains(RuinedBastionDistrict.shortcutGate))
    }

    @Test
    fun `city ledge can be passed through from below and landed on from above`() {
        val engine = GameEngine()
        engine.update(1f / 60f)

        engine.player.x = 300f
        engine.player.y = 416f
        engine.player.vx = 0f
        engine.player.vy = 0f
        engine.player.grounded = true
        engine.player.coyoteTime = 0.12f
        engine.inputJump = true
        engine.onJumpPressed()

        repeat(50) {
            engine.update(1f / 60f)
        }

        assertTrue("The player should land on the interior city ledge.", engine.player.grounded)
        assertEquals(306f, engine.player.y, 1.5f)
    }

    @Test
    fun `shortcut gate opens after entering the far side at street level`() {
        val engine = GameEngine()
        assertTrue(engine.platforms.contains(RuinedBastionDistrict.shortcutGate))

        engine.player.x = 1600f
        engine.player.y = 416f
        engine.update(1f / 60f)
        assertTrue("The gate must remain closed on its near side.", engine.platforms.contains(RuinedBastionDistrict.shortcutGate))

        engine.player.x = 1780f
        engine.player.y = 416f
        engine.player.vx = 0f
        engine.player.vy = 0f
        engine.update(1f / 60f)

        assertFalse("The gate should disappear once the player reaches its far side.", engine.platforms.contains(RuinedBastionDistrict.shortcutGate))
    }
}
