package com.tinyswords.realmwar.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color as AColor
import android.graphics.Paint
import android.os.SystemClock
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.tinyswords.realmwar.TinySwordsApplication
import com.tinyswords.realmwar.game.BUILDING_DEFS
import com.tinyswords.realmwar.game.Building
import com.tinyswords.realmwar.game.BuildingType
import com.tinyswords.realmwar.game.GameEngine
import com.tinyswords.realmwar.game.GameState
import com.tinyswords.realmwar.game.ResourceNode
import com.tinyswords.realmwar.game.Stockpile
import com.tinyswords.realmwar.game.UNIT_DEFS
import com.tinyswords.realmwar.game.Unit as GUnit
import com.tinyswords.realmwar.game.WorldGenerator
import com.tinyswords.realmwar.render.MinimapRenderer
import com.tinyswords.realmwar.render.WorldRenderer
import com.tinyswords.realmwar.storage.WorldRecord
import kotlin.math.hypot
import kotlin.math.max

@Composable
fun GameScreen(
    application: TinySwordsApplication,
    world: WorldRecord,
    onExit: () -> Unit,
) {
    // Build engine + state once per (world.id) entry.
    val engine = remember(world.id) {
        val state = GameState(world.settings, world.seed)
        WorldGenerator(state).generate()
        GameEngine(state, application.soundBank).also { eng -> eng.start() }
    }
    DisposableEffect(engine) {
        onDispose { engine.stop() }
    }

    // Persist autosave on world exit.
    val onExitWithSave: () -> Unit = remember(world.id, engine) {
        {
            val updated = world.copy(lastPlayedAtMs = System.currentTimeMillis())
            application.storage.saveWorld(updated)
            onExit()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(TS.PanelDark)) {
        // World canvas
        WorldCanvas(
            engine = engine,
            spriteCache = application.spriteCache,
            modifier = Modifier.fillMaxSize(),
        )
        // HUD overlay
        HudOverlay(
            engine = engine,
            world = world,
            onExit = onExitWithSave,
        )
        // Toast
        val toastValue = engine.toast.value
        if (toastValue != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 110.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                Box(
                    modifier = Modifier
                        .background(Color(0xCC101A28), RoundedCornerShape(6.dp))
                        .border(BorderStroke(2.dp, TS.PanelEdge), RoundedCornerShape(6.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = toastValue,
                        color = TS.Ink,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                    )
                }
            }
        }
        // Pause overlay
        if (engine.paused.value) {
            PauseOverlay(
                onResume = { engine.paused.value = false },
                onExit = onExitWithSave,
            )
        }
    }
}

// ============================================================================
// World canvas (AndroidView wrapping a custom View that drives 60fps redraws)
// ============================================================================

@Composable
private fun WorldCanvas(
    engine: GameEngine,
    spriteCache: com.tinyswords.realmwar.assets.SpriteCache,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    AndroidView(
        modifier = modifier,
        factory = {
            GameSurfaceView(ctx).apply {
                this.engine = engine
                this.renderer = WorldRenderer(spriteCache)
                this.minimap = MinimapRenderer()
                this.layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
        },
    )
}

private class GameSurfaceView(context: Context) : View(context), Choreographer.FrameCallback {
    lateinit var engine: GameEngine
    lateinit var renderer: WorldRenderer
    lateinit var minimap: MinimapRenderer
    private val camera = WorldRenderer.Camera(0f, 0f, 1f)
    private var initialized = false

    // Touch state
    private var lastX = 0f
    private var lastY = 0f
    private var pointerCount = 0
    private var pinchDist = 0f
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var moved = false
    private var downAtMs = 0L
    private var longPressFired = false
    private val longPressThresholdMs = 380L
    private val moveThresholdPx = 14f

    private var attached = false

    init {
        setBackgroundColor(AColor.BLACK)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onDetachedFromWindow() {
        attached = false
        Choreographer.getInstance().removeFrameCallback(this)
        super.onDetachedFromWindow()
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!attached) return
        invalidate()
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onDraw(canvas: Canvas) {
        if (!::engine.isInitialized || !::renderer.isInitialized) return
        val state = engine.state
        if (!initialized && width > 0 && height > 0) {
            // Center on player base.
            val p = state.playerFaction().basePos
            camera.x = p.x - width / 2f / camera.zoom
            camera.y = p.y - height / 2f / camera.zoom
            initialized = true
        }
        clampCamera(state)
        renderer.draw(
            canvas, state, camera, width, height,
            selectedIds = engine.selection.toSet(),
            placingType = engine.placingBuilding.value,
            placingX = if (engine.placingBuilding.value != null) lastX / camera.zoom + camera.x else null,
            placingY = if (engine.placingBuilding.value != null) lastY / camera.zoom + camera.y else null,
        )
        // Minimap (top-right)
        val mmW = (width * 0.22f).toInt().coerceIn(160, 280)
        val mmH = (height * 0.22f).toInt().coerceIn(110, 220)
        val mmX = width - mmW - 16
        val mmY = 16
        canvas.save()
        canvas.translate(mmX.toFloat(), mmY.toFloat())
        // Frame
        val border = Paint().apply { color = AColor.argb(220, 5, 10, 18); style = Paint.Style.FILL }
        canvas.drawRect(-3f, -3f, mmW + 3f, mmH + 3f, border)
        minimap.draw(canvas, state, camera, mmW, mmH, width, height)
        canvas.restore()
    }

    private fun clampCamera(state: GameState) {
        val maxX = (state.worldW - width / camera.zoom).coerceAtLeast(0f)
        val maxY = (state.worldH - height / camera.zoom).coerceAtLeast(0f)
        if (camera.x < 0f) camera.x = 0f
        if (camera.y < 0f) camera.y = 0f
        if (camera.x > maxX) camera.x = maxX
        if (camera.y > maxY) camera.y = maxY
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pointerCount = 1
                lastX = event.x; lastY = event.y
                dragStartX = event.x; dragStartY = event.y
                moved = false
                downAtMs = SystemClock.uptimeMillis()
                longPressFired = false
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                pointerCount = event.pointerCount
                if (pointerCount >= 2) pinchDist = pinchDistance(event)
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount >= 2) {
                    val newDist = pinchDistance(event)
                    if (pinchDist > 0f) {
                        val factor = newDist / pinchDist
                        val cx = (event.getX(0) + event.getX(1)) / 2f
                        val cy = (event.getY(0) + event.getY(1)) / 2f
                        zoomAt(cx, cy, factor)
                    }
                    pinchDist = newDist
                    moved = true
                } else {
                    val dx = event.x - lastX
                    val dy = event.y - lastY
                    if (hypot(event.x - dragStartX, event.y - dragStartY) > moveThresholdPx) {
                        moved = true
                        camera.x -= dx / camera.zoom
                        camera.y -= dy / camera.zoom
                    }
                    lastX = event.x; lastY = event.y
                    if (!longPressFired && !moved &&
                        SystemClock.uptimeMillis() - downAtMs > longPressThresholdMs) {
                        // Long-press: rally point for selected production building or stop
                        longPressFired = true
                        val wx = event.x / camera.zoom + camera.x
                        val wy = event.y / camera.zoom + camera.y
                        val anyBuilding = engine.selectedBuildings().isNotEmpty()
                        if (anyBuilding) engine.orderRally(wx, wy) else engine.orderStop()
                        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                    }
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                pointerCount = event.pointerCount - 1
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!moved && !longPressFired) {
                    val wx = event.x / camera.zoom + camera.x
                    val wy = event.y / camera.zoom + camera.y
                    handleTap(wx, wy)
                }
                pointerCount = 0; pinchDist = 0f
            }
        }
        return true
    }

    private fun pinchDistance(e: MotionEvent): Float {
        val dx = e.getX(0) - e.getX(1)
        val dy = e.getY(0) - e.getY(1)
        return hypot(dx, dy)
    }

    private fun zoomAt(cx: Float, cy: Float, factor: Float) {
        val before = camera.zoom
        val after = (before * factor).coerceIn(0.32f, 2.6f)
        if (after == before) return
        // Keep the world point under finger fixed.
        val worldX = cx / before + camera.x
        val worldY = cy / before + camera.y
        camera.zoom = after
        camera.x = worldX - cx / after
        camera.y = worldY - cy / after
    }

    private fun handleTap(wx: Float, wy: Float) {
        val state = engine.state
        // Building placement intent first.
        val placing = engine.placingBuilding.value
        if (placing != null) {
            engine.tryPlaceBuilding(placing, wx, wy)
            return
        }
        // Pick the nearest entity within forgiving radius.
        val tap = nearestEntity(state, wx, wy, 36f)
        val playerId = state.playerFaction().def.id
        if (tap != null) {
            when (tap) {
                is GUnit -> {
                    if (tap.faction == playerId) {
                        engine.selection.clear(); engine.selection.add(tap.id)
                    } else if (engine.selection.isNotEmpty()) {
                        engine.orderAttack(tap.id)
                    }
                }
                is Building -> {
                    if (tap.faction == playerId) {
                        engine.selection.clear(); engine.selection.add(tap.id)
                    } else if (engine.selection.isNotEmpty()) {
                        engine.orderAttack(tap.id)
                    }
                }
                is ResourceNode -> {
                    if (engine.selection.isNotEmpty()) engine.orderGather(tap.id)
                }
            }
            return
        }
        // Empty tap: if units are selected, move; otherwise clear.
        if (engine.ownedSelection().isNotEmpty()) {
            engine.orderMove(wx, wy)
        } else {
            engine.selection.clear()
        }
    }

    private fun nearestEntity(state: GameState, wx: Float, wy: Float, maxDist: Float):
        com.tinyswords.realmwar.game.Entity? {
        var best: com.tinyswords.realmwar.game.Entity? = null
        var bestD = maxDist
        for (u in state.units) {
            if (u.dead) continue
            val d = hypot(u.x - wx, u.y - wy)
            if (d < bestD) { bestD = d; best = u }
        }
        for (b in state.buildings) {
            if (b.dead) continue
            val def = b.def
            val pad = max(def.placeW, def.placeH) / 2f + 8f
            val d = hypot(b.x - wx, b.y - wy)
            if (d < pad && d < bestD) { bestD = d; best = b }
        }
        for (r in state.resources) {
            if (r.dead) continue
            val d = hypot(r.x - wx, r.y - wy)
            if (d < 28f && d < bestD) { bestD = d; best = r }
        }
        return best
    }
}

