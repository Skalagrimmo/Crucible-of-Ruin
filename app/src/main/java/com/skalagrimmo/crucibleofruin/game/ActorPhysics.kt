package com.skalagrimmo.crucibleofruin.game

import kotlin.math.ceil
import kotlin.math.floor

/**
 * Shared axis-separated physics resolver (Stage 2, #34).
 *
 * One implementation of gravity integration and AABB resolve replaces the four
 * private, slightly different copies that previously lived in GamePhysics (player),
 * GameEngine.updateEnemies (enemies), BaseBoss.updatePhysics (boss), and the
 * pickup loop in GameEngine.updatePickups. Each call site now supplies an
 * [ActorPhysicsConfig] describing its constants and resolve semantics, so future
 * physics fixes land in exactly one place.
 */
interface PhysicsActor {
    var x: Float
    var y: Float
    var vx: Float
    var vy: Float
    val w: Float
    val h: Float
    var grounded: Boolean
    var facing: Int

    /**
     * Optional horizontal speed multiplier applied inside the resolver
     * (the boss uses it for the corrosion slow).
     */
    fun horizontalSpeedMultiplier(): Float = 1f

    /** Called exactly once when a resolve pass snaps the actor onto a platform top. */
    fun onLanded(platform: Platform) {}
}

/** Which platforms participate in a given resolve pass. */
enum class PlatformFilter {
    SOLID_ONLY,
    NOT_OPEN,
    ALL
}

/** How the vertical axis is resolved after gravity integration. */
enum class VerticalResolveMode {
    /**
     * Player semantics: a swept top-crossing landing that also catches thin
     * platforms at large steps, plus a solid-only overlap fallback that
     * blocks the underside.
     */
    SWEPT_LANDING,

    /** Enemy and boss semantics: plain overlap resolve — land while falling, block while rising. */
    OVERLAP_LAND_OR_BLOCK,

    /** Pickup semantics: unconditional snap-to-top overlap resolve. */
    OVERLAP_SNAP_TO_TOP
}

/**
 * Per-archetype physics behavior: gravity and terminal velocity plus the flags
 * the shared resolver needs to reproduce each legacy call site exactly.
 */
class ActorPhysicsConfig(
    val gravity: Float,
    val terminalVelocity: Float,
    val horizontalMotion: Boolean = true,
    val horizontalFilter: PlatformFilter = PlatformFilter.SOLID_ONLY,
    val flipOnWall: Boolean = false,
    val verticalMode: VerticalResolveMode = VerticalResolveMode.SWEPT_LANDING,
    val landingFilter: PlatformFilter = PlatformFilter.NOT_OPEN,
    val overlapFilter: PlatformFilter = PlatformFilter.SOLID_ONLY,
    val ignorePlatforms: Boolean = false
)

object ActorPhysics {
    private const val LANDING_SLOP = 0.5f

