package com.tinyswords.realmwar.ui.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.tinyswords.realmwar.data.WorldRecord
import com.tinyswords.realmwar.render.AssetLibrary
import com.tinyswords.realmwar.ui.components.PixelPanel
import kotlinx.coroutines.delay

/**
 * The "loading bar" between picking a world and entering it. Real generation happens lazily on
 * the GameScreen but we show staged progress messages mirroring the web flow.
 */
@Composable
fun GenerationScreen(record: WorldRecord, onReady: () -> kotlin.Unit) {
    var progress by remember { mutableStateOf(0f) }
    var status by remember { mutableStateOf("Preparing seed...") }
    val ctx = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(record.id) {
        AssetLibrary.get(ctx).preloadCore()
        val stages = listOf(
            "Preparing seed..." to 0.10f,
            "Carving land masses..." to 0.30f,
            "Painting terrain..." to 0.50f,
            "Spawning factions..." to 0.65f,
            "Scattering resources..." to 0.80f,
            "Releasing wildlife..." to 0.92f,
            "Lighting torches..." to 1.0f
        )
        for ((msg, p) in stages) {
            status = msg
            progress = p
            delay(220)
        }
        onReady()
    }

    val animatedProgress by animateFloatAsState(targetValue = progress, label = "progress")

    Box(modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .padding(24.dp), contentAlignment = Alignment.Center) {
        PixelPanel(title = "Generating ${record.name}") {
            Text(status, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(6.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}
