package com.example

import com.example.game.GameEngine
import com.example.game.GameLevelFactory
import com.example.game.GameLevelLayout
import com.example.game.LevelRoom
import com.example.game.Platform
import com.example.game.PlatformStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Stage 7a (#51): the world's size is a property of the level layout.
 *
 * The engine adopts the layout's bounds for its tile grid, camera clamp, and
 * pit/out-of-bounds thresholds. The stock world is 6200x1080 — the corridor
 * plus the Undercroft district stacked below it (Stage 7b, #51) — while a
 * custom layout may adopt any bounds.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WorldDimensionsTest {

    /** A minimal custom layout with explicit non-stock world bounds. */
    private fun customLayout(width: Float, height: Float): GameLevelLayout = GameLevelLayout(
        platforms = listOf(
            Platform(0f, height - 80f, width, 80f, PlatformStyle.STONE)
        ),
        hazards = emptyList(),
        breakables = emptyList(),
        enemies = emptyList(),
        boss = GameLevelFactory.createBoss(),
        rooms = listOf(
            LevelRoom(id = "custom_room", name = "Custom Room", x = 0f, y = 0f, w = width, h = height)
        ),
        width = width,
        height = height
    )

    @Test
    fun stockWorldKeepsItsDimensions() {
        val engine = GameEngine()

        // Stage 7b (#51): the stock world grew one corridor height downward —
        // the Undercroft district stacks below the 540 px corridor.
        assertEquals(6200f, engine.levelWidth)
        assertEquals(1080f, engine.levelHeight)
        assertEquals(620, engine.levelGrid.cols)
        assertEquals(108, engine.levelGrid.rows)
    }

    @Test
    fun engineAdoptsLayoutDimensionsAndGridCoverage() {
        val engine = GameEngine()
        engine.initLevel(customLayout(width = 1500f, height = 1000f))

        assertEquals(1500f, engine.levelWidth)
        assertEquals(1000f, engine.levelHeight)
        assertEquals(150, engine.levelGrid.cols)
        assertEquals(100, engine.levelGrid.rows)
        assertTrue(
            "Geometry at the custom world's far corner must rasterize into the resized grid",
            engine.levelGrid.rectTouchesCollision(1494f, 994f, 2f, 2f)
        )
    }

    @Test
    fun restartReturnsToTheFactoryDimensions() {
        val engine = GameEngine()
        engine.initLevel(customLayout(width = 1500f, height = 1000f))

        engine.restart()

        assertEquals(6200f, engine.levelWidth)
        assertEquals(1080f, engine.levelHeight)
        assertEquals(620, engine.levelGrid.cols)
        assertEquals(108, engine.levelGrid.rows)
    }

    @Test
    fun pitDeathFollowsTheLayoutHeight() {
        val engine = GameEngine()
        engine.initLevel(customLayout(width = 6200f, height = 300f))
        engine.player.x = 100f
        engine.player.y = 450f

        repeat(6) { engine.update(0.08f) }

        assertTrue(
            "A player below the layout's height + 100 must die on the next ticks",
            engine.player.dead
        )
    }

    @Test
    fun cameraClampsToTheLayoutBounds() {
        val engine = GameEngine()
        engine.initLevel(customLayout(width = 1500f, height = 1000f))
        engine.player.x = 1400f
        engine.player.y = 900f

        repeat(30) { engine.update(1f / 30f) }

        assertTrue(
            "Camera must clamp to the layout's horizontal bound",
            engine.camera.x <= 1500f - 960f + 0.5f
        )
        assertTrue(
            "Camera must clamp to the layout's vertical bound",
            engine.camera.y <= 1000f - 540f + 0.5f
        )
        assertFalse(engine.camera.x < 0f)
        assertFalse(engine.camera.y < 0f)
    }

    @Test
    fun layoutDefaultsKeepTheWorldBounds() {
        val layout = GameLevelFactory.create()

        assertEquals(6200f, layout.width)
        assertEquals(1080f, layout.height)
    }
}
