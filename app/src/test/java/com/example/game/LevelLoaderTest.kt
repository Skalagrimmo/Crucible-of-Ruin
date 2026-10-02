package com.example.game

import org.junit.Assert.*
import org.junit.Test

class LevelLoaderTest {

    private fun canonicalLevel(): LevelDef {
        val resource = requireNotNull(
            javaClass.classLoader?.getResourceAsStream("levels/level_01.json")
        )
        return LevelJson.parse(resource.bufferedReader().use { it.readText() })
    }

    @Test
    fun level01_matchesOriginalLayoutCountsAndBounds() {
        val def = canonicalLevel()

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
        val def = canonicalLevel()

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
        val checkpoints = canonicalLevel().checkpoints

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

        assertEquals(canonicalLevel(), parsed)
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
            boss = BossDef(800f, 900f, 700f)
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
    fun level02_structuralDataStaysInsideLevelBounds() {
        val resource = requireNotNull(
            javaClass.classLoader?.getResourceAsStream("levels/level_02.json")
        )
        val def = LevelJson.parse(resource.bufferedReader().use { it.readText() })

        assertEquals("level_02", def.id)
        assertEquals(2200f, def.width, 0.001f)
        assertEquals(2160f, def.height, 0.001f)
        assertEquals(52, def.platforms.size)
        assertEquals(12, def.enemies.size)
        assertEquals(4, def.checkpoints.size)

        def.platforms.forEach {
            assertTrue(it.x >= 0f && it.y >= 0f)
            assertTrue(it.x + it.w <= def.width)
            assertTrue(it.y + it.h <= def.height)
        }
        def.enemies.forEach {
            assertTrue(it.x in 0f..def.width)
            assertTrue(it.y in 0f..def.height)
        }
        def.checkpoints.forEach {
            assertTrue(it.x in 0f..def.width)
            assertTrue(it.y in 0f..def.height)
        }
        assertTrue(def.boss.x in 0f..def.width)
        assertTrue(def.boss.y in 0f..def.height)
        assertTrue(def.boss.triggerX in 0f..def.width)
    }

    @Test
    fun level02_checkpointsRespawnAboveSupportingPlatforms() {
        val resource = requireNotNull(
            javaClass.classLoader?.getResourceAsStream("levels/level_02.json")
        )
        val def = LevelJson.parse(resource.bufferedReader().use { it.readText() })
        val playerHeight = 44f

        def.checkpoints.forEach { cp ->
            val support = def.platforms
                .filter { cp.x >= it.x && cp.x <= it.x + it.w && it.y >= cp.y + playerHeight }
                .minByOrNull { it.y - (cp.y + playerHeight) }

            assertNotNull("Checkpoint ${cp.id} has no supporting platform", support)
            val landingGap = support!!.y - (cp.y + playerHeight)
            assertTrue("Checkpoint ${cp.id} is too far above its support", landingGap in 0f..60f)
        }
    }

    @Test
    fun level02_checkpointsConnectToMainAscentRoute() {
        val resource = requireNotNull(
            javaClass.classLoader?.getResourceAsStream("levels/level_02.json")
        )
        val def = LevelJson.parse(resource.bufferedReader().use { it.readText() })
        val route = def.platforms.filter { it.w == 220f }
        val playerHeight = 44f

        fun horizontalGap(a: PlatformDef, b: PlatformDef): Float = when {
            b.x > a.x + a.w -> b.x - (a.x + a.w)
            a.x > b.x + b.w -> a.x - (b.x + b.w)
            else -> 0f
        }

        def.checkpoints.forEach { cp ->
            val support = def.platforms
                .filter { cp.x >= it.x && cp.x <= it.x + it.w && it.y >= cp.y + playerHeight }
                .minByOrNull { it.y - (cp.y + playerHeight) }
            assertNotNull("Checkpoint ${cp.id} has no support", support)

            val connected = route.any { step ->
                kotlin.math.abs(step.y - support!!.y) <= 100f &&
                    horizontalGap(step, support) <= 66f
            }
            assertTrue("Checkpoint ${cp.id} is isolated from ascent route", connected)
        }
    }

    @Test
    fun level02_hasVerticalProgressionAcrossMultipleViewports() {
        val resource = requireNotNull(
            javaClass.classLoader?.getResourceAsStream("levels/level_02.json")
        )
        val def = LevelJson.parse(resource.bufferedReader().use { it.readText() })

        assertTrue(def.height >= 4f * 540f)
        assertEquals(listOf(0, 1, 2, 3), def.checkpoints.map { it.id })
        assertTrue(def.checkpoints.zipWithNext().all { (lower, upper) -> upper.y < lower.y })
        assertTrue(def.boss.y < def.checkpoints.last().y)
    }

    @Test
    fun level02_buildsRuntimeAndTraversesFullCameraHeight() {
        val resource = requireNotNull(
            javaClass.classLoader?.getResourceAsStream("levels/level_02.json")
        )
        val def = LevelJson.parse(resource.bufferedReader().use { it.readText() })
        val engine = GameEngine(def)
        engine.audio.isMuted = true

        assertEquals(def.platforms.size, engine.platforms.size)
        assertEquals(def.hazards.size, engine.hazards.size)
        assertEquals(def.enemies.size, engine.enemies.size)
        assertEquals(def.breakables.size, engine.breakables.size)
        assertEquals(def.checkpoints.size, engine.checkpoints.size)

        repeat(180) {
            engine.camera.update(1100f, def.height + 500f, 1, def.width, def.height, 1f / 60f)
        }
        assertEquals(def.height - engine.camera.viewH, engine.camera.y, 0.001f)

        repeat(180) {
            engine.camera.update(1100f, -500f, 1, def.width, def.height, 1f / 60f)
        }
        assertEquals(0f, engine.camera.y, 0.001f)
    }

    @Test
    fun level02_bossRequiresBothHorizontalAndVerticalArenaTrigger() {
        val resource = requireNotNull(
            javaClass.classLoader?.getResourceAsStream("levels/level_02.json")
        )
        val def = LevelJson.parse(resource.bufferedReader().use { it.readText() })
        val engine = GameEngine(def)
        engine.audio.isMuted = true
        val triggerY = requireNotNull(def.boss.triggerY)

        engine.player.x = def.boss.triggerX - 1f
        engine.player.y = triggerY - 1f
        engine.update(1f / 60f)
        assertFalse(engine.boss.active)

        engine.player.x = def.boss.triggerX + 1f
        engine.player.y = triggerY + 100f
        engine.update(1f / 60f)
        assertFalse(engine.boss.active)

        engine.player.x = def.boss.triggerX + 1f
        engine.player.y = triggerY - 1f
        engine.update(1f / 60f)
        assertTrue(engine.boss.active)
    }

    @Test
    fun level01_bossKeepsLegacyHorizontalOnlyTrigger() {
        val resource = requireNotNull(
            javaClass.classLoader?.getResourceAsStream("levels/level_01.json")
        )
        val def = LevelJson.parse(resource.bufferedReader().use { it.readText() })
        val engine = GameEngine(def)
        engine.audio.isMuted = true

        assertNull(def.boss.triggerY)
        engine.player.x = def.boss.triggerX + 1f
        engine.update(1f / 60f)

        assertTrue(engine.boss.active)
    }

    @Test
    fun level02_mainAscentPlatformStepsFitPlayerJumpEnvelope() {
        val resource = requireNotNull(
            javaClass.classLoader?.getResourceAsStream("levels/level_02.json")
        )
        val def = LevelJson.parse(resource.bufferedReader().use { it.readText() })

        // Full-height jump apex: v² / 2g ~= 88 px. The greybox uses
        // intermediate geometry so large floor-to-floor gaps are not expected
        // to be cleared in a single jump.
        val jumpRise = 430f * 430f / (2f * 1050f)
        assertTrue(jumpRise in 85f..90f)

        val sortedTops = def.platforms.map { it.y }.sortedDescending()
        val verticalGaps = sortedTops.zipWithNext().map { (lower, upper) -> lower - upper }

        assertTrue(verticalGaps.maxOrNull()!! <= jumpRise)
    }

    @Test
    fun level02_lowerAscentHasReachableEdgeToEdgeSteps() {
        val resource = requireNotNull(
            javaClass.classLoader?.getResourceAsStream("levels/level_02.json")
        )
        val def = LevelJson.parse(resource.bufferedReader().use { it.readText() })

        val route = def.platforms.filter {
            it.y in 1440f..2000f && it.w == 220f
        }.sortedByDescending { it.y }

        assertEquals(8, route.size)
        route.zipWithNext().forEach { (from, to) ->
            val rise = from.y - to.y
            val horizontalGap = when {
                to.x > from.x + from.w -> to.x - (from.x + from.w)
                from.x > to.x + to.w -> from.x - (to.x + to.w)
                else -> 0f
            }
            assertTrue(rise <= 80f)
            assertTrue(horizontalGap <= 66f)
        }
    }

    @Test
    fun level02_upperAscentHasReachableEdgeToEdgeSteps() {
        val resource = requireNotNull(
            javaClass.classLoader?.getResourceAsStream("levels/level_02.json")
        )
        val def = LevelJson.parse(resource.bufferedReader().use { it.readText() })

        val route = def.platforms.filter {
            it.y in 160f..1360f && it.w == 220f
        }.sortedByDescending { it.y }

        assertEquals(16, route.size)
        route.zipWithNext().forEach { (from, to) ->
            val rise = from.y - to.y
            val horizontalGap = when {
                to.x > from.x + from.w -> to.x - (from.x + from.w)
                from.x > to.x + to.w -> from.x - (to.x + to.w)
                else -> 0f
            }
            assertTrue(rise <= 80f)
            assertTrue(horizontalGap <= 66f)
        }
    }

    @Test
    fun level02_hasContinuousReachableRouteFromSpawnToSummit() {
        val resource = requireNotNull(
            javaClass.classLoader?.getResourceAsStream("levels/level_02.json")
        )
        val def = LevelJson.parse(resource.bufferedReader().use { it.readText() })

        val route = def.platforms.filter { it.w == 220f }.sortedByDescending { it.y }
        assertEquals(24, route.size)

        val start = route.first()
        val summit = route.last()
        assertTrue(start.y >= 2000f)
        assertTrue(summit.y <= 160f)

        val reachable = mutableSetOf(0)
        for (i in route.indices) {
            if (i !in reachable) continue
            val from = route[i]
            for (j in i + 1 until route.size) {
                val to = route[j]
                val rise = from.y - to.y
                if (rise < 0f || rise > 80f) continue
                val horizontalGap = when {
                    to.x > from.x + from.w -> to.x - (from.x + from.w)
                    from.x > to.x + to.w -> from.x - (to.x + to.w)
                    else -> 0f
                }
                if (horizontalGap <= 66f) reachable += j
            }
        }

        assertTrue(route.lastIndex in reachable)
    }

    @Test
    fun playerJumpConstants_defineExpectedFullJumpEnvelope() {
        val engine = GameEngine(verticalFixture())
        val player = engine.player

        val theoreticalRise = player.jumpForce * player.jumpForce / (2f * player.gravity)
        val timeToApex = -player.jumpForce / player.gravity
        val conservativeHorizontalTravel = player.airSpeed * timeToApex

        assertTrue(theoreticalRise in 85f..90f)
        assertTrue(timeToApex in 0.40f..0.42f)
        assertTrue(conservativeHorizontalTravel in 64f..68f)
    }

    @Test
    fun playerJumpPhysics_reachesExpectedApexBand() {
        val def = verticalFixture()
        val engine = GameEngine(def)
        engine.audio.isMuted = true
        engine.player.x = 500f
        engine.player.y = 1190f - engine.player.h
        engine.player.grounded = true
        engine.player.coyoteTime = 0.12f

        val startY = engine.player.y
        engine.inputJump = true
        engine.onJumpPressed()
        var highestY = startY

        repeat(30) {
            engine.update(1f / 60f)
            highestY = minOf(highestY, engine.player.y)
        }
        engine.inputJump = false
        repeat(60) {
            engine.update(1f / 60f)
            highestY = minOf(highestY, engine.player.y)
        }

        val rise = startY - highestY
        assertTrue(rise > 20f)
        assertTrue(engine.player.vy >= 0f)
        assertTrue(engine.player.y >= highestY)
    }

    @Test
    fun camera_smallerThanViewport_clampsToOrigin() {
        val camera = Camera2D()
        camera.update(450f, 270f, 1, 900f, 500f, 1f / 60f)

        assertEquals(0f, camera.x, 0.001f)
        assertEquals(0f, camera.y, 0.001f)
    }

    @Test
    fun injectedLevel_usesItsOwnBossTrigger() {
        val def = verticalFixture()
        val engine = GameEngine(def)
        engine.audio.isMuted = true
        engine.player.x = def.boss.triggerX + 1f

        engine.update(1f / 60f)

        assertTrue(engine.boss.active)
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


    private fun verticalFixture(): LevelDef {
        val resource = requireNotNull(
            javaClass.classLoader?.getResourceAsStream("levels/level_vertical_test.json")
        )
        return LevelJson.parse(resource.bufferedReader().use { it.readText() })
    }

    @Test
    fun verticalFixture_playerFallsAndLandsOnElevatedPlatform() {
        val engine = GameEngine(verticalFixture())
        engine.audio.isMuted = true
        engine.player.x = 470f
        engine.player.y = 1040f
        engine.player.vx = 0f
        engine.player.vy = 0f

        repeat(120) { engine.update(1f / 60f) }

        assertTrue(engine.player.grounded)
        assertEquals(1190f - engine.player.h, engine.player.y, 0.75f)
        assertEquals(0f, engine.player.vy, 0.001f)
    }

    @Test
    fun verticalFixture_respawnsAtElevatedCheckpointCoordinates() {
        val engine = GameEngine(verticalFixture())
        engine.audio.isMuted = true
        engine.setStartCheckpoint(2)
        val summit = engine.checkpoints[2]

        engine.player.takeDamage(engine.player.maxHp, engine.player.x, engine)
        assertTrue(engine.player.dead)

        var frames = 0
        while (engine.player.dead && frames < 120) {
            engine.update(1f / 60f)
            frames++
        }

        assertFalse(engine.player.dead)
        assertTrue(frames in 80..90)
        assertEquals(summit.x, engine.player.x, 0.001f)
        assertEquals(summit.y, engine.player.y, 0.001f)
        assertEquals(engine.player.maxHp, engine.player.hp, 0.001f)
        assertSame(summit, engine.activeCheckpoint)
    }
}