// ============================================================================
// HUD overlay (Compose) — top resources, action dock, build menu, sidebar
// ============================================================================

@Composable
private fun HudOverlay(
    engine: GameEngine,
    world: WorldRecord,
    onExit: () -> Unit,
) {
    // Force recomposition on tick — simple but effective.
    val tick = engine.tick.value
    val state = engine.state
    val player = state.playerFaction()

    Column(modifier = Modifier.fillMaxSize()) {
        // Top resource strip
        TopBar(player.res, player.popUsed, player.popCap, world.name, engine, onExit)

        // Spacer fills middle (the canvas under HUD)
        Spacer(modifier = Modifier.weight(1f))

        // Bottom dock: selection / action panel + build button
        BottomDock(engine, tick.toLong())
    }
}

@Composable
private fun TopBar(
    res: Stockpile,
    popUsed: Int,
    popCap: Int,
    worldName: String,
    engine: GameEngine,
    onExit: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xCC101A28))
            .border(BorderStroke(0.dp, TS.PanelEdge))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ResourceChip("WOOD", res.wood, Color(0xFF9CCB77))
        ResourceChip("GOLD", res.gold, Color(0xFFE8C557))
        ResourceChip("FOOD", res.food, Color(0xFFF6A167))
        ResourceChip("POP", popUsed, Color(0xFFA8B5C8), max = popCap)
        Spacer(Modifier.weight(1f))
        Text(
            text = worldName.uppercase(),
            color = TS.Ink,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Black,
            fontSize = 14.sp,
            letterSpacing = 1.6.sp,
        )
        Spacer(Modifier.width(8.dp))
        IconChip(text = if (engine.fast.value) ">>" else ">", onClick = {
            engine.fast.value = !engine.fast.value
        })
        IconChip(text = if (engine.paused.value) "P" else "II", onClick = {
            engine.paused.value = !engine.paused.value
        })
        IconChip(text = "X", danger = true, onClick = onExit)
    }
}

