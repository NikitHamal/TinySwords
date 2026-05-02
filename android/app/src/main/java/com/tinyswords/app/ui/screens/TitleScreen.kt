package com.tinyswords.app.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
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
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0a1a2a),
                        Color(0xFF11281f),
                        Color(0xFF1a1a0a)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        val compact = maxHeight < 430.dp
        Column(
            modifier = Modifier
                .fillMaxWidth(if (compact) 0.86f else 0.58f)
                .widthIn(max = 520.dp)
                .heightIn(max = maxHeight * 0.90f)
                .verticalScroll(rememberScrollState())
                .background(GameColors.Panel.copy(alpha = 0.96f), RoundedCornerShape(16.dp))
                .border(3.dp, GameColors.PanelBorder, RoundedCornerShape(16.dp))
                .padding(if (compact) 18.dp else 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 16.dp)
        ) {
            Text(
                text = "TINY SWORDS",
                style = GameTypography.Title.copy(fontSize = if (compact) 30.sp else 40.sp),
                textAlign = TextAlign.Center
            )
            Text(
                text = "REALM WAR",
                style = GameTypography.Heading.copy(
                    fontSize = if (compact) 15.sp else 19.sp,
                    color = GameColors.TextSecondary,
                    letterSpacing = if (compact) 4.sp else 6.sp
                ),
                textAlign = TextAlign.Center
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.56f)
                    .height(2.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Color.Transparent, GameColors.PanelBorder, Color.Transparent)
                        )
                    )
            )

            MenuButton("NEW GAME", onClick = onNewGame, compact = compact)
            MenuButton("LOAD GAME", onClick = onLoadGame, compact = compact)
            MenuButton("SETTINGS", onClick = onSettings, compact = compact)

            Text(
                text = "Native Android Edition",
                style = GameTypography.Small.copy(color = GameColors.TextSecondary.copy(alpha = 0.65f))
            )
        }
    }
}

@Composable
private fun MenuButton(text: String, onClick: () -> Unit, compact: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (compact) 42.dp else 52.dp)
            .background(GameColors.ButtonNormal, RoundedCornerShape(6.dp))
            .border(1.5.dp, GameColors.ButtonBorder, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = GameTypography.Button.copy(fontSize = if (compact) 12.sp else 15.sp, letterSpacing = 2.sp)
        )
    }
}
