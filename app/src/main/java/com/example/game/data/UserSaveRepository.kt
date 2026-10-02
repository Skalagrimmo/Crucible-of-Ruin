package com.example.game.data

import android.content.Context
import com.example.R
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class UserSaveRepository(private val context: Context) {
    private val firestore: FirebaseFirestore by lazy {
        val dbId = context.getString(R.string.firestore_database_id)
        FirebaseFirestore.getInstance(dbId)
    }

    suspend fun getUserSave(userId: String): UserSave? {
        if (userId.isBlank()) return null
        return try {
            val doc = firestore.collection("users").document(userId).get().await()
            if (doc.exists()) {
                UserSave(
                    userId = doc.getString("userId") ?: userId,
                    displayName = doc.getString("displayName") ?: "Alchemist",
                    highScore = doc.getLong("highScore")?.toInt() ?: 0,
                    bestTimeSeconds = doc.getDouble("bestTimeSeconds")?.toFloat() ?: 0f,
                    foesSlain = doc.getLong("foesSlain")?.toInt() ?: 0,
                    lastCheckpoint = doc.getLong("lastCheckpoint")?.toInt() ?: 0,
                    targetFps = doc.getLong("targetFps")?.toInt() ?: 30,
                    soundMuted = doc.getBoolean("soundMuted") ?: false,
                    updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
                )
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun saveUserSave(save: UserSave): Boolean {
        if (save.userId.isBlank()) return false
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
