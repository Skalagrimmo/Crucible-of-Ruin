package com.skalagrimmo.crucibleofruin.game.boss

import androidx.compose.ui.graphics.Color
import com.skalagrimmo.crucibleofruin.game.GameEngine
import com.skalagrimmo.crucibleofruin.game.Platform
import com.skalagrimmo.crucibleofruin.game.ProjectileType
import com.skalagrimmo.crucibleofruin.game.aabbIntersects
import com.skalagrimmo.crucibleofruin.game.status.CombatStatusType

/**
 * Stage 9c (#62): the first multi-component boss — Vogelgrim, Siege Golem of
 * the Foundry (Contra Hard Corps / Gunstar Heroes pattern).
 *
 * The golem is a STRUCTURE, not one entity: two destructible arm cannons
 * ([cannonNear] / [cannonFar], each powering its own ranged attack) mounted
 * around the Crucible Core weak point in the chest. Destroying a cannon
 * strips its attack from the rotation; destroying both overloads the foundry
 * core into a berserk slam mode (PHASE_3 regardless of body HP). Component
 * hits still chip the body (Stage 9a routing), so overall HP governs death.
 *
 * Rendering: GameRenderer draws the golem's body procedurally (a foundry
 * walker with a glowing furnace chest around the core socket) until a
 * dedicated sprite lands; the arm cannons ride the Stage 9b part frames.
 *
 * A mid-boss: [onDefeated] does NOT end the run — GameEngine reopens the
 * arena and arms the next encounter in the chain.
 */
