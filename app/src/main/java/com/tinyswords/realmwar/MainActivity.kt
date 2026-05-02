package com.tinyswords.realmwar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.android.awaitFrame
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent { TinySwordsApp() }
    }
}

@Composable
fun TinySwordsApp() {
    val context = LocalContext.current
    val engine = remember { TinySwordsEngine(context) }
    var tick by remember { mutableIntStateOf(0) }
    var screen by remember { mutableStateOf("menu") }

    LaunchedEffect(screen) {
        var previous = awaitFrame()
        while (true) {
            val now = awaitFrame()
            if (screen == "game") engine.update((now - previous) / 1_000_000_000f)
            previous = now
            tick++
        }
    }

    DisposableEffect(Unit) { onDispose { } }

    Box(Modifier.fillMaxSize().background(Color(0xff111927))) {
        if (screen == "menu") {
            TitleScreen(engine = engine, onStart = { screen = "game" })
        } else {
            GameScreen(engine = engine, tick = tick, onMenu = { screen = "menu" })
        }
    }
}

@Composable
private fun TitleScreen(engine: TinySwordsEngine, onStart: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Color(0xff162338))
            val tile = 80f
            var y = 0f
            while (y < size.height) {
                var x = 0f
                while (x < size.width) {
                    val c = if (((x / tile).toInt() + (y / tile).toInt()) % 2 == 0) Color(0xff203a35) else Color(0xff1a3140)
                    drawRect(c, Offset(x, y), Size(tile, tile))
                    x += tile
                }
                y += tile
            }
            drawCircle(Color(0xff244e54), radius = size.minDimension * .38f, center = Offset(size.width * .5f, size.height * .58f))
            drawCircle(Color(0xff3f7a47), radius = size.minDimension * .30f, center = Offset(size.width * .5f, size.height * .60f))
        }
        Column(
            modifier = Modifier.align(Alignment.Center).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("TINY SWORDS", color = Color(0xffffe48a), fontSize = 44.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
            Text("REALM WAR", color = Color(0xffffffff), fontSize = 25.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(20.dp))
            PixelButton("Single Player", onStart)
            Spacer(Modifier.height(10.dp))
            PixelButton("Continue Realm", onStart)
            Spacer(Modifier.height(10.dp))
            Text("Native Kotlin + Compose RTS rebuild • no WebView", color = Color(0xffc7d5e8), fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        }
        Row(Modifier.align(Alignment.BottomCenter).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniStat("Wood", engine.playerStock().wood.toString(), Color(0xff9ccb77))
            MiniStat("Gold", engine.playerStock().gold.toString(), Color(0xfff7dc62))
            MiniStat("Food", engine.playerStock().food.toString(), Color(0xfff6a167))
        }
    }
}

@Composable
private fun GameScreen(engine: TinySwordsEngine, tick: Int, onMenu: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xff111927))) {
        GameCanvas(engine, tick)
        TopHud(engine, Modifier.align(Alignment.TopCenter), onMenu)
        SidePanel(engine, Modifier.align(Alignment.CenterEnd))
        CommandBar(engine, Modifier.align(Alignment.BottomCenter))
        Minimap(engine, Modifier.align(Alignment.BottomStart).padding(12.dp))
        if (engine.toastTimer > 0f) {
            Text(
                engine.toast,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 58.dp).background(Color(0xdd182133), RoundedCornerShape(2.dp)).border(2.dp, Color(0xff8c6b3f)).padding(horizontal = 14.dp, vertical = 8.dp),
                color = Color(0xfffff2b4),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun GameCanvas(engine: TinySwordsEngine, tick: Int) {
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragEnd by remember { mutableStateOf<Offset?>(null) }
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(engine) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val start = down.position
                    var last = start
                    var moved = false
                    var selectDrag = false
                    dragStart = null
                    dragEnd = null
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.changedToUpIgnoreConsumed()) break
                        val delta = change.positionChange()
                        if (delta != Offset.Zero) {
                            last = change.position
                            if ((last - start).getDistance() > 12f) moved = true
                            if (moved) {
                                if (engine.selected.filterIsInstance<UnitEntity>().isNotEmpty()) {
                                    selectDrag = true
                                    dragStart = start
                                    dragEnd = last
                                } else {
                                    engine.pan(delta.x, delta.y)
                                }
                                change.consume()
                            }
                        }
                    }
                    val width = size.width.toFloat()
                    val height = size.height.toFloat()
                    if (!moved) engine.handleTap(start.x, start.y, width, height)
                    else if (selectDrag) engine.dragSelect(start.x, start.y, last.x, last.y, width, height)
                    dragStart = null
                    dragEnd = null
                }
            }
    ) {
        drawGameWorld(engine)
        val a = dragStart
        val b = dragEnd
        if (a != null && b != null) {
            val left = min(a.x, b.x)
            val top = min(a.y, b.y)
            drawRect(Color(0x335fcdf0), Offset(left, top), Size(abs(a.x - b.x), abs(a.y - b.y)))
            drawRect(Color(0xfff5d37d), Offset(left, top), Size(abs(a.x - b.x), abs(a.y - b.y)), style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
        }
    }
}

