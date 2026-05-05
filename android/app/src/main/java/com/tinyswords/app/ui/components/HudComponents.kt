package com.tinyswords.app.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tinyswords.app.game.*
import com.tinyswords.app.game.entities.*
import com.tinyswords.app.ui.theme.GameColors
import com.tinyswords.app.ui.theme.GameTypography

// ── Asset Icon ──
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

// ── Compact horizontal resource pill ──
@Composable
private fun CompactResourcePill(value: String, color: Color, assetPath: String, iconSize: Int = 18) {
    Row(
        modifier = Modifier.padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        AssetIcon(
            assetPath = assetPath,
            fallbackColor = color,
            modifier = Modifier.size(iconSize.dp)
        )
        Text(
            text = value,
            style = GameTypography.Body.copy(fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp),
        )
    }
}

// ── Top-center compact resource bar ──
@Composable
fun ResourceBar(
    wood: Int, gold: Int, food: Int, popUsed: Int, popCap: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(GameColors.Panel.copy(alpha = 0.86f), RoundedCornerShape(6.dp))
            .border(1.dp, GameColors.PanelBorder, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        CompactResourcePill(wood.toString(), GameColors.WoodColor, "Tiny Swords (Free Pack)/Terrain/Resources/Wood/Wood Resource/Wood Resource.png", 18)
        // Gold icon was rendered visibly smaller because the source asset has more
        // padding; bump the rendered size so it visually matches wood/food.
        CompactResourcePill(gold.toString(), GameColors.GoldColor, "Tiny Swords (Free Pack)/Terrain/Resources/Gold/Gold Resource/Gold_Resource.png", 22)
        CompactResourcePill(food.toString(), GameColors.FoodColor, "Tiny Swords (Free Pack)/Terrain/Resources/Meat/Meat Resource/Meat Resource.png", 18)
        CompactResourcePill("$popUsed/$popCap", GameColors.PopColor, "Tiny Swords (Free Pack)/Buildings/Blue Buildings/House1.png", 18)
    }
}

// ── Minimal icon-only command button (for bottom dock) ──
@Composable
fun MinimalIconButton(
    iconPath: String,
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
            .size(46.dp)
            .background(bg.copy(alpha = 0.92f), RoundedCornerShape(6.dp))
            .border(1.dp, if (isActive) GameColors.TextGold else GameColors.ButtonBorder, RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        AssetIcon(iconPath, GameColors.TextGold, Modifier.size(32.dp))
    }
}

// ── HP Bar ──
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
            .height(6.dp)
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