class SiegeGolemBoss(
    spawnX: Float,
    spawnY: Float
) : BaseBoss(
    x = spawnX,
    y = spawnY,
    w = 130f,
    h = 170f,
    maxHp = 420f,
    bossName = "Vogelgrim, Siege Golem of the Foundry",
    weakPoint = WeakPoint(
        id = "crucible_core",
        name = "Foundry Crucible Core",
        offsetX = 50f,
        offsetY = 70f,
        width = 30f,
        height = 30f,
        damageMultiplier = 2.0f,
        maxStaggerHealth = 80f
    )
) {
    val cannonNear = BossComponent(
        id = "cannon_near",
        name = "Near Cannon",
        offsetX = 0f,
        offsetY = 55f,
        width = 30f,
        height = 55f,
        maxHp = 55f,
        damageMultiplier = 1.2f,
        powersAttackIds = setOf("sweep_volley"),
        accentColor = Color(0xFF4A7A9F)
    )

    val cannonFar = BossComponent(
        id = "cannon_far",
        name = "Far Cannon",
        offsetX = 100f,
        offsetY = 55f,
        width = 30f,
        height = 55f,
        maxHp = 55f,
        damageMultiplier = 1.2f,
        powersAttackIds = setOf("magma_mortar"),
        accentColor = Color(0xFF9F4A4A)
    )

    override val components: List<BossComponent> = listOf(cannonNear, cannonFar)

    private val attackVolley = BossAttackPattern(
        id = "sweep_volley",
        name = "Foundry Sweep Volley",
        type = TelegraphType.MAGMA_MORTAR,
        telegraphDuration = 0.8f,
        activeDuration = 0.5f,
        recoveryDuration = 0.7f,
        baseDamage = 18f,
        exposesWeakPoint = true,
        warningWidth = 340f,
        warningHeight = 40f,
        warningColor = Color(0xFF40B0FF)
    )

    private val attackMortar = BossAttackPattern(
        id = "magma_mortar",
        name = "Siege Mortar",
        type = TelegraphType.MAGMA_MORTAR,
        telegraphDuration = 0.9f,
        activeDuration = 0.45f,
        recoveryDuration = 0.8f,
        baseDamage = 22f,
        exposesWeakPoint = true,
        warningWidth = 150f,
        warningHeight = 120f,
        warningColor = Color(0xFFFFA500)
    )

    private val attackSlam = BossAttackPattern(
        id = "ground_slam",
        name = "Siege Leap Slam",
        type = TelegraphType.GROUND_SLAM,
        telegraphDuration = 0.75f,
        activeDuration = 1.1f,
        recoveryDuration = 0.9f,
        baseDamage = 30f,
        exposesWeakPoint = false,
        warningWidth = 240f,
        warningHeight = 40f,
        warningColor = Color(0xFFFF2040)
    )

    private var isSlamFalling: Boolean = false

    override fun selectNextAttack(): BossAttackPattern {
        attackCycle++
        // Stage 9a (#62): destroyed cannons strip the attacks they powered;
        // the body slam is always available.
        val pool = buildList {
            if (isAttackAvailable("sweep_volley")) add(attackVolley)
            if (isAttackAvailable("magma_mortar")) add(attackMortar)
            add(attackSlam)
        }
        return pool[attackCycle % pool.size]
    }

    override fun onStartAttack(attack: BossAttackPattern, engine: GameEngine) {
        val player = engine.player
        val distToPlayerX = (player.x + player.w / 2f) - (x + w / 2f)
        facing = if (distToPlayerX >= 0) 1 else -1

        when (attack.id) {
            "sweep_volley" -> {
                vx = 0f
                engine.audio.play("explode")
                engine.camera.shake(8f)
                // Horizontal cannon volley from the front muzzle; the spread
                // rolls use the engine's seeded RNG (Stage 6a, #38) so replays
                // reproduce the barrage exactly.
                val muzzleX = if (facing > 0) x + w else x - 14f
                val shots = if (currentPhase == BossPhase.PHASE_1) 3 else 5
                for (i in 0 until shots) {
                    val bVx = facing * (300f + engine.rng.nextFloat() * 70f)
                    val bVy = (i - (shots - 1) / 2f) * 26f
                    engine.projectiles.spawn(
                        muzzleX,
                        y + 82f,
                        bVx,
                        bVy,
                        ProjectileType.BOSS_MAGMA,
                        senderIsPlayer = false,
                        damage = attack.baseDamage
                    )
                }
            }
            "magma_mortar" -> {
                vx = 0f
                engine.audio.play("explode")
                engine.camera.shake(8f)
                val count = if (currentPhase == BossPhase.PHASE_1) 3 else 5
                for (i in 0 until count) {
                    val spread = (i - (count - 1) / 2f) * 85f
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
            "ground_slam" -> {
                isSlamFalling = false
                vy = -560f
                vx = ((player.x - x) * 0.85f).coerceIn(-420f, 420f)
                engine.audio.play("boss_roar")
            }
        }
    }

    override fun onUpdateAttack(attack: BossAttackPattern, dt: Float, engine: GameEngine) {
        val player = engine.player
        when (attack.id) {
            "sweep_volley", "magma_mortar" -> vx = 0f
            "ground_slam" -> {
                if (vy > 80f && !isSlamFalling) {
                    isSlamFalling = true
                    vy += 850f * dt
                }
                if (aabbIntersects(x, y, w, h, player.x, player.y, player.w, player.h)) {
                    player.takeDamage(attack.baseDamage, x + w / 2f, engine)
                    player.statusController.applyEffect(
                        CombatStatusType.ALCHEMY_BURN,
                        duration = 4.5f,
                        stacks = 1
                    )
                }
            }
        }
    }

    override fun onLandedOnPlatform(platform: Platform, engine: GameEngine) {
        if (state == BossState.ATTACKING && currentAttack?.id == "ground_slam") {
            engine.audio.play("hit")
            engine.camera.shake(14f)
            engine.particles.spawn(x + w / 2f, y + h, Color(0xFFFF5010), 30, 200f, 0.5f, 5f)

            // Spawn shockwaves traveling in both directions
            engine.projectiles.spawn(x - 12f, y + h - 24f, -270f, 0f, ProjectileType.SHOCKWAVE, false, 25f)
            engine.projectiles.spawn(x + w + 12f, y + h - 24f, 270f, 0f, ProjectileType.SHOCKWAVE, false, 25f)
        }
    }

    override fun onComponentDestroyed(component: BossComponent, engine: GameEngine) {
        super.onComponentDestroyed(component, engine)
        // Stage 9c (#62): both cannons gone — the foundry core overloads into
        // a berserk slam mode regardless of body HP (parts-driven phase).
        if (aliveComponents.isEmpty() && currentPhase != BossPhase.PHASE_3) {
            transitionToPhase(BossPhase.PHASE_3, engine)
        }
    }

    override fun onDefeated(engine: GameEngine) {
        hp = 0f
        dead = true
        state = BossState.DEAD
        engine.audio.play("explode")
        engine.camera.shake(20f)
        engine.particles.spawnEnemyDeath(x + w / 2f, y + h / 2f, Color(0xFFFF7010))
        // Stage 9c (#62): a mid-boss defeat must NOT end the run — GameEngine
        // reopens the arena and arms the next encounter in the chain.
    }
}
