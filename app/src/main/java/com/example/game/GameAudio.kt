package com.example.game

/**
 * GameAudio delegates directly to the pre-loaded static AudioTrack pool in AssetCache.
 * Guarantees zero JNI allocations and zero coroutines created during playback.
 */
class GameAudio {
    var isMuted: Boolean
        get() = AssetCache.isMuted
        set(value) {
            AssetCache.isMuted = value
        }

    init {
        AssetCache.init()
    }

    fun play(soundKey: String) {
        AssetCache.playSound(soundKey)
    }
}
