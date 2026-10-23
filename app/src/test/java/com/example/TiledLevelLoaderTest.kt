package com.example

import com.example.game.EnemyArchetype
import com.example.game.GameLevelFactory
import com.example.game.Hazard
import com.example.game.HazardType
import com.example.game.Platform
import com.example.game.PlatformStyle
import com.example.game.TiledLevelLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TiledLevelLoaderTest {

    @Test
    fun ruinedBastionTmxLoadsTheExpectedGeometryAndEntities() {
        val path = listOf(
            File("src/main/assets/levels/ruined_bastion.tmx"),
            File("app/src/main/assets/levels/ruined_bastion.tmx")
        ).firstOrNull { it.isFile }
            ?: error("Could not find src/main/assets/levels/ruined_bastion.tmx")

        val section = path.inputStream().use { TiledLevelLoader.load(it) }

        assertEquals("Unexpected platform count", 9, section.platforms.size)
        assertEquals("Unexpected hazard count", 2, section.hazards.size)
        assertEquals("Unexpected enemy count", 5, section.enemies.size)
        assertEquals("Unexpected breakable count", 2, section.breakables.size)

        assertTrue(
            "Starting floor geometry must retain the current world coordinates",
            section.platforms.contains(Platform(0f, 460f, 600f, 80f, PlatformStyle.STONE))
        )
        assertTrue(
            "The first gap must retain its acid hazard",
            section.hazards.contains(Hazard(600f, 500f, 80f, 40f, HazardType.ACID))
        )
        assertTrue(
            "The upper jump sequence must keep its third ledge",
            section.platforms.contains(Platform(500f, 250f, 110f, 20f, PlatformStyle.STONE_LEDGE))
        )

        val firstLurker = section.enemies.first { it.x == 420f }
        assertEquals(EnemyArchetype.LURKER, firstLurker.type)
        assertEquals(120f, firstLurker.patrolRange, 0f)

        val healthUrn = section.breakables.first { it.x == 380f }
        assertEquals("health", healthUrn.dropType)
    }

    @Test
    fun tiledPrototypeMatchesItsCurrentFactoryLandmarksButDoesNotReplaceTheFullLevel() {
        val section = File("src/main/assets/levels/ruined_bastion.tmx")
            .takeIf { it.isFile }
            ?.inputStream()
            ?.use { TiledLevelLoader.load(it) }
            ?: File("app/src/main/assets/levels/ruined_bastion.tmx")
                .inputStream()
                .use { TiledLevelLoader.load(it) }

        val fullLayout = GameLevelFactory.create()
        assertTrue(fullLayout.platforms.contains(section.platforms.first()))
        assertTrue(fullLayout.hazards.contains(section.hazards.first()))
        assertEquals(28, fullLayout.platforms.size)
        assertEquals(6, fullLayout.hazards.size)
    }
}
