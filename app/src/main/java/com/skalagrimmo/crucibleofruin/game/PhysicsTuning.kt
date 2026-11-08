package com.skalagrimmo.crucibleofruin.game

/**
 * The designer's ruler (Stage 5, #37): a single source of truth for the
 * player's movement constants. Gameplay code and the level-reachability tests
 * read the same values, so the level's readability is enforced by measurement
 * instead of hand-tuning.
 *
 * Derived numbers with the stock values:
 *  - jump apex  v^2/(2g) = 500^2 / 2100 = 119 px
 *  - air time   2v/g    = 1000 / 1050 = 0.952 s
 *  - jump reach vx*t    = 160 * 0.952 = 152 px
 */
object PhysicsTuning {
    /** Magnitude of the jump impulse; applied as -JUMP_FORCE. */
    const val JUMP_FORCE = 500f

    /** Gravity acting on the player, px/s^2. */
    const val GRAVITY = 1050f

    /** Top speed while grounded, px/s. */
    const val RUN_SPEED = 190f

    /** Top speed while airborne — caps the horizontal reach of a jump, px/s. */
    const val AIR_SPEED = 160f

    /** Terminal fall speed of the shared resolver, px/s. */
    const val TERMINAL_VELOCITY = 750f

    /** Apex height of a base jump: v^2 / (2g) = 119 px. */
    val JUMP_HEIGHT: Float = JUMP_FORCE * JUMP_FORCE / (2f * GRAVITY)

    /** Full air time of a base jump: 2v / g = 0.952 s. */
    val JUMP_AIR_TIME: Float = 2f * JUMP_FORCE / GRAVITY

    /** Horizontal reach of a base jump: air speed * air time = 152 px. */
    val JUMP_REACH: Float = AIR_SPEED * JUMP_AIR_TIME
}
