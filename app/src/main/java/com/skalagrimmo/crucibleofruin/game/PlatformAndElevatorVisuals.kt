package com.skalagrimmo.crucibleofruin.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.abs

class PlatformAndElevatorVisuals {

    private val stoneFill = Color(0xFF23272F)
    private val stoneBorder = Color(0xFF14171D)
    private val stoneHighlight = Color(0xFF3E4654)
    private val girderColor = Color(0xFF2C323B)
    private val rivetColor = Color(0xFF8A93A2)
    private val elevatorPlatformColor = Color(0xFF343B46)
    private val elevatorChassisColor = Color(0xFF22262E)
    private val cableColor = Color(0xFF525A66)
    private val hazardYellow = Color(0xFFD48806)

    private val gateSolidColor = Color(0xFF3D332A)
    private val gateOpenColor = Color(0xFF3D332A).copy(alpha = 0.35f)
    private val bossInnerPurple = Color(0xFF5A2A78)
    private val bossBorderGlow = Color(0xFF9B4FD8)
    private val portcullisBarsColor = Color(0xFF6B5848)

    fun renderPlatforms(scope: DrawScope, platforms: List<Platform>, cameraX: Float, cameraY: Float) {
        with(scope) {
            for (platform in platforms) {
                if (platform.x + platform.w < cameraX - 100f || platform.x > cameraX + 1060f ||
                    platform.y + platform.h < cameraY - 100f || platform.y > cameraY + 640f) {
                    continue
                }

                val screenX = platform.x - cameraX
                val screenY = platform.y - cameraY

                when (platform.style) {
                    PlatformStyle.STONE -> renderStonePlatform(screenX, screenY, platform.w, platform.h)
                    PlatformStyle.STONE_LEDGE -> renderStoneLedge(screenX, screenY, platform.w, platform.h)
                    PlatformStyle.IRON_GIRDER -> {
                        if (platform.motion != null && abs(platform.x - 610f) < 40f) {
                            renderIndustrialElevator(platform, screenX, screenY, cameraX, cameraY)
                        } else {
                            renderIronGirder(screenX, screenY, platform.w, platform.h)
                        }
                    }
                    PlatformStyle.BOSS_GATE -> renderBossGate(platform, screenX, screenY, platform.w, platform.h)
                    PlatformStyle.IRON_GATE -> renderIronGate(platform, screenX, screenY, platform.w, platform.h)
                }
            }
        }
    }

    private fun DrawScope.renderStonePlatform(x: Float, y: Float, w: Float, h: Float) {
        val visualRect = Rect(x, y - 4f, x + w, y + h + 20f)
        drawRect(stoneFill, visualRect.topLeft, visualRect.size)
        drawRect(stoneBorder, visualRect.topLeft, visualRect.size, style = Stroke(width = 2f))

        drawLine(
            color = stoneHighlight,
            start = Offset(x, y),
            end = Offset(x + w, y),
            strokeWidth = 3f
        )

        var cx = x + 48f
        while (cx < x + w) {
            drawLine(stoneBorder, Offset(cx, y), Offset(cx, y + h), strokeWidth = 1.5f)
            cx += 48f
        }
    }

    private fun DrawScope.renderStoneLedge(x: Float, y: Float, w: Float, h: Float) {
        val ledgeRect = Rect(x - 6f, y, x + w + 6f, y + h + 10f)
        drawRect(stoneFill, ledgeRect.topLeft, ledgeRect.size)
        drawRect(stoneBorder, ledgeRect.topLeft, ledgeRect.size, style = Stroke(width = 2f))

        val bracketPath = Path().apply {
            moveTo(x + 8f, y + h + 10f)
            lineTo(x + 28f, y + h + 10f)
            lineTo(x + 8f, y + h + 22f)
            close()
        }
        drawPath(bracketPath, stoneBorder)
    }

    private fun DrawScope.renderIronGirder(x: Float, y: Float, w: Float, h: Float) {
        drawRect(girderColor, Offset(x, y), Size(w, h))
        drawRect(stoneBorder, Offset(x, y), Size(w, h), style = Stroke(width = 2f))

        var rx = x + 12f
        while (rx < x + w - 8f) {
            drawCircle(rivetColor, radius = 2.5f, center = Offset(rx, y + h / 2f))
            rx += 24f
        }
    }

