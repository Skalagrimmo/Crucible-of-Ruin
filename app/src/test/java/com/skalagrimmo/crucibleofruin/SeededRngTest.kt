package com.skalagrimmo.crucibleofruin

import com.skalagrimmo.crucibleofruin.game.EnemyArchetype
import com.skalagrimmo.crucibleofruin.game.EnemyEntity
import com.skalagrimmo.crucibleofruin.game.GameEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Stage 6a (#38): gameplay randomness is seeded. The same seed must reproduce
 * the exact same loot sequence across engine instances; different seeds must
 * diverge. Visual randomness (particles, camera shake) is intentionally not
 * part of the deterministic stream.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SeededRngTest {

    /** Loot types rolled for twenty lurker kills with a fresh engine of the given seed. */
    private fun lootRolls(seed: Long): List<String> {
        val engine = GameEngine(rngSeed = seed)
        engine.platforms.clear()
        engine.hazards.clear()
        engine.enemies.clear()

        repeat(20) { i ->
            engine.enemies.add(EnemyEntity(100f + i * 40f, 100f, EnemyArchetype.LURKER))
        }
        engine.enemies.forEachIndexed { i, e ->
            // 45 damage kills a lurker (hp 45) and rolls its loot drop.
            e.takeDamage(45f, i.toLong() + 1L, 0f, engine)
        }

        return (0 until engine.pickups.activeCount).map { engine.pickups.get(it).type }
    }

    @Test
    fun sameSeedReproducesTheSameLootSequence() {
        assertEquals(
            "Identical seeds must roll identical loot sequences",
            lootRolls(20261103L),
            lootRolls(20261103L)
        )
    }

    @Test
    fun differentSeedsProduceDifferentLootSequences() {
        assertNotEquals(
            "Different seeds should diverge over twenty rolls",
            lootRolls(20261103L),
            lootRolls(31102026L)
        )
    }

    @Test
    fun seedIsExposedForSaveGamesAndReplays() {
        val engine = GameEngine(rngSeed = 42L)
        assertEquals("The seed must be readable for persistence and replay", 42L, engine.rngSeed)
    }
}
