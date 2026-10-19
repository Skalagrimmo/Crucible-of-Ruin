package com.example.game.codex

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.alchemy.AlchemyViewModel
import com.example.game.alchemy.PermanentBuffType
import com.example.game.data.UserSave

/**
 * Grimoire & Codex of Ruin
 * Dark-fantasy gothic grimoire presenting monster lore, boss telegraph guides,
 * alchemical transmutation recipes, and combat mastery statistics.
 */
@Composable
fun GrimoireCodexDialog(
    userSave: UserSave,
    alchemyViewModel: AlchemyViewModel? = null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("📖 Bestiary", "⚗️ Transmutations", "⚔️ Combat Mastery")

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xE60A0612))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.88f)
                .clickable(enabled = false) {} // Prevent accidental close on dialog click
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF1B112B), Color(0xFF10091D))
                    ),
                    RoundedCornerShape(16.dp)
                )
                .border(1.5.dp, Color(0xFFB096DA), RoundedCornerShape(16.dp))
                .padding(18.dp)
                .testTag("dialog_grimoire_codex")
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "GRIMOIRE OF RUIN",
                            color = Color(0xFFFFD700),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Serif,
                            letterSpacing = 1.2.sp
                        )
                        Text(
                            text = "Ancient Alchemical Lore & Bestiary of the Crucible",
                            color = Color(0xFFC0A8DC),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x66291642)),
                        shape = CircleShape,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                        modifier = Modifier
                            .size(34.dp)
                            .border(1.dp, Color(0xFFB096DA), CircleShape)
                            .testTag("btn_close_grimoire")
                    ) {
                        Text("✕", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Custom Tab Bar
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color(0x66180E28),
                    contentColor = Color(0xFFFFD700),
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = Color(0xFFFFD700),
                            height = 2.dp
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0x33B096DA), RoundedCornerShape(8.dp))
                ) {
                    tabTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    text = title,
                                    fontSize = 11.sp,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTab == index) Color(0xFFFFD700) else Color(0xFFB096DA)
                                )
                            },
                            modifier = Modifier.testTag("tab_grimoire_$index")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Tab Content
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    when (selectedTab) {
                        0 -> BestiaryTabContent(userSave)
                        1 -> TransmutationsTabContent(userSave, alchemyViewModel)
                        2 -> CombatMasteryTabContent(userSave)
                    }
                }
            }
        }
    }
}

@Composable
private fun BestiaryTabContent(userSave: UserSave) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        BestiaryCard(
            title = "CRUCIBLE CHIMERA (APEX BOSS)",
            rank = "Crucible Sovereign",
            symbol = "🐉",
            accentColor = Color(0xFFFF5722),
            lore = "A monstrous amalgamation of obsidian slag, volcanic brimstone, and forbidden alchemical ichor. Born from philosopher stone transmutations deep in the laboratory crucible.",
            telegraphTip = "Telegraphs: Dash Lunge (forward spark charge), Ground Slam (skyward leap), Magma Mortar (volcanic lob), Void Eruption (phase 3 dual ground shockwaves).",
            weakness = "CRITICAL WEAK POINT: Core exposed during recovery and stagger. Inflict Corrosion (+30% DMG) to shatter its posture.",
            tacticalTip = "Alchemical Riposte: Dash forward through its Dash Lunge to trigger a Chrono Break, replenishing your flasks and counter-rupturing its molten armor!"
        )

        BestiaryCard(
            title = "OBSIDIAN GOLEM",
            rank = "Heavy Armored Sentinel",
            symbol = "🗿",
            accentColor = Color(0xFFB388FF),
            lore = "Imposing animated monoliths carved from solid bedrock and infused with corrosive quicksilver. Their pulverizing fists generate shockwaves.",
            telegraphTip = "Attacks: Slow ground slam with high knockback. Inflicts Corrosion status.",
            weakness = "Vulnerable to Alchemy-Burn DoT which melts internal silicate mortar.",
            tacticalTip = "High natural defense (+30% armor). Soften with Corrosion or burn with flasks from distance."
        )

        BestiaryCard(
            title = "CHERUB IMP",
            rank = "Aerial Corrosive Stalker",
            symbol = "🦇",
            accentColor = Color(0xFF76FF03),
            lore = "Corrupted winged homunculi hovering near cavern ceilings, spitting caustic vitriol globes onto unwary trespassers.",
            telegraphTip = "Attacks: Sinuously dives when player is below; spits acid spray inflicting Alchemy-Burn.",
            weakness = "Extremely low durability (30 HP). A single melee slash or thrown flask destroys it instantly.",
            tacticalTip = "Use upward jump attacks or time your approach to strike as it lowers into melee reach."
        )

        BestiaryCard(
            title = "CULTIST INITIATE",
            rank = "Void Sorcerer",
            symbol = "🧙",
            accentColor = Color(0xFF40F0FF),
            lore = "Fallen scholars sworn to the Crucible, channeling eldritch plasma orbs from ritual parchment.",
            telegraphTip = "Attacks: Charges a green void orb for 2 seconds before hurling horizontally.",
            weakness = "Long spellcasting delay leaves them completely defenseless.",
            tacticalTip = "Shadow Dash forward to close the gap and strike before their void projectile launches."
        )
    }
}