    /**
     * Integrates one step of gravity, movement, and platform resolution for any
     * [PhysicsActor]. The axis order mirrors the legacy loops: gravity, then the
     * X move with wall resolve, then the Y move with landing/underside resolve.
     */
    fun moveAndResolve(actor: PhysicsActor, platforms: List<Platform>, config: ActorPhysicsConfig, dt: Float) {
        if (config.gravity != 0f) {
            actor.vy += config.gravity * dt
            if (actor.vy > config.terminalVelocity) actor.vy = config.terminalVelocity
        }

        // Physics X: move, then push out of the platforms configured as walls.
        if (config.horizontalMotion) {
            val effectiveVx = actor.vx * actor.horizontalSpeedMultiplier()
            actor.x += effectiveVx * dt
            if (!config.ignorePlatforms) {
                for (platform in platforms) {
                    if (!matches(platform.collision, config.horizontalFilter)) continue
                    // Stage 3 (#35): a one-way surface is only a floor — it never
                    // acts as a side wall for any archetype.
                    if (platform.oneWay) continue
                    if (aabbIntersects(
                            actor.x, actor.y, actor.w, actor.h,
                            platform.x, platform.y, platform.w, platform.h
                        )
                    ) {
                        if (effectiveVx > 0f) {
                            actor.x = platform.x - actor.w
                            if (config.flipOnWall) actor.facing = -1
                        } else if (effectiveVx < 0f) {
                            actor.x = platform.x + platform.w
                            if (config.flipOnWall) actor.facing = 1
                        }
                        actor.vx = 0f
                    }
                }
            }
        }

        // Physics Y: remember the previous feet position before applying this tick's motion.
        val previousFeet = actor.y + actor.h
        actor.y += actor.vy * dt
        val currentFeet = actor.y + actor.h

        if (config.ignorePlatforms) return
        actor.grounded = false

        when (config.verticalMode) {
            VerticalResolveMode.SWEPT_LANDING -> {
                // Find the first top surface crossed while descending. This swept check also
                // catches thin ledges when a single step moves the actor across the surface.
                if (actor.vy > 0f) {
                    var landingPlatform: Platform? = null
                    for (platform in platforms) {
                        if (!matches(platform.collision, config.landingFilter)) continue
                        val overlapsX = actor.x < platform.x + platform.w &&
                            actor.x + actor.w > platform.x
                        val crossedTop = previousFeet <= platform.y + LANDING_SLOP &&
                            currentFeet >= platform.y
                        if (overlapsX && crossedTop &&
                            (landingPlatform == null || platform.y < landingPlatform.y)
                        ) {
                            landingPlatform = platform
                        }
                    }
                    if (landingPlatform != null) {
                        landOn(actor, landingPlatform)
                    }
                }
                // Solid platforms still block the underside and resolve any existing
                // overlap. One-way surfaces intentionally do not participate here.
                if (!actor.grounded) {
                    for (platform in platforms) {
                        if (!matches(platform.collision, config.overlapFilter)) continue
                        if (aabbIntersects(
                                actor.x, actor.y, actor.w, actor.h,
                                platform.x, platform.y, platform.w, platform.h
                            )
                        ) {
                            if (actor.vy > 0f) {
                                landOn(actor, platform)
                            } else if (actor.vy < 0f) {
                                actor.y = platform.y + platform.h
                                actor.vy = 0f
                            }
                        }
                    }
                }
            }
            VerticalResolveMode.OVERLAP_LAND_OR_BLOCK -> {
                for (platform in platforms) {
                    if (!matches(platform.collision, config.landingFilter)) continue
                    if (aabbIntersects(
                            actor.x, actor.y, actor.w, actor.h,
                            platform.x, platform.y, platform.w, platform.h
                        )
                    ) {
                        // Stage 3 (#35): one-way surfaces never block rising motion,
                        // and they only catch actors whose feet crossed the top this
                        // tick — a side entry with feet already below the top falls
                        // through instead of being teleported on top.
                        if (platform.oneWay) {
                            if (actor.vy > 0f && previousFeet <= platform.y + LANDING_SLOP) {
                                landOn(actor, platform)
                            }
                        } else if (actor.vy > 0f) {
                            landOn(actor, platform)
                        } else if (actor.vy < 0f) {
                            actor.y = platform.y + platform.h
                            actor.vy = 0f
                        }
                    }
                }
            }
            VerticalResolveMode.OVERLAP_SNAP_TO_TOP -> {
                for (platform in platforms) {
                    if (!matches(platform.collision, config.landingFilter)) continue
                    if (aabbIntersects(
                            actor.x, actor.y, actor.w, actor.h,
                            platform.x, platform.y, platform.w, platform.h
                        )
                    ) {
                        actor.y = platform.y - actor.h
                        actor.vy = 0f
                    }
                }
            }
        }
    }

    /**
     * Stage 6b (#38): tile-grid resolver.
     *
     * Behaviorally identical to the list resolver above on the world's real
     * geometry: every factory rectangle is 10 px aligned and the maximum
     * horizontal step per tick (~8.7 px) stays below one tile, so the list
     * resolver's sequential pushes converge to exactly the cell-edge extremes
     * this single-pass version computes; no actor ever overlaps more than one
     * surface of a kind per tick. The one intentional divergence: OPEN
     * platforms rasterize as empty cells, while a list pass with
     * PlatformFilter.ALL would treat them as walls or floors. No enemy or boss
     * ever overlaps a gate column while the gates are open (enemies live before
     * the arena; the boss only moves while the gates are sealed), so the
     * divergence is unreachable. The list overload stays as the reference
     * implementation — LevelGridParityTest pins the two together.
     */
    internal fun moveAndResolve(actor: PhysicsActor, grid: LevelGrid, config: ActorPhysicsConfig, dt: Float) {
        if (config.gravity != 0f) {
            actor.vy += config.gravity * dt
            if (actor.vy > config.terminalVelocity) actor.vy = config.terminalVelocity
        }

        // Physics X: move, then push out of the solid cells configured as walls.
        if (config.horizontalMotion) {
            val effectiveVx = actor.vx * actor.horizontalSpeedMultiplier()
            actor.x += effectiveVx * dt
            if (!config.ignorePlatforms) {
                resolveWallsOnGrid(actor, grid, config, effectiveVx)
            }
        }

        // Physics Y: remember the previous feet position before applying this tick's motion.
        val previousFeet = actor.y + actor.h
        actor.y += actor.vy * dt

        if (config.ignorePlatforms) return
        actor.grounded = false

        // Strict-edge column/row ranges: identical to the legacy aabb's strict
        // inequalities on cell-aligned geometry (touching edges never overlap).
        val colFrom = floor(actor.x / grid.tile).toInt()
        val colTo = ceil((actor.x + actor.w) / grid.tile).toInt() - 1
        val rowFrom = floor(actor.y / grid.tile).toInt()
        val rowTo = ceil((actor.y + actor.h) / grid.tile).toInt() - 1

        when (config.verticalMode) {
            VerticalResolveMode.SWEPT_LANDING -> resolveSweptLandingOnGrid(
                actor, grid, config, previousFeet, colFrom, colTo, rowFrom, rowTo
            )
            VerticalResolveMode.OVERLAP_LAND_OR_BLOCK -> resolveOverlapOnGrid(
                actor, grid, config, previousFeet, colFrom, colTo, rowFrom, rowTo
            )
            VerticalResolveMode.OVERLAP_SNAP_TO_TOP -> resolveSnapToTopOnGrid(
                actor, grid, config, colFrom, colTo, rowFrom, rowTo
            )
        }
    }

