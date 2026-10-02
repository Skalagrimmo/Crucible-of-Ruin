package com.example.game

import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Inlined primitive AABB check without object allocations.
 */
inline fun aabbIntersects(
    ax: Float, ay: Float, aw: Float, ah: Float,
    bx: Float, by: Float, bw: Float, bh: Float
): Boolean {
    return ax < bx + bw && ax + aw > bx && ay < by + bh && ay + ah > by
}

enum class PlatformStyle {
    STONE,
    IRON_GIRDER,
    STONE_LEDGE,
    BOSS_GATE
}

enum class HazardType {
    ACID,
    SPIKES
}

data class Platform(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val style: PlatformStyle
)

data class Hazard(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val type: HazardType
)

data class Checkpoint(
    val id: Int,
    val x: Float,
    val y: Float,
    val name: String,
    var activated: Boolean = false
)

class BreakableUrn(
    val x: Float,
    val y: Float,
    val w: Float = 24f,
    val h: Float = 24f,
    val dropType: String, // "health" or "flask"
    var dead: Boolean = false
)

class PickupItem {
    var x: Float = 0f
    var y: Float = 0f
    var w: Float = 16f
    var h: Float = 16f
    var type: String = "health"
    var vy: Float = -130f

    fun init(spawnX: Float, spawnY: Float, pickupType: String) {
        x = spawnX
        y = spawnY
        type = pickupType
        vy = -130f
    }
}

/**
 * Dense-array zero-allocation object pool for item pickups.
 */
class PickupPool(val capacity: Int = 24) {
    val pool = Array(capacity) { PickupItem() }
    var activeCount: Int = 0
        private set

    fun get(index: Int): PickupItem = pool[index]

    fun spawn(spawnX: Float, spawnY: Float, type: String): PickupItem {
        val item = if (activeCount < capacity) {
            pool[activeCount++]
        } else {
            pool[capacity - 1]
        }
        item.init(spawnX, spawnY, type)
        return item
    }

    fun removeAt(index: Int) {
        if (index in 0 until activeCount) {
            activeCount--
            if (index < activeCount) {
                val temp = pool[index]
                pool[index] = pool[activeCount]
                pool[activeCount] = temp
            }
        }
    }

    fun clear() {
        activeCount = 0
    }
}

enum class ProjectileType {
    FLASK,
    CULTIST_ORB,
    BOSS_MAGMA,
    SHOCKWAVE
}

class Projectile {
    var x: Float = 0f
    var y: Float = 0f
    var vx: Float = 0f
    var vy: Float = 0f
    var type: ProjectileType = ProjectileType.FLASK
    var senderIsPlayer: Boolean = true
    var damage: Float = 20f
    var w: Float = 16f
    var h: Float = 16f
    var life: Float = 4.0f
    var gravity: Float = 420f

    fun init(
        spawnX: Float,
        spawnY: Float,
        velX: Float,
        velY: Float,
        pType: ProjectileType,
        isPlayer: Boolean,
        dmg: Float = 20f
    ) {
        x = spawnX
        y = spawnY
        vx = velX
        vy = velY
        type = pType
        senderIsPlayer = isPlayer
        damage = dmg

        w = if (type == ProjectileType.SHOCKWAVE) 20f else 16f
        h = if (type == ProjectileType.SHOCKWAVE) 24f else 16f
        life = if (type == ProjectileType.SHOCKWAVE) 1.6f else 4.0f
        gravity = if (type == ProjectileType.CULTIST_ORB || type == ProjectileType.SHOCKWAVE) 0f else 420f
    }
}

/**
 * Dense-array object pool for projectiles.
 * Guarantees zero runtime allocations and contiguous memory layout.
 */
class ProjectilePool(val capacity: Int = 40) {
    val pool = Array(capacity) { Projectile() }
    var activeCount: Int = 0
        private set

    fun get(index: Int): Projectile = pool[index]

    fun spawn(
        x: Float,
        y: Float,
        vx: Float,
        vy: Float,
        type: ProjectileType,
        senderIsPlayer: Boolean,
        damage: Float = 20f
    ): Projectile {
        val p = if (activeCount < capacity) {
            pool[activeCount++]
        } else {
            pool[capacity - 1]
        }
        p.init(x, y, vx, vy, type, senderIsPlayer, damage)
        return p
    }

    fun removeAt(index: Int) {
        if (index in 0 until activeCount) {
            activeCount--
            if (index < activeCount) {
                val temp = pool[index]
                pool[index] = pool[activeCount]
                pool[activeCount] = temp
            }
        }
    }

    fun clear() {
        activeCount = 0
    }
}

/**
 * Pre-allocated particle entity.
 */
class Particle {
    var x: Float = 0f
    var y: Float = 0f
    var vx: Float = 0f
    var vy: Float = 0f
    var color: Color = Color.White
    var life: Float = 0f
    var maxLife: Float = 1f
    var size: Float = 3f
    var gravity: Float = 260f
}

