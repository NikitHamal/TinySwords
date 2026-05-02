package com.tinyswords.realmwar.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Tiny pixel-art-flavored visual language — chunky panels, warm gold accents,
 * and slab type to evoke the tin-soldier aesthetic of the source pack.
 */
object TS {
    val PanelTop = Color(0xFF253546)
    val PanelMid = Color(0xFF1A2638)
    val PanelDark = Color(0xFF101A28)
    val PanelEdge = Color(0xFF050A12)
    val PanelHi = Color(0xFF38516A)

    val Gold = Color(0xFFE8C557)
    val GoldLow = Color(0xFFA37B17)
    val Wood = Color(0xFF9CCB77)
    val Food = Color(0xFFF6A167)
    val Danger = Color(0xFFCD4444)
    val Frame = Color(0xFF2A405A)
    val Ink = Color(0xFFEFE3C5)
    val Mute = Color(0xFFA8B5C8)

    val PanelBrush = Brush.verticalGradient(
        listOf(PanelTop, PanelMid, PanelDark)
    )
}

@Composable
fun TinySwordsTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = TS.Gold,
        onPrimary = Color.Black,
        background = TS.PanelDark,
        surface = TS.PanelMid,
        onBackground = TS.Ink,
        onSurface = TS.Ink,
        error = TS.Danger,
    )
    MaterialTheme(colorScheme = colors, typography = pixelTypography(), content = content)
}

private fun pixelTypography(): Typography {
    val base = Typography()
    val ink = TextStyle(color = TS.Ink, fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 0.4.sp)
    return base.copy(
        titleLarge = base.titleLarge.copy(color = TS.Ink, fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Black, fontSize = 28.sp, letterSpacing = 0.6.sp),
        titleMedium = base.titleMedium.copy(color = TS.Ink, fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = 0.5.sp),
        bodyMedium = base.bodyMedium.copy(color = TS.Ink, fontFamily = FontFamily.Monospace),
        bodySmall = base.bodySmall.copy(color = TS.Mute, fontFamily = FontFamily.Monospace),
        labelLarge = ink,
    )
}

@Composable
fun pixelPanelModifier(
    padding: PaddingValues = PaddingValues(12.dp),
): Modifier = Modifier
    .background(TS.PanelBrush, RoundedCornerShape(6.dp))
    .border(BorderStroke(2.dp, TS.PanelEdge), RoundedCornerShape(6.dp))
    .border(BorderStroke(1.dp, TS.PanelHi), RoundedCornerShape(6.dp))
    .padding(padding)
