package com.example.game

import org.junit.Assert.*
import org.junit.Test

class GameEngineRegressionTest {

    private fun freshEngine(): GameEngine = GameEngine().also {
        it.audio.muted = true
    }

    @Test
    fun levelBaseline_isStable() {
        val engine = freshEngine()

        assertEquals(6200f, engine.levelWidth, 0.001f)
        assertEquals(540f, engine.levelHeight, 0.001f)
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
