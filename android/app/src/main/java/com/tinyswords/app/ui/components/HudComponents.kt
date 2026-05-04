package com.tinyswords.app.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tinyswords.app.game.BUILDINGS
import com.tinyswords.app.game.FACTIONS
import com.tinyswords.app.game.FactionState
import com.tinyswords.app.game.UNITS
import com.tinyswords.app.game.entities.GameBuilding
import com.tinyswords.app.game.entities.GameEntity
import com.tinyswords.app.game.entities.GameResource
import com.tinyswords.app.game.entities.GameUnit
import com.tinyswords.app.game.entities.ResourceType
import com.tinyswords.app.ui.theme.GameColors
import com.tinyswords.app.ui.theme.GameTypography

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
        Image(bitmap = bitmap, contentDescription = null, modifier = modifier, filterQuality = FilterQuality.None)
    } else {
        Box(modifier = modifier.background(fallbackColor, RoundedCornerShape(2.dp)))
    }
}

@Composable
fun ResourceBar(
    wood: Int,
    gold: Int,
    food: Int,
    popUsed: Int,
    popCap: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(GameColors.Panel.copy(alpha = 0.76f), RoundedCornerShape(6.dp))
            .border(1.dp, GameColors.PanelBorder.copy(alpha = 0.86f), RoundedCornerShape(6.dp))
            .padding(horizontal = 5.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ResourcePill(wood.toString(), "Tiny Swords (Free Pack)/Terrain/Resources/Wood/Wood Resource/Wood Resource.png", GameColors.WoodColor)
        ResourcePill(gold.toString(), "Tiny Swords (Free Pack)/Terrain/Resources/Gold/Gold Stones/Gold Stone 1.png", GameColors.GoldColor, iconSize = 22.dp)
        ResourcePill(food.toString(), "Tiny Swords (Free Pack)/Terrain/Resources/Meat/Meat Resource/Meat Resource.png", GameColors.FoodColor)
        ResourcePill("$popUsed/$popCap", "Tiny Swords (Free Pack)/Buildings/Blue Buildings/House1.png", GameColors.PopColor)
    }
}

@Composable
private fun ResourcePill(value: String, assetPath: String, color: Color, iconSize: Dp = 19.dp) {
    Row(
        modifier = Modifier
            .height(26.dp)
            .widthIn(min = 56.dp)
            .background(Color(0x7A1B140E), RoundedCornerShape(4.dp))
            .border(1.dp, GameColors.PanelBorder.copy(alpha = 0.72f), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AssetIcon(assetPath, color, Modifier.size(iconSize))
        Text(value, style = GameTypography.Body.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White))
    }
}

