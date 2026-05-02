package com.tinyswords.realmwar.ui.screen

import android.graphics.Canvas
import android.graphics.Color
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tinyswords.realmwar.TinySwordsApp
import com.tinyswords.realmwar.data.WorldRecord
import com.tinyswords.realmwar.game.Building
import com.tinyswords.realmwar.game.GameConfig
import com.tinyswords.realmwar.game.GameSession
import com.tinyswords.realmwar.game.SeededRandom
import com.tinyswords.realmwar.game.Unit
import com.tinyswords.realmwar.game.WorldGen
import com.tinyswords.realmwar.game.parseSettingsFromData
import com.tinyswords.realmwar.render.AssetLibrary
import com.tinyswords.realmwar.render.WorldRenderer
import com.tinyswords.realmwar.ui.components.PixelButton
import com.tinyswords.realmwar.ui.components.PixelPanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

@Composable
fun GameScreen(record: WorldRecord, onExit: () -> kotlin.Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as TinySwordsApp
    val assets = remember { AssetLibrary.get(ctx) }
    val renderer = remember { WorldRenderer(assets) }
    val density = LocalDensity.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val sessionState = remember(record.id) { mutableStateOf<GameSession?>(null) }
    val refresh = remember { mutableStateOf(0) }
    var showBuildMenu by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var showPause by remember { mutableStateOf(false) }
    var showMinimap by remember { mutableStateOf(true) }

    LaunchedEffect(record.id) {
        withContext(Dispatchers.Default) {
            val settings = parseSettingsFromData(record.settings).copy(name = record.name, seed = record.seed)
            val rng = SeededRandom(record.seed.ifBlank { record.id })
            val world = WorldGen(settings, rng).generate()
            val session = GameSession(record, settings, world)
            app.worldStorage.loadState(record.id)?.let { session.applySaveData(it) }
            sessionState.value = session
        }
    }

    val session = sessionState.value
    if (session == null) {
        Box(modifier = Modifier.fillMaxSize().background(Color.parseColor("#0d1426").let { androidx.compose.ui.graphics.Color(it) }), contentAlignment = Alignment.Center) {
            Text("Generating world...", color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    // Tick loop
    LaunchedEffect(session) {
        var last = System.nanoTime()
        while (true) {
            val now = System.nanoTime()
            val dt = ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
            last = now
            session.step(dt)
            if (session.settings.autosave && session.autosaveTimer > 45f) {
                session.autosaveTimer = 0f
                withContext(Dispatchers.IO) {
                    app.worldStorage.saveState(record.id, session.toSaveData())
                }
            }
            refresh.value = (refresh.value + 1) and 0x7FFFFFFF
            delay(16)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                    session.paused = true
                    runCatching {
                        app.worldStorage.saveState(record.id, session.toSaveData())
                    }
                }
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    if (!showPause) session.paused = false
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Game render layer
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { maxHeight.toPx() }
            GameSurface(session, renderer, widthPx, heightPx, refreshTrigger = refresh.value)
            // Drag-select rectangle gestures
            GameInput(
                session = session,
                widthPx = widthPx,
                heightPx = heightPx,
                onLongPressBuildMenu = { showBuildMenu = true }
            )
        }
        // HUD overlay
        HudOverlay(
            session = session,
            onBuild = { showBuildMenu = !showBuildMenu },
            onHelp = { showHelp = !showHelp },
            onPauseToggle = {
                showPause = !showPause
                session.paused = showPause
            },
            onSelectArmy = { session.selectAllPlayerArmy() },
            onMinimapToggle = { showMinimap = !showMinimap },
            showMinimap = showMinimap,
            onMinimapTap = { wx, wy ->
                session.camera.x = (wx - 600 / session.camera.zoom).coerceIn(0f, (session.world.width - 200).toFloat())
                session.camera.y = (wy - 360 / session.camera.zoom).coerceIn(0f, (session.world.height - 200).toFloat())
            }
        )
        if (showBuildMenu) {
            BuildMenu(
                session = session,
                onClose = { showBuildMenu = false },
                onSelected = { type ->
                    session.startBuildPlacement(type)
                    showBuildMenu = false
                }
            )
        }
        if (showHelp) HelpOverlay(onClose = { showHelp = false })
        if (showPause) PauseOverlay(
            session = session,
            onResume = { showPause = false; session.paused = false },
            onSaveExit = {
                runCatching { app.worldStorage.saveState(record.id, session.toSaveData()) }
                onExit()
            }
        )
    }
}

/**
 * Hosts an Android SurfaceView so we can draw with android.graphics.Canvas at native speed and
 * still nest inside Compose. The renderer handles all of the world drawing; Compose draws the HUD
 * on top.
 */
@Composable
private fun GameSurface(
    session: GameSession,
    renderer: WorldRenderer,
    viewW: Float,
    viewH: Float,
    refreshTrigger: Int
) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            object : SurfaceView(ctx), SurfaceHolder.Callback {
                init {
                    holder.addCallback(this)
                    setZOrderMediaOverlay(false)
                }
                override fun surfaceCreated(holder: SurfaceHolder) { drawNow(holder) }
                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) { drawNow(holder) }
                override fun surfaceDestroyed(holder: SurfaceHolder) {}
                fun drawNow(holder: SurfaceHolder) {
                    val canvas: Canvas? = try { holder.lockCanvas() } catch (_: Throwable) { null }
                    if (canvas != null) {
                        try { renderer.render(canvas, session.world, session.camera, viewW, viewH, session.selected, session.placement) }
                        finally { try { holder.unlockCanvasAndPost(canvas) } catch (_: Throwable) {} }
                    }
                }
            }
        },
        update = { view ->
            val canvas: Canvas? = try { view.holder.lockCanvas() } catch (_: Throwable) { null }
            if (canvas != null) {
                try { renderer.render(canvas, session.world, session.camera, viewW, viewH, session.selected, session.placement) }
                finally { try { view.holder.unlockCanvasAndPost(canvas) } catch (_: Throwable) {} }
            }
        }
    )
}

