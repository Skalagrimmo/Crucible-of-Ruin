package com.example.game

import org.junit.Assert.*
import org.junit.Test

class GameEngineRegressionTest {

    private fun canonicalLevel(): LevelDef {
        val resource = requireNotNull(
            javaClass.classLoader?.getResourceAsStream("levels/level_01.json")
        )
        return LevelJson.parse(resource.bufferedReader().use { it.readText() })
    }

    private fun freshEngine(): GameEngine = GameEngine(canonicalLevel()).also {
        it.audio.isMuted = true
    }

    @Test
    fun levelBaseline_isStable() {
        val engine = freshEngine()

        assertEquals(6200f, engine.levelWidth, 0.001f)
        assertEquals(540f, engine.levelHeight, 0.001f)
        assertEquals(28, engine.platforms.size)
        assertEquals(6, engine.hazards.size)
        assertEquals(12, engine.enemies.size)
        assertEquals(4, engine.breakables.size)
        assertEquals(3, engine.checkpoints.size)
        assertEquals(0, engine.activeCheckpoint.id)
        assertEquals(5750f, engine.boss.x, 0.001f)
        assertEquals(360f, engine.boss.y, 0.001f)
    }

    @Test
    fun startCheckpoint_activatesAllPreviousCheckpoints() {
        val engine = freshEngine()

        engine.setStartCheckpoint(2)

        assertEquals(2, engine.activeCheckpoint.id)
        assertTrue(engine.checkpoints[0].activated)
        assertTrue(engine.checkpoints[1].activated)
        assertTrue(engine.checkpoints[2].activated)
        assertEquals(engine.activeCheckpoint.x, engine.player.x, 0.001f)
        assertEquals(engine.activeCheckpoint.y, engine.player.y, 0.001f)
    }

    @Test
    fun level02_eachCheckpointSurvivesDeathAndRespawnsAtSelectedStage() {
        val resource = requireNotNull(
            javaClass.classLoader?.getResourceAsStream("levels/level_02.json")
        )
        val def = LevelJson.parse(resource.bufferedReader().use { it.readText() })

        def.checkpoints.indices.forEach { checkpointIndex ->
            val engine = GameEngine(def)
            engine.audio.isMuted = true
            engine.setStartCheckpoint(checkpointIndex)
            val expected = def.checkpoints[checkpointIndex]

            assertEquals(expected.id, engine.activeCheckpoint.id)
            assertEquals(expected.x, engine.player.x, 0.001f)
            assertEquals(expected.y, engine.player.y, 0.001f)

            engine.player.takeDamage(engine.player.maxHp, engine.player.x, engine)
            assertTrue(engine.player.dead)

            repeat(240) {
                if (engine.player.dead) engine.update(1f / 60f)
            }

            assertFalse("Checkpoint ${expected.id} did not respawn", engine.player.dead)
            assertEquals(expected.id, engine.activeCheckpoint.id)
            assertEquals(expected.x, engine.player.x, 0.001f)
            assertEquals(expected.y, engine.player.y, 0.001f)
        }
    }

    @Test
    fun level02_verticalRespawnSnapsCameraNearCheckpoint() {
        val resource = requireNotNull(
            javaClass.classLoader?.getResourceAsStream("levels/level_02.json")
        )
        val def = LevelJson.parse(resource.bufferedReader().use { it.readText() })
        val engine = GameEngine(def)
        engine.audio.isMuted = true
        engine.setStartCheckpoint(2)
        val checkpoint = engine.activeCheckpoint

        engine.camera.y = 0f
        engine.player.takeDamage(engine.player.maxHp, engine.player.x, engine)
        repeat(240) {
            if (engine.player.dead) engine.update(1f / 60f)
        }

        val expectedY = (checkpoint.y - engine.camera.viewH / 2f - 20f)
            .coerceIn(0f, (engine.levelHeight - engine.camera.viewH).coerceAtLeast(0f))
        assertFalse(engine.player.dead)
        assertEquals(expectedY, engine.camera.y, 0.001f)
    }

    @Test
    fun oneWayPlatform_allowsPlayerToPassUpThroughItsUnderside() {
        val def = canonicalLevel().copy(
            platforms = listOf(PlatformDef(300f, 300f, 220f, 20f, PlatformStyle.STONE_LEDGE, oneWay = true)),
            hazards = emptyList(),
            enemies = emptyList(),
            breakables = emptyList(),
            checkpoints = listOf(CheckpointDef(0, 360f, 420f, "Below")),
            boss = BossDef(900f, 0f, 1000f)
        )
        val engine = GameEngine(def)
        engine.audio.isMuted = true
        engine.player.x = 360f
        engine.player.y = 350f
        engine.player.vy = -430f
        engine.player.grounded = false

        var crossedAboveTop = false
        repeat(30) {
            engine.update(1f / 60f)
            if (engine.player.y + engine.player.h < 300f) crossedAboveTop = true
        }

        assertTrue("Player was blocked by one-way platform underside", crossedAboveTop)
    }

    @Test
    fun oneWayPlatform_catchesPlayerWhenFallingFromAbove() {
        val def = canonicalLevel().copy(
            platforms = listOf(PlatformDef(300f, 300f, 220f, 20f, PlatformStyle.STONE_LEDGE, oneWay = true)),
            hazards = emptyList(),
            enemies = emptyList(),
            breakables = emptyList(),
            checkpoints = listOf(CheckpointDef(0, 360f, 180f, "Above")),
            boss = BossDef(900f, 0f, 1000f)
        )
        val engine = GameEngine(def)
        engine.audio.isMuted = true
        engine.player.x = 360f
        engine.player.y = 200f
        engine.player.vy = 120f
        engine.player.grounded = false

        repeat(60) { engine.update(1f / 60f) }

        assertTrue(engine.player.grounded)
        assertEquals(300f - engine.player.h, engine.player.y, 0.001f)
        assertEquals(0f, engine.player.vy, 0.001f)
    }

