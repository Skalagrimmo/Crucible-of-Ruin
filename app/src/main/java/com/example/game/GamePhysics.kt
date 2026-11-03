package com.example.game

/**
 * Player velocity integration and axis-separated collision resolution against level
 * platforms. Platform collision behavior is data-driven:
 * - SOLID blocks horizontal movement and both vertical directions.
 * - ONE_WAY only catches a descending player crossing its top surface.
 *
 * GameEngine still owns input intent, combat, statuses, hazards, and update order.
 */
internal object GamePhysics {
    private const val MAX_FALL_SPEED = 750f
    private const val COYOTE_TIME_AFTER_LANDING = 0.12f
    private const val LANDING_SLOP = 0.5f

    /**
     * Integrates vertical velocity, resolves horizontal movement first, then vertical
     * movement. One-way platforms are ignored on the X axis and when moving upward.
     */
    fun movePlayer(player: PlayerEntity, platforms: List<Platform>, dt: Float) {
        player.vy += player.gravity * dt
        if (player.vy > MAX_FALL_SPEED) player.vy = MAX_FALL_SPEED

        // Physics X: only solid platforms have side walls.
        player.x += player.vx * dt
        for (platform in platforms) {
            if (platform.collision == PlatformCollision.ONE_WAY) continue

            if (aabbIntersects(
                    player.x, player.y, player.w, player.h,
                    platform.x, platform.y, platform.w, platform.h
                )
            ) {
                if (player.vx > 0f) {
                    player.x = platform.x - player.w
                } else if (player.vx < 0f) {
                    player.x = platform.x + platform.w
                }
                player.vx = 0f
            }
        }

        // Physics Y: remember the previous feet position before applying this frame's motion.
        val previousFeet = player.y + player.h
        player.y += player.vy * dt
        val currentFeet = player.y + player.h
        player.grounded = false

        // Find the first top surface crossed while descending. This swept check also
        // catches thin ledges when a low frame rate moves the player across the surface.
        if (player.vy > 0f) {
            var landingTop: Float? = null
            for (platform in platforms) {
                val overlapsX = player.x < platform.x + platform.w &&
                    player.x + player.w > platform.x
                val crossedTop = previousFeet <= platform.y + LANDING_SLOP &&
                    currentFeet >= platform.y

                if (overlapsX && crossedTop && (landingTop == null || platform.y < landingTop)) {
                    landingTop = platform.y
                }
            }

            if (landingTop != null) {
                player.y = landingTop - player.h
                player.vy = 0f
                player.grounded = true
                player.coyoteTime = COYOTE_TIME_AFTER_LANDING
            }
        }

        // Solid platforms still block the underside and resolve any existing overlap.
        // One-way surfaces intentionally do not participate in this pass.
        if (!player.grounded) {
            for (platform in platforms) {
                if (platform.collision == PlatformCollision.ONE_WAY) continue

                if (aabbIntersects(
                        player.x, player.y, player.w, player.h,
                        platform.x, platform.y, platform.w, platform.h
                    )
                ) {
                    if (player.vy > 0f) {
                        player.y = platform.y - player.h
                        player.vy = 0f
                        player.grounded = true
                        player.coyoteTime = COYOTE_TIME_AFTER_LANDING
                    } else if (player.vy < 0f) {
                        player.y = platform.y + platform.h
                        player.vy = 0f
                    }
                }
            }
        }
    }
}
