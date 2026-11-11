package com.skalagrimmo.crucibleofruin

import com.skalagrimmo.crucibleofruin.game.GameEngine
import com.skalagrimmo.crucibleofruin.game.LiveLevelLoader
import com.skalagrimmo.crucibleofruin.game.boss.CrucibleChimeraBoss
import com.skalagrimmo.crucibleofruin.game.boss.SiegeGolemBoss
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream

/**
 * Stage 10d (#69): the live pipeline — the full-world TMX resolves into the
 * GameLevelLayout the engine installs, boss ids resolve through the closed
 * factory map, an unknown boss id fails fast, and the engine actually boots
 * on the TMX world through its level source.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LiveLevelLoaderTest {

    private fun loadLiveLayout() = LiveLevelLoader.load(
        FileInputStream(
            listOf(
                File("src/main/assets/levels/crucible_of_ruin.tmx"),
                File("app/src/main/assets/levels/crucible_of_ruin.tmx")
            ).firstOrNull { it.isFile }
                ?: throw IllegalStateException("crucible_of_ruin.tmx not found under assets/levels")
        )
    )

    @Test
    fun liveTmxResolvesIntoTheEngineLayout() {
        val layout = loadLiveLayout()
        assertEquals(57, layout.platforms.size)
        assertEquals(13, layout.hazards.size)
        assertEquals(18, layout.enemies.size)
        assertEquals(14, layout.breakables.size)
        assertEquals(15, layout.rooms.size)
        assertEquals(1, layout.gateTriggers.size)
        assertEquals(6200f, layout.width, 0f)
        assertEquals(1080f, layout.height, 0f)
    }

    @Test
    fun openingEncounterIsTheSiegeGolemAtItsTiledSpawn() {
        val layout = loadLiveLayout()
        assertTrue(layout.boss is SiegeGolemBoss)
        assertEquals(3500f, layout.boss.x, 0f)
        assertEquals(260f, layout.boss.y, 0f)
        assertEquals(3200f, layout.bossTriggerX, 0f)
        assertEquals(540f, layout.bossTriggerMaxY, 0f)
    }

    @Test
    fun queuedEncounterIsTheChimeraAtItsTiledSpawn() {
        val layout = loadLiveLayout()
        assertEquals(1, layout.laterEncounters.size)
        val chimera = layout.laterEncounters[0].createBoss()
        assertTrue(chimera is CrucibleChimeraBoss)
        assertEquals(5750f, chimera.x, 0f)
        assertEquals(360f, chimera.y, 0f)
        assertEquals(5340f, layout.laterEncounters[0].triggerX, 0f)
        assertEquals(540f, layout.laterEncounters[0].triggerMaxY, 0f)
    }

    @Test
    fun unknownBossIdFailsFast() {
        val tmx = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<map version=\"1.10\" tiledversion=\"1.12.2\" orientation=\"orthogonal\" renderorder=\"right-down\" width=\"10\" height=\"10\" tilewidth=\"10\" tileheight=\"10\" infinite=\"0\">\n" +
            "  <objectgroup id=\"1\" name=\"Bosses\">\n" +
            "    <object id=\"1\" name=\"mystery\" type=\"\" x=\"100\" y=\"200\">\n" +
            "      <properties>\n" +
            "        <property name=\"boss\" type=\"string\" value=\"unknown_boss\"/>\n" +
            "        <property name=\"triggerX\" type=\"string\" value=\"50\"/>\n" +
            "      </properties>\n" +
            "    </object>\n" +
            "  </objectgroup>\n" +
            "</map>\n"
        try {
            LiveLevelLoader.load(ByteArrayInputStream(tmx.toByteArray(Charsets.UTF_8)))
            throw AssertionError("Expected an IllegalArgumentException for the unknown bossId")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("unknown_boss"))
        }
    }

    @Test
    fun engineInstallsTheLiveTmxWorldThroughItsLevelSource() {
        val layout = loadLiveLayout()
        val engine = GameEngine(levelSource = { layout })
        assertEquals(6200f, engine.levelWidth, 0f)
        assertEquals(1080f, engine.levelHeight, 0f)
        assertEquals(57, engine.platforms.size)
        assertEquals(18, engine.enemies.size)
        assertTrue(engine.boss is SiegeGolemBoss)
    }
}
