package com.example.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import android.graphics.Paint
import android.graphics.Typeface
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

    private val COLOR_CITY_FACADE = Color(0xB51E1628)
    private val COLOR_CITY_FACADE_EDGE = Color(0xFF45334F)
    private val COLOR_CITY_FACADE_SEAM = Color(0xFF2A2034)
    private val COLOR_CITY_WINDOW_FRAME = Color(0xFF5C4666)
    private val COLOR_CITY_WINDOW_GLASS = Color(0xFF242D43)
    private val COLOR_CITY_WINDOW_GLOW = Color(0x6659BFA6)

    private data class CityFacade(
        val x: Float,
        val y: Float,
        val w: Float,
        val h: Float,
        val windowColumns: Int
    )

    private val CITY_FACADES = listOf(
        CityFacade(RuinedBastionDistrict.worldX(284f), RuinedBastionDistrict.worldY(242f),
            RuinedBastionDistrict.worldWidth(306f), RuinedBastionDistrict.worldHeight(168f), 4),
        CityFacade(RuinedBastionDistrict.worldX(714f), RuinedBastionDistrict.worldY(172f),
            RuinedBastionDistrict.worldWidth(256f), RuinedBastionDistrict.worldHeight(238f), 3),
        CityFacade(RuinedBastionDistrict.worldX(1224f), RuinedBastionDistrict.worldY(242f),
            RuinedBastionDistrict.worldWidth(376f), RuinedBastionDistrict.worldHeight(168f), 5)
    )

    private val COLOR_ACID_BASE = Color(0xFF0F4422)
    private val COLOR_ACID_SURFACE = Color(0xFF2FF078)
    private val COLOR_ACID_BUBBLE = Color(0xFF80FFA8)
    private val COLOR_SPIKE = Color(0xFF6B5E78)
    private val COLOR_SLASH_ARC = Color(0xFF40F0FF)

    private fun DrawScope.drawCityArchitecture(camX: Float, camY: Float) {
        for (facade in CITY_FACADES) {
            if (facade.x + facade.w < camX || facade.x > camX + 960f) continue

            val x = facade.x - camX
            val y = facade.y - camY

            drawRect(COLOR_CITY_FACADE, topLeft = Offset(x, y), size = Size(facade.w, facade.h))
            drawRect(COLOR_CITY_FACADE_EDGE, topLeft = Offset(x, y), size = Size(facade.w, 4f))
            drawRect(COLOR_CITY_FACADE_EDGE, topLeft = Offset(x, y + facade.h - 4f), size = Size(facade.w, 4f))

            // Window frames are purely decorative. Their low-contrast glow makes
            // each building read as a distinct interior instead of loose ledges.
            val columnStep = facade.w / (facade.windowColumns + 1)
            for (column in 1..facade.windowColumns) {
                val windowX = x + columnStep * column - 10f
                val windowY = y + 24f
                drawRect(
                    COLOR_CITY_WINDOW_FRAME,
                    topLeft = Offset(windowX, windowY),
                    size = Size(20f, 32f)
                )
                drawRect(
                    COLOR_CITY_WINDOW_GLASS,
                    topLeft = Offset(windowX + 3f, windowY + 3f),
                    size = Size(14f, 26f)
                )
                drawRect(
                    COLOR_CITY_WINDOW_GLOW,
                    topLeft = Offset(windowX + 5f, windowY + 5f),
                    size = Size(4f, 8f)
                )
                drawRect(
                    COLOR_CITY_FACADE_EDGE,
                    topLeft = Offset(windowX + 9f, windowY + 3f),
                    size = Size(2f, 26f)
                )
            }

            // Thin masonry courses make the surfaces read as walls without
            // obscuring the silhouettes or turning decoration into collision.
            var courseY = y + 68f
            while (courseY < y + facade.h - 8f) {
                drawRect(
                    COLOR_CITY_FACADE_SEAM,
                    topLeft = Offset(x + 4f, courseY),
                    size = Size(facade.w - 8f, 2f)
                )
                courseY += 34f
            }
        }
    }

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

            drawCircle(COLOR_MOON, radius = 65f, center = Offset(680f - camX * 0.05f, 120f))

            // Background Ruin Spires (Speed: 0.15)
            var rx = -100f
            while (rx < 7000f) {
                val sx = rx - camX * 0.15f
                if (sx in -120f..1060f) {
                    drawRect(COLOR_PARALLAX_RUIN, topLeft = Offset(sx, 160f), size = Size(90f, 380f))
                    drawCircle(COLOR_PARALLAX_RUIN, radius = 45f, center = Offset(sx + 45f, 160f))
                }
                rx += 280f
            }

            // Midground Pre-loaded Textures: Stained Glass & Pillars (Speed: 0.35)
            var px = 100f
            while (px < 6800f) {
                val dx = px - camX * 0.35f
                if (dx in -80f..1040f) {
                    if ((px.toInt() % 640) == 0) {
                        drawImage(AssetCache.texStainedGlass, topLeft = Offset(dx, 140f))
                    } else {
                        drawImage(AssetCache.texPillar, topLeft = Offset(dx, 80f))
                    }
                }
                px += 320f
            }

            // 2. City architecture: visual-only facade layer, drawn behind
            // platforms and actors so it adds depth without changing collision.
            drawCityArchitecture(camX, camY)

            // 3. Platforms (Spatial Culling)
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
                    PlatformStyle.CITY_LEDGE -> {
                        drawRect(Color(0xFF30253A), topLeft = Offset(dx, dy), size = Size(p.w, p.h))
                        drawRect(Color(0xFF695174), topLeft = Offset(dx, dy), size = Size(p.w, 3f))
                        if (p.h > 7f) {
                            drawRect(Color(0xFF211927), topLeft = Offset(dx, dy + p.h - 3f), size = Size(p.w, 3f))
                        }
                        var seamX = dx + 22f
                        while (seamX < dx + p.w - 4f) {
                            drawRect(COLOR_CITY_FACADE_SEAM, topLeft = Offset(seamX, dy + 4f), size = Size(2f, (p.h - 7f).coerceAtLeast(1f)))
                            seamX += 28f
                        }
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

            // 6. Pickups (Dense object pool + Pre-loaded textures & alchemical orbs)
            for (i in 0 until engine.pickups.activeCount) {
                val pk = engine.pickups.get(i)
                when (pk.type) {
                    "health" -> drawImage(AssetCache.texPickupHealth, topLeft = Offset(pk.x - camX, pk.y - camY))
                    "flask" -> drawImage(AssetCache.texPickupFlask, topLeft = Offset(pk.x - camX, pk.y - camY))
                    "sulfur" -> {
                        // Glowing Sulfur Crystal
                        val cx = pk.x - camX + 8f
                        val cy = pk.y - camY + 8f
                        drawCircle(Color(0xFFFFB039), radius = 7f, center = Offset(cx, cy))
                        drawCircle(Color(0xFFFFE082), radius = 3.5f, center = Offset(cx, cy))
                    }
                    "mercury" -> {
                        // Liquid Quicksilver Sphere
                        val cx = pk.x - camX + 8f
                        val cy = pk.y - camY + 8f
                        drawCircle(Color(0xFF64D8CB), radius = 7f, center = Offset(cx, cy))
                        drawCircle(Color(0xFFE0F7FA), radius = 3.5f, center = Offset(cx, cy))
                    }
                    "vitriol" -> {
                        // Resonant Vitriol Jewel
                        val cx = pk.x - camX + 8f
                        val cy = pk.y - camY + 8f
                        drawCircle(Color(0xFFB388FF), radius = 7.5f, center = Offset(cx, cy))
                        drawCircle(Color(0xFFEDE7F6), radius = 4f, center = Offset(cx, cy))
                    }
                    else -> drawImage(AssetCache.texPickupFlask, topLeft = Offset(pk.x - camX, pk.y - camY))
                }
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

                // Enemy Status Effect Indicators (Floating aura/dots above enemy)
                val enemyEffects = e.statusController.getActiveEffects()
                if (enemyEffects.isNotEmpty()) {
                    var offX = -((enemyEffects.size - 1) * 6f)
                    for (eff in enemyEffects) {
                        val dotX = ex + e.w / 2f + offX
                        val dotY = ey - 8f
                        drawCircle(Color(0xCC100A1C), radius = 5f, center = Offset(dotX, dotY))
                        drawCircle(eff.type.primaryColor, radius = 3.5f, center = Offset(dotX, dotY))
                        drawCircle(Color.White, radius = 1.2f, center = Offset(dotX, dotY))
                        offX += 12f
                    }
                }

                // Enemy Stagger Daze Indicator
                if (e.isStaggered) {
                    val dazePulse = (sin(System.currentTimeMillis() / 70.0) * 0.25f + 0.75f).toFloat()
                    val headX = ex + e.w / 2f
                    val headY = ey - 14f
                    drawCircle(Color(0xFFFFD700), radius = 4f * dazePulse, center = Offset(headX - 6f, headY))
                    drawCircle(Color(0xFFFFF275), radius = 3f * dazePulse, center = Offset(headX, headY - 4f))
                    drawCircle(Color(0xFFFFD700), radius = 4f * dazePulse, center = Offset(headX + 6f, headY))
                }
            }

            // 8. Boss (Pre-loaded textures, telegraphed attack indicators, and weak-point reticle)
            val boss = engine.boss
            if (boss.active && !boss.dead) {
                val bx = boss.x - camX
                val by = boss.y - camY

                // Telegraph Warning Indicator
                if (boss.state == com.example.game.boss.BossState.TELEGRAPHING) {
                    val atk = boss.currentAttack
                    if (atk != null) {
                        val warnX = if (boss.facing > 0) bx + boss.w else bx - atk.warningWidth
                        val warnY = by + boss.h - atk.warningHeight
                        val prog = boss.telegraphProgress
                        val pulse = (sin(prog * 16f) * 0.2f + 0.4f).coerceIn(0.15f, 0.85f)

                        // Telegraph Hazard Area
                        drawRect(
                            color = atk.warningColor.copy(alpha = pulse * 0.45f),
                            topLeft = Offset(warnX, warnY),
                            size = Size(atk.warningWidth, atk.warningHeight)
                        )
                        drawRect(
                            color = atk.warningColor.copy(alpha = 0.9f),
                            topLeft = Offset(warnX, warnY),
                            size = Size(atk.warningWidth, atk.warningHeight),
                            style = Stroke(width = 2f)
                        )
                        // Telegraph Charge Progress Bar
                        val fillW = atk.warningWidth * prog
                        val fillStartX = if (boss.facing > 0) warnX else (warnX + atk.warningWidth - fillW)
                        drawRect(
                            color = Color(0xFFFFFFFF).copy(alpha = 0.85f),
                            topLeft = Offset(fillStartX, warnY + atk.warningHeight - 4f),
                            size = Size(fillW, 4f)
                        )
                    }
                }

                // Phase 3 Crucible Overload Aura
                if (boss.currentPhase == com.example.game.boss.BossPhase.PHASE_3) {
                    val auraPulse = (sin(System.currentTimeMillis() / 150.0) * 0.25f + 0.4f).toFloat()
                    drawCircle(
                        color = Color(0xFFC020FF).copy(alpha = auraPulse * 0.35f),
                        radius = boss.w * 0.65f,
                        center = Offset(bx + boss.w / 2f, by + boss.h / 2f)
                    )
                }

                // Boss Body
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

                // Weak Point Reticle & Core Indicator
                if (boss.weakPoint.isExposed && boss.state != com.example.game.boss.BossState.DEAD) {
                    val wpBounds = boss.weakPoint.getWorldBounds(boss.x, boss.y, boss.w, boss.facing)
                    val wpx = wpBounds[0] - camX
                    val wpy = wpBounds[1] - camY
                    val pulse = (sin(boss.weakPoint.glowPulseTimer) * 0.3f + 0.7f).coerceIn(0.4f, 1.0f)
                    val coreCenterX = wpx + wpBounds[2] / 2f
                    val coreCenterY = wpy + wpBounds[3] / 2f

                    // Outer Target Diamond Reticle
                    drawCircle(
                        color = Color(0xFFFFD700).copy(alpha = pulse * 0.85f),
                        radius = 12f,
                        center = Offset(coreCenterX, coreCenterY),
                        style = Stroke(width = 2f)
                    )
                    // Inner Critical Core
                    drawCircle(
                        color = if (boss.state == com.example.game.boss.BossState.STAGGERED) Color(0xFFFFD700) else Color(0xFFFF2040),
                        radius = 5.5f,
                        center = Offset(coreCenterX, coreCenterY)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 2f,
                        center = Offset(coreCenterX, coreCenterY)
                    )
                }

                // Stagger Stun Indicator (Stars above head)
                if (boss.state == com.example.game.boss.BossState.STAGGERED) {
                    val headX = bx + boss.w / 2f
                    val headY = by - 12f
                    val time = System.currentTimeMillis() / 200.0
                    for (k in 0 until 3) {
                        val angle = time + k * (Math.PI * 2.0 / 3.0)
                        val starX = (headX + kotlin.math.cos(angle) * 18f).toFloat()
                        val starY = (headY + kotlin.math.sin(angle) * 6f).toFloat()
                        drawCircle(Color(0xFFFFD700), radius = 3f, center = Offset(starX, starY))
                    }
                }

                // Boss Status Debuff Badges in world space (above head)
                val bossEffects = boss.statusController.getActiveEffects()
                if (bossEffects.isNotEmpty()) {
                    val badgeHeadX = bx + boss.w / 2f
                    val badgeHeadY = by - (if (boss.state == com.example.game.boss.BossState.STAGGERED) 26f else 14f)
                    var badgeOffX = -((bossEffects.size - 1) * 16f)
                    for (eff in bossEffects) {
                        val cx = badgeHeadX + badgeOffX
                        val cy = badgeHeadY
                        drawCircle(Color(0xEE12081C), radius = 8f, center = Offset(cx, cy))
                        drawCircle(eff.type.primaryColor, radius = 8f, center = Offset(cx, cy), style = Stroke(width = 1.5f))
                        drawCircle(
                            color = eff.type.secondaryColor,
                            radius = 6f,
                            center = Offset(cx, cy),
                            style = Stroke(width = 2.5f)
                        )
                        drawCircle(Color.White, radius = 2f, center = Offset(cx, cy))
                        badgeOffX += 32f
                    }
                }
            }

            // 9. Player (Pre-loaded textures, shadow dash afterimages, and riposte visual fx)
            val p = engine.player

            // Render shadow dash afterimages
            for (img in p.afterimages) {
                val ix = img.x - camX
                val iy = img.y - camY
                val tex = if (img.facing > 0) AssetCache.texPlayerIdleRight else AssetCache.texPlayerIdleLeft
                drawImage(
                    image = tex,
                    topLeft = Offset(ix - 3f, iy - 2f),
                    alpha = img.alpha * 0.65f
                )
                drawCircle(
                    color = img.color.copy(alpha = img.alpha * 0.35f),
                    radius = p.w * 0.75f,
                    center = Offset(ix + p.w / 2f, iy + p.h / 2f),
                    style = Stroke(width = 1.5f)
                )
            }

            if (!p.dead && (p.invulnTimer <= 0f || (System.currentTimeMillis() / 60) % 2 == 0L)) {
                val px = p.x - camX
                val py = p.y - camY
                val bob = if (p.grounded && abs(p.vx) > 20f) sin(p.walkAnimTimer) * 2f else 0f

                // Perfect Riposte Shockwave Ring
                if (p.perfectRiposteTimer > 0f) {
                    val progress = 1f - (p.perfectRiposteTimer / 1.3f).coerceIn(0f, 1f)
                    val shockRadius = p.w * (1.2f + progress * 2.8f)
                    val shockAlpha = (1f - progress).coerceIn(0f, 1f)
                    drawCircle(
                        color = Color(0xFFFFD700).copy(alpha = shockAlpha * 0.80f),
                        radius = shockRadius,
                        center = Offset(px + p.w / 2f, py + p.h / 2f),
                        style = Stroke(width = 3.5f)
                    )
                    drawCircle(
                        color = Color(0xFF00E5FF).copy(alpha = shockAlpha * 0.60f),
                        radius = shockRadius * 0.75f,
                        center = Offset(px + p.w / 2f, py + p.h / 2f),
                        style = Stroke(width = 2f)
                    )
                }

                // Shadow Dash In-Flight Glow
                if (p.isDashing) {
                    drawCircle(
                        color = Color(0x6600E5FF),
                        radius = p.w * 1.1f,
                        center = Offset(px + p.w / 2f, py + p.h / 2f)
                    )
                }

                // Player Status Effect Aura
                if (p.statusController.hasEffect(com.example.game.status.CombatStatusType.ALCHEMY_BURN)) {
                    val pulse = (sin(System.currentTimeMillis() / 120.0) * 0.2f + 0.5f).toFloat()
                    drawCircle(
                        color = com.example.game.status.CombatStatusType.ALCHEMY_BURN.primaryColor.copy(alpha = pulse * 0.35f),
                        radius = p.w * 0.9f,
                        center = Offset(px + p.w / 2f, py + p.h / 2f)
                    )
                }
                if (p.statusController.hasEffect(com.example.game.status.CombatStatusType.CORROSION)) {
                    val pulse = (sin(System.currentTimeMillis() / 150.0) * 0.2f + 0.45f).toFloat()
                    drawCircle(
                        color = com.example.game.status.CombatStatusType.CORROSION.primaryColor.copy(alpha = pulse * 0.30f),
                        radius = p.w * 0.95f,
                        center = Offset(px + p.w / 2f, py + p.h / 2f)
                    )
                }

                if (p.attacking) {
                    val tex = if (p.facing > 0) AssetCache.texPlayerAttackRight else AssetCache.texPlayerAttackLeft
                    drawImage(tex, topLeft = Offset(px - 10f, py - 4f + bob))
                    // Slash Arc based on combo step
                    val arcColor = when (p.lastComboHit) {
                        3 -> Color(0xFFFF2040)
                        2 -> Color(0xFFFFB300)
                        else -> COLOR_SLASH_ARC
                    }
                    val arcSize = if (p.lastComboHit == 3) 60f else 48f
                    val arcStroke = if (p.lastComboHit == 3) 4.5f else 3f
                    val arcOffX = if (p.facing > 0) {
                        px - (if (p.lastComboHit == 3) 6f else 10f)
                    } else {
                        px - (if (p.lastComboHit == 3) 24f else 10f)
                    }
                    drawArc(
                        color = arcColor,
                        startAngle = if (p.facing > 0) -80f else 100f,
                        sweepAngle = if (p.lastComboHit == 3) 160f else 140f,
                        useCenter = false,
                        topLeft = Offset(arcOffX, py - (if (p.lastComboHit == 3) 8f else 4f)),
                        size = Size(arcSize, arcSize),
                        style = Stroke(width = arcStroke)
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

            // 12. Floating Combat Text (FCT) Damage Numbers & Critical Banners
            val nativeCanvas = drawContext.canvas.nativeCanvas
            val strokePaint = Paint().apply {
                isAntiAlias = true
                typeface = Typeface.DEFAULT_BOLD
                style = Paint.Style.STROKE
            }
            val fillPaint = Paint().apply {
                isAntiAlias = true
                typeface = Typeface.DEFAULT_BOLD
                style = Paint.Style.FILL
            }

            for (i in 0 until engine.damageNumbers.activeCount) {
                val dn = engine.damageNumbers.get(i)
                val alpha = (dn.life / dn.maxLife).coerceIn(0f, 1f)
                val dnx = dn.x - camX
                val dny = dn.y - camY
                val textSizePx = if (dn.isCrit) 20f * dn.scale else 14f * dn.scale

                strokePaint.textSize = textSizePx
                fillPaint.textSize = textSizePx

                // Dark outline for legibility
                strokePaint.color = android.graphics.Color.argb((alpha * 230).toInt(), 12, 6, 20)
                strokePaint.strokeWidth = if (dn.isCrit) 3.5f else 2.5f
                nativeCanvas.drawText(dn.text, dnx, dny, strokePaint)

                // Fill color
                val r = (dn.color.red * 255).toInt()
                val g = (dn.color.green * 255).toInt()
                val b = (dn.color.blue * 255).toInt()
                fillPaint.color = android.graphics.Color.argb((alpha * 255).toInt(), r, g, b)
                nativeCanvas.drawText(dn.text, dnx, dny, fillPaint)
            }
        }
    }
}
