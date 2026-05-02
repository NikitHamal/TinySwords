package com.tinyswords.realmwar.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tinyswords.realmwar.game.Difficulty
import com.tinyswords.realmwar.game.GraphicsTier
import com.tinyswords.realmwar.game.MapStyle
import com.tinyswords.realmwar.game.ResourceDensity
import com.tinyswords.realmwar.game.WorldSettings
import com.tinyswords.realmwar.game.WorldSize
import com.tinyswords.realmwar.storage.GlobalSettings
import com.tinyswords.realmwar.storage.WorldRecord
import com.tinyswords.realmwar.storage.WorldStorage
import java.text.DateFormat
import java.util.Date

// ----- Shared chunky pixel button --------------------------------------------

@Composable
fun PixelButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val baseTop = when {
        danger -> Color(0xFFCD4444)
        primary -> TS.Gold
        else -> TS.PanelTop
    }
    val baseMid = when {
        danger -> Color(0xFF8E2C2C)
        primary -> TS.GoldLow
        else -> TS.PanelMid
    }
    val ink = if (primary) Color(0xFF1A1305) else TS.Ink
    val brush = Brush.verticalGradient(listOf(baseTop, baseMid))
    val edge = if (primary) Color(0xFF60410A) else TS.PanelEdge
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(brush, RoundedCornerShape(6.dp))
            .border(BorderStroke(2.dp, edge), RoundedCornerShape(6.dp))
            .border(BorderStroke(1.dp, TS.PanelHi.copy(alpha = if (primary) 0.6f else 0.5f)), RoundedCornerShape(6.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text.uppercase(),
            color = if (enabled) ink else ink.copy(alpha = 0.45f),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Black,
            fontSize = 14.sp,
            letterSpacing = 1.2.sp,
        )
    }
}

@Composable
private fun ChipButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val brush = if (selected) Brush.verticalGradient(listOf(TS.Gold, TS.GoldLow))
    else Brush.verticalGradient(listOf(TS.PanelTop, TS.PanelMid))
    val edge = if (selected) Color(0xFF60410A) else TS.PanelEdge
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(brush)
            .border(BorderStroke(2.dp, edge), RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = text.uppercase(),
            color = if (selected) Color(0xFF1A1305) else TS.Ink,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            letterSpacing = 0.6.sp,
        )
    }
}

@Composable
private fun PanelTitle(text: String) {
    Text(
        text = text.uppercase(),
        color = TS.Gold,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Black,
        fontSize = 22.sp,
        letterSpacing = 1.4.sp,
    )
}

@Composable
private fun PanelSubtitle(text: String) {
    Text(
        text = text,
        color = TS.Mute,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
    )
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = TS.Gold.copy(alpha = 0.85f),
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        letterSpacing = 1.0.sp,
    )
}

// ----- Loading screen ---------------------------------------------------------

@Composable
fun LoadingScreen(progress: Float) {
    Box(modifier = Modifier.fillMaxSize().background(TS.PanelBrush), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "TINY SWORDS",
                color = TS.Gold,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                fontSize = 38.sp,
                letterSpacing = 4.sp,
            )
            Text(
                text = "Realm War",
                color = TS.Ink.copy(alpha = 0.85f),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(28.dp))
            Box(
                modifier = Modifier
                    .width(280.dp).height(18.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(TS.PanelDark)
                    .border(BorderStroke(2.dp, TS.PanelEdge), RoundedCornerShape(4.dp)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .background(Brush.horizontalGradient(listOf(TS.Gold, Color(0xFFFFE49A)))),
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Forging ${(progress * 100).toInt()}%",
                color = TS.Mute,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
            )
        }
    }
}

// ----- Title screen -----------------------------------------------------------

