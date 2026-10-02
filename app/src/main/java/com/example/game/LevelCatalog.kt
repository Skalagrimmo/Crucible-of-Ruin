package com.example.game

/**
 * Canonical data description of the original playable level.
 * Keep this equivalent to the pre-data-driven GameEngine layout until migration is complete.
 */
object LevelCatalog {
    val level01 = LevelDef(
        id = "level_01",
        name = "Crucible of Ruin",
        width = 6200f,
        height = 540f,
        platforms = listOf(
            PlatformDef(0f, 460f, 600f, 80f, PlatformStyle.STONE),
            PlatformDef(680f, 460f, 500f, 80f, PlatformStyle.STONE),
            PlatformDef(240f, 390f, 90f, 20f, PlatformStyle.STONE_LEDGE),
            PlatformDef(360f, 320f, 100f, 20f, PlatformStyle.STONE_LEDGE),
            PlatformDef(500f, 250f, 110f, 20f, PlatformStyle.STONE_LEDGE),
            PlatformDef(800f, 380f, 120f, 20f, PlatformStyle.IRON_GIRDER),
            PlatformDef(1000f, 310f, 130f, 20f, PlatformStyle.IRON_GIRDER),
            PlatformDef(1200f, 420f, 350f, 120f, PlatformStyle.STONE),
            PlatformDef(1600f, 400f, 320f, 140f, PlatformStyle.STONE),
            PlatformDef(1920f, 420f, 380f, 120f, PlatformStyle.STONE),
            PlatformDef(2360f, 460f, 120f, 80f, PlatformStyle.STONE),
            PlatformDef(2540f, 400f, 130f, 20f, PlatformStyle.IRON_GIRDER),
            PlatformDef(2730f, 340f, 140f, 20f, PlatformStyle.IRON_GIRDER),
            PlatformDef(2940f, 260f, 160f, 280f, PlatformStyle.STONE),
            PlatformDef(3100f, 430f, 650f, 110f, PlatformStyle.STONE),
            PlatformDef(3250f, 320f, 140f, 20f, PlatformStyle.IRON_GIRDER),
            PlatformDef(3440f, 240f, 160f, 20f, PlatformStyle.IRON_GIRDER),
            PlatformDef(3800f, 440f, 200f, 100f, PlatformStyle.STONE),
            PlatformDef(4080f, 420f, 240f, 120f, PlatformStyle.STONE),
            PlatformDef(4360f, 420f, 320f, 120f, PlatformStyle.STONE),
            PlatformDef(4740f, 360f, 110f, 20f, PlatformStyle.STONE_LEDGE),
            PlatformDef(4900f, 300f, 120f, 20f, PlatformStyle.STONE_LEDGE),
            PlatformDef(5070f, 430f, 200f, 110f, PlatformStyle.STONE),
            PlatformDef(5270f, 450f, 930f, 90f, PlatformStyle.STONE),
            PlatformDef(5270f, 100f, 30f, 350f, PlatformStyle.BOSS_GATE),
            PlatformDef(6170f, 100f, 30f, 350f, PlatformStyle.BOSS_GATE),
            PlatformDef(5420f, 330f, 120f, 18f, PlatformStyle.IRON_GIRDER),
            PlatformDef(5880f, 330f, 120f, 18f, PlatformStyle.IRON_GIRDER)
        ),
        hazards = listOf(
            HazardDef(600f, 500f, 80f, 40f, HazardType.ACID),
            HazardDef(1550f, 510f, 50f, 30f, HazardType.SPIKES),
            HazardDef(2300f, 500f, 60f, 40f, HazardType.ACID),
            HazardDef(2480f, 510f, 350f, 30f, HazardType.ACID),
            HazardDef(4000f, 500f, 80f, 40f, HazardType.ACID),
            HazardDef(4680f, 510f, 260f, 30f, HazardType.ACID)
        ),
        enemies = listOf(
            EnemyDef(420f, 410f, EnemyArchetype.LURKER, 120f),
            EnemyDef(920f, 410f, EnemyArchetype.LURKER, 140f),
            EnemyDef(520f, 180f, EnemyArchetype.CHERUB, 110f),
            EnemyDef(1100f, 240f, EnemyArchetype.CHERUB, 120f),
            EnemyDef(1420f, 370f, EnemyArchetype.LURKER, 100f),
            EnemyDef(3000f, 210f, EnemyArchetype.CULTIST, 0f),
            EnemyDef(3300f, 370f, EnemyArchetype.GOLEM, 150f),
            EnemyDef(3560f, 380f, EnemyArchetype.LURKER, 90f),
            EnemyDef(3480f, 190f, EnemyArchetype.CHERUB, 100f),
            EnemyDef(4140f, 360f, EnemyArchetype.CULTIST, 0f),
            EnemyDef(4820f, 220f, EnemyArchetype.CHERUB, 130f),
            EnemyDef(5120f, 370f, EnemyArchetype.GOLEM, 70f)
        ),
        breakables = listOf(
            BreakableDef(380f, 296f, "health"),
            BreakableDef(1240f, 396f, "flask"),
            BreakableDef(3500f, 216f, "flask"),
            BreakableDef(5180f, 406f, "health")
        ),
        checkpoints = listOf(
            CheckpointDef(0, 80f, 380f, "Section 1: Ruined Bastion"),
            CheckpointDef(1, 1980f, 340f, "Section 2: Crucible Laboratories"),
            CheckpointDef(2, 4400f, 340f, "Section 3: Sanctum Approach")
        ),
        boss = BossDef(5750f, 360f)
    )
}
