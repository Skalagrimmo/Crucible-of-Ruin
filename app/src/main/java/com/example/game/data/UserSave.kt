package com.example.game.data

data class UserSave(
    val userId: String = "",
    val displayName: String = "Alchemist",
    val highScore: Int = 0,
    val bestTimeSeconds: Float = 0f,
    val foesSlain: Int = 0,
    val lastCheckpoint: Int = 0,
    val targetFps: Int = 30,
    val soundMuted: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)