private fun DrawScope.drawGameWorld(engine: TinySwordsEngine) {
    withTransform({ scale(engine.zoom, engine.zoom); translate(-engine.camera.x, -engine.camera.y) }) {
        drawTerrain(engine)
        val drawables = mutableListOf<Pair<Float, () -> Unit>>()
        engine.resources.filter { !it.dead && visible(engine, it.x, it.y, 180f) }.forEach { r -> drawables += r.y to { drawResource(engine, r) } }
        engine.buildings.filter { !it.dead && visible(engine, it.x, it.y, 260f) }.forEach { b -> drawables += (b.y + b.kind.placeYOffset) to { drawBuilding(engine, b) } }
        engine.units.filter { !it.dead && visible(engine, it.x, it.y, 120f) }.forEach { u -> drawables += u.y to { drawUnit(engine, u) } }
        drawables.sortedBy { it.first }.forEach { it.second() }
        engine.projectiles.forEach { drawProjectile(engine, it) }
        engine.effects.forEach { drawEffect(it) }
        engine.placing?.let { drawPlacementGhost(engine, it) }
    }
}

private fun DrawScope.drawTerrain(engine: TinySwordsEngine) {
    drawRect(Color(0xff26465f), Offset.Zero, Size(WORLD_WIDTH, WORLD_HEIGHT))
    val left = floor(engine.camera.x / TILE_SIZE).toInt().coerceAtLeast(0)
    val top = floor(engine.camera.y / TILE_SIZE).toInt().coerceAtLeast(0)
    val right = min(engine.landCols() - 1, ((engine.camera.x + size.width / engine.zoom) / TILE_SIZE).toInt() + 3)
    val bottom = min(engine.landRows() - 1, ((engine.camera.y + size.height / engine.zoom) / TILE_SIZE).toInt() + 3)
    for (ty in top..bottom) for (tx in left..right) {
        val x = tx * TILE_SIZE
        val y = ty * TILE_SIZE
        if (engine.landAt(tx, ty)) {
            val tint = when ((tx * 17 + ty * 11) % 5) {
                0 -> Color(0xff69a85d)
                1 -> Color(0xff75b665)
                2 -> Color(0xff5f9d55)
                else -> Color(0xff6cae62)
            }
            drawRect(tint, Offset(x, y), Size(TILE_SIZE + 1f, TILE_SIZE + 1f))
            if (!engine.landAt(tx, ty - 1)) drawRect(Color(0xffd7c078), Offset(x, y), Size(TILE_SIZE, 8f))
            if (!engine.landAt(tx, ty + 1)) drawRect(Color(0xff3c7e56), Offset(x, y + TILE_SIZE - 8f), Size(TILE_SIZE, 8f))
        } else {
            val foam = if ((tx + ty) % 3 == 0) Color(0x333aa4c9) else Color(0x0026456f)
            drawRect(foam, Offset(x + 6f, y + 6f), Size(TILE_SIZE - 12f, TILE_SIZE - 12f))
        }
    }
}

