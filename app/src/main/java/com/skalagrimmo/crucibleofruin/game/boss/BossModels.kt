package com.skalagrimmo.crucibleofruin.game.boss

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

/**
 * Destructible sub-part of a multi-component boss (Stage 9a, #62).
 * Inspired by Contra Hard Corps / Gunstar Heroes: a boss is a structure of
 * independently targetable parts, each with its own HP; destroying a part
 * changes the fight via [BaseBoss.onComponentDestroyed].
 *
 * Component hits damage BOTH the part and the boss body, so overall boss HP
 * still governs death and HP-based phase progression. A boss with no
 * components keeps the exact single-entity behavior.
 */
class BossComponent(
    val id: String,
    val name: String,
    var offsetX: Float,
    var offsetY: Float,
    val width: Float,
    val height: Float,
    val maxHp: Float,
    /** Damage taken multiplier when the strike lands on this part. */
    val damageMultiplier: Float = 1.0f,
    /** Attack pattern ids this part powers; destroy the part to strip them. */
    val powersAttackIds: Set<String> = emptySet(),
    /** Renderer accent for the part (Stage 9b draws it in local coordinates). */
    val accentColor: Color = Color(0xFF8B2010)
) {
    var hp: Float = maxHp
    var destroyed: Boolean = false
    var hurtFlashTimer: Float = 0f

    /** True while the part is attached and can absorb / route hits. */
    val isAlive: Boolean
        get() = !destroyed

    fun update(dt: Float) {
        if (hurtFlashTimer > 0f) {
            hurtFlashTimer = (hurtFlashTimer - dt).coerceAtLeast(0f)
        }
    }

    /**
     * Checks if a world-space attack box intersects this attached part.
     * Mirrors the boss width by facing exactly like [WeakPoint.intersects].
     */
    fun intersects(
        boxX: Float, boxY: Float, boxW: Float, boxH: Float,
        bossX: Float, bossY: Float, bossW: Float, bossFacing: Int
    ): Boolean {
        if (destroyed) return false
        val (worldX, worldY) = worldOrigin(bossX, bossY, bossW, bossFacing)
        return boxX < worldX + width &&
                boxX + boxW > worldX &&
                boxY < worldY + height &&
                boxY + boxH > worldY
    }

    /** Absolute world-space bounding box: [x, y, width, height]. */
    fun getWorldBounds(bossX: Float, bossY: Float, bossW: Float, bossFacing: Int): FloatArray {
        val (worldX, worldY) = worldOrigin(bossX, bossY, bossW, bossFacing)
        return floatArrayOf(worldX, worldY, width, height)
    }

    /**
     * Applies damage to the part. Returns true when this hit destroyed it.
     */
    fun applyDamage(amount: Float): Boolean {
        if (destroyed) return false
        hp -= amount
        hurtFlashTimer = 0.15f
        if (hp <= 0f) {
            hp = 0f
            destroyed = true
            return true
        }
        return false
    }

    private fun worldOrigin(bossX: Float, bossY: Float, bossW: Float, bossFacing: Int): Pair<Float, Float> {
        val worldX = if (bossFacing > 0) {
            bossX + offsetX
        } else {
            bossX + (bossW - offsetX - width)
        }
        return Pair(worldX, bossY + offsetY)
    }
}
