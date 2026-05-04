package com.tinyswords.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tinyswords.app.R
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
            horizontalArrangement = Arrangement.spacedBy(if (compact) 14.dp else 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1.05f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.tiny_swords_logo_full),
                    contentDescription = null,
                    modifier = Modifier.size(if (compact) 72.dp else 108.dp)
                )
                Text(
                    text = "TINY\nSWORDS",
                    style = GameTypography.Title.copy(
                        fontSize = if (compact) 32.sp else 46.sp,
                        lineHeight = if (compact) 31.sp else 43.sp
                    ),
                    textAlign = TextAlign.Center
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 11.dp)
            ) {
                RealmMenuButton("NEW REALM", onNewGame, compact, true)
                RealmMenuButton("LOAD REALM", onLoadGame, compact)
                RealmMenuButton("SETTINGS", onSettings, compact)
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
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            title,
            style = GameTypography.Button.copy(
                fontSize = if (compact) 12.sp else 14.sp,
                color = if (primary) GameColors.TextGold else GameColors.TextPrimary
            ),
            modifier = Modifier.weight(1f)
        )
        Text(">", style = GameTypography.Heading.copy(color = GameColors.TextGold, fontSize = if (compact) 16.sp else 20.sp))
    }
}
