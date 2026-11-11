package com.skalagrimmo.crucibleofruin

import com.skalagrimmo.crucibleofruin.game.GameLevelFactory
import com.skalagrimmo.crucibleofruin.game.TiledLevelLoader
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileInputStream

/**
 * Stage 10c (#69): the full-world TMX asset must be a semantic twin of
 * GameLevelFactory.create() — the same platforms (order included), hazards,
 * urns, enemies, room/door graph, gate triggers, world bounds, and boss
 * encounter chain. The factory stays the runtime source of truth until the
 * Stage 10d switch; this test is the contract that the live asset can
 * replace it without moving a single pixel.
 *
 * Platform/Hazard/LevelRoom/GateTrigger are data classes, so ordered-list
 * equality covers them (including motion and gate identity). BreakableUrn
 * and EnemyEntity are plain classes, so they are compared field by field.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FullLevelTmxParityTest {

    private fun loadFullTmxSection() = TiledLevelLoader.load(
        FileInputStream(
            listOf(
                File("src/main/assets/levels/crucible_of_ruin.tmx"),
                File("app/src/main/assets/levels/crucible_of_ruin.tmx")
            ).firstOrNull { it.isFile }
                ?: throw IllegalStateException("crucible_of_ruin.tmx not found under assets/levels")
        )
    )

    @Test
    fun fullWorldTmxMatchesTheFactoryPlatformForPlatform() {
        val section = loadFullTmxSection()
        val layout = GameLevelFactory.create()
        assertEquals(layout.platforms, section.platforms)
    }

    @Test
    fun fullWorldTmxMatchesTheFactoryHazardForHazard() {
        val section = loadFullTmxSection()
        val layout = GameLevelFactory.create()
        assertEquals(layout.hazards, section.hazards)
    }

    @Test
    fun fullWorldTmxMatchesTheFactoryRoomGraph() {
        val section = loadFullTmxSection()
        val layout = GameLevelFactory.create()
        assertEquals(layout.rooms, section.rooms)
    }

    @Test
    fun fullWorldTmxMatchesTheFactoryGateTriggers() {
        val section = loadFullTmxSection()
        val layout = GameLevelFactory.create()
        assertEquals(layout.gateTriggers, section.gateTriggers)
    }

    @Test
    fun fullWorldTmxMatchesTheFactoryWorldBounds() {
        val section = loadFullTmxSection()
        val layout = GameLevelFactory.create()
        assertEquals(layout.width, section.width, 0f)
        assertEquals(layout.height, section.height, 0f)
    }

    @Test
    fun fullWorldTmxMatchesTheFactoryUrnsUrnForUrn() {
        val section = loadFullTmxSection()
        val layout = GameLevelFactory.create()
        assertEquals(layout.breakables.size, section.breakables.size)
        for (index in layout.breakables.indices) {
            val factoryUrn = layout.breakables[index]
            val tmxUrn = section.breakables[index]
            assertEquals("urn x at " + index, factoryUrn.x, tmxUrn.x, 0f)
            assertEquals("urn y at " + index, factoryUrn.y, tmxUrn.y, 0f)
            assertEquals("urn w at " + index, factoryUrn.w, tmxUrn.w, 0f)
            assertEquals("urn h at " + index, factoryUrn.h, tmxUrn.h, 0f)
            assertEquals("urn drop at " + index, factoryUrn.dropType, tmxUrn.dropType)
        }
    }

    @Test
    fun fullWorldTmxMatchesTheFactoryEnemiesEnemyForEnemy() {
        val section = loadFullTmxSection()
        val layout = GameLevelFactory.create()
        assertEquals(layout.enemies.size, section.enemies.size)
        for (index in layout.enemies.indices) {
            val factoryEnemy = layout.enemies[index]
            val tmxEnemy = section.enemies[index]
            assertEquals("enemy x at " + index, factoryEnemy.x, tmxEnemy.x, 0f)
            assertEquals("enemy y at " + index, factoryEnemy.y, tmxEnemy.y, 0f)
            assertEquals("enemy type at " + index, factoryEnemy.type, tmxEnemy.type)
            assertEquals("enemy patrol at " + index, factoryEnemy.patrolRange, tmxEnemy.patrolRange, 0f)
        }
    }

    @Test
    fun fullWorldTmxMatchesTheFactoryEncounterChain() {
        val section = loadFullTmxSection()
        val layout = GameLevelFactory.create()
        assertEquals("siege_golem", section.bossEncounters.first().bossId)
        assertEquals(layout.bossTriggerX, section.bossEncounters.first().triggerX, 0f)
        assertEquals(layout.bossTriggerMaxY, section.bossEncounters.first().triggerMaxY, 0f)
        assertEquals(layout.laterEncounters.size, section.bossEncounters.size - 1)
        for (index in layout.laterEncounters.indices) {
            assertEquals(
                layout.laterEncounters[index].triggerX,
                section.bossEncounters[index + 1].triggerX,
                0f
            )
            assertEquals(
                layout.laterEncounters[index].triggerMaxY,
                section.bossEncounters[index + 1].triggerMaxY,
                0f
            )
        }
    }
}