// ── Minimal selection panel (bottom-left) ──
@Composable
fun SelectionPanel(
    selected: List<GameEntity>,
    modifier: Modifier = Modifier
) {
    if (selected.isEmpty()) return

    Row(
        modifier = modifier
            .background(GameColors.Panel.copy(alpha = 0.88f), RoundedCornerShape(6.dp))
            .border(1.dp, GameColors.PanelBorder, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val first = selected.first()
        when {
            selected.size > 1 -> {
                val units = selected.filterIsInstance<GameUnit>()
                val firstUnit = units.firstOrNull()
                if (firstUnit != null) {
                    AssetIcon(unitIconPath(firstUnit.type), GameColors.TextGold, Modifier.size(28.dp))
                }
                Text(
                    text = "${units.size}",
                    style = GameTypography.Heading.copy(fontSize = 14.sp, color = Color.White)
                )
            }
            first is GameUnit -> {
                val def = UNITS[first.type] ?: return
                AssetIcon(unitIconPath(first.type), GameColors.TextGold, Modifier.size(28.dp))
                Column {
                    Text(
                        text = def.label,
                        style = GameTypography.Heading.copy(fontSize = 11.sp)
                    )
                    HpBar(first.hp, first.maxHp, width = 56)
                }
            }
            first is GameBuilding -> {
                val def = BUILDINGS[first.type] ?: return
                val maxLevel = buildingUpgradeMaxLevel(first.type)
                val title = if (maxLevel > 1) "${def.label} Lv.${first.level}/${maxLevel}" else def.label
                AssetIcon(buildingIconPath(first.type), GameColors.TextGold, Modifier.size(28.dp))
                Column {
                    Text(
                        text = title,
                        style = GameTypography.Heading.copy(fontSize = 11.sp)
                    )
                    if (first.buildProgress < 1f) {
                        HpBar((first.buildProgress * 100).toInt(), 100, width = 56)
                    } else {
                        HpBar(first.hp, first.maxHp, width = 56)
                    }
                }
            }
            first is GameResource -> {
                val typeName = when (first.type) {
                    ResourceType.TREE -> "Forest"
                    ResourceType.GOLD -> "Gold"
                    ResourceType.FOOD -> if (first.isAnimal) {
                        first.animalKind.replaceFirstChar { it.uppercase() }
                    } else "Food"
                }
                Text(
                    text = "$typeName: ${first.amount.toInt()}",
                    style = GameTypography.Small.copy(fontSize = 11.sp, color = Color.White)
                )
            }
        }
    }
}

// ── Generic small command button (used for pause overlay etc.) ──
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

// ── Bottom-center horizontal action dock (icon-only, minimal) ──
@Composable
fun ActionDock(
    selected: List<GameEntity>,
    onAttackMove: () -> Unit,
    onStop: () -> Unit,
    onHold: () -> Unit,
    onBuildMenu: () -> Unit,
    onTrain: (String) -> Unit,
    onUpgrade: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasUnits = selected.any { it is GameUnit && it.faction == 0 }
    val hasBuilding = selected.firstOrNull() is GameBuilding && (selected.first() as GameBuilding).faction == 0
    val hasWorkers = selected.any { it is GameUnit && it.type == "worker" && it.faction == 0 }

    Row(
        modifier = modifier
            .background(GameColors.Panel.copy(alpha = 0.86f), RoundedCornerShape(7.dp))
            .border(1.dp, GameColors.PanelBorder, RoundedCornerShape(7.dp))
            .padding(5.dp)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (hasUnits) {
            // Build (workers only) — hammer icon
            if (hasWorkers) {
                MinimalIconButton(
                    iconPath = "Tiny Swords (Free Pack)/UI Elements/UI Elements/Icons/Icon_08.png",
                    onClick = onBuildMenu
                )
            }
            // Attack Move — swords
            MinimalIconButton(
                iconPath = "Tiny Swords (Free Pack)/UI Elements/UI Elements/Swords/Swords.png",
                onClick = onAttackMove
            )
            // Stop — red button
            MinimalIconButton(
                iconPath = "Tiny Swords (Free Pack)/UI Elements/UI Elements/Buttons/TinyRoundRedButton.png",
                onClick = onStop
            )
            // Hold — ribbon/flag icon (matches web version's iconRally)
            MinimalIconButton(
                iconPath = "Tiny Swords (Free Pack)/UI Elements/UI Elements/Ribbons/SmallRibbons.png",
                onClick = onHold
            )
        }

        if (hasBuilding) {
            val building = selected.first() as GameBuilding
            val bdef = BUILDINGS[building.type]
            if (upgradeCostFor(building) != null) {
                MinimalIconButton(
                    iconPath = "Tiny Swords (Free Pack)/UI Elements/UI Elements/Icons/Icon_10.png",
                    onClick = onUpgrade
                )
            }
            if (bdef != null && bdef.trains.isNotEmpty()) {
                for (unitType in bdef.trains) {
                    UNITS[unitType] ?: continue
                    MinimalIconButton(
                        iconPath = unitIconPath(unitType),
                        onClick = { onTrain(unitType) }
                    )
                }
            }
        }
    }
}

// ── Horizontal scrollable build menu (sits above the ActionDock) ──
@Composable
fun BuildMenu(
    faction: FactionState,
    onBuild: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(GameColors.Panel.copy(alpha = 0.90f), RoundedCornerShape(7.dp))
            .border(1.dp, GameColors.PanelBorder, RoundedCornerShape(7.dp))
            .padding(5.dp)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for ((type, bdef) in BUILDINGS) {
            if (type == "castle") continue
            val canAfford = faction.canAfford(bdef.costWood, bdef.costGold, bdef.costFood)
            MinimalIconButton(
                iconPath = buildingIconPath(type),
                onClick = { onBuild(type) },
                enabled = canAfford
            )
        }
        MinimalIconButton(
            iconPath = "Tiny Swords (Free Pack)/UI Elements/UI Elements/Buttons/TinyRoundRedButton.png",
            onClick = onClose
        )
    }
}

internal fun unitIconPath(type: String): String = when (type) {
    "worker" -> "Tiny Swords (Free Pack)/Units/Blue Units/Pawn/Pawn_Idle.png"
    "warrior" -> "Tiny Swords (Free Pack)/Units/Blue Units/Warrior/Warrior_Idle.png"
    "archer" -> "Tiny Swords (Free Pack)/Units/Blue Units/Archer/Archer_Idle.png"
    "lancer" -> "Tiny Swords (Free Pack)/Units/Blue Units/Lancer/Lancer_Idle.png"
    "monk" -> "Tiny Swords (Free Pack)/Units/Blue Units/Monk/Idle.png"
    else -> "Tiny Swords (Free Pack)/UI Elements/UI Elements/Icons/Icon_01.png"
}

internal fun buildingIconPath(type: String): String = when (type) {
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
