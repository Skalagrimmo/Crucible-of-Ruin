package com.example.game

import android.content.Context

/**
 * Android boundary for loading immutable level data from packaged assets.
 * Parsing itself stays in LevelJson so the engine remains JVM-testable.
 */
object AndroidLevelAssets {
    fun load(context: Context, assetPath: String): LevelDef {
        val json = context.assets.open(assetPath)
            .bufferedReader()
            .use { it.readText() }
        return LevelJson.parse(json)
    }

    fun level01(context: Context): LevelDef =
        load(context, "levels/level_01.json")
}
