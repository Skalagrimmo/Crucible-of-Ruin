package com.example.game.data

import android.content.Context
import android.content.SharedPreferences
import com.example.R
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class UserSaveRepository(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("crucible_user_save", Context.MODE_PRIVATE)

    private val firestore: FirebaseFirestore by lazy {
        val dbId = context.getString(R.string.firestore_database_id)
        FirebaseFirestore.getInstance(dbId)
    }

    fun getLocalUserSave(): UserSave {
        return UserSave(
            userId = prefs.getString("userId", "") ?: "",
            displayName = prefs.getString("displayName", "Alchemist") ?: "Alchemist",
            highScore = prefs.getInt("highScore", 0),
            bestTimeSeconds = prefs.getFloat("bestTimeSeconds", 0f),
            foesSlain = prefs.getInt("foesSlain", 0),
            lastCheckpoint = prefs.getInt("lastCheckpoint", 0),
            targetFps = prefs.getInt("targetFps", 30),
            soundMuted = prefs.getBoolean("soundMuted", false),
            updatedAt = prefs.getLong("updatedAt", System.currentTimeMillis())
        )
    }

    fun saveLocalUserSave(save: UserSave) {
        prefs.edit()
            .putString("userId", save.userId)
            .putString("displayName", save.displayName)
            .putInt("highScore", save.highScore)
            .putFloat("bestTimeSeconds", save.bestTimeSeconds)
            .putInt("foesSlain", save.foesSlain)
            .putInt("lastCheckpoint", save.lastCheckpoint)
            .putInt("targetFps", save.targetFps)
            .putBoolean("soundMuted", save.soundMuted)
            .putLong("updatedAt", save.updatedAt)
            .apply()
    }

    suspend fun getUserSave(userId: String): UserSave {
        var local = getLocalUserSave()
        if (userId.isNotBlank() && local.userId != userId) {
            local = local.copy(userId = userId)
        }

        if (userId.isBlank()) return local

        return try {
            val doc = firestore.collection("users").document(userId).get().await()
            if (doc.exists()) {
                val cloudSave = UserSave(
                    userId = doc.getString("userId") ?: userId,
                    displayName = doc.getString("displayName") ?: local.displayName,
                    highScore = maxOf(doc.getLong("highScore")?.toInt() ?: 0, local.highScore),
                    bestTimeSeconds = run {
                        val cloudTime = doc.getDouble("bestTimeSeconds")?.toFloat() ?: 0f
                        if (local.bestTimeSeconds <= 0f) cloudTime
                        else if (cloudTime <= 0f) local.bestTimeSeconds
                        else minOf(cloudTime, local.bestTimeSeconds)
                    },
                    foesSlain = maxOf(doc.getLong("foesSlain")?.toInt() ?: 0, local.foesSlain),
                    lastCheckpoint = maxOf(doc.getLong("lastCheckpoint")?.toInt() ?: 0, local.lastCheckpoint),
                    targetFps = doc.getLong("targetFps")?.toInt() ?: local.targetFps,
                    soundMuted = doc.getBoolean("soundMuted") ?: local.soundMuted,
                    updatedAt = maxOf(doc.getLong("updatedAt") ?: 0L, local.updatedAt)
                )
                saveLocalUserSave(cloudSave)
                cloudSave
            } else {
                saveUserSave(local)
                local
            }
        } catch (_: Exception) {
            local
        }
    }

    suspend fun saveUserSave(save: UserSave): Boolean {
        saveLocalUserSave(save)

        if (save.userId.isBlank()) return true

        return try {
            val map = hashMapOf(
                "userId" to save.userId,
                "displayName" to save.displayName,
                "highScore" to save.highScore,
                "bestTimeSeconds" to save.bestTimeSeconds,
                "foesSlain" to save.foesSlain,
                "lastCheckpoint" to save.lastCheckpoint,
                "targetFps" to save.targetFps,
                "soundMuted" to save.soundMuted,
                "updatedAt" to System.currentTimeMillis()
            )
            firestore.collection("users").document(save.userId)
                .set(map, SetOptions.merge())
                .await()
            true
        } catch (_: Exception) {
            false
        }
    }
}
