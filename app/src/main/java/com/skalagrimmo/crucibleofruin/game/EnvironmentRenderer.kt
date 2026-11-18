package com.skalagrimmo.crucibleofruin.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope

class EnvironmentRenderer {

    private val darkSlate = Color(0xFF1A1D24)
    private val gothicArchShadow = Color(0xFF111318)
    private val structuralSteel = Color(0xFF2C323B)
    private val copperConduit = Color(0xFF8A5531)
    private val amberLight = Color(0xFFFFB340)
    private val coolMoonlight = Color(0xFF3E5A6D)

    fun render(scope: DrawScope, cameraX: Float, cameraY: Float, worldWidth: Float) {
        with(scope) {
            val virtualWidth = 960f
            val virtualHeight = 540f

            // Layer 1: distant Gothic silhouettes with restrained vertical parallax.
            val bgCamX = cameraX * 0.3f
            val bgCamY = cameraY * 0.1f
            drawDistantBastionSilhouettes(bgCamX, bgCamY, virtualWidth, virtualHeight, worldWidth)

            // Layer 2: industrial infrastructure integrated into the midground.
            val midCamX = cameraX * 0.6f
            val midCamY = cameraY * 0.3f
            drawMidgroundInfrastructure(midCamX, midCamY, virtualWidth, virtualHeight, worldWidth)
        }
    }

    private fun DrawScope.drawDistantBastionSilhouettes(
        camX: Float,
        camY: Float,
        vWidth: Float,
        vHeight: Float,
        worldWidth: Float
    ) {
        val spacing = 450f
        var x = -camX % spacing - 150f
        while (x < vWidth + 300f) {
            val worldSectionX = camX + x
            if (worldSectionX > -300f && worldSectionX < worldWidth + 300f) {
                val verticalOffset = (camY * 0.5f).coerceIn(-40f, 40f)
                val path = Path().apply {
                    moveTo(x, vHeight + verticalOffset)
                    lineTo(x, 80f + verticalOffset)
                    lineTo(x + 100f, -20f + verticalOffset)
                    lineTo(x + 200f, 80f + verticalOffset)
                    lineTo(x + 200f, vHeight + verticalOffset)
                    close()
                }
                drawPath(path, gothicArchShadow)

                drawRect(
                    color = coolMoonlight.copy(alpha = 0.12f),
                    topLeft = Offset(x + 60f, 120f + verticalOffset),
                    size = Size(80f, 180f)
                )
            }
            x += spacing
        }
    }

    private fun DrawScope.drawMidgroundInfrastructure(
        camX: Float,
        camY: Float,
        vWidth: Float,
        vHeight: Float,
        worldWidth: Float
    ) {
        val spacing = 320f
        var x = -camX % spacing - 100f
        while (x < vWidth + 200f) {
            val worldSectionX = camX + x
            if (worldSectionX > -200f && worldSectionX < worldWidth + 200f) {
                val verticalOffset = camY.coerceIn(-60f, 60f)
                drawRect(
                    color = structuralSteel,
                    topLeft = Offset(x, 40f + verticalOffset),
                    size = Size(18f, vHeight - 40f)
                )

                drawLine(
                    color = copperConduit,
                    start = Offset(x - 80f, 200f + verticalOffset),
                    end = Offset(x + 240f, 200f + verticalOffset),
                    strokeWidth = 5f
                )

                drawRect(
                    color = darkSlate,
                    topLeft = Offset(x - 6f, 185f + verticalOffset),
                    size = Size(30f, 30f)
                )
                drawCircle(
                    color = amberLight,
                    radius = 3.5f,
                    center = Offset(x + 9f, 200f + verticalOffset)
                )
            }
            x += spacing
        }
    }
}
