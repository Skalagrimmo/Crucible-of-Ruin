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
    val boss: BaseBoss,
    // Stage 6d (#38): the room/door graph for the world map. A single TMX
    // section carries no rooms yet — the default keeps the loader
    // source-compatible.
    val rooms: List<LevelRoom> = emptyList()
)

/**
 * Builds the static geometry and encounters for the current side-scrolling level.
 * This factory describes the world; GameEngine remains responsible for simulation,
 * collisions, combat, and lifecycle/update order.
 */
internal object GameLevelFactory {
    /** Creates a fresh boss instance for the initial layout and encounter resets. */
    fun createBoss(): BaseBoss = CrucibleChimeraBoss(5750f, 360f)

    /**
     * Stage 6d (#38): the room/door graph the world map charts. Rooms are
     * full-height slices of the corridor; every door sits on the shared
     * boundary, on the surface the player actually crosses on, and both
     * sides declare it — so the graph is walkable in both directions.
     */
    private fun buildRooms(): List<LevelRoom> = listOf(
        LevelRoom(
            id = "bastion_entry", name = "Bastion Entry Hall",
            x = 0f, y = 0f, w = 600f, h = 540f,
            doors = listOf(RoomDoor(x = 600f, y = 460f, toRoomId = "bastion_ascent"))
        ),
        LevelRoom(
            id = "bastion_ascent", name = "The Bastion Ascent",
            x = 600f, y = 0f, w = 600f, h = 540f,
            doors = listOf(
                RoomDoor(x = 600f, y = 460f, toRoomId = "bastion_entry"),
                RoomDoor(x = 1200f, y = 420f, toRoomId = "bastion_ramparts")
            )
        ),
        LevelRoom(
            id = "bastion_ramparts", name = "The Bastion Ramparts",
            x = 1200f, y = 0f, w = 720f, h = 540f,
            doors = listOf(
                RoomDoor(x = 1200f, y = 420f, toRoomId = "bastion_ascent"),
                RoomDoor(x = 1920f, y = 420f, toRoomId = "crucible_labs")
            )
        ),
        LevelRoom(
            id = "crucible_labs", name = "The Crucible Laboratories",
            x = 1920f, y = 0f, w = 1020f, h = 540f,
            doors = listOf(
                RoomDoor(x = 1920f, y = 420f, toRoomId = "bastion_ramparts"),
                RoomDoor(x = 2940f, y = 260f, toRoomId = "cultist_tower")
            )
        ),
        LevelRoom(
            id = "cultist_tower", name = "The Cultist Tower",
            x = 2940f, y = 0f, w = 160f, h = 540f,
            doors = listOf(
                RoomDoor(x = 2940f, y = 260f, toRoomId = "crucible_labs"),
                RoomDoor(x = 3100f, y = 430f, toRoomId = "golem_gallery")
            )
        ),
        LevelRoom(
            id = "golem_gallery", name = "The Gallery of the Golem",
            x = 3100f, y = 0f, w = 650f, h = 540f,
            doors = listOf(
                RoomDoor(x = 3100f, y = 430f, toRoomId = "cultist_tower"),
                RoomDoor(x = 3750f, y = 430f, toRoomId = "vitriol_pools")
            )
        ),
        LevelRoom(
            id = "vitriol_pools", name = "The Vitriol Pools",
            x = 3750f, y = 0f, w = 610f, h = 540f,
            doors = listOf(
                RoomDoor(x = 3750f, y = 430f, toRoomId = "golem_gallery"),
                RoomDoor(x = 4360f, y = 420f, toRoomId = "sanctum_approach")
            )
        ),
        LevelRoom(
            id = "sanctum_approach", name = "The Sanctum Approach",
            x = 4360f, y = 0f, w = 910f, h = 540f,
            doors = listOf(
                RoomDoor(x = 4360f, y = 420f, toRoomId = "vitriol_pools"),
                RoomDoor(x = 5270f, y = 450f, toRoomId = "grand_sanctum")
            )
        ),
        LevelRoom(
            id = "grand_sanctum", name = "The Grand Sanctum",
            x = 5270f, y = 0f, w = 930f, h = 540f,
            doors = listOf(
                RoomDoor(x = 5270f, y = 450f, toRoomId = "sanctum_approach")
            )
        )
    )

