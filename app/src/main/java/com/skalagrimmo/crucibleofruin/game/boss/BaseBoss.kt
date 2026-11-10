package com.skalagrimmo.crucibleofruin.game.boss

import androidx.compose.ui.graphics.Color
import com.skalagrimmo.crucibleofruin.game.ActorPhysics
import com.skalagrimmo.crucibleofruin.game.ActorPhysicsConfig
import com.skalagrimmo.crucibleofruin.game.GameEngine
import com.skalagrimmo.crucibleofruin.game.PhysicsActor
import com.skalagrimmo.crucibleofruin.game.Platform
import com.skalagrimmo.crucibleofruin.game.PlatformFilter
import com.skalagrimmo.crucibleofruin.game.VerticalResolveMode
import com.skalagrimmo.crucibleofruin.game.status.CombatStatusType
import com.skalagrimmo.crucibleofruin.game.status.StatusEffectController

/**
 * Base Boss class providing foundational architecture for:
 * 1. Multi-phase state machine and smooth transitions
 * 2. Visual telegraphed attack pattern lifecycle
 * 3. Precision weak-point targeting, critical hits, and stagger breakdown
 */
abstract class BaseBoss(
    override var x: Float,
    override var y: Float,
    override val w: Float,
    override val h: Float,
    val maxHp: Float,
    val bossName: String,
    val weakPoint: WeakPoint
) : PhysicsActor {
    var hp: Float = maxHp
    override var facing: Int = -1 // 1 = right, -1 = left
    override var vx: Float = 0f
    override var vy: Float = 0f
    override var grounded: Boolean = false
    var dead: Boolean = false
    var active: Boolean = false
    var lastHitId: Long = 0L
    var hurtTimer: Float = 0f

    // Combat status effect controller (DoTs, debuffs, corrosion, burn)
    val statusController = StatusEffectController()

    /** Corrosion slow scales the boss's horizontal step inside the shared resolver (Stage 2, #34). */
    override fun horizontalSpeedMultiplier(): Float = statusController.getSpeedMultiplier()

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

    // --- Multi-Component System (Stage 9a, #62) ---
    /**
     * Destructible sub-parts of this boss. Empty by default, so component-less
     * bosses (e.g. CrucibleChimeraBoss) keep the exact single-entity behavior.
     */
    open val components: List<BossComponent> = emptyList()

    /** Parts still attached to the boss structure. */
    val aliveComponents: List<BossComponent>
        get() = components.filter { it.isAlive }

    fun componentById(id: String): BossComponent? = components.firstOrNull { it.id == id }

    /**
     * Stage 9a (#62): an attack is available when no destroyed component powers
     * it. Subclasses call this from [selectNextAttack] to strip disabled attacks.
     */
    fun isAttackAvailable(attackId: String): Boolean =
        components.none { it.destroyed && attackId in it.powersAttackIds }

    /**
     * Executes one game update tick for boss state, timers, physics, and attacks.
     */
    open fun update(dt: Float, engine: GameEngine) {
        if (!active || dead) return

        weakPoint.update(dt)
        // Stage 9a (#62): tick component hurt-flash timers.
        for (component in components) component.update(dt)

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
        // Stage 2 (#34): the shared ActorPhysics resolver replaces the private
        // axis-separated loops. Behavior is unchanged — gravity 950 with terminal
        // 750, resolve against every platform, corrosion-scaled horizontal step,
        // and the landing hook below still fires for subclasses.
        physicsEngine = engine
        // Stage 6b (#38): resolves against the tile grid instead of the list.
        ActorPhysics.moveAndResolve(this, engine.levelGrid, BOSS_PHYSICS_CONFIG, dt)
        physicsEngine = null
    }

    /** Transient engine reference so the shared resolver's landing hook can reach it. */
    private var physicsEngine: GameEngine? = null

    /** Routes the shared resolver's landing event to the engine-aware boss hook. */
    override fun onLanded(platform: Platform) {
        val engine = physicsEngine ?: return
        onLandedOnPlatform(platform, engine)
    }

    companion object {
        /** Boss resolver config (Stage 2, #34): overlaps every platform on both axes. */
        private val BOSS_PHYSICS_CONFIG = ActorPhysicsConfig(
            gravity = 950f,
            terminalVelocity = 750f,
            horizontalFilter = PlatformFilter.ALL,
            verticalMode = VerticalResolveMode.OVERLAP_LAND_OR_BLOCK,
            landingFilter = PlatformFilter.ALL
        )
    }

    protected open fun onLandedOnPlatform(platform: com.skalagrimmo.crucibleofruin.game.Platform, engine: GameEngine) {}

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

        // Stage 9a (#62): route the strike to a destructible component first,
        // then fall back to the weak point / plain body hit.
        val hitComponent = components.firstOrNull {
            it.intersects(slashX, slashY, slashW, slashH, x, y, w, facing)
        }

        // Check Weak Point collision
        val isWeakPointHit = hitComponent == null && weakPoint.intersects(
            slashX, slashY, slashW, slashH,
            x, y, w, facing
        )

        // Amplify damage if boss is afflicted with Corrosion
        val modifiedAmount = amount * statusController.getIncomingDamageMultiplier()

        val finalDamage = when {
            isWeakPointHit -> {
                val critDamage = modifiedAmount * weakPoint.damageMultiplier
                onWeakPointHit(critDamage, engine)
                critDamage
            }
            hitComponent != null -> modifiedAmount * hitComponent.damageMultiplier
            else -> modifiedAmount
        }

        hp -= finalDamage
        hurtTimer = 0.18f
        engine.audio.play("hit")
        engine.camera.shake(if (isWeakPointHit) 8f else 5f)

        if (hitComponent != null) {
            // Stage 9a (#62): component hits chip the part; destroying it fires
            // the hook (explosion feedback + subclass behavior like attack stripping).
            val cBounds = hitComponent.getWorldBounds(x, y, w, facing)
            engine.particles.spawn(
                cBounds[0] + cBounds[2] / 2f,
                cBounds[1] + cBounds[3] / 2f,
                Color(0xFFFF8020),
                count = 10,
                speed = 140f,
                life = 0.4f,
                size = 4f
            )
            if (hitComponent.applyDamage(finalDamage)) {
                onComponentDestroyed(hitComponent, engine)
            }
        } else if (isWeakPointHit) {
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

    /**
     * Stage 9a (#62): a destructible component just broke off the boss.
     * Default feedback: explosion, shake, haptics. Subclasses extend this to
     * strip the attacks the part powered or force a phase transition.
     */
    open fun onComponentDestroyed(component: BossComponent, engine: GameEngine) {
        engine.audio.play("explode")
        engine.camera.shake(12f)
        engine.haptics?.onBossHit()
        val cBounds = component.getWorldBounds(x, y, w, facing)
        engine.particles.spawnEnemyDeath(
            cBounds[0] + cBounds[2] / 2f,
            cBounds[1] + cBounds[3] / 2f,
            Color(0xFFFF7010)
        )
    }
}
