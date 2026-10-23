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
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CityDistrictPrototypeTest {
    private fun near(actual: Float, expected: Float, tolerance: Float = 0.05f): Boolean =
        abs(actual - expected) <= tolerance

    @Test
    fun `district uses larger proportions and keeps later areas connected`() {
        val engine = GameEngine()
        assertTrue(engine.platforms.any { it.x == 0f && it.y == 460f && it.w == 676f })
        assertTrue(engine.platforms.any { near(it.x, 338f) && near(it.y, 208f) && it.style == PlatformStyle.CITY_LEDGE && it.w > 450f })
        assertTrue(engine.platforms.any { near(it.x, 572f) && near(it.y, 712f) })
        assertTrue(engine.platforms.any { near(it.x, 1105f) && near(it.y, 491.5f) && it.style == PlatformStyle.CITY_LEDGE })
        assertTrue(engine.platforms.contains(RuinedBastionDistrict.shortcutGate))
        assertTrue(engine.platforms.any { it.x == 2496f && it.y == 420f && it.w == 380f })
        assertEquals(2556f, engine.checkpoints[1].x, 0.01f)
    }

    @Test
    fun `city ledge can be passed upward through`() {
        val engine = GameEngine()
        engine.update(1f / 60f)
        val ledge = engine.platforms.first { near(it.x, 383.5f) && near(it.y, 344.5f) && it.style == PlatformStyle.CITY_LEDGE }
        engine.player.x = 390f
        engine.player.y = ledge.y + 12f
        engine.player.vx = 0f
        engine.player.vy = -250f
        engine.player.grounded = false
        repeat(5) { engine.update(1f / 60f) }
        assertTrue("The player should pass upward through a one-way city ledge.", engine.player.y < ledge.y)
    }

    @Test
    fun `city ledge catches a falling player from above`() {
        val engine = GameEngine()
        engine.update(1f / 60f)
        val ledge = engine.platforms.first { near(it.x, 383.5f) && near(it.y, 344.5f) && it.style == PlatformStyle.CITY_LEDGE }
        engine.player.x = 390f
        engine.player.y = 260f
        engine.player.vx = 0f
        engine.player.vy = 350f
        engine.player.grounded = false
        repeat(20) { engine.update(1f / 60f) }
        assertTrue("The player should land on the enlarged city ledge.", engine.player.grounded)
        assertEquals(ledge.y - engine.player.h, engine.player.y, 1.5f)
    }

    @Test
    fun `shortcut gate opens after entering the far side at street level`() {
        val engine = GameEngine()
        assertTrue(engine.platforms.contains(RuinedBastionDistrict.shortcutGate))
        engine.player.x = RuinedBastionDistrict.worldX(1600f)
        engine.player.y = 416f
        engine.update(1f / 60f)
        assertTrue("The gate must remain closed on its near side.", engine.platforms.contains(RuinedBastionDistrict.shortcutGate))
        engine.player.x = RuinedBastionDistrict.worldX(1780f)
        engine.player.y = 416f
        engine.player.vx = 0f
        engine.player.vy = 0f
        engine.update(1f / 60f)
        assertFalse("The gate should disappear once the player reaches its far side.", engine.platforms.contains(RuinedBastionDistrict.shortcutGate))
    }
}