/**
 * High-performance dense-array object pool for particles.
 * Eliminates garbage collection spikes and minimizes memory usage.
 */
class ParticlePool(val capacity: Int = 160) {
    val pool = Array(capacity) { Particle() }
    var activeCount: Int = 0
        private set

    fun get(index: Int): Particle = pool[index]

    fun acquire(): Particle {
        return if (activeCount < capacity) {
            pool[activeCount++]
        } else {
            pool[capacity - 1]
        }
    }

    fun removeAt(index: Int) {
        if (index in 0 until activeCount) {
            activeCount--
            if (index < activeCount) {
                val temp = pool[index]
                pool[index] = pool[activeCount]
                pool[activeCount] = temp
            }
        }
    }

    fun spawn(
        x: Float,
        y: Float,
        color: Color,
        count: Int = 8,
        speed: Float = 120f,
        life: Float = 0.4f,
        size: Float = 3f
    ) {
        for (i in 0 until count) {
            val angle = Random.nextFloat() * (Math.PI.toFloat() * 2f)
            val spd = (0.3f + Random.nextFloat() * 0.7f) * speed
            val p = acquire()
            p.x = x
            p.y = y
            p.vx = cos(angle) * spd
            p.vy = sin(angle) * spd - (Random.nextFloat() * 40f)
            p.color = color
            p.maxLife = life + (Random.nextFloat() * 0.2f)
            p.life = p.maxLife
            p.size = size * (0.6f + Random.nextFloat() * 0.8f)
            p.gravity = 260f
        }
    }

    fun spawnEnemyDeath(x: Float, y: Float, primaryColor: Color = Color(0xFF8018A0)) {
        for (i in 0 until 18) {
            val angle = Random.nextFloat() * (Math.PI.toFloat() * 2f)
            val spd = 60f + Random.nextFloat() * 120f
            val p = acquire()
            p.x = x
            p.y = y
            p.vx = cos(angle) * spd
            p.vy = sin(angle) * spd - 30f
            p.color = if (i % 3 == 0) Color(0xFFC040FF) else primaryColor
            p.maxLife = 0.45f + Random.nextFloat() * 0.2f
            p.life = p.maxLife
            p.size = 3.5f + Random.nextFloat() * 2f
            p.gravity = 300f
        }
    }

    fun spawnEnemyHurt(x: Float, y: Float, color: Color = Color(0xFFB02040)) {
        for (i in 0 until 8) {
            val angle = Random.nextFloat() * (Math.PI.toFloat() * 2f)
            val spd = 80f + Random.nextFloat() * 80f
            val p = acquire()
            p.x = x
            p.y = y
            p.vx = cos(angle) * spd
            p.vy = sin(angle) * spd - 20f
            p.color = color
            p.maxLife = 0.25f + Random.nextFloat() * 0.15f
            p.life = p.maxLife
            p.size = 3f
            p.gravity = 240f
        }
    }

    fun spawnSlashArc(x: Float, y: Float, facing: Int, color: Color = Color(0xFF40F0FF)) {
        for (i in 0 until 10) {
            val baseAngle = if (facing > 0) -0.8f else (Math.PI.toFloat() + 0.8f)
            val angle = baseAngle + (Random.nextFloat() - 0.5f) * 1.2f
            val spd = 160f + Random.nextFloat() * 120f
            val p = acquire()
            p.x = x + facing * 16f
            p.y = y - 10f + (Random.nextFloat() - 0.5f) * 20f
            p.vx = cos(angle) * spd
            p.vy = sin(angle) * spd
            p.color = color
            p.maxLife = 0.18f
            p.life = 0.18f
            p.size = 3.5f
            p.gravity = 0f
        }
    }

    fun spawnExplosion(x: Float, y: Float) {
        for (i in 0 until 24) {
            val angle = Random.nextFloat() * (Math.PI.toFloat() * 2f)
            val spd = 60f + Random.nextFloat() * 140f
            val p = acquire()
            p.x = x
            p.y = y
            p.vx = cos(angle) * spd
            p.vy = sin(angle) * spd - 40f
            p.color = if (i % 2 == 0) Color(0xFFC040FF) else Color(0xFF38F0B0)
            p.maxLife = 0.4f + Random.nextFloat() * 0.25f
            p.life = p.maxLife
            p.size = 4f + Random.nextFloat() * 2f
            p.gravity = 250f
        }
    }

    fun update(dt: Float) {
        var i = 0
        while (i < activeCount) {
            val p = pool[i]
            p.life -= dt
            if (p.life <= 0f) {
                removeAt(i)
                continue
            }
            p.vy += p.gravity * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            i++
        }
    }

    fun clear() {
        activeCount = 0
    }
}
