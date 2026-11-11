package com.skalagrimmo.crucibleofruin.game

import com.skalagrimmo.crucibleofruin.game.boss.BaseBoss
import com.skalagrimmo.crucibleofruin.game.boss.CrucibleChimeraBoss
import com.skalagrimmo.crucibleofruin.game.boss.SiegeGolemBoss

/**
 * Stage 10d (#69): the legacy/fallback level source. The engine's default
 * path loads the live crucible_of_ruin.tmx asset through LiveLevelLoader;
 * this factory remains for tests and for callers that install no level
 * source. FullLevelTmxParityTest pins the TMX and this factory to identical
 * content.
 *
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
    val rooms: List<LevelRoom> = emptyList(),
    // Stage 7a (#51): world bounds travel with the layout, so a district can
    // stack below the corridor without the engine hard-coding its size. The
    // defaults keep the stock 6200x540 corridor and loader callers compatible.
    val width: Float = 6200f,
    val height: Float = 540f,
    // Stage 8a (#56): rectangles that unlock the district's sealed shortcut gates.
    val gateTriggers: List<GateTrigger> = emptyList(),
    // Stage 9c (#62): the active encounter's arming line, its fresh-instance
    // factory, and the encounters queued behind it. The defaults keep the
    // legacy single-Chimera corridor for every existing caller and test layout.
    val bossTriggerX: Float = 5340f,
    val bossTriggerMaxY: Float = 540f,
    val bossFactory: () -> BaseBoss = { GameLevelFactory.createBoss() },
    val laterEncounters: List<BossEncounter> = emptyList()
)

/**
 * Stage 9c (#62): one boss fight in the encounter chain — a fresh-boss
 * factory plus the trigger line whose crossing arms the fight.
 */
data class BossEncounter(
    val createBoss: () -> BaseBoss,
    val triggerX: Float,
    val triggerMaxY: Float = 540f
)

/**
 * Builds the static geometry and encounters for the current side-scrolling level.
 * This factory describes the world; GameEngine remains responsible for simulation,
 * collisions, combat, and lifecycle/update order.
 */
internal object GameLevelFactory {
    /** Creates a fresh boss instance for the initial layout and encounter resets. */
    fun createBoss(): BaseBoss = CrucibleChimeraBoss(5750f, 360f)

    /** Stage 9c (#62): fresh Chimera for the final Sanctum encounter. */
    fun createChimeraBoss(): BaseBoss = CrucibleChimeraBoss(5750f, 360f)

    /** Stage 9c (#62): fresh Siege Golem for the Foundry Yard mid-boss encounter. */
    fun createSiegeGolemBoss(): BaseBoss = SiegeGolemBoss(3500f, 260f)