@Composable
fun TitleScreen(
    storage: WorldStorage,
    onContinue: (WorldRecord?) -> Unit,
    onSinglePlayer: () -> Unit,
    onSettings: () -> Unit,
) {
    var latest by remember { mutableStateOf<WorldRecord?>(null) }
    LaunchedEffect(Unit) {
        latest = storage.loadWorlds().firstOrNull()
    }
    Box(
        modifier = Modifier.fillMaxSize().background(TS.PanelBrush),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left: title + tagline
            Column(
                modifier = Modifier.weight(1.4f),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "TINY",
                    color = TS.Gold,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    fontSize = 72.sp,
                    letterSpacing = 6.sp,
                )
                Text(
                    text = "SWORDS",
                    color = TS.Ink,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    fontSize = 72.sp,
                    letterSpacing = 6.sp,
                )
                Text(
                    text = "Realm War — a tin-soldier RTS",
                    color = TS.Mute,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    letterSpacing = 1.6.sp,
                )
            }
            // Right: menu panel
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight(0.9f),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(min = 300.dp, max = 360.dp)
                        .then(pixelPanelModifier(PaddingValues(20.dp))),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    PanelTitle("Main Menu")
                    PanelSubtitle("Lead a clan. Forge a realm. Outlast four rivals.")
                    Spacer(Modifier.height(4.dp))
                    PixelButton(
                        text = "Continue",
                        modifier = Modifier.fillMaxWidth(),
                        enabled = latest != null,
                        primary = true,
                        onClick = { onContinue(latest) },
                    )
                    PixelButton(
                        text = "Single Player",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onSinglePlayer,
                    )
                    PixelButton(
                        text = "Settings",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onSettings,
                    )
                    if (latest != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Last: ${latest!!.name}",
                            color = TS.Mute,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }
    }
}

// ----- World list -------------------------------------------------------------

@Composable
fun WorldListScreen(
    storage: WorldStorage,
    onBack: () -> Unit,
    onCreateNew: () -> Unit,
    onPlay: (WorldRecord) -> Unit,
) {
    var worlds by remember { mutableStateOf(storage.loadWorlds()) }
    val df = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    Column(
        modifier = Modifier.fillMaxSize().background(TS.PanelBrush).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PanelTitle("Choose a World")
            Spacer(Modifier.weight(1f))
            PixelButton(text = "New", primary = true, onClick = onCreateNew)
            Spacer(Modifier.width(8.dp))
            PixelButton(text = "Back", onClick = onBack)
        }
        if (worlds.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 220.dp)
                    .then(pixelPanelModifier(PaddingValues(20.dp))),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                       verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PanelSubtitle("No worlds yet — forge your first realm.")
                    PixelButton(text = "Create World", primary = true, onClick = onCreateNew)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(worlds, key = { it.id }) { record ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(pixelPanelModifier(PaddingValues(14.dp))),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = record.name,
                                color = TS.Ink,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                letterSpacing = 0.6.sp,
                            )
                            Text(
                                text = "${record.settings.size.label} · ${record.settings.mapStyle.label} · " +
                                    "${record.settings.difficulty.label} · seed ${record.seed}",
                                color = TS.Mute,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                            )
                            Text(
                                text = "Played ${df.format(Date(record.lastPlayedAtMs))}",
                                color = TS.Mute.copy(alpha = 0.7f),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        PixelButton(text = "Play", primary = true, onClick = { onPlay(record) })
                        Spacer(Modifier.width(8.dp))
                        PixelButton(
                            text = "Delete",
                            danger = true,
                            onClick = {
                                storage.deleteWorld(record.id)
                                worlds = storage.loadWorlds()
                            },
                        )
                    }
                }
            }
        }
    }
}

// ----- Create world -----------------------------------------------------------

