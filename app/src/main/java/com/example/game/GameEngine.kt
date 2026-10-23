package com.example.game

import androidx.compose.ui.graphics.Color
import com.example.game.boss.BaseBoss
import com.example.game.boss.BossState
import com.example.game.boss.CrucibleChimeraBoss
import com.example.game.status.CombatStatusType
import com.example.game.status.StatusEffectController
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

        x = x.coerceIn(0f, maxW - viewW)
        y = y.coerceIn(0f, maxH - viewH)

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

    var maxHp: Float = 100f
    var hp: Float = 100f
    var maxFlasks: Int = 3
    var flasks: Int = 3

    val runSpeed: Float = 190f
    val airSpeed: Float = 160f
    val jumpForce: Float = -500f
    val gravity: Float = 1050f
    var coyoteTime: Float = 0f
    var jumpBuffer: Float = 0f

    var attacking: Boolean = false
    var attackTimer: Float = 0f
    var attackDuration: Float = 0.22f
    var attackHitboxActive: Boolean = false
    var attackId: Long = 0L
    var subCooldown: Float = 0f

    // 3-Hit Melee Combo System
    var comboIndex: Int = 0 // 0 = Slash 1, 1 = Slash 2, 2 = Finisher
    var comboResetTimer: Float = 0f
    val maxComboResetTimer: Float = 0.65f
    var lastComboHit: Int = 1 // 1, 2, or 3
    var comboFinisherFlashTimer: Float = 0f

    var invulnTimer: Float = 0f
    var knockbackTimer: Float = 0f
    var dead: Boolean = false
    var respawnTimer: Float = 0f
    var walkAnimTimer: Float = 0f

    // Combat status effect controller (debuffs, DoTs, Corrosion, Alchemy-Burn)
    val statusController = StatusEffectController()

    // Shadow Dash & Alchemical Riposte
    var isDashing: Boolean = false
    var dashTimer: Float = 0f
    val dashDuration: Float = 0.22f
    var dashCooldown: Float = 0f
    val maxDashCooldown: Float = 0.85f
    val dashSpeed: Float = 440f
    var dashDir: Int = 1
    val afterimages = ArrayList<PlayerAfterimage>()
    var perfectRiposteTimer: Float = 0f
    var riposteNotice: String = ""

    fun takeDamage(amount: Float, fromX: Float, engine: GameEngine) {
        if (invulnTimer > 0f || dead) return
        val finalAmount = amount * engine.defenseMultiplier * statusController.getIncomingDamageMultiplier()
        hp -= finalAmount
        engine.audio.play("hurt")
        engine.haptics?.onPlayerDamage()
        engine.camera.shake(8f)
        engine.particles.spawn(x + w / 2f, y + h / 2f, Color(0xFFE03030), 14, 140f, 0.4f, 4f)

        // Spawn floating damage text on player
        engine.damageNumbers.spawn(
            x = x + w / 2f,
            y = y + 4f,
            text = finalAmount.toInt().toString(),
            color = Color(0xFFFF5252)
        )

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
        comboIndex = 0
        comboResetTimer = 0f
        lastComboHit = 1
        comboFinisherFlashTimer = 0f
        isDashing = false
        dashTimer = 0f
        dashCooldown = 0f
        afterimages.clear()
        perfectRiposteTimer = 0f
        riposteNotice = ""
        statusController.clear()
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
    var startX: Float = x
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
    var poise: Float
    val maxPoise: Float
    var isStaggered: Boolean = false
    var staggerTimer: Float = 0f
    var shootCooldown: Float = 2.0f
    var charging: Boolean = false
    var flyPhase: Float = (Math.random() * Math.PI * 2.0).toFloat()
    var targetY: Float = y

    // Combat status effect controller (debuffs, DoTs, Corrosion, Alchemy-Burn)
    val statusController = StatusEffectController()

    init {
        when (type) {
            EnemyArchetype.LURKER -> {
                w = 30f; h = 36f; hp = 45f; maxHp = 45f; speed = 90f; state = "patrol"
                maxPoise = 30f; poise = 30f
            }
            EnemyArchetype.CHERUB -> {
                w = 26f; h = 24f; hp = 30f; maxHp = 30f; speed = 110f; state = "fly"
                maxPoise = 20f; poise = 20f
            }
            EnemyArchetype.CULTIST -> {
                w = 28f; h = 42f; hp = 50f; maxHp = 50f; speed = 0f; state = "idle"
                maxPoise = 25f; poise = 25f
            }
            EnemyArchetype.GOLEM -> {
                w = 44f; h = 54f; hp = 130f; maxHp = 130f; speed = 45f; state = "patrol"
                maxPoise = 85f; poise = 85f
            }
        }
    }

    fun takeDamage(
        amount: Float,
        aId: Long,
        fromX: Float,
        engine: GameEngine,
        poiseDamage: Float = 15f,
        knockbackMult: Float = 1.0f,
        isCrit: Boolean = false
    ) {
        if (lastHitId == aId || dead) return
        lastHitId = aId
        val finalAmount = amount * statusController.getIncomingDamageMultiplier()
        hp -= finalAmount
        hurtTimer = 0.22f
        engine.audio.play("hit")
        engine.particles.spawnEnemyHurt(x + w / 2f, y + h / 2f, if (isCrit) Color(0xFFFFD700) else Color(0xFFB02040))

        // Directional knockback
        val dir = if (fromX > x + w / 2f) -1 else 1
        val baseKnock = if (type == EnemyArchetype.GOLEM) 40f else 125f
        vx = dir * baseKnock * knockbackMult
        if (knockbackMult > 1.4f) {
            vy = -140f // Lift enemy upward on combo finisher
        }

        // Spawn Floating Combat Text damage number
        engine.damageNumbers.spawn(
            x = x + w / 2f,
            y = y + 4f,
            text = finalAmount.toInt().toString() + (if (isCrit) "!" else ""),
            color = if (isCrit) Color(0xFFFFD700) else if (knockbackMult > 1.4f) Color(0xFFFF3B30) else Color(0xFFFFF275),
            isCrit = isCrit || knockbackMult > 1.4f
        )

        // Poise damage & Stagger break
        poise -= poiseDamage
        if (poise <= 0f && !isStaggered && !dead) {
            poise = 0f
            isStaggered = true
            staggerTimer = 0.85f
            engine.audio.play("hit")
            engine.damageNumbers.spawn(
                x = x + w / 2f,
                y = y - 10f,
                text = "STAGGERED!",
                color = Color(0xFFFFD700),
                isCrit = true
            )
            engine.particles.spawnExplosion(x + w / 2f, y + h / 2f)
        }

        if (hp <= 0f) {
            dead = true
            engine.enemiesDefeated++
            engine.particles.spawnEnemyDeath(x + w / 2f, y + h / 2f, Color(0xFF8018A0))
            if (Math.random() < 0.70) {
                val r = Math.random()
                val dropType = when {
                    r < 0.20 -> "health"
                    r < 0.38 -> "flask"
                    r < 0.65 -> "sulfur"
                    r < 0.88 -> "mercury"
                    else -> "vitriol"
                }
                engine.pickups.spawn(x + w / 2f - 8f, y + h / 2f - 8f, dropType)
            }
        }
    }
}