@Composable
fun SelectionPanel(selected: List<GameEntity>, modifier: Modifier = Modifier) {
    if (selected.isEmpty()) return
    val first = selected.first()
    Column(
        modifier = modifier
            .widthIn(min = 118.dp, max = 158.dp)
            .background(GameColors.Panel.copy(alpha = 0.70f), RoundedCornerShape(6.dp))
            .border(1.dp, GameColors.PanelBorder.copy(alpha = 0.78f), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        when {
            selected.size > 1 -> {
                val units = selected.filterIsInstance<GameUnit>()
                Text("${units.size} units", style = GameTypography.Heading.copy(fontSize = 11.sp))
                Text(compactComposition(units), style = GameTypography.Small.copy(fontSize = 8.sp), maxLines = 1)
            }
            first is GameUnit -> {
                val def = UNITS[first.type] ?: return@Column
                Text(def.label, style = GameTypography.Heading.copy(fontSize = 11.sp))
                MiniBar(first.hp.toFloat() / first.maxHp.toFloat(), GameColors.HpGreen)
                val cargo = first.carrying?.let { " ${it}:${first.carryAmount.toInt()}" } ?: ""
                Text("${first.hp}/${first.maxHp}$cargo", style = GameTypography.Small.copy(fontSize = 8.sp), maxLines = 1)
            }
            first is GameBuilding -> {
                val def = BUILDINGS[first.type] ?: return@Column
                Text(def.label, style = GameTypography.Heading.copy(fontSize = 11.sp))
                if (first.buildProgress < 1f) {
                    MiniBar(first.buildProgress, GameColors.AccentBlue)
                    Text("Build ${(first.buildProgress * 100f).toInt()}%", style = GameTypography.Small.copy(fontSize = 8.sp), maxLines = 1)
                } else {
                    MiniBar(first.hp.toFloat() / first.maxHp.toFloat(), GameColors.HpGreen)
                    val queue = if (first.queue.isNotEmpty()) " Q:${first.queue.size}" else ""
                    Text("${first.hp}/${first.maxHp}$queue", style = GameTypography.Small.copy(fontSize = 8.sp), maxLines = 1)
                }
            }
            first is GameResource -> {
                Text(resourceName(first), style = GameTypography.Heading.copy(fontSize = 11.sp), maxLines = 1)
                if (first.isAnimal && first.animalHp > 0f) MiniBar(first.animalHp / first.animalMaxHp, GameColors.HpGreen)
                Text(first.amount.toInt().toString(), style = GameTypography.Small.copy(fontSize = 8.sp), maxLines = 1)
            }
        }
    }
}

@Composable
private fun MiniBar(pct: Float, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .background(Color(0xCC17120E), RoundedCornerShape(2.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(pct.coerceIn(0f, 1f))
                .background(color, RoundedCornerShape(2.dp))
        )
    }
}

@Composable
fun SelectionActionBar(
    selected: List<GameEntity>,
    buildMenuOpen: Boolean,
    onAttackMove: () -> Unit,
    onStop: () -> Unit,
    onBuildMenu: () -> Unit,
    onTrain: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (selected.isEmpty()) return
    val playerUnits = selected.filterIsInstance<GameUnit>().filter { it.faction == 0 }
    val hasWorkers = playerUnits.any { it.type == "worker" }
    val hasMilitary = playerUnits.any { it.type != "worker" }
    val building = selected.firstOrNull() as? GameBuilding
    val trains = if (building != null && building.faction == 0 && building.buildProgress >= 1f) {
        BUILDINGS[building.type]?.trains.orEmpty()
    } else {
        emptyList()
    }
    Row(
        modifier = modifier
            .background(GameColors.Panel.copy(alpha = 0.78f), RoundedCornerShape(7.dp))
            .border(1.dp, GameColors.PanelBorder.copy(alpha = 0.82f), RoundedCornerShape(7.dp))
            .horizontalScroll(rememberScrollState())
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (hasWorkers) {
            HudIconButton("Tiny Swords (Free Pack)/UI Elements/UI Elements/Icons/Icon_08.png", GameColors.TextGold, onBuildMenu, active = buildMenuOpen)
        }
        if (hasMilitary) {
            HudIconButton("Tiny Swords (Free Pack)/UI Elements/UI Elements/Swords/Swords.png", GameColors.HpRed, onAttackMove)
            HudIconButton("Tiny Swords (Free Pack)/UI Elements/UI Elements/Buttons/TinyRoundRedButton.png", GameColors.HpRed, onStop)
        }
        for (unitType in trains) {
            HudIconButton(unitIconPath(unitType), GameColors.TextGold, { onTrain(unitType) })
        }
    }
}

@Composable
fun BuildStrip(
    faction: FactionState,
    onBuild: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(GameColors.Panel.copy(alpha = 0.80f), RoundedCornerShape(7.dp))
            .border(1.dp, GameColors.PanelBorder.copy(alpha = 0.82f), RoundedCornerShape(7.dp))
            .horizontalScroll(rememberScrollState())
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HudIconButton("Tiny Swords (Free Pack)/UI Elements/UI Elements/Buttons/TinyRoundRedButton.png", GameColors.HpRed, onClose)
        for ((type, def) in BUILDINGS) {
            if (type == "castle") continue
            HudIconButton(
                iconPath = buildingIconPath(type),
                fallback = GameColors.TextGold,
                onClick = { onBuild(type) },
                enabled = faction.canAfford(def.costWood, def.costGold, def.costFood)
            )
        }
    }
}

@Composable
private fun HudIconButton(
    iconPath: String,
    fallback: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean = false
) {
    val border = if (active) GameColors.TextGold else GameColors.ButtonBorder.copy(alpha = 0.82f)
    Box(
        modifier = modifier
            .size(42.dp)
            .background(if (active) GameColors.ButtonPressed.copy(alpha = 0.92f) else GameColors.ButtonNormal.copy(alpha = if (enabled) 0.90f else 0.45f), RoundedCornerShape(6.dp))
            .border(1.dp, border, RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        AssetIcon(iconPath, fallback, Modifier.size(30.dp))
    }
}

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

@Composable
fun PauseOverlay(
    onResume: () -> Unit,
    onSaveAndExit: () -> Unit,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize().background(Color(0xAA000000)), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .width(280.dp)
                .background(GameColors.Panel, RoundedCornerShape(12.dp))
                .border(2.dp, GameColors.PanelBorder, RoundedCornerShape(12.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("PAUSED", style = GameTypography.Title)
            CommandButton("RESUME", onClick = onResume, modifier = Modifier.fillMaxWidth())
            CommandButton("SAVE & EXIT", onClick = onSaveAndExit, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun GameOverOverlay(
    winner: Int,
    isPlayerWin: Boolean,
    onNewGame: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize().background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
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
                style = GameTypography.Title.copy(fontSize = 32.sp, color = if (isPlayerWin) GameColors.TextGold else GameColors.HpRed)
            )
            val factionName = FACTIONS.getOrNull(winner)?.name ?: "Unknown"
            Text(
                text = if (isPlayerWin) "The $factionName reigns supreme!" else "The $factionName has conquered your realm.",
                style = GameTypography.Body
            )
            CommandButton("NEW GAME", onClick = onNewGame, modifier = Modifier.fillMaxWidth())
            CommandButton("EXIT", onClick = onExit, modifier = Modifier.fillMaxWidth())
        }
    }
}

private fun compactComposition(units: List<GameUnit>): String {
    if (units.isEmpty()) return ""
    return units.groupBy { it.type }
        .entries
        .joinToString(" ") { (type, group) -> "${group.size}x${UNITS[type]?.label ?: type}" }
}

private fun resourceName(resource: GameResource): String = when (resource.type) {
    ResourceType.TREE -> "Forest"
    ResourceType.GOLD -> "Gold"
    ResourceType.FOOD -> if (resource.isAnimal) {
        when (resource.animalKind) {
            "grouse" -> "Grouse"
            else -> resource.animalKind.replaceFirstChar { it.uppercase() }
        }
    } else {
        "Food"
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
