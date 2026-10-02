package com.example.game

import org.junit.Assert.*
import org.junit.Test

class LevelLoaderTest {

    @Test
    fun level01_matchesOriginalLayoutCountsAndBounds() {
        val def = LevelCatalog.level01

        assertEquals(6200f, def.width, 0.001f)
        assertEquals(540f, def.height, 0.001f)
        assertEquals(28, def.platforms.size)
        assertEquals(6, def.hazards.size)
        assertEquals(12, def.enemies.size)
        assertEquals(4, def.breakables.size)
        assertEquals(3, def.checkpoints.size)
        assertEquals(5750f, def.boss.x, 0.001f)
        assertEquals(360f, def.boss.y, 0.001f)
    }

    @Test
    fun loader_createsFreshMutableRuntimeObjects() {
        val def = LevelCatalog.level01

        val firstCheckpoints = LevelLoader.checkpoints(def)
        val secondCheckpoints = LevelLoader.checkpoints(def)
        firstCheckpoints[0].activated = true

        assertTrue(firstCheckpoints[0].activated)
        assertFalse(secondCheckpoints[0].activated)

        val firstEnemies = LevelLoader.enemies(def)
        val secondEnemies = LevelLoader.enemies(def)
        assertNotSame(firstEnemies[0], secondEnemies[0])

        val firstBoss = LevelLoader.boss(def)
        val secondBoss = LevelLoader.boss(def)
        assertNotSame(firstBoss, secondBoss)
    }

    @Test
    fun level01_checkpointIdentityAndOrder_areStable() {
        val checkpoints = LevelCatalog.level01.checkpoints

        assertEquals(listOf(0, 1, 2), checkpoints.map { it.id })
        assertEquals("Section 1: Ruined Bastion", checkpoints[0].name)
        assertEquals("Section 2: Crucible Laboratories", checkpoints[1].name)
        assertEquals("Section 3: Sanctum Approach", checkpoints[2].name)
    }


    @Test
    fun jsonParser_reconstructsCanonicalLevel() {
        val resource = requireNotNull(
            javaClass.classLoader?.getResourceAsStream("levels/level_01.json")
        )
        val json = resource.bufferedReader().use { it.readText() }
        val parsed = LevelJson.parse(json)

        assertEquals(LevelCatalog.level01, parsed)
        assertEquals(28, LevelLoader.platforms(parsed).size)
        assertEquals(12, LevelLoader.enemies(parsed).size)
    }


    @Test
    fun engine_acceptsInjectedLevelDefinition() {
        val tiny = LevelDef(
            id = "test",
            name = "Injected",
            width = 900f,
            height = 1080f,
            platforms = listOf(PlatformDef(0f, 1000f, 900f, 80f, PlatformStyle.STONE)),
            hazards = emptyList(),
            enemies = emptyList(),
            breakables = emptyList(),
            checkpoints = listOf(CheckpointDef(0, 40f, 900f, "Start")),
            boss = BossDef(800f, 900f)
        )

        val engine = GameEngine(tiny)

        assertEquals(900f, engine.levelWidth, 0.001f)
        assertEquals(1080f, engine.levelHeight, 0.001f)
        assertEquals(1, engine.platforms.size)
        assertEquals(0, engine.enemies.size)
        assertEquals(800f, engine.boss.x, 0.001f)
        assertEquals(40f, engine.player.x, 0.001f)
    }


    @Test
    fun verticalFixture_allowsCameraToTravelAndClampAcrossMultipleScreens() {
        val resource = requireNotNull(
            javaClass.classLoader?.getResourceAsStream("levels/level_vertical_test.json")
        )
        val def = LevelJson.parse(resource.bufferedReader().use { it.readText() })
        val engine = GameEngine(def)

        assertEquals(1620f, engine.levelHeight, 0.001f)

        repeat(90) {
            engine.camera.update(600f, 810f, 1, engine.levelWidth, engine.levelHeight, 1f / 60f)
        }
        assertTrue(engine.camera.y > 0f)
        assertTrue(engine.camera.y < engine.levelHeight - engine.camera.viewH)

        repeat(180) {
            engine.camera.update(600f, 2000f, 1, engine.levelWidth, engine.levelHeight, 1f / 60f)
        }
        assertEquals(engine.levelHeight - engine.camera.viewH, engine.camera.y, 0.001f)

        repeat(180) {
            engine.camera.update(600f, -500f, 1, engine.levelWidth, engine.levelHeight, 1f / 60f)
        }
        assertEquals(0f, engine.camera.y, 0.001f)
    }
}
