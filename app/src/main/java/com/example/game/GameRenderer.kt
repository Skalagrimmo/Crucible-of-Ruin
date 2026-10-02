package com.example.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.abs
import kotlin.math.sin

object GameRenderer {
    private val COLOR_SKY_TOP = Color(0xFF0C0714)
    private val COLOR_SKY_BOT = Color(0xFF271738)
    private val COLOR_MOON = Color(0x1F78FFB4)
    private val COLOR_PARALLAX_RUIN = Color(0xFF140C1E)

    private val COLOR_STONE_BASE = Color(0xFF2B2336)
    private val COLOR_STONE_TOP = Color(0xFF453854)
    private val COLOR_STONE_LINE = Color(0xFF1E1826)

    private val COLOR_GIRDER_BASE = Color(0xFF3A3144)
    private val COLOR_GIRDER_TOP = Color(0xFF544761)
    private val COLOR_RIVET = Color(0xFFC7B06B)
    private val COLOR_BOSS_GATE = Color(0xFF241A2E)
    private val COLOR_BOSS_GATE_INNER = Color(0xFF7A3090)

    private val COLOR_ACID_BASE = Color(0xFF0F4422)
    private val COLOR_ACID_SURFACE = Color(0xFF2FF078)
    private val COLOR_ACID_BUBBLE = Color(0xFF80FFA8)
    private val COLOR_SPIKE = Color(0xFF6B5E78)
    private val COLOR_SLASH_ARC = Color(0xFF40F0FF)

