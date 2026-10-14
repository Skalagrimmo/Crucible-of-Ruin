package com.example.game.alchemy

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.game.GameAudio
import com.example.game.GameHaptics
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Full-featured interactive Alchemy Workbench UI.
 * Features a drag-and-drop Crucible where players place 3 collected reagents
 * to transmute permanent alchemical stat augmentations.
 */
@Composable
fun AlchemyWorkbench(
    alchemyViewModel: AlchemyViewModel,
    audio: GameAudio,
    haptics: GameHaptics?,
    onClose: () -> Unit,
    onBuffTransmuted: (PermanentBuffType) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val pouch by alchemyViewModel.pouch.collectAsState()
    val crucibleSlots by alchemyViewModel.crucibleSlots.collectAsState()
    val matchedRecipe by alchemyViewModel.matchedPermanentRecipe.collectAsState()
    val permanentRanks by alchemyViewModel.permanentBuffRanks.collectAsState()
    val craftingNotice by alchemyViewModel.craftingNotice.collectAsState()

    // Drag-and-drop state
    var draggedResource by remember { mutableStateOf<ResourceType?>(null) }
    var dragPosition by remember { mutableStateOf(Offset.Zero) }
    var isDragging by remember { mutableStateOf(false) }
    var crucibleTargetBounds by remember { mutableStateOf<Rect?>(null) }
    var isOverCrucibleTarget by remember { mutableStateOf(false) }

    // Runic rotation pulse
    val infiniteTransition = rememberInfiniteTransition(label = "workbench_pulse")
    val runeRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(14000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rune_rot"
    )
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_pulse"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFA0A0612))
            .padding(16.dp)
            .testTag("alchemy_workbench_root")
    ) {
        val rootWidth = maxWidth
        val rootHeight = maxHeight

        Column(modifier = Modifier.fillMaxSize()) {
            // --- TOP HEADER ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF1E122C), Color(0xFF2C1940), Color(0xFF1E122C))
                        ),
                        RoundedCornerShape(8.dp)
                    )
                    .border(1.dp, Color(0xFFD4AF37), RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("⚗️", fontSize = 20.sp)
                    Column {
                        Text(
                            text = "ALCHEMICAL TRANSMUTATION CRUCIBLE",
                            color = Color(0xFFFFD700),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )
                        Text(
                            text = "Drag 3 reagents into the Crucible to transmute permanent alchemical augmentations",
                            color = Color(0xFFC0AEE0),
                            fontSize = 10.sp
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Pouch inventory summary
                    Row(
                        modifier = Modifier
                            .background(Color(0x9910081C), RoundedCornerShape(6.dp))
                            .border(1.dp, Color(0x44D4AF37), RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (res in ResourceType.values()) {
                            Text(
                                text = "${res.symbol} ${pouch[res] ?: 0}",
                                color = res.color,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // Close Button
                    Button(
                        onClick = onClose,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A2B68)),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier
                            .height(30.dp)
                            .testTag("workbench_close_button")
                    ) {
                        Text("CLOSE ✕", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // --- MAIN 3-COLUMN WORKBENCH LAYOUT ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. LEFT PANEL: REAGENT SHELF (Draggable source items)
                Column(
                    modifier = Modifier
                        .weight(1.0f)
                        .fillMaxHeight()
                        .background(Color(0x99180E24), RoundedCornerShape(10.dp))
                        .border(1.dp, Color(0x338F77AA), RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "REAGENT SHELF",
                            color = Color(0xFFFFD700),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Hold & drag or tap to add",
                            color = Color(0xFF9E8DB8),
                            fontSize = 9.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        // Draggable Cards
                        for (res in ResourceType.values()) {
                            val count = pouch[res] ?: 0
                            val canUse = count > 0 && crucibleSlots.size < 3

                            DraggableReagentCard(
                                resource = res,
                                availableCount = count,
                                isEnabled = canUse,
                                onDragStarted = { startPos ->
                                    draggedResource = res
                                    dragPosition = startPos
                                    isDragging = true
                                    haptics?.onResourceCollected()
                                },
                                onDragDelta = { delta ->
                                    dragPosition += delta
                                    val target = crucibleTargetBounds
                                    isOverCrucibleTarget = target != null && target.contains(dragPosition)
                                },
                                onDragEnded = {
                                    val target = crucibleTargetBounds
                                    if (target != null && target.contains(dragPosition)) {
                                        val added = alchemyViewModel.addReagentToCrucible(res)
                                        if (added) {
                                            audio.play("throw")
                                            haptics?.onMeleeHit()
                                        }
                                    }
                                    draggedResource = null
                                    isDragging = false
                                    isOverCrucibleTarget = false
                                },
                                onCardTapped = {
                                    if (canUse) {
                                        val added = alchemyViewModel.addReagentToCrucible(res)
                                        if (added) {
                                            audio.play("throw")
                                            haptics?.onResourceCollected()
                                        }
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    Text(
                        text = "💡 Tip: 3 reagents react in the Crucible to forge permanent stat augments.",
                        color = Color(0x88D4AF37),
                        fontSize = 9.sp,
                        lineHeight = 12.sp
                    )
                }

                // 2. CENTER PANEL: THE TRANSMUTATION CRUCIBLE (Drop target)
                Column(
                    modifier = Modifier
                        .weight(1.35f)
                        .fillMaxHeight()
                        .onGloballyPositioned { coordinates ->
                            crucibleTargetBounds = coordinates.boundsInRoot()
                        }
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    if (isOverCrucibleTarget) Color(0xFF381944) else Color(0xFF1F1030),
                                    Color(0xFF140A22)
                                )
                            ),
                            RoundedCornerShape(10.dp)
                        )
                        .border(
                            width = if (isOverCrucibleTarget) 2.dp else 1.dp,
                            color = if (isOverCrucibleTarget) Color(0xFFFFD700) else Color(0x558F77AA),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .padding(12.dp)
                        .testTag("crucible_drop_zone"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "CRUCIBLE VESSEL",
                            color = Color(0xFFFFD700),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = if (isOverCrucibleTarget) "RELEASE TO ADD REAGENT" else "DROP TARGET [3 SLOTS]",
                            color = if (isOverCrucibleTarget) Color(0xFF80FFA8) else Color(0xFF9E8DB8),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // 3 Crucible Slots
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            for (slotIndex in 0 until 3) {
                                val itemInSlot = crucibleSlots.getOrNull(slotIndex)
                                CrucibleSlotBox(
                                    slotIndex = slotIndex,
                                    resource = itemInSlot,
                                    onRemove = {
                                        alchemyViewModel.removeReagentFromCrucible(slotIndex)
                                        audio.play("slash")
                                        haptics?.onMeleeSwing()
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Cauldron graphic & Runes
                        Box(
                            modifier = Modifier
                                .size(90.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val c = center
                                val r = size.minDimension / 2f
                                // Runic circle
                                drawCircle(
                                    color = (matchedRecipe?.buffType?.color ?: Color(0xFF8F77AA)).copy(alpha = glowPulse * 0.45f),
                                    radius = r - 4f,
                                    style = Stroke(width = 2f)
                                )
                                drawCircle(
                                    color = Color(0x228F77AA),
                                    radius = r - 12f
                                )
                                // Runic spokes
                                for (i in 0 until 6) {
                                    val angle = (runeRotation + i * 60f) * (Math.PI / 180.0).toFloat()
                                    val sx = c.x + cos(angle) * (r - 12f)
                                    val sy = c.y + sin(angle) * (r - 12f)
                                    drawLine(
                                        color = Color(0x66D4AF37),
                                        start = c,
                                        end = Offset(sx, sy),
                                        strokeWidth = 1.5f
                                    )
                                }
                            }
                            Text(
                                text = if (matchedRecipe != null) matchedRecipe!!.buffType.icon else "🜂",
                                fontSize = 28.sp,
                                modifier = Modifier.scale(0.9f + glowPulse * 0.15f)
                            )
                        }

                        // Transmutation Output Preview Box
                        Spacer(modifier = Modifier.height(6.dp))
                        if (matchedRecipe != null) {
                            val recipe = matchedRecipe!!
                            val buff = recipe.buffType
                            val currentRank = permanentRanks[buff] ?: 0
                            val isMax = currentRank >= buff.maxRank

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0x992B1540), RoundedCornerShape(8.dp))
                                    .border(1.dp, buff.color.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(buff.icon, fontSize = 14.sp)
                                    Text(
                                        text = buff.title.uppercase(),
                                        color = buff.color,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (isMax) "MAX RANK" else "Rank $currentRank → ${currentRank + 1}/${buff.maxRank}",
                                        color = Color(0xFFFFD700),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Text(
                                    text = buff.description,
                                    color = Color(0xFFC0AEE0),
                                    fontSize = 9.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else if (crucibleSlots.size == 3) {
                            Text(
                                text = "Unstable Mixture - No permanent reaction",
                                color = Color(0xFFFF8A80),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Text(
                                text = "Add ${3 - crucibleSlots.size} more reagent(s) to transmute",
                                color = Color(0x88FFFFFF),
                                fontSize = 10.sp
                            )
                        }
                    }

                    // Action Buttons (Transmute / Clear)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                alchemyViewModel.clearCrucible()
                                audio.play("slash")
                            },
                            enabled = crucibleSlots.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF332044),
                                disabledContainerColor = Color(0x22332044)
                            ),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .weight(0.4f)
                                .height(34.dp)
                                .testTag("crucible_clear_button")
                        ) {
                            Text("CLEAR", fontSize = 10.sp, color = Color(0xFFFFA0A0))
                        }

                        val canTransmute = matchedRecipe != null && ((permanentRanks[matchedRecipe!!.buffType] ?: 0) < matchedRecipe!!.buffType.maxRank)

                        Button(
                            onClick = {
                                val transmuted = alchemyViewModel.transmutePermanentBuff()
                                if (transmuted != null) {
                                    audio.play("victory")
                                    haptics?.onBuffActivated()
                                    onBuffTransmuted(transmuted)
                                }
                            },
                            enabled = canTransmute,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF7B1FA2),
                                disabledContainerColor = Color(0x33442A5C)
                            ),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .weight(0.6f)
                                .height(34.dp)
                                .testTag("transmute_button")
                        ) {
                            Text(
                                text = "⚡ TRANSMUTE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (canTransmute) Color(0xFFFFD700) else Color(0x66FFFFFF)
                            )
                        }
                    }
                }

                // 3. RIGHT PANEL: PERMANENT AUGMENTATIONS GRIMOIRE
                Column(
                    modifier = Modifier
                        .weight(1.35f)
                        .fillMaxHeight()
                        .background(Color(0x99180E24), RoundedCornerShape(10.dp))
                        .border(1.dp, Color(0x338F77AA), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "PERMANENT AUGMENTATIONS",
                        color = Color(0xFFFFD700),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Enduring stats bonded to the alchemist",
                        color = Color(0xFF9E8DB8),
                        fontSize = 9.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (recipe in PermanentAlchemyRecipe.ALL_PERMANENT_RECIPES) {
                            val buff = recipe.buffType
                            val rank = permanentRanks[buff] ?: 0
                            val isMax = rank >= buff.maxRank

                            // Auto-fill availability
                            val canAutoFill = !isMax && (pouch[ResourceType.SULFUR_ASH] ?: 0) >= recipe.requiredSulfur &&
                                    (pouch[ResourceType.QUICKSILVER] ?: 0) >= recipe.requiredMercury &&
                                    (pouch[ResourceType.VITRIOL_CRYSTAL] ?: 0) >= recipe.requiredVitriol

                            PermanentBuffCard(
                                recipe = recipe,
                                currentRank = rank,
                                isMax = isMax,
                                canAutoFill = canAutoFill,
                                onAutoFill = {
                                    alchemyViewModel.autoFillRecipe(recipe)
                                    audio.play("throw")
                                    haptics?.onResourceCollected()
                                }
                            )
                        }
                    }
                }
            }
        }

        // --- FLOATING DRAGGED GHOST ITEM (Follows finger) ---
        if (isDragging && draggedResource != null) {
            val res = draggedResource!!
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (dragPosition.x - 28.dp.toPx()).roundToInt(),
                            (dragPosition.y - 28.dp.toPx()).roundToInt()
                        )
                    }
                    .size(56.dp)
                    .zIndex(100f)
                    .scale(1.2f)
                    .background(Color(0xEE2A1744), CircleShape)
                    .border(2.dp, res.color, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(res.symbol, fontSize = 24.sp, color = res.color)
            }
        }
    }
}

/**
 * Draggable Reagent item card on the shelf.
 */
@Composable
private fun DraggableReagentCard(
    resource: ResourceType,
    availableCount: Int,
    isEnabled: Boolean,
    onDragStarted: (Offset) -> Unit,
    onDragDelta: (Offset) -> Unit,
    onDragEnded: () -> Unit,
    onCardTapped: () -> Unit
) {
    var itemRootBounds by remember { mutableStateOf<Rect?>(null) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                itemRootBounds = coordinates.boundsInRoot()
            }
            .pointerInput(resource, isEnabled) {
                if (isEnabled) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val root = itemRootBounds
                            val startPoint = if (root != null) {
                                Offset(root.left + offset.x, root.top + offset.y)
                            } else {
                                offset
                            }
                            onDragStarted(startPoint)
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            onDragDelta(dragAmount)
                        },
                        onDragEnd = { onDragEnded() },
                        onDragCancel = { onDragEnded() }
                    )
                }
            }
            .clickable(enabled = isEnabled) { onCardTapped() }
            .background(
                if (isEnabled) Color(0x992B1944) else Color(0x441F1430),
                RoundedCornerShape(8.dp)
            )
            .border(
                1.dp,
                if (isEnabled) resource.color.copy(alpha = 0.7f) else Color(0x228F77AA),
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 10.dp, vertical = 7.dp)
            .testTag("reagent_card_${resource.name.lowercase()}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(Color(0x66120822), CircleShape)
                    .border(1.dp, resource.color, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(resource.symbol, color = resource.color, fontSize = 16.sp)
            }
            Column {
                Text(
                    text = resource.displayName,
                    color = if (isEnabled) Color.White else Color(0x66FFFFFF),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isEnabled) "Drag to Crucible" else "Depleted / Full",
                    color = if (isEnabled) Color(0xFFC0AEE0) else Color(0x44FFFFFF),
                    fontSize = 8.sp
                )
            }
        }

        Box(
            modifier = Modifier
                .background(resource.color.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                .border(1.dp, resource.color.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = "x$availableCount",
                color = resource.color,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

/**
 * Visual drop receptacle slot in the Crucible.
 */
@Composable
private fun CrucibleSlotBox(
    slotIndex: Int,
    resource: ResourceType?,
    onRemove: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .background(
                if (resource != null) Color(0xDD2A1448) else Color(0x55170B28),
                RoundedCornerShape(8.dp)
            )
            .border(
                1.5.dp,
                resource?.color ?: Color(0x44D4AF37),
                RoundedCornerShape(8.dp)
            )
            .clickable(enabled = resource != null) { onRemove() }
            .testTag("crucible_slot_$slotIndex"),
        contentAlignment = Alignment.Center
    ) {
        if (resource != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(resource.symbol, color = resource.color, fontSize = 18.sp)
                Text("✕", color = Color(0xFFAAAAAA), fontSize = 8.sp)
            }
        } else {
            Text(
                text = "${slotIndex + 1}",
                color = Color(0x33D4AF37),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Permanent buff description card in the Grimoire panel.
 */
@Composable
private fun PermanentBuffCard(
    recipe: PermanentAlchemyRecipe,
    currentRank: Int,
    isMax: Boolean,
    canAutoFill: Boolean,
    onAutoFill: () -> Unit
) {
    val buff = recipe.buffType

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0x88241538), RoundedCornerShape(8.dp))
            .border(1.dp, if (currentRank > 0) buff.color.copy(alpha = 0.6f) else Color(0x228F77AA), RoundedCornerShape(8.dp))
            .padding(8.dp)
            .testTag("permanent_buff_${buff.id}")
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(buff.icon, fontSize = 13.sp)
                    Text(
                        text = buff.title,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Rank badge
                Text(
                    text = if (isMax) "MAX" else "Rank $currentRank/${buff.maxRank}",
                    color = if (isMax) Color(0xFFFFD700) else buff.color,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(2.dp))
            Text(buff.statLabel, color = buff.color, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)

            Spacer(modifier = Modifier.height(4.dp))

            // Recipe requirement badges & Auto-fill button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (recipe.requiredSulfur > 0) {
                        Text("🜍x${recipe.requiredSulfur}", color = Color(0xFFFFB039), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    }
                    if (recipe.requiredMercury > 0) {
                        Text("☿x${recipe.requiredMercury}", color = Color(0xFF64D8CB), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    }
                    if (recipe.requiredVitriol > 0) {
                        Text("🜊x${recipe.requiredVitriol}", color = Color(0xFFB388FF), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    }
                }

                if (!isMax) {
                    Button(
                        onClick = onAutoFill,
                        enabled = canAutoFill,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF5E35B1),
                            disabledContainerColor = Color(0x225E35B1)
                        ),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        modifier = Modifier
                            .height(22.dp)
                            .testTag("autofill_${buff.id}")
                    ) {
                        Text(
                            text = if (canAutoFill) "FILL ➔" else "NEED RES",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (canAutoFill) Color.White else Color(0x44FFFFFF)
                        )
                    }
                }
            }
        }
    }
}
