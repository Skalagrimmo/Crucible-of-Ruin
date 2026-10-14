package com.example.game.boss

import androidx.compose.ui.graphics.Color

/**
 * High-level state of a Boss entity.
 */
enum class BossState {
    IDLE,
    TELEGRAPHING,
    ATTACKING,
    RECOVERING,
    PHASE_TRANSITION,
    STAGGERED,
    DEAD
}

/**
 * Phases of a boss encounter with increasing intensity and combat pacing.
 */
enum class BossPhase(
    val phaseIndex: Int,
    val title: String,
    val speedMultiplier: Float,
    val cooldownMultiplier: Float,
    val accentColor: Color
) {
    PHASE_1(
        phaseIndex = 1,
        title = "PHASE I: AWAKENED",
        speedMultiplier = 1.0f,
        cooldownMultiplier = 1.0f,
        accentColor = Color(0xFFFFD700)
    ),
    PHASE_2(
        phaseIndex = 2,
        title = "PHASE II: MOLTEN FURY",
        speedMultiplier = 1.25f,
        cooldownMultiplier = 0.75f,
        accentColor = Color(0xFFFF4500)
    ),
    PHASE_3(
        phaseIndex = 3,
        title = "PHASE III: CRUCIBLE OVERLOAD",
        speedMultiplier = 1.45f,
        cooldownMultiplier = 0.55f,
        accentColor = Color(0xFFC020FF)
    )
}

/**
 * Types of telegraphed attacks with distinct warning visualizations.
 */
enum class TelegraphType {
    DASH_LUNGE,      // Horizontal linear charging lane
    GROUND_SLAM,     // Impact circle with shockwaves
    MAGMA_MORTAR,    // Arc artillery volley with aerial warning
    VOID_ERUPTION    // Ground hazard eruption zone
}

/**
 * Definition of a telegraphed attack pattern.
 */
data class BossAttackPattern(
    val id: String,
    val name: String,
    val type: TelegraphType,
    val telegraphDuration: Float,
    val activeDuration: Float,
    val recoveryDuration: Float,
    val baseDamage: Float,
    val exposesWeakPoint: Boolean = true,
    val warningWidth: Float = 140f,
    val warningHeight: Float = 30f,
    val warningColor: Color = Color(0xFFFF3020)
)

/**
 * Dedicated weak-point target on a boss entity.
 * Striking this zone yields amplified damage and accumulates stagger to stun the boss.
 */
class WeakPoint(
    val id: String = "core",
    val name: String = "Alchemical Core",
    var offsetX: Float,
    var offsetY: Float,
    val width: Float = 22f,
    val height: Float = 22f,
    val damageMultiplier: Float = 2.2f,
    val maxStaggerHealth: Float = 75f
) {
    var isExposed: Boolean = true
    var staggerHealth: Float = maxStaggerHealth
    var glowPulseTimer: Float = 0f
    var isBroken: Boolean = false

    /**
     * Updates pulse animation timer.
     */
    fun update(dt: Float) {
        glowPulseTimer += dt * 4.5f
    }

    /**
     * Checks if a world-space attack box intersects this weak point.
     * Takes boss position and facing direction into account.
     */
    fun intersects(
        boxX: Float, boxY: Float, boxW: Float, boxH: Float,
        bossX: Float, bossY: Float, bossW: Float, bossFacing: Int
    ): Boolean {
        if (!isExposed) return false
        val wpWorldX = if (bossFacing > 0) {
            bossX + offsetX
        } else {
            bossX + (bossW - offsetX - width)
        }
        val wpWorldY = bossY + offsetY

        return boxX < wpWorldX + width &&
                boxX + boxW > wpWorldX &&
                boxY < wpWorldY + height &&
                boxY + boxH > wpWorldY
    }

    /**
     * Calculates absolute world-space bounding box.
     */
    fun getWorldBounds(bossX: Float, bossY: Float, bossW: Float, bossFacing: Int): FloatArray {
        val wpX = if (bossFacing > 0) {
            bossX + offsetX
        } else {
            bossX + (bossW - offsetX - width)
        }
        val wpY = bossY + offsetY
        return floatArrayOf(wpX, wpY, width, height)
    }

    /**
     * Applies damage to weak point and decrements stagger meter.
     * Returns true if this hit broke the stagger threshold.
     */
    fun applyWeakPointDamage(amount: Float): Boolean {
        staggerHealth -= amount
        if (staggerHealth <= 0f && !isBroken) {
            isBroken = true
            staggerHealth = 0f
            return true
        }
        return false
    }

    /**
     * Resets stagger meter after recovery.
     */
    fun resetStagger() {
        staggerHealth = maxStaggerHealth
        isBroken = false
    }
}