@Composable
private fun GameInput(
    session: GameSession,
    widthPx: Float,
    heightPx: Float,
    onLongPressBuildMenu: () -> kotlin.Unit
) {
    val cam = session.camera
    Box(
        modifier = Modifier
            .fillMaxSize()
            // Pan + pinch zoom
            .pointerInput(session) {
                detectTransformGestures { _, panChange, zoomChange, _ ->
                    cam.zoom = (cam.zoom * zoomChange).coerceIn(0.5f, 2.5f)
                    cam.x = (cam.x - panChange.x / cam.zoom).coerceIn(0f, max(0f, session.world.width - widthPx / cam.zoom))
                    cam.y = (cam.y - panChange.y / cam.zoom).coerceIn(0f, max(0f, session.world.height - heightPx / cam.zoom))
                }
            }
            // Tap (single) = select, double tap = focus, long press = build menu
            .pointerInput(session) {
                detectTapGestures(
                    onTap = { offset ->
                        val wx = cam.x + offset.x / cam.zoom
                        val wy = cam.y + offset.y / cam.zoom
                        if (session.placement != null) {
                            session.movePlacement(wx, wy)
                            session.confirmPlacement()
                            return@detectTapGestures
                        }
                        session.selectAt(wx, wy, additive = false)
                    },
                    onDoubleTap = { offset ->
                        val wx = cam.x + offset.x / cam.zoom
                        val wy = cam.y + offset.y / cam.zoom
                        // Order: right-click equivalent on mobile = double-tap to issue order
                        if (session.placement != null) {
                            session.movePlacement(wx, wy)
                            session.confirmPlacement()
                            return@detectTapGestures
                        }
                        session.issueOrder(wx, wy)
                    },
                    onLongPress = { offset ->
                        val wx = cam.x + offset.x / cam.zoom
                        val wy = cam.y + offset.y / cam.zoom
                        if (session.selected.isNotEmpty()) {
                            session.issueOrder(wx, wy)
                        } else {
                            onLongPressBuildMenu()
                        }
                    }
                )
            }
    )
}