private fun DrawScope.drawResource(engine: TinySwordsEngine, r: ResourceEntity) {
    drawShadow(r.x, r.y + 4f, r.radius * 1.4f, 6f)
    if (r.animal != null) {
        val moving = abs(r.vx) + abs(r.vy) > 8f
        val anim = if (r.flash > 0f) "hurt" else if (moving) "run" else "idle"
        val image = engine.sprites.animal(r.animal, anim)
        if (image != null) {
            val fw = 32
            val fh = 32
            val frames = max(1, image.width / fw)
            val rows = max(1, image.height / fh)
            val frame = ((engine.time * if (moving) 9f else 3f).toInt() + r.id) % frames
            val row = r.directionRow.coerceIn(0, rows - 1)
            val scale = r.animal.scale
            drawSprite(image, frame * fw, row * fh, fw, fh, r.x, r.y, scale, 28f, r.vx < -1f)
        } else drawCircle(Color(0xffc49a6c), r.radius, Offset(r.x, r.y))
    } else {
        val image = engine.sprites.terrain(r.sprite)
        if (image != null) {
            val fw = if (r.kind == ResourceKind.Wood) 192 else 128
            val fh = if (r.kind == ResourceKind.Wood) 256 else 128
            val frames = max(1, image.width / fw)
            val frame = if (r.kind == ResourceKind.Wood) ((engine.time * 4f).toInt() + r.id) % frames else 0
            val scale = if (r.kind == ResourceKind.Wood) .65f else .65f
            val baseline = if (r.kind == ResourceKind.Wood) 241f else 79f
            drawSprite(image, frame * fw, 0, fw, fh, r.x, r.y, scale, baseline, false)
        } else drawCircle(if (r.kind == ResourceKind.Gold) Color(0xfff7dc62) else Color(0xff27772c), r.radius, Offset(r.x, r.y))
    }
    if (engine.selected.contains(r)) drawSelection(r.x, r.y, r.radius + 9f, Color(0xfff5d37d))
}

private fun DrawScope.drawBuilding(engine: TinySwordsEngine, b: BuildingEntity) {
    drawShadow(b.x, b.y + b.kind.placeYOffset, b.kind.placeW * .48f, b.kind.placeH * .22f)
    val image = engine.sprites.building(b.faction, b.kind, b.spriteVariant)
    if (image != null) {
        val alpha = if (b.build < 1f) .58f + .42f * b.build else 1f
        drawImage(
            image,
            dstOffset = IntOffset((b.x - b.kind.drawW * .5f).toInt(), (b.y - b.kind.drawH + b.kind.placeYOffset * .5f).toInt()),
            dstSize = IntSize(b.kind.drawW.toInt(), b.kind.drawH.toInt()),
            alpha = alpha,
            filterQuality = FilterQuality.None
        )
    } else drawRect(b.faction.color, Offset(b.x - b.kind.drawW / 2, b.y - b.kind.drawH), Size(b.kind.drawW, b.kind.drawH))
    if (engine.selected.contains(b)) drawSelection(b.x, b.y + b.kind.placeYOffset, max(b.kind.placeW, b.kind.placeH) * .55f, Color(0xfff5d37d))
    if (b.hp < b.maxHp || b.build < 1f) drawHp(b.x, b.y - b.kind.drawH - 8f, if (b.build < 1f) b.build else hpPercent(b.hp, b.maxHp), b.faction.color, 54f)
    if (b.queue.isNotEmpty()) drawHp(b.x, b.y + b.kind.placeYOffset + 28f, 1f - b.queue.first().remaining / b.queue.first().kind.trainTime, Color(0xff8ee6ff), 46f)
}

private fun DrawScope.drawUnit(engine: TinySwordsEngine, u: UnitEntity) {
    drawShadow(u.x, u.y + 4f, u.radius * 1.4f, 7f)
    if (u.selected) drawSelection(u.x, u.y, u.radius + 8f, Color(0xfff5d37d))
    val anim = when {
        u.kind == UnitKind.Worker && u.carryKind != null -> "run"
        u.kind == UnitKind.Worker && u.order == Order.Harvest && u.target != null -> "chop"
        u.kind == UnitKind.Worker && (u.order == Order.Build || u.order == Order.Repair) -> "build"
        u.order == Order.Move || u.order == Order.ReturnCargo || u.order == Order.AttackMove -> "run"
        u.order == Order.Attack || u.order == Order.Heal -> "attack"
        else -> "idle"
    }
    val image = engine.sprites.unit(u.faction, u.kind, anim, u.carryKind)
    if (image != null) {
        val frames = max(1, image.width / u.kind.frameW)
        val frame = u.anim.toInt() % frames
        val scale = u.kind.scale * SPRITE_BOOST
        val drawYOffset = if (u.kind == UnitKind.Lancer) 27f else 0f
        drawSprite(image, frame * u.kind.frameW, 0, u.kind.frameW, u.kind.frameH, u.x, u.y + 7f + drawYOffset, scale, u.kind.frameH - 16f, u.face < 0f, if (u.flash > 0f) .72f else 1f)
    } else drawCircle(u.faction.color, u.radius, Offset(u.x, u.y))
    if (u.hp < u.maxHp) drawHp(u.x, u.y - u.kind.frameH * u.kind.scale * SPRITE_BOOST + 12f, hpPercent(u.hp, u.maxHp), u.faction.color, 36f)
}

private fun DrawScope.drawProjectile(engine: TinySwordsEngine, p: Projectile) {
    drawRect(p.faction.color, Offset(p.x - 8f, p.y - 2f), Size(16f, 4f))
}

