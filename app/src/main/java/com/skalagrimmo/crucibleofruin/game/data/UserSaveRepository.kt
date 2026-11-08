package com.skalagrimmo.crucibleofruin.game.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Device-local save storage: one save slot held in SharedPreferences.
 *
 * Storage history this repository migrates from, in order:
 * 1. The original single-file slot ("crucible_save_prefs").
 * 2. The Firebase-era per-account slots ("crucible_save_prefs_<uid>" and
 *    "crucible_save_prefs_guest") used while cloud sync existed.
 * When the local slot is still empty, the freshest legacy snapshot (by
 * saved_at) is adopted once, so upgrading past the removal of cloud saves
 * never destroys device progress.
 */
class UserSaveRepository(private val context: Context) {

    fun getLocalUserSave(): UserSave {
        val prefs = preferences()
        migrateLegacySnapshotsIfNeeded(prefs)

        return UserSave(
            displayName = prefs.getString("display_name", "Alchemist") ?: "Alchemist",
            highScore = prefs.getInt("high_score", 0),
            bestTimeSeconds = prefs.getFloat("best_time", 0f),
            foesSlain = prefs.getInt("foes_slain", 0),
            lastCheckpoint = prefs.getInt("last_checkpoint", 0),
            savedAt = prefs.getLong("saved_at", 0L),
            targetFps = prefs.getInt("target_fps", 30),
            soundMuted = prefs.getBoolean("sound_muted", false),
            hapticsEnabled = prefs.getBoolean("haptics_enabled", true),
            permanentVitality = prefs.getInt("perm_vitality", 0),
            permanentDamage = prefs.getInt("perm_damage", 0),
            permanentSpeed = prefs.getInt("perm_speed", 0),
            permanentFlasks = prefs.getInt("perm_flasks", 0),
            permanentDefense = prefs.getInt("perm_defense", 0),
            permanentVitriol = prefs.getInt("perm_vitriol", 0),
            savedSulfur = prefs.getInt("saved_sulfur", 3),
            savedMercury = prefs.getInt("saved_mercury", 2),
            savedVitriol = prefs.getInt("saved_vitriol", 1),
            perfectDodges = prefs.getInt("perfect_dodges", 0)
        )
    }

    /**
     * Saves a local snapshot without advancing the progress timestamp, for
     * device-only settings (FPS cap, audio, haptics) that must not make an
     * older progress snapshot look newer than it is.
     */
    fun saveLocalUserSave(save: UserSave): UserSave {
        writeLocalSnapshot(save)
        return save
    }

    /** Reads the save. Kept as the progression entry point callers already use. */
    fun getUserSave(): UserSave = getLocalUserSave()

    /** Persists progress and advances the progress timestamp. */
    fun saveUserSave(save: UserSave): UserSave {
        val persisted = save.copy(savedAt = System.currentTimeMillis())
        writeLocalSnapshot(persisted)
        return persisted
    }

    private fun writeLocalSnapshot(save: UserSave) {
        val prefs = preferences()
        migrateLegacySnapshotsIfNeeded(prefs)
        prefs.edit()
            .putString("display_name", save.displayName)
            .putInt("high_score", save.highScore)
            .putFloat("best_time", save.bestTimeSeconds)
            .putInt("foes_slain", save.foesSlain)
            .putInt("last_checkpoint", save.lastCheckpoint)
            .putLong("saved_at", save.savedAt)
            .putInt("target_fps", save.targetFps)
            .putBoolean("sound_muted", save.soundMuted)
            .putBoolean("haptics_enabled", save.hapticsEnabled)
            .putInt("perm_vitality", save.permanentVitality)
            .putInt("perm_damage", save.permanentDamage)
            .putInt("perm_speed", save.permanentSpeed)
            .putInt("perm_flasks", save.permanentFlasks)
            .putInt("perm_defense", save.permanentDefense)
            .putInt("perm_vitriol", save.permanentVitriol)
            .putInt("saved_sulfur", save.savedSulfur)
            .putInt("saved_mercury", save.savedMercury)
            .putInt("saved_vitriol", save.savedVitriol)
            .putInt("perfect_dodges", save.perfectDodges)
            .putBoolean(KEY_HAS_SAVE, true)
            .apply()
    }

    private fun preferences(): SharedPreferences =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    private fun hasStoredSave(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_HAS_SAVE, false)

    private fun migrateLegacySnapshotsIfNeeded(target: SharedPreferences) {
        if (target.getBoolean(KEY_MIGRATED, false)) return

        if (!hasStoredSave(target)) {
            val candidates = legacySnapshotCandidates()
            val freshest = candidates.maxByOrNull { (_, values) ->
                (values["saved_at"] as? Long) ?: 0L
            }
            if (freshest != null) {
                val editor = target.edit()
                freshest.second.forEach { (key, value) ->
                    when (value) {
                        is String -> editor.putString(key, value)
                        is Int -> editor.putInt(key, value)
                        is Long -> editor.putLong(key, value)
                        is Float -> editor.putFloat(key, value)
                        is Boolean -> editor.putBoolean(key, value)
                        is Set<*> -> editor.putStringSet(
                            key,
                            value.filterIsInstance<String>().toMutableSet()
                        )
                    }
                }
                editor.putBoolean(KEY_HAS_SAVE, true).commit()
            }
        }
        target.edit().putBoolean(KEY_MIGRATED, true).commit()
    }

    /**
     * Collects every legacy snapshot still present on the device: the original
     * single file plus each Firebase-era per-account/guest file.
     */
    private fun legacySnapshotCandidates(): Map<String, Map<String, Any>> {
        val candidates = mutableMapOf<String, Map<String, Any>>()

        val legacy = context.getSharedPreferences(LEGACY_PREFERENCES, Context.MODE_PRIVATE).all
        if (legacy.isNotEmpty()) candidates[LEGACY_PREFERENCES] = legacy

        val sharedPrefsDir = context.filesDir.parentFile?.resolve("shared_prefs")
        sharedPrefsDir?.listFiles { file ->
            file.isFile &&
                file.name.startsWith(LEGACY_PREFIX) &&
                file.name != PREFERENCES + ".xml"
        }?.forEach { file ->
            val name = file.name.removeSuffix(".xml")
            if (name != PREFERENCES) {
                val values = context.getSharedPreferences(name, Context.MODE_PRIVATE).all
                if (values.isNotEmpty()) candidates[name] = values
            }
        }

        return candidates
    }

    private companion object {
        const val LEGACY_PREFERENCES = "crucible_save_prefs"
        const val LEGACY_PREFIX = "crucible_save_prefs_"
        const val PREFERENCES = "crucible_save_prefs_local"
        const val KEY_HAS_SAVE = "_has_saved_snapshot"
        const val KEY_MIGRATED = "_legacy_migrated"
    }
}
