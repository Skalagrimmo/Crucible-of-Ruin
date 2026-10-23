package com.example.game

/**
 * First playable district prototype: a continuous street, stacked interiors,
 * connected rooftops, an underpass, and a gate that opens from the far side.
 *
 * Coordinates are in world space. Keep this layout separate from GameEngine so
 * future districts can follow the same pattern without rewriting physics.
 */
object RuinedBastionDistrict {
    private const val SCALE_X = 1.3f
    private const val SCALE_Y = 1.05f
    private const val THICKNESS_SCALE = 1.15f
    private const val GROUND_Y = 460f
    // City now spans 0..2496 world units; later sections shift by 576 to connect.
    const val HORIZONTAL_OFFSET = 576f
    fun worldX(value: Float): Float = value * SCALE_X
    fun worldY(value: Float): Float = GROUND_Y + (value - GROUND_Y) * SCALE_Y
    fun worldWidth(value: Float): Float = value * SCALE_X
    fun worldHeight(value: Float): Float = value * THICKNESS_SCALE
    private fun cityPlatform(x: Float, y: Float, w: Float, h: Float, style: PlatformStyle): Platform =
        Platform(worldX(x), worldY(y), worldWidth(w), worldHeight(h), style)
    val shortcutUnlockMinX: Float = worldX(1760f)
    val shortcutUnlockMaxX: Float = worldX(1900f)
    val shortcutGate = cityPlatform(1650f, 330f, 24f, 130f, PlatformStyle.BOSS_GATE)

    fun populate(
        platforms: MutableList<Platform>,
        breakables: MutableList<BreakableUrn>,
        enemies: MutableList<EnemyEntity>
    ) {
        // Lower street. The first opening drops into the underpass; the second
        // is the stairwell that reconnects below the city block.
        platforms.addAll(
            listOf(
                cityPlatform(0f, 460f, 520f, 80f, PlatformStyle.STONE),
                cityPlatform(620f, 460f, 230f, 80f, PlatformStyle.STONE),
                cityPlatform(1000f, 460f, 140f, 80f, PlatformStyle.STONE),
                cityPlatform(1200f, 460f, 720f, 80f, PlatformStyle.STONE),

                // Underpass floor and its rising return steps.
                cityPlatform(440f, 700f, 400f, 28f, PlatformStyle.STONE),
                cityPlatform(730f, 630f, 90f, 16f, PlatformStyle.CITY_LEDGE),
                cityPlatform(790f, 560f, 90f, 16f, PlatformStyle.CITY_LEDGE),
                cityPlatform(850f, 490f, 130f, 16f, PlatformStyle.CITY_LEDGE),

                // House A: a high roof, two interior ledges, and a climb-in route.
                cityPlatform(260f, 220f, 354f, 22f, PlatformStyle.CITY_LEDGE),
                cityPlatform(260f, 242f, 24f, 168f, PlatformStyle.STONE),
                cityPlatform(590f, 242f, 24f, 168f, PlatformStyle.STONE),
                cityPlatform(160f, 380f, 70f, 16f, PlatformStyle.CITY_LEDGE),
                cityPlatform(220f, 300f, 60f, 16f, PlatformStyle.CITY_LEDGE),
                cityPlatform(295f, 350f, 110f, 16f, PlatformStyle.CITY_LEDGE),
                cityPlatform(435f, 390f, 110f, 16f, PlatformStyle.CITY_LEDGE),

                // House B: upper passage connects to House A's roof.
                cityPlatform(690f, 150f, 304f, 22f, PlatformStyle.CITY_LEDGE),
                cityPlatform(690f, 172f, 24f, 238f, PlatformStyle.STONE),
                cityPlatform(970f, 172f, 24f, 238f, PlatformStyle.STONE),
                cityPlatform(730f, 360f, 80f, 16f, PlatformStyle.CITY_LEDGE),
                cityPlatform(825f, 290f, 95f, 16f, PlatformStyle.CITY_LEDGE),
                cityPlatform(870f, 230f, 70f, 16f, PlatformStyle.CITY_LEDGE),

                // Exterior stair to the third roof. Steps are offset so the
                // player can jump up without hitting their solid undersides.
                cityPlatform(1030f, 390f, 60f, 16f, PlatformStyle.CITY_LEDGE),
                cityPlatform(1100f, 320f, 60f, 16f, PlatformStyle.CITY_LEDGE),
                cityPlatform(1165f, 250f, 30f, 16f, PlatformStyle.CITY_LEDGE),

                // House C and tower. The upper connector passes above the gate.
                cityPlatform(1200f, 220f, 424f, 22f, PlatformStyle.CITY_LEDGE),
                cityPlatform(1200f, 242f, 24f, 168f, PlatformStyle.STONE),
                cityPlatform(1600f, 242f, 24f, 168f, PlatformStyle.STONE),
                cityPlatform(1250f, 360f, 80f, 16f, PlatformStyle.CITY_LEDGE),
                cityPlatform(1350f, 300f, 80f, 16f, PlatformStyle.CITY_LEDGE),
                cityPlatform(1400f, 190f, 70f, 16f, PlatformStyle.CITY_LEDGE),
                cityPlatform(1480f, 110f, 120f, 16f, PlatformStyle.CITY_LEDGE),
                cityPlatform(1510f, 360f, 70f, 16f, PlatformStyle.CITY_LEDGE),
                cityPlatform(1520f, 270f, 220f, 18f, PlatformStyle.CITY_LEDGE),
                cityPlatform(1740f, 350f, 100f, 18f, PlatformStyle.CITY_LEDGE)
            )
        )

        // The gate is a full collision block on the street and is removed once
        // the player reaches the far side by using the upper route.
        platforms.add(shortcutGate)

        breakables.add(BreakableUrn(worldX(370f), worldY(326f), dropType = "health"))
        breakables.add(BreakableUrn(worldX(1270f), worldY(336f), dropType = "flask"))

        enemies.add(EnemyEntity(worldX(400f), worldY(410f), EnemyArchetype.LURKER, worldWidth(100f)))
        enemies.add(EnemyEntity(worldX(800f), worldY(410f), EnemyArchetype.LURKER, worldWidth(90f)))
        enemies.add(EnemyEntity(worldX(535f), worldY(165f), EnemyArchetype.CHERUB, worldWidth(110f)))
        enemies.add(EnemyEntity(worldX(1460f), worldY(150f), EnemyArchetype.CHERUB, worldWidth(100f)))
        enemies.add(EnemyEntity(worldX(1420f), worldY(410f), EnemyArchetype.LURKER, worldWidth(90f)))
    }
}