    fun create(): GameLevelLayout {
        val platforms = mutableListOf<Platform>()
        val hazards = mutableListOf<Hazard>()
        val breakables = mutableListOf<BreakableUrn>()
        val enemies = mutableListOf<EnemyEntity>()

        // --- SECTION 1: THE RUINED BASTION (x: 0 - 1900) ---
        platforms.add(Platform(0f, 460f, 600f, 80f, PlatformStyle.STONE))
        platforms.add(Platform(680f, 460f, 500f, 80f, PlatformStyle.STONE))
        hazards.add(Hazard(600f, 500f, 80f, 40f, HazardType.ACID))

        // Stage 6c (#38): the bastion elevator — a one-way girder riding up and
        // down across the first acid pit. The bottom stop (top at 450) sits one
        // tile above the lower floors; the top stop (330) lines up with the
        // ledge stack's jump band. Base and swept path stay clear of every
        // static rectangle, so a rider is never pushed into a wall.
        platforms.add(
            Platform(
                610f, 330f, 60f, 20f, PlatformStyle.IRON_GIRDER, PlatformCollision.ONE_WAY,
                PlatformMotion(baseX = 610f, baseY = 330f, dx = 0f, dy = 120f, speed = 60f)
            )
        )

        platforms.add(Platform(240f, 390f, 90f, 20f, PlatformStyle.STONE_LEDGE, PlatformCollision.ONE_WAY))
        platforms.add(Platform(360f, 320f, 100f, 20f, PlatformStyle.STONE_LEDGE, PlatformCollision.ONE_WAY))
        platforms.add(Platform(500f, 250f, 110f, 20f, PlatformStyle.STONE_LEDGE, PlatformCollision.ONE_WAY))

        platforms.add(Platform(800f, 380f, 120f, 20f, PlatformStyle.IRON_GIRDER, PlatformCollision.ONE_WAY))
        platforms.add(Platform(1000f, 310f, 130f, 20f, PlatformStyle.IRON_GIRDER, PlatformCollision.ONE_WAY))
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
        platforms.add(Platform(2540f, 400f, 130f, 20f, PlatformStyle.IRON_GIRDER, PlatformCollision.ONE_WAY))
        platforms.add(Platform(2730f, 340f, 140f, 20f, PlatformStyle.IRON_GIRDER, PlatformCollision.ONE_WAY))
        hazards.add(Hazard(2480f, 510f, 350f, 30f, HazardType.ACID))

        // Stage 6c (#38): the laboratory ferry — a one-way girder gliding between
        // the upper girder and the cultist tower. Its right edge stops exactly at
        // the tower wall (x 2940), so the swept cells never overlap the tower's
        // static cells and a re-sync can never punch a hole in its roof; at the
        // far stop a rider steps down 10 px onto the tower's roof.
        platforms.add(
            Platform(
                2800f, 250f, 80f, 20f, PlatformStyle.IRON_GIRDER, PlatformCollision.ONE_WAY,
                PlatformMotion(baseX = 2800f, baseY = 250f, dx = 60f, dy = 0f, speed = 60f)
            )
        )

        platforms.add(Platform(2940f, 260f, 160f, 280f, PlatformStyle.STONE))
        enemies.add(EnemyEntity(3000f, 210f, EnemyArchetype.CULTIST, 0f))

        platforms.add(Platform(3100f, 430f, 650f, 110f, PlatformStyle.STONE))
        enemies.add(EnemyEntity(3300f, 370f, EnemyArchetype.GOLEM, 150f))
        enemies.add(EnemyEntity(3560f, 380f, EnemyArchetype.LURKER, 90f))

        platforms.add(Platform(3250f, 320f, 140f, 20f, PlatformStyle.IRON_GIRDER, PlatformCollision.ONE_WAY))
        platforms.add(Platform(3440f, 240f, 160f, 20f, PlatformStyle.IRON_GIRDER, PlatformCollision.ONE_WAY))
        breakables.add(BreakableUrn(3500f, 216f, dropType = "flask"))
        enemies.add(EnemyEntity(3480f, 190f, EnemyArchetype.CHERUB, 100f))

        platforms.add(Platform(3800f, 440f, 200f, 100f, PlatformStyle.STONE))
        hazards.add(Hazard(4000f, 500f, 80f, 40f, HazardType.ACID))
        platforms.add(Platform(4080f, 420f, 240f, 120f, PlatformStyle.STONE))
        enemies.add(EnemyEntity(4140f, 360f, EnemyArchetype.CULTIST, 0f))

        // --- SECTION 3: THE SANCTUM APPROACH (x: 4300 - 5100) ---
        platforms.add(Platform(4360f, 420f, 320f, 120f, PlatformStyle.STONE))

        platforms.add(Platform(4740f, 360f, 110f, 20f, PlatformStyle.STONE_LEDGE, PlatformCollision.ONE_WAY))
        hazards.add(Hazard(4680f, 510f, 260f, 30f, HazardType.ACID))
        platforms.add(Platform(4900f, 300f, 120f, 20f, PlatformStyle.STONE_LEDGE, PlatformCollision.ONE_WAY))
        enemies.add(EnemyEntity(4820f, 220f, EnemyArchetype.CHERUB, 130f))

        platforms.add(Platform(5070f, 430f, 200f, 110f, PlatformStyle.STONE))
        enemies.add(EnemyEntity(5120f, 370f, EnemyArchetype.GOLEM, 70f))
        breakables.add(BreakableUrn(5180f, 406f, dropType = "health"))

        // --- BOSS ARENA: GRAND SANCTUM (x: 5270 - 6200) ---
        platforms.add(Platform(5270f, 450f, 930f, 90f, PlatformStyle.STONE))
        // Arena gates start open (no collision) so the approach is walkable.
        // GameEngine seals them when the boss encounter activates.
        platforms.add(Platform(5270f, 100f, 30f, 350f, PlatformStyle.BOSS_GATE, PlatformCollision.OPEN))
        platforms.add(Platform(6170f, 100f, 30f, 350f, PlatformStyle.BOSS_GATE, PlatformCollision.OPEN))
        // Stage 5 (#37): arena girders lowered from y=330 to y=350. At y=330 they
        // sat 120 px above the floor — 1 px over the 119 px base-jump apex, making
        // them unreachable without the alchemy speed buff. LevelReachabilityTest
        // pins this choice against PhysicsTuning.
        // Stage 6b (#38): the girder thickness rounds up from 18 to 20 px so every
        // factory rectangle snaps exactly onto the 10 px tile grid. The top stays
        // at 350 — the reachability ruler and jump apex are unaffected.
        platforms.add(Platform(5420f, 350f, 120f, 20f, PlatformStyle.IRON_GIRDER, PlatformCollision.ONE_WAY))
        platforms.add(Platform(5880f, 350f, 120f, 20f, PlatformStyle.IRON_GIRDER, PlatformCollision.ONE_WAY))

        return GameLevelLayout(
            platforms = platforms,
            hazards = hazards,
            breakables = breakables,
            enemies = enemies,
            boss = createBoss(),
            rooms = buildRooms()
        )
    }
}
