package com.tinyswords.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.tinyswords.app.audio.SoundBank
import com.tinyswords.app.data.SaveSystem
import com.tinyswords.app.engine.*
import com.tinyswords.app.game.*
import com.tinyswords.app.game.entities.*
import com.tinyswords.app.ui.components.*
import com.tinyswords.app.ui.theme.GameColors
import com.tinyswords.app.ui.theme.GameTypography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private data class GameBundle(
    val gameState: GameState,
    val simulation: GameSimulation,
    val assetManager: AssetManager,
    val renderer: GameGlRenderer
)

@Composable
fun GameScreen(
    worldSettings: WorldSettings,
    worldId: String?,
    saveSystem: SaveSystem,
    soundBank: SoundBank,
    onExit: () -> Unit
) {
    val appContext = LocalContext.current.applicationContext
    val loadKey = "${worldId ?: "unsaved"}:${worldSettings.seed}:${worldSettings.size}:${worldSettings.mapStyle}"
    var bundle by remember(loadKey) { mutableStateOf<GameBundle?>(null) }
    var loadingStage by remember(loadKey) { mutableStateOf("Preparing realm...") }
    var loadingProgress by remember(loadKey) { mutableStateOf(0.05f) }

    LaunchedEffect(loadKey) {
        bundle = null
        loadingStage = "Reading world settings"
        loadingProgress = 0.10f
        val gameState = GameState(worldSettings)
        val simulation = GameSimulation(gameState)

        loadingStage = "Generating terrain and paths"
        loadingProgress = 0.30f
        withContext(Dispatchers.Default) { simulation.initialize() }

        if (worldId != null) {
            loadingStage = "Loading saved armies and economy"
            loadingProgress = 0.55f
            val loaded = withContext(Dispatchers.IO) { saveSystem.loadGame(worldId, gameState) }
            if (!loaded) withContext(Dispatchers.IO) { saveSystem.saveGame(worldId, gameState) }
            withContext(Dispatchers.Default) { gameState.rebuildSpatialIndices() }
        }

        loadingStage = "Warming pixel art assets"
        loadingProgress = 0.76f
        val assets = withContext(Dispatchers.Default) { AssetManager(appContext).also { it.preload() } }

        loadingStage = "Opening battlefield"
        loadingProgress = 0.94f
        val renderer = GameGlRenderer(assets)
        bundle = GameBundle(gameState, simulation, assets, renderer)
    }

    val loadedBundle = bundle
    if (loadedBundle == null) {
        RealmLoadingScreen(stage = loadingStage, progress = loadingProgress, onCancel = onExit)
        return
    }

    ActiveGameScreen(
        bundle = loadedBundle,
        worldId = worldId,
        saveSystem = saveSystem,
        soundBank = soundBank,
        onExit = onExit
    )
}

@Composable
private fun ActiveGameScreen(
    bundle: GameBundle,
    worldId: String?,
    saveSystem: SaveSystem,
    soundBank: SoundBank,
    onExit: () -> Unit
) {
    val gameState = bundle.gameState
    val simulation = bundle.simulation
    val renderer = bundle.renderer
    val assetManager = bundle.assetManager

    var gameView by remember { mutableStateOf<GameView?>(null) }
    var selectionVersion by remember { mutableStateOf(0) }
    var uiTick by remember { mutableStateOf(0) }
    var isPaused by remember { mutableStateOf(false) }
    var showBuildMenu by remember { mutableStateOf(false) }
    var gameOverWinner by remember { mutableStateOf(-1) }
    var isGameOver by remember { mutableStateOf(false) }
    var volume by remember { mutableStateOf(saveSystem.loadGlobalSettings().volume) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> gameView?.onPause()
                Lifecycle.Event.ON_RESUME -> gameView?.onResume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
            // GameView queues OpenGL resource disposal on the GL thread.
            assetManager.destroy()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF143340))) {
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
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
            )

            val currentSelection = remember(selectionVersion, uiTick) {
                synchronized(gameState) { gameState.selected.toList() }
            }
            SelectionPanel(
                selected = currentSelection,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 8.dp, bottom = 58.dp)
            )

            if (currentSelection.isNotEmpty()) {
                ActionBar(
                    selected = currentSelection,
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
                    onBuildMenu = { showBuildMenu = !showBuildMenu },
                    onTrain = { unitType ->
                        gameView?.runCommand {
                            val building = gameState.selected.firstOrNull() as? GameBuilding
                            if (building != null) simulation.economy.trainUnit(building, unitType)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                )
            } else {
                QuickControlBar(
                    onWorkers = { gameView?.selectAllWorkers() },
                    onArmy = { gameView?.selectAllMilitary() },
                    onAll = { gameView?.selectAllUnits() },
                    onHome = { gameView?.focusPlayerBase() },
                    onCancel = {
                        showBuildMenu = false
                        gameView?.cancelPlacement()
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                )
            }

            if (showBuildMenu) {
                BuildBar(
                    faction = faction,
                    onBuild = { type ->
                        gameView?.runCommand { gameState.placingBuilding = type }
                        showBuildMenu = false
                    },
                    onClose = { showBuildMenu = false },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 8.dp, end = 8.dp, bottom = 56.dp)
                )
            }

            CommandButton(
                text = "II",
                onClick = {
                    isPaused = true
                    gameView?.pause()
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
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

@Composable
private fun RealmLoadingScreen(stage: String, progress: Float, onCancel: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF07111B), Color(0xFF123327), Color(0xFF1F210B))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 430.dp)
                .fillMaxWidth(0.72f)
                .background(GameColors.Panel.copy(alpha = 0.96f), RoundedCornerShape(14.dp))
                .border(2.dp, GameColors.PanelBorder, RoundedCornerShape(14.dp))
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Text("GENERATING REALM", style = GameTypography.Title.copy(fontSize = 26.sp), textAlign = TextAlign.Center)
            Text(stage, style = GameTypography.Body.copy(color = GameColors.TextSecondary), textAlign = TextAlign.Center)
            LinearProgressIndicator(
                progress = progress.coerceIn(0f, 1f),
                modifier = Modifier.fillMaxWidth().height(10.dp),
                color = GameColors.TextGold,
                trackColor = Color(0x66000000)
            )
            Text("Terrain, navigation, saved entities and sprites are prepared here before the battlefield opens.", style = GameTypography.Small, textAlign = TextAlign.Center)
            CommandButton("CANCEL", onClick = onCancel, modifier = Modifier.fillMaxWidth())
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