@Composable
fun CreateWorldScreen(
    storage: WorldStorage,
    onBack: () -> Unit,
    onCreated: (WorldRecord) -> Unit,
) {
    var name by remember { mutableStateOf("New Realm") }
    var seed by remember { mutableStateOf("") }
    var size by remember { mutableStateOf(WorldSize.LARGE) }
    var style by remember { mutableStateOf(MapStyle.CROSSROADS) }
    var difficulty by remember { mutableStateOf(Difficulty.NORMAL) }
    var density by remember { mutableStateOf(ResourceDensity.RICH) }
    var rivals by remember { mutableStateOf(4) }
    var graphics by remember { mutableStateOf(GraphicsTier.BALANCED) }

    Column(
        modifier = Modifier.fillMaxSize().background(TS.PanelBrush).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PanelTitle("Forge Your Realm")
            Spacer(Modifier.weight(1f))
            PixelButton(text = "Back", onClick = onBack)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth().weight(1f)
                .then(pixelPanelModifier(PaddingValues(18.dp))),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Name
            FieldLabel("Realm Name")
            PixelTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = "New Realm",
                modifier = Modifier.fillMaxWidth(),
            )

            // Seed
            FieldLabel("Seed (optional)")
            PixelTextField(
                value = seed,
                onValueChange = { seed = it },
                placeholder = "auto",
                modifier = Modifier.fillMaxWidth(),
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FieldLabel("World Size")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        WorldSize.values().forEach { s ->
                            ChipButton(text = s.label.split(" ").first(), selected = size == s, onClick = { size = s })
                        }
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FieldLabel("Difficulty")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Difficulty.values().forEach { d ->
                            ChipButton(text = d.label, selected = difficulty == d, onClick = { difficulty = d })
                        }
                    }
                }
            }

            FieldLabel("Map Style")
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MapStyle.values().toList().chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { ms ->
                            ChipButton(
                                text = ms.label.split(" ").first(),
                                selected = style == ms,
                                onClick = { style = ms },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
            Text(
                text = style.description,
                color = TS.Mute,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FieldLabel("Resources")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ResourceDensity.values().forEach { d ->
                            ChipButton(text = d.label, selected = density == d, onClick = { density = d })
                        }
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FieldLabel("Rivals")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (n in 0..4) {
                            ChipButton(text = n.toString(), selected = rivals == n, onClick = { rivals = n })
                        }
                    }
                }
            }

            FieldLabel("Graphics")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GraphicsTier.values().forEach { g ->
                    ChipButton(text = g.label, selected = graphics == g, onClick = { graphics = g })
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            PixelButton(
                text = "Forge Realm",
                primary = true,
                onClick = {
                    val record = storage.createWorld(
                        name.ifBlank { "New Realm" },
                        WorldSettings(
                            size = size,
                            mapStyle = style,
                            difficulty = difficulty,
                            resourceDensity = density,
                            rivals = rivals,
                            seed = seed,
                            graphics = graphics,
                        ),
                    )
                    onCreated(record)
                },
            )
        }
    }
}

// ----- Settings ---------------------------------------------------------------

@Composable
fun SettingsScreen(
    storage: WorldStorage,
    onBack: () -> Unit,
) {
    var settings by remember { mutableStateOf(storage.globalSettings()) }
    Column(
        modifier = Modifier.fillMaxSize().background(TS.PanelBrush).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PanelTitle("Settings")
            Spacer(Modifier.weight(1f))
            PixelButton(text = "Back", onClick = {
                storage.saveGlobalSettings(settings); onBack()
            })
        }
        Column(
            modifier = Modifier
                .fillMaxWidth().weight(1f)
                .then(pixelPanelModifier(PaddingValues(18.dp))),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // Volume
            FieldLabel("Sound Volume")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { v ->
                    ChipButton(
                        text = "${(v * 100).toInt()}%",
                        selected = kotlin.math.abs(settings.volume - v) < 0.05f,
                        onClick = { settings = settings.copy(volume = v) },
                    )
                }
            }
            // Autosave
            FieldLabel("Autosave")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ChipButton(text = "ON", selected = settings.autosave, onClick = {
                    settings = settings.copy(autosave = true)
                })
                ChipButton(text = "OFF", selected = !settings.autosave, onClick = {
                    settings = settings.copy(autosave = false)
                })
            }
            // Edge pan
            FieldLabel("Edge-Pan Camera")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ChipButton(text = "ON", selected = settings.edgePan, onClick = {
                    settings = settings.copy(edgePan = true)
                })
                ChipButton(text = "OFF", selected = !settings.edgePan, onClick = {
                    settings = settings.copy(edgePan = false)
                })
            }
            // Graphics
            FieldLabel("Graphics")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GraphicsTier.values().forEach { g ->
                    ChipButton(text = g.label, selected = settings.graphics == g, onClick = {
                        settings = settings.copy(graphics = g)
                    })
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Tiny Swords assets © Pixel Frog. Game code MIT-licensed.",
                color = TS.Mute.copy(alpha = 0.7f),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
        }
    }
}

// ----- Pixel text field -------------------------------------------------------

@Composable
private fun PixelTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(TS.PanelDark, RoundedCornerShape(4.dp))
            .border(BorderStroke(2.dp, TS.PanelEdge), RoundedCornerShape(4.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                color = TS.Mute.copy(alpha = 0.6f),
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                color = TS.Ink,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            ),
            cursorBrush = Brush.verticalGradient(listOf(TS.Gold, TS.Gold)),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
