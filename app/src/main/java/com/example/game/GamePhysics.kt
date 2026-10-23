package com.example.game

/**
 * Player velocity integration and axis-separated collision resolution against level
 * platforms. GameEngine still owns input intent, combat, statuses, hazards, and update
 * order; this module preserves the existing movement/collision calculations.
 */
internal object GamePhysics {
    private const val MAX_FALL_SPEED = 750f
    private const val COYOTE_TIME_AFTER_LANDING = 0.12f

    /**
     * Integrates vertical velocity, resolves horizontal movement first, then vertical
     * movement. The order is intentional: it matches the previous GameEngine loop.
     */
    fun movePlayer(player: PlayerEntity, platforms: List<Platform>, dt: Float) {
        player.vy += player.gravity * dt
        if (player.vy > MAX_FALL_SPEED) player.vy = MAX_FALL_SPEED

        // Physics X
        player.x += player.vx * dt
        for (platform in platforms) {
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

        // Physics Y
        player.y += player.vy * dt
        player.grounded = false
        for (platform in platforms) {
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
