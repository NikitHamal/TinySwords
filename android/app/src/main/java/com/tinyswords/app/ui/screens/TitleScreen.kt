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
import androidx.compose.ui.text.font.FontWeight
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0a1a2a),
                        Color(0xFF1a2a1a),
                        Color(0xFF1a1a0a)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Decorative border frame
        Column(
            modifier = Modifier
                .width(420.dp)
                .background(GameColors.Panel, RoundedCornerShape(16.dp))
                .border(3.dp, GameColors.PanelBorder, RoundedCornerShape(16.dp))
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Title
            Text(
                text = "TINY SWORDS",
                style = GameTypography.Title.copy(fontSize = 36.sp),
                textAlign = TextAlign.Center
            )
            Text(
                text = "REALM WAR",
                style = GameTypography.Heading.copy(
                    fontSize = 18.sp,
                    color = GameColors.TextSecondary,
                    letterSpacing = 6.sp
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Decorative divider
            Box(
                modifier = Modifier
                    .width(200.dp)
                    .height(2.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Color.Transparent, GameColors.PanelBorder, Color.Transparent)
                        )
                    )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Menu buttons
            MenuButton("NEW GAME", onClick = onNewGame)
            MenuButton("LOAD GAME", onClick = onLoadGame)
            MenuButton("SETTINGS", onClick = onSettings)

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "v1.0.0",
                style = GameTypography.Small.copy(color = GameColors.TextSecondary.copy(alpha = 0.5f))
            )
        }
    }
}

@Composable
private fun MenuButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(GameColors.ButtonNormal, RoundedCornerShape(6.dp))
            .border(1.5.dp, GameColors.ButtonBorder, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = GameTypography.Button.copy(fontSize = 14.sp, letterSpacing = 2.sp)
        )
    }
}
