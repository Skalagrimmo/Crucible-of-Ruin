package com.example.game

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class Camera2D(val viewW: Float = 960f, val viewH: Float = 540f) {
    var x: Float = 0f
    var y: Float = 0f
    var shakeIntensity: Float = 0f
    var lookAhead: Float = 0f

    fun shake(amt: Float) {
        shakeIntensity = min(24f, shakeIntensity + amt)
    }

    fun update(targetX: Float, targetY: Float, targetFacing: Int, maxW: Float, maxH: Float, dt: Float) {
        val targetLead = targetFacing * 40f
        lookAhead += (targetLead - lookAhead) * 4f * dt

        val desiredX = targetX - viewW / 2f + lookAhead
        val desiredY = targetY - viewH / 2f - 20f

        x += (desiredX - x) * 6f * dt
        y += (desiredY - y) * 4f * dt

        x = x.coerceIn(0f, (maxW - viewW).coerceAtLeast(0f))
        y = y.coerceIn(0f, (maxH - viewH).coerceAtLeast(0f))

        if (shakeIntensity > 0.1f) {
            shakeIntensity *= (0.9f).coerceAtLeast(0f)
        } else {
            shakeIntensity = 0f
        }
    }

    fun getOffsetX(): Float {
        return if (shakeIntensity > 0f) x + ((Math.random().toFloat() - 0.5f) * shakeIntensity) else x
    }

    fun getOffsetY(): Float {
        return if (shakeIntensity > 0f) y + ((Math.random().toFloat() - 0.5f) * shakeIntensity) else y
    }
}

class PlayerEntity(var x: Float, var y: Float) {
    val w: Float = 26f
    val h: Float = 44f
    var vx: Float = 0f
    var vy: Float = 0f
    var facing: Int = 1 // 1 = right, -1 = left
    var grounded: Boolean = false

    val maxHp: Float = 100f
    var hp: Float = 100f
    val maxFlasks: Int = 5
    var flasks: Int = 3

    val runSpeed: Float = 190f
    val airSpeed: Float = 160f
    val jumpForce: Float = -430f
    val gravity: Float = 1050f
    var coyoteTime: Float = 0f
    var jumpBuffer: Float = 0f

    var attacking: Boolean = false
    var attackTimer: Float = 0f
    val attackDuration: Float = 0.28f
    var attackHitboxActive: Boolean = false
    var attackId: Long = 0L
    var subCooldown: Float = 0f

    var invulnTimer: Float = 0f
    var knockbackTimer: Float = 0f
    var dead: Boolean = false
    var respawnTimer: Float = 0f
    var walkAnimTimer: Float = 0f

    fun takeDamage(amount: Float, fromX: Float, engine: GameEngine) {
        if (invulnTimer > 0f || dead) return
        hp -= amount
        engine.audio.play("hurt")
        engine.camera.shake(8f)
        engine.particles.spawn(x + w / 2f, y + h / 2f, Color(0xFFE03030), 14, 140f, 0.4f, 4f)

        if (hp <= 0f) {
            hp = 0f
            dead = true
            respawnTimer = 1.4f
            engine.particles.spawnEnemyDeath(x + w / 2f, y + h / 2f, Color(0xFF8020A0))
            return
        }

        invulnTimer = 1.1f
        knockbackTimer = 0.24f
        val dir = if (fromX > x + w / 2f) -1 else 1
        vx = dir * 180f
        vy = -210f
    }

    fun respawn(cp: Checkpoint) {
        x = cp.x
        y = cp.y
        vx = 0f
        vy = 0f
        hp = maxHp
        flasks = max(flasks, 2)
        dead = false
        invulnTimer = 1.5f
        knockbackTimer = 0f
    }
}

enum class EnemyArchetype {
    LURKER,
    CHERUB,
    CULTIST,
    GOLEM
}

