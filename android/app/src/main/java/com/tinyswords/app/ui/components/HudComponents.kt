package com.tinyswords.app.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tinyswords.app.game.*
import com.tinyswords.app.game.entities.*
import com.tinyswords.app.ui.theme.GameColors
import com.tinyswords.app.ui.theme.GameTypography

// ── Resource Pill ──
@Composable
fun ResourcePill(label: String, value: Int, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(GameColors.Panel, RoundedCornerShape(6.dp))
            .border(1.dp, GameColors.PanelBorder, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, RoundedCornerShape(2.dp))
        )
        Text(
            text = "$value",
            style = GameTypography.Body.copy(fontWeight = FontWeight.Bold, color = Color.White),
        )
        Text(
            text = label,
            style = GameTypography.Small.copy(color = GameColors.TextSecondary, fontSize = 8.sp),
        )
    }
}

// ── Top Resource Bar ──
@Composable
fun ResourceBar(
    wood: Int, gold: Int, food: Int, popUsed: Int, popCap: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ResourcePill("WOOD", wood, GameColors.WoodColor)
        ResourcePill("GOLD", gold, GameColors.GoldColor)
        ResourcePill("FOOD", food, GameColors.FoodColor)
        ResourcePill("$popUsed/$popCap", popUsed, GameColors.PopColor)
    }
}

