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
                Text(
                    text = "TINY\nSWORDS",
                    style = GameTypography.Title.copy(fontSize = if (compact) 32.sp else 46.sp, lineHeight = if (compact) 31.sp else 43.sp),
                    textAlign = TextAlign.Center
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 11.dp)
            ) {
                RealmMenuButton("NEW REALM", onNewGame, compact, true)
                RealmMenuButton("LOAD REALM", onLoadGame, compact, false)
                RealmMenuButton("SETTINGS", onSettings, compact, false)
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
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, style = GameTypography.Button.copy(fontSize = if (compact) 12.sp else 14.sp, color = if (primary) GameColors.TextGold else GameColors.TextPrimary))
        Text("›", style = GameTypography.Heading.copy(color = GameColors.TextGold, fontSize = if (compact) 18.sp else 22.sp))
    }
}
