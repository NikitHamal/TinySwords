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
            .background(GameColors.Panel.copy(alpha = 0.88f), RoundedCornerShape(5.dp))
            .border(1.dp, GameColors.PanelBorder, RoundedCornerShape(5.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        AssetIcon(
            assetPath = assetPath,
            fallbackColor = color,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = value,
            style = GameTypography.Body.copy(fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp),
        )
    }
}

// ── Top-center Resource Bar ──
@Composable
fun ResourceBar(
    wood: Int, gold: Int, food: Int, popUsed: Int, popCap: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ResourcePill(wood.toString(), GameColors.WoodColor, "Tiny Swords (Free Pack)/Terrain/Resources/Wood/Wood Resource/Wood Resource.png")
        ResourcePill(gold.toString(), GameColors.GoldColor, "Tiny Swords (Free Pack)/Terrain/Resources/Gold/Gold Resource/Gold_Resource.png")
        ResourcePill(food.toString(), GameColors.FoodColor, "Tiny Swords (Free Pack)/Terrain/Resources/Meat/Meat Resource/Meat Resource.png")
        ResourcePill("$popUsed/$popCap", GameColors.PopColor, "Tiny Swords (Free Pack)/Buildings/Blue Buildings/House1.png")
    }
}

// ── Icon-only command chip ──
@Composable
fun IconCommandChip(
    iconPath: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val bg = if (enabled) GameColors.ButtonNormal else GameColors.ButtonDisabled
    Box(
        modifier = modifier
            .size(42.dp)
            .background(bg.copy(alpha = 0.94f), RoundedCornerShape(5.dp))
            .border(1.dp, GameColors.ButtonBorder, RoundedCornerShape(5.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        AssetIcon(iconPath, GameColors.TextGold, Modifier.size(26.dp))
    }
}

// ── Quick bottom control bar ──
@Composable
fun QuickControlBar(
    onWorkers: () -> Unit,
    onArmy: () -> Unit,
    onAll: () -> Unit,
    onHome: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .background(GameColors.Panel.copy(alpha = 0.78f), RoundedCornerShape(7.dp))
            .border(1.dp, GameColors.PanelBorder, RoundedCornerShape(7.dp))
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconCommandChip("Tiny Swords (Free Pack)/Units/Blue Units/Pawn/Pawn_Idle.png", onWorkers)
        IconCommandChip("Tiny Swords (Free Pack)/Units/Blue Units/Warrior/Warrior_Idle.png", onArmy)
        IconCommandChip("Tiny Swords (Free Pack)/UI Elements/UI Elements/Icons/Icon_01.png", onAll)
        IconCommandChip("Tiny Swords (Free Pack)/Buildings/Blue Buildings/Castle.png", onHome)
        IconCommandChip("Tiny Swords (Free Pack)/UI Elements/UI Elements/Buttons/TinyRoundRedButton.png", onCancel)
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

// ── Minimal Selection Panel ──
@Composable
fun SelectionPanel(
    selected: List<GameEntity>,
    modifier: Modifier = Modifier
) {
    if (selected.isEmpty()) return

    Column(
        modifier = modifier
            .widthIn(min = 120.dp, max = 200.dp)
            .background(GameColors.Panel, RoundedCornerShape(6.dp))
            .border(1.dp, GameColors.PanelBorder, RoundedCornerShape(6.dp))
            .padding(6.dp)
    ) {
        val first = selected.first()

        when {
            selected.size > 1 -> {
                val units = selected.filterIsInstance<GameUnit>()
                Text(
                    text = "${units.size} selected",
                    style = GameTypography.Heading.copy(fontSize = 11.sp)
                )
            }
            first is GameUnit -> {
                val def = UNITS[first.type] ?: return
                Text(
                    text = def.label,
                    style = GameTypography.Heading.copy(fontSize = 11.sp)
                )
                HpBar(first.hp, first.maxHp, width = 60)
            }
            first is GameBuilding -> {
                val def = BUILDINGS[first.type] ?: return
                Text(
                    text = def.label,
                    style = GameTypography.Heading.copy(fontSize = 11.sp)
                )
                HpBar(first.hp, first.maxHp, width = 60)
                if (first.buildProgress < 1f) {
                    Text(
                        text = "Build ${(first.buildProgress * 100).toInt()}%",
                        style = GameTypography.Small.copy(color = GameColors.AccentBlue, fontSize = 9.sp)
                    )
                }
            }
            first is GameResource -> {
                val typeName = when (first.type) {
                    ResourceType.TREE -> "Wood"
                    ResourceType.GOLD -> "Gold"
                    ResourceType.FOOD -> if (first.isAnimal) {
                        when (first.animalKind) { "grouse" -> "Grouse" else -> first.animalKind.replaceFirstChar { it.uppercase() } }
                    } else "Food"
                }
                Text(
                    text = typeName,
                    style = GameTypography.Heading.copy(fontSize = 11.sp)
                )
                if (first.isAnimal && first.animalHp > 0) {
                    HpBar(first.animalHp.toInt(), first.animalMaxHp.toInt(), width = 60)
                }
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

// ── Bottom Action Bar ──
@Composable
fun ActionBar(
    selected: List<GameEntity>,
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
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .background(GameColors.Panel.copy(alpha = 0.84f), RoundedCornerShape(7.dp))
            .border(1.dp, GameColors.PanelBorder, RoundedCornerShape(7.dp))
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (hasUnits) {
            if (hasWorkers) {
                IconCommandChip("Tiny Swords (Free Pack)/UI Elements/UI Elements/Icons/Icon_08.png", onBuildMenu)
            }
            IconCommandChip("Tiny Swords (Free Pack)/UI Elements/UI Elements/Swords/Swords.png", onAttackMove)
            IconCommandChip("Tiny Swords (Free Pack)/UI Elements/UI Elements/Buttons/TinyRoundRedButton.png", onStop)
            IconCommandChip("Tiny Swords (Free Pack)/UI Elements/UI Elements/Icons/Icon_05.png", onHold)
        }

        if (hasBuilding) {
            val building = selected.first() as GameBuilding
            val bdef = BUILDINGS[building.type]
            if (bdef != null && bdef.trains.isNotEmpty()) {
                for (unitType in bdef.trains) {
                    val udef = UNITS[unitType] ?: continue
                    IconCommandChip(unitIconPath(unitType), { onTrain(unitType) })
                }
            }
        }
    }
}

// ── Build Sub-Bar ──
@Composable
fun BuildBar(
    faction: FactionState,
    onBuild: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .background(GameColors.Panel.copy(alpha = 0.90f), RoundedCornerShape(7.dp))
            .border(1.dp, GameColors.PanelBorder, RoundedCornerShape(7.dp))
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for ((type, bdef) in BUILDINGS) {
            if (type == "castle") continue
            val canAfford = faction.canAfford(bdef.costWood, bdef.costGold, bdef.costFood)
            IconCommandChip(buildingIconPath(type), { onBuild(type) }, enabled = canAfford)
        }
        IconCommandChip("Tiny Swords (Free Pack)/UI Elements/UI Elements/Buttons/TinyRoundRedButton.png", onClose)
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
