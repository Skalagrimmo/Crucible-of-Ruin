package com.example.game.alchemy

import androidx.compose.ui.graphics.Color

enum class ResourceType(val displayName: String, val symbol: String, val color: Color) {
    SULFUR_ASH("Sulfur Ash", "🜍", Color(0xFFFFB039)),
    QUICKSILVER("Quicksilver", "☿", Color(0xFF64D8CB)),
    VITRIOL_CRYSTAL("Vitriol Crystal", "🜊", Color(0xFFB388FF))
}

data class AlchemyEffect(
    val id: String,
    val name: String,
    val description: String,
    val durationSeconds: Float,
    val remainingSeconds: Float = durationSeconds,
    val color: Color,
    val iconSymbol: String,
    val damageMultiplier: Float = 1.0f,
    val defenseMultiplier: Float = 1.0f,
    val speedMultiplier: Float = 1.0f,
    val instantHealAmount: Float = 0f,
    val bonusFlasks: Int = 0
) {
    val progress: Float
        get() = if (durationSeconds > 0f) (remainingSeconds / durationSeconds).coerceIn(0f, 1f) else 0f

    val isExpired: Boolean
        get() = remainingSeconds <= 0f
}

data class AlchemyRecipe(
    val id: String,
    val name: String,
    val description: String,
    val requiredSulfur: Int,
    val requiredMercury: Int,
    val requiredVitriol: Int,
    val createEffect: () -> AlchemyEffect
) {
    companion object {
        val ALL_RECIPES = listOf(
            AlchemyRecipe(
                id = "cinnabar_blade",
                name = "Cinnabar Blade",
                description = "+60% Melee Slash damage with searing alchemical flame (25s)",
                requiredSulfur = 2,
                requiredMercury = 1,
                requiredVitriol = 0,
                createEffect = {
                    AlchemyEffect(
                        id = "cinnabar_blade",
                        name = "Cinnabar Blade",
                        description = "Searing alchemical flame empowers sword strikes (+60% DMG)",
                        durationSeconds = 25f,
                        color = Color(0xFFFF5722),
                        iconSymbol = "⚔️",
                        damageMultiplier = 1.6f
                    )
                }
            ),
            AlchemyRecipe(
                id = "philosophers_aegis",
                name = "Philosopher's Aegis",
                description = "Hardens skin into alchemical stone, cutting incoming damage by 50% (20s)",
                requiredSulfur = 1,
                requiredMercury = 2,
                requiredVitriol = 0,
                createEffect = {
                    AlchemyEffect(
                        id = "philosophers_aegis",
                        name = "Philosopher's Aegis",
                        description = "Alchemical stone barrier reduces damage by 50%",
                        durationSeconds = 20f,
                        color = Color(0xFFFFD700),
                        iconSymbol = "🛡️",
                        defenseMultiplier = 0.5f
                    )
                }
            ),
            AlchemyRecipe(
                id = "swift_quicksilver",
                name = "Swift Quicksilver",
                description = "+35% Movement speed and nimble jump acceleration (20s)",
                requiredSulfur = 0,
                requiredMercury = 3,
                requiredVitriol = 0,
                createEffect = {
                    AlchemyEffect(
                        id = "swift_quicksilver",
                        name = "Swift Quicksilver",
                        description = "Fluid mercury enhances movement and jump reflexes (+35% SPD)",
                        durationSeconds = 20f,
                        color = Color(0xFF00E5FF),
                        iconSymbol = "⚡",
                        speedMultiplier = 1.35f
                    )
                }
            ),
            AlchemyRecipe(
                id = "elixir_transmutation",
                name = "Transmutation Elixir",
                description = "Instantly restores 40 HP and replenishes 2 Alchemical Flasks",
                requiredSulfur = 2,
                requiredMercury = 1,
                requiredVitriol = 1,
                createEffect = {
                    AlchemyEffect(
                        id = "elixir_transmutation",
                        name = "Transmutation Elixir",
                        description = "Instant restoration: +40 HP & +2 Volatile Flasks",
                        durationSeconds = 3f,
                        color = Color(0xFFB388FF),
                        iconSymbol = "🧪",
                        instantHealAmount = 40f,
                        bonusFlasks = 2
                    )
                }
            )
        )
    }
}