private fun DrawScope.drawEffect(e: Effect) {
    val t = (e.ttl / e.maxTtl).coerceIn(0f, 1f)
    val color = when (e.kind) {
        "heal" -> Color(0xff8fffd2)
        "boom" -> Color(0xffff9b52)
        else -> Color(0xfffff2a4)
    }
    drawCircle(color.copy(alpha = t), (1f - t) * 28f + 6f, Offset(e.x, e.y))
}

private fun DrawScope.drawPlacementGhost(engine: TinySwordsEngine, kind: BuildingKind) {
    val x = engine.camera.x + size.width / engine.zoom * .5f
    val y = engine.camera.y + size.height / engine.zoom * .5f
    val ok = engine.placementIssue(kind, x, y) == null
    drawRect(if (ok) Color(0x5567e681) else Color(0x55e65353), Offset(x - kind.placeW / 2, y + kind.placeYOffset - kind.placeH / 2), Size(kind.placeW, kind.placeH))
}

private fun DrawScope.drawSprite(image: ImageBitmap, sx: Int, sy: Int, fw: Int, fh: Int, x: Float, baseY: Float, scale: Float, baseline: Float, flip: Boolean, alpha: Float = 1f) {
    val w = fw * scale
    val h = fh * scale
    withTransform({ translate(x, baseY); if (flip) scale(-1f, 1f) }) {
        drawImage(
            image,
            srcOffset = IntOffset(sx, sy),
            srcSize = IntSize(fw, fh),
            dstOffset = IntOffset((-w / 2f).toInt(), (-baseline * scale).toInt()),
            dstSize = IntSize(w.toInt(), h.toInt()),
            alpha = alpha,
            filterQuality = FilterQuality.None
        )
    }
}

private fun DrawScope.drawShadow(x: Float, y: Float, rx: Float, ry: Float) {
    drawOval(Color(0x44000000), Offset(x - rx, y - ry), Size(rx * 2f, ry * 2f))
}

private fun DrawScope.drawSelection(x: Float, y: Float, radius: Float, color: Color) {
    drawOval(color.copy(alpha = .42f), Offset(x - radius, y - radius * .36f), Size(radius * 2f, radius * .72f), style = androidx.compose.ui.graphics.drawscope.Stroke(3f))
}

private fun DrawScope.drawHp(x: Float, y: Float, pct: Float, color: Color, w: Float) {
    drawRect(Color(0xff1a1e27), Offset(x - w / 2, y), Size(w, 7f))
    drawRect(color, Offset(x - w / 2 + 1f, y + 1f), Size((w - 2f) * pct.coerceIn(0f, 1f), 5f))
}

private fun DrawScope.visible(engine: TinySwordsEngine, x: Float, y: Float, pad: Float): Boolean =
    x > engine.camera.x - pad && y > engine.camera.y - pad && x < engine.camera.x + size.width / engine.zoom + pad && y < engine.camera.y + size.height / engine.zoom + pad

@Composable
private fun TopHud(engine: TinySwordsEngine, modifier: Modifier, onMenu: () -> Unit) {
    val pop = engine.population(FactionId.Blue)
    Row(
        modifier = modifier.fillMaxWidth().background(Color(0xdd121a28)).border(2.dp, Color(0xff3b4c65)).padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniStat("Wood", engine.playerStock().wood.toString(), Color(0xff9ccb77))
            MiniStat("Gold", engine.playerStock().gold.toString(), Color(0xfff7dc62))
            MiniStat("Food", engine.playerStock().food.toString(), Color(0xfff6a167))
            MiniStat("Pop", "${pop.used}/${pop.cap}", Color(0xff8ee6ff))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (engine.paused) "PAUSED" else "REALM LIVE", color = if (engine.paused) Color(0xffffb060) else Color(0xffb9f7ba), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            SmallHudButton("−") { engine.adjustZoom(-.1f) }
            SmallHudButton("+") { engine.adjustZoom(.1f) }
            SmallHudButton("Home") { engine.focusHome() }
            SmallHudButton(if (engine.paused) "Play" else "Pause") { engine.paused = !engine.paused }
            SmallHudButton("Menu", onMenu)
        }
    }
}

