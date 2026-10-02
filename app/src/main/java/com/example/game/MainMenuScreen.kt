package com.example.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.data.AuthRepository
import com.example.game.data.UserSave
import com.example.game.data.UserSaveRepository
import kotlinx.coroutines.launch
import kotlin.random.Random

@Composable
fun MainMenuScreen(
    onStartGame: (startCheckpoint: Int, targetFps: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authRepo = remember { AuthRepository(context) }
    val saveRepo = remember { UserSaveRepository(context) }

    val user by authRepo.currentUser.collectAsState()
    val isAuthLoading by authRepo.isLoading.collectAsState()
    val authError by authRepo.errorMessage.collectAsState()

    var userSave by remember { mutableStateOf(saveRepo.getLocalUserSave()) }
    var targetFps by remember { mutableIntStateOf(userSave.targetFps) }
    var soundMuted by remember { mutableStateOf(userSave.soundMuted) }

    // Dialog toggles
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showStatsDialog by remember { mutableStateOf(false) }
    var showControlsDialog by remember { mutableStateOf(false) }

    // Load user cloud save whenever user logs in or app launches
    LaunchedEffect(user) {
        val uid = user?.uid ?: ""
        val save = saveRepo.getUserSave(uid)
        userSave = save
        targetFps = save.targetFps
        soundMuted = save.soundMuted
        AssetCache.isMuted = soundMuted
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0C0714))
    ) {
        // Atmospheric Gothic Background with Floating Embers
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawRect(Color(0xFF09050F), size = Size(w, h))
            drawCircle(Color(0x1F78FFB4), radius = 80f, center = Offset(w * 0.82f, h * 0.25f))

            // Parallax spires
            var sx = -20f
            while (sx < w + 80f) {
                drawRect(Color(0xFF140C1E), topLeft = Offset(sx, h * 0.45f), size = Size(90f, h * 0.55f))
                drawCircle(Color(0xFF140C1E), radius = 45f, center = Offset(sx + 45f, h * 0.45f))
                sx += 240f
            }
        }

        // Top Header: User Profile & Google Sign-In Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Version and low-end profile tag
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .background(Color(0x8025173B), RoundedCornerShape(6.dp))
                        .border(1.dp, Color(0xFF6B459E), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "v1.2 NATIVE ANDROID",
                        color = Color(0xFFE2D6F5),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .background(Color(0x801B3A28), RoundedCornerShape(6.dp))
                        .border(1.dp, Color(0xFF38FF80), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${targetFps} FPS CAPPED",
                        color = Color(0xFF80FFA8),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Google Sign-In / Account status
            if (isAuthLoading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .background(Color(0xB3181124), RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    CircularProgressIndicator(color = Color(0xFFFFD700), modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("Authenticating...", color = Color.White, fontSize = 11.sp)
                }
            } else if (user != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .background(Color(0xCC1E1530), RoundedCornerShape(20.dp))
                        .border(1.dp, Color(0xFF8F77AA), RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(Color(0xFF8B5CF6), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = (user?.displayName ?: "A").take(1).uppercase(),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = user?.displayName ?: "Alchemist",
                        color = Color(0xFFFFD700),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Sign Out",
                        color = Color(0xFFFFA0A0),
                        fontSize = 10.sp,
                        modifier = Modifier
                            .clickable { authRepo.signOut() }
                            .padding(horizontal = 4.dp)
                            .testTag("sign_out_button")
                    )
                }
            } else {
                Button(
                    onClick = {
                        scope.launch { authRepo.signInWithGoogle() }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E1C44)),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .border(1.dp, Color(0xFFFFD700), RoundedCornerShape(20.dp))
                        .testTag("google_sign_in_button")
                ) {
                    Text("Sign in with Google", color = Color(0xFFFFD700), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Center: Classic Gothic Main Menu Column
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 380.dp)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Title
            Text(
                text = "CRUCIBLE OF RUIN",
                color = Color(0xFFFFD700),
                fontSize = 32.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "An Alchemical Dark Fantasy Platformer",
                color = Color(0xFFBCA6DF),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(28.dp))

            // 1. CONTINUE GAME (If checkpoint reached)
            val hasSaveProgress = userSave.lastCheckpoint > 0
            Button(
                onClick = {
                    onStartGame(userSave.lastCheckpoint, targetFps)
                },
                enabled = hasSaveProgress,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4C2A78),
                    disabledContainerColor = Color(0x5525163D)
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .border(
                        1.5.dp,
                        if (hasSaveProgress) Color(0xFFFFD700) else Color(0x33FFFFFF),
                        RoundedCornerShape(10.dp)
                    )
                    .testTag("menu_continue_button")
            ) {
                Text(
                    text = if (hasSaveProgress) "CONTINUE (SECTION ${userSave.lastCheckpoint + 1})" else "CONTINUE",
                    color = if (hasSaveProgress) Color.White else Color(0x66FFFFFF),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. NEW GAME
            Button(
                onClick = {
                    onStartGame(0, targetFps)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C1948)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .border(1.dp, Color(0xFF8F77AA), RoundedCornerShape(10.dp))
                    .testTag("menu_start_button")
            ) {
                Text(
                    text = "NEW GAME",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3. SETTINGS & OPTIONS
            Button(
                onClick = { showSettingsDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F1233)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .border(1.dp, Color(0x668F77AA), RoundedCornerShape(10.dp))
                    .testTag("menu_options_button")
            ) {
                Text("OPTIONS & PERFORMANCE", color = Color(0xFFDCCFF5), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 4. ARCHIVES / STATS / CLOUD
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { showStatsDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A0F2B)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .border(1.dp, Color(0x558F77AA), RoundedCornerShape(10.dp))
                        .testTag("menu_stats_button")
                ) {
                    Text("ARCHIVES", color = Color(0xFFDCCFF5), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = { showControlsDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A0F2B)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .border(1.dp, Color(0x558F77AA), RoundedCornerShape(10.dp))
                        .testTag("menu_controls_button")
                ) {
                    Text("CONTROLS", color = Color(0xFFDCCFF5), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Bottom Footer status
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
        ) {
            val status = if (user != null) "Cloud Sync Active • Firebase Connected" else "Guest Mode • Sign in to save progress to the cloud"
            Text(
                text = status,
                color = if (user != null) Color(0xFF80FFA8) else Color(0x80D5CBEC),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        // --- DIALOG 1: SETTINGS & OPTIONS ---
        if (showSettingsDialog) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xD9090610))
                    .clickable { showSettingsDialog = false },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 440.dp)
                        .background(Color(0xFF1B1229), RoundedCornerShape(14.dp))
                        .border(1.5.dp, Color(0xFF8F77AA), RoundedCornerShape(14.dp))
                        .clickable(enabled = false) {}
                        .padding(24.dp)
                ) {
                    Text(
                        text = "OPTIONS & PERFORMANCE",
                        color = Color(0xFFFFD700),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(18.dp))

                    // Frame Rate Option
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Frame Rate Cap", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = if (targetFps == 30) "30 FPS (Recommended for 1.4 GHz & 2GB RAM)" else "60 FPS (Full Speed)",
                                color = Color(0xFF90FFB0),
                                fontSize = 11.sp
                            )
                        }
                        Button(
                            onClick = {
                                targetFps = if (targetFps == 30) 60 else 30
                                userSave = userSave.copy(targetFps = targetFps)
                                scope.launch { saveRepo.saveUserSave(userSave) }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4C2A78)),
                            modifier = Modifier.testTag("dialog_toggle_fps")
                        ) {
                            Text("${targetFps} FPS", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Audio Option
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Master Audio", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = if (soundMuted) "Muted" else "Enabled (Native procedural audio)",
                                color = Color(0xFFB0A2C8),
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = !soundMuted,
                            onCheckedChange = { isEnabled ->
                                soundMuted = !isEnabled
                                AssetCache.isMuted = soundMuted
                                userSave = userSave.copy(soundMuted = soundMuted)
                                scope.launch { saveRepo.saveUserSave(userSave) }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFFFD700))
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = { showSettingsDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF594475)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("close_settings_button")
                    ) {
                        Text("Save & Close", color = Color.White)
                    }
                }
            }
        }

        // --- DIALOG 2: ARCHIVES / STATS ---
        if (showStatsDialog) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xD9090610))
                    .clickable { showStatsDialog = false },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 440.dp)
                        .background(Color(0xFF1B1229), RoundedCornerShape(14.dp))
                        .border(1.5.dp, Color(0xFF8F77AA), RoundedCornerShape(14.dp))
                        .clickable(enabled = false) {}
                        .padding(24.dp)
                ) {
                    Text(
                        text = "ALCHEMICAL ARCHIVES",
                        color = Color(0xFFFFD700),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    val mins = (userSave.bestTimeSeconds / 60).toInt()
                    val secs = (userSave.bestTimeSeconds % 60).toInt()

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("• Player: ${user?.displayName ?: "Guest Alchemist"}", color = Color.White, fontSize = 13.sp)
                        Text("• Highest Checkpoint: Section ${userSave.lastCheckpoint + 1}", color = Color(0xFFDCCFF5), fontSize = 13.sp)
                        Text("• Best Stage Clear Time: ${mins}m ${secs}s", color = Color(0xFFDCCFF5), fontSize = 13.sp)
                        Text("• Foes Vanquished: ${userSave.foesSlain}", color = Color(0xFFDCCFF5), fontSize = 13.sp)
                        Text(
                            text = if (user != null) "• Cloud Storage: Synced with Firestore" else "• Cloud Storage: Not connected (Sign in with Google)",
                            color = if (user != null) Color(0xFF80FFA8) else Color(0xFFFFB080),
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = { showStatsDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF594475)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Close", color = Color.White)
                    }
                }
            }
        }

        // --- DIALOG 3: CONTROLS & HOW TO PLAY ---
        if (showControlsDialog) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xD9090610))
                    .clickable { showControlsDialog = false },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 480.dp)
                        .background(Color(0xFF1B1229), RoundedCornerShape(14.dp))
                        .border(1.5.dp, Color(0xFF8F77AA), RoundedCornerShape(14.dp))
                        .clickable(enabled = false) {}
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "CONTROLS & COMBAT GUIDE",
                        color = Color(0xFFFFD700),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("◀ ▶ Movement: Traverse left and right through ruined gothic halls.", color = Color.White, fontSize = 12.sp)
                        Text("▲ Jump: Tap for short hop, hold for maximum vertical reach. Coyote time allows jumping briefly after walking off ledges.", color = Color(0xFFDCCFF5), fontSize = 12.sp)
                        Text("⚔️ Melee Slash: Deliver rapid sword strikes. Shatters breakable urns and deals 28 damage to foes.", color = Color(0xFFDCCFF5), fontSize = 12.sp)
                        Text("🧪 Alchemical Flask: Lob volatile reagent bottles with AoE explosion (45 damage). Essential for aerial cherubs and crowds.", color = Color(0xFFDCCFF5), fontSize = 12.sp)
                        Text("Altar Shrines: Touch golden shrines to bind your soul and unlock continue checkpoints.", color = Color(0xFF90FFC0), fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = { showControlsDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF594475)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Understood", color = Color.White)
                    }
                }
            }
        }
    }
}
