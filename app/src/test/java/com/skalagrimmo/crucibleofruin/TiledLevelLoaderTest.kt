package com.skalagrimmo.crucibleofruin

import com.skalagrimmo.crucibleofruin.game.EnemyArchetype
import com.skalagrimmo.crucibleofruin.game.GateTrigger
import com.skalagrimmo.crucibleofruin.game.LevelRoom
import com.skalagrimmo.crucibleofruin.game.RoomDoor
import com.skalagrimmo.crucibleofruin.game.TiledBossEncounter
import com.skalagrimmo.crucibleofruin.game.GameLevelFactory
import com.skalagrimmo.crucibleofruin.game.Hazard
import com.skalagrimmo.crucibleofruin.game.HazardType
import com.skalagrimmo.crucibleofruin.game.Platform
import com.skalagrimmo.crucibleofruin.game.PlatformCollision
import com.skalagrimmo.crucibleofruin.game.PlatformMotion
import com.skalagrimmo.crucibleofruin.game.PlatformStyle
import com.skalagrimmo.crucibleofruin.game.TiledLevelLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        // Stage 10a (#69): the shipped section's map-level bounds flow in.
        assertEquals(1920f, section.width, 0f)
        // Stage 10b (#69): the prototype section carries no meta layers yet.
        assertTrue(section.rooms.isEmpty())
        assertTrue(section.gateTriggers.isEmpty())
        assertTrue(section.bossEncounters.isEmpty())
        assertEquals(540f, section.height, 0f)

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
        // Stage 8b (#56): the Iron Garrison adds the district street, the
        // stair return, four fortress blocks, three rooftops, the sealed
        // gate, and four street hazards.
        // Stage 9c (#62): the Foundry Yard arena adds two boss gates.
        assertEquals(57, fullLayout.platforms.size)
        assertEquals(13, fullLayout.hazards.size)
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

    @Test
    fun motionPropertiesBuildTheDeterministicPingPongMotion() {
        // Stage 10a (#69): motionDx/motionDy/motionSpeed build the Stage 6c
        // PlatformMotion with the object rectangle as its base; an explicit
        // motionStartPhase flows through and the default is 0.
        val tmx = """
            <?xml version="1.0" encoding="UTF-8"?>
            <map orientation="orthogonal" width="40" height="20" tilewidth="16" tileheight="16">
              <objectgroup name="Platforms">
                <object name="elevator" x="610" y="330" width="60" height="20">
                  <properties>
                    <property name="style" value="IRON_GIRDER"/>
                    <property name="oneWay" value="true"/>
                    <property name="motionDx" value="0"/>
                    <property name="motionDy" value="120"/>
                    <property name="motionSpeed" value="60"/>
                  </properties>
                </object>
                <object name="phased_ferry" x="2800" y="250" width="80" height="20">
                  <properties>
                    <property name="style" value="IRON_GIRDER"/>
                    <property name="oneWay" value="true"/>
                    <property name="motionDx" value="60"/>
                    <property name="motionDy" value="0"/>
                    <property name="motionSpeed" value="60"/>
                    <property name="motionStartPhase" value="0.25"/>
                  </properties>
                </object>
              </objectgroup>
            </map>
        """.trimIndent()

        val section = TiledLevelLoader.load(ByteArrayInputStream(tmx.toByteArray(Charsets.UTF_8)))

        assertEquals(2, section.platforms.size)
        assertEquals(
            "The object rectangle is the motion base; the properties feed the ping-pong",
            PlatformMotion(baseX = 610f, baseY = 330f, dx = 0f, dy = 120f, speed = 60f),
            section.platforms[0].motion
        )
        assertEquals(
            "An explicit motionStartPhase flows through",
            PlatformMotion(baseX = 2800f, baseY = 250f, dx = 60f, dy = 0f, speed = 60f, startPhase = 0.25f),
            section.platforms[1].motion
        )
    }

    @Test
    fun partialMotionPropertiesFailFast() {
        // Stage 10a (#69): declaring only part of the motion triple must fail
        // loudly instead of silently producing a static platform.
        val tmx = """
            <?xml version="1.0" encoding="UTF-8"?>
            <map orientation="orthogonal" width="40" height="20" tilewidth="16" tileheight="16">
              <objectgroup name="Platforms">
                <object name="broken_elevator" x="0" y="330" width="60" height="20">
                  <properties>
                    <property name="style" value="IRON_GIRDER"/>
                    <property name="motionDx" value="0"/>
                    <property name="motionDy" value="120"/>
                  </properties>
                </object>
              </objectgroup>
            </map>
        """.trimIndent()

        val exception = loadExpectingFailure(tmx)
        assertTrue(
            "The error must name the full motion triple",
            exception.message!!.contains("motionDx, motionDy and motionSpeed")
        )
    }

    @Test
    fun nonPositiveMotionSpeedFailsFast() {
        // Stage 10a (#69): a degenerate speed would zero out the period and
        // freeze the platform at its base — reject it at load time.
        val tmx = """
            <?xml version="1.0" encoding="UTF-8"?>
            <map orientation="orthogonal" width="40" height="20" tilewidth="16" tileheight="16">
              <objectgroup name="Platforms">
                <object name="frozen_ferry" x="0" y="330" width="60" height="20">
                  <properties>
                    <property name="style" value="IRON_GIRDER"/>
                    <property name="motionDx" value="0"/>
                    <property name="motionDy" value="120"/>
                    <property name="motionSpeed" value="0"/>
                  </properties>
                </object>
              </objectgroup>
            </map>
        """.trimIndent()

        val exception = loadExpectingFailure(tmx)
        assertTrue(exception.message!!.contains("motionSpeed"))
    }

    @Test
    fun gateIdPropertyFlowsIntoThePlatform() {
        // Stage 10a (#69): the sealed shortcut gate carries its trigger
        // identity; plain platforms keep a null gateId.
        val tmx = """
            <?xml version="1.0" encoding="UTF-8"?>
            <map orientation="orthogonal" width="40" height="20" tilewidth="16" tileheight="16">
              <objectgroup name="Platforms">
                <object name="keep_gate" x="5700" y="880" width="30" height="120">
                  <properties>
                    <property name="style" value="IRON_GATE"/>
                    <property name="gateId" value="garrison_gate"/>
                  </properties>
                </object>
                <object name="plain_floor" x="0" y="460" width="600" height="80">
                  <properties>
                    <property name="style" value="STONE"/>
                  </properties>
                </object>
              </objectgroup>
            </map>
        """.trimIndent()

        val section = TiledLevelLoader.load(ByteArrayInputStream(tmx.toByteArray(Charsets.UTF_8)))

        assertEquals(2, section.platforms.size)
        assertEquals("garrison_gate", section.platforms[0].gateId)
        assertEquals(PlatformCollision.SOLID, section.platforms[0].collision)
        assertNull("A platform without gateId stays null", section.platforms[1].gateId)
    }

    @Test
    fun collisionPropertyOverridesOneWayAndTheStyleDefault() {
        // Stage 10a (#69): collision=OPEN reproduces the factory's boss-gate
        // pattern; an explicit collision beats the oneWay shorthand; a missing
        // property keeps the style default.
        val tmx = """
            <?xml version="1.0" encoding="UTF-8"?>
            <map orientation="orthogonal" width="40" height="20" tilewidth="16" tileheight="16">
              <objectgroup name="Platforms">
                <object name="open_boss_gate" x="0" y="80" width="30" height="350">
                  <properties>
                    <property name="style" value="BOSS_GATE"/>
                    <property name="collision" value="OPEN"/>
                  </properties>
                </object>
                <object name="collision_beats_oneWay" x="100" y="80" width="30" height="350">
                  <properties>
                    <property name="style" value="BOSS_GATE"/>
                    <property name="oneWay" value="true"/>
                    <property name="collision" value="SOLID"/>
                  </properties>
                </object>
                <object name="default_boss_gate" x="200" y="80" width="30" height="350">
                  <properties>
                    <property name="style" value="BOSS_GATE"/>
                  </properties>
                </object>
              </objectgroup>
            </map>
        """.trimIndent()

        val section = TiledLevelLoader.load(ByteArrayInputStream(tmx.toByteArray(Charsets.UTF_8)))

        assertEquals(3, section.platforms.size)
        assertEquals(
            "collision=OPEN must reproduce the factory's boss-gate pattern",
            PlatformCollision.OPEN,
            section.platforms[0].collision
        )
        assertEquals(
            "An explicit collision beats the oneWay shorthand",
            PlatformCollision.SOLID,
            section.platforms[1].collision
        )
        assertEquals(
            "A missing collision property keeps the style default",
            PlatformCollision.SOLID,
            section.platforms[2].collision
        )
    }

    @Test
    fun invalidCollisionValueFailsFast() {
        val tmx = """
            <?xml version="1.0" encoding="UTF-8"?>
            <map orientation="orthogonal" width="40" height="20" tilewidth="16" tileheight="16">
              <objectgroup name="Platforms">
                <object name="wet_gate" x="0" y="80" width="30" height="350">
                  <properties>
                    <property name="style" value="BOSS_GATE"/>
                    <property name="collision" value="WATER"/>
                  </properties>
                </object>
              </objectgroup>
            </map>
        """.trimIndent()

        val exception = loadExpectingFailure(tmx)
        assertTrue(exception.message!!.contains("'collision'"))
    }

    @Test
    fun mapLevelBoundsFlowIntoTheSection() {
        // Stage 10a (#69): levelWidth/levelHeight map properties become the
        // section bounds; a map without them keeps the stock corridor defaults.
        val withBounds = """
            <?xml version="1.0" encoding="UTF-8"?>
            <map orientation="orthogonal" width="40" height="20" tilewidth="16" tileheight="16">
              <properties>
                <property name="levelWidth" type="int" value="1920"/>
                <property name="levelHeight" type="int" value="1080"/>
              </properties>
              <objectgroup name="Platforms"/>
            </map>
        """.trimIndent()

        val section = TiledLevelLoader.load(ByteArrayInputStream(withBounds.toByteArray(Charsets.UTF_8)))
        assertEquals(1920f, section.width, 0f)
        assertEquals(1080f, section.height, 0f)

        val withoutBounds = """
            <?xml version="1.0" encoding="UTF-8"?>
            <map orientation="orthogonal" width="40" height="20" tilewidth="16" tileheight="16">
              <objectgroup name="Platforms"/>
            </map>
        """.trimIndent()

        val fallback = TiledLevelLoader.load(ByteArrayInputStream(withoutBounds.toByteArray(Charsets.UTF_8)))
        assertEquals(6200f, fallback.width, 0f)
        assertEquals(540f, fallback.height, 0f)
    }

    @Test
    fun invalidMapBoundsFailFast() {
        val withBadBounds = """
            <?xml version="1.0" encoding="UTF-8"?>
            <map orientation="orthogonal" width="40" height="20" tilewidth="16" tileheight="16">
              <properties>
                <property name="levelWidth" type="int" value="wide"/>
              </properties>
              <objectgroup name="Platforms"/>
            </map>
        """.trimIndent()

        val exception = loadExpectingFailure(withBadBounds)
        assertTrue(exception.message!!.contains("levelWidth"))
    }

    @Test
    fun roomsAndDoorsParseIntoTheRoomGraph() {
        // Stage 10b (#69): rooms are world rectangles keyed by roomId; door
        // objects anchor a transition at a world point and attach to the
        // room named by fromRoomId — both sides declare it, as the factory does.
        val tmx = """
            <?xml version="1.0" encoding="UTF-8"?>
            <map orientation="orthogonal" width="40" height="20" tilewidth="16" tileheight="16">
              <objectgroup name="Rooms">
                <object name="hall" x="0" y="0" width="600" height="540">
                  <properties>
                    <property name="roomId" value="bastion_entry"/>
                    <property name="roomName" value="Bastion Entry Hall"/>
                  </properties>
                </object>
                <object name="ascent" x="600" y="0" width="600" height="540">
                  <properties>
                    <property name="roomId" value="bastion_ascent"/>
                    <property name="roomName" value="The Bastion Ascent"/>
                  </properties>
                </object>
              </objectgroup>
              <objectgroup name="Doors">
                <object name="door_out" x="600" y="460" width="8" height="80">
                  <properties>
                    <property name="fromRoomId" value="bastion_entry"/>
                    <property name="toRoomId" value="bastion_ascent"/>
                  </properties>
                </object>
                <object name="door_back" x="600" y="460" width="8" height="80">
                  <properties>
                    <property name="fromRoomId" value="bastion_ascent"/>
                    <property name="toRoomId" value="bastion_entry"/>
                  </properties>
                </object>
              </objectgroup>
            </map>
        """.trimIndent()

        val section = TiledLevelLoader.load(ByteArrayInputStream(tmx.toByteArray(Charsets.UTF_8)))

        assertEquals(2, section.rooms.size)
        val entry = section.rooms.first { it.id == "bastion_entry" }
        assertEquals("Bastion Entry Hall", entry.name)
        assertEquals(600f, entry.w, 0f)
        assertEquals(540f, entry.h, 0f)
        assertEquals(
            listOf(RoomDoor(x = 600f, y = 460f, toRoomId = "bastion_ascent")),
            entry.doors
        )
        val ascent = section.rooms.first { it.id == "bastion_ascent" }
        assertEquals(
            listOf(RoomDoor(x = 600f, y = 460f, toRoomId = "bastion_entry")),
            ascent.doors
        )
    }

    @Test
    fun unknownDoorRoomFailsFast() {
        // Stage 10b (#69): a door into a room the layer never declares must
        // fail at load time, not on the world map.
        val tmx = """
            <?xml version="1.0" encoding="UTF-8"?>
            <map orientation="orthogonal" width="40" height="20" tilewidth="16" tileheight="16">
              <objectgroup name="Rooms">
                <object name="hall" x="0" y="0" width="600" height="540">
                  <properties>
                    <property name="roomId" value="bastion_entry"/>
                    <property name="roomName" value="Bastion Entry Hall"/>
                  </properties>
                </object>
              </objectgroup>
              <objectgroup name="Doors">
                <object name="door_nowhere" x="600" y="460" width="8" height="80">
                  <properties>
                    <property name="fromRoomId" value="bastion_entry"/>
                    <property name="toRoomId" value="missing_room"/>
                  </properties>
                </object>
              </objectgroup>
            </map>
        """.trimIndent()

        val exception = loadExpectingFailure(tmx)
        assertTrue(exception.message!!.contains("missing_room"))
    }

    @Test
    fun duplicateRoomIdFailsFast() {
        val tmx = """
            <?xml version="1.0" encoding="UTF-8"?>
            <map orientation="orthogonal" width="40" height="20" tilewidth="16" tileheight="16">
              <objectgroup name="Rooms">
                <object name="hall_a" x="0" y="0" width="300" height="540">
                  <properties>
                    <property name="roomId" value="bastion_entry"/>
                    <property name="roomName" value="Bastion Entry Hall"/>
                  </properties>
                </object>
                <object name="hall_b" x="300" y="0" width="300" height="540">
                  <properties>
                    <property name="roomId" value="bastion_entry"/>
                    <property name="roomName" value="Impostor Hall"/>
                  </properties>
                </object>
              </objectgroup>
            </map>
        """.trimIndent()

        val exception = loadExpectingFailure(tmx)
        assertTrue(exception.message!!.contains("bastion_entry"))
    }

    @Test
    fun gateTriggersParseIntoUnlockRectangles() {
        // Stage 10b (#69): the GateTriggers layer mirrors the Stage 8a
        // shortcut unlock semantics.
        val tmx = """
            <?xml version="1.0" encoding="UTF-8"?>
            <map orientation="orthogonal" width="40" height="20" tilewidth="16" tileheight="16">
              <objectgroup name="GateTriggers">
                <object name="gate_unlock" x="5800" y="540" width="80" height="140">
                  <properties>
                    <property name="gateId" value="garrison_gate"/>
                  </properties>
                </object>
              </objectgroup>
            </map>
        """.trimIndent()

        val section = TiledLevelLoader.load(ByteArrayInputStream(tmx.toByteArray(Charsets.UTF_8)))

        assertEquals(
            listOf(GateTrigger(gateId = "garrison_gate", x = 5800f, y = 540f, w = 80f, h = 140f)),
            section.gateTriggers
        )
    }

    @Test
    fun gateTriggerWithoutGateIdFailsFast() {
        val tmx = """
            <?xml version="1.0" encoding="UTF-8"?>
            <map orientation="orthogonal" width="40" height="20" tilewidth="16" tileheight="16">
              <objectgroup name="GateTriggers">
                <object name="anonymous_trigger" x="5800" y="540" width="80" height="140"/>
              </objectgroup>
            </map>
        """.trimIndent()

        val exception = loadExpectingFailure(tmx)
        assertTrue(exception.message!!.contains("gateId"))
    }

    @Test
    fun bossLayerParsesTheEncounterChain() {
        // Stage 10b (#69): the first Bosses object is the opening encounter,
        // the rest queue behind it in layer order; the object position is the
        // spawn point and triggerMaxY defaults to the corridor height.
        val tmx = """
            <?xml version="1.0" encoding="UTF-8"?>
            <map orientation="orthogonal" width="40" height="20" tilewidth="16" tileheight="16">
              <objectgroup name="Bosses">
                <object name="siege_golem" x="3500" y="260" width="130" height="170">
                  <properties>
                    <property name="boss" value="siege_golem"/>
                    <property name="triggerX" value="3200"/>
                    <property name="triggerMaxY" value="540"/>
                  </properties>
                </object>
                <object name="chimera" x="5750" y="360" width="200" height="180">
                  <properties>
                    <property name="boss" value="chimera"/>
                    <property name="triggerX" value="5340"/>
                  </properties>
                </object>
              </objectgroup>
            </map>
        """.trimIndent()

        val section = TiledLevelLoader.load(ByteArrayInputStream(tmx.toByteArray(Charsets.UTF_8)))

        assertEquals(
            listOf(
                TiledBossEncounter(
                    bossId = "siege_golem",
                    spawnX = 3500f,
                    spawnY = 260f,
                    triggerX = 3200f,
                    triggerMaxY = 540f
                ),
                TiledBossEncounter(
                    bossId = "chimera",
                    spawnX = 5750f,
                    spawnY = 360f,
                    triggerX = 5340f,
                    triggerMaxY = 540f
                )
            ),
            section.bossEncounters
        )
    }

    @Test
    fun bossWithoutArchetypeFailsFast() {
        val tmx = """
            <?xml version="1.0" encoding="UTF-8"?>
            <map orientation="orthogonal" width="40" height="20" tilewidth="16" tileheight="16">
              <objectgroup name="Bosses">
                <object name="nameless" x="3500" y="260" width="130" height="170">
                  <properties>
                    <property name="triggerX" value="3200"/>
                  </properties>
                </object>
              </objectgroup>
            </map>
        """.trimIndent()

        val exception = loadExpectingFailure(tmx)
        assertTrue(exception.message!!.contains("'boss'"))
    }
    private fun loadExpectingFailure(tmx: String): IllegalArgumentException {
        val exception = try {
            TiledLevelLoader.load(ByteArrayInputStream(tmx.toByteArray(Charsets.UTF_8)))
            null
        } catch (expected: IllegalArgumentException) {
            expected
        }
        assertTrue("Expected an IllegalArgumentException for malformed TMX", exception != null)
        return exception!!
    }
}