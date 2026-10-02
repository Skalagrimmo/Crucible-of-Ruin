package com.example.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import com.example.game.data.AuthRepository
import com.example.game.data.UserSave
import com.example.game.data.UserSaveRepository
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min

@Composable
fun GameScreen(
    levelId: String = "level_01",
    startCheckpoint: Int = 0,
    initialTargetFps: Int = 30,
    onNextLevel: (() -> Unit)? = null,
    onReturnToMainMenu: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val authRepo = remember { com.example.game.data.AuthRepository(context) }
    val saveRepo = remember { com.example.game.data.UserSaveRepository(context) }
    val user by authRepo.currentUser.collectAsState()

    val engine = remember(context, levelId) {
        GameEngine(AndroidLevelAssets.level(context, levelId)).apply {
            setStartCheckpoint(startCheckpoint)
        }
    }

    // Auto-save checkpoint progress to local storage and Firestore
    engine.onCheckpointActivated = { cpId ->
        val currentUid = user?.uid ?: ""
        scope.launch {
            val current = saveRepo.getUserSave(currentUid)
            saveRepo.saveUserSave(
                current.copy(
                    lastCheckpoint = maxOf(current.lastCheckpoint, cpId),
                    foesSlain = current.foesSlain + engine.enemiesDefeated
                )
            )
        }
    }

    // Auto-save victory stats
    LaunchedEffect(engine.state) {
        if (engine.state == "victory") {
            val currentUid = user?.uid ?: ""
            scope.launch {
                val current = saveRepo.getUserSave(currentUid)
                val bestTime = if (current.bestTimeSeconds <= 0f) engine.timeElapsed else minOf(current.bestTimeSeconds, engine.timeElapsed)
                saveRepo.saveUserSave(
                    current.copy(
                        bestTimeSeconds = bestTime,
                        foesSlain = current.foesSlain + engine.enemiesDefeated,
                        highScore = maxOf(current.highScore, (engine.enemiesDefeated * 100 + engine.player.hp.toInt() * 10))
                    )
                )
            }
        }
    }

    // Recomposition ticker triggered only on frame ticks
    var frameTick by remember { mutableIntStateOf(0) }

    // Target frame rate cap (default: 30 FPS for low-end hardware optimization)
    var targetFps by remember { mutableIntStateOf(initialTargetFps) }

    // Forced Frame Rate Capped Game Loop using withFrameNanos (zero GC allocation)
    LaunchedEffect(targetFps) {
        val targetFrameNanos = 1_000_000_000L / targetFps
        var lastFrameTimeNanos = 0L

        while (true) {
            withFrameNanos { nowNanos ->
                if (lastFrameTimeNanos == 0L) {
                    lastFrameTimeNanos = nowNanos
                    return@withFrameNanos
                }

                val elapsedNanos = nowNanos - lastFrameTimeNanos
                // 1.5ms phase tolerance for stable frame pacing across 60/90/120Hz displays
                if (elapsedNanos >= targetFrameNanos - 1_500_000L) {
                    var dt = elapsedNanos / 1_000_000_000f
                    lastFrameTimeNanos = nowNanos

                    // Clamp delta time to 0.05s max to preserve physics stability
                    if (dt > 0.05f) dt = 0.05f

                    engine.update(dt)
                    frameTick++
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B0811))
    ) {
        // Main Game Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Read frameTick to trigger redraw
            val tick = frameTick
            if (tick >= 0) {
                GameRenderer.render(this, engine)
            }
        }

        // Top Left: HUD (Vitae & Flasks)
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            // Health Bar
            Box(
                modifier = Modifier
                    .width(180.dp)
                    .height(20.dp)
                    .background(Color(0xD0100A1A), RoundedCornerShape(4.dp))
                    .border(1.5.dp, Color(0xFF8F77AA), RoundedCornerShape(4.dp))
            ) {
                val hpRatio = (engine.player.hp / engine.player.maxHp).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxSize(fraction = hpRatio)
                        .background(if (hpRatio > 0.35f) Color(0xFFE63946) else Color(0xFFD90429))
                )
                Text(
                    text = "VITAE: ${engine.player.hp.toInt()} / 100",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Reagent Flasks
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (i in 0 until engine.player.maxFlasks) {
                    val hasFlask = i < engine.player.flasks
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(
                                if (hasFlask) Color(0xFFB040F0) else Color(0xFF33203E),
                                RoundedCornerShape(3.dp)
                            )
                            .border(1.dp, Color(0xFFB050D0), RoundedCornerShape(3.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (hasFlask) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(Color(0xFF40FFD0), CircleShape)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = engine.activeCheckpoint.name,
                color = Color(0xB3E5DBF7),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // Top Right: Controls (FPS Cap, Sound & Pause)
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Frame Rate Cap Toggle Button (30 FPS Power Saver / 60 FPS)
            Button(
                onClick = {
                    targetFps = if (targetFps == 30) 60 else 30
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (targetFps == 30) Color(0xCC1E3A27) else Color(0x99181124)
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .height(38.dp)
                    .border(
                        1.dp,
                        if (targetFps == 30) Color(0xFF40FF80) else Color(0x55FFFFFF),
                        RoundedCornerShape(12.dp)
                    )
                    .testTag("fps_cap_button")
            ) {
                Text(
                    text = "${targetFps} FPS",
                    color = if (targetFps == 30) Color(0xFF80FFA8) else Color.White,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            var isMuted by remember { mutableStateOf(false) }
            IconButton(
                onClick = {
                    isMuted = !isMuted
                    engine.audio.isMuted = isMuted
                },
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0x99181124), CircleShape)
                    .border(1.dp, Color(0x55FFFFFF), CircleShape)
                    .testTag("mute_button")
            ) {
                Text(text = if (isMuted) "🔇" else "🔊", fontSize = 16.sp)
            }

            IconButton(
                onClick = {
                    engine.state = if (engine.state == "paused") "playing" else "paused"
                },
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0x99181124), CircleShape)
                    .border(1.dp, Color(0x55FFFFFF), CircleShape)
                    .testTag("pause_button")
            ) {
                Text(text = if (engine.state == "paused") "▶" else "⏸", fontSize = 16.sp, color = Color.White)
            }
        }

        // Boss Health Bar (Bottom center, when active)
        if (engine.boss.active && !engine.boss.dead) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (engine.boss.phase2) "IGNIS CHIMERA - ENRAGED" else "IGNIS, THE ALCHEMICAL CHIMERA",
                    color = if (engine.boss.phase2) Color(0xFFFF2050) else Color(0xFFFFD700),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .width(360.dp)
                        .height(14.dp)
                        .background(Color(0xDD100A1A), RoundedCornerShape(3.dp))
                        .border(1.dp, if (engine.boss.phase2) Color(0xFFFF2050) else Color(0xFFD0A040), RoundedCornerShape(3.dp))
                ) {
                    val ratio = (engine.boss.hp / engine.boss.maxHp).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxSize(fraction = ratio)
                            .background(if (engine.boss.phase2) Color(0xFFB01035) else Color(0xFFC95020))
                    )
                }
            }
        }

        // On-Screen Touch Controls (Multi-touch with detectTapGestures & pointer inputs)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            // Directional Buttons (Left / Right)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TouchHoldButton(
                    symbol = "◀",
                    label = "Left",
                    onHoldChange = { isPressed -> engine.inputLeft = isPressed },
                    modifier = Modifier.testTag("btn_left")
                )
                TouchHoldButton(
                    symbol = "▶",
                    label = "Right",
                    onHoldChange = { isPressed -> engine.inputRight = isPressed },
                    modifier = Modifier.testTag("btn_right")
                )
            }

            // Action Buttons (Sub, Attack, Jump)
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                // Flask Sub-Weapon
                TouchTapButton(
                    symbol = "🧪",
                    label = "Flask",
                    size = 56,
                    color = Color(0x9945193C),
                    borderColor = Color(0xFFB050D0),
                    onTap = { engine.onSubPressed() },
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .testTag("btn_flask")
                )

                // Melee Slash Attack
                TouchTapButton(
                    symbol = "⚔️",
                    label = "Attack",
                    size = 68,
                    color = Color(0x992E1B46),
                    borderColor = Color(0xFF8F77AA),
                    onTap = { engine.onAttackPressed() },
                    modifier = Modifier.testTag("btn_attack")
                )

                // Jump Button (supports hold for full trajectory)
                TouchHoldButton(
                    symbol = "▲",
                    label = "Jump",
                    size = 68,
                    color = Color(0x99261C3D),
                    borderColor = Color(0xFFB096DA),
                    onHoldChange = { isHeld ->
                        engine.inputJump = isHeld
                        if (isHeld) engine.onJumpPressed()
                    },
                    modifier = Modifier.testTag("btn_jump")
                )
            }
        }

        // Pause Menu Dialog
        if (engine.state == "paused") {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xD908050E)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = "GAME PAUSED",
                        color = Color(0xFFF0D890),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Crucible of Ruin - Native Android Edition",
                        color = Color(0xB3E5DBF7),
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { engine.state = "playing" },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF594475)),
                        modifier = Modifier.testTag("resume_button")
                    ) {
                        Text("Resume Game", color = Color.White)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { targetFps = if (targetFps == 30) 60 else 30 },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF37274E)),
                        modifier = Modifier.testTag("toggle_fps_pause_menu")
                    ) {
                        Text(
                            text = if (targetFps == 30) "Frame Rate: 30 FPS (Power Saver Active)" else "Frame Rate: 60 FPS (Full Rate)",
                            color = if (targetFps == 30) Color(0xFF80FFA8) else Color.White,
                            fontSize = 13.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { engine.restart() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E204A)),
                        modifier = Modifier.testTag("restart_button")
                    ) {
                        Text("Restart Stage", color = Color.White)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { onReturnToMainMenu() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B112B)),
                        modifier = Modifier.testTag("pause_main_menu_button")
                    ) {
                        Text("Return to Main Menu", color = Color(0xFFDCCFF5))
                    }
                }
            }
        }

        // Perished Overlay
        if (engine.player.dead) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xB3100614)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "YOU HAVE PERISHED",
                        color = Color(0xFFE63946),
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Respawning at altar shrine...",
                        color = Color(0xFFD8D0E5),
                        fontSize = 14.sp
                    )
                }
            }
        }

        // Victory Stage Clear Screen
        if (engine.state == "victory") {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xE608050E)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = "STAGE CLEAR",
                        color = Color(0xFFFFD700),
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (levelId == "level_01") "The Crucible Chimera Has Been Purged" else "Stage Guardian Has Been Purged",
                        color = Color(0xFF90FFC0),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    val mins = (engine.timeElapsed / 60).toInt()
                    val secs = (engine.timeElapsed % 60).toInt()
                    Text(
                        text = "Time Elapsed: ${mins}m ${secs}s\nFoes Slain: ${engine.enemiesDefeated}\nVitae Preserved: ${engine.player.hp.toInt()}%",
                        color = Color(0xFFE5DBF7),
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (onNextLevel != null) {
                            Button(
                                onClick = onNextLevel,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                                modifier = Modifier.testTag("next_level_button")
                            ) {
                                Text("Next Stage", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                        Button(
                            onClick = { engine.restart() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                            modifier = Modifier.testTag("play_again_button")
                        ) {
                            Text("Play Again", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { onReturnToMainMenu() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E1C48)),
                            modifier = Modifier.testTag("victory_main_menu_button")
                        ) {
                            Text("Main Menu", color = Color(0xFFFFD700), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TouchHoldButton(
    symbol: String,
    label: String,
    size: Int = 64,
    color: Color = Color(0x991A1426),
    borderColor: Color = Color(0x73B096DA),
    onHoldChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(size.dp)
            .background(
                if (isPressed) Color(0xCC7846B4) else color,
                RoundedCornerShape((size / 3).dp)
            )
            .border(
                2.dp,
                if (isPressed) Color(0xFFF0C050) else borderColor,
                RoundedCornerShape((size / 3).dp)
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        onHoldChange(true)
                        tryAwaitRelease()
                        isPressed = false
                        onHoldChange(false)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = symbol,
                color = if (isPressed) Color.White else Color(0xFFE5DBF7),
                fontSize = (size * 0.36f).sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                color = Color(0xB3E5DBF7),
                fontSize = 8.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun TouchTapButton(
    symbol: String,
    label: String,
    size: Int = 64,
    color: Color = Color(0x991A1426),
    borderColor: Color = Color(0x73B096DA),
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(size.dp)
            .background(
                if (isPressed) Color(0xCC7846B4) else color,
                CircleShape
            )
            .border(
                2.dp,
                if (isPressed) Color(0xFFF0C050) else borderColor,
                CircleShape
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        onTap()
                        tryAwaitRelease()
                        isPressed = false
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = symbol,
                color = if (isPressed) Color.White else Color(0xFFE5DBF7),
                fontSize = (size * 0.32f).sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                color = Color(0xB3E5DBF7),
                fontSize = 8.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