@Composable
private fun HudOverlay(
    session: GameSession,
    onBuild: () -> kotlin.Unit,
    onHelp: () -> kotlin.Unit,
    onPauseToggle: () -> kotlin.Unit,
    onSelectArmy: () -> kotlin.Unit,
    onMinimapToggle: () -> kotlin.Unit,
    showMinimap: Boolean,
    onMinimapTap: (Float, Float) -> kotlin.Unit
) {
    val player = session.world.playerFaction()
    Box(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        // Top resource bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            PixelPanel(modifier = Modifier.padding(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ResourceLabel("WOOD", player.resWood, MaterialTheme.colorScheme.primary)
                    ResourceLabel("GOLD", player.resGold, MaterialTheme.colorScheme.secondary)
                    ResourceLabel("FOOD", player.resFood, MaterialTheme.colorScheme.primary)
                    val pop = session.world.units.filter { it.factionId == 0 }.sumOf { it.def.pop }
                    val popCap = session.world.buildings.filter { it.factionId == 0 && !it.dead }.sumOf { it.def.popProvided }
                    ResourceLabel("POP", pop, MaterialTheme.colorScheme.primary, max = popCap)
                }
            }
        }

        // Right side action buttons
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End,
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            PixelButton("Build", onClick = onBuild, primary = true)
            PixelButton("Army", onClick = onSelectArmy)
            PixelButton(if (session.paused) "▶" else "‖", onClick = onPauseToggle)
            PixelButton("?", onClick = onHelp)
        }

        // Bottom selection panel + commands
        SelectionPanel(session, modifier = Modifier.align(Alignment.BottomStart))

        // Minimap top-right
        if (showMinimap) {
            Minimap(
                session = session,
                modifier = Modifier.align(Alignment.TopEnd).padding(end = 70.dp).size(width = 220.dp, height = 150.dp),
                onTap = onMinimapTap,
                onClose = onMinimapToggle
            )
        }
    }
}