    private fun resolveWallsOnGrid(
        actor: PhysicsActor,
        grid: LevelGrid,
        config: ActorPhysicsConfig,
        effectiveVx: Float
    ) {
        val colFrom = floor(actor.x / grid.tile).toInt()
        val colTo = ceil((actor.x + actor.w) / grid.tile).toInt() - 1
        val rowFrom = floor(actor.y / grid.tile).toInt()
        val rowTo = ceil((actor.y + actor.h) / grid.tile).toInt() - 1
        // One-way surfaces never act as side walls (Stage 3, #35) — only solid
        // cells do, which is exactly what the grid stores for them.
        if (effectiveVx > 0f) {
            var minLeft = Float.MAX_VALUE
            var row = rowFrom
            while (row <= rowTo) {
                var col = colFrom
                while (col <= colTo) {
                    if (grid.solidIn(col, row)) {
                        val left = col * grid.tile
                        if (left < minLeft) minLeft = left
                    }
                    col++
                }
                row++
            }
            if (minLeft != Float.MAX_VALUE) {
                actor.x = minLeft - actor.w
                if (config.flipOnWall) actor.facing = -1
                actor.vx = 0f
            }
        } else if (effectiveVx < 0f) {
            var maxRight = -Float.MAX_VALUE
            var row = rowFrom
            while (row <= rowTo) {
                var col = colFrom
                while (col <= colTo) {
                    if (grid.solidIn(col, row)) {
                        val right = (col + 1) * grid.tile
                        if (right > maxRight) maxRight = right
                    }
                    col++
                }
                row++
            }
            if (maxRight != -Float.MAX_VALUE) {
                actor.x = maxRight
                if (config.flipOnWall) actor.facing = 1
                actor.vx = 0f
            }
        }
    }

    private fun resolveSweptLandingOnGrid(
        actor: PhysicsActor,
        grid: LevelGrid,
        config: ActorPhysicsConfig,
        previousFeet: Float,
        colFrom: Int,
        colTo: Int,
        rowFrom: Int,
        rowTo: Int
    ) {
        // Find the topmost cell top crossed while descending. The crossed-top
        // condition mirrors the legacy float comparison exactly:
        // top + LANDING_SLOP >= previousFeet and top <= current feet.
        if (actor.vy > 0f) {
            val currentFeet = actor.y + actor.h
            // The scan starts one tile above the swept band: a moving platform's
            // true top can sit up to a tile inside its surface cell (Stage 6c),
            // and the crossed check below compares against that true top.
            var row = ceil((previousFeet - LANDING_SLOP) / grid.tile).toInt() - 2
            if (row < 0) row = 0
            while (row * grid.tile + grid.tile + LANDING_SLOP < previousFeet) row++
            val endRow = floor(currentFeet / grid.tile).toInt()
            while (row <= endRow) {
                var col = colFrom
                while (col <= colTo) {
                    // A cell is a landable surface only when the cell above it is
                    // empty: interior rows of a rasterized platform are not tops,
                    // while the list resolver only ever sees the platform's top y.
                    if (matchesCell(grid.collisionCell(col, row), config.landingFilter) &&
                        grid.collisionCell(col, row - 1) == 0
                    ) {
                        val platform = grid.platformIn(col, row)
                        val top = platform?.y ?: row * grid.tile
                        if (top + LANDING_SLOP >= previousFeet && currentFeet >= top) {
                            landOn(actor, top, platform)
                            return
                        }
                    }
                    col++
                }
                row++
            }
        }
        // Solid cells still block the underside and resolve any existing overlap.
        // One-way cells intentionally do not participate here.
        if (actor.grounded) return
        if (actor.vy > 0f) {
            var row = rowFrom
            while (row <= rowTo) {
                var col = colFrom
                while (col <= colTo) {
                    if (matchesCell(grid.collisionCell(col, row), config.overlapFilter)) {
                        val platform = grid.platformIn(col, row)
                        landOn(actor, platform?.y ?: row * grid.tile, platform)
                        return
                    }
                    col++
                }
                row++
            }
        } else if (actor.vy < 0f) {
            var row = rowTo
            while (row >= rowFrom) {
                var col = colFrom
                while (col <= colTo) {
                    if (matchesCell(grid.collisionCell(col, row), config.overlapFilter)) {
                        actor.y = (row + 1) * grid.tile
                        actor.vy = 0f
                        return
                    }
                    col++
                }
                row--
            }
        }
    }

