package com.tinyswords.app.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
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
fun AssetIcon(assetPath: String, fallbackColor: Color, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap = remember(assetPath) {
        try {
            context.assets.open(assetPath).use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
        } catch (_: Throwable) {
            null
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = modifier,
            filterQuality = FilterQuality.None
        )
    } else {
        Box(modifier = modifier.background(fallbackColor, RoundedCornerShape(2.dp)))
    }
}

@Composable
fun ResourcePill(value: String, color: Color, assetPath: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .widthIn(min = 70.dp)
            .background(GameColors.Panel.copy(alpha = 0.88f), RoundedCornerShape(5.dp))
            .border(1.dp, GameColors.PanelBorder, RoundedCornerShape(5.dp))
            .padding(horizontal = 5.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        AssetIcon(
            assetPath = assetPath,
            fallbackColor = color,
            modifier = Modifier.size(17.dp)
        )
        Text(
            text = value,
            style = GameTypography.Body.copy(fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp),
        )
    }
}

// ── Top-right Resource Stack ──
@Composable
fun ResourceBar(
    wood: Int, gold: Int, food: Int, popUsed: Int, popCap: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ResourcePill(wood.toString(), GameColors.WoodColor, "Tiny Swords (Free Pack)/Terrain/Resources/Wood/Wood Resource/Wood Resource.png")
        ResourcePill(gold.toString(), GameColors.GoldColor, "Tiny Swords (Free Pack)/Terrain/Resources/Gold/Gold Resource/Gold_Resource.png")
        ResourcePill(food.toString(), GameColors.FoodColor, "Tiny Swords (Free Pack)/Terrain/Resources/Meat/Meat Resource/Meat Resource.png")
        ResourcePill("$popUsed/$popCap", GameColors.PopColor, "Tiny Swords (Free Pack)/Buildings/Blue Buildings/House1.png")
    }
}

