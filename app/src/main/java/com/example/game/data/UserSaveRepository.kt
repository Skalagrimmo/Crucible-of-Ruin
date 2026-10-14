package com.example.game.data

import android.content.Context
import com.example.R
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class UserSaveRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("crucible_save_prefs", Context.MODE_PRIVATE)

    private val firestore: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance(context.getString(R.string.firestore_database_id))
    }

    fun getLocalUserSave(): UserSave {
        return UserSave(
            userId = prefs.getString("user_id", "") ?: "",
            displayName = prefs.getString("display_name", "Alchemist") ?: "Alchemist",
            highScore = prefs.getInt("high_score", 0),
            bestTimeSeconds = prefs.getFloat("best_time", 0f),
            foesSlain = prefs.getInt("foes_slain", 0),
            lastCheckpoint = prefs.getInt("last_checkpoint", 0),
            savedAt = prefs.getLong("saved_at", System.currentTimeMillis()),
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
            savedVitriol = prefs.getInt("saved_vitriol", 1)
        )
    }

    fun saveLocalUserSave(save: UserSave) {
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
            .apply()
    }

    suspend fun getUserSave(userId: String): UserSave {
        val local = getLocalUserSave()
        if (userId.isBlank()) return local

        return try {
            val snapshot = firestore.collection("users").document(userId).get().await()
            if (snapshot.exists()) {
                val cloud = snapshot.toObject(UserSave::class.java)
                if (cloud != null) {
                    val merged = cloud.copy(
                        targetFps = local.targetFps,
                        soundMuted = local.soundMuted,
                        hapticsEnabled = local.hapticsEnabled,
                        highScore = maxOf(local.highScore, cloud.highScore),
                        lastCheckpoint = maxOf(local.lastCheckpoint, cloud.lastCheckpoint),
                        foesSlain = maxOf(local.foesSlain, cloud.foesSlain),
                        permanentVitality = maxOf(local.permanentVitality, cloud.permanentVitality),
                        permanentDamage = maxOf(local.permanentDamage, cloud.permanentDamage),
                        permanentSpeed = maxOf(local.permanentSpeed, cloud.permanentSpeed),
                        permanentFlasks = maxOf(local.permanentFlasks, cloud.permanentFlasks),
                        permanentDefense = maxOf(local.permanentDefense, cloud.permanentDefense),
                        permanentVitriol = maxOf(local.permanentVitriol, cloud.permanentVitriol),
                        savedSulfur = maxOf(local.savedSulfur, cloud.savedSulfur),
                        savedMercury = maxOf(local.savedMercury, cloud.savedMercury),
                        savedVitriol = maxOf(local.savedVitriol, cloud.savedVitriol)
                    )
                    saveLocalUserSave(merged)
                    merged
                } else {
                    local
                }
            } else {
                local
            }
        } catch (e: Exception) {
            local
        }
    }

    suspend fun saveUserSave(save: UserSave): Result<Unit> {
        saveLocalUserSave(save)
        if (save.userId.isBlank()) return Result.success(Unit)

        return try {
            firestore.collection("users").document(save.userId)
                .set(save, SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