typealias BossEntity = BaseBoss

class GameEngine {
    val audio = GameAudio()
    val camera = Camera2D()
    val particles = ParticlePool(160)
    val projectiles = ProjectilePool(40)
    val pickups = PickupPool(24)
    val damageNumbers = DamageNumberPool(36)

    val levelWidth = 6200f + RuinedBastionDistrict.HORIZONTAL_OFFSET
    val levelHeight = 840f

    var state: String = "playing"
    var timeElapsed: Float = 0f
    var enemiesDefeated: Int = 0

    val checkpoints = listOf(
        Checkpoint(0, 80f, RuinedBastionDistrict.worldY(380f), "Section 1: Lower Bastion City", true),
        Checkpoint(1, 1980f + RuinedBastionDistrict.HORIZONTAL_OFFSET, 340f, "Section 2: Crucible Laboratories", false),
        Checkpoint(2, 4400f + RuinedBastionDistrict.HORIZONTAL_OFFSET, 340f, "Section 3: Sanctum Approach", false)
    )
    var activeCheckpoint: Checkpoint = checkpoints[0]

    val platforms = ArrayList<Platform>()
    val hazards = ArrayList<Hazard>()
    val breakables = ArrayList<BreakableUrn>()
    val enemies = ArrayList<EnemyEntity>()
    var boss: BaseBoss = CrucibleChimeraBoss(5750f + RuinedBastionDistrict.HORIZONTAL_OFFSET, 360f)

    val player: PlayerEntity = PlayerEntity(activeCheckpoint.x, activeCheckpoint.y)

    var inputLeft: Boolean = false
    var inputRight: Boolean = false
    var inputJump: Boolean = false
    var inputAttack: Boolean = false
    var inputSub: Boolean = false
    var inputDash: Boolean = false

    var perfectDodgesCount: Int = 0
    var timeDilation: Float = 1.0f
    var timeDilationTimer: Float = 0f

    private var attackCounter: Long = 1L
    private var cityShortcutUnlocked: Boolean = false

