package com.example.game.data

import android.content.Context
import android.content.SharedPreferences
import com.example.R
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class UserSaveRepository(private val context: Context) {
    private val firestore: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance(context.getString(R.string.firestore_database_id))
    }

    /**
     * Each account has its own local snapshot. Guest progress lives in a separate file so
     * signing into a second account cannot silently mix the two accounts' consumables.
     */
    fun getLocalUserSave(userId: String = ""): UserSave {
        val normalizedUserId = userId.trim()
        val prefs = preferencesFor(normalizedUserId)
        migrateLegacyPreferencesIfNeeded(normalizedUserId, prefs)

        return UserSave(
            userId = normalizedUserId,
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
     * Saves a local snapshot without changing the progress timestamp.
     * Device-only settings must not make an older offline progression snapshot look newer
     * than the cloud save. Progress timestamps are advanced by saveUserSave().
     */
    fun saveLocalUserSave(save: UserSave): UserSave {
        val normalized = save.copy(userId = save.userId.trim())
        writeLocalSnapshot(normalized)
        return normalized
    }

    private fun writeLocalSnapshot(save: UserSave) {
        val prefs = preferencesFor(save.userId)
        migrateLegacyPreferencesIfNeeded(save.userId, prefs)
        prefs.edit()
            .putString("user_id", save.userId)
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

    private fun preferencesFor(userId: String): SharedPreferences {
        val accountKey = userId.ifBlank { "guest" }
        return context.getSharedPreferences("${PREFERENCES_PREFIX}$accountKey", Context.MODE_PRIVATE)
    }

    private fun hasStoredSave(userId: String): Boolean {
        val prefs = preferencesFor(userId)
        migrateLegacyPreferencesIfNeeded(userId, prefs)
        return prefs.getBoolean(KEY_HAS_SAVE, false)
    }

    /**
     * Migrate the former single SharedPreferences file only to the matching owner.
     * Legacy guest data is never copied into a different signed-in account.
     */
    private fun migrateLegacyPreferencesIfNeeded(userId: String, target: SharedPreferences) {
        if (target.getBoolean(KEY_LEGACY_MIGRATED, false)) return

        val legacy = context.getSharedPreferences(LEGACY_PREFERENCES, Context.MODE_PRIVATE)
        val legacyValues = legacy.all
        if (legacyValues.isEmpty()) {
            target.edit().putBoolean(KEY_LEGACY_MIGRATED, true).apply()
            return
        }

        val legacyOwner = legacy.getString("user_id", "")?.trim().orEmpty()
        if (legacyOwner == userId) {
            val editor = target.edit()
            legacyValues.forEach { (key, value) ->
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
            editor.putBoolean(KEY_HAS_SAVE, true)
                .putBoolean(KEY_LEGACY_MIGRATED, true)
                .apply()
        } else {
            // This account has checked the legacy file; do not import another owner's data.
            target.edit().putBoolean(KEY_LEGACY_MIGRATED, true).apply()
        }
    }

    suspend fun getUserSave(userId: String): UserSave {
        val normalizedUserId = userId.trim()
        val local = getLocalUserSave(normalizedUserId)
        val localHasSave = hasStoredSave(normalizedUserId)
        if (normalizedUserId.isBlank()) return local

        return try {
            val snapshot = firestore.collection("users")
                .document(normalizedUserId)
                .get()
                .await()
            val cloud = if (snapshot.exists()) snapshot.toObject(UserSave::class.java) else null

            if (cloud != null) {
                val merged = mergeUserSaves(
                    local = local,
                    cloud = cloud,
                    userId = normalizedUserId,
                    localHasSave = localHasSave
                )
                val localProgressIsNewer = localHasSave && local.savedAt > cloud.savedAt
                if (localProgressIsNewer) {
                    // A newer offline snapshot wins and is pushed back to the cloud.
                    saveUserSave(merged)
                    getLocalUserSave(normalizedUserId)
                } else {
                    // Preserve the cloud snapshot timestamp; reading must not make stale local
                    // progression look newer than the cloud on the next launch.
                    writeLocalSnapshot(merged)
                    merged
                }
            } else {
                // First login: keep this account's local data. If it has none, import the
                // separate guest save once; never merge guest data into an existing cloud save.
                val seed = if (!localHasSave && hasStoredSave("")) {
                    getLocalUserSave("")
                } else {
                    local
                }
                val initial = seed.copy(userId = normalizedUserId)
                saveUserSave(initial)
                getLocalUserSave(normalizedUserId)
            }
        } catch (_: Exception) {
            // Local-first fallback remains usable when Firebase or the network is unavailable.
            local
        }
    }

    suspend fun saveUserSave(save: UserSave): Result<Unit> {
        val normalizedUserId = save.userId.trim()
        val persisted = save.copy(
            userId = normalizedUserId,
            savedAt = System.currentTimeMillis()
        )
        writeLocalSnapshot(persisted)
        if (normalizedUserId.isBlank()) return Result.success(Unit)

        return try {
            firestore.collection("users")
                .document(normalizedUserId)
                .set(persisted, SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private companion object {
        const val LEGACY_PREFERENCES = "crucible_save_prefs"
        const val PREFERENCES_PREFIX = "crucible_save_prefs_"
        const val KEY_HAS_SAVE = "_has_saved_snapshot"
        const val KEY_LEGACY_MIGRATED = "_legacy_migrated"
    }
}

/**
 * Merge monotonic achievements independently, but take consumable reagent counts from one
 * timestamped snapshot. Using maxOf for consumables restores ingredients after crafting.
 */
internal fun mergeUserSaves(
    local: UserSave,
    cloud: UserSave,
    userId: String,
    localHasSave: Boolean
): UserSave {
    val localIsNewer = localHasSave && local.savedAt > cloud.savedAt
    val progress = if (localIsNewer) local else cloud
    val deviceSettings = if (localHasSave) local else cloud

    val localBest = local.bestTimeSeconds
    val cloudBest = cloud.bestTimeSeconds
    val bestTime = when {
        localBest <= 0f -> cloudBest
        cloudBest <= 0f -> localBest
        else -> minOf(localBest, cloudBest)
    }

    return progress.copy(
        userId = userId,
        highScore = maxOf(local.highScore, cloud.highScore),
        bestTimeSeconds = bestTime,
        foesSlain = maxOf(local.foesSlain, cloud.foesSlain),
        lastCheckpoint = maxOf(local.lastCheckpoint, cloud.lastCheckpoint),
        savedAt = maxOf(local.savedAt, cloud.savedAt),
        targetFps = deviceSettings.targetFps,
        soundMuted = deviceSettings.soundMuted,
        hapticsEnabled = deviceSettings.hapticsEnabled,
        permanentVitality = maxOf(local.permanentVitality, cloud.permanentVitality),
        permanentDamage = maxOf(local.permanentDamage, cloud.permanentDamage),
        permanentSpeed = maxOf(local.permanentSpeed, cloud.permanentSpeed),
        permanentFlasks = maxOf(local.permanentFlasks, cloud.permanentFlasks),
        permanentDefense = maxOf(local.permanentDefense, cloud.permanentDefense),
        permanentVitriol = maxOf(local.permanentVitriol, cloud.permanentVitriol),
        savedSulfur = progress.savedSulfur,
        savedMercury = progress.savedMercury,
        savedVitriol = progress.savedVitriol,
        perfectDodges = maxOf(local.perfectDodges, cloud.perfectDodges)
    )
}
