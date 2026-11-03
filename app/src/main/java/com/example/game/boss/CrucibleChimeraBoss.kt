package com.example.game.boss

import androidx.compose.ui.graphics.Color
import com.example.game.GameEngine
import com.example.game.Platform
import com.example.game.ProjectileType
import com.example.game.aabbIntersects

/**
 * Concrete implementation of the Ignis Chimera Crucible Boss using [BaseBoss].
 * Features 4 distinct telegraphed attacks, multi-phase aggression scaling,
 * and a vulnerable alchemical chest core.
 */
class CrucibleChimeraBoss(
    spawnX: Float,
    spawnY: Float
) : BaseBoss(
    x = spawnX,
    y = spawnY,
    w = 90f,
    h = 80f,
    maxHp = 380f,
    bossName = "Ignis, Alchemical Chimera",
    weakPoint = WeakPoint(
        id = "alchemical_core",
        name = "Molten Alchemical Core",
        offsetX = 34f,
        offsetY = 24f,
        width = 24f,
        height = 24f,
        damageMultiplier = 2.2f,
        maxStaggerHealth = 70f
    )
) {
    // Attack Definitions
    private val attackDash = BossAttackPattern(
        id = "dash_lunge",
        name = "Crucible Charge",
        type = TelegraphType.DASH_LUNGE,
        telegraphDuration = 0.85f,
        activeDuration = 0.80f,
        recoveryDuration = 0.70f,
        baseDamage = 26f,
        exposesWeakPoint = true,
        warningWidth = 320f,
        warningHeight = 36f,
        warningColor = Color(0xFFFF4010)
    )

    private val attackMortar = BossAttackPattern(
        id = "magma_mortar",
        name = "Volatile Mortar",
        type = TelegraphType.MAGMA_MORTAR,
        telegraphDuration = 0.90f,
        activeDuration = 0.45f,
        recoveryDuration = 0.80f,
        baseDamage = 24f,
        exposesWeakPoint = true,
        warningWidth = 140f,
        warningHeight = 120f,
        warningColor = Color(0xFFFFA500)
    )

    private val attackSlam = BossAttackPattern(
        id = "ground_slam",
        name = "Cataclysmic Leap Slam",
        type = TelegraphType.GROUND_SLAM,
        telegraphDuration = 0.75f,
        activeDuration = 1.10f,
        recoveryDuration = 0.90f,
        baseDamage = 32f,
        exposesWeakPoint = false,
        warningWidth = 240f,
        warningHeight = 40f,
        warningColor = Color(0xFFFF2040)
    )

    private val attackEruption = BossAttackPattern(
        id = "void_eruption",
        name = "Crucible Floor Eruption",
        type = TelegraphType.VOID_ERUPTION,
        telegraphDuration = 1.0f,
        activeDuration = 0.60f,
        recoveryDuration = 0.75f,
        baseDamage = 35f,
        exposesWeakPoint = true,
        warningWidth = 360f,
        warningHeight = 45f,
        warningColor = Color(0xFFC020FF)
    )

    private var isSlamFalling: Boolean = false

    override fun selectNextAttack(): BossAttackPattern {
        attackCycle++
        val attacksAvailable = when (currentPhase) {
            BossPhase.PHASE_1 -> listOf(attackDash, attackMortar, attackSlam)
            BossPhase.PHASE_2 -> listOf(attackDash, attackMortar, attackSlam, attackEruption)
            BossPhase.PHASE_3 -> listOf(attackDash, attackEruption, attackSlam, attackMortar)
        }
        return attacksAvailable[attackCycle % attacksAvailable.size]
    }

    override fun onStartAttack(attack: BossAttackPattern, engine: GameEngine) {
        val player = engine.player
        val distToPlayerX = (player.x + player.w / 2f) - (x + w / 2f)
        facing = if (distToPlayerX >= 0) 1 else -1

        when (attack.type) {
            TelegraphType.DASH_LUNGE -> {
                val speed = if (currentPhase == BossPhase.PHASE_3) 520f else if (currentPhase == BossPhase.PHASE_2) 440f else 360f
                vx = facing * speed
                engine.audio.play("boss_roar")
                engine.camera.shake(6f)
            }
            TelegraphType.MAGMA_MORTAR -> {
                vx = 0f
                engine.audio.play("explode")
                engine.camera.shake(8f)
                val count = when (currentPhase) {
                    BossPhase.PHASE_1 -> 3
                    BossPhase.PHASE_2 -> 5
                    BossPhase.PHASE_3 -> 7
                }
                for (i in 0 until count) {
                    val spread = (i - (count - 1) / 2f) * 85f
                    // Stage 6a (#38): the mortar spread rolls use the engine's
                    // seeded gameplay RNG so replays reproduce the barrage exactly.
                    val bVx = facing * (190f + engine.rng.nextFloat() * 90f) + spread
                    val bVy = -410f - engine.rng.nextFloat() * 90f
                    engine.projectiles.spawn(
                        x + w / 2f,
                        y + 10f,
                        bVx,
                        bVy,
                        ProjectileType.BOSS_MAGMA,
                        senderIsPlayer = false,
                        damage = attack.baseDamage
                    )
                }
            }
            TelegraphType.GROUND_SLAM -> {
                isSlamFalling = false
                vy = -560f
                // Stage 4 (#36): cap the leap's horizontal speed. The unbounded
                // distance scale reached ~765 px/s at the far side of the arena,
                // which risks tunneling through thin geometry at the 30 FPS cap.
                vx = ((player.x - x) * 0.85f).coerceIn(-420f, 420f)
                engine.audio.play("boss_roar")
            }
            TelegraphType.VOID_ERUPTION -> {
                vx = 0f
                engine.audio.play("explode")
                engine.camera.shake(12f)
                // Spawn ground eruption shockwaves
                engine.projectiles.spawn(x - 20f, y + h - 24f, -280f, 0f, ProjectileType.SHOCKWAVE, false, attack.baseDamage)
                engine.projectiles.spawn(x + w + 20f, y + h - 24f, 280f, 0f, ProjectileType.SHOCKWAVE, false, attack.baseDamage)
                engine.particles.spawn(x + w / 2f, y + h - 10f, currentPhase.accentColor, 35, 200f, 0.6f, 5.5f)
            }
        }
    }

    override fun onUpdateAttack(attack: BossAttackPattern, dt: Float, engine: GameEngine) {
        val player = engine.player

        when (attack.type) {
            TelegraphType.DASH_LUNGE -> {
                engine.particles.spawn(x + w / 2f, y + h - 12f, Color(0xFFFFA020), 4, 80f, 0.3f, 3.5f)
                if (aabbIntersects(x, y, w, h, player.x, player.y, player.w, player.h)) {
                    player.takeDamage(attack.baseDamage * currentPhase.speedMultiplier, x + w / 2f, engine)
                    player.statusController.applyEffect(com.example.game.status.CombatStatusType.CORROSION, duration = 5.5f, stacks = 1)
                }
            }
            TelegraphType.GROUND_SLAM -> {
                if (vy > 80f && !isSlamFalling) {
                    isSlamFalling = true
                    vy += 850f * dt
                }
                if (aabbIntersects(x, y, w, h, player.x, player.y, player.w, player.h)) {
                    player.takeDamage(attack.baseDamage, x + w / 2f, engine)
                    player.statusController.applyEffect(com.example.game.status.CombatStatusType.ALCHEMY_BURN, duration = 4.5f, stacks = 1)
                }
            }
            TelegraphType.MAGMA_MORTAR -> {
                vx = 0f
            }
            TelegraphType.VOID_ERUPTION -> {
                vx = 0f
            }
        }
    }

    override fun onLandedOnPlatform(platform: Platform, engine: GameEngine) {
        if (state == BossState.ATTACKING && currentAttack?.type == TelegraphType.GROUND_SLAM) {
            engine.audio.play("hit")
            engine.camera.shake(14f)
            engine.particles.spawn(x + w / 2f, y + h, Color(0xFFFF5010), 30, 200f, 0.5f, 5f)

            // Spawn shockwaves traveling in both directions
            engine.projectiles.spawn(x - 12f, y + h - 24f, -270f, 0f, ProjectileType.SHOCKWAVE, false, 25f)
            engine.projectiles.spawn(x + w + 12f, y + h - 24f, 270f, 0f, ProjectileType.SHOCKWAVE, false, 25f)
        }
    }

    override fun onPhaseTransition(fromPhase: BossPhase, toPhase: BossPhase, engine: GameEngine) {
        super.onPhaseTransition(fromPhase, toPhase, engine)
        // Additional phase transformation effects
        engine.haptics?.onBossHit()
    }

    override fun onStaggered(engine: GameEngine) {
        super.onStaggered(engine)
        engine.haptics?.onBossHit()
    }
}
