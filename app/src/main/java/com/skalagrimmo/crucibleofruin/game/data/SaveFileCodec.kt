package com.skalagrimmo.crucibleofruin.game.data

import org.json.JSONObject

/**
 * Encodes and decodes [UserSave] as a portable JSON document for the manual
 * export/import backup flow in the Options menu. The document carries a format
 * marker and a version number so future format changes can migrate old files
 * instead of rejecting them.
 */
object SaveFileCodec {
    private const val FORMAT_MARKER = "crucible-of-ruin-save"
    private const val FORMAT_VERSION = 1

    fun encode(save: UserSave): String {
        val json = JSONObject()
        json.put("format", FORMAT_MARKER)
        json.put("version", FORMAT_VERSION)
        json.put("displayName", save.displayName)
        json.put("highScore", save.highScore)
        json.put("bestTimeSeconds", save.bestTimeSeconds.toDouble())
        json.put("foesSlain", save.foesSlain)
        json.put("lastCheckpoint", save.lastCheckpoint)
        json.put("savedAt", save.savedAt)
        json.put("targetFps", save.targetFps)
        json.put("soundMuted", save.soundMuted)
        json.put("hapticsEnabled", save.hapticsEnabled)
        json.put("permanentVitality", save.permanentVitality)
        json.put("permanentDamage", save.permanentDamage)
        json.put("permanentSpeed", save.permanentSpeed)
        json.put("permanentFlasks", save.permanentFlasks)
        json.put("permanentDefense", save.permanentDefense)
        json.put("permanentVitriol", save.permanentVitriol)
        json.put("savedSulfur", save.savedSulfur)
        json.put("savedMercury", save.savedMercury)
        json.put("savedVitriol", save.savedVitriol)
        json.put("perfectDodges", save.perfectDodges)
        return json.toString(2)
    }

    /**
     * Decodes a save document. Returns null when the text is malformed, is not a
     * Crucible of Ruin save, or was written by a newer format version.
     */
    fun decode(json: String): UserSave? {
        return try {
            val obj = JSONObject(json)
            if (obj.optString("format") != FORMAT_MARKER) return null
            if (obj.optInt("version", 0) > FORMAT_VERSION) return null
            UserSave(
                displayName = obj.optString("displayName", "Alchemist"),
                highScore = obj.optInt("highScore", 0),
                bestTimeSeconds = obj.optDouble("bestTimeSeconds", 0.0).toFloat(),
                foesSlain = obj.optInt("foesSlain", 0),
                lastCheckpoint = obj.optInt("lastCheckpoint", 0),
                savedAt = obj.optLong("savedAt", 0L),
                targetFps = obj.optInt("targetFps", 30),
                soundMuted = obj.optBoolean("soundMuted", false),
                hapticsEnabled = obj.optBoolean("hapticsEnabled", true),
                permanentVitality = obj.optInt("permanentVitality", 0),
                permanentDamage = obj.optInt("permanentDamage", 0),
                permanentSpeed = obj.optInt("permanentSpeed", 0),
                permanentFlasks = obj.optInt("permanentFlasks", 0),
                permanentDefense = obj.optInt("permanentDefense", 0),
                permanentVitriol = obj.optInt("permanentVitriol", 0),
                savedSulfur = obj.optInt("savedSulfur", 3),
                savedMercury = obj.optInt("savedMercury", 2),
                savedVitriol = obj.optInt("savedVitriol", 1),
                perfectDodges = obj.optInt("perfectDodges", 0)
            )
        } catch (_: Exception) {
            null
        }
    }
}