@Composable
private fun ResourceChip(label: String, value: Int, color: Color, max: Int? = null) {
    Row(
        modifier = Modifier
            .background(Color(0xAA050A12), RoundedCornerShape(4.dp))
            .border(BorderStroke(1.dp, TS.PanelEdge), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, RoundedCornerShape(2.dp))
                .border(BorderStroke(1.dp, Color.Black), RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            color = color,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            letterSpacing = 1.0.sp,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (max != null) "$value/$max" else "$value",
            color = TS.Ink,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Black,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun IconChip(text: String, danger: Boolean = false, onClick: () -> Unit) {
    val bg = if (danger) Color(0xFFCD4444) else TS.PanelTop
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(bg, RoundedCornerShape(4.dp))
            .border(BorderStroke(2.dp, TS.PanelEdge), RoundedCornerShape(4.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (danger) Color.White else TS.Ink,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Black,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun BottomDock(engine: GameEngine, tick: Long) {
    val state = engine.state
    val player = state.playerFaction()
    val selectedUnits = engine.selection.mapNotNull { id -> state.units.firstOrNull { it.id == id && !it.dead } }
        .filter { it.faction == player.def.id }
    val selectedBuildings = engine.selection.mapNotNull { id -> state.buildings.firstOrNull { it.id == id && !it.dead } }
        .filter { it.faction == player.def.id }

    var buildMenuOpen by remember { mutableStateOf(false) }
    val placing = engine.placingBuilding.value

    Column {
        if (buildMenuOpen) {
            BuildMenu(
                player = player,
                onChoose = { type ->
                    engine.placingBuilding.value = type
                    buildMenuOpen = false
                    engine.showToast("Tap on the map to place ${type.displayName}.", 3f)
                },
                onClose = { buildMenuOpen = false },
            )
        }
        if (placing != null) {
            // Cancel placement strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xCC101A28))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Placing ${placing.displayName} — tap map to confirm.",
                    color = TS.Gold,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.weight(1f))
                PixelButton(text = "Cancel", danger = true, onClick = { engine.placingBuilding.value = null })
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xCC101A28))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Selection / actions panel
            Column(modifier = Modifier.weight(1f)) {
                when {
                    selectedBuildings.isNotEmpty() -> BuildingActions(engine, selectedBuildings.first())
                    selectedUnits.isNotEmpty() -> UnitActions(engine, selectedUnits)
                    else -> {
                        Text(
                            text = "Tap a unit, building, or resource. Long-press for stop / rally.",
                            color = TS.Mute,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
            // Build entry
            PixelButton(
                text = "Build",
                primary = true,
                onClick = { buildMenuOpen = !buildMenuOpen },
            )
            PixelButton(
                text = "Stop",
                onClick = { engine.orderStop() },
            )
        }
    }
}

@Composable
private fun BuildMenu(
    player: com.tinyswords.realmwar.game.Faction,
    onChoose: (BuildingType) -> Unit,
    onClose: () -> Unit,
) {
    val items = listOf(
        BuildingType.HOUSE,
        BuildingType.BARRACKS,
        BuildingType.ARCHERY,
        BuildingType.TOWER,
        BuildingType.MONASTERY,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xEE101A28))
            .border(BorderStroke(1.dp, TS.PanelEdge))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "BUILD",
            color = TS.Gold,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Black,
            fontSize = 14.sp,
            letterSpacing = 1.5.sp,
        )
        items.forEach { type ->
            val def = BUILDING_DEFS[type]!!
            val canAfford = player.res.canAfford(def.cost)
            BuildCard(
                title = type.displayName,
                cost = def.cost,
                enabled = canAfford,
                onClick = { onChoose(type) },
            )
        }
        Spacer(Modifier.weight(1f))
        PixelButton(text = "Close", onClick = onClose)
    }
}

@Composable
private fun BuildCard(
    title: String,
    cost: Stockpile,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (enabled) TS.PanelTop else TS.PanelMid, RoundedCornerShape(4.dp))
            .border(BorderStroke(2.dp, TS.PanelEdge), RoundedCornerShape(4.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .widthIn(min = 92.dp),
    ) {
        Text(
            text = title.uppercase(),
            color = if (enabled) TS.Ink else TS.Mute,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Black,
            fontSize = 11.sp,
            letterSpacing = 0.8.sp,
        )
        Text(
            text = costText(cost),
            color = if (enabled) TS.Gold else TS.Mute.copy(alpha = 0.6f),
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
        )
    }
}

private fun costText(c: Stockpile): String = buildString {
    if (c.wood > 0) append("W${c.wood} ")
    if (c.gold > 0) append("G${c.gold} ")
    if (c.food > 0) append("F${c.food}")
}.trim().ifBlank { "free" }

@Composable
private fun UnitActions(engine: GameEngine, units: List<GUnit>) {
    val grouped = units.groupingBy { it.type }.eachCount()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Selected: " + grouped.entries.joinToString(", ") { "${it.value}× ${it.key.displayName}" },
            color = TS.Ink,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Black,
            fontSize = 12.sp,
        )
        val first = units.first()
        Text(
            text = "HP avg ${units.sumOf { it.hp.toInt() } / units.size}/${first.maxHp.toInt()}  " +
                "Spd ${first.def.speedPx.toInt()}  Dmg ${first.def.damage}  Rng ${first.def.rangePx.toInt()}",
            color = TS.Mute,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun BuildingActions(engine: GameEngine, building: Building) {
    val def = building.def
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = def.type.displayName.uppercase(),
                color = TS.Gold,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
                letterSpacing = 1.2.sp,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "HP ${building.hp.toInt()}/${building.maxHp.toInt()}",
                color = TS.Mute,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
        }
        if (def.trains.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                def.trains.forEach { t ->
                    val ud = UNIT_DEFS[t]!!
                    val player = engine.state.playerFaction()
                    val canAfford = player.res.canAfford(ud.cost) &&
                        player.popUsed + ud.popCost <= player.popCap
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (canAfford) TS.PanelTop else TS.PanelMid, RoundedCornerShape(4.dp))
                            .border(BorderStroke(2.dp, TS.PanelEdge), RoundedCornerShape(4.dp))
                            .clickable(enabled = canAfford) { engine.queueTraining(building.id, t) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = t.displayName.uppercase(),
                            color = if (canAfford) TS.Ink else TS.Mute,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp,
                        )
                        Text(
                            text = costText(ud.cost),
                            color = if (canAfford) TS.Gold else TS.Mute.copy(alpha = 0.6f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                        )
                    }
                }
            }
            building.trainingType?.let { t ->
                val td = UNIT_DEFS[t]!!
                val pct = 1f - (building.trainingTimeLeft / td.buildTimeSec).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(8.dp)
                        .background(TS.PanelDark, RoundedCornerShape(2.dp))
                        .border(BorderStroke(1.dp, TS.PanelEdge), RoundedCornerShape(2.dp)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(pct)
                            .background(TS.Gold, RoundedCornerShape(2.dp)),
                    )
                }
            }
        }
    }
}

@Composable
private fun PauseOverlay(onResume: () -> Unit, onExit: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xC0050A12)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = pixelPanelModifier(PaddingValues(28.dp)),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "PAUSED",
                color = TS.Gold,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                fontSize = 32.sp,
                letterSpacing = 4.sp,
            )
            PixelButton(text = "Resume", primary = true, onClick = onResume, modifier = Modifier.widthIn(min = 220.dp))
            PixelButton(text = "Save & Exit", danger = true, onClick = onExit, modifier = Modifier.widthIn(min = 220.dp))
        }
    }
}