@Composable
fun IconCommandButton(
    text: String,
    iconPath: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isActive: Boolean = false
) {
    val bg = when {
        !enabled -> GameColors.ButtonDisabled
        isActive -> GameColors.ButtonPressed
        else -> GameColors.ButtonNormal
    }
    Box(
        modifier = modifier
            .width(44.dp)
            .height(44.dp)
            .background(bg.copy(alpha = 0.94f), RoundedCornerShape(5.dp))
            .border(1.dp, if (isActive) GameColors.TextGold else GameColors.ButtonBorder, RoundedCornerShape(5.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        if (iconPath != null) {
            AssetIcon(iconPath, GameColors.TextGold, Modifier.size(32.dp))
        }
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
            .width(160.dp)
            .background(GameColors.Panel.copy(alpha = 0.88f), RoundedCornerShape(8.dp))
            .border(1.dp, GameColors.PanelBorder, RoundedCornerShape(8.dp))
            .padding(6.dp)
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
                    ResourceType.FOOD -> if (first.isAnimal) {
                        when (first.animalKind) { "grouse" -> "Black Grouse" else -> first.animalKind.replaceFirstChar { it.uppercase() } }
                    } else "Food"
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
            .height(34.dp)
            .widthIn(min = 46.dp)
            .background(bg, RoundedCornerShape(4.dp))
            .border(1.dp, GameColors.ButtonBorder, RoundedCornerShape(4.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = GameTypography.Button.copy(
                color = if (enabled) GameColors.TextPrimary else GameColors.TextSecondary,
                fontSize = 11.sp
            )
        )
    }
}

// ── Action Dock ──
@Composable
fun ActionDock(
    selected: List<GameEntity>,
    onMove: () -> Unit,
    onAttackMove: () -> Unit,
    onStop: () -> Unit,
    onHold: () -> Unit,
    onBuildMenu: () -> Unit,
    onTrain: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val hasUnits = selected.any { it is GameUnit && it.faction == 0 }
    val hasBuilding = selected.firstOrNull() is GameBuilding && (selected.first() as GameBuilding).faction == 0
    val hasWorkers = selected.any { it is GameUnit && it.type == "worker" && it.faction == 0 }

    Row(
        modifier = modifier
            .fillMaxWidth(0.9f)
            .heightIn(max = 60.dp)
            .horizontalScroll(rememberScrollState())
            .background(GameColors.Panel.copy(alpha = 0.84f), RoundedCornerShape(8.dp))
            .border(1.dp, GameColors.PanelBorder, RoundedCornerShape(8.dp))
            .padding(7.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (hasUnits) {
            IconCommandButton("Attack Move", "Tiny Swords (Free Pack)/UI Elements/UI Elements/Swords/Swords.png", onAttackMove)
            IconCommandButton("Stop", "Tiny Swords (Free Pack)/UI Elements/UI Elements/Buttons/TinyRoundRedButton.png", onStop)
            IconCommandButton("Hold", "Tiny Swords (Free Pack)/UI Elements/UI Elements/Icons/Icon_05.png", onHold)

            if (hasWorkers) {
                IconCommandButton("Build", "Tiny Swords (Free Pack)/UI Elements/UI Elements/Icons/Icon_08.png", onBuildMenu)
            }
        }

        if (hasBuilding) {
            val building = selected.first() as GameBuilding
            val bdef = BUILDINGS[building.type]
            if (bdef != null && bdef.trains.isNotEmpty()) {
                for (unitType in bdef.trains) {
                    val udef = UNITS[unitType] ?: continue
                    IconCommandButton(
                        text = udef.label,
                        iconPath = unitIconPath(unitType),
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
    Row(
        modifier = modifier
            .fillMaxWidth(0.9f)
            .heightIn(max = 60.dp)
            .horizontalScroll(rememberScrollState())
            .background(GameColors.Panel.copy(alpha = 0.90f), RoundedCornerShape(8.dp))
            .border(1.dp, GameColors.PanelBorder, RoundedCornerShape(8.dp))
            .padding(7.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for ((type, bdef) in BUILDINGS) {
            if (type == "castle") continue
            val canAfford = faction.canAfford(bdef.costWood, bdef.costGold, bdef.costFood)
            IconCommandButton(
                text = bdef.label,
                iconPath = buildingIconPath(type),
                onClick = { onBuild(type) },
                enabled = canAfford
            )
        }
    }
}

private fun unitIconPath(type: String): String = when (type) {
    "worker" -> "Tiny Swords (Free Pack)/Units/Blue Units/Pawn/Pawn_Idle.png"
    "warrior" -> "Tiny Swords (Free Pack)/Units/Blue Units/Warrior/Warrior_Idle.png"
    "archer" -> "Tiny Swords (Free Pack)/Units/Blue Units/Archer/Archer_Idle.png"
    "lancer" -> "Tiny Swords (Free Pack)/Units/Blue Units/Lancer/Lancer_Idle.png"
    "monk" -> "Tiny Swords (Free Pack)/Units/Blue Units/Monk/Idle.png"
    else -> "Tiny Swords (Free Pack)/UI Elements/UI Elements/Icons/Icon_01.png"
}

private fun buildingIconPath(type: String): String = when (type) {
    "house" -> "Tiny Swords (Free Pack)/Buildings/Blue Buildings/House1.png"
    "barracks" -> "Tiny Swords (Free Pack)/Buildings/Blue Buildings/Barracks.png"
    "archery" -> "Tiny Swords (Free Pack)/Buildings/Blue Buildings/Archery.png"
    "tower" -> "Tiny Swords (Free Pack)/Buildings/Blue Buildings/Tower.png"
    "monastery" -> "Tiny Swords (Free Pack)/Buildings/Blue Buildings/Monastery.png"
    else -> "Tiny Swords (Free Pack)/Buildings/Blue Buildings/Castle.png"
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