    /**
     * Stage 6d (#38): the room/door graph the world map charts. Stage 7b (#51):
     * rooms are arbitrary world rectangles — the corridor is charted as
     * full-height slices, and the Undercroft stacks a second band of rooms
     * below the Gallery of the Golem. Every door sits on the surface the
     * player actually crosses on, and both sides declare it — so the graph is
     * walkable in both directions. A vertical transition (the shaft) anchors
     * both ends in one world column: the shaft mouth above, the landing below.
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
                // Stage 7b (#51): the shaft mouth — the way down into the Undercroft.
                RoomDoor(x = 3355f, y = 430f, toRoomId = "sunken_descent"),
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
                // Stage 8b (#56): the shaft mouth — the way down into the
                // Iron Garrison district.
                RoomDoor(x = 4490f, y = 420f, toRoomId = "garrison_descent"),
                RoomDoor(x = 5270f, y = 450f, toRoomId = "grand_sanctum")
            )
        ),
        LevelRoom(
            id = "grand_sanctum", name = "The Grand Sanctum",
            x = 5270f, y = 0f, w = 930f, h = 540f,
            doors = listOf(
                RoomDoor(x = 5270f, y = 450f, toRoomId = "sanctum_approach")
            )
        ),
        // Stage 7b (#51): the Undercroft — a second band of rooms stacked a full
        // corridor height below the Gallery of the Golem. The door pair of the
        // vertical transition anchors both ends of the shaft in one world column.
        LevelRoom(
            id = "sunken_descent", name = "The Sunken Descent",
            x = 3100f, y = 540f, w = 400f, h = 540f,
            doors = listOf(
                RoomDoor(x = 3355f, y = 1000f, toRoomId = "golem_gallery"),
                RoomDoor(x = 3500f, y = 1000f, toRoomId = "undercroft_halls")
            )
        ),
        LevelRoom(
            id = "undercroft_halls", name = "The Undercroft Halls",
            x = 3500f, y = 540f, w = 500f, h = 540f,
            doors = listOf(
                RoomDoor(x = 3500f, y = 1000f, toRoomId = "sunken_descent"),
                RoomDoor(x = 4000f, y = 1000f, toRoomId = "reliquary_vault")
            )
        ),
        LevelRoom(
            id = "reliquary_vault", name = "The Reliquary Vault",
            x = 4000f, y = 540f, w = 360f, h = 540f,
            doors = listOf(
                RoomDoor(x = 4000f, y = 1000f, toRoomId = "undercroft_halls"),
                // Stage 8b (#56): the vault's right wall opens onto the
                // Iron Garrison street.
                RoomDoor(x = 4360f, y = 1000f, toRoomId = "garrison_descent")
            )
        ),
        // Stage 8b (#56): the Iron Garrison — the district band continues
        // below the Sanctum Approach and the Grand Sanctum, tiling the lower
        // band all the way to the world's right edge. The shaft door pair
        // anchors both ends in one column (mouth 4490 above, landing 4490
        // below); the keep's elevated door anchors on the L2 girder that
        // crosses the boundary.
        LevelRoom(
            id = "garrison_descent", name = "The Garrison Descent",
            x = 4360f, y = 540f, w = 340f, h = 540f,
            doors = listOf(
                RoomDoor(x = 4490f, y = 1000f, toRoomId = "sanctum_approach"),
                RoomDoor(x = 4360f, y = 1000f, toRoomId = "reliquary_vault"),
                RoomDoor(x = 4700f, y = 1000f, toRoomId = "garrison_street")
            )
        ),
        LevelRoom(
            id = "garrison_street", name = "The Garrison Street",
            x = 4700f, y = 540f, w = 1000f, h = 540f,
            doors = listOf(
                RoomDoor(x = 4700f, y = 1000f, toRoomId = "garrison_descent"),
                RoomDoor(x = 5700f, y = 1000f, toRoomId = "garrison_keep"),
                RoomDoor(x = 5700f, y = 590f, toRoomId = "garrison_keep")
            )
        ),
        LevelRoom(
            id = "garrison_keep", name = "The Garrison Keep",
            x = 5700f, y = 540f, w = 500f, h = 540f,
            doors = listOf(
                RoomDoor(x = 5700f, y = 1000f, toRoomId = "garrison_street"),
                RoomDoor(x = 5700f, y = 590f, toRoomId = "garrison_street")
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

        // Stage 11a (#77): the ascent barrier — a solid wall that ends the
        // ground run through the Bastion Ascent. The only way over is the
        // girder ladder (800 -> 1000) and a jump from the top girder onto
        // the wall cap, through the lurker and cherub holding the climb.
        platforms.add(Platform(1140f, 240f, 40f, 220f, PlatformStyle.STONE))        platforms.add(Platform(1200f, 420f, 350f, 120f, PlatformStyle.STONE))

        platforms.add(Platform(1600f, 400f, 320f, 140f, PlatformStyle.STONE))

        // Stage 11a (#77): the ramparts barrier — the ground run into the
        // laboratories ends at a solid wall; the one-way ledge ladder on
        // its left face is the way over, a cherub haunts the climb, and
        // the right-face ledge keeps the return trip open.
        platforms.add(Platform(1690f, 330f, 100f, 20f, PlatformStyle.STONE_LEDGE, PlatformCollision.ONE_WAY))
        platforms.add(Platform(1810f, 250f, 100f, 20f, PlatformStyle.STONE_LEDGE, PlatformCollision.ONE_WAY))
        platforms.add(Platform(1880f, 180f, 40f, 220f, PlatformStyle.STONE))
        platforms.add(Platform(1940f, 310f, 90f, 20f, PlatformStyle.STONE_LEDGE, PlatformCollision.ONE_WAY))        hazards.add(Hazard(1550f, 510f, 50f, 30f, HazardType.SPIKES))

        enemies.add(EnemyEntity(420f, 410f, EnemyArchetype.LURKER, 120f))
        enemies.add(EnemyEntity(920f, 410f, EnemyArchetype.LURKER, 140f))
        enemies.add(EnemyEntity(520f, 180f, EnemyArchetype.CHERUB, 110f))
        enemies.add(EnemyEntity(1100f, 240f, EnemyArchetype.CHERUB, 120f))
        enemies.add(EnemyEntity(1420f, 370f, EnemyArchetype.LURKER, 100f))

        // Stage 11a (#77): the ramparts climb's aerial guard.
        enemies.add(EnemyEntity(1750f, 220f, EnemyArchetype.CHERUB, 110f))
        breakables.add(BreakableUrn(380f, 296f, dropType = "health"))

        // Stage 11a (#77): the ascent wall cap's reward.
        breakables.add(BreakableUrn(1150f, 216f, dropType = "flask"))        breakables.add(BreakableUrn(1240f, 396f, dropType = "flask"))

        // Stage 11a (#77): the ramparts wall cap's reward.
        breakables.add(BreakableUrn(1890f, 156f, dropType = "health"))
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

        // Stage 7b (#51): the gallery floor splits around the Undercroft shaft
        // (x 3360-3450) — the hole drops the player into the Sunken Descent,
        // and the lift riding the shaft is the way back up.
        platforms.add(Platform(3100f, 430f, 260f, 110f, PlatformStyle.STONE))
        platforms.add(Platform(3450f, 430f, 300f, 110f, PlatformStyle.STONE))
        // Stage 9c (#62): the Foundry Yard between the floor halves is the
        // Siege Golem's arena — the regular garrison here is gone, and a
        // health urn waits just inside the entry gate.
        breakables.add(BreakableUrn(3200f, 406f, dropType = "health"))

        platforms.add(Platform(3250f, 320f, 140f, 20f, PlatformStyle.IRON_GIRDER, PlatformCollision.ONE_WAY))
        platforms.add(Platform(3440f, 240f, 160f, 20f, PlatformStyle.IRON_GIRDER, PlatformCollision.ONE_WAY))
        breakables.add(BreakableUrn(3500f, 216f, dropType = "flask"))

        // Stage 11a (#77): the vitriol wall cap's reward.
        breakables.add(BreakableUrn(3970f, 216f, dropType = "flask"))
        // Stage 9c (#62): Foundry Yard arena gates — open for the approach,
        // sealed by GameEngine when the Siege Golem encounter arms.
        platforms.add(Platform(3110f, 80f, 30f, 350f, PlatformStyle.BOSS_GATE, PlatformCollision.OPEN))
        platforms.add(Platform(3720f, 80f, 30f, 350f, PlatformStyle.BOSS_GATE, PlatformCollision.OPEN))

        platforms.add(Platform(3800f, 440f, 200f, 100f, PlatformStyle.STONE))

        // Stage 11a (#77): the vitriol barrier — the acid crossing stops
        // being a hop: the ledge and wall cap force a climb under the
        // cherub's patrol before the jump over the acid to the far floor.
        platforms.add(Platform(3840f, 350f, 100f, 20f, PlatformStyle.STONE_LEDGE, PlatformCollision.ONE_WAY))
        platforms.add(Platform(3960f, 240f, 40f, 200f, PlatformStyle.STONE))
        platforms.add(Platform(4020f, 330f, 60f, 20f, PlatformStyle.STONE_LEDGE, PlatformCollision.ONE_WAY))        hazards.add(Hazard(4000f, 500f, 80f, 40f, HazardType.ACID))
        platforms.add(Platform(4080f, 420f, 240f, 120f, PlatformStyle.STONE))
        enemies.add(EnemyEntity(4140f, 360f, EnemyArchetype.CULTIST, 0f))

        // Stage 11a (#77): the vitriol climb's aerial guard.
        enemies.add(EnemyEntity(3900f, 280f, EnemyArchetype.CHERUB, 110f))
        // --- SECTION 3: THE SANCTUM APPROACH (x: 4300 - 5100) ---
        // Stage 8b (#56): the approach floor splits around the Iron Garrison
        // shaft (x 4500-4600) — the hole drops the player into the district
        // street, and the stair return is the way back up.
        platforms.add(Platform(4360f, 420f, 140f, 120f, PlatformStyle.STONE))
        platforms.add(Platform(4600f, 420f, 80f, 120f, PlatformStyle.STONE))

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

        // --- SECTION 4: THE UNDERCROFT (x: 3100 - 4360, y: 540 - 1080) ---
        // Stage 7b (#51): the vertical district below the Gallery of the Golem.
        // Every climb is measured against the PhysicsTuning ruler: a base jump
        // clears 119 px, so no rise on the route exceeds 110 px and no horizontal
        // gap exceeds the 152 px base-jump reach. The district floors sit at
        // y = 1000, and the shaft lift rides from the descent floor up to the
        // gallery level — its top stop (460) is a 30 px step onto the gallery
        // floor's 430 top, well within a base jump.

        // The shaft lift: rides 960 -> 460 and back. The swept column stays
        // strictly inside the shaft (x 3360-3450), so it never overlaps the
        // split gallery floor's static cells.
        platforms.add(
            Platform(
                3370f, 960f, 70f, 20f, PlatformStyle.IRON_GIRDER, PlatformCollision.ONE_WAY,
                PlatformMotion(baseX = 3370f, baseY = 960f, dx = 0f, dy = -500f, speed = 110f)
            )
        )

        // Sunken Descent: the landing under the shaft, spike teeth near the wall,
        // a lurker patrolling beside the lift's doorstep.
        platforms.add(Platform(3100f, 1000f, 400f, 80f, PlatformStyle.STONE))
        hazards.add(Hazard(3120f, 980f, 60f, 20f, HazardType.SPIKES))
        enemies.add(EnemyEntity(3250f, 964f, EnemyArchetype.LURKER, 60f))
        breakables.add(BreakableUrn(3470f, 976f, dropType = "flask"))

        // Undercroft Halls: a zigzag girder climb toward a ledge under the
        // corridor's ceiling, a cherub haunting the climb, and a cultist
        // holding the vault door behind an acid strip.
        platforms.add(Platform(3500f, 1000f, 500f, 80f, PlatformStyle.STONE))
        platforms.add(Platform(3560f, 890f, 110f, 20f, PlatformStyle.IRON_GIRDER, PlatformCollision.ONE_WAY))
        platforms.add(Platform(3720f, 780f, 110f, 20f, PlatformStyle.IRON_GIRDER, PlatformCollision.ONE_WAY))
        platforms.add(Platform(3560f, 670f, 110f, 20f, PlatformStyle.IRON_GIRDER, PlatformCollision.ONE_WAY))
        platforms.add(Platform(3780f, 600f, 120f, 20f, PlatformStyle.STONE_LEDGE, PlatformCollision.ONE_WAY))
        hazards.add(Hazard(3900f, 970f, 80f, 30f, HazardType.ACID))
        enemies.add(EnemyEntity(3700f, 700f, EnemyArchetype.CHERUB, 120f))
        enemies.add(EnemyEntity(3860f, 958f, EnemyArchetype.CULTIST, 0f))
        breakables.add(BreakableUrn(3820f, 576f, dropType = "health"))

        // Reliquary Vault: the district's treasure room — spike teeth at the
        // door, a golem guardian, and a perch with the vault's hoard.
        platforms.add(Platform(4000f, 1000f, 360f, 80f, PlatformStyle.STONE))
        platforms.add(Platform(4150f, 890f, 100f, 20f, PlatformStyle.IRON_GIRDER, PlatformCollision.ONE_WAY))
        hazards.add(Hazard(4040f, 980f, 60f, 20f, HazardType.SPIKES))
        enemies.add(EnemyEntity(4200f, 946f, EnemyArchetype.GOLEM, 90f))
        breakables.add(BreakableUrn(4180f, 866f, dropType = "flask"))
        breakables.add(BreakableUrn(4290f, 976f, dropType = "health"))


        // --- SECTION 5: THE IRON GARRISON (x: 4360 - 6200, y: 540 - 1080) ---
        // Stage 8b (#56): the fortress district below the Sanctum Approach,
        // reached through the shaft in the approach floor. Every rise stays
        // under the 119 px base-jump apex and every gap under the base-jump
        // reach, so the whole district climbs with base stats. The street runs
        // the district's full width at the Undercroft's floor level; the
        // fortress blocks wall it into roofless pockets; the rooftops are the
        // way across, and the stair return climbs back to the corridor
        // through the same shaft the player dropped through.

        // The district street: bridges the Reliquary Vault's floor (same top,
        // 1000) all the way to the world's right edge.
        platforms.add(Platform(4360f, 1000f, 1840f, 80f, PlatformStyle.STONE))

        // The stair return: a stacked one-way climb under the shaft — the
        // same pass-through pattern as the Undercroft's girder climb. The top
        // step g5 hugs the shaft's left wall and g4 stacks under the shaft's
        // right half, so a drop through the mouth lands on one of them and
        // never traps the player: walking off g5's right edge steps down onto
        // g4, and walking off g4's left edge falls the open column straight to
        // the district street. Climbing back is a chain of base jumps — g5
        // sits 100 px below the corridor floor's top, and the 119 px apex
        // clears it with room to drift over the ledge.
        platforms.add(Platform(4560f, 890f, 110f, 20f, PlatformStyle.STONE_LEDGE, PlatformCollision.ONE_WAY))
        platforms.add(Platform(4620f, 800f, 110f, 20f, PlatformStyle.STONE_LEDGE, PlatformCollision.ONE_WAY))
        platforms.add(Platform(4560f, 710f, 110f, 20f, PlatformStyle.STONE_LEDGE, PlatformCollision.ONE_WAY))
        platforms.add(Platform(4560f, 620f, 110f, 20f, PlatformStyle.STONE_LEDGE, PlatformCollision.ONE_WAY))
        platforms.add(Platform(4500f, 520f, 70f, 20f, PlatformStyle.STONE_LEDGE, PlatformCollision.ONE_WAY))

        // The fortress blocks: solid walls that break the street into
        // roofless pockets and carry the rooftop route across the district.
        platforms.add(Platform(4780f, 890f, 160f, 110f, PlatformStyle.STONE))
        platforms.add(Platform(5040f, 820f, 140f, 180f, PlatformStyle.STONE))
        platforms.add(Platform(5300f, 740f, 160f, 260f, PlatformStyle.STONE))
        platforms.add(Platform(5900f, 820f, 140f, 180f, PlatformStyle.STONE))

        // The sealed keep gate: solid bars between the street pockets until
        // the elevated route's trigger unlocks them (Stage 8a seams).
        platforms.add(
            Platform(
                x = 5700f, y = 880f, w = 30f, h = 120f,
                style = PlatformStyle.IRON_GATE, collision = PlatformCollision.SOLID,
                gateId = "garrison_gate"
            )
        )

        // The elevated route over the rooftops: L2 crosses the gate's
        // column, and landing on L3 fires the trigger that unseals the gate.
        platforms.add(Platform(5500f, 670f, 120f, 20f, PlatformStyle.STONE_LEDGE, PlatformCollision.ONE_WAY))
        platforms.add(Platform(5660f, 590f, 120f, 20f, PlatformStyle.IRON_GIRDER, PlatformCollision.ONE_WAY))
        platforms.add(Platform(5800f, 670f, 120f, 20f, PlatformStyle.STONE_LEDGE, PlatformCollision.ONE_WAY))

        // Street hazards: acid pools and spike teeth between the blocks.
        hazards.add(Hazard(4950f, 980f, 80f, 20f, HazardType.ACID))
        hazards.add(Hazard(5210f, 980f, 60f, 20f, HazardType.SPIKES))
        hazards.add(Hazard(5470f, 980f, 70f, 20f, HazardType.ACID))
        hazards.add(Hazard(6160f, 980f, 40f, 20f, HazardType.SPIKES))

        // The garrison watch: a lurker by the stair's doorstep, cherubs
        // haunting the rooftops, a cultist holding the gate's street, and a
        // golem in the pocket past the keep block.
        enemies.add(EnemyEntity(4420f, 964f, EnemyArchetype.LURKER, 80f))
        enemies.add(EnemyEntity(5150f, 760f, EnemyArchetype.CHERUB, 110f))
        enemies.add(EnemyEntity(5390f, 680f, EnemyArchetype.CHERUB, 120f))
        enemies.add(EnemyEntity(5600f, 958f, EnemyArchetype.CULTIST, 0f))
        enemies.add(EnemyEntity(6080f, 946f, EnemyArchetype.GOLEM, 30f))

        breakables.add(BreakableUrn(4430f, 976f, dropType = "health"))
        breakables.add(BreakableUrn(4830f, 866f, dropType = "flask"))
        breakables.add(BreakableUrn(5350f, 716f, dropType = "health"))
        breakables.add(BreakableUrn(5940f, 796f, dropType = "flask"))
        breakables.add(BreakableUrn(6110f, 976f, dropType = "flask"))

        return GameLevelLayout(
            platforms = platforms,
            hazards = hazards,
            breakables = breakables,
            enemies = enemies,
            // Stage 9c (#62): the run opens with the Siege Golem in the Foundry
            // Yard; the Chimera waits as the final Sanctum encounter behind it.
            boss = createSiegeGolemBoss(),
            bossFactory = { createSiegeGolemBoss() },
            bossTriggerX = 3200f,
            laterEncounters = listOf(
                BossEncounter(createBoss = { createChimeraBoss() }, triggerX = 5340f)
            ),
            rooms = buildRooms(),
            // Stage 7b (#51): the Undercroft stacks below the corridor — the
            // world grows one corridor height downward.
            width = 6200f,
            height = 1080f,
            // Stage 8b (#56): crossing the elevated route's far landing
            // unlocks the sealed keep gate — the #11 shortcut semantics.
            gateTriggers = listOf(
                GateTrigger(gateId = "garrison_gate", x = 5800f, y = 540f, w = 80f, h = 140f)
            )
        )
    }
}
