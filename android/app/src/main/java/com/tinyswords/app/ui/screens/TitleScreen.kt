package com.tinyswords.app.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tinyswords.app.R
import com.tinyswords.app.ui.components.AssetIcon
import com.tinyswords.app.ui.theme.GameColors
import com.tinyswords.app.ui.theme.GameTypography

@Composable
fun TitleScreen(
    onNewGame: () -> Unit,
    onLoadGame: () -> Unit,
    onSettings: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(RealmMenuBackground()),
        contentAlignment = Alignment.Center
    ) {
        val compact = maxHeight < 430.dp
        Row(
            modifier = Modifier
                .fillMaxWidth(if (compact) 0.92f else 0.78f)
                .widthIn(max = 860.dp)
                .heightIn(max = maxHeight * 0.92f)
                .verticalScroll(rememberScrollState())
                .background(GameColors.Panel.copy(alpha = 0.96f), RoundedCornerShape(14.dp))
                .border(3.dp, GameColors.PanelBorder, RoundedCornerShape(14.dp))
                .padding(if (compact) 14.dp else 22.dp),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1.08f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(if (compact) 64.dp else 92.dp)
                )
                Text(
                    text = "TINY\nSWORDS",
                    style = GameTypography.Title.copy(fontSize = if (compact) 32.sp else 46.sp, lineHeight = if (compact) 31.sp else 43.sp),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "REALM WAR",
                    style = GameTypography.Heading.copy(fontSize = if (compact) 14.sp else 18.sp, color = GameColors.TextSecondary, letterSpacing = 5.sp),
                    textAlign = TextAlign.Center
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    AssetIcon("Tiny Swords (Free Pack)/Buildings/Blue Buildings/Castle.png", GameColors.AccentBlue, Modifier.size(if (compact) 52.dp else 74.dp))
                    AssetIcon("Tiny Swords (Free Pack)/Units/Blue Units/Warrior/Warrior_Idle.png", GameColors.TextGold, Modifier.size(if (compact) 46.dp else 62.dp))
                    AssetIcon("Tiny Swords (Free Pack)/Terrain/Resources/Wood/Wood Resource/Wood Resource.png", GameColors.WoodColor, Modifier.size(if (compact) 42.dp else 58.dp))
                }
                Text(
                    text = "A persistent 2D RTS kingdom: gather, build, defend, and command armies across generated realms.",
                    style = GameTypography.Small.copy(color = GameColors.TextSecondary, fontSize = if (compact) 9.sp else 11.sp),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(0.92f)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 11.dp)
            ) {
                RealmMenuButton("NEW REALM", "Generate terrain and start a save", "Tiny Swords (Free Pack)/UI Elements/UI Elements/Icons/Icon_08.png", onNewGame, compact, true)
                RealmMenuButton("LOAD REALM", "Continue a saved war", "Tiny Swords (Free Pack)/UI Elements/UI Elements/Icons/Icon_01.png", onLoadGame, compact, false)
                RealmMenuButton("SETTINGS", "Audio and performance defaults", "Tiny Swords (Free Pack)/UI Elements/UI Elements/Icons/Icon_05.png", onSettings, compact, false)
                Text(
                    text = "Native Android Edition",
                    style = GameTypography.Small.copy(color = GameColors.TextSecondary.copy(alpha = 0.70f)),
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
internal fun RealmMenuBackground(): Brush = Brush.verticalGradient(
    listOf(Color(0xFF06131B), Color(0xFF123323), Color(0xFF201A08))
)

@Composable
internal fun RealmMenuButton(
    title: String,
    subtitle: String,
    iconPath: String,
    onClick: () -> Unit,
    compact: Boolean,
    primary: Boolean = false,
    modifier: Modifier = Modifier
) {
    val bg = if (primary) GameColors.ButtonPressed else GameColors.ButtonNormal
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(if (compact) 46.dp else 58.dp)
            .background(bg.copy(alpha = 0.96f), RoundedCornerShape(7.dp))
            .border(1.5.dp, if (primary) GameColors.TextGold else GameColors.ButtonBorder, RoundedCornerShape(7.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AssetIcon(iconPath, GameColors.TextGold, Modifier.size(if (compact) 28.dp else 34.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = GameTypography.Button.copy(fontSize = if (compact) 11.sp else 13.sp, color = if (primary) GameColors.TextGold else GameColors.TextPrimary))
            Text(subtitle, style = GameTypography.Small.copy(fontSize = if (compact) 7.sp else 8.sp, color = GameColors.TextSecondary), maxLines = 1)
        }
        Text("›", style = GameTypography.Heading.copy(color = GameColors.TextGold, fontSize = if (compact) 18.sp else 22.sp))
    }
}
