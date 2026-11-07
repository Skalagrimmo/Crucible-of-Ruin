package com.example.game

/**
 * Stage 7 (#27): the combo meter's confirmed semantics.
 *
 * The badge labels the NEXT AVAILABLE attack, not the swing the player just
 * completed: after hit 1 the meter reads "COMBO x2" (the next press swings
 * hit 2), after hit 2 it reads "FINISHER!" (the next press swings the crucible
 * heavy finisher), and the pips light up to and including that next stage.
 * The engine's comboIndex IS the next-available stage (0 = Slash 1,
 * 1 = Slash 2, 2 = Finisher), so the badge is a pure function of it plus the
 * reset window. The semantics were confirmed from the screen recording that
 * opened #27 — the apparent mismatch there was reading the label as a
 * completed-action counter — and are now pinned by ComboIndicatorTest.
 */
data class ComboBadge(
    val visible: Boolean,
    val label: String,
    val isFinisherReady: Boolean,
    val filledPips: Int
)

object ComboIndicator {

    /**
     * Maps the engine's combo state onto the on-screen badge. The badge shows
     * only while the combo reset window is open (comboResetTimer > 0).
     */
    fun badgeFor(comboIndex: Int, comboResetTimer: Float): ComboBadge {
        val visible = comboResetTimer > 0f
        val finisherReady = comboIndex == 2
        val label = if (finisherReady) "FINISHER!" else "COMBO x" + (comboIndex + 1)
        return ComboBadge(
            visible = visible,
            label = label,
            isFinisherReady = finisherReady,
            filledPips = comboIndex + 1
        )
    }
}
