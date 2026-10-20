package com.example.game.boss

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.game.status.CombatStatusType
import kotlin.math.sin

/**
 * Gothic typeface using bundled Cinzel medieval font with serif fallback.
 */
val GothicFontFamily = FontFamily(
    Font(R.font.cinzel, FontWeight.Normal),
    Font(R.font.cinzel, FontWeight.Bold)
)

/**
 * Custom Gothic-themed Boss Health Bar component for boss encounters.
 * Features:
 * - Ornate gothic cathedral iron and brass framing with pointed filigree endcaps
 * - Antique gold gothic serif typography (Cinzel) with ominous drop shadows
 * - Soulsborne-style dual-layer ghost health bar (damage catch-up delay)
 * - Deep crimson alchemical blood vitae gradient with specular highlights
 * - Phase markers and dividing notches at 33% and 66% health thresholds
 * - Dynamic boss state banners (Staggered, Telegraphing, Phase Transition)
 * - Integrated gothic weak-point poise/stagger stability meter
 * - Gothic alchemical seals for active combat status effects (Corrosion, Alchemy-Burn)
 */
@Composable
fun GothicBossHealthBar(
    boss: BaseBoss,
    frameTick: Int,
    modifier: Modifier = Modifier
) {
    // Only render when the boss is active and alive
    AnimatedVisibility(
        visible = boss.active && !boss.dead,
        enter = fadeIn(tween(400)) + slideInVertically(
            initialOffsetY = { -it },
            animationSpec = tween(500, easing = FastOutSlowInEasing)
        ),
        exit = fadeOut(tween(300)) + slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = tween(400)
        ),
        modifier = modifier
    ) {
        val hpRatio = (boss.hp / boss.maxHp).coerceIn(0f, 1f)

        // Soulsborne Ghost damage bar: smoothly lags behind real HP after a delay
        val ghostHpRatio by animateFloatAsState(
            targetValue = hpRatio,
            animationSpec = tween(
                durationMillis = 500,
                delayMillis = 300,
                easing = FastOutSlowInEasing
            ),
            label = "ghost_health_anim"
        )

        // Subtle ambient pulse for enraged or critical boss health
        val infiniteTransition = rememberInfiniteTransition(label = "gothic_pulse")
        val pulseGlow by infiniteTransition.animateFloat(
            initialValue = 0.6f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse_glow"
        )

        Column(
            modifier = Modifier
                .widthIn(min = 340.dp, max = 460.dp)
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .testTag("gothic_boss_health_bar"),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- TOP TITLE ROW: Gothic filigree divider & Boss Title ---
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 2.dp)
            ) {
                Text(
                    text = "⚜",
                    color = Color(0xFFC49A45),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(end = 6.dp)
                )

                Text(
                    text = boss.bossName.uppercase(),
                    color = Color(0xFFF2E6C8),
                    fontFamily = GothicFontFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.2.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.shadow(elevation = 3.dp, ambientColor = Color.Black, spotColor = Color.Black)
                )

                Text(
                    text = "⚜",
                    color = Color(0xFFC49A45),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }

            // Subtitle: Phase & Epithet with gothic flourish
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(bottom = 3.dp)
            ) {
                Text(
                    text = "—",
                    color = Color(0x88D4AF37),
                    fontSize = 10.sp
                )
                Text(
                    text = boss.currentPhase.title,
                    color = boss.currentPhase.accentColor,
                    fontFamily = GothicFontFamily,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = "• ${boss.hp.toInt()} / ${boss.maxHp.toInt()} VITAE",
                    color = Color(0xFFAAA090),
                    fontFamily = GothicFontFamily,
                    fontSize = 9.sp,
                    letterSpacing = 0.8.sp
                )
                Text(
                    text = "—",
                    color = Color(0x88D4AF37),
                    fontSize = 10.sp
                )
            }

            // --- MAIN HEALTH BAR WITH ORNATE GOTHIC FRAME ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
            ) {
                // Background & Soulsborne Health Bars
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 3.dp)
                        .clip(CutCornerShape(topStart = 4.dp, bottomEnd = 4.dp, topEnd = 4.dp, bottomStart = 4.dp))
                        .background(Color(0xF00D0614))
                ) {
                    // Dark stone groove channel texture
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color(0xFF07030A),
                                        Color(0xFF160B24),
                                        Color(0xFF07030A)
                                    )
                                )
                            )
                    )

                    // 1. Ghost Catch-up Health Bar (Amber/Molten Orange)
                    if (ghostHpRatio > hpRatio) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction = ghostHpRatio)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            Color(0xFF9E2A2B),
                                            Color(0xFFE07A5F),
                                            Color(0xFFFFB703)
                                        )
                                    )
                                )
                        )
                    }

                    // 2. Active Health Bar (Crimson Blood & Phase Accent)
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction = hpRatio)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color(0xFF4A0008),
                                        Color(0xFF8B0000),
                                        Color(0xFFC70020),
                                        boss.currentPhase.accentColor
                                    )
                                )
                        )
                    )

                    // 3. Specular Top Sheen Highlight for glassy/alchemical depth
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = hpRatio)
                            .height(2.5.dp)
                            .align(Alignment.TopStart)
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color(0x88FFFFFF),
                                        Color(0x00FFFFFF)
                                    )
                                )
                            )
                    )

                    // 4. Phase Transition Dividers (at 33% and 66%)
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(0.33f)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(1.5.dp)
                                .align(Alignment.CenterEnd)
                                .background(Color(0xBBFFE57F))
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(0.66f)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(1.5.dp)
                                .align(Alignment.CenterEnd)
                                .background(Color(0xBBFFE57F))
                        )
                    }
                }

                // Custom Gothic Iron & Brass Filigree Framing Canvas overlay
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawGothicFrame(
                        phase = boss.currentPhase,
                        hpRatio = hpRatio,
                        isHurt = boss.hurtTimer > 0f
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // --- BOTTOM ROW: STAGGER / POISE SHIELD & COMBAT STATUSES ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: Gothic Poise / Stagger Meter
                val staggerRatio = (boss.weakPoint.staggerHealth / boss.weakPoint.maxStaggerHealth).coerceIn(0f, 1f)
                val isStaggered = boss.state == BossState.STAGGERED

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (isStaggered) "⚡ CORE BROKEN" else "POISE",
                        color = if (isStaggered) Color(0xFFFFD700) else Color(0xFFC0A4D4),
                        fontFamily = GothicFontFamily,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )

                    Box(
                        modifier = Modifier
                            .width(90.dp)
                            .height(5.dp)
                            .clip(CutCornerShape(1.5.dp))
                            .background(Color(0xDD120A1E))
                            .border(0.8.dp, Color(0xFF705888), CutCornerShape(1.5.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction = if (isStaggered) 1f else staggerRatio)
                                .background(
                                    if (isStaggered) {
                                        Brush.horizontalGradient(
                                            listOf(Color(0xFFFFB703), Color(0xFFFFE600), Color(0xFFFFFFFF))
                                        )
                                    } else {
                                        Brush.horizontalGradient(
                                            listOf(Color(0xFF5A3E75), Color(0xFFB584E6))
                                        )
                                    }
                                )
                        )
                    }
                }

                // Right: Active Boss Combat Status Badges (Corrosion, Alchemy-Burn)
                val bossDebuffs = remember(frameTick) {
                    boss.statusController.getActiveEffects().toList()
                }

                if (bossDebuffs.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.testTag("boss_debuffs_row")
                    ) {
                        for (bDebuff in bossDebuffs) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color(0xE0170A24))
                                    .border(0.8.dp, bDebuff.type.primaryColor, RoundedCornerShape(3.dp))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                    .testTag("boss_debuff_${bDebuff.type.id}")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(bDebuff.type.iconSymbol, fontSize = 8.sp)
                                    Text(
                                        text = "${bDebuff.type.displayName.uppercase()}" +
                                                (if (bDebuff.stacks > 1) " x${bDebuff.stacks}" else "") +
                                                " ${String.format("%.1f", bDebuff.remaining)}s",
                                        color = bDebuff.type.primaryColor,
                                        fontSize = 7.5.sp,
                                        fontFamily = GothicFontFamily,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (bDebuff.type == CombatStatusType.CORROSION) {
                                        Text(
                                            text = "(+${30 * bDebuff.stacks}%)",
                                            color = Color(0xFFFFD700),
                                            fontSize = 7.5.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- DYNAMIC STATE WARNING / BANNER (Staggered or Telegraphing) ---
            when (boss.state) {
                BossState.STAGGERED -> {
                    Box(
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xCC3D2400))
                            .border(1.dp, Color(0xFFFFD700).copy(alpha = pulseGlow), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "★ WEAK POINT EXPOSED • CRITICAL VULNERABILITY (x2.2 DMG) ★",
                            color = Color(0xFFFFD700),
                            fontFamily = GothicFontFamily,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp
                        )
                    }
                }
                BossState.TELEGRAPHING -> {
                    val atk = boss.currentAttack
                    Box(
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xCC35070A))
                            .border(1.dp, (atk?.warningColor ?: Color(0xFFFF3020)).copy(alpha = pulseGlow), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "⚠ WARNING: ${atk?.name?.uppercase() ?: "INCOMING ASSAULT"} ⚠",
                            color = atk?.warningColor ?: Color(0xFFFF5252),
                            fontFamily = GothicFontFamily,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp
                        )
                    }
                }
                BossState.PHASE_TRANSITION -> {
                    Box(
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xCC2A003E))
                            .border(1.dp, boss.currentPhase.accentColor.copy(alpha = pulseGlow), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "⚡ TRANSCENDENCE: ${boss.currentPhase.title} ⚡",
                            color = boss.currentPhase.accentColor,
                            fontFamily = GothicFontFamily,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                    }
                }
                else -> {
                    // Normal state: no extra banner
                }
            }
        }
    }
}