@Composable
private fun SidePanel(engine: TinySwordsEngine, modifier: Modifier) {
    Column(
        modifier = modifier.padding(10.dp).width(190.dp).background(Color(0xdd172034), RoundedCornerShape(2.dp)).border(2.dp, Color(0xff4c5f75)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        val first = engine.selected.firstOrNull()
        Text("SELECTION", color = Color(0xffffe48a), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 14.sp)
        when (first) {
            is UnitEntity -> {
                Text(if (engine.selected.size > 1) "${engine.selected.size} Units" else first.kind.label, color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                Text("HP ${first.hp.toInt()}/${first.maxHp.toInt()} • ${first.kind.role}", color = Color(0xffc7d5e8), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
            is BuildingEntity -> {
                Text(first.kind.label, color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                Text("HP ${first.hp.toInt()}/${first.maxHp.toInt()} • Build ${(first.build * 100).toInt()}%", color = Color(0xffc7d5e8), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                if (first.queue.isNotEmpty()) Text("Queue: ${first.queue.joinToString { it.kind.label }}", color = Color(0xff8ee6ff), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
            is ResourceEntity -> {
                Text(first.animal?.label ?: first.kind.label, color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                Text("${first.kind.label}: ${first.amount}/${first.maxAmount}", color = Color(0xffc7d5e8), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
            else -> Text("Tap units or buildings. Drag with selected units to box-select; tap ground to move.", color = Color(0xffc7d5e8), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        }
    }
}

@Composable
private fun CommandBar(engine: TinySwordsEngine, modifier: Modifier) {
    Row(
        modifier = modifier.padding(bottom = 10.dp).background(Color(0xdd171f2f), RoundedCornerShape(3.dp)).border(2.dp, Color(0xff6b4d31)).padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val building = engine.selected.filterIsInstance<BuildingEntity>().firstOrNull()
        if (building != null) {
            building.kind.trains.forEach { kind -> CommandButton("${kind.hotkey}\n${kind.label}") { engine.train(kind) } }
        }
        if (engine.selected.filterIsInstance<UnitEntity>().any { it.kind == UnitKind.Worker }) {
            BuildingKind.entries.forEach { kind -> CommandButton("${kind.hotkey}\n${kind.label}") { engine.startPlacing(kind) } }
        }
        if (engine.placing != null) CommandButton("X\nCancel") { engine.cancelPlacing() }
        if (building == null && engine.selected.filterIsInstance<UnitEntity>().none { it.kind == UnitKind.Worker }) {
            Text("Select workers to build • select buildings to train", color = Color(0xffc7d5e8), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Minimap(engine: TinySwordsEngine, modifier: Modifier) {
    Canvas(modifier.size(190.dp, 130.dp).background(Color(0xdd0d1522)).border(2.dp, Color(0xff5e7088))) {
        drawRect(Color(0xff243e59))
        engine.buildings.filter { !it.dead }.forEach { b ->
            drawRect(b.faction.color, Offset(b.x / WORLD_WIDTH * size.width - 2f, b.y / WORLD_HEIGHT * size.height - 2f), Size(4f, 4f))
        }
        engine.units.filter { !it.dead }.forEach { u ->
            drawCircle(u.faction.color, 1.6f, Offset(u.x / WORLD_WIDTH * size.width, u.y / WORLD_HEIGHT * size.height))
        }
        drawRect(Color.White.copy(alpha = .45f), Offset(engine.camera.x / WORLD_WIDTH * size.width, engine.camera.y / WORLD_HEIGHT * size.height), Size(size.width * .12f / engine.zoom, size.height * .12f / engine.zoom), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f))
    }
}

@Composable
private fun MiniStat(label: String, value: String, tint: Color) {
    Row(Modifier.background(Color(0xff1c2636), RoundedCornerShape(2.dp)).border(1.dp, tint.copy(alpha = .75f)).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).background(tint))
        Spacer(Modifier.width(5.dp))
        Text("$label $value", color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PixelButton(text: String, onClick: () -> Unit) {
    Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = Color(0xff316aa8), contentColor = Color.White), shape = RoundedCornerShape(2.dp), modifier = Modifier.width(230.dp).height(48.dp).border(2.dp, Color(0xffffe48a), RoundedCornerShape(2.dp))) {
        Text(text, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun SmallHudButton(text: String, onClick: () -> Unit) {
    Text(text, modifier = Modifier.background(Color(0xff26344b), RoundedCornerShape(2.dp)).border(1.dp, Color(0xff7e91ad)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 5.dp), color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun CommandButton(text: String, onClick: () -> Unit) {
    Text(text, modifier = Modifier.size(72.dp, 46.dp).background(Color(0xff27364b), RoundedCornerShape(2.dp)).border(2.dp, Color(0xff9a7449)).clickable(onClick = onClick).padding(5.dp), color = Color(0xfffff2b4), fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
}