    fun render(drawScope: DrawScope, engine: GameEngine) {
        AssetCache.init()
        val canvasW = drawScope.size.width
        val canvasH = drawScope.size.height

        val scaleX = canvasW / 960f
        val scaleY = canvasH / 540f
        val scale = minOf(scaleX, scaleY)

        val offsetX = (canvasW - 960f * scale) / 2f
        val offsetY = (canvasH - 540f * scale) / 2f

        val camX = engine.camera.getOffsetX()
        val camY = engine.camera.getOffsetY()

        drawScope.withTransform({
            translate(offsetX, offsetY)
            scale(scale, scale, Offset.Zero)
            clipRect(0f, 0f, 960f, 540f)
        }) {
            // 1. Sky & Parallax
            drawRect(COLOR_SKY_BOT, size = Size(960f, 540f))
            drawRect(COLOR_SKY_TOP, size = Size(960f, 270f))

            drawCircle(
                COLOR_MOON,
                radius = 65f,
                center = Offset(680f - camX * 0.05f, 120f - camY * 0.03f)
            )

            // Background Ruin Spires (Speed: 0.15)
            var rx = -100f
            while (rx < 6400f) {
                val sx = rx - camX * 0.15f
                val sy = 160f - camY * 0.08f
                if (sx in -120f..1060f) {
                    drawRect(COLOR_PARALLAX_RUIN, topLeft = Offset(sx, sy), size = Size(90f, 380f))
                    drawCircle(COLOR_PARALLAX_RUIN, radius = 45f, center = Offset(sx + 45f, sy))
                }
                rx += 280f
            }

            // Midground Pre-loaded Textures: Stained Glass & Pillars (Speed: 0.35)
            var px = 100f
            while (px < 6100f) {
                val dx = px - camX * 0.35f
                val parallaxY = camY * 0.18f
                if (dx in -80f..1040f) {
                    if ((px.toInt() % 640) == 0) {
                        drawImage(AssetCache.texStainedGlass, topLeft = Offset(dx, 140f - parallaxY))
                    } else {
                        drawImage(AssetCache.texPillar, topLeft = Offset(dx, 80f - parallaxY))
                    }
                }
                px += 320f
            }

            // 2. Platforms (Spatial Culling)
            for (p in engine.platforms) {
                if (p.x + p.w < camX || p.x > camX + 960f) continue
                val dx = p.x - camX
                val dy = p.y - camY
                when (p.style) {
                    PlatformStyle.IRON_GIRDER -> {
                        drawRect(COLOR_GIRDER_BASE, topLeft = Offset(dx, dy), size = Size(p.w, p.h))
                        drawRect(COLOR_GIRDER_TOP, topLeft = Offset(dx, dy), size = Size(p.w, 4f))
                        var rv = dx + 8f
                        while (rv < dx + p.w - 4f) {
                            drawRect(COLOR_RIVET, topLeft = Offset(rv, dy + 6f), size = Size(4f, 4f))
                            rv += 24f
                        }
                    }
                    PlatformStyle.BOSS_GATE -> {
                        drawRect(COLOR_BOSS_GATE, topLeft = Offset(dx, dy), size = Size(p.w, p.h))
                        drawRect(COLOR_BOSS_GATE_INNER, topLeft = Offset(dx + 6f, dy), size = Size(p.w - 12f, p.h))
                    }
                    else -> {
                        drawRect(COLOR_STONE_BASE, topLeft = Offset(dx, dy), size = Size(p.w, p.h))
                        drawRect(COLOR_STONE_TOP, topLeft = Offset(dx, dy), size = Size(p.w, 5f))
                        if (p.h > 24f) {
                            drawRect(COLOR_STONE_LINE, topLeft = Offset(dx, dy + 20f), size = Size(p.w, 2f))
                        }
                    }
                }
            }

            // 3. Hazards (Acid / Spikes)
            for (h in engine.hazards) {
                if (h.x + h.w < camX || h.x > camX + 960f) continue
                val hx = h.x - camX
                val hy = h.y - camY
                if (h.type == HazardType.ACID) {
                    drawRect(COLOR_ACID_BASE, topLeft = Offset(hx, hy), size = Size(h.w, h.h))
                    drawImage(AssetCache.texAcidSurface, topLeft = Offset(hx, hy))
                    val bOffset = (System.currentTimeMillis() % 1000L) / 1000f
                    drawCircle(COLOR_ACID_BUBBLE, radius = 3f, center = Offset(hx + h.w * 0.3f, hy + 2f - bOffset * 5f))
                    drawCircle(COLOR_ACID_BUBBLE, radius = 4f, center = Offset(hx + h.w * 0.7f, hy + 2f - ((bOffset + 0.5f) % 1f) * 5f))
                } else {
                    var sx = hx
                    while (sx < hx + h.w - 6f) {
                        drawRect(COLOR_SPIKE, topLeft = Offset(sx + 3f, hy), size = Size(6f, h.h))
                        sx += 14f
                    }
                }
            }

            // 4. Checkpoints (Pre-loaded textures)
            for (cp in engine.checkpoints) {
                if (cp.x + 30f < camX || cp.x - 30f > camX + 960f) continue
                val cx = cp.x - camX
                val cy = cp.y - camY
                val tex = if (cp.activated) AssetCache.texCheckpointActive else AssetCache.texCheckpointInactive
                drawImage(tex, topLeft = Offset(cx - 12f, cy - 38f))
            }

            // 5. Breakables (Pre-loaded texture)
            for (b in engine.breakables) {
                if (!b.dead && b.x + b.w >= camX && b.x <= camX + 960f) {
                    drawImage(AssetCache.texUrn, topLeft = Offset(b.x - camX, b.y - camY))
                }
            }

            // 6. Pickups (Dense object pool + Pre-loaded textures)
            for (i in 0 until engine.pickups.activeCount) {
                val pk = engine.pickups.get(i)
                val tex = if (pk.type == "health") AssetCache.texPickupHealth else AssetCache.texPickupFlask
                drawImage(tex, topLeft = Offset(pk.x - camX, pk.y - camY))
            }

            // 7. Enemies (Pre-loaded texture blits)
            for (e in engine.enemies) {
                if (e.dead || e.x + e.w < camX - 40f || e.x > camX + 1000f) continue
                val ex = e.x - camX
                val ey = e.y - camY

                if (e.hurtTimer > 0f) {
                    drawRect(Color.White, topLeft = Offset(ex, ey), size = Size(e.w, e.h))
                    continue
                }

                when (e.type) {
                    EnemyArchetype.LURKER -> {
                        val tex = if (e.facing > 0) AssetCache.texLurkerRight else AssetCache.texLurkerLeft
                        drawImage(tex, topLeft = Offset(ex, ey))
                    }
                    EnemyArchetype.CHERUB -> {
                        val wingY = sin(e.flyPhase * 3f) * 4f
                        drawImage(AssetCache.texCherub, topLeft = Offset(ex, ey + wingY))
                    }
                    EnemyArchetype.CULTIST -> {
                        val tex = if (e.facing > 0) AssetCache.texCultistRight else AssetCache.texCultistLeft
                        drawImage(tex, topLeft = Offset(ex - 2f, ey - 2f))
                    }
                    EnemyArchetype.GOLEM -> {
                        val tex = if (e.facing > 0) AssetCache.texGolemRight else AssetCache.texGolemLeft
                        drawImage(tex, topLeft = Offset(ex - 6f, ey - 2f))
                    }
                }
            }

            // 8. Boss (Pre-loaded textures)
            val boss = engine.boss
            if (boss.active && !boss.dead) {
                val bx = boss.x - camX
                val by = boss.y - camY

                if (boss.hurtTimer > 0f) {
                    drawRect(Color.White, topLeft = Offset(bx, by), size = Size(boss.w, boss.h))
                } else {
                    val tex = if (boss.phase2) {
                        if (boss.facing > 0) AssetCache.texBossEnragedRight else AssetCache.texBossEnragedLeft
                    } else {
                        if (boss.facing > 0) AssetCache.texBossNormalRight else AssetCache.texBossNormalLeft
                    }
                    drawImage(tex, topLeft = Offset(bx - 3f, by - 2f))
                }
            }

            // 9. Player (Pre-loaded textures)
            val p = engine.player
            if (!p.dead && (p.invulnTimer <= 0f || (System.currentTimeMillis() / 60) % 2 == 0L)) {
                val px = p.x - camX
                val py = p.y - camY
                val bob = if (p.grounded && abs(p.vx) > 20f) sin(p.walkAnimTimer) * 2f else 0f

                if (p.attacking) {
                    val tex = if (p.facing > 0) AssetCache.texPlayerAttackRight else AssetCache.texPlayerAttackLeft
                    drawImage(tex, topLeft = Offset(px - 10f, py - 4f + bob))
                    // Slash Arc
                    drawArc(
                        color = COLOR_SLASH_ARC,
                        startAngle = if (p.facing > 0) -80f else 100f,
                        sweepAngle = 140f,
                        useCenter = false,
                        topLeft = Offset(px - 10f, py - 4f),
                        size = Size(48f, 48f),
                        style = Stroke(width = 3f)
                    )
                } else {
                    val tex = if (p.facing > 0) AssetCache.texPlayerIdleRight else AssetCache.texPlayerIdleLeft
                    drawImage(tex, topLeft = Offset(px - 3f, py - 2f + bob))
                }
            }

            // 10. Projectiles (Dense object pool)
            for (i in 0 until engine.projectiles.activeCount) {
                val proj = engine.projectiles.get(i)
                val prx = proj.x - camX
                val pry = proj.y - camY
                when (proj.type) {
                    ProjectileType.FLASK -> {
                        drawRect(Color(0xFFB040F0), topLeft = Offset(prx, pry), size = Size(12f, 12f))
                        drawRect(Color(0xFF40FFD0), topLeft = Offset(prx + 2f, pry + 4f), size = Size(8f, 6f))
                    }
                    ProjectileType.CULTIST_ORB -> {
                        drawCircle(Color(0xFF20A050), radius = 8f, center = Offset(prx + 8f, pry + 8f))
                        drawCircle(Color(0xFF70FFB0), radius = 4f, center = Offset(prx + 8f, pry + 8f))
                    }
                    ProjectileType.BOSS_MAGMA -> {
                        drawCircle(Color(0xFFE03010), radius = 9f, center = Offset(prx + 8f, pry + 8f))
                        drawCircle(Color(0xFFFFBB20), radius = 5f, center = Offset(prx + 8f, pry + 8f))
                    }
                    ProjectileType.SHOCKWAVE -> {
                        drawRect(Color(0xFFF07020), topLeft = Offset(prx, pry), size = Size(proj.w, proj.h))
                        drawRect(Color(0xFFFFE060), topLeft = Offset(prx + 4f, pry + 2f), size = Size(proj.w - 8f, proj.h - 4f))
                    }
                }
            }

            // 11. Particles (Dense zero-allocation pool with in-place alpha)
            for (i in 0 until engine.particles.activeCount) {
                val pt = engine.particles.get(i)
                val pAlpha = (pt.life / pt.maxLife).coerceIn(0f, 1f)
                drawRect(
                    color = pt.color,
                    topLeft = Offset(pt.x - camX, pt.y - camY),
                    size = Size(pt.size * pAlpha, pt.size * pAlpha),
                    alpha = pAlpha
                )
            }
        }
    }
}
