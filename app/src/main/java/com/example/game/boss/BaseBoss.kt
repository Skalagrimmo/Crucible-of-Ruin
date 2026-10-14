package com.example.game.boss

import androidx.compose.ui.graphics.Color
import com.example.game.GameEngine
import com.example.game.aabbIntersects
import com.example.game.status.CombatStatusType
import com.example.game.status.StatusEffectController

/**
 * Base Boss class providing foundational architecture for:
 * 1. Multi-phase state machine and smooth transitions
 * 2. Visual telegraphed attack pattern lifecycle
 * 3. Precision weak-point targeting, critical hits, and stagger breakdown
 */
abstract class BaseBoss(
    var x: Float,
    var y: Float,
    val w: Float,
    val h: Float,
    val maxHp: Float,
    val bossName: String,
    val weakPoint: WeakPoint
) {
    var hp: Float = maxHp
    var facing: Int = -1 // 1 = right, -1 = left
    var vx: Float = 0f
    var vy: Float = 0f
    var grounded: Boolean = false
    var dead: Boolean = false
    var active: Boolean = false
    var lastHitId: Long = 0L
    var hurtTimer: Float = 0f

    // Combat status effect controller (DoTs, debuffs, corrosion, burn)
    val statusController = StatusEffectController()

    // --- State Machine ---
    var state: BossState = BossState.IDLE
    var stateTimer: Float = 1.0f

    // --- Phase Management ---
    var currentPhase: BossPhase = BossPhase.PHASE_1
    val isPhase2: Boolean
        get() = currentPhase != BossPhase.PHASE_1
    val phase2: Boolean // Backwards compatibility for existing renderers
        get() = isPhase2

    var phaseTransitionTimer: Float = 0f
    val phaseTransitionDuration: Float = 1.2f

    // --- Telegraph & Attack Management ---
    var currentAttack: BossAttackPattern? = null
    var telegraphTimer: Float = 0f
    var attackTimer: Float = 0f
    var recoveryTimer: Float = 0f
    var attackCycle: Int = 0

    val telegraphProgress: Float
        get() {
            val atk = currentAttack ?: return 0f
            if (state != BossState.TELEGRAPHING || atk.telegraphDuration <= 0f) return 0f
            return (1f - (telegraphTimer / atk.telegraphDuration)).coerceIn(0f, 1f)
        }

    // --- Stagger Mechanic ---
    var staggerTimer: Float = 0f
    val staggerDuration: Float = 2.4f

    /**
     * Executes one game update tick for boss state, timers, physics, and attacks.
     */
    open fun update(dt: Float, engine: GameEngine) {
        if (!active || dead) return

        weakPoint.update(dt)

        if (hurtTimer > 0f) {
            hurtTimer = (hurtTimer - dt).coerceAtLeast(0f)
        }

        // Process active combat status debuffs (Alchemy-Burn, Corrosion)
        statusController.update(dt) { type, damage, stacks ->
            hp -= damage
            hurtTimer = 0.08f
            engine.particles.spawn(
                x + (Math.random().toFloat() * w),
                y + (Math.random().toFloat() * h),
                type.primaryColor,
                count = 3,
                speed = 65f,
                life = 0.3f,
                size = 3.5f
            )
            checkPhaseProgression(engine)
            if (hp <= 0f) {
                onDefeated(engine)
            }
        }

        // Ambient status particle trails on boss body
        if (statusController.hasEffect(CombatStatusType.ALCHEMY_BURN)) {
            engine.particles.spawn(
                x + (Math.random().toFloat() * w),
                y + h - 8f,
                CombatStatusType.ALCHEMY_BURN.primaryColor,
                count = 1,
                speed = 45f,
                life = 0.35f,
                size = 3.2f
            )
        }
        if (statusController.hasEffect(CombatStatusType.CORROSION)) {
            engine.particles.spawn(
                x + (Math.random().toFloat() * w),
                y + (Math.random().toFloat() * h),
                CombatStatusType.CORROSION.primaryColor,
                count = 1,
                speed = 30f,
                life = 0.3f,
                size = 2.8f
            )
        }

        when (state) {
            BossState.IDLE -> updateIdle(dt, engine)
            BossState.TELEGRAPHING -> updateTelegraphing(dt, engine)
            BossState.ATTACKING -> updateAttacking(dt, engine)
            BossState.RECOVERING -> updateRecovering(dt, engine)
            BossState.PHASE_TRANSITION -> updatePhaseTransition(dt, engine)
            BossState.STAGGERED -> updateStaggered(dt, engine)
            BossState.DEAD -> {}
        }

        // Apply general physics and platform resolution
        updatePhysics(dt, engine)
    }

    private fun updateIdle(dt: Float, engine: GameEngine) {
        vx = 0f
        // Orient towards player
        val distToPlayerX = (engine.player.x + engine.player.w / 2f) - (x + w / 2f)
        facing = if (distToPlayerX >= 0) 1 else -1

        stateTimer -= dt
        if (stateTimer <= 0f) {
            beginNextTelegraph(engine)
        }
    }

    private fun beginNextTelegraph(engine: GameEngine) {
        val nextAtk = selectNextAttack()
        currentAttack = nextAtk
        state = BossState.TELEGRAPHING
        telegraphTimer = nextAtk.telegraphDuration * currentPhase.cooldownMultiplier
        vx = 0f

        weakPoint.isExposed = nextAtk.exposesWeakPoint
        engine.haptics?.onMeleeSwing()
    }

    private fun updateTelegraphing(dt: Float, engine: GameEngine) {
        vx = 0f
        telegraphTimer -= dt

        val atk = currentAttack
        if (atk != null) {
            // Emit warning particles along telegraph path
            val warnX = if (facing > 0) x + w else x - atk.warningWidth
            engine.particles.spawn(
                warnX + atk.warningWidth / 2f,
                y + h - 14f,
                atk.warningColor,
                count = 2,
                speed = 70f,
                life = 0.25f,
                size = 3f
            )
        }

        if (telegraphTimer <= 0f && atk != null) {
            // Launch attack
            state = BossState.ATTACKING
            attackTimer = atk.activeDuration
            onStartAttack(atk, engine)
        }
    }

    private fun updateAttacking(dt: Float, engine: GameEngine) {
        val atk = currentAttack ?: run {
            state = BossState.IDLE
            stateTimer = 0.8f
            return
        }

        attackTimer -= dt
        onUpdateAttack(atk, dt, engine)

        if (attackTimer <= 0f) {
            state = BossState.RECOVERING
            recoveryTimer = atk.recoveryDuration * currentPhase.cooldownMultiplier
            vx = 0f
        }
    }

    private fun updateRecovering(dt: Float, engine: GameEngine) {
        vx = 0f
        recoveryTimer -= dt
        if (recoveryTimer <= 0f) {
            state = BossState.IDLE
            stateTimer = (0.7f * currentPhase.cooldownMultiplier).coerceAtLeast(0.3f)
            currentAttack = null
            weakPoint.isExposed = true
        }
    }

    private fun updatePhaseTransition(dt: Float, engine: GameEngine) {
        vx = 0f
        vy = 0f
        phaseTransitionTimer -= dt

        // Pulsing eruption particles during phase transformation
        engine.particles.spawn(
            x + w / 2f,
            y + h / 2f,
            currentPhase.accentColor,
            count = 4,
            speed = 130f,
            life = 0.35f,
            size = 4.5f
        )

        if (phaseTransitionTimer <= 0f) {
            state = BossState.IDLE
            stateTimer = 0.5f
            weakPoint.resetStagger()
        }
    }

    private fun updateStaggered(dt: Float, engine: GameEngine) {
        vx = 0f
        staggerTimer -= dt

        // Emitting vulnerable electric / spark particles
        engine.particles.spawn(
            x + w / 2f,
            y + 16f,
            Color(0xFFFFD700),
            count = 3,
            speed = 60f,
            life = 0.3f,
            size = 3.5f
        )

        if (staggerTimer <= 0f) {
            onRecoverFromStagger(engine)
            state = BossState.IDLE
            stateTimer = 0.7f
            weakPoint.resetStagger()
        }
    }

    protected open fun updatePhysics(dt: Float, engine: GameEngine) {
        vy += 950f * dt
        if (vy > 750f) vy = 750f

        // Horizontal movement with corrosion slow penalty & collision
        val effectiveVx = vx * statusController.getSpeedMultiplier()
        x += effectiveVx * dt
        for (p in engine.platforms) {
            if (aabbIntersects(x, y, w, h, p.x, p.y, p.w, p.h)) {
                if (effectiveVx > 0f) {
                    x = p.x - w
                    vx = 0f
                } else if (effectiveVx < 0f) {
                    x = p.x + p.w
                    vx = 0f
                }
            }
        }

        // Vertical movement & collision
        y += vy * dt
        grounded = false
        for (p in engine.platforms) {
            if (aabbIntersects(x, y, w, h, p.x, p.y, p.w, p.h)) {
                if (vy > 0f) {
                    y = p.y - h
                    vy = 0f
                    grounded = true
                    onLandedOnPlatform(p, engine)
                } else if (vy < 0f) {
                    y = p.y + p.h
                    vy = 0f
                }
            }
        }
    }

    protected open fun onLandedOnPlatform(platform: com.example.game.Platform, engine: GameEngine) {}

    /**
     * Processes damage applied to the boss entity, checking weak-point collision first.
     */
    open fun takeDamage(amount: Float, attackId: Long, fromX: Float, engine: GameEngine) {
        if (lastHitId == attackId || dead || !active || state == BossState.PHASE_TRANSITION) return
        lastHitId = attackId

        val player = engine.player
        val slashX = if (player.facing > 0) player.x + player.w - 4f else player.x - 34f
        val slashY = player.y + 4f
        val slashW = 36f
        val slashH = player.h - 8f

        // Check Weak Point collision
        val isWeakPointHit = weakPoint.intersects(
            slashX, slashY, slashW, slashH,
            x, y, w, facing
        )

        // Amplify damage if boss is afflicted with Corrosion
        val modifiedAmount = amount * statusController.getIncomingDamageMultiplier()

        val finalDamage = if (isWeakPointHit) {
            val critDamage = modifiedAmount * weakPoint.damageMultiplier
            onWeakPointHit(critDamage, engine)
            critDamage
        } else {
            modifiedAmount
        }

        hp -= finalDamage
        hurtTimer = 0.18f
        engine.audio.play("hit")
        engine.camera.shake(if (isWeakPointHit) 8f else 5f)

        if (isWeakPointHit) {
            val wpBounds = weakPoint.getWorldBounds(x, y, w, facing)
            engine.particles.spawn(
                wpBounds[0] + wpBounds[2] / 2f,
                wpBounds[1] + wpBounds[3] / 2f,
                Color(0xFFFFD700),
                count = 18,
                speed = 180f,
                life = 0.45f,
                size = 4.5f
            )
            // Weak point breach inflicts severe Corrosion & Alchemy-Burn overload
            statusController.applyEffect(CombatStatusType.CORROSION, duration = 6.0f, stacks = 2)
            statusController.applyEffect(CombatStatusType.ALCHEMY_BURN, duration = 5.0f, stacks = 1)

            val staggered = weakPoint.applyWeakPointDamage(finalDamage)
            if (staggered && state != BossState.STAGGERED) {
                triggerStagger(engine)
            }
        } else {
            engine.particles.spawnEnemyHurt(x + w / 2f, y + h / 2f, Color(0xFFFF3060))
        }

        checkPhaseProgression(engine)

        if (hp <= 0f) {
            onDefeated(engine)
        }
    }

    protected open fun onWeakPointHit(damage: Float, engine: GameEngine) {
        engine.haptics?.onBossHit()
    }

    private fun triggerStagger(engine: GameEngine) {
        state = BossState.STAGGERED
        staggerTimer = staggerDuration
        vx = 0f
        engine.audio.play("boss_roar")
        engine.camera.shake(14f)
        engine.particles.spawnEnemyDeath(x + w / 2f, y + h / 2f, Color(0xFFFFD700))
        onStaggered(engine)
    }

    protected open fun checkPhaseProgression(engine: GameEngine) {
        val hpRatio = (hp / maxHp).coerceIn(0f, 1f)

        if (hpRatio <= 0.33f && currentPhase != BossPhase.PHASE_3) {
            transitionToPhase(BossPhase.PHASE_3, engine)
        } else if (hpRatio <= 0.66f && currentPhase == BossPhase.PHASE_1) {
            transitionToPhase(BossPhase.PHASE_2, engine)
        }
    }

    protected fun transitionToPhase(newPhase: BossPhase, engine: GameEngine) {
        val oldPhase = currentPhase
        currentPhase = newPhase
        state = BossState.PHASE_TRANSITION
        phaseTransitionTimer = phaseTransitionDuration
        vx = 0f

        engine.audio.play("boss_roar")
        engine.camera.shake(16f)
        engine.particles.spawnEnemyDeath(x + w / 2f, y + h / 2f, newPhase.accentColor)

        onPhaseTransition(oldPhase, newPhase, engine)
    }

    protected open fun onDefeated(engine: GameEngine) {
        hp = 0f
        dead = true
        state = BossState.DEAD
        engine.audio.play("victory")
        engine.camera.shake(20f)
        engine.particles.spawnEnemyDeath(x + w / 2f, y + h / 2f, Color(0xFFFFD700))
        engine.state = "victory"
    }

    // --- Subclass Hooks ---
    abstract fun selectNextAttack(): BossAttackPattern
    abstract fun onStartAttack(attack: BossAttackPattern, engine: GameEngine)
    abstract fun onUpdateAttack(attack: BossAttackPattern, dt: Float, engine: GameEngine)
    open fun onPhaseTransition(fromPhase: BossPhase, toPhase: BossPhase, engine: GameEngine) {}
    open fun onStaggered(engine: GameEngine) {}
    open fun onRecoverFromStagger(engine: GameEngine) {}
}
