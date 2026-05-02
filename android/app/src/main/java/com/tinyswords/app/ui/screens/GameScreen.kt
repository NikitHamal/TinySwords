package com.tinyswords.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.tinyswords.app.audio.SoundBank
import com.tinyswords.app.data.SaveSystem
import com.tinyswords.app.engine.*
import com.tinyswords.app.game.*
import com.tinyswords.app.game.entities.*
import com.tinyswords.app.ui.components.*
import kotlinx.coroutines.delay

@Composable
fun GameScreen(
    worldSettings: WorldSettings,
    worldId: String?,
    saveSystem: SaveSystem,
    soundBank: SoundBank,
    onExit: () -> Unit
) {
    val context = LocalContext.current

    val gameState = remember(worldId, worldSettings.seed) { GameState(worldSettings) }
    val simulation = remember(worldId, worldSettings.seed) {
        GameSimulation(gameState).also { sim ->
            // Build deterministic terrain/decor first, then overlay saved gameplay
            // entities. The previous order loaded entities and then regenerated a
            // fresh world on top of them, which made Load Game appear broken.
            sim.initialize()
            if (worldId != null) {
                val loaded = saveSystem.loadGame(worldId, gameState)
                if (!loaded) saveSystem.saveGame(worldId, gameState)
            }
        }
    }
    val assetManager = remember(context) { AssetManager(context).also { it.preload() } }
    val renderer = remember(assetManager) { GameRenderer(assetManager) }

    var gameView by remember { mutableStateOf<GameView?>(null) }
    var selectionVersion by remember { mutableStateOf(0) }
    var uiTick by remember { mutableStateOf(0) }
    var isPaused by remember { mutableStateOf(false) }
    var showBuildMenu by remember { mutableStateOf(false) }
    var gameOverWinner by remember { mutableStateOf(-1) }
    var isGameOver by remember { mutableStateOf(false) }
    var volume by remember { mutableStateOf(saveSystem.loadGlobalSettings().volume) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(250L)
            uiTick++
        }
    }

    LaunchedEffect(worldId) {
        val id = worldId ?: return@LaunchedEffect
        while (true) {
            delay(15000L)
            val autosave = saveSystem.loadGlobalSettings().autosave && gameState.settings.autosave
            if (autosave && !gameState.paused && !gameState.gameOver) {
                synchronized(gameState) { saveSystem.saveGame(id, gameState) }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (worldId != null) {
                synchronized(gameState) { saveSystem.saveGame(worldId, gameState) }
            }
            gameView?.destroy()
            renderer.destroy()
            assetManager.destroy()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                GameView(
                    context = ctx,
                    simulation = simulation,
                    renderer = renderer,
                    onSelectionChanged = { selectionVersion++ },
                    onGameOver = { winner ->
                        gameOverWinner = winner
                        isGameOver = true
                    }
                ).also { gameView = it }
            }
        )

        if (!isGameOver) {
            val (wood, gold, food, popUsed, popCap) = remember(uiTick) {
                synchronized(gameState) {
                    val faction = gameState.factions[0]
                    val pop = gameState.population(0)
                    Quint(faction.wood.toInt(), faction.gold.toInt(), faction.food.toInt(), pop.first, pop.second)
                }
            }
            val faction = gameState.factions[0]

            ResourceBar(
                wood = wood,
                gold = gold,
                food = food,
                popUsed = popUsed,
                popCap = popCap,
                modifier = Modifier.align(Alignment.TopStart)
            )

            val currentSelection = remember(selectionVersion, uiTick) {
                synchronized(gameState) { gameState.selected.toList() }
            }
            SelectionPanel(
                selected = currentSelection,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 8.dp)
            )

            QuickControlPanel(
                onWorkers = { gameView?.selectAllWorkers() },
                onArmy = { gameView?.selectAllMilitary() },
                onAll = { gameView?.selectAllUnits() },
                onHome = { gameView?.focusPlayerBase() },
                onCancel = {
                    showBuildMenu = false
                    gameView?.cancelPlacement()
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 82.dp)
            )

            if (currentSelection.isNotEmpty()) {
                val formationMode = remember(uiTick) { synchronized(gameState) { gameState.formationMode } }
                ActionDock(
                    selected = currentSelection,
                    formationMode = formationMode,
                    onMove = { },
                    onAttackMove = { gameView?.armAttackMove() },
                    onStop = {
                        gameView?.runCommand {
                            val units = gameState.selected.filterIsInstance<GameUnit>().filter { it.faction == 0 }
                            simulation.orderStop(units)
                        }
                    },
                    onHold = {
                        gameView?.runCommand {
                            val units = gameState.selected.filterIsInstance<GameUnit>().filter { it.faction == 0 }
                            simulation.orderHold(units)
                        }
                    },
                    onFormation = { mode -> gameView?.runCommand { simulation.setFormation(mode) } },
                    onBuildMenu = { showBuildMenu = !showBuildMenu },
                    onTrain = { unitType ->
                        gameView?.runCommand {
                            val building = gameState.selected.firstOrNull() as? GameBuilding
                            if (building != null) simulation.economy.trainUnit(building, unitType)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp)
                )
            }

            if (showBuildMenu) {
                BuildMenu(
                    faction = faction,
                    onBuild = { type ->
                        gameView?.runCommand { gameState.placingBuilding = type }
                        showBuildMenu = false
                    },
                    onClose = { showBuildMenu = false },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 64.dp)
                )
            }

            CommandButton(
                text = "||",
                onClick = {
                    isPaused = true
                    gameView?.pause()
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            )
        }

        if (isPaused) {
            PauseOverlay(
                onResume = {
                    isPaused = false
                    gameView?.resume()
                },
                onSaveAndExit = {
                    if (worldId != null) {
                        synchronized(gameState) { saveSystem.saveGame(worldId, gameState) }
                    }
                    onExit()
                },
                volume = volume,
                onVolumeChange = { v ->
                    volume = v
                    soundBank.setVolume(v)
                    val settings = saveSystem.loadGlobalSettings()
                    settings.volume = v
                    saveSystem.saveGlobalSettings(settings)
                }
            )
        }

        if (isGameOver) {
            GameOverOverlay(
                winner = gameOverWinner,
                isPlayerWin = gameOverWinner == 0,
                onNewGame = {
                    isGameOver = false
                    onExit()
                },
                onExit = onExit
            )
        }
    }
}

private data class Quint(
    val first: Int,
    val second: Int,
    val third: Int,
    val fourth: Int,
    val fifth: Int
)
