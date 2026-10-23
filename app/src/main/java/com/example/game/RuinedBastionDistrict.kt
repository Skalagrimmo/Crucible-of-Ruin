package com.example.game

/**
 * First playable district prototype: a continuous street, stacked interiors,
 * connected rooftops, an underpass, and a gate that opens from the far side.
 *
 * Coordinates are in world space. Keep this layout separate from GameEngine so
 * future districts can follow the same pattern without rewriting physics.
 */
object RuinedBastionDistrict {
    val shortcutGate = Platform(
        x = 1650f,
        y = 330f,
        w = 24f,
        h = 130f,
        style = PlatformStyle.BOSS_GATE
    )

    fun populate(
        platforms: MutableList<Platform>,
        breakables: MutableList<BreakableUrn>,
        enemies: MutableList<EnemyEntity>
    ) {
        // Lower street. The first opening drops into the underpass; the second
        // is the stairwell that reconnects below the city block.
        platforms.addAll(
            listOf(
                Platform(0f, 460f, 520f, 80f, PlatformStyle.STONE),
                Platform(620f, 460f, 230f, 80f, PlatformStyle.STONE),
                Platform(1000f, 460f, 140f, 80f, PlatformStyle.STONE),
                Platform(1200f, 460f, 720f, 80f, PlatformStyle.STONE),

                // Underpass floor and its rising return steps.
                Platform(440f, 700f, 400f, 28f, PlatformStyle.STONE),
                Platform(730f, 630f, 90f, 16f, PlatformStyle.CITY_LEDGE),
                Platform(790f, 560f, 90f, 16f, PlatformStyle.CITY_LEDGE),
                Platform(850f, 490f, 130f, 16f, PlatformStyle.CITY_LEDGE),

                // House A: a high roof, two interior ledges, and a climb-in route.
                Platform(260f, 220f, 354f, 22f, PlatformStyle.CITY_LEDGE),
                Platform(260f, 242f, 24f, 168f, PlatformStyle.STONE),
                Platform(590f, 242f, 24f, 168f, PlatformStyle.STONE),
                Platform(160f, 380f, 70f, 16f, PlatformStyle.CITY_LEDGE),
                Platform(220f, 300f, 60f, 16f, PlatformStyle.CITY_LEDGE),
                Platform(295f, 350f, 110f, 16f, PlatformStyle.CITY_LEDGE),
                Platform(435f, 390f, 110f, 16f, PlatformStyle.CITY_LEDGE),

                // House B: upper passage connects to House A's roof.
                Platform(690f, 180f, 304f, 22f, PlatformStyle.CITY_LEDGE),
                Platform(690f, 202f, 24f, 208f, PlatformStyle.STONE),
                Platform(970f, 202f, 24f, 208f, PlatformStyle.STONE),
                Platform(730f, 360f, 80f, 16f, PlatformStyle.CITY_LEDGE),
                Platform(825f, 290f, 95f, 16f, PlatformStyle.CITY_LEDGE),
                Platform(870f, 230f, 70f, 16f, PlatformStyle.CITY_LEDGE),

                // Exterior stair to the third roof. Steps are offset so the
                // player can jump up without hitting their solid undersides.
                Platform(1030f, 390f, 60f, 16f, PlatformStyle.CITY_LEDGE),
                Platform(1100f, 320f, 60f, 16f, PlatformStyle.CITY_LEDGE),
                Platform(1165f, 250f, 30f, 16f, PlatformStyle.CITY_LEDGE),

                // House C and tower. The upper connector passes above the gate.
                Platform(1200f, 220f, 424f, 22f, PlatformStyle.CITY_LEDGE),
                Platform(1200f, 242f, 24f, 168f, PlatformStyle.STONE),
                Platform(1600f, 242f, 24f, 168f, PlatformStyle.STONE),
                Platform(1250f, 360f, 80f, 16f, PlatformStyle.CITY_LEDGE),
                Platform(1350f, 300f, 80f, 16f, PlatformStyle.CITY_LEDGE),
                Platform(1400f, 160f, 70f, 16f, PlatformStyle.CITY_LEDGE),
                Platform(1480f, 110f, 120f, 16f, PlatformStyle.CITY_LEDGE),
                Platform(1510f, 360f, 70f, 16f, PlatformStyle.CITY_LEDGE),
                Platform(1520f, 270f, 220f, 18f, PlatformStyle.CITY_LEDGE),
                Platform(1740f, 350f, 100f, 18f, PlatformStyle.CITY_LEDGE)
            )
        )

        // The gate is a full collision block on the street and is removed once
        // the player reaches the far side by using the upper route.
        platforms.add(shortcutGate)

        breakables.add(BreakableUrn(370f, 326f, dropType = "health"))
        breakables.add(BreakableUrn(1270f, 336f, dropType = "flask"))

        enemies.add(EnemyEntity(400f, 410f, EnemyArchetype.LURKER, 100f))
        enemies.add(EnemyEntity(800f, 410f, EnemyArchetype.LURKER, 90f))
        enemies.add(EnemyEntity(535f, 165f, EnemyArchetype.CHERUB, 110f))
        enemies.add(EnemyEntity(1460f, 150f, EnemyArchetype.CHERUB, 100f))
        enemies.add(EnemyEntity(1420f, 410f, EnemyArchetype.LURKER, 90f))
    }
}