/**
 * Custom Canvas drawing routine for gothic iron filigree, brass bevels,
 * cathedral finial endcaps, and ornamental phase pips.
 */
private fun DrawScope.drawGothicFrame(
    phase: BossPhase,
    hpRatio: Float,
    isHurt: Boolean
) {
    val w = size.width
    val h = size.height

    val outerLeft = 14.dp.toPx()
    val outerRight = w - 14.dp.toPx()
    val barTop = 3.dp.toPx()
    val barBottom = h - 3.dp.toPx()

    // Antique Brass / Wrought Iron palette
    val darkIron = Color(0xFF281C33)
    val antiqueBrass = Color(0xFFC5A059)
    val brightGold = Color(0xFFFFE082)
    val shadowColor = Color(0x99000000)

    // 1. Top and Bottom Rail Metallic Trim
    val railBrush = Brush.horizontalGradient(
        listOf(
            darkIron,
            antiqueBrass,
            brightGold,
            phase.accentColor,
            brightGold,
            antiqueBrass,
            darkIron
        ),
        startX = outerLeft,
        endX = outerRight
    )

    // Top border rail
    drawLine(
        brush = railBrush,
        start = Offset(outerLeft - 2f, barTop),
        end = Offset(outerRight + 2f, barTop),
        strokeWidth = 1.8.dp.toPx()
    )

    // Bottom border rail
    drawLine(
        brush = railBrush,
        start = Offset(outerLeft - 2f, barBottom),
        end = Offset(outerRight + 2f, barBottom),
        strokeWidth = 1.8.dp.toPx()
    )

    // 2. Left Gothic Finial / Cathedral Pointed Bracket
    val leftFinial = Path().apply {
        moveTo(outerLeft, barTop - 2.dp.toPx())
        lineTo(4.dp.toPx(), h / 2f) // Pointed spearhead
        lineTo(outerLeft, barBottom + 2.dp.toPx())
        lineTo(outerLeft + 4.dp.toPx(), h / 2f)
        close()
    }
    drawPath(leftFinial, color = darkIron, style = Fill)
    drawPath(leftFinial, brush = Brush.linearGradient(listOf(antiqueBrass, brightGold, darkIron)), style = Stroke(width = 1.5.dp.toPx()))

    // Ruby jewel at the left finial center
    drawCircle(
        brush = Brush.radialGradient(listOf(Color(0xFFFF3344), Color(0xFF6B0000))),
        radius = 3.dp.toPx(),
        center = Offset(10.dp.toPx(), h / 2f)
    )
    drawCircle(color = brightGold, radius = 3.dp.toPx(), center = Offset(10.dp.toPx(), h / 2f), style = Stroke(1.dp.toPx()))

    // 3. Right Gothic Finial / Cathedral Pointed Bracket
    val rightFinial = Path().apply {
        moveTo(outerRight, barTop - 2.dp.toPx())
        lineTo(w - 4.dp.toPx(), h / 2f) // Pointed spearhead
        lineTo(outerRight, barBottom + 2.dp.toPx())
        lineTo(outerRight - 4.dp.toPx(), h / 2f)
        close()
    }
    drawPath(rightFinial, color = darkIron, style = Fill)
    drawPath(rightFinial, brush = Brush.linearGradient(listOf(darkIron, brightGold, antiqueBrass)), style = Stroke(width = 1.5.dp.toPx()))

    // Ruby jewel at the right finial center
    drawCircle(
        brush = Brush.radialGradient(listOf(Color(0xFFFF3344), Color(0xFF6B0000))),
        radius = 3.dp.toPx(),
        center = Offset(w - 10.dp.toPx(), h / 2f)
    )
    drawCircle(color = brightGold, radius = 3.dp.toPx(), center = Offset(w - 10.dp.toPx(), h / 2f), style = Stroke(1.dp.toPx()))

    // 4. Center Top Gothic Crest Arch & Finial Diamond
    val centerX = w / 2f
    val crestDiamond = Path().apply {
        moveTo(centerX, barTop - 4.dp.toPx())
        lineTo(centerX + 4.dp.toPx(), barTop)
        lineTo(centerX, barTop + 4.dp.toPx())
        lineTo(centerX - 4.dp.toPx(), barTop)
        close()
    }
    drawPath(crestDiamond, color = Color(0xFF1B0F2A), style = Fill)
    drawPath(crestDiamond, color = brightGold, style = Stroke(1.2.dp.toPx()))

    // 5. Phase Threshold Gothic Diamond Pips at 33% and 66%
    val innerWidth = outerRight - outerLeft
    val phasePipPositions = listOf(0.33f, 0.66f)

    for (pos in phasePipPositions) {
        val pipX = outerLeft + (innerWidth * pos)

        // Top diamond notch
        val topPip = Path().apply {
            moveTo(pipX, barTop - 2.5.dp.toPx())
            lineTo(pipX + 2.5.dp.toPx(), barTop)
            lineTo(pipX, barTop + 2.5.dp.toPx())
            lineTo(pipX - 2.5.dp.toPx(), barTop)
            close()
        }
        drawPath(topPip, color = brightGold, style = Fill)

        // Bottom diamond notch
        val botPip = Path().apply {
            moveTo(pipX, barBottom - 2.5.dp.toPx())
            lineTo(pipX + 2.5.dp.toPx(), barBottom)
            lineTo(pipX, barBottom + 2.5.dp.toPx())
            lineTo(pipX - 2.5.dp.toPx(), barBottom)
            close()
        }
        drawPath(botPip, color = brightGold, style = Fill)
    }

    // 6. Hurt impact flash
    if (isHurt) {
        drawRect(
            color = Color(0x66FFFFFF),
            topLeft = Offset(outerLeft, barTop),
            size = Size(innerWidth * hpRatio, barBottom - barTop)
        )
    }
}
