package com.tinyswords.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import com.tinyswords.audio.SoundBank
import com.tinyswords.game.*
import com.tinyswords.input.TouchHandler
import com.tinyswords.input.gameInput
import com.tinyswords.render.GameRenderer
import com.tinyswords.render.SpriteCache
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Hosts the live game: world Canvas + HUD overlay. The simulation ticks on a 60Hz coroutine
 * loop, the renderer reads the [Game] state directly each Compose frame.
 *
 * Re-composition is driven by [Game.revision] — bumping it from the simulator forces Compose
 * to redraw without us needing to copy state into snapshots.
 */
@Composable
fun GameScreen(seed: String, onExitToMenu: () -> kotlin.Unit) {
    val ctx = LocalContext.current
    val sprites = remember { SpriteCache(ctx) }
    val sounds = remember { SoundBank(ctx) }
    val game = remember(seed) { Game(seed) }
    val sim = remember(seed) { Simulation(game) }
    val renderer = remember { GameRenderer(sprites) }
    val handler = remember(seed) {
        TouchHandler(game, sim) { type, wx, wy ->
            tryPlaceBuilding(game, type, wx, wy)
        }
    }
    var buildMenuOpen by remember { mutableStateOf(false) }
    var showVictory by remember { mutableStateOf(false) }

    // 60 Hz fixed-step game loop. Coroutine survives recomposition because [game] is keyed.
    LaunchedEffect(seed) {
        var lastNs = System.nanoTime()
        while (isActive) {
            val now = System.nanoTime()
            val dt = ((now - lastNs) / 1_000_000_000.0).toFloat()
            lastNs = now
            sim.step(dt)
            if (game.gameOver != -1) showVictory = true
            delay(16)
        }
    }
    DisposableEffect(Unit) { onDispose { sounds.release() } }

    val rev = game.revision

    Box(Modifier.fillMaxSize().background(Color(0xFF0F172A))) {
        Canvas(
            Modifier
                .fillMaxSize()
                .gameInput(handler)
        ) {
            // Force re-read of the revision flag so Compose schedules redraws.
            @Suppress("UNUSED_VARIABLE")
            val r = rev
            handler.canvasW = size.width; handler.canvasH = size.height
            renderer.draw(this, game, size.width, size.height)
        }

        // ---- Top bar: resources / pop ----
        Box(Modifier.align(Alignment.TopStart).padding(8.dp)) {
            TopBar(game.factions[game.playerFaction])
        }

        // ---- State readout (top-right) ----
        Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
            StateReadout(
                paused = game.paused,
                onPause = { game.paused = !game.paused; game.bumpRevision() },
                onSave = {
                    com.tinyswords.data.SaveLoad.save(ctx, game, seed)
                },
                onMenu = onExitToMenu
            )
        }

        // ---- Minimap (bottom-right) ----
        Box(Modifier.align(Alignment.BottomEnd).padding(8.dp)) {
            Minimap(game)
        }

        // ---- Selection panel (bottom-left) ----
        Box(Modifier.align(Alignment.BottomStart).padding(8.dp)) {
            SelectionPanel(game, sim)
        }

        // ---- Action dock (bottom-center) ----
        Column(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (buildMenuOpen) {
                BuildMenu(
                    faction = game.factions[game.playerFaction],
                    current = handler.placing,
                    onPick = { handler.placing = it },
                    onClose = { buildMenuOpen = false; handler.placing = null }
                )
                Spacer(Modifier.height(6.dp))
            }
            if (handler.placing != null) {
                Text("Tap to place ${handler.placing!!.display}", style = Type.Small.copy(color = Palette.Gold))
                Spacer(Modifier.height(4.dp))
            }
            ActionDock(
                game, sim,
                onOpenBuild = { buildMenuOpen = !buildMenuOpen },
                onAttackMove = {
                    val cx = game.camera.x + game.camera.viewW / game.camera.zoom * 0.5f
                    val cy = game.camera.y + game.camera.viewH / game.camera.zoom * 0.5f
                    game.selection.mapNotNull { game.findEntity(it) }
                        .filterIsInstance<GameUnit>()
                        .filter { it.faction == game.playerFaction }
                        .forEach { sim.issueAttackMove(it, cx, cy) }
                },
                onStop = {
                    game.selection.mapNotNull { game.findEntity(it) }
                        .filterIsInstance<GameUnit>()
                        .filter { it.faction == game.playerFaction }
                        .forEach { sim.issueStop(it) }
                }
            )
        }

        if (showVictory) {
            VictoryOverlay(game.gameOver, onMenu = onExitToMenu, onDismiss = { showVictory = false })
        }
    }
}

@Composable
private fun VictoryOverlay(winner: Int, onMenu: () -> kotlin.Unit, onDismiss: () -> kotlin.Unit) {
    Box(
        Modifier.fillMaxSize().background(Color(0xC8000000)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val msg = if (winner == 0) "Victory" else "Defeat"
            Text(msg, style = Type.Display)
            Spacer(Modifier.height(12.dp))
            MenuButton("Menu", onMenu)
            Spacer(Modifier.height(6.dp))
            MenuButton("Dismiss", onDismiss)
        }
    }
}

/** Try to place a building for the player at world (wx, wy). Validates land + cost + overlap. */
private fun tryPlaceBuilding(game: Game, type: BuildingType, wx: Float, wy: Float): Boolean {
    if (!game.world.isLandPx(wx, wy)) return false
    val f = game.factions[game.playerFaction]
    if (f.wood < type.costWood || f.gold < type.costGold) return false
    val overlap = game.buildings.any { b ->
        val dx = b.x - wx; val dy = b.y - wy
        kotlin.math.abs(dx) < (b.w + type.width) * 0.55f &&
            kotlin.math.abs(dy) < (b.h + type.height) * 0.55f
    }
    if (overlap) return false
    f.wood -= type.costWood; f.gold -= type.costGold
    val site = game.createBuilding(type, wx, wy, game.playerFaction, complete = false)
    // Send the nearest worker to build it.
    val worker = game.units
        .filter { it.faction == game.playerFaction && it.type == UnitType.WORKER }
        .minByOrNull { (it.x - wx).let { d -> d * d } + (it.y - wy).let { d -> d * d } }
    if (worker != null) {
        worker.order = Order.BUILD; worker.targetId = site.id; worker.path.clear()
    }
    return true
}
