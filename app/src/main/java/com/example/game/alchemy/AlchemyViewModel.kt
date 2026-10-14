package com.example.game.alchemy

import androidx.lifecycle.ViewModel
import com.example.game.data.UserSave
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class AlchemyViewModel : ViewModel() {

    // Player's alchemical reagent pouch
    private val _pouch = MutableStateFlow(
        mapOf(
            ResourceType.SULFUR_ASH to 3,
            ResourceType.QUICKSILVER to 2,
            ResourceType.VITRIOL_CRYSTAL to 1
        )
    )
    val pouch: StateFlow<Map<ResourceType, Int>> = _pouch.asStateFlow()

    // Temporary combat buffs
    private val _activeEffects = MutableStateFlow<List<AlchemyEffect>>(emptyList())
    val activeEffects: StateFlow<List<AlchemyEffect>> = _activeEffects.asStateFlow()

    // Permanent alchemical augmentations (rank 0 to 5)
    private val _permanentBuffRanks = MutableStateFlow(
        mapOf(
            PermanentBuffType.VITALITY_CORE to 0,
            PermanentBuffType.IGNIS_FERVOR to 0,
            PermanentBuffType.QUICKSILVER_STRIDE to 0,
            PermanentBuffType.CRUCIBLE_VESSEL to 0,
            PermanentBuffType.AEGIS_INFUSION to 0,
            PermanentBuffType.VITRIOLIC_TOUCH to 0
        )
    )
    val permanentBuffRanks: StateFlow<Map<PermanentBuffType, Int>> = _permanentBuffRanks.asStateFlow()

    // Drag-and-drop Crucible Slots (holds up to 3 reagents for permanent transmutation)
    private val _crucibleSlots = MutableStateFlow<List<ResourceType>>(emptyList())
    val crucibleSlots: StateFlow<List<ResourceType>> = _crucibleSlots.asStateFlow()

    // Resulting matched permanent recipe
    private val _matchedPermanentRecipe = MutableStateFlow<PermanentAlchemyRecipe?>(null)
    val matchedPermanentRecipe: StateFlow<PermanentAlchemyRecipe?> = _matchedPermanentRecipe.asStateFlow()

    // Temporary prompt when brewing an elixir or transmuting
    private val _craftingNotice = MutableStateFlow<String?>(null)
    val craftingNotice: StateFlow<String?> = _craftingNotice.asStateFlow()

    fun loadFromUserSave(save: UserSave) {
        _pouch.update {
            mapOf(
                ResourceType.SULFUR_ASH to maxOf(save.savedSulfur, it[ResourceType.SULFUR_ASH] ?: 0),
                ResourceType.QUICKSILVER to maxOf(save.savedMercury, it[ResourceType.QUICKSILVER] ?: 0),
                ResourceType.VITRIOL_CRYSTAL to maxOf(save.savedVitriol, it[ResourceType.VITRIOL_CRYSTAL] ?: 0)
            )
        }
        _permanentBuffRanks.update {
            mapOf(
                PermanentBuffType.VITALITY_CORE to save.permanentVitality,
                PermanentBuffType.IGNIS_FERVOR to save.permanentDamage,
                PermanentBuffType.QUICKSILVER_STRIDE to save.permanentSpeed,
                PermanentBuffType.CRUCIBLE_VESSEL to save.permanentFlasks,
                PermanentBuffType.AEGIS_INFUSION to save.permanentDefense,
                PermanentBuffType.VITRIOLIC_TOUCH to save.permanentVitriol
            )
        }
    }

    fun getResourceCount(type: ResourceType): Int {
        return _pouch.value[type] ?: 0
    }

    fun getPermanentRank(type: PermanentBuffType): Int {
        return _permanentBuffRanks.value[type] ?: 0
    }

    /**
     * Collects an alchemical reagent from vanquished foes or shattered urns.
     */
    fun collectResource(type: ResourceType, amount: Int = 1) {
        _pouch.update { current ->
            val existing = current[type] ?: 0
            current + (type to (existing + amount))
        }
    }

    /**
     * Adds a reagent to the Crucible from the pouch (via drag-and-drop or tap).
     */
    fun addReagentToCrucible(type: ResourceType): Boolean {
        val currentPouchCount = getResourceCount(type)
        if (currentPouchCount <= 0 || _crucibleSlots.value.size >= 3) {
            return false
        }

        // Deduct 1 from pouch and place into crucible slot
        _pouch.update { current ->
            val count = (current[type] ?: 0) - 1
            current + (type to maxOf(0, count))
        }
        _crucibleSlots.update { it + type }
        updateMatchedRecipe()
        return true
    }

    /**
     * Removes a reagent from a specific crucible slot and returns it to the pouch.
     */
    fun removeReagentFromCrucible(index: Int) {
        val currentSlots = _crucibleSlots.value
        if (index !in currentSlots.indices) return

        val removed = currentSlots[index]
        _crucibleSlots.update {
            it.toMutableList().apply { removeAt(index) }
        }
        _pouch.update { current ->
            val count = (current[removed] ?: 0) + 1
            current + (removed to count)
        }
        updateMatchedRecipe()
    }

    /**
     * Clears all crucible slots and returns all reagents to the pouch.
     */
    fun clearCrucible() {
        val currentSlots = _crucibleSlots.value
        if (currentSlots.isEmpty()) return

        for (res in currentSlots) {
            _pouch.update { current ->
                val count = (current[res] ?: 0) + 1
                current + (res to count)
            }
        }
        _crucibleSlots.value = emptyList()
        _matchedPermanentRecipe.value = null
    }

    /**
     * Fills the crucible slots directly with the required reagents for a recipe if the player has them.
     */
    fun autoFillRecipe(recipe: PermanentAlchemyRecipe): Boolean {
        val neededSulfur = recipe.requiredSulfur
        val neededMercury = recipe.requiredMercury
        val neededVitriol = recipe.requiredVitriol

        // Total available in pouch + currently in crucible
        val currentInCrucible = _crucibleSlots.value
        val totalSulfur = getResourceCount(ResourceType.SULFUR_ASH) + currentInCrucible.count { it == ResourceType.SULFUR_ASH }
        val totalMercury = getResourceCount(ResourceType.QUICKSILVER) + currentInCrucible.count { it == ResourceType.QUICKSILVER }
        val totalVitriol = getResourceCount(ResourceType.VITRIOL_CRYSTAL) + currentInCrucible.count { it == ResourceType.VITRIOL_CRYSTAL }

        if (totalSulfur < neededSulfur || totalMercury < neededMercury || totalVitriol < neededVitriol) {
            return false
        }

        // Return everything to pouch first, then take exact recipe components
        clearCrucible()
        repeat(neededSulfur) { addReagentToCrucible(ResourceType.SULFUR_ASH) }
        repeat(neededMercury) { addReagentToCrucible(ResourceType.QUICKSILVER) }
        repeat(neededVitriol) { addReagentToCrucible(ResourceType.VITRIOL_CRYSTAL) }
        return true
    }

    private fun updateMatchedRecipe() {
        val slots = _crucibleSlots.value
        _matchedPermanentRecipe.value = if (slots.size == 3) {
            PermanentAlchemyRecipe.findMatchingRecipe(slots)
        } else {
            null
        }
    }

    /**
     * Transmutes the combined reagents in the crucible into a permanent stat buff.
     * Consumes the 3 placed reagents and increments the corresponding permanent buff rank.
     */
    fun transmutePermanentBuff(): PermanentBuffType? {
        val recipe = _matchedPermanentRecipe.value ?: return null
        val buffType = recipe.buffType
        val currentRank = getPermanentRank(buffType)

        if (currentRank >= buffType.maxRank) {
            _craftingNotice.value = "${buffType.title} is already at MAX Rank!"
            return null
        }

        // Permanently consume items in crucible
        _crucibleSlots.value = emptyList()
        _matchedPermanentRecipe.value = null

        // Increment permanent rank
        _permanentBuffRanks.update { current ->
            current + (buffType to (currentRank + 1))
        }

        val newRank = currentRank + 1
        _craftingNotice.value = "Transmuted: ${buffType.title} (Rank $newRank/$buffType.maxRank)!"
        return buffType
    }

    // --- Temporary Combat Elixirs Crafting ---
    fun canCraft(recipe: AlchemyRecipe): Boolean {
        val currentSulfur = getResourceCount(ResourceType.SULFUR_ASH)
        val currentMercury = getResourceCount(ResourceType.QUICKSILVER)
        val currentVitriol = getResourceCount(ResourceType.VITRIOL_CRYSTAL)

        return currentSulfur >= recipe.requiredSulfur &&
                currentMercury >= recipe.requiredMercury &&
                currentVitriol >= recipe.requiredVitriol
    }

    fun craft(recipe: AlchemyRecipe): AlchemyEffect? {
        if (!canCraft(recipe)) return null

        _pouch.update { current ->
            val sulfur = (current[ResourceType.SULFUR_ASH] ?: 0) - recipe.requiredSulfur
            val mercury = (current[ResourceType.QUICKSILVER] ?: 0) - recipe.requiredMercury
            val vitriol = (current[ResourceType.VITRIOL_CRYSTAL] ?: 0) - recipe.requiredVitriol

            mapOf(
                ResourceType.SULFUR_ASH to maxOf(0, sulfur),
                ResourceType.QUICKSILVER to maxOf(0, mercury),
                ResourceType.VITRIOL_CRYSTAL to maxOf(0, vitriol)
            )
        }

        val effect = recipe.createEffect()
        _activeEffects.update { current ->
            val filtered = current.filter { it.id != effect.id }
            filtered + effect
        }

        _craftingNotice.value = "Brewed: ${recipe.name}!"
        return effect
    }

    fun clearCraftingNotice() {
        _craftingNotice.value = null
    }

    fun update(dt: Float) {
        if (_activeEffects.value.isEmpty()) return

        _activeEffects.update { current ->
            current.mapNotNull { effect ->
                val nextRemaining = effect.remainingSeconds - dt
                if (nextRemaining > 0f) {
                    effect.copy(remainingSeconds = nextRemaining)
                } else {
                    null
                }
            }
        }
    }

    // --- Multipliers combining Temporary Elixirs + Permanent Augmentations ---
    fun getTotalDamageMultiplier(): Float {
        var mult = 1.0f + (getPermanentRank(PermanentBuffType.IGNIS_FERVOR) * 0.15f)
        for (effect in _activeEffects.value) {
            mult *= effect.damageMultiplier
        }
        return mult
    }

    fun getTotalDefenseMultiplier(): Float {
        var mult = 1.0f - (getPermanentRank(PermanentBuffType.AEGIS_INFUSION) * 0.08f)
        for (effect in _activeEffects.value) {
            mult *= effect.defenseMultiplier
        }
        return mult.coerceAtLeast(0.2f)
    }

    fun getTotalSpeedMultiplier(): Float {
        var mult = 1.0f + (getPermanentRank(PermanentBuffType.QUICKSILVER_STRIDE) * 0.10f)
        for (effect in _activeEffects.value) {
            mult *= effect.speedMultiplier
        }
        return mult
    }

    fun getPermanentBonusMaxHp(): Float {
        return getPermanentRank(PermanentBuffType.VITALITY_CORE) * 25f
    }

    fun getPermanentBonusMaxFlasks(): Int {
        return getPermanentRank(PermanentBuffType.CRUCIBLE_VESSEL)
    }

    fun getPermanentWeakPointCritBonus(): Float {
        return getPermanentRank(PermanentBuffType.VITRIOLIC_TOUCH) * 0.20f
    }

    // Backwards-compatible methods
    fun getDamageMultiplier(): Float = getTotalDamageMultiplier()
    fun getDefenseMultiplier(): Float = getTotalDefenseMultiplier()
    fun getSpeedMultiplier(): Float = getTotalSpeedMultiplier()

    fun reset() {
        _activeEffects.value = emptyList()
        clearCrucible()
        _craftingNotice.value = null
    }
}
