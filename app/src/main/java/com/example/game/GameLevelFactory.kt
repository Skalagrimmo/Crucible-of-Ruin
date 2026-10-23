package com.example.game

import com.example.game.boss.BaseBoss
import com.example.game.boss.CrucibleChimeraBoss

/**
 * Static level content used to populate the engine's mutable runtime collections.
 *
 * Creating a new layout returns fresh enemies, breakables, and boss state so a restart
 * does not reuse damaged entities from the previous run. Checkpoints remain owned by
 * GameEngine because their activation state is part of progression/runtime state.
 */
internal data class GameLevelLayout(
    val platforms: List<Platform>,
    val hazards: List<Hazard>,
    val breakables: List<BreakableUrn>,
    val enemies: List<EnemyEntity>,
    val boss: BaseBoss
)

/**
 * Builds the static geometry and encounters for the current side-scrolling level.
 * This factory describes the world; GameEngine remains responsible for simulation,
 * collisions, combat, and lifecycle/update order.
 */
internal object GameLevelFactory {
    fun create(): GameLevelLayout {
        val platforms = mutableListOf<Platform>()
        val hazards = mutableListOf<Hazard>()
        val breakables = mutableListOf<BreakableUrn>()
        val enemies = mutableListOf<EnemyEntity>()

        // --- SECTION 1: THE RUINED BASTION (x: 0 - 1900) ---
        platforms.add(Platform(0f, 460f, 600f, 80f, PlatformStyle.STONE))
        platforms.add(Platform(680f, 460f, 500f, 80f, PlatformStyle.STONE))
        hazards.add(Hazard(600f, 500f, 80f, 40f, HazardType.ACID))

        platforms.add(Platform(240f, 390f, 90f, 20f, PlatformStyle.STONE_LEDGE))
        platforms.add(Platform(360f, 320f, 100f, 20f, PlatformStyle.STONE_LEDGE))
        platforms.add(Platform(500f, 250f, 110f, 20f, PlatformStyle.STONE_LEDGE))

        platforms.add(Platform(800f, 380f, 120f, 20f, PlatformStyle.IRON_GIRDER))
        platforms.add(Platform(1000f, 310f, 130f, 20f, PlatformStyle.IRON_GIRDER))
        platforms.add(Platform(1200f, 420f, 350f, 120f, PlatformStyle.STONE))

        platforms.add(Platform(1600f, 400f, 320f, 140f, PlatformStyle.STONE))
        hazards.add(Hazard(1550f, 510f, 50f, 30f, HazardType.SPIKES))

        enemies.add(EnemyEntity(420f, 410f, EnemyArchetype.LURKER, 120f))
        enemies.add(EnemyEntity(920f, 410f, EnemyArchetype.LURKER, 140f))
        enemies.add(EnemyEntity(520f, 180f, EnemyArchetype.CHERUB, 110f))
        enemies.add(EnemyEntity(1100f, 240f, EnemyArchetype.CHERUB, 120f))
        enemies.add(EnemyEntity(1420f, 370f, EnemyArchetype.LURKER, 100f))

        breakables.add(BreakableUrn(380f, 296f, dropType = "health"))
        breakables.add(BreakableUrn(1240f, 396f, dropType = "flask"))

        // --- SECTION 2: THE CRUCIBLE LABORATORIES (x: 1900 - 4300) ---
        platforms.add(Platform(1920f, 420f, 380f, 120f, PlatformStyle.STONE))

        platforms.add(Platform(2360f, 460f, 120f, 80f, PlatformStyle.STONE))
        hazards.add(Hazard(2300f, 500f, 60f, 40f, HazardType.ACID))
        platforms.add(Platform(2540f, 400f, 130f, 20f, PlatformStyle.IRON_GIRDER))
        platforms.add(Platform(2730f, 340f, 140f, 20f, PlatformStyle.IRON_GIRDER))
        hazards.add(Hazard(2480f, 510f, 350f, 30f, HazardType.ACID))

        platforms.add(Platform(2940f, 260f, 160f, 280f, PlatformStyle.STONE))
        enemies.add(EnemyEntity(3000f, 210f, EnemyArchetype.CULTIST, 0f))

        platforms.add(Platform(3100f, 430f, 650f, 110f, PlatformStyle.STONE))
        enemies.add(EnemyEntity(3300f, 370f, EnemyArchetype.GOLEM, 150f))
        enemies.add(EnemyEntity(3560f, 380f, EnemyArchetype.LURKER, 90f))

        platforms.add(Platform(3250f, 320f, 140f, 20f, PlatformStyle.IRON_GIRDER))
        platforms.add(Platform(3440f, 240f, 160f, 20f, PlatformStyle.IRON_GIRDER))
        breakables.add(BreakableUrn(3500f, 216f, dropType = "flask"))
        enemies.add(EnemyEntity(3480f, 190f, EnemyArchetype.CHERUB, 100f))

        platforms.add(Platform(3800f, 440f, 200f, 100f, PlatformStyle.STONE))
        hazards.add(Hazard(4000f, 500f, 80f, 40f, HazardType.ACID))
        platforms.add(Platform(4080f, 420f, 240f, 120f, PlatformStyle.STONE))
        enemies.add(EnemyEntity(4140f, 360f, EnemyArchetype.CULTIST, 0f))

        // --- SECTION 3: THE SANCTUM APPROACH (x: 4300 - 5100) ---
        platforms.add(Platform(4360f, 420f, 320f, 120f, PlatformStyle.STONE))

        platforms.add(Platform(4740f, 360f, 110f, 20f, PlatformStyle.STONE_LEDGE))
        hazards.add(Hazard(4680f, 510f, 260f, 30f, HazardType.ACID))
        platforms.add(Platform(4900f, 300f, 120f, 20f, PlatformStyle.STONE_LEDGE))
        enemies.add(EnemyEntity(4820f, 220f, EnemyArchetype.CHERUB, 130f))

        platforms.add(Platform(5070f, 430f, 200f, 110f, PlatformStyle.STONE))
        enemies.add(EnemyEntity(5120f, 370f, EnemyArchetype.GOLEM, 70f))
        breakables.add(BreakableUrn(5180f, 406f, dropType = "health"))

        // --- BOSS ARENA: GRAND SANCTUM (x: 5270 - 6200) ---
        platforms.add(Platform(5270f, 450f, 930f, 90f, PlatformStyle.STONE))
        platforms.add(Platform(5270f, 100f, 30f, 350f, PlatformStyle.BOSS_GATE))
        platforms.add(Platform(6170f, 100f, 30f, 350f, PlatformStyle.BOSS_GATE))
        platforms.add(Platform(5420f, 330f, 120f, 18f, PlatformStyle.IRON_GIRDER))
        platforms.add(Platform(5880f, 330f, 120f, 18f, PlatformStyle.IRON_GIRDER))

        return GameLevelLayout(
            platforms = platforms,
            hazards = hazards,
            breakables = breakables,
            enemies = enemies,
            boss = CrucibleChimeraBoss(5750f, 360f)
        )
    }
}
