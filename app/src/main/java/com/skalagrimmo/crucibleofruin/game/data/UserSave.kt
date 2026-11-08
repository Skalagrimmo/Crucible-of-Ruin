package com.skalagrimmo.crucibleofruin.game.data

/**
 * Device-local player save. There are no accounts and no cloud sync; progress
 * survives device transfers through Android's OS-level backup and the manual
 * JSON export/import flow (see [SaveFileCodec]).
 */
data class UserSave(
    val displayName: String = "Alchemist",
    val highScore: Int = 0,
    val bestTimeSeconds: Float = 0f,
    val foesSlain: Int = 0,
    val lastCheckpoint: Int = 0,
    val savedAt: Long = System.currentTimeMillis(),
    val targetFps: Int = 30,
    val soundMuted: Boolean = false,
    val hapticsEnabled: Boolean = true,
    // Permanent Alchemical Augmentations
    val permanentVitality: Int = 0,
    val permanentDamage: Int = 0,
    val permanentSpeed: Int = 0,
    val permanentFlasks: Int = 0,
    val permanentDefense: Int = 0,
    val permanentVitriol: Int = 0,
    // Persistent Reagents
    val savedSulfur: Int = 3,
    val savedMercury: Int = 2,
    val savedVitriol: Int = 1,
    // Combat Mastery Statistics
    val perfectDodges: Int = 0
)
