package com.example.game

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

    /** Snaps the actor onto a platform top and reports the landing through the actor hook. */
    private fun landOn(actor: PhysicsActor, platform: Platform) {
        actor.y = platform.y - actor.h
        actor.vy = 0f
        actor.grounded = true
        actor.onLanded(platform)
    }

    private fun matches(collision: PlatformCollision, filter: PlatformFilter): Boolean = when (filter) {
        PlatformFilter.SOLID_ONLY -> collision == PlatformCollision.SOLID
        PlatformFilter.NOT_OPEN -> collision != PlatformCollision.OPEN
        PlatformFilter.ALL -> true
    }
}
