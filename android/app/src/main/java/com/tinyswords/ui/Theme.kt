package com.tinyswords.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Pixel-art HUD palette — these match the web game's CSS variables. */
object Palette {
    val Panel       = Color(0xEB121F23)
    val PanelDeep   = Color(0xFF081119)
    val PanelStrong = Color(0xF5091115)
    val Gold        = Color(0xFFF2CF63)
    val GoldDeep    = Color(0xFF765322)
    val Text        = Color(0xFFFBFFE7)
    val Muted       = Color(0xFFABC8BD)
    val Green       = Color(0xFF78D777)
    val Red         = Color(0xFFE36B62)
    val LineHot     = Color(0xCCF6E18F)
    val Line        = Color(0x33F6E18F)
}

/** Helper text styles tuned for the pixel-art UI: monospace, slightly larger, gold accents. */
object Type {
    val Display = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Palette.Gold)
    val Heading = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
    val Body    = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = Palette.Text)
    val Small   = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Palette.Muted)
    val Tiny    = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = Palette.Muted)
}

@Composable
fun TinySwordsTheme(content: @Composable () -> kotlin.Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.Gold,
            onPrimary = Palette.PanelDeep,
            background = Palette.PanelDeep,
            surface = Palette.Panel,
            onSurface = Palette.Text
        ),
        content = content
    )
}
