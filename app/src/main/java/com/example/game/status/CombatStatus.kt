package com.example.game.status

import androidx.compose.ui.graphics.Color

/**
 * Types of temporary combat status effects (debuffs).
 */
enum class CombatStatusType(
    val id: String,
    val displayName: String,
    val iconSymbol: String,
    val primaryColor: Color,
    val secondaryColor: Color,
    val baseDps: Float,
    val baseDuration: Float,
    val maxStacks: Int = 3,
    val description: String
) {
    ALCHEMY_BURN(
        id = "alchemy_burn",
        displayName = "Alchemy-Burn",
        iconSymbol = "🔥",
        primaryColor = Color(0xFFFF5722),
        secondaryColor = Color(0xFFFFC107),
        baseDps = 14f,
        baseDuration = 5.0f,
        maxStacks = 3,
        description = "Searing alchemical flame inflicting periodic damage over time."
    ),
    CORROSION(
        id = "corrosion",
        displayName = "Corrosion",
        iconSymbol = "🧪",
        primaryColor = Color(0xFF76FF03),
        secondaryColor = Color(0xFFB388FF),
        baseDps = 5f,
        baseDuration = 6.0f,
        maxStacks = 3,
        description = "Caustic vitriol dissolving armor (+30% DMG taken) and slowing movement by 20%."
    )
}

/**
 * Active combat status effect instance tracking remaining duration, stacks, and DoT timers.
 */
data class ActiveCombatStatus(
    val type: CombatStatusType,
    var duration: Float = type.baseDuration,
    var remaining: Float = duration,
    var stacks: Int = 1,
    var dps: Float = type.baseDps,
    var tickTimer: Float = 0.5f,
    val tickInterval: Float = 0.5f
) {
    val progress: Float
        get() = if (duration > 0f) (remaining / duration).coerceIn(0f, 1f) else 0f

    val isExpired: Boolean
        get() = remaining <= 0f

    fun refresh(addDuration: Float, addStacks: Int = 1) {
        remaining = maxOf(remaining, addDuration)
        duration = maxOf(duration, remaining)
        stacks = (stacks + addStacks).coerceAtMost(type.maxStacks)
    }
}

/**
 * Controller managing combat status debuffs for an entity (Player, Boss, or Enemy).
 * Handles stacking, tick interval calculation, stat penalties, and cleanup.
 */
class StatusEffectController {
    private val _effects = mutableListOf<ActiveCombatStatus>()

    fun getActiveEffects(): List<ActiveCombatStatus> = _effects

    fun hasEffect(type: CombatStatusType): Boolean {
        return _effects.any { it.type == type && !it.isExpired }
    }

    fun getEffect(type: CombatStatusType): ActiveCombatStatus? {
        return _effects.firstOrNull { it.type == type && !it.isExpired }
    }

    /**
     * Applies or refreshes a debuff on this entity.
     */
    fun applyEffect(
        type: CombatStatusType,
        duration: Float = type.baseDuration,
        dpsMultiplier: Float = 1.0f,
        stacks: Int = 1
    ) {
        val existing = getEffect(type)
        if (existing != null) {
            existing.refresh(duration, stacks)
            existing.dps = maxOf(existing.dps, type.baseDps * dpsMultiplier)
        } else {
            _effects.add(
                ActiveCombatStatus(
                    type = type,
                    duration = duration,
                    remaining = duration,
                    stacks = stacks.coerceIn(1, type.maxStacks),
                    dps = type.baseDps * dpsMultiplier
                )
            )
        }
    }

    /**
     * Updates all status debuffs by dt.
     * Invokes [onTick] whenever a damage-over-time tick occurs.
     */
    fun update(
        dt: Float,
        onTick: (type: CombatStatusType, damage: Float, stacks: Int) -> Unit
    ) {
        if (_effects.isEmpty()) return

        val iterator = _effects.iterator()
        while (iterator.hasNext()) {
            val eff = iterator.next()
            eff.remaining -= dt

            if (eff.remaining <= 0f) {
                iterator.remove()
                continue
            }

            eff.tickTimer -= dt
            if (eff.tickTimer <= 0f) {
                eff.tickTimer = eff.tickInterval
                val tickDamage = (eff.dps * eff.stacks) * eff.tickInterval
                onTick(eff.type, tickDamage, eff.stacks)
            }
        }
    }

    /**
     * Multiplier applied to incoming damage taken (e.g. +30% per Corrosion stack).
     */
    fun getIncomingDamageMultiplier(): Float {
        val corrosion = getEffect(CombatStatusType.CORROSION) ?: return 1.0f
        return 1.0f + (0.30f * corrosion.stacks)
    }

    /**
     * Multiplier applied to movement speed (e.g. -20% per Corrosion stack).
     */
    fun getSpeedMultiplier(): Float {
        val corrosion = getEffect(CombatStatusType.CORROSION) ?: return 1.0f
        return (1.0f - (0.20f * corrosion.stacks)).coerceAtLeast(0.45f)
    }

    fun clear() {
        _effects.clear()
    }
}