@Composable
private fun ResourceLabel(label: String, amount: Int, accent: androidx.compose.ui.graphics.Color, max: Int? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = accent, style = MaterialTheme.typography.labelLarge)
        Text(if (max != null) "$amount/$max" else "$amount", color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun SelectionPanel(session: GameSession, modifier: Modifier = Modifier) {
    val selectedUnits = session.world.units.filter { it.id in session.selected && !it.dead }
    val selectedBuildings = session.world.buildings.filter { it.id in session.selected && !it.dead }
    if (selectedUnits.isEmpty() && selectedBuildings.isEmpty()) return
    PixelPanel(modifier = modifier.padding(2.dp)) {
        if (selectedUnits.isNotEmpty()) {
            val byType = selectedUnits.groupBy { it.type }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((type, list) in byType) {
                    val def = list.first().def
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${def.label} ×${list.size}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
                        Text("HP ${list.sumOf { it.hp.toInt() }}/${list.size * def.hp}", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (selectedBuildings.isNotEmpty()) {
            val b = selectedBuildings.first()
            Column {
                Text("${b.def.label}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
                Text("HP ${b.hp.toInt()}/${b.def.hp}", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodySmall)
                if (b.def.trains.isNotEmpty() && b.factionId == 0 && b.built) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (unitType in b.def.trains) {
                            val def = GameConfig.UNITS[unitType] ?: continue
                            PixelButton("Train ${def.label}", onClick = { session.trainUnit(b.id, unitType) })
                        }
                    }
                    b.production?.let {
                        Text("Producing ${it.unitDef.label} ${(it.time / it.total * 100).toInt()}%", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun BuildMenu(session: GameSession, onClose: () -> kotlin.Unit, onSelected: (String) -> kotlin.Unit) {
    val player = session.world.playerFaction()
    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        PixelPanel(title = "Build Menu") {
            for ((type, def) in GameConfig.BUILDINGS) {
                val canAfford = player.resWood >= def.cost.wood && player.resGold >= def.cost.gold && player.resFood >= def.cost.food
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(def.label, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(110.dp))
                    Text("W${def.cost.wood} G${def.cost.gold}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    PixelButton("Place", primary = canAfford, enabled = canAfford, onClick = { onSelected(type) })
                }
            }
            PixelButton("Cancel", onClick = onClose, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun HelpOverlay(onClose: () -> kotlin.Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        PixelPanel(title = "How to play") {
            Text("Tap to select units. Long-press / double-tap on map to issue orders.", color = MaterialTheme.colorScheme.onSurface)
            Text("Pinch to zoom. Drag with one finger to pan the camera.", color = MaterialTheme.colorScheme.onSurface)
            Text("Build menu places foundations - workers build them automatically.", color = MaterialTheme.colorScheme.onSurface)
            Text("Castle / Barracks / Archery / Monastery train units.", color = MaterialTheme.colorScheme.onSurface)
            Text("Towers come with a built-in archer and shoot enemies in range.", color = MaterialTheme.colorScheme.onSurface)
            Text("Hunt deer, hare, fox, grouse, boar for food. Boars hit back.", color = MaterialTheme.colorScheme.onSurface)
            Text("Destroy every enemy castle to win the realm.", color = MaterialTheme.colorScheme.onSurface)
            PixelButton("Close", primary = true, onClick = onClose, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun PauseOverlay(session: GameSession, onResume: () -> kotlin.Unit, onSaveExit: () -> kotlin.Unit) {
    Box(modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xCC000000)).padding(16.dp), contentAlignment = Alignment.Center) {
        PixelPanel(title = "Paused") {
            Text("Realm paused. Take a breath.", color = MaterialTheme.colorScheme.onSurface)
            PixelButton("Resume", primary = true, onClick = onResume, modifier = Modifier.fillMaxWidth())
            PixelButton("Save & Exit", onClick = onSaveExit, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Minimap(
    session: GameSession,
    modifier: Modifier,
    onTap: (Float, Float) -> kotlin.Unit,
    onClose: () -> kotlin.Unit
) {
    PixelPanel(modifier = modifier) {
        Row {
            Text("MINIMAP", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.weight(1f))
            PixelButton("X", onClick = onClose)
        }
        Box(modifier = Modifier.fillMaxWidth().height(110.dp).pointerInput(session) {
            detectTapGestures { offset ->
                val world = session.world
                val mw = size.width.toFloat()
                val mh = size.height.toFloat()
                val wx = offset.x / mw * world.width
                val wy = offset.y / mh * world.height
                onTap(wx, wy)
            }
        }) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val world = session.world
                val mw = size.width
                val mh = size.height
                // Land
                val sx = mw / world.width
                val sy = mh / world.height
                // Water bg
                drawRect(androidx.compose.ui.graphics.Color(0xFF264F78), size = size)
                // Sample land grid - downsample to 2x for speed
                val cols = world.cols
                val rows = world.rows
                val sxT = mw / cols
                val syT = mh / rows
                for (ty in 0 until rows step 2) for (tx in 0 until cols step 2) {
                    if (world.landMap[ty * cols + tx].toInt() == 0) continue
                    drawRect(
                        color = androidx.compose.ui.graphics.Color(0xFF69A44F),
                        topLeft = androidx.compose.ui.geometry.Offset(tx * sxT, ty * syT),
                        size = androidx.compose.ui.geometry.Size(sxT * 2f, syT * 2f)
                    )
                }
                // Draw factions / units
                for (b in world.buildings) {
                    val color = androidx.compose.ui.graphics.Color(GameConfig.FACTIONS[b.factionId].color)
                    drawRect(
                        color = color,
                        topLeft = androidx.compose.ui.geometry.Offset(b.x * sx - 1.5f, b.y * sy - 1.5f),
                        size = androidx.compose.ui.geometry.Size(3f, 3f)
                    )
                }
                for (u in world.units) {
                    val color = androidx.compose.ui.graphics.Color(GameConfig.FACTIONS[u.factionId].color)
                    drawCircle(color = color, radius = 1.4f, center = androidx.compose.ui.geometry.Offset(u.x * sx, u.y * sy))
                }
                // Camera viewport box
                val viewW = (1280f / session.camera.zoom).coerceAtMost(world.width.toFloat())
                val viewH = (720f / session.camera.zoom).coerceAtMost(world.height.toFloat())
                drawRect(
                    color = androidx.compose.ui.graphics.Color.White,
                    topLeft = androidx.compose.ui.geometry.Offset(session.camera.x * sx, session.camera.y * sy),
                    size = androidx.compose.ui.geometry.Size(viewW * sx, viewH * sy),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f)
                )
            }
        }
    }
}
