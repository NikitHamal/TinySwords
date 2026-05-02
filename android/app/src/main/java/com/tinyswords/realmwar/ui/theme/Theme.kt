package com.tinyswords.realmwar.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val ColorScheme = darkColorScheme(
    primary = Color(0xFFF7DC62),
    onPrimary = Color(0xFF1a120c),
    secondary = Color(0xFFA23C3C),
    onSecondary = Color.White,
    background = Color(0xFF0D1426),
    onBackground = Color(0xFFE7E1CF),
    surface = Color(0xFF152038),
    onSurface = Color(0xFFE7E1CF),
    surfaceVariant = Color(0xFF1d2c4a),
    onSurfaceVariant = Color(0xFFCBC4B0)
)

private val PixelTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 36.sp, letterSpacing = 2.sp),
    displayMedium = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 24.sp, letterSpacing = 1.5.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 16.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.sp)
)

private val PixelShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp()),
    small = RoundedCornerShape(4.dp()),
    medium = RoundedCornerShape(6.dp()),
    large = RoundedCornerShape(10.dp()),
    extraLarge = RoundedCornerShape(14.dp())
)

private fun Int.dp() = androidx.compose.ui.unit.Dp(this.toFloat())

@Composable
fun TinySwordsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme,
        typography = PixelTypography,
        shapes = PixelShapes,
        content = content
    )
}
