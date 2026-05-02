package com.tinyswords.app.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tinyswords.app.audio.SoundBank
import com.tinyswords.app.data.SaveSystem
import com.tinyswords.app.ui.components.AssetIcon
import com.tinyswords.app.ui.components.CommandButton
import com.tinyswords.app.ui.theme.GameColors
import com.tinyswords.app.ui.theme.GameTypography

@Composable
fun SettingsScreen(
    saveSystem: SaveSystem,
    soundBank: SoundBank,
    onBack: () -> Unit
) {
    var settings by remember { mutableStateOf(saveSystem.loadGlobalSettings()) }
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(RealmMenuBackground()),
        contentAlignment = Alignment.Center
    ) {
        val compact = maxHeight < 430.dp
        Column(
            modifier = Modifier
                .fillMaxWidth(if (compact) 0.90f else 0.58f)
                .widthIn(max = 620.dp)
                .heightIn(max = maxHeight * 0.88f)
                .verticalScroll(rememberScrollState())
                .background(GameColors.Panel.copy(alpha = 0.97f), RoundedCornerShape(14.dp))
                .border(3.dp, GameColors.PanelBorder, RoundedCornerShape(14.dp))
                .padding(if (compact) 14.dp else 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            AssetIcon("Tiny Swords (Free Pack)/UI Elements/UI Elements/Icons/Icon_05.png", GameColors.TextGold, Modifier.size(if (compact) 42.dp else 56.dp))
            Text("SETTINGS", style = GameTypography.Title.copy(fontSize = if (compact) 22.sp else 30.sp))
            Text("Global defaults. Existing saved worlds keep their own world settings.", style = GameTypography.Small.copy(color = GameColors.TextSecondary), textAlign = TextAlign.Center)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x55000000), RoundedCornerShape(8.dp))
                    .border(1.dp, GameColors.PanelBorder, RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Text("VOLUME ${(settings.volume * 100f).toInt()}%", style = GameTypography.Heading.copy(fontSize = 13.sp, color = GameColors.TextGold))
                Slider(
                    value = settings.volume,
                    onValueChange = { value ->
                        settings = settings.copy(volume = value.coerceIn(0f, 1f))
                        soundBank.setVolume(settings.volume)
                        saveSystem.saveGlobalSettings(settings)
                    },
                    valueRange = 0f..1f
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingChip("Autosave", if (settings.autosave) "ON" else "OFF", settings.autosave, Modifier.weight(1f)) {
                    settings = settings.copy(autosave = !settings.autosave)
                    saveSystem.saveGlobalSettings(settings)
                }
                SettingChip("Graphics", settings.graphics.uppercase(), true, Modifier.weight(1f)) {
                    val next = when (settings.graphics) {
                        "performance" -> "balanced"
                        "balanced" -> "high"
                        else -> "performance"
                    }
                    settings = settings.copy(graphics = next)
                    saveSystem.saveGlobalSettings(settings)
                }
            }

            Text(
                text = "Performance mode lowers spawn density and reduces frame pressure; use it on older devices.",
                style = GameTypography.Small.copy(color = GameColors.TextSecondary),
                textAlign = TextAlign.Center
            )
            CommandButton("BACK", onClick = onBack, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SettingChip(label: String, value: String, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .height(72.dp)
            .background(if (active) GameColors.ButtonPressed else GameColors.ButtonNormal, RoundedCornerShape(6.dp))
            .border(1.dp, if (active) GameColors.TextGold else GameColors.ButtonBorder, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(label, style = GameTypography.Small.copy(color = GameColors.TextSecondary))
        Text(value, style = GameTypography.Button.copy(fontSize = 11.sp))
    }
}
