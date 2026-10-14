package com.example.game

import android.content.Context
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class GameHaptics(private val context: Context) {
    var isEnabled: Boolean = true

    private val vibrator: Vibrator? by lazy {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Light, crisp tick when initiating a melee slash.
     */
    fun onMeleeSwing() {
        if (!isEnabled) return
        performOneShot(durationMs = 12L, amplitude = 70)
    }

    /**
     * Punchy impact when a sword slash strikes an enemy or breakable urn.
     */
    fun onMeleeHit() {
        if (!isEnabled) return
        performOneShot(durationMs = 28L, amplitude = 170)
    }

    /**
     * Heavy, jagged double-thud when the player takes damage.
     */
    fun onPlayerDamage() {
        if (!isEnabled) return
        performWaveform(
            timings = longArrayOf(0, 45, 35, 65),
            amplitudes = intArrayOf(0, 200, 0, 255)
        )
    }

    /**
     * Deep, heavy resonant pulse when dealing damage to or striking the Crucible Boss.
     */
    fun onBossHit() {
        if (!isEnabled) return
        performWaveform(
            timings = longArrayOf(0, 60, 40, 90),
            amplitudes = intArrayOf(0, 220, 0, 255)
        )
    }

    /**
     * Volatile alchemical flask explosion impact.
     */
    fun onFlaskExplosion() {
        if (!isEnabled) return
        performWaveform(
            timings = longArrayOf(0, 35, 25, 50),
            amplitudes = intArrayOf(0, 180, 0, 230)
        )
    }

    /**
     * Ascending triple pulse when crafting and drinking a combat elixir.
     */
    fun onBuffActivated() {
        if (!isEnabled) return
        performWaveform(
            timings = longArrayOf(0, 20, 30, 25, 30, 40),
            amplitudes = intArrayOf(0, 120, 0, 180, 0, 240)
        )
    }

    /**
     * Gentle tick on collecting alchemical reagents (Sulfur, Mercury, Vitriol).
     */
    fun onResourceCollected() {
        if (!isEnabled) return
        performOneShot(durationMs = 16L, amplitude = 100)
    }

    private fun performOneShot(durationMs: Long, amplitude: Int) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val clampedAmp = amplitude.coerceIn(1, 255)
                val effect = VibrationEffect.createOneShot(durationMs, clampedAmp)
                vib.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(durationMs)
            }
        } catch (_: Exception) {}
    }

    private fun performWaveform(timings: LongArray, amplitudes: IntArray) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && vib.hasAmplitudeControl()) {
                val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
                vib.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(timings, -1)
            }
        } catch (_: Exception) {}
    }
}
