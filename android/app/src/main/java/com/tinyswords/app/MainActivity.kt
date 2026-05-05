package com.tinyswords.app

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.tinyswords.app.audio.SoundBank
import com.tinyswords.app.data.CrashHandler
import com.tinyswords.app.data.SaveSystem
import com.tinyswords.app.game.WorldSettings
import com.tinyswords.app.ui.screens.*

class MainActivity : ComponentActivity() {

    private lateinit var saveSystem: SaveSystem
    private lateinit var soundBank: SoundBank

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))

        // Immersive fullscreen
        setupImmersive()

        saveSystem = SaveSystem(this)
        soundBank = SoundBank(this)
        soundBank.init()

        val globalSettings = saveSystem.loadGlobalSettings()
        soundBank.setVolume(globalSettings.volume)

        val crashTrace = CrashHandler.getCrashTrace(this)
        if (crashTrace != null) {
            setContent {
                CrashScreen(crashTrace)
            }
            return
        }

        setContent {
            TinySwordsApp(saveSystem, soundBank)
        }
    }

    private fun setupImmersive() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) setupImmersive()
    }

    override fun onDestroy() {
        soundBank.destroy()
        super.onDestroy()
    }
}

@Composable
fun TinySwordsApp(saveSystem: SaveSystem, soundBank: SoundBank) {
    var screen by remember { mutableStateOf<Screen>(Screen.Title) }

    when (val current = screen) {
        is Screen.Title -> {
            val hasWorlds = remember { saveSystem.listWorlds().isNotEmpty() }
            TitleScreen(
                onQuickPlay = {
                    val defaultSettings = WorldSettings()
                    val meta = saveSystem.createWorldMeta("Quick Realm", defaultSettings)
                    screen = Screen.Game(defaultSettings, meta.id)
                },
                onNewGame = { screen = Screen.NewGame },
                onLoadGame = { screen = Screen.LoadGame },
                onSettings = { screen = Screen.Settings },
                hasExistingWorlds = hasWorlds
            )
        }
        is Screen.NewGame -> {
            NewGameScreen(
                onStart = { settings ->
                    val meta = saveSystem.createWorldMeta("Realm ${System.currentTimeMillis() % 1000}", settings)
                    screen = Screen.Game(settings, meta.id)
                },
                onBack = { screen = Screen.Title }
            )
        }
        is Screen.Settings -> {
            SettingsScreen(
                saveSystem = saveSystem,
                soundBank = soundBank,
                onBack = { screen = Screen.Title }
            )
        }
        is Screen.LoadGame -> {
            LoadGameScreen(
                saveSystem = saveSystem,
                onLoad = { worldId ->
                    val worlds = saveSystem.listWorlds()
                    val meta = worlds.find { it.id == worldId }
                    if (meta != null) {
                        screen = Screen.Game(meta.settings, meta.id)
                    }
                },
                onBack = { screen = Screen.Title }
            )
        }
        is Screen.Game -> {
            GameScreen(
                worldSettings = current.settings,
                worldId = current.worldId,
                saveSystem = saveSystem,
                soundBank = soundBank,
                onExit = { screen = Screen.Title }
            )
        }
    }
}

sealed class Screen {
    data object Title : Screen()
    data object NewGame : Screen()
    data object LoadGame : Screen()
    data object Settings : Screen()
    data class Game(val settings: WorldSettings, val worldId: String?) : Screen()
}