    private fun resolveOverlapOnGrid(
        actor: PhysicsActor,
        grid: LevelGrid,
        config: ActorPhysicsConfig,
        previousFeet: Float,
        colFrom: Int,
        colTo: Int,
        rowFrom: Int,
        rowTo: Int
    ) {
        if (actor.vy > 0f) {
            // Highest overlapped surface. A one-way surface only counts when the
            // feet crossed its top this tick — a side entry with feet already
            // below the top falls through instead of teleporting on top
            // (Stage 3, #35).
            var row = rowFrom
            while (row <= rowTo) {
                var col = colFrom
                while (col <= colTo) {
                    val cell = grid.collisionCell(col, row)
                    val platform = grid.platformIn(col, row)
                    val top = platform?.y ?: row * grid.tile
                    if (matchesCell(cell, config.landingFilter) &&
                        (cell != 2 ||
                            (top + LANDING_SLOP >= previousFeet &&
                                grid.collisionCell(col, row - 1) == 0))
                    ) {
                        landOn(actor, top, platform)
                        return
                    }
                    col++
                }
                row++
            }
        } else if (actor.vy < 0f) {
            // Rising: only solid cells block the underside — one-way never does.
            var row = rowTo
            while (row >= rowFrom) {
                var col = colFrom
                while (col <= colTo) {
                    if (grid.solidIn(col, row)) {
                        actor.y = (row + 1) * grid.tile
                        actor.vy = 0f
                        return
                    }
                    col++
                }
                row--
            }
        }
    }

    private fun resolveSnapToTopOnGrid(
        actor: PhysicsActor,
        grid: LevelGrid,
        config: ActorPhysicsConfig,
        colFrom: Int,
        colTo: Int,
        rowFrom: Int,
        rowTo: Int
    ) {
        // Unconditional snap onto the highest overlapped non-open surface; the
        // legacy loop neither grounds the actor nor reports a landing here.
        var row = rowFrom
        while (row <= rowTo) {
            var col = colFrom
            while (col <= colTo) {
                if (matchesCell(grid.collisionCell(col, row), config.landingFilter)) {
                    val platform = grid.platformIn(col, row)
                    actor.y = (platform?.y ?: row * grid.tile) - actor.h
                    actor.vy = 0f
                    return
                }
                col++
            }
            row++
        }
    }

    /** Cell twin of [matches]: 0 empty, 1 solid, 2 one-way. OPEN never rasterizes. */
    private fun matchesCell(cell: Int, filter: PlatformFilter): Boolean = when (filter) {
        PlatformFilter.SOLID_ONLY -> cell == 1
        PlatformFilter.NOT_OPEN -> cell != 0
        // The legacy list pass with ALL also matches OPEN platforms; no enemy or
        // boss ever overlaps an open gate, so the divergence is unreachable
        // (documented on the grid overload above).
        PlatformFilter.ALL -> cell != 0
    }

    /** Grid twin of the landOn below: snaps onto a cell top and reports the landing. */
    private fun landOn(actor: PhysicsActor, topY: Float, platform: Platform?) {
        actor.y = topY - actor.h
        actor.vy = 0f
        actor.grounded = true
        if (platform != null) actor.onLanded(platform)
    }

    /** Snaps the actor onto a platform top and reports the landing through the actor hook. */
    private fun landOn(actor: PhysicsActor, platform: Platform) {
        actor.y = platform.y - actor.h
        actor.vy = 0f
        actor.grounded = true
        actor.onLanded(platform)
    }

    private fun matches(collision: PlatformCollision, filter: PlatformFilter): Boolean {
        // Stage 9c (#62): an OPEN platform (an unsealed arena gate) collides with
        // nothing and no one — every filter skips it. This mirrors the tile grid,
        // which never rasterizes OPEN platforms, keeping the list reference and
        // the grid resolver indistinguishable (Stage 6b, #38).
        if (collision == PlatformCollision.OPEN) return false
        return when (filter) {
            PlatformFilter.SOLID_ONLY -> collision == PlatformCollision.SOLID
            PlatformFilter.NOT_OPEN -> collision != PlatformCollision.OPEN
            PlatformFilter.ALL -> true
        }
    }
}
