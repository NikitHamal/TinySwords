package com.tinyswords.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tinyswords.game.*

/**
 * In-game HUD. Three pieces:
 *   - top bar (resources + pop)
 *   - bottom-left selection panel
 *   - bottom-center action dock (orders / training)
 *   - top-right state readout (pause, save, menu)
 *
 * The minimap and build menu live in [Minimap] / [BuildMenu] composables.
 */
@Composable
fun TopBar(faction: Faction) {
    Row(
        Modifier
            .clip(RoundedCornerShape(bottomStart = 0.dp, bottomEnd = 8.dp))
            .background(Palette.Panel)
            .border(2.dp, Palette.LineHot, RoundedCornerShape(bottomStart = 0.dp, bottomEnd = 8.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("TINY SWORDS", style = Type.Heading.copy(color = Palette.Gold))
        ResourcePill("Wood", faction.wood, Color(0xFF8B5A3C))
        ResourcePill("Gold", faction.gold, Palette.Gold)
        ResourcePill("Food", faction.food, Palette.Red)
        ResourcePill("Pop", faction.popUsed, Palette.Green, "/${faction.popCap}")
    }
}

@Composable
private fun ResourcePill(label: String, value: Int, accent: Color, suffix: String = "") {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$value$suffix", style = Type.Body.copy(color = accent))
        Text(label.uppercase(), style = Type.Tiny)
    }
}

@Composable
fun StateReadout(paused: Boolean, onPause: () -> kotlin.Unit, onSave: () -> kotlin.Unit, onMenu: () -> kotlin.Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(bottomStart = 8.dp))
            .background(Palette.Panel)
            .border(2.dp, Palette.LineHot, RoundedCornerShape(bottomStart = 8.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val statusColor = if (paused) Palette.Red else Palette.Green
        Box(Modifier.size(8.dp).background(statusColor, RoundedCornerShape(4.dp)))
        Text(if (paused) "Paused" else "Live", style = Type.Small)
        DockButton(if (paused) "Resume" else "Pause", onPause)
        DockButton("Save", onSave)
        DockButton("Menu", onMenu)
    }
}

@Composable
fun SelectionPanel(game: Game, sim: com.tinyswords.game.Simulation) {
    val sel = game.selection.mapNotNull { game.findEntity(it) }
    if (sel.isEmpty()) return
    Column(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Palette.Panel)
            .border(2.dp, Palette.LineHot, RoundedCornerShape(8.dp))
            .padding(10.dp)
            .widthIn(min = 220.dp, max = 320.dp)
    ) {
        if (sel.size == 1) {
            val e = sel.first()
            val name = when (e) {
                is GameUnit -> e.type.display
                is Building -> e.type.display
                is GameResource -> e.kind.display
                else -> "Entity"
            }
            Text(name, style = Type.Heading)
            Text("HP: ${e.hp.toInt()} / ${e.maxHp.toInt()}", style = Type.Small)
            if (e is Building && e.queue.isNotEmpty()) {
                Text("Training: ${e.queue.first().type.display} (${(e.queue.first().progress * 100).toInt()}%)", style = Type.Small)
            }
            if (e is GameUnit) Text("Order: ${e.order.name.lowercase()}", style = Type.Small)
            if (e is GameResource) Text("Amount: ${e.amount}", style = Type.Small)
        } else {
            Text("Selected: ${sel.size}", style = Type.Heading)
            val units = sel.filterIsInstance<GameUnit>().groupBy { it.type }
            for ((type, list) in units) {
                Text("${type.display} × ${list.size}", style = Type.Small)
            }
        }
    }
}

@Composable
fun ActionDock(
    game: Game,
    sim: com.tinyswords.game.Simulation,
    onOpenBuild: () -> kotlin.Unit,
    onAttackMove: () -> kotlin.Unit,
    onStop: () -> kotlin.Unit
) {
    val sel = game.selection.mapNotNull { game.findEntity(it) }
    if (sel.isEmpty()) return
    val ownUnits = sel.filterIsInstance<GameUnit>().filter { it.faction == game.playerFaction }
    val ownBuildings = sel.filterIsInstance<Building>().filter { it.faction == game.playerFaction }
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Palette.Panel)
            .border(2.dp, Palette.LineHot, RoundedCornerShape(8.dp))
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (ownUnits.isNotEmpty()) {
            DockButton("Stop", onStop)
            DockButton("Attack-Move", onAttackMove)
            if (ownUnits.any { it.type == UnitType.WORKER }) {
                DockButton("Build", onOpenBuild)
            }
        }
        for (b in ownBuildings.distinctBy { it.type }) {
            for (t in b.type.trains) {
                DockButton("Train ${t.display}") {
                    val f = game.factions[game.playerFaction]
                    if (f.wood >= t.costWood && f.gold >= t.costGold && b.queue.size < 5) {
                        f.wood -= t.costWood; f.gold -= t.costGold
                        b.queue += TrainOrder(t)
                    }
                }
            }
        }
    }
}

@Composable
fun DockButton(label: String, onClick: () -> kotlin.Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Palette.PanelStrong)
            .border(1.dp, Palette.LineHot, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label, style = Type.Small.copy(color = Palette.Gold))
    }
}