    @Test
    fun groundEnemy_landsOnOneWayPlatformFromAbove() {
        val def = canonicalLevel().copy(
            platforms = listOf(PlatformDef(300f, 300f, 220f, 20f, PlatformStyle.STONE_LEDGE, oneWay = true)),
            hazards = emptyList(),
            enemies = listOf(EnemyDef(360f, 200f, EnemyArchetype.GOLEM, 0f)),
            breakables = emptyList(),
            checkpoints = listOf(CheckpointDef(0, 50f, 50f, "Safe")),
            boss = BossDef(900f, 0f, 1000f)
        )
        val engine = GameEngine(def)
        engine.audio.isMuted = true
        val enemy = engine.enemies.single()
        enemy.vy = 120f

        repeat(60) { engine.update(1f / 60f) }

        assertTrue(enemy.grounded)
        assertEquals(300f - enemy.h, enemy.y, 0.001f)
        assertEquals(0f, enemy.vy, 0.001f)
    }

    @Test
    fun jumpPress_setsJumpBuffer() {
        val engine = freshEngine()

        engine.player.jumpBuffer = 0f
        engine.onJumpPressed()

        assertEquals(0.15f, engine.player.jumpBuffer, 0.001f)
    }

    @Test
    fun attackPress_startsAttackAndAdvancesAttackId() {
        val engine = freshEngine()
        val previousId = engine.player.attackId

        engine.onAttackPressed()

        assertTrue(engine.player.attacking)
        assertEquals(engine.player.attackDuration, engine.player.attackTimer, 0.001f)
        assertTrue(engine.player.attackId > previousId)
    }

    @Test
    fun subweapon_consumesOneFlaskAndStartsCooldown() {
        val engine = freshEngine()
        val before = engine.player.flasks

        engine.onSubPressed()

        assertEquals(before - 1, engine.player.flasks)
        assertEquals(0.45f, engine.player.subCooldown, 0.001f)
    }

    @Test
    fun invulnerability_preventsRepeatedDamage() {
        val engine = freshEngine()
        val player = engine.player

        player.takeDamage(25f, player.x + 100f, engine)
        val hpAfterFirstHit = player.hp

        player.takeDamage(25f, player.x + 100f, engine)

        assertEquals(75f, hpAfterFirstHit, 0.001f)
        assertEquals(hpAfterFirstHit, player.hp, 0.001f)
        assertTrue(player.invulnTimer > 0f)
    }

    @Test
    fun lethalDamage_marksPlayerDead() {
        val engine = freshEngine()
        val player = engine.player

        player.takeDamage(player.maxHp, player.x, engine)

        assertTrue(player.dead)
        assertEquals(0f, player.hp, 0.001f)
        assertTrue(player.respawnTimer > 0f)
    }

    @Test
    fun deadPlayer_cannotActivateNearbyCheckpointDuringRespawnCountdown() {
        val engine = freshEngine()
        val next = engine.checkpoints[1]
        engine.player.x = next.x
        engine.player.y = next.y
        engine.player.takeDamage(engine.player.maxHp, engine.player.x, engine)

        engine.update(1f / 60f)

        assertTrue(engine.player.dead)
        assertFalse(next.activated)
        assertEquals(0, engine.activeCheckpoint.id)
    }

    @Test
    fun deadPlayer_freezesBossCombatUntilRespawn() {
        val engine = freshEngine()
        val boss = engine.boss
        boss.active = true
        boss.state = "chase"
        engine.player.takeDamage(engine.player.maxHp, engine.player.x, engine)

        val bossX = boss.x
        val bossY = boss.y
        val projectileCount = engine.projectiles.activeCount

        repeat(30) { engine.update(1f / 60f) }

        assertTrue(engine.player.dead)
        assertEquals(bossX, boss.x, 0.001f)
        assertEquals(bossY, boss.y, 0.001f)
        assertEquals(projectileCount, engine.projectiles.activeCount)
    }

    @Test
    fun bossCrossingHalfHealth_entersPhaseTwo() {
        val engine = freshEngine()
        val boss = engine.boss
        boss.active = true

        boss.takeDamage(180f, 1001L, engine.player.x, engine)

        assertTrue(boss.phase2)
        assertFalse(boss.dead)
    }

    @Test
    fun lethalBossDamage_setsVictoryState() {
        val engine = freshEngine()
        val boss = engine.boss
        boss.active = true

        boss.takeDamage(boss.maxHp, 2001L, engine.player.x, engine)

        assertTrue(boss.dead)
        assertEquals("victory", engine.state)
        assertEquals(0f, boss.hp, 0.001f)
    }

    @Test
    fun restart_restoresPlayingStateAndRunCounters() {
        val engine = freshEngine()
        engine.state = "victory"
        engine.timeElapsed = 42f
        engine.enemiesDefeated = 7

        engine.restart()

        assertEquals("playing", engine.state)
        assertEquals(0f, engine.timeElapsed, 0.001f)
        assertEquals(0, engine.enemiesDefeated)
        assertFalse(engine.player.dead)
    }
}