    private fun DrawScope.renderIndustrialElevator(
        platform: Platform,
        screenX: Float,
        screenY: Float,
        cameraX: Float,
        cameraY: Float
    ) {
        val motion = platform.motion
        if (motion != null) {
            val baseScreenX = motion.baseX - cameraX
            val baseScreenY = motion.baseY - cameraY
            val railCenterX = baseScreenX + platform.w / 2f
            val shaftBottomY = baseScreenY + motion.dy + platform.h + 40f

            drawLine(
                color = girderColor,
                start = Offset(railCenterX - 18f, baseScreenY - 160f),
                end = Offset(railCenterX - 18f, shaftBottomY),
                strokeWidth = 5f
            )
            drawLine(
                color = girderColor,
                start = Offset(railCenterX + 18f, baseScreenY - 160f),
                end = Offset(railCenterX + 18f, shaftBottomY),
                strokeWidth = 5f
            )

            drawRect(
                color = stoneBorder,
                topLeft = Offset(railCenterX - 28f, baseScreenY - 180f),
                size = Size(56f, 24f)
            )
            drawCircle(
                color = rivetColor,
                radius = 8f,
                center = Offset(railCenterX, baseScreenY - 168f)
            )

            drawLine(
                cableColor,
                Offset(screenX + 10f, baseScreenY - 168f),
                Offset(screenX + 10f, screenY),
                strokeWidth = 3f
            )
            drawLine(
                cableColor,
                Offset(screenX + platform.w - 10f, baseScreenY - 168f),
                Offset(screenX + platform.w - 10f, screenY),
                strokeWidth = 3f
            )
        }

        drawRect(
            color = elevatorChassisColor,
            topLeft = Offset(screenX, screenY + platform.h),
            size = Size(platform.w, 8f)
        )

        drawRect(elevatorPlatformColor, Offset(screenX, screenY), Size(platform.w, platform.h))
        drawRect(stoneBorder, Offset(screenX, screenY), Size(platform.w, platform.h), style = Stroke(width = 2f))

        drawRect(
            color = elevatorChassisColor,
            topLeft = Offset(screenX - 4f, screenY - 6f),
            size = Size(8f, platform.h + 12f)
        )
        drawRect(
            color = elevatorChassisColor,
            topLeft = Offset(screenX + platform.w - 4f, screenY - 6f),
            size = Size(8f, platform.h + 12f)
        )

        drawRect(
            color = rivetColor,
            topLeft = Offset(screenX - 6f, screenY + 4f),
            size = Size(3f, 12f)
        )
        drawRect(
            color = rivetColor,
            topLeft = Offset(screenX + platform.w + 3f, screenY + 4f),
            size = Size(3f, 12f)
        )

        drawLine(
            color = hazardYellow,
            start = Offset(screenX + 4f, screenY + platform.h - 4f),
            end = Offset(screenX + platform.w - 4f, screenY + platform.h - 4f),
            strokeWidth = 4f
        )
    }

    private fun DrawScope.renderBossGate(platform: Platform, x: Float, y: Float, w: Float, h: Float) {
        val isOpen = platform.collision == PlatformCollision.OPEN

        if (isOpen) {
            drawRect(gateOpenColor, Offset(x, y), Size(w, h))
            drawRect(stoneBorder, Offset(x, y), Size(w, h), style = Stroke(width = 1.5f))
        } else {
            drawRect(gateSolidColor, Offset(x, y), Size(w, h))
            drawRect(bossInnerPurple, Offset(x + 4f, y + 4f), Size(w - 8f, h - 8f))
            drawRect(bossBorderGlow, Offset(x, y), Size(w, h), style = Stroke(width = 2.5f))
            drawCircle(
                color = bossBorderGlow,
                radius = 6f,
                center = Offset(x + w / 2f, y + h / 2f)
            )
        }
    }

    private fun DrawScope.renderIronGate(platform: Platform, x: Float, y: Float, w: Float, h: Float) {
        val isOpen = platform.collision == PlatformCollision.OPEN

        if (isOpen) {
            drawRect(gateOpenColor, Offset(x, y), Size(w, h))
            drawRect(stoneBorder, Offset(x, y), Size(w, h), style = Stroke(width = 1.5f))
        } else {
            drawRect(gateSolidColor, Offset(x, y), Size(w, h))
            drawRect(stoneBorder, Offset(x, y), Size(w, h), style = Stroke(width = 2f))

            var bx = x + 12f
            while (bx < x + w - 8f) {
                drawLine(portcullisBarsColor, Offset(bx, y), Offset(bx, y + h), strokeWidth = 3.5f)
                bx += 16f
            }
            drawLine(
                portcullisBarsColor,
                Offset(x, y + h / 2f),
                Offset(x + w, y + h / 2f),
                strokeWidth = 3.5f
            )
        }
    }
}
