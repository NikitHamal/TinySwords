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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        Column(
            modifier = Modifier
                .fillMaxWidth(if (compact) 0.78f else 0.55f)
                .widthIn(max = 460.dp)
                .heightIn(max = maxHeight * 0.92f)
                .verticalScroll(rememberScrollState())
                .padding(if (compact) 14.dp else 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compact) 14.dp else 22.dp)
        ) {
            Text(
                text = "TINY\nSWORDS",
                style = GameTypography.Title.copy(
                    fontSize = if (compact) 44.sp else 60.sp,
                    lineHeight = if (compact) 42.sp else 56.sp
                ),
                textAlign = TextAlign.Center
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp)
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
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(if (compact) 50.dp else 62.dp)
            .background(bg.copy(alpha = 0.96f), RoundedCornerShape(8.dp))
            .border(1.5.dp, if (primary) GameColors.TextGold else GameColors.ButtonBorder, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            style = GameTypography.Button.copy(
                fontSize = if (compact) 14.sp else 17.sp,
                color = if (primary) GameColors.TextGold else GameColors.TextPrimary
            )
        )
    }
}
