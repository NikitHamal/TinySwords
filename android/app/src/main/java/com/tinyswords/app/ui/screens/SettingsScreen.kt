package com.tinyswords.app.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tinyswords.app.audio.SoundBank
import com.tinyswords.app.data.SaveSystem
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
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0a1a2a), Color(0xFF11281f), Color(0xFF1a1a0a))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        val compact = maxHeight < 430.dp
        Column(
            modifier = Modifier
                .fillMaxWidth(if (compact) 0.74f else 0.48f)
                .widthIn(max = 520.dp)
                .heightIn(max = maxHeight * 0.88f)
                .verticalScroll(rememberScrollState())
                .background(GameColors.Panel.copy(alpha = 0.97f), RoundedCornerShape(12.dp))
                .border(2.dp, GameColors.PanelBorder, RoundedCornerShape(12.dp))
                .padding(if (compact) 16.dp else 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("SETTINGS", style = GameTypography.Title.copy(fontSize = if (compact) 22.sp else 28.sp))
            Text(
                text = "Audio and play options are saved globally.",
                style = GameTypography.Small.copy(color = GameColors.TextSecondary),
                textAlign = TextAlign.Center
            )

            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "VOLUME ${(settings.volume * 100f).toInt()}%",
                    style = GameTypography.Heading.copy(fontSize = 14.sp)
                )
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SettingChip(
                    label = "Autosave",
                    value = if (settings.autosave) "ON" else "OFF",
                    active = settings.autosave,
                    modifier = Modifier.weight(1f)
                ) {
                    settings = settings.copy(autosave = !settings.autosave)
                    saveSystem.saveGlobalSettings(settings)
                }
                SettingChip(
                    label = "Graphics",
                    value = settings.graphics.uppercase(),
                    active = true,
                    modifier = Modifier.weight(1f)
                ) {
                    val next = if (settings.graphics == "balanced") "performance" else "balanced"
                    settings = settings.copy(graphics = next)
                    saveSystem.saveGlobalSettings(settings)
                }
            }

            CommandButton("BACK", onClick = onBack, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SettingChip(
    label: String,
    value: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
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
