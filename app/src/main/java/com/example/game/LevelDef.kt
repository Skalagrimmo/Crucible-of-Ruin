package com.example.game

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/**
 * Serializable level description. Behavior remains in GameEngine/entities;
 * this model only describes level content and placement.
 */
@JsonClass(generateAdapter = true)
data class LevelDef(
    val id: String,
    val name: String,
    val width: Float,
    val height: Float,
    val platforms: List<PlatformDef>,
    val hazards: List<HazardDef>,
    val enemies: List<EnemyDef>,
    val breakables: List<BreakableDef>,
    val checkpoints: List<CheckpointDef>,
    val boss: BossDef
)

@JsonClass(generateAdapter = true)
data class PlatformDef(val x: Float, val y: Float, val w: Float, val h: Float, val style: PlatformStyle)
@JsonClass(generateAdapter = true)
data class HazardDef(val x: Float, val y: Float, val w: Float, val h: Float, val type: HazardType)
@JsonClass(generateAdapter = true)
data class EnemyDef(val x: Float, val y: Float, val archetype: EnemyArchetype, val patrolRange: Float)
@JsonClass(generateAdapter = true)
data class BreakableDef(val x: Float, val y: Float, val dropType: String)
@JsonClass(generateAdapter = true)
data class CheckpointDef(val id: Int, val x: Float, val y: Float, val name: String)
@JsonClass(generateAdapter = true)
data class BossDef(val x: Float, val y: Float)

/**
 * Converts immutable level data into fresh runtime entities.
 */
object LevelLoader {
    fun platforms(def: LevelDef): List<Platform> =
        def.platforms.map { Platform(it.x, it.y, it.w, it.h, it.style) }

    fun hazards(def: LevelDef): List<Hazard> =
        def.hazards.map { Hazard(it.x, it.y, it.w, it.h, it.type) }

    fun enemies(def: LevelDef): List<EnemyEntity> =
        def.enemies.map { EnemyEntity(it.x, it.y, it.archetype, it.patrolRange) }

    fun breakables(def: LevelDef): List<BreakableUrn> =
        def.breakables.map { BreakableUrn(it.x, it.y, dropType = it.dropType) }

    fun checkpoints(def: LevelDef): List<Checkpoint> =
        def.checkpoints.map { Checkpoint(it.id, it.x, it.y, it.name) }

    fun boss(def: LevelDef): BossEntity = BossEntity(def.boss.x, def.boss.y)
}


object LevelJson {
    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val adapter = moshi.adapter(LevelDef::class.java)

    fun parse(json: String): LevelDef =
        requireNotNull(adapter.fromJson(json)) { "Level JSON is empty or invalid" }
}
