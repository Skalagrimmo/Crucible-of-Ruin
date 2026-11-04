package com.example

import com.example.game.EnemyArchetype
import com.example.game.GameLevelFactory
import com.example.game.Hazard
import com.example.game.HazardType
import com.example.game.Platform
import com.example.game.PlatformCollision
import com.example.game.PlatformStyle
import com.example.game.TiledLevelLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
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
        // Stage 6c (#38): the two moving platforms (the bastion elevator and the
        // laboratory ferry) join the 28 static corridor ones.
        // Stage 7b (#51): the gallery floor splits around the shaft and the
        // Undercroft adds three floors, the shaft lift, the climb, and a perch.
        assertEquals(40, fullLayout.platforms.size)
        assertEquals(9, fullLayout.hazards.size)
    }

    @Test
    fun oneWayTmxPropertyOverridesTheStyleDefaultCollision() {
        // Stage 3 (#35): an explicit oneWay property beats the style default, and
        // a missing property keeps the style default untouched.
        val tmx = """
            <?xml version="1.0" encoding="UTF-8"?>
            <map orientation="orthogonal" width="40" height="20" tilewidth="16" tileheight="16">
              <objectgroup name="Platforms">
                <object name="solid-girder" x="0" y="100" width="80" height="16">
                  <properties>
                    <property name="style" value="IRON_GIRDER"/>
                    <property name="oneWay" value="false"/>
                  </properties>
                </object>
                <object name="one-way-stone" x="100" y="100" width="80" height="16">
                  <properties>
                    <property name="style" value="STONE"/>
                    <property name="oneWay" value="true"/>
                  </properties>
                </object>
                <object name="default-girder" x="200" y="100" width="80" height="16">
                  <properties>
                    <property name="style" value="IRON_GIRDER"/>
                  </properties>
                </object>
              </objectgroup>
            </map>
        """.trimIndent()

        val section = TiledLevelLoader.load(ByteArrayInputStream(tmx.toByteArray(Charsets.UTF_8)))

        assertEquals(3, section.platforms.size)
        assertEquals(
            "oneWay=false must make a girder fully solid",
            PlatformCollision.SOLID,
            section.platforms[0].collision
        )
        assertEquals(
            "oneWay=true must make a stone slab pass-through",
            PlatformCollision.ONE_WAY,
            section.platforms[1].collision
        )
        assertEquals(
            "A missing oneWay property keeps the style default",
            PlatformCollision.ONE_WAY,
            section.platforms[2].collision
        )
    }
}
