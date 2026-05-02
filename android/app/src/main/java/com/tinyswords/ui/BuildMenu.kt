package com.tinyswords.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.tinyswords.game.BuildingType
import com.tinyswords.game.Faction

/**
 * Build picker. Shows all 6 building types as cards. Tap to select; the next world tap places.
 * Disabled state ghosts cards the player can't currently afford.
 */
@Composable
fun BuildMenu(faction: Faction, current: BuildingType?, onPick: (BuildingType?) -> kotlin.Unit, onClose: () -> kotlin.Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Palette.Panel)
            .border(2.dp, Palette.LineHot, RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Build", style = Type.Heading)
            Spacer(Modifier.weight(1f))
            DockButton("Close", onClose)
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (t in BuildingType.ALL) {
                val canAfford = faction.wood >= t.costWood && faction.gold >= t.costGold
                val selected = current == t
                Column(
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (selected) Palette.LineHot else Palette.PanelStrong)
                        .border(1.dp, Palette.LineHot, RoundedCornerShape(4.dp))
                        .clickable(enabled = canAfford) { onPick(if (selected) null else t) }
                        .padding(8.dp)
                        .width(76.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(t.display, style = Type.Small.copy(color = if (canAfford) Palette.Gold else Palette.Muted))
                    Text("W ${t.costWood}", style = Type.Tiny)
                    Text("G ${t.costGold}", style = Type.Tiny)
                }
            }
        }
    }
}