class EnemyEntity(
    var x: Float,
    var y: Float,
    val type: EnemyArchetype,
    val patrolRange: Float = 160f
) {
    val startX: Float = x
    val startY: Float = y
    var facing: Int = -1
    var grounded: Boolean = false
    var dead: Boolean = false
    var lastHitId: Long = 0L
    var hurtTimer: Float = 0f
    var stateTimer: Float = 0f
    var state: String = "patrol"
    var vx: Float = 0f
    var vy: Float = 0f

    val w: Float
    val h: Float
    var hp: Float
    val maxHp: Float
    val speed: Float
    var shootCooldown: Float = 2.0f
    var charging: Boolean = false
    var flyPhase: Float = (Math.random() * Math.PI * 2.0).toFloat()
    var targetY: Float = y

    init {
        when (type) {
            EnemyArchetype.LURKER -> {
                w = 30f; h = 36f; hp = 45f; maxHp = 45f; speed = 90f; state = "patrol"
            }
            EnemyArchetype.CHERUB -> {
                w = 26f; h = 24f; hp = 30f; maxHp = 30f; speed = 110f; state = "fly"
            }
            EnemyArchetype.CULTIST -> {
                w = 28f; h = 42f; hp = 50f; maxHp = 50f; speed = 0f; state = "idle"
            }
            EnemyArchetype.GOLEM -> {
                w = 44f; h = 54f; hp = 130f; maxHp = 130f; speed = 45f; state = "patrol"
            }
        }
    }

    fun takeDamage(amount: Float, aId: Long, fromX: Float, engine: GameEngine) {
        if (lastHitId == aId || dead) return
        lastHitId = aId
        hp -= amount
        hurtTimer = 0.2f
        engine.audio.play("hit")
        engine.particles.spawnEnemyHurt(x + w / 2f, y + h / 2f, Color(0xFFB02040))

        val dir = if (fromX > x + w / 2f) -1 else 1
        vx = dir * (if (type == EnemyArchetype.GOLEM) 40f else 120f)

        if (hp <= 0f) {
            dead = true
            engine.enemiesDefeated++
            engine.particles.spawnEnemyDeath(x + w / 2f, y + h / 2f, Color(0xFF8018A0))
            if (Math.random() < 0.4) {
                val dropType = if (Math.random() < 0.5) "health" else "flask"
                engine.pickups.spawn(x + w / 2f - 8f, y + h / 2f - 8f, dropType)
            }
        }
    }
}

class BossEntity(var x: Float, var y: Float) {
    val w: Float = 90f
    val h: Float = 80f
    val maxHp: Float = 350f
    var hp: Float = 350f
    var facing: Int = -1
    var vx: Float = 0f
    var vy: Float = 0f
    var dead: Boolean = false
    var phase2: Boolean = false
    var lastHitId: Long = 0L
    var hurtTimer: Float = 0f

    var state: String = "idle"
    var stateTimer: Float = 1.2f
    var attackCycle: Int = 0
    var active: Boolean = false

    fun takeDamage(amount: Float, aId: Long, fromX: Float, engine: GameEngine) {
        if (lastHitId == aId || dead || !active) return
        lastHitId = aId
        hp -= amount
        hurtTimer = 0.18f
        engine.audio.play("hit")
        engine.camera.shake(6f)
        engine.particles.spawnEnemyHurt(x + w / 2f, y + h / 2f, Color(0xFFFF3060))

        if (hp <= maxHp * 0.5f && !phase2) {
            phase2 = true
            engine.audio.play("boss_roar")
            engine.camera.shake(16f)
            engine.particles.spawnEnemyDeath(x + w / 2f, y + h / 2f, Color(0xFFC030FF))
        }

        if (hp <= 0f) {
            hp = 0f
            dead = true
            engine.audio.play("victory")
            engine.camera.shake(20f)
            engine.particles.spawnEnemyDeath(x + w / 2f, y + h / 2f, Color(0xFFFFD700))
            engine.state = "victory"
        }
    }
}

