package com.tinyswords.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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

@Composable
fun GameScreen(
    worldSettings: WorldSettings,
    worldId: String?,
    saveSystem: SaveSystem,
    soundBank: SoundBank,
    onExit: () -> Unit
) {
    val context = LocalContext.current

    // Game state initialization - must be done synchronously before rendering starts
    val gameState = remember { GameState(worldSettings) }
    val simulation = remember {
        GameSimulation(gameState).also { sim ->
            // Try to load existing save
            val loaded = if (worldId != null) {
                val saveLoaded = saveSystem.loadGame(worldId, gameState)
                if (saveLoaded) {
                    // Regenerate terrain deterministically from seed
                    sim.worldGenerator.generate()
                    true
                } else false
            } else false
            if (!loaded) {
                sim.initialize()
            }
        }
    }
    val assetManager = remember { AssetManager(context).also { it.preload() } }
    val renderer = remember { GameRenderer(assetManager) }

    // UI state
    var selectionVersion by remember { mutableStateOf(0) }
    var isPaused by remember { mutableStateOf(false) }
    var showBuildMenu by remember { mutableStateOf(false) }
    var gameOverWinner by remember { mutableStateOf(-1) }
    var isGameOver by remember { mutableStateOf(false) }
    var volume by remember { mutableStateOf(saveSystem.loadGlobalSettings().volume) }

    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            assetManager.destroy()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Game View (SurfaceView)
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
                )
            }
        )

        // HUD Overlays
        if (!isGameOver) {
            // Top Resource Bar
            val (popUsed, popCap) = gameState.population(0)
            val faction = gameState.factions[0]
            ResourceBar(
                wood = faction.wood.toInt(),
                gold = faction.gold.toInt(),
                food = faction.food.toInt(),
                popUsed = popUsed,
                popCap = popCap,
                modifier = Modifier.align(Alignment.TopStart)
            )

            // Selection Panel (left side)
            val currentSelection = remember(selectionVersion) { gameState.selected.toList() }
            SelectionPanel(
                selected = currentSelection,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 8.dp)
            )

            // Action Dock (bottom center)
            if (currentSelection.isNotEmpty()) {
                ActionDock(
                    selected = currentSelection,
                    formationMode = gameState.formationMode,
                    onMove = { /* handled by tap */ },
                    onAttackMove = {
                        // Toggle attack-move mode for next tap
                    },
                    onStop = {
                        val units = currentSelection.filterIsInstance<GameUnit>().filter { it.faction == 0 }
                        simulation.orderStop(units)
                    },
                    onHold = {
                        val units = currentSelection.filterIsInstance<GameUnit>().filter { it.faction == 0 }
                        simulation.orderHold(units)
                    },
                    onFormation = { mode -> simulation.setFormation(mode) },
                    onBuildMenu = { showBuildMenu = !showBuildMenu },
                    onTrain = { unitType ->
                        val building = currentSelection.firstOrNull() as? GameBuilding
                        if (building != null) {
                            simulation.economy.trainUnit(building, unitType)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp)
                )
            }

            // Build Menu
            if (showBuildMenu) {
                BuildMenu(
                    faction = faction,
                    onBuild = { type ->
                        gameState.placingBuilding = type
                        showBuildMenu = false
                    },
                    onClose = { showBuildMenu = false },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 60.dp)
                )
            }

            // Pause button (top right)
            CommandButton(
                text = "||",
                onClick = {
                    isPaused = true
                    gameState.paused = true
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            )
        }

        // Pause Overlay
        if (isPaused) {
            PauseOverlay(
                onResume = {
                    isPaused = false
                    gameState.paused = false
                },
                onSaveAndExit = {
                    if (worldId != null) {
                        saveSystem.saveGame(worldId, gameState)
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

        // Game Over Overlay
        if (isGameOver) {
            GameOverOverlay(
                winner = gameOverWinner,
                isPlayerWin = gameOverWinner == 0,
                onNewGame = {
                    isGameOver = false
                    // Reset would need to re-create
                    onExit()
                },
                onExit = onExit
            )
        }
    }
}
