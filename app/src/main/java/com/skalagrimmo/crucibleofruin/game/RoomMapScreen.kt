package com.skalagrimmo.crucibleofruin.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Stage 6d (#38): the world map overlay.
 *
 * Renders the engine's RoomGraph with a fog of war: visited rooms carry their
 * names, unvisited rooms show as dark silhouettes with a question mark, doors
 * appear as gold notches on the shared boundaries, and a live marker follows
 * the player through the current room. Tapping anywhere (or the Close button)
 * dismisses the map; the caller decides whether play resumes.
 */
@Composable
fun RoomMapOverlay(
    engine: GameEngine,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val rooms = engine.roomGraph.rooms
    val visited = engine.visitedRooms
    val current = engine.currentRoom

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xD908050E))
            .clickable { onClose() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "WORLD MAP",
                color = Color(0xFFF0D890),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Rooms Charted: " + visited.size + " / " + rooms.size,
                color = Color(0xB3E5DBF7),
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(14.dp))
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
                    .background(Color(0x140E1A26), RoundedCornerShape(10.dp))
                    .border(1.5.dp, Color(0xFF8F77AA), RoundedCornerShape(10.dp))
            ) {
                val scale = minOf(
                    size.width / engine.levelWidth,
                    size.height / engine.levelHeight
                )
                val ox = (size.width - engine.levelWidth * scale) / 2f
                val oy = (size.height - engine.levelHeight * scale) / 2f

                // Doors: gold notches on the shared boundary of charted rooms.
                for (room in rooms) {
                    if (room.id !in visited) continue
                    for (door in room.doors) {
                        val nx = ox + door.x * scale
                        val ny = oy + door.y * scale
                        drawLine(
                            color = Color(0xFFF0C050).copy(alpha = 0.9f),
                            start = Offset(nx - 7f, ny),
                            end = Offset(nx + 7f, ny),
                            strokeWidth = 3f
                        )
                    }
                }

                for (room in rooms) {
                    val isVisited = room.id in visited
                    val isCurrent = current?.id == room.id
                    val left = ox + room.x * scale
                    val top = oy + room.y * scale
                    val w = room.w * scale
                    val h = room.h * scale

                    drawRect(
                        color = when {
                            isCurrent -> Color(0x59382A10)
                            isVisited -> Color(0x40263C4E)
                            else -> Color(0x1A161022)
                        },
                        topLeft = Offset(left, top),
                        size = Size(w, h)
                    )
                    drawRect(
                        color = when {
                            isCurrent -> Color(0xFFF0C050)
                            isVisited -> Color(0xFF8F77AA)
                            else -> Color(0x33443068)
                        },
                        topLeft = Offset(left, top),
                        size = Size(w, h),
                        style = Stroke(2f)
                    )

                    if (isVisited) {
                        val label = textMeasurer.measure(
                            room.name,
                            TextStyle(
                                color = Color(0xFFE5DBF7),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        if (label.size.width <= w - 4f) {
                            drawText(
                                label,
                                topLeft = Offset(
                                    left + (w - label.size.width) / 2f,
                                    top + h / 2f - label.size.height / 2f
                                )
                            )
                        }
                    } else {
                        val unknown = textMeasurer.measure(
                            "?",
                            TextStyle(
                                color = Color(0x66556677),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        drawText(
                            unknown,
                            topLeft = Offset(
                                left + (w - unknown.size.width) / 2f,
                                top + h / 2f - unknown.size.height / 2f
                            )
                        )
                    }
                }

                // Live marker: the player's position inside the current room.
                if (current != null) {
                    drawCircle(
                        color = Color(0xFFFFE082),
                        radius = 4f,
                        center = Offset(
                            ox + engine.player.x * scale,
                            oy + engine.player.y * scale
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF594475)),
                modifier = Modifier.testTag("map_close_button")
            ) {
                Text("Close Map", color = Color.White)
            }
        }
    }
}