class GameEngine(
    private val levelDef: LevelDef
) {
    val audio = GameAudio()
    val camera = Camera2D()
    val particles = ParticlePool(160)
    val projectiles = ProjectilePool(40)
    val pickups = PickupPool(24)

    val levelWidth: Float get() = levelDef.width
    val levelHeight: Float get() = levelDef.height

    var state: String = "playing"
    var timeElapsed: Float = 0f
    var enemiesDefeated: Int = 0

    val checkpoints: List<Checkpoint> = LevelLoader.checkpoints(levelDef).also {
        it.first().activated = true
    }
    var activeCheckpoint: Checkpoint = checkpoints.first()

    val platforms = ArrayList<Platform>()
    val hazards = ArrayList<Hazard>()
    val breakables = ArrayList<BreakableUrn>()
    val enemies = ArrayList<EnemyEntity>()
    var boss: BossEntity = LevelLoader.boss(levelDef)

    val player: PlayerEntity = PlayerEntity(activeCheckpoint.x, activeCheckpoint.y)

    var inputLeft: Boolean = false
    var inputRight: Boolean = false
    var inputJump: Boolean = false
    var inputAttack: Boolean = false
    var inputSub: Boolean = false

    private var attackCounter: Long = 1L

    var onCheckpointActivated: ((Int) -> Unit)? = null

    init {
        initLevel()
    }

    fun setStartCheckpoint(index: Int) {
        val idx = index.coerceIn(0, checkpoints.size - 1)
        for (i in 0..idx) {
            checkpoints[i].activated = true
        }
        activeCheckpoint = checkpoints[idx]
        player.x = activeCheckpoint.x
        player.y = activeCheckpoint.y
        camera.x = (player.x - 300f).coerceAtLeast(0f)
    }

    fun restart() {
        initLevel()
        player.respawn(activeCheckpoint)
        state = "playing"
        timeElapsed = 0f
        enemiesDefeated = 0
    }

    fun initLevel() {
        platforms.clear()
        hazards.clear()
        breakables.clear()
        pickups.clear()
        enemies.clear()
        projectiles.clear()

        platforms.addAll(LevelLoader.platforms(levelDef))
        hazards.addAll(LevelLoader.hazards(levelDef))
        breakables.addAll(LevelLoader.breakables(levelDef))
        enemies.addAll(LevelLoader.enemies(levelDef))
        boss = LevelLoader.boss(levelDef)
    }

    fun onJumpPressed() {
        player.jumpBuffer = 0.15f
    }

    fun onAttackPressed() {
        if (!player.attacking && !player.dead && player.knockbackTimer <= 0f) {
            player.attacking = true
            player.attackTimer = player.attackDuration
            player.attackId = ++attackCounter
            audio.play("slash")
            particles.spawnSlashArc(
                if (player.facing > 0) player.x + player.w else player.x,
                player.y + player.h / 2f,
                player.facing
            )
        }
    }

    fun onSubPressed() {
        if (player.subCooldown <= 0f && player.flasks > 0 && !player.dead && player.knockbackTimer <= 0f) {
            player.flasks--
            player.subCooldown = 0.45f
            audio.play("throw")
            val spawnX = if (player.facing > 0) player.x + player.w + 4f else player.x - 14f
            val spawnY = player.y + 10f
            projectiles.spawn(
                spawnX, spawnY,
                player.facing * 340f + player.vx * 0.3f, -220f,
                ProjectileType.FLASK, true
            )
        }
    }

    fun update(dt: Float) {
        if (state == "paused") return
        timeElapsed += dt

        // Checkpoint detection
        for (cp in checkpoints) {
            if (!cp.activated && abs(player.x - cp.x) < 50f && abs(player.y - cp.y) < 70f) {
                cp.activated = true
                activeCheckpoint = cp
                audio.play("checkpoint")
                camera.shake(5f)
                particles.spawn(cp.x, cp.y, Color(0xFFFFD700), 28, 160f, 0.8f, 5f)
                onCheckpointActivated?.invoke(cp.id)
            }
        }

        // Boss trigger. Horizontal-only levels can omit triggerY; tall levels
        // require the player to reach the configured vertical arena as well.
        val bossTriggerY = levelDef.boss.triggerY
        val bossTriggerReached = player.x > levelDef.boss.triggerX &&
            (bossTriggerY == null || player.y <= bossTriggerY)
        if (!boss.active && bossTriggerReached) {
            boss.active = true
            audio.play("boss_roar")
            camera.shake(12f)
        }

        updatePlayer(dt)
        if (!player.dead) {
            updateEnemies(dt)
            updateBoss(dt)
            updateProjectiles(dt)
            updatePickups(dt)
        }
        particles.update(dt)

        camera.update(player.x, player.y, player.facing, levelWidth, levelHeight, dt)
    }

    private fun updatePlayer(dt: Float) {
        if (player.dead) {
            player.respawnTimer -= dt
            if (player.respawnTimer <= 0f) {
                player.respawn(activeCheckpoint)
                camera.x = player.x - 400f
            }
            return
        }

        if (player.invulnTimer > 0f) player.invulnTimer -= dt
        if (player.knockbackTimer > 0f) player.knockbackTimer -= dt
        if (player.subCooldown > 0f) player.subCooldown -= dt
        if (player.coyoteTime > 0f) player.coyoteTime -= dt
        if (player.jumpBuffer > 0f) player.jumpBuffer -= dt

        // Horizontal Movement
        if (player.knockbackTimer <= 0f) {
            var moveDir = 0
            if (inputLeft) moveDir -= 1
            if (inputRight) moveDir += 1

            if (moveDir != 0) {
                player.facing = moveDir
                val targetSpeed = moveDir * (if (player.grounded) player.runSpeed else player.airSpeed)
                player.vx += (targetSpeed - player.vx) * (if (player.grounded) 14f else 7f) * dt
                player.walkAnimTimer += dt * 10f
            } else {
                player.vx += (0f - player.vx) * (if (player.grounded) 18f else 6f) * dt
                player.walkAnimTimer = 0f
            }

            // Jump
            if (player.jumpBuffer > 0f && player.coyoteTime > 0f) {
                player.vy = player.jumpForce
                player.grounded = false
                player.coyoteTime = 0f
                player.jumpBuffer = 0f
                audio.play("jump")
                particles.spawn(player.x + player.w / 2f, player.y + player.h, Color(0xFF8B7DA8), 6, 60f, 0.25f, 2.5f)
            }
            if (!inputJump && player.vy < -120f) {
                player.vy += 700f * dt
            }
        }

        // Melee attack hitbox active frames
        if (player.attacking) {
            player.attackTimer -= dt
            val elapsed = player.attackDuration - player.attackTimer
            player.attackHitboxActive = (elapsed in 0.04f..0.22f)
            if (player.attackTimer <= 0f) {
                player.attacking = false
                player.attackHitboxActive = false
            }
        }

        player.vy += player.gravity * dt
        if (player.vy > 750f) player.vy = 750f

        // Physics X
        player.x += player.vx * dt
        for (p in platforms) {
            if (aabbIntersects(player.x, player.y, player.w, player.h, p.x, p.y, p.w, p.h)) {
                if (player.vx > 0f) {
                    player.x = p.x - player.w
                } else if (player.vx < 0f) {
                    player.x = p.x + p.w
                }
                player.vx = 0f
            }
        }

        // Physics Y
        player.y += player.vy * dt
        player.grounded = false
        for (p in platforms) {
            if (aabbIntersects(player.x, player.y, player.w, player.h, p.x, p.y, p.w, p.h)) {
                if (player.vy > 0f) {
                    player.y = p.y - player.h
                    player.vy = 0f
                    player.grounded = true
                    player.coyoteTime = 0.12f
                } else if (player.vy < 0f) {
                    player.y = p.y + p.h
                    player.vy = 0f
                }
            }
        }

        // Hazards
        for (h in hazards) {
            if (aabbIntersects(player.x, player.y, player.w, player.h, h.x, h.y, h.w, h.h)) {
                player.takeDamage(25f, h.x + h.w / 2f, this)
                player.vy = -280f
            }
        }

        // Pit fall
        if (player.y > levelHeight + 100f) {
            player.takeDamage(100f, player.x, this)
        }

        // Melee combat checks
        if (player.attacking && player.attackHitboxActive) {
            val slashX = if (player.facing > 0) player.x + player.w - 4f else player.x - 34f
            val slashY = player.y + 4f
            val slashW = 36f
            val slashH = player.h - 8f

            for (e in enemies) {
                if (!e.dead && aabbIntersects(slashX, slashY, slashW, slashH, e.x, e.y, e.w, e.h)) {
                    e.takeDamage(28f, player.attackId, player.x, this)
                    camera.shake(4f)
                }
            }
            if (boss.active && !boss.dead && aabbIntersects(slashX, slashY, slashW, slashH, boss.x, boss.y, boss.w, boss.h)) {
                boss.takeDamage(28f, player.attackId, player.x, this)
                camera.shake(5f)
            }
            for (b in breakables) {
                if (!b.dead && aabbIntersects(slashX, slashY, slashW, slashH, b.x, b.y, b.w, b.h)) {
                    b.dead = true
                    audio.play("hit")
                    particles.spawn(b.x + 12f, b.y + 12f, Color(0xFFB39060), 12, 110f, 0.35f, 3.5f)
                    pickups.spawn(b.x + 4f, b.y, b.dropType)
                }
            }
        }
    }

    private fun updateEnemies(dt: Float) {
        val px = player.x
        val py = player.y

        for (e in enemies) {
            if (e.dead) continue
            if (e.hurtTimer > 0f) e.hurtTimer -= dt

            val distToPlayerX = px - e.x
            val distToPlayer = hypot(px - e.x, py - e.y)

            when (e.type) {
                EnemyArchetype.LURKER -> {
                    when (e.state) {
                        "patrol" -> {
                            e.vx = e.facing * e.speed
                            if (abs(e.x - e.startX) > e.patrolRange) {
                                e.facing = if (e.x > e.startX) -1 else 1
                            }
                            if (distToPlayer < 200f && abs(py - e.y) < 70f) {
                                e.facing = if (distToPlayerX > 0) 1 else -1
                                e.state = "telegraph"
                                e.stateTimer = 0.35f
                                e.vx = 0f
                            }
                        }
                        "telegraph" -> {
                            e.stateTimer -= dt
                            e.vx = 0f
                            if (e.stateTimer <= 0f) {
                                e.state = "lunge"
                                e.stateTimer = 0.5f
                                e.vx = e.facing * 230f
                                e.vy = -120f
                                audio.play("slash")
                            }
                        }
                        "lunge" -> {
                            e.stateTimer -= dt
                            if (e.stateTimer <= 0f) {
                                e.state = "cooldown"
                                e.stateTimer = 0.8f
                                e.vx = 0f
                            }
                        }
                        "cooldown" -> {
                            e.stateTimer -= dt
                            e.vx = 0f
                            if (e.stateTimer <= 0f) e.state = "patrol"
                        }
                    }
                }
                EnemyArchetype.CHERUB -> {
                    e.flyPhase += dt * 3.5f
                    when (e.state) {
                        "fly" -> {
                            e.vx = e.facing * e.speed
                            e.vy = sin(e.flyPhase) * 60f
                            if (abs(e.x - e.startX) > e.patrolRange) {
                                e.facing = if (e.x > e.startX) -1 else 1
                            }
                            if (distToPlayer < 240f && py > e.y) {
                                e.state = "swoop"
                                e.stateTimer = 1.0f
                                e.facing = if (distToPlayerX > 0) 1 else -1
                                e.targetY = py
                            }
                        }
                        "swoop" -> {
                            e.stateTimer -= dt
                            e.vx = e.facing * (e.speed * 1.6f)
                            e.vy = 120f
                            if (e.y >= e.targetY || e.stateTimer <= 0f) {
                                e.state = "ascend"
                                e.stateTimer = 0.9f
                            }
                        }
                        "ascend" -> {
                            e.stateTimer -= dt
                            e.vy = -130f
                            if (e.y <= e.startY || e.stateTimer <= 0f) e.state = "fly"
                        }
                    }
                }
                EnemyArchetype.CULTIST -> {
                    e.facing = if (distToPlayerX > 0) 1 else -1
                    e.shootCooldown -= dt
                    if (e.shootCooldown <= 0.6f && !e.charging && distToPlayer < 450f) {
                        e.charging = true
                        particles.spawn(if (e.facing > 0) e.x + e.w else e.x, e.y + 12f, Color(0xFF40FF80), 5, 40f, 0.4f, 2.5f)
                    }
                    if (e.shootCooldown <= 0f) {
                        e.charging = false
                        e.shootCooldown = 2.4f + Math.random().toFloat() * 0.6f
                        if (distToPlayer < 480f) {
                            audio.play("throw")
                            val spawnX = if (e.facing > 0) e.x + e.w else e.x - 8f
                            projectiles.spawn(
                                spawnX, e.y + 10f, e.facing * 180f, -80f, ProjectileType.CULTIST_ORB, false, 18f
                            )
                        }
                    }
                }
                EnemyArchetype.GOLEM -> {
                    e.shootCooldown -= dt
                    when (e.state) {
                        "patrol" -> {
                            e.vx = e.facing * e.speed
                            if (abs(e.x - e.startX) > e.patrolRange) {
                                e.facing = if (e.x > e.startX) -1 else 1
                            }
                            if (distToPlayer < 140f && e.shootCooldown <= 0f) {
                                e.state = "telegraph_slam"
                                e.stateTimer = 0.7f
                                e.vx = 0f
                                e.facing = if (distToPlayerX > 0) 1 else -1
                            }
                        }
                        "telegraph_slam" -> {
                            e.stateTimer -= dt
                            e.vx = 0f
                            if (e.stateTimer <= 0f) {
                                audio.play("hit")
                                camera.shake(9f)
                                particles.spawn(e.x + e.w / 2f, e.y + e.h, Color(0xFF735E4B), 16, 120f, 0.4f, 4f)
                                val shockX = if (e.facing > 0) e.x + e.w else e.x - 16f
                                projectiles.spawn(
                                    shockX, e.y + e.h - 24f, e.facing * 220f, 0f, ProjectileType.SHOCKWAVE, false, 24f
                                )
                                e.shootCooldown = 3.2f
                                e.state = "patrol"
                            }
                        }
                    }
                }
            }

            if (e.type != EnemyArchetype.CHERUB) {
                e.vy += 950f * dt
                if (e.vy > 650f) e.vy = 650f
            }

            e.x += e.vx * dt
            if (e.type != EnemyArchetype.CHERUB) {
                for (p in platforms) {
                    if (aabbIntersects(e.x, e.y, e.w, e.h, p.x, p.y, p.w, p.h)) {
                        if (e.vx > 0f) {
                            e.x = p.x - e.w
                            e.facing = -1
                        } else if (e.vx < 0f) {
                            e.x = p.x + p.w
                            e.facing = 1
                        }
                        e.vx = 0f
                    }
                }
            }

            e.y += e.vy * dt
            if (e.type != EnemyArchetype.CHERUB) {
                e.grounded = false
                for (p in platforms) {
                    if (aabbIntersects(e.x, e.y, e.w, e.h, p.x, p.y, p.w, p.h)) {
                        if (e.vy > 0f) {
                            e.y = p.y - e.h
                            e.vy = 0f
                            e.grounded = true
                        } else if (e.vy < 0f) {
                            e.y = p.y + p.h
                            e.vy = 0f
                        }
                    }
                }
            }

            if (aabbIntersects(e.x, e.y, e.w, e.h, player.x, player.y, player.w, player.h)) {
                player.takeDamage(if (e.type == EnemyArchetype.GOLEM) 24f else 16f, e.x + e.w / 2f, this)
            }
        }
    }

    private fun updateBoss(dt: Float) {
        if (!boss.active || boss.dead) return
        if (boss.hurtTimer > 0f) boss.hurtTimer -= dt

        boss.stateTimer -= dt
        val distToPlayerX = player.x + player.w / 2f - (boss.x + boss.w / 2f)

        when (boss.state) {
            "idle" -> {
                boss.vx = 0f
                boss.facing = if (distToPlayerX > 0) 1 else -1
                if (boss.stateTimer <= 0f) {
                    boss.attackCycle = (boss.attackCycle + 1) % 3
                    val mult = if (boss.phase2) 0.65f else 1.0f

                    when (boss.attackCycle) {
                        0 -> {
                            boss.state = "telegraph_dash"
                            boss.stateTimer = 0.8f * mult
                            audio.play("slash")
                        }
                        1 -> {
                            boss.state = "telegraph_mortar"
                            boss.stateTimer = 0.9f * mult
                        }
                        else -> {
                            boss.state = "telegraph_slam"
                            boss.stateTimer = 0.7f * mult
                        }
                    }
                }
            }
            "telegraph_dash" -> {
                boss.vx = 0f
                boss.facing = if (distToPlayerX > 0) 1 else -1
                particles.spawn(if (boss.facing > 0) boss.x + boss.w else boss.x, boss.y + 20f, Color(0xFFFF3010), 3, 60f, 0.25f, 3f)
                if (boss.stateTimer <= 0f) {
                    boss.state = "dash"
                    boss.stateTimer = 0.9f
                    boss.vx = boss.facing * (if (boss.phase2) 440f else 340f)
                    audio.play("boss_roar")
                }
            }
            "dash" -> {
                particles.spawn(boss.x + boss.w / 2f, boss.y + boss.h - 10f, Color(0xFFF07010), 4, 80f, 0.3f, 3.5f)
                if (boss.stateTimer <= 0f) {
                    boss.state = "idle"
                    boss.stateTimer = if (boss.phase2) 0.6f else 1.1f
                }
            }
            "telegraph_mortar" -> {
                boss.vx = 0f
                boss.facing = if (distToPlayerX > 0) 1 else -1
                particles.spawn(boss.x + boss.w / 2f, boss.y + 10f, Color(0xFFFFA020), 4, 100f, 0.35f, 4f)
                if (boss.stateTimer <= 0f) {
                    boss.state = "mortar"
                    boss.stateTimer = 0.4f
                    audio.play("explode")
                    val count = if (boss.phase2) 5 else 3
                    for (i in 0 until count) {
                        val spread = (i - (count - 1) / 2f) * 90f
                        val bVx = boss.facing * (200f + Math.random().toFloat() * 80f) + spread
                        val bVy = -380f - Math.random().toFloat() * 80f
                        projectiles.spawn(
                            boss.x + boss.w / 2f, boss.y + 10f, bVx, bVy, ProjectileType.BOSS_MAGMA, false, 25f
                        )
                    }
                }
            }
            "mortar" -> {
                boss.vx = 0f
                if (boss.stateTimer <= 0f) {
                    boss.state = "idle"
                    boss.stateTimer = if (boss.phase2) 0.8f else 1.3f
                }
            }
            "telegraph_slam" -> {
                boss.vx = 0f
                if (boss.stateTimer <= 0f) {
                    boss.state = "slam_air"
                    boss.vy = -540f
                    boss.vx = (player.x - boss.x) * 0.9f
                    audio.play("boss_roar")
                }
            }
            "slam_air" -> {
                if (boss.vy > 100f) {
                    boss.vy += 800f * dt
                }
            }
        }

        boss.vy += 950f * dt
        if (boss.vy > 750f) boss.vy = 750f

        boss.x += boss.vx * dt
        for (p in platforms) {
            if (aabbIntersects(boss.x, boss.y, boss.w, boss.h, p.x, p.y, p.w, p.h)) {
                if (boss.vx > 0f) {
                    boss.x = p.x - boss.w
                    boss.vx = 0f
                    if (boss.state == "dash") boss.stateTimer = 0f
                } else if (boss.vx < 0f) {
                    boss.x = p.x + p.w
                    boss.vx = 0f
                    if (boss.state == "dash") boss.stateTimer = 0f
                }
            }
        }

        boss.y += boss.vy * dt
        for (p in platforms) {
            if (aabbIntersects(boss.x, boss.y, boss.w, boss.h, p.x, p.y, p.w, p.h)) {
                if (boss.vy > 0f) {
                    boss.y = p.y - boss.h
                    boss.vy = 0f
                    if (boss.state == "slam_air") {
                        boss.state = "idle"
                        boss.stateTimer = if (boss.phase2) 0.7f else 1.3f
                        audio.play("hit")
                        camera.shake(14f)
                        particles.spawn(boss.x + boss.w / 2f, boss.y + boss.h, Color(0xFFFF6020), 28, 180f, 0.5f, 5f)
                        projectiles.spawn(
                            boss.x - 10f, boss.y + boss.h - 24f, -260f, 0f, ProjectileType.SHOCKWAVE, false, 24f
                        )
                        projectiles.spawn(
                            boss.x + boss.w + 10f, boss.y + boss.h - 24f, 260f, 0f, ProjectileType.SHOCKWAVE, false, 24f
                        )
                    }
                }
            }
        }

        if (aabbIntersects(boss.x, boss.y, boss.w, boss.h, player.x, player.y, player.w, player.h)) {
            player.takeDamage(if (boss.phase2) 28f else 22f, boss.x + boss.w / 2f, this)
        }
    }

    private fun updateProjectiles(dt: Float) {
        var i = 0
        while (i < projectiles.activeCount) {
            val proj = projectiles.get(i)
            proj.life -= dt
            if (proj.life <= 0f) {
                explodeProjectile(proj)
                projectiles.removeAt(i)
                continue
            }
            proj.vy += proj.gravity * dt
            proj.x += proj.vx * dt
            proj.y += proj.vy * dt

            // Platform hit
            if (proj.type != ProjectileType.SHOCKWAVE) {
                var collided = false
                for (p in platforms) {
                    if (aabbIntersects(proj.x, proj.y, proj.w, proj.h, p.x, p.y, p.w, p.h)) {
                        collided = true
                        break
                    }
                }
                if (collided) {
                    explodeProjectile(proj)
                    projectiles.removeAt(i)
                    continue
                }
            }

            // Damage player if enemy projectile
            if (!proj.senderIsPlayer && aabbIntersects(proj.x, proj.y, proj.w, proj.h, player.x, player.y, player.w, player.h)) {
                player.takeDamage(proj.damage, proj.x, this)
                explodeProjectile(proj)
                projectiles.removeAt(i)
                continue
            }
            i++
        }
    }

    private fun explodeProjectile(proj: Projectile) {
        when (proj.type) {
            ProjectileType.FLASK -> {
                audio.play("explode")
                camera.shake(6f)
                particles.spawnExplosion(proj.x + proj.w / 2f, proj.y + proj.h / 2f)
                val aoeX = proj.x - 45f
                val aoeY = proj.y - 45f
                val aoeW = 90f
                val aoeH = 90f
                val hitId = ++attackCounter

                for (e in enemies) {
                    if (!e.dead && aabbIntersects(aoeX, aoeY, aoeW, aoeH, e.x, e.y, e.w, e.h)) {
                        e.takeDamage(45f, hitId, aoeX + aoeW / 2f, this)
                    }
                }
                if (boss.active && !boss.dead && aabbIntersects(aoeX, aoeY, aoeW, aoeH, boss.x, boss.y, boss.w, boss.h)) {
                    boss.takeDamage(45f, hitId, aoeX + aoeW / 2f, this)
                }
                for (b in breakables) {
                    if (!b.dead && aabbIntersects(aoeX, aoeY, aoeW, aoeH, b.x, b.y, b.w, b.h)) {
                        b.dead = true
                        audio.play("hit")
                        particles.spawn(b.x + 12f, b.y + 12f, Color(0xFFB39060), 12, 110f, 0.35f, 3.5f)
                        pickups.spawn(b.x + 4f, b.y, b.dropType)
                    }
                }
            }
            ProjectileType.BOSS_MAGMA -> {
                audio.play("hit")
                particles.spawn(proj.x, proj.y, Color(0xFFFF4020), 14, 150f, 0.35f, 4f)
            }
            ProjectileType.CULTIST_ORB -> {
                audio.play("hit")
                particles.spawn(proj.x, proj.y, Color(0xFF40FF80), 10, 120f, 0.3f, 3.5f)
            }
            ProjectileType.SHOCKWAVE -> {}
        }
    }

    private fun updatePickups(dt: Float) {
        var i = 0
        while (i < pickups.activeCount) {
            val pk = pickups.get(i)
            pk.vy += 800f * dt
            pk.y += pk.vy * dt

            for (p in platforms) {
                if (aabbIntersects(pk.x, pk.y, pk.w, pk.h, p.x, p.y, p.w, p.h)) {
                    pk.y = p.y - pk.h
                    pk.vy = 0f
                }
            }

            if (aabbIntersects(pk.x, pk.y, pk.w, pk.h, player.x, player.y, player.w, player.h)) {
                if (pk.type == "health") {
                    player.hp = min(player.maxHp, player.hp + 35f)
                } else {
                    player.flasks = min(player.maxFlasks, player.flasks + 2)
                }
                audio.play("pickup")
                particles.spawn(
                    pk.x + 8f, pk.y + 8f,
                    if (pk.type == "health") Color(0xFFFF3050) else Color(0xFF40FFB0),
                    12, 110f, 0.4f, 3.5f
                )
                pickups.removeAt(i)
                continue
            }
            i++
        }
    }
}
