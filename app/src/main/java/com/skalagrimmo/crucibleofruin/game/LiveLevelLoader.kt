package com.skalagrimmo.crucibleofruin.game

import android.content.res.AssetManager
import com.skalagrimmo.crucibleofruin.game.boss.BaseBoss
import com.skalagrimmo.crucibleofruin.game.boss.CrucibleChimeraBoss
import com.skalagrimmo.crucibleofruin.game.boss.SiegeGolemBoss
import java.io.InputStream

/**
 * Stage 10d (#69): the live level source. Loads the full-world TMX asset
 * through [TiledLevelLoader] and resolves its boss encounter chain into the
 * [GameLevelLayout] the engine installs. Boss archetypes resolve through a
 * closed factory map — the loader never reflects over classes, and an
 * unknown bossId fails fast instead of spawning nothing.
 */
internal object LiveLevelLoader {

    /** The live world asset inside app/src/main/assets. */
    const val LEVEL_ASSET_PATH = "levels/crucible_of_ruin.tmx"

    // Closed factory map: bossId -> (spawnX, spawnY) -> a fresh boss instance.
    private val bossFactories: Map<String, (Float, Float) -> BaseBoss> = mapOf(
        "siege_golem" to { x, y -> SiegeGolemBoss(x, y) },
        "chimera" to { x, y -> CrucibleChimeraBoss(x, y) }
    )

    /** Loads the live level from the APK assets (needs a Context's AssetManager). */
    fun loadFromAssets(assets: AssetManager): GameLevelLayout =
        load(assets.open(LEVEL_ASSET_PATH))

    /** Loads the live level from any TMX stream — unit tests pass a FileInputStream. */
    fun load(input: InputStream): GameLevelLayout = toLayout(TiledLevelLoader.load(input))

    private fun toLayout(section: TiledLevelSection): GameLevelLayout {
        require(section.bossEncounters.isNotEmpty()) {
            "The live level TMX declares no boss encounters"
        }
        val first = section.bossEncounters.first()
        return GameLevelLayout(
            platforms = section.platforms,
            hazards = section.hazards,
            breakables = section.breakables,
            enemies = section.enemies,
            boss = freshBoss(first.bossId, first.spawnX, first.spawnY),
            bossFactory = { freshBoss(first.bossId, first.spawnX, first.spawnY) },
            bossTriggerX = first.triggerX,
            bossTriggerMaxY = first.triggerMaxY,
            laterEncounters = section.bossEncounters.drop(1).map { encounter ->
                BossEncounter(
                    createBoss = { freshBoss(encounter.bossId, encounter.spawnX, encounter.spawnY) },
                    triggerX = encounter.triggerX,
                    triggerMaxY = encounter.triggerMaxY
                )
            },
            rooms = section.rooms,
            width = section.width,
            height = section.height,
            gateTriggers = section.gateTriggers
        )
    }

    private fun freshBoss(bossId: String, spawnX: Float, spawnY: Float): BaseBoss {
        val factory = bossFactories[bossId]
            ?: throw IllegalArgumentException("Unknown bossId '" + bossId + "' in the live level TMX")
        return factory(spawnX, spawnY)
    }
}