@Composable
private fun BestiaryCard(
    title: String,
    rank: String,
    symbol: String,
    accentColor: Color,
    lore: String,
    telegraphTip: String,
    weakness: String,
    tacticalTip: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xD9160D25), RoundedCornerShape(10.dp))
            .border(1.dp, accentColor.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(accentColor.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        .border(1.dp, accentColor, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(symbol, fontSize = 20.sp)
                }

                Column {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = rank,
                        color = accentColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = lore,
                color = Color(0xFFE5DBF7),
                fontSize = 10.sp,
                lineHeight = 14.sp
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x6610091E), RoundedCornerShape(6.dp))
                    .padding(8.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = "⚡ $telegraphTip",
                        color = Color(0xFFFFD54F),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "🎯 $weakness",
                        color = Color(0xFF80D8FF),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "💡 $tacticalTip",
                        color = Color(0xFF69F0AE),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun TransmutationsTabContent(userSave: UserSave, alchemyViewModel: AlchemyViewModel?) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "PERMANENT AUGMENTATION MASTERIES",
            color = Color(0xFFFFD700),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp
        )

        PermanentBuffType.entries.forEach { buff ->
            val rank = when (buff) {
                PermanentBuffType.VITALITY_CORE -> userSave.permanentVitality
                PermanentBuffType.IGNIS_FERVOR -> userSave.permanentDamage
                PermanentBuffType.QUICKSILVER_STRIDE -> userSave.permanentSpeed
                PermanentBuffType.CRUCIBLE_VESSEL -> userSave.permanentFlasks
                PermanentBuffType.AEGIS_INFUSION -> userSave.permanentDefense
                PermanentBuffType.VITRIOLIC_TOUCH -> userSave.permanentVitriol
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xD9160D25), RoundedCornerShape(8.dp))
                    .border(1.dp, buff.color.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(buff.icon, fontSize = 18.sp)
                        Column {
                            Text(
                                text = buff.title,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = buff.description,
                                color = Color(0xFFC0A8DC),
                                fontSize = 9.sp
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .background(buff.color.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                            .border(1.dp, buff.color, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "Rank $rank / ${buff.maxRank}",
                            color = buff.color,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "REAGENTS OF THE GREAT WORK",
            color = Color(0xFFFFD700),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ReagentPouchItem("Sulfur Ash", "🌋", userSave.savedSulfur, Color(0xFFFFB039), Modifier.weight(1f))
            ReagentPouchItem("Quicksilver", "💧", userSave.savedMercury, Color(0xFF64D8CB), Modifier.weight(1f))
            ReagentPouchItem("Vitriol Crystal", "💎", userSave.savedVitriol, Color(0xFFB388FF), Modifier.weight(1f))
        }
    }
}

@Composable
private fun ReagentPouchItem(
    name: String,
    icon: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color(0xD9160D25), RoundedCornerShape(8.dp))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(icon, fontSize = 16.sp)
            Text(name, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text("Stock: $count", color = color, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun CombatMasteryTabContent(userSave: UserSave) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Spotlight: Shadow Dash & Perfect Riposte Mechanic
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFF23113D), Color(0xFF101B38))
                    ),
                    RoundedCornerShape(10.dp)
                )
                .border(1.5.dp, Color(0xFF00E5FF), RoundedCornerShape(10.dp))
                .padding(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("💨", fontSize = 22.sp)
                    Column {
                        Text(
                            text = "SHADOW DASH & PERFECT RIPOSTE",
                            color = Color(0xFF00E5FF),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = "Acrobatic Evasion & Counter-Shockwave",
                            color = Color(0xFFFFD700),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "Surge horizontally with complete invulnerability frames, trailing ethereal shadow afterimages. Dashing directly through an incoming attack, projectile, or boss telegraph triggers a PERFECT RIPOSTE:",
                    color = Color(0xFFE5DBF7),
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x66081024), RoundedCornerShape(6.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text("• ⏳ Chrono-Break: Slows down time for 0.22s to maneuver behind foes.", color = Color(0xFF80D8FF), fontSize = 9.sp)
                    Text("• 🧪 Alchemical Synthesis: Instantly replenishes +1 Alchemical Flask!", color = Color(0xFF69F0AE), fontSize = 9.sp)
                    Text("• 💥 Kinetic Rupture: Detonates a 55 DMG shockwave applying 2x Corrosion & 2x Burn to the Boss!", color = Color(0xFFFFD54F), fontSize = 9.sp)
                }
            }
        }

        Text(
            text = "CAREER COMBAT RECORD",
            color = Color(0xFFFFD700),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard("Total Foes Slain", "💀", "${userSave.foesSlain}", Color(0xFFFF5252), Modifier.weight(1f))
            StatCard("Perfect Ripostes", "⚡", "${userSave.perfectDodges}", Color(0xFF00E5FF), Modifier.weight(1f))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val bestTimeFormatted = if (userSave.bestTimeSeconds > 0f) {
                val mins = (userSave.bestTimeSeconds / 60).toInt()
                val secs = (userSave.bestTimeSeconds % 60).toInt()
                String.format("%02d:%02d", mins, secs)
            } else "--:--"
            StatCard("Fastest Clear", "⏱️", bestTimeFormatted, Color(0xFFFFD54F), Modifier.weight(1f))
            StatCard("High Score", "🏆", "${userSave.highScore}", Color(0xFFB388FF), Modifier.weight(1f))
        }

        // Grimoire Mastery Perk Badge
        val isAdeptUnlocked = userSave.perfectDodges >= 3 && userSave.foesSlain >= 5
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (isAdeptUnlocked) Color(0xD91E1436) else Color(0x66160E28),
                    RoundedCornerShape(8.dp)
                )
                .border(
                    1.dp,
                    if (isAdeptUnlocked) Color(0xFFFFD700) else Color(0x448F77AA),
                    RoundedCornerShape(8.dp)
                )
                .padding(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(if (isAdeptUnlocked) "🌟" else "🔒", fontSize = 18.sp)
                Column {
                    Text(
                        text = "MASTERY PERK: ADEPT OF THE CRUCIBLE",
                        color = if (isAdeptUnlocked) Color(0xFFFFD700) else Color(0xFF8F77AA),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isAdeptUnlocked) "ACTIVE: Perfect Riposte radius augmented by +25% with radiant flash." else "LOCKED: Perform 3 Perfect Ripostes & slay 5 foes to unlock.",
                        color = if (isAdeptUnlocked) Color(0xFF69F0AE) else Color(0xFF8F77AA),
                        fontSize = 9.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    icon: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color(0xD9160D25), RoundedCornerShape(8.dp))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(icon, fontSize = 16.sp)
            Text(label, color = Color(0xFFC0A8DC), fontSize = 9.sp)
            Text(value, color = color, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace)
        }
    }
}
