package com.example.game.alchemy

import androidx.compose.ui.graphics.Color

/**
 * Types of permanent alchemical stat augmentations.
 */
enum class PermanentBuffType(
    val id: String,
    val title: String,
    val description: String,
    val icon: String,
    val color: Color,
    val statLabel: String,
    val maxRank: Int = 5
) {
    VITALITY_CORE(
        id = "vitality_core",
        title = "Vitality Vessel",
        description = "Permanently hardens alchemical bloodline (+25 Max HP per rank)",
        icon = "❤️",
        color = Color(0xFFFF3366),
        statLabel = "+25 Max HP"
    ),
    IGNIS_FERVOR(
        id = "ignis_fervor",
        title = "Ignis Blade Tempering",
        description = "Infuses weapons with eternal alchemical heat (+15% Melee DMG per rank)",
        icon = "⚔️",
        color = Color(0xFFFF6D00),
        statLabel = "+15% Melee DMG"
    ),
    QUICKSILVER_STRIDE(
        id = "quicksilver_stride",
        title = "Quicksilver Celerity",
        description = "Refines agility and neuromuscular impulse (+10% Speed per rank)",
        icon = "⚡",
        color = Color(0xFF00E5FF),
        statLabel = "+10% Move Speed"
    ),
    CRUCIBLE_VESSEL(
        id = "crucible_vessel",
        title = "Crucible Flask Belt",
        description = "Transmutes additional flask chambers (+1 Flask Capacity per rank)",
        icon = "🧪",
        color = Color(0xFF69F0AE),
        statLabel = "+1 Max Flasks"
    ),
    AEGIS_INFUSION(
        id = "aegis_infusion",
        title = "Adamantine Ward",
        description = "Reinforces skin with stone crystallization (+8% Damage Reduction per rank)",
        icon = "🛡️",
        color = Color(0xFFFFD700),
        statLabel = "+8% Damage Resist"
    ),
    VITRIOLIC_TOUCH(
        id = "vitriolic_touch",
        title = "Vitriolic Alcahest",
        description = "Hones strike precision against Boss weak points (+20% Crit DMG per rank)",
        icon = "✨",
        color = Color(0xFFD500F9),
        statLabel = "+20% Weak-Point Crit"
    )
}

/**
 * Recipe mapping a specific combination of 3 crucible reagents to a permanent stat buff.
 */
data class PermanentAlchemyRecipe(
    val id: String,
    val buffType: PermanentBuffType,
    val requiredSulfur: Int,
    val requiredMercury: Int,
    val requiredVitriol: Int
) {
    /**
     * Checks if a collection of 3 crucible ingredients matches this recipe.
     */
    fun matches(reagents: List<ResourceType>): Boolean {
        if (reagents.size != 3) return false
        val s = reagents.count { it == ResourceType.SULFUR_ASH }
        val m = reagents.count { it == ResourceType.QUICKSILVER }
        val v = reagents.count { it == ResourceType.VITRIOL_CRYSTAL }
        return s == requiredSulfur && m == requiredMercury && v == requiredVitriol
    }

    companion object {
        val ALL_PERMANENT_RECIPES = listOf(
            PermanentAlchemyRecipe(
                id = "perm_vitality",
                buffType = PermanentBuffType.VITALITY_CORE,
                requiredSulfur = 2,
                requiredMercury = 0,
                requiredVitriol = 1
            ),
            PermanentAlchemyRecipe(
                id = "perm_damage",
                buffType = PermanentBuffType.IGNIS_FERVOR,
                requiredSulfur = 2,
                requiredMercury = 1,
                requiredVitriol = 0
            ),
            PermanentAlchemyRecipe(
                id = "perm_speed",
                buffType = PermanentBuffType.QUICKSILVER_STRIDE,
                requiredSulfur = 1,
                requiredMercury = 2,
                requiredVitriol = 0
            ),
            PermanentAlchemyRecipe(
                id = "perm_flasks",
                buffType = PermanentBuffType.CRUCIBLE_VESSEL,
                requiredSulfur = 1,
                requiredMercury = 1,
                requiredVitriol = 1
            ),
            PermanentAlchemyRecipe(
                id = "perm_defense",
                buffType = PermanentBuffType.AEGIS_INFUSION,
                requiredSulfur = 0,
                requiredMercury = 1,
                requiredVitriol = 2
            ),
            PermanentAlchemyRecipe(
                id = "perm_vitriol",
                buffType = PermanentBuffType.VITRIOLIC_TOUCH,
                requiredSulfur = 1,
                requiredMercury = 0,
                requiredVitriol = 2
            )
        )

        fun findMatchingRecipe(reagents: List<ResourceType>): PermanentAlchemyRecipe? {
            return ALL_PERMANENT_RECIPES.firstOrNull { it.matches(reagents) }
        }
    }
}