// ── HP Bar Component ──
@Composable
fun HpBar(current: Int, max: Int, width: Int = 80, modifier: Modifier = Modifier) {
    val pct = if (max > 0) current.toFloat() / max else 0f
    val color = when {
        pct > 0.6f -> GameColors.HpGreen
        pct > 0.3f -> GameColors.HpYellow
        else -> GameColors.HpRed
    }

    Box(
        modifier = modifier
            .width(width.dp)
            .height(8.dp)
            .background(Color(0xFF1a1a1a), RoundedCornerShape(2.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(pct)
                .background(color, RoundedCornerShape(2.dp))
        )
    }
}

// ── Selection Panel ──
@Composable
fun SelectionPanel(
    selected: List<GameEntity>,
    modifier: Modifier = Modifier
) {
    if (selected.isEmpty()) return

    Column(
        modifier = modifier
            .width(200.dp)
            .background(GameColors.Panel, RoundedCornerShape(8.dp))
            .border(1.dp, GameColors.PanelBorder, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        val first = selected.first()

        when {
            selected.size > 1 -> {
                // Multi-select
                val units = selected.filterIsInstance<GameUnit>()
                Text(
                    text = "${units.size} Units Selected",
                    style = GameTypography.Heading.copy(fontSize = 13.sp)
                )
                Spacer(modifier = Modifier.height(4.dp))

                // Unit composition
                val types = units.groupBy { it.type }
                for ((type, group) in types) {
                    val def = UNITS[type] ?: continue
                    Text(
                        text = "${group.size}x ${def.label}",
                        style = GameTypography.Small
                    )
                }
            }
            first is GameUnit -> {
                val def = UNITS[first.type] ?: return
                Text(
                    text = def.label,
                    style = GameTypography.Heading.copy(fontSize = 13.sp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                HpBar(first.hp, first.maxHp)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "HP: ${first.hp}/${first.maxHp}",
                    style = GameTypography.Small
                )
                if (first.type == "worker" && first.carrying != null) {
                    Text(
                        text = "Carrying: ${first.carrying} (${first.carryAmount.toInt()})",
                        style = GameTypography.Small.copy(color = GameColors.TextGold)
                    )
                }
                Text(
                    text = "DMG: ${kotlin.math.abs(def.damage)} | RNG: ${def.range.toInt()} | SPD: ${def.speed.toInt()}",
                    style = GameTypography.Small
                )
            }
            first is GameBuilding -> {
                val def = BUILDINGS[first.type] ?: return
                Text(
                    text = def.label,
                    style = GameTypography.Heading.copy(fontSize = 13.sp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                HpBar(first.hp, first.maxHp)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "HP: ${first.hp}/${first.maxHp}",
                    style = GameTypography.Small
                )
                if (first.buildProgress < 1f) {
                    Text(
                        text = "Building: ${(first.buildProgress * 100).toInt()}%",
                        style = GameTypography.Small.copy(color = GameColors.AccentBlue)
                    )
                }
                if (first.queue.isNotEmpty()) {
                    val slot = first.queue[0]
                    Text(
                        text = "Training: ${UNITS[slot.unitType]?.label ?: slot.unitType} (${(slot.progress * 100).toInt()}%)",
                        style = GameTypography.Small.copy(color = GameColors.AccentGreen)
                    )
                    if (first.queue.size > 1) {
                        Text(
                            text = "+${first.queue.size - 1} in queue",
                            style = GameTypography.Small
                        )
                    }
                }
                if (def.pop > 0) {
                    Text(
                        text = "Pop: +${def.pop}",
                        style = GameTypography.Small.copy(color = GameColors.PopColor)
                    )
                }
            }
            first is GameResource -> {
                val typeName = when (first.type) {
                    ResourceType.TREE -> "Forest"
                    ResourceType.GOLD -> "Gold Deposit"
                    ResourceType.FOOD -> if (first.isAnimal) first.animalKind.replaceFirstChar { it.uppercase() } else "Food"
                }
                Text(
                    text = typeName,
                    style = GameTypography.Heading.copy(fontSize = 13.sp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (first.isAnimal && first.animalHp > 0) {
                    HpBar(first.animalHp.toInt(), first.animalMaxHp.toInt())
                    Spacer(modifier = Modifier.height(2.dp))
                }
                Text(
                    text = "Remaining: ${first.amount.toInt()}",
                    style = GameTypography.Small
                )
            }
        }
    }
}

// ── Game Command Button ──
@Composable
fun CommandButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    isActive: Boolean = false,
    modifier: Modifier = Modifier
) {
    val bg = when {
        !enabled -> GameColors.ButtonDisabled
        isActive -> GameColors.ButtonPressed
        else -> GameColors.ButtonNormal
    }

    Box(
        modifier = modifier
            .height(48.dp)
            .widthIn(min = 48.dp)
            .background(bg, RoundedCornerShape(4.dp))
            .border(1.dp, GameColors.ButtonBorder, RoundedCornerShape(4.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = GameTypography.Button.copy(
                color = if (enabled) GameColors.TextPrimary else GameColors.TextSecondary
            )
        )
    }
}

// ── Action Dock ──
@Composable
fun ActionDock(
    selected: List<GameEntity>,
    formationMode: String,
    onMove: () -> Unit,
    onAttackMove: () -> Unit,
    onStop: () -> Unit,
    onHold: () -> Unit,
    onFormation: (String) -> Unit,
    onBuildMenu: () -> Unit,
    onTrain: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val hasUnits = selected.any { it is GameUnit && it.faction == 0 }
    val hasBuilding = selected.firstOrNull() is GameBuilding && (selected.first() as GameBuilding).faction == 0
    val hasWorkers = selected.any { it is GameUnit && it.type == "worker" && it.faction == 0 }

    FlowRow(
        modifier = modifier
            .background(GameColors.Panel, RoundedCornerShape(8.dp))
            .border(1.dp, GameColors.PanelBorder, RoundedCornerShape(8.dp))
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (hasUnits) {
            CommandButton("ATK", onClick = onAttackMove)
            CommandButton("STOP", onClick = onStop)
            CommandButton("HOLD", onClick = onHold)

            Spacer(modifier = Modifier.width(4.dp))

            // Formation buttons
            CommandButton("LINE", onClick = { onFormation("line") }, isActive = formationMode == "line")
            CommandButton("BOX", onClick = { onFormation("box") }, isActive = formationMode == "box")
            CommandButton("V", onClick = { onFormation("wedge") }, isActive = formationMode == "wedge")
            CommandButton("SPLIT", onClick = { onFormation("split") }, isActive = formationMode == "split")

            if (hasWorkers) {
                Spacer(modifier = Modifier.width(4.dp))
                CommandButton("BUILD", onClick = onBuildMenu)
            }
        }

        if (hasBuilding) {
            val building = selected.first() as GameBuilding
            val bdef = BUILDINGS[building.type]
            if (bdef != null) {
                for ((idx, unitType) in bdef.trains.withIndex()) {
                    val udef = UNITS[unitType] ?: continue
                    CommandButton(
                        text = "${udef.label}\n${udef.costGold}G",
                        onClick = { onTrain(unitType) }
                    )
                }
            }
        }
    }
}

// ── Build Menu ──
@Composable
fun BuildMenu(
    faction: FactionState,
    onBuild: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(GameColors.Panel, RoundedCornerShape(8.dp))
            .border(1.dp, GameColors.PanelBorder, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Build",
                style = GameTypography.Heading
            )
            CommandButton("X", onClick = onClose)
        }
        Spacer(modifier = Modifier.height(6.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            for ((type, bdef) in BUILDINGS) {
                if (type == "castle") continue // Can't build castles
                val canAfford = faction.canAfford(bdef.costWood, bdef.costGold, bdef.costFood)

                Column(
                    modifier = Modifier
                        .width(80.dp)
                        .background(
                            if (canAfford) GameColors.ButtonNormal else GameColors.ButtonDisabled,
                            RoundedCornerShape(4.dp)
                        )
                        .border(1.dp, GameColors.ButtonBorder, RoundedCornerShape(4.dp))
                        .clickable(enabled = canAfford) { onBuild(type) }
                        .padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = bdef.label,
                        style = GameTypography.Button.copy(fontSize = 10.sp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "W${bdef.costWood}",
                        style = GameTypography.Small.copy(color = GameColors.WoodColor, fontSize = 8.sp)
                    )
                    Text(
                        text = "G${bdef.costGold}",
                        style = GameTypography.Small.copy(color = GameColors.GoldColor, fontSize = 8.sp)
                    )
                }
            }
        }
    }
}

// ── Pause Overlay ──
@Composable
fun PauseOverlay(
    onResume: () -> Unit,
    onSaveAndExit: () -> Unit,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xAA000000)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(280.dp)
                .background(GameColors.Panel, RoundedCornerShape(12.dp))
                .border(2.dp, GameColors.PanelBorder, RoundedCornerShape(12.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "PAUSED",
                style = GameTypography.Title
            )

            CommandButton(
                text = "RESUME",
                onClick = onResume,
                modifier = Modifier.fillMaxWidth()
            )

            CommandButton(
                text = "SAVE & EXIT",
                onClick = onSaveAndExit,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ── Game Over Overlay ──
@Composable
fun GameOverOverlay(
    winner: Int,
    isPlayerWin: Boolean,
    onNewGame: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xCC000000)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(300.dp)
                .background(GameColors.Panel, RoundedCornerShape(12.dp))
                .border(2.dp, GameColors.PanelBorder, RoundedCornerShape(12.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (isPlayerWin) "VICTORY!" else "DEFEAT",
                style = GameTypography.Title.copy(
                    fontSize = 32.sp,
                    color = if (isPlayerWin) GameColors.TextGold else GameColors.HpRed
                )
            )

            val factionName = FACTIONS.getOrNull(winner)?.name ?: "Unknown"
            Text(
                text = if (isPlayerWin) "The $factionName reigns supreme!" else "The $factionName has conquered your realm.",
                style = GameTypography.Body,
            )

            CommandButton(
                text = "NEW GAME",
                onClick = onNewGame,
                modifier = Modifier.fillMaxWidth()
            )
            CommandButton(
                text = "EXIT",
                onClick = onExit,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
