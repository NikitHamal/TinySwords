package com.tinyswords.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Pixel art game color palette
object GameColors {
    val Background = Color(0xFF1a1a2e)
    val Panel = Color(0xCC2a1a0a)
    val PanelBorder = Color(0xFF6a5030)
    val PanelLight = Color(0xFF3a2a1a)

    val TextPrimary = Color(0xFFf0e8d0)
    val TextSecondary = Color(0xFFb0a890)
    val TextGold = Color(0xFFe6ca59)

    val ButtonNormal = Color(0xFF4a3a2a)
    val ButtonPressed = Color(0xFF6a5a3a)
    val ButtonDisabled = Color(0xFF2a2a2a)
    val ButtonBorder = Color(0xFF8a7050)

    val WoodColor = Color(0xFF6a9a30)
    val GoldColor = Color(0xFFd4a017)
    val FoodColor = Color(0xFFcc6633)
    val PopColor = Color(0xFF90b0d0)

    val HpGreen = Color(0xFF40c040)
    val HpYellow = Color(0xFFc0c040)
    val HpRed = Color(0xFFc04040)

    val BlueFaction = Color(0xFF61b7d9)
    val RedFaction = Color(0xFFdb6060)
    val YellowFaction = Color(0xFFe6ca59)
    val PurpleFaction = Color(0xFFb071df)
    val BlackFaction = Color(0xFFaeb3bd)

    val AccentBlue = Color(0xFF4a90c0)
    val AccentGreen = Color(0xFF50a050)
    val AccentRed = Color(0xFFc05050)
    val Green = Color(0xFF6bc982)
}

object GameTypography {
    val Title = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        color = GameColors.TextGold
    )
    val Heading = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        color = GameColors.TextPrimary
    )
    val Body = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        color = GameColors.TextPrimary
    )
    val SectionTitle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        color = GameColors.TextGold
    )
    val Small = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        color = GameColors.TextSecondary
    )
    val Button = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        color = GameColors.TextPrimary
    )
}
