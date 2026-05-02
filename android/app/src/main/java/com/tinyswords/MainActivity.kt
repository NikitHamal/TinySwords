package com.tinyswords

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tinyswords.data.SaveLoad
import com.tinyswords.ui.GameScreen
import com.tinyswords.ui.MainMenu
import com.tinyswords.ui.MenuButton
import com.tinyswords.ui.TinySwordsTheme
import com.tinyswords.ui.Type

/**
 * Single-Activity host. Compose handles all navigation: menu ↔ in-game ↔ about.
 * Immersive mode is enabled so the game uses the full edge-to-edge screen on phones.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        setContent {
            TinySwordsTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Root()
                }
            }
        }
    }
}

@Composable
private fun Root() {
    val ctx = LocalContext.current
    var screen by remember { mutableStateOf<Screen>(Screen.Menu) }
    when (val s = screen) {
        Screen.Menu -> MainMenu(
            hasSave = SaveLoad.hasSave(ctx),
            onContinue = {
                val blob = SaveLoad.load(ctx)
                if (blob != null) screen = Screen.Game(blob.seed)
            },
            onNewGame = { seed ->
                SaveLoad.delete(ctx)
                screen = Screen.Game(seed)
            },
            onAbout = { screen = Screen.About }
        )
        is Screen.Game -> GameScreen(seed = s.seed, onExitToMenu = { screen = Screen.Menu })
        Screen.About -> AboutScreen { screen = Screen.Menu }
    }
}

private sealed interface Screen {
    data object Menu : Screen
    data class Game(val seed: String) : Screen
    data object About : Screen
}

@Composable
private fun AboutScreen(onBack: () -> kotlin.Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Tiny Swords", style = Type.Display)
            Text("Pixel-art real-time strategy. Build, train, and conquer.", style = Type.Body)
            Text("Long-press to issue orders. Pinch to zoom. Drag to pan.", style = Type.Small)
            Spacer(Modifier.height(12.dp))
            MenuButton("Back", onBack)
        }
    }
}