    var haptics: GameHaptics? = null
    var damageMultiplier: Float = 1.0f
    var defenseMultiplier: Float = 1.0f
    var speedMultiplier: Float = 1.0f
    var onResourceCollected: ((com.example.game.alchemy.ResourceType) -> Unit)? = null
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
        perfectDodgesCount = 0
        timeDilation = 1.0f
        timeDilationTimer = 0f
    }

    fun initLevel() {
        platforms.clear()
        hazards.clear()
        breakables.clear()
        pickups.clear()
        enemies.clear()
        projectiles.clear()

        // --- SECTION 1: LOWER BASTION CITY (x: 0 - 1900) ---
        // The district is authored separately so the geometry can evolve without
        // mixing it into combat, boss, and later-section setup.
        cityShortcutUnlocked = false
        RuinedBastionDistrict.populate(platforms, breakables, enemies)
        val cityPlatformCount = platforms.size
        val cityHazardCount = hazards.size
        val cityBreakableCount = breakables.size
        val cityEnemyCount = enemies.size

        // --- SECTION 2: THE CRUCIBLE LABORATORIES (x: 1900 - 4300) ---
        platforms.add(Platform(1920f, 420f, 380f, 120f, PlatformStyle.STONE))

        platforms.add(Platform(2360f, 460f, 120f, 80f, PlatformStyle.STONE))
        hazards.add(Hazard(2300f, 500f, 60f, 40f, HazardType.ACID))
        platforms.add(Platform(2540f, 400f, 130f, 20f, PlatformStyle.IRON_GIRDER))
        platforms.add(Platform(2730f, 340f, 140f, 20f, PlatformStyle.IRON_GIRDER))
        hazards.add(Hazard(2480f, 510f, 350f, 30f, HazardType.ACID))

        platforms.add(Platform(2940f, 260f, 160f, 280f, PlatformStyle.STONE))
        enemies.add(EnemyEntity(3000f, 210f, EnemyArchetype.CULTIST, 0f))

        platforms.add(Platform(3100f, 430f, 650f, 110f, PlatformStyle.STONE))
        enemies.add(EnemyEntity(3300f, 370f, EnemyArchetype.GOLEM, 150f))
        enemies.add(EnemyEntity(3560f, 380f, EnemyArchetype.LURKER, 90f))

        platforms.add(Platform(3250f, 320f, 140f, 20f, PlatformStyle.IRON_GIRDER))
        platforms.add(Platform(3440f, 240f, 160f, 20f, PlatformStyle.IRON_GIRDER))
        breakables.add(BreakableUrn(3500f, 216f, dropType = "flask"))
        enemies.add(EnemyEntity(3480f, 190f, EnemyArchetype.CHERUB, 100f))

        platforms.add(Platform(3800f, 440f, 200f, 100f, PlatformStyle.STONE))
        hazards.add(Hazard(4000f, 500f, 80f, 40f, HazardType.ACID))
        platforms.add(Platform(4080f, 420f, 240f, 120f, PlatformStyle.STONE))
        enemies.add(EnemyEntity(4140f, 360f, EnemyArchetype.CULTIST, 0f))

        // --- SECTION 3: THE SANCTUM APPROACH (x: 4300 - 5100) ---
        platforms.add(Platform(4360f, 420f, 320f, 120f, PlatformStyle.STONE))

        platforms.add(Platform(4740f, 360f, 110f, 20f, PlatformStyle.STONE_LEDGE))
        hazards.add(Hazard(4680f, 510f, 260f, 30f, HazardType.ACID))
        platforms.add(Platform(4900f, 300f, 120f, 20f, PlatformStyle.STONE_LEDGE))
        enemies.add(EnemyEntity(4820f, 220f, EnemyArchetype.CHERUB, 130f))

        platforms.add(Platform(5070f, 430f, 200f, 110f, PlatformStyle.STONE))
        enemies.add(EnemyEntity(5120f, 370f, EnemyArchetype.GOLEM, 70f))
        breakables.add(BreakableUrn(5180f, 406f, dropType = "health"))

        // --- BOSS ARENA: GRAND SANCTUM (x: 5270 - 6200) ---
        platforms.add(Platform(5270f, 450f, 930f, 90f, PlatformStyle.STONE))
        platforms.add(Platform(5270f, 100f, 30f, 350f, PlatformStyle.BOSS_GATE))
        platforms.add(Platform(6170f, 100f, 30f, 350f, PlatformStyle.BOSS_GATE))
        platforms.add(Platform(5420f, 330f, 120f, 18f, PlatformStyle.IRON_GIRDER))
        platforms.add(Platform(5880f, 330f, 120f, 18f, PlatformStyle.IRON_GIRDER))

        boss = CrucibleChimeraBoss(5750f + RuinedBastionDistrict.HORIZONTAL_OFFSET, 360f)
        // Keep later rooms' internal shapes, shifting them to the right of the expanded city.
        val cityOffset = RuinedBastionDistrict.HORIZONTAL_OFFSET
        for (i in cityPlatformCount until platforms.size) platforms[i] = platforms[i].copy(x = platforms[i].x + cityOffset)
        for (i in cityHazardCount until hazards.size) hazards[i] = hazards[i].copy(x = hazards[i].x + cityOffset)
        for (i in cityBreakableCount until breakables.size) breakables[i].x += cityOffset
        for (i in cityEnemyCount until enemies.size) {
            enemies[i].x += cityOffset
            enemies[i].startX += cityOffset
        }
        damageNumbers.clear()
    }

    fun onJumpPressed() {
        player.jumpBuffer = 0.15f
    }

    fun onAttackPressed() {
        if (!player.attacking && !player.dead && player.knockbackTimer <= 0f) {
            val combo = player.comboIndex // 0, 1, or 2
            player.lastComboHit = combo + 1 // 1, 2, or 3

            // Attack timings per combo hit
            player.attackDuration = when (combo) {
                0 -> 0.22f // Hit 1: quick jab
                1 -> 0.26f // Hit 2: horizontal cleave
                else -> 0.35f // Hit 3: crucible heavy finisher
            }
            player.attacking = true
            player.attackTimer = player.attackDuration
            player.attackId = ++attackCounter
            audio.play("slash")
            haptics?.onMeleeSwing()

            // Subtle forward step surge on combo hits 2 & 3
            if (combo > 0) {
                player.vx += player.facing * (if (combo == 1) 75f else 130f)
            }
            if (combo == 2) {
                player.comboFinisherFlashTimer = 0.35f
                camera.shake(9f)
            }

            // Advance combo chain & set reset window
            player.comboResetTimer = player.maxComboResetTimer
            player.comboIndex = (combo + 1) % 3

            // Visual particle arc based on combo stage
            val arcColor = when {
                damageMultiplier > 1f -> Color(0xFFFF5722)
                combo == 2 -> Color(0xFFFF2040) // Finisher: fiery crimson
                combo == 1 -> Color(0xFFFFB300) // Hit 2: radiant amber
                else -> Color(0xFF00E5FF) // Hit 1: electric cyan
            }
            particles.spawnSlashArc(
                if (player.facing > 0) player.x + player.w else player.x,
                player.y + player.h / 2f,
                player.facing,
                arcColor
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

    fun onDashPressed() {
        if (player.dashCooldown > 0f || player.dead || player.knockbackTimer > 0f) return

        player.isDashing = true
        player.dashTimer = player.dashDuration
        player.dashCooldown = player.maxDashCooldown / (if (speedMultiplier > 1f) 1.20f else 1.0f)
        val moveDir = when {
            inputLeft -> -1
            inputRight -> 1
            else -> player.facing
        }
        player.dashDir = moveDir
        player.facing = moveDir
        player.invulnTimer = 0.22f

        audio.play("dash")
        particles.spawn(
            player.x + player.w / 2f,
            player.y + player.h / 2f,
            Color(0xFF80D8FF),
            count = 10,
            speed = 120f,
            life = 0.3f,
            size = 3.5f
        )

        checkPerfectRiposte()
    }

    private fun checkPerfectRiposte() {
        var riposteTriggered = false
        val px = player.x + player.w / 2f
        val py = player.y + player.h / 2f

        // 1. Check incoming enemy projectiles (<95f)
        for (i in projectiles.activeCount - 1 downTo 0) {
            val proj = projectiles.get(i)
            if (!proj.senderIsPlayer) {
                val dist = kotlin.math.hypot(proj.x - px, proj.y - py)
                if (dist < 95f) {
                    riposteTriggered = true
                    explodeProjectile(proj)
                    projectiles.removeAt(i)
                    break
                }
            }
        }

        // 2. Check nearby active enemies (<90f)
        for (e in enemies) {
            if (!e.dead) {
                val dist = kotlin.math.hypot(e.x + e.w / 2f - px, e.y + e.h / 2f - py)
                if (dist < 90f) {
                    riposteTriggered = true
                    e.takeDamage(42f * damageMultiplier, 88888L + attackCounter++, px, this)
                    e.statusController.applyEffect(CombatStatusType.CORROSION, duration = 6.0f, stacks = 2)
                    e.statusController.applyEffect(CombatStatusType.ALCHEMY_BURN, duration = 5.0f, stacks = 1)
                    break
                }
            }
        }

        // 3. Check Boss if telegraphing or attacking nearby (<140f)
        if (boss.active && !boss.dead) {
            val bossDist = kotlin.math.hypot(boss.x + boss.w / 2f - px, boss.y + boss.h / 2f - py)
            if (bossDist < 140f && (boss.state == BossState.TELEGRAPHING || boss.state == BossState.ATTACKING)) {
                riposteTriggered = true
                val riposteDmg = 55f * damageMultiplier
                boss.takeDamage(riposteDmg, 77777L + attackCounter++, px, this)
                boss.statusController.applyEffect(CombatStatusType.CORROSION, duration = 6.0f, stacks = 2)
                boss.statusController.applyEffect(CombatStatusType.ALCHEMY_BURN, duration = 5.0f, stacks = 2)
            }
        }

        if (riposteTriggered) {
            perfectDodgesCount++
            player.perfectRiposteTimer = 1.3f
            player.riposteNotice = "⚡ PERFECT RIPOSTE! FLASK RESTORED ⚡"

            if (player.flasks < player.maxFlasks) {
                player.flasks++
            }

            audio.play("riposte")
            haptics?.onBossHit()
            camera.shake(10f)

            timeDilation = 0.32f
            timeDilationTimer = 0.22f

            particles.spawn(px, py, Color(0xFFFFD700), count = 22, speed = 180f, life = 0.45f, size = 4.5f)
            particles.spawn(px, py, Color(0xFF00E5FF), count = 16, speed = 150f, life = 0.40f, size = 4.0f)
        }
    }

    fun update(rawDt: Float) {
        if (state == "paused") return

        // Time dilation (slow-mo on perfect riposte)
        if (timeDilationTimer > 0f) {
            timeDilationTimer -= rawDt
            if (timeDilationTimer <= 0f) {
                timeDilation = 1.0f
            }
        }
        val dt = rawDt * timeDilation
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

        // Boss trigger
        if (!boss.active && player.x > 5340f + RuinedBastionDistrict.HORIZONTAL_OFFSET) {
            boss.active = true
            audio.play("boss_roar")
            camera.shake(12f)
        }

        updatePlayer(dt)
        updateCityShortcut()
        updateEnemies(dt)
        updateBoss(dt)
        updateProjectiles(dt)
        updatePickups(dt)
        particles.update(dt)
        damageNumbers.update(dt)

        camera.update(player.x, player.y, player.facing, levelWidth, levelHeight, dt)
    }

    private fun updateCityShortcut() {
        if (cityShortcutUnlocked || player.dead) return

        // The gate is blocked from the street. Reaching its far side by the
        // rooftop route opens it, creating a permanent shortcut for this run.
        if (player.x >= RuinedBastionDistrict.shortcutUnlockMinX &&
            player.x < RuinedBastionDistrict.shortcutUnlockMaxX &&
            player.y >= RuinedBastionDistrict.worldY(390f)) {
            cityShortcutUnlocked = true
            platforms.remove(RuinedBastionDistrict.shortcutGate)
            audio.play("checkpoint")
            camera.shake(4f)
            particles.spawn(
                RuinedBastionDistrict.shortcutGate.x + RuinedBastionDistrict.shortcutGate.w / 2f,
                RuinedBastionDistrict.shortcutGate.y + RuinedBastionDistrict.shortcutGate.h / 2f,
                Color(0xFFFFD700),
                count = 16,
                speed = 105f,
                life = 0.45f,
                size = 3.5f
            )
        }
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
        if (player.dashCooldown > 0f) player.dashCooldown = (player.dashCooldown - dt).coerceAtLeast(0f)
        if (player.perfectRiposteTimer > 0f) player.perfectRiposteTimer = (player.perfectRiposteTimer - dt).coerceAtLeast(0f)
        if (player.comboResetTimer > 0f) {
            player.comboResetTimer = (player.comboResetTimer - dt).coerceAtLeast(0f)
            if (player.comboResetTimer <= 0f) {
                player.comboIndex = 0
            }
        }
        if (player.comboFinisherFlashTimer > 0f) {
            player.comboFinisherFlashTimer = (player.comboFinisherFlashTimer - dt).coerceAtLeast(0f)
        }

        // Update shadow afterimages
        val imgIter = player.afterimages.iterator()
        while (imgIter.hasNext()) {
            val img = imgIter.next()
            img.alpha -= dt * 3.8f
            if (img.alpha <= 0f) imgIter.remove()
        }

        // Shadow Dash Movement & i-frames
        if (player.isDashing) {
            player.dashTimer -= dt
            player.invulnTimer = 0.16f
            player.vx = player.dashDir * player.dashSpeed * (if (speedMultiplier > 1f) 1.15f else 1.0f)
            player.vy = 0f

            if (player.afterimages.isEmpty() || kotlin.math.abs(player.afterimages.last().x - player.x) > 24f) {
                player.afterimages.add(
                    PlayerAfterimage(
                        x = player.x,
                        y = player.y,
                        facing = player.facing,
                        alpha = 0.75f,
                        color = Color(0xFF00E5FF)
                    )
                )
            }

            particles.spawn(
                player.x + player.w / 2f,
                player.y + player.h / 2f,
                Color(0xFF00E5FF),
                count = 2,
                speed = 40f,
                life = 0.2f,
                size = 2.8f
            )

            if (player.dashTimer <= 0f) {
                player.isDashing = false
                player.vx *= 0.4f
            }
        }

        // Process active status debuffs (Alchemy-Burn, Corrosion)
        player.statusController.update(dt) { type, damage, stacks ->
            player.hp -= damage
            particles.spawn(
                player.x + (Math.random().toFloat() * player.w),
                player.y + (Math.random().toFloat() * player.h),
                type.primaryColor,
                count = 4,
                speed = 70f,
                life = 0.35f,
                size = 3.2f
            )
            if (player.hp <= 0f && !player.dead) {
                player.hp = 0f
                player.dead = true
                player.respawnTimer = 1.4f
                particles.spawnEnemyDeath(player.x + player.w / 2f, player.y + player.h / 2f, Color(0xFF8020A0))
            }
        }

        // Ambient status particle trails on player
        if (player.statusController.hasEffect(CombatStatusType.ALCHEMY_BURN)) {
            particles.spawn(
                player.x + (Math.random().toFloat() * player.w),
                player.y + player.h - 6f,
                CombatStatusType.ALCHEMY_BURN.primaryColor,
                count = 1,
                speed = 35f,
                life = 0.3f,
                size = 2.6f
            )
        }
        if (player.statusController.hasEffect(CombatStatusType.CORROSION)) {
            particles.spawn(
                player.x + (Math.random().toFloat() * player.w),
                player.y + (Math.random().toFloat() * player.h),
                CombatStatusType.CORROSION.primaryColor,
                count = 1,
                speed = 25f,
                life = 0.28f,
                size = 2.4f
            )
        }

        // Horizontal Movement (adjusted by speedMultiplier and Corrosion debuff penalty)
        if (player.knockbackTimer <= 0f && !player.isDashing) {
            var moveDir = 0
            if (inputLeft) moveDir -= 1
            if (inputRight) moveDir += 1

            if (moveDir != 0) {
                player.facing = moveDir
                val baseSpeed = if (player.grounded) player.runSpeed else player.airSpeed
                val effectiveSpeedMult = speedMultiplier * player.statusController.getSpeedMultiplier()
                val targetSpeed = moveDir * baseSpeed * effectiveSpeedMult
                player.vx += (targetSpeed - player.vx) * (if (player.grounded) 14f else 7f) * dt
                player.walkAnimTimer += dt * 10f
            } else {
                player.vx += (0f - player.vx) * (if (player.grounded) 18f else 6f) * dt
                player.walkAnimTimer = 0f
            }

            // Jump
            if (player.jumpBuffer > 0f && player.coyoteTime > 0f) {
                player.vy = player.jumpForce * (if (speedMultiplier > 1f) 1.12f else 1.0f)
                player.grounded = false
                player.coyoteTime = 0f
                player.jumpBuffer = 0f
                audio.play("jump")
                val jumpColor = if (speedMultiplier > 1f) Color(0xFF00E5FF) else Color(0xFF8B7DA8)
                particles.spawn(player.x + player.w / 2f, player.y + player.h, jumpColor, 6, 60f, 0.25f, 2.5f)
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
            // City ledges are one-way: don't let their sides or undersides
            // interfere with movement while the player is below them.
            if (p.style == PlatformStyle.CITY_LEDGE) continue
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
        val previousY = player.y
        player.y += player.vy * dt
        player.grounded = false
        for (p in platforms) {
            if (p.style == PlatformStyle.CITY_LEDGE) {
                val previousBottom = previousY + player.h
                val currentBottom = player.y + player.h
                val crossedTopWhileFalling =
                    player.vy >= 0f &&
                    player.y >= previousY &&
                    previousBottom <= p.y &&
                    currentBottom >= p.y &&
                    player.x + player.w > p.x &&
                    player.x < p.x + p.w

                if (crossedTopWhileFalling) {
                    player.y = p.y - player.h
                    player.vy = 0f
                    player.grounded = true
                    player.coyoteTime = 0.12f
                }
                continue
            }

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
            val comboStep = player.lastComboHit
            val isFinisher = comboStep == 3
            val slashX = if (player.facing > 0) player.x + player.w - 4f else player.x - (if (isFinisher) 42f else 34f)
            val slashY = player.y + 4f
            val slashW = if (isFinisher) 46f else 36f
            val slashH = player.h - 8f

            // Base damage, poise damage, and knockback scaling per combo step
            val (baseDmg, poiseDmg, knockMult) = when (comboStep) {
                1 -> Triple(22f, 15f, 0.9f)
                2 -> Triple(30f, 22f, 1.3f)
                else -> Triple(52f, 45f, 2.1f) // Crucible Heavy Finisher
            }
            val finalDmg = baseDmg * damageMultiplier

            for (e in enemies) {
                if (!e.dead && aabbIntersects(slashX, slashY, slashW, slashH, e.x, e.y, e.w, e.h)) {
                    e.takeDamage(
                        amount = finalDmg,
                        aId = player.attackId,
                        fromX = player.x,
                        engine = this,
                        poiseDamage = poiseDmg,
                        knockbackMult = knockMult,
                        isCrit = isFinisher
                    )
                    camera.shake(if (isFinisher) 9f else if (comboStep == 2) 5f else 3.5f)
                    haptics?.onMeleeHit()

                    if (isFinisher) {
                        particles.spawnExplosion(e.x + e.w / 2f, e.y + e.h / 2f)
                    }

                    // Player melee applies status debuffs (higher chance on finisher)
                    val burnChance = if (isFinisher) 0.65 else 0.40
                    val corrChance = if (isFinisher) 0.55 else 0.35
                    if (Math.random() < burnChance) {
                        e.statusController.applyEffect(CombatStatusType.ALCHEMY_BURN, duration = 4.5f, stacks = 1)
                    }
                    if (Math.random() < corrChance) {
                        e.statusController.applyEffect(CombatStatusType.CORROSION, duration = 5.0f, stacks = 1)
                    }
                }
            }
            if (boss.active && !boss.dead && aabbIntersects(slashX, slashY, slashW, slashH, boss.x, boss.y, boss.w, boss.h)) {
                val isWeakPoint = boss.weakPoint.intersects(slashX, slashY, slashW, slashH, boss.x, boss.y, boss.w, boss.facing)
                val critMult = if (isWeakPoint) boss.weakPoint.damageMultiplier else 1.0f
                val bossDmg = finalDmg * critMult

                boss.takeDamage(bossDmg, player.attackId, player.x, this)
                camera.shake(if (isFinisher) 10f else if (isWeakPoint) 7f else 4.5f)
                haptics?.onBossHit()

                // Spawn floating combat text on boss
                damageNumbers.spawn(
                    x = boss.x + boss.w / 2f + (kotlin.random.Random.nextFloat() - 0.5f) * 20f,
                    y = boss.y + 10f,
                    text = bossDmg.toInt().toString() + (if (isWeakPoint || isFinisher) "!" else ""),
                    color = if (isWeakPoint) Color(0xFFFFD700) else if (isFinisher) Color(0xFFFF3B30) else Color(0xFFFFF275),
                    isCrit = isWeakPoint || isFinisher
                )

                if (isWeakPoint && boss.weakPoint.isBroken) {
                    damageNumbers.spawn(
                        x = boss.x + boss.w / 2f,
                        y = boss.y - 12f,
                        text = "CORE SHATTER!",
                        color = Color(0xFFFFD700),
                        isCrit = true
                    )
                }

                if (isFinisher) {
                    particles.spawnExplosion(boss.x + boss.w / 2f, boss.y + boss.h / 2f)
                }

                // Player melee applies status debuffs to boss
                val burnChance = if (isFinisher) 0.65 else 0.45
                val corrChance = if (isFinisher) 0.55 else 0.40
                if (Math.random() < burnChance) {
                    boss.statusController.applyEffect(CombatStatusType.ALCHEMY_BURN, duration = 4.5f, stacks = 1)
                }
                if (Math.random() < corrChance) {
                    boss.statusController.applyEffect(CombatStatusType.CORROSION, duration = 5.0f, stacks = 1)
                }
            }
            for (b in breakables) {
                if (!b.dead && aabbIntersects(slashX, slashY, slashW, slashH, b.x, b.y, b.w, b.h)) {
                    b.dead = true
                    audio.play("hit")
                    haptics?.onMeleeHit()
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

            // Update enemy status effects (Alchemy-Burn, Corrosion)
            e.statusController.update(dt) { type, damage, stacks ->
                e.hp -= damage
                e.hurtTimer = 0.08f
                particles.spawn(
                    e.x + (Math.random().toFloat() * e.w),
                    e.y + (Math.random().toFloat() * e.h),
                    type.primaryColor,
                    count = 3,
                    speed = 55f,
                    life = 0.25f,
                    size = 3f
                )
                if (e.hp <= 0f && !e.dead) {
                    e.dead = true
                    enemiesDefeated++
                    particles.spawnEnemyDeath(e.x + e.w / 2f, e.y + e.h / 2f, Color(0xFF8018A0))
                    if (Math.random() < 0.70) {
                        val r = Math.random()
                        val dropType = when {
                            r < 0.20 -> "health"
                            r < 0.38 -> "flask"
                            r < 0.65 -> "sulfur"
                            r < 0.88 -> "mercury"
                            else -> "vitriol"
                        }
                        pickups.spawn(e.x + e.w / 2f - 8f, e.y + e.h / 2f - 8f, dropType)
                    }
                }
            }
            if (e.dead) continue

            // Ambient status particle trail on enemies
            if (e.statusController.hasEffect(CombatStatusType.ALCHEMY_BURN)) {
                particles.spawn(e.x + e.w / 2f, e.y + e.h - 6f, CombatStatusType.ALCHEMY_BURN.primaryColor, 1, 25f, 0.25f, 2.2f)
            }
            if (e.statusController.hasEffect(CombatStatusType.CORROSION)) {
                particles.spawn(e.x + e.w / 2f, e.y + e.h / 2f, CombatStatusType.CORROSION.primaryColor, 1, 20f, 0.25f, 2.0f)
            }

            if (e.isStaggered) {
                e.staggerTimer -= dt
                e.vx = 0f
                if (e.staggerTimer <= 0f) {
                    e.isStaggered = false
                    e.poise = e.maxPoise
                }
            } else {
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

            if (!e.isStaggered && aabbIntersects(e.x, e.y, e.w, e.h, player.x, player.y, player.w, player.h)) {
                if (!player.isDashing && player.invulnTimer <= 0f) {
                    player.takeDamage(if (e.type == EnemyArchetype.GOLEM) 24f else 16f, e.x + e.w / 2f, this)
                    if (e.type == EnemyArchetype.GOLEM) {
                        player.statusController.applyEffect(CombatStatusType.CORROSION, duration = 5.0f, stacks = 1)
                    } else if (e.type == EnemyArchetype.CHERUB) {
                        player.statusController.applyEffect(CombatStatusType.ALCHEMY_BURN, duration = 4.0f, stacks = 1)
                    }
                }
            }
        }
    }

    private fun updateBoss(dt: Float) {
        boss.update(dt, this)
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
                // Projectile combat status infliction
                when (proj.type) {
                    ProjectileType.BOSS_MAGMA -> {
                        player.statusController.applyEffect(CombatStatusType.ALCHEMY_BURN, duration = 5.0f, stacks = 1)
                    }
                    ProjectileType.SHOCKWAVE -> {
                        player.statusController.applyEffect(CombatStatusType.CORROSION, duration = 4.5f, stacks = 1)
                    }
                    ProjectileType.CULTIST_ORB -> {
                        player.statusController.applyEffect(CombatStatusType.CORROSION, duration = 4.0f, stacks = 1)
                    }
                    else -> {}
                }
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
                haptics?.onFlaskExplosion()
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
                        // Volatile Flask explosion saturates targets with Burn & Corrosion
                        e.statusController.applyEffect(CombatStatusType.ALCHEMY_BURN, duration = 5.5f, stacks = 2)
                        e.statusController.applyEffect(CombatStatusType.CORROSION, duration = 5.0f, stacks = 1)
                    }
                }
                if (boss.active && !boss.dead && aabbIntersects(aoeX, aoeY, aoeW, aoeH, boss.x, boss.y, boss.w, boss.h)) {
                    boss.takeDamage(45f, hitId, aoeX + aoeW / 2f, this)
                    boss.statusController.applyEffect(CombatStatusType.ALCHEMY_BURN, duration = 5.5f, stacks = 2)
                    boss.statusController.applyEffect(CombatStatusType.CORROSION, duration = 5.0f, stacks = 1)
                    haptics?.onBossHit()
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
                var isResource = false
                when (pk.type) {
                    "health" -> player.hp = min(player.maxHp, player.hp + 35f)
                    "flask" -> player.flasks = min(player.maxFlasks, player.flasks + 2)
                    "sulfur" -> {
                        isResource = true
                        onResourceCollected?.invoke(com.example.game.alchemy.ResourceType.SULFUR_ASH)
                    }
                    "mercury" -> {
                        isResource = true
                        onResourceCollected?.invoke(com.example.game.alchemy.ResourceType.QUICKSILVER)
                    }
                    "vitriol" -> {
                        isResource = true
                        onResourceCollected?.invoke(com.example.game.alchemy.ResourceType.VITRIOL_CRYSTAL)
                    }
                }
                audio.play("pickup")
                if (isResource) {
                    haptics?.onResourceCollected()
                }
                val particleColor = when (pk.type) {
                    "health" -> Color(0xFFFF3050)
                    "flask" -> Color(0xFF40FFB0)
                    "sulfur" -> Color(0xFFFFB039)
                    "mercury" -> Color(0xFF64D8CB)
                    "vitriol" -> Color(0xFFB388FF)
                    else -> Color.White
                }
                particles.spawn(pk.x + 8f, pk.y + 8f, particleColor, 12, 110f, 0.4f, 3.5f)
                pickups.removeAt(i)
                continue
            }
            i++
        }
    }
}
