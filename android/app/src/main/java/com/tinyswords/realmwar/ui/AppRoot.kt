package com.tinyswords.realmwar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.tinyswords.realmwar.TinySwordsApplication
import com.tinyswords.realmwar.storage.WorldRecord

/** Top-level navigation between menus and an active match. */
sealed class Screen {
    data object Loading : Screen()
    data object Title : Screen()
    data object WorldList : Screen()
    data object CreateWorld : Screen()
    data object Settings : Screen()
    data class InGame(val world: WorldRecord) : Screen()
}

@Composable
fun AppRoot(application: TinySwordsApplication) {
    TinySwordsTheme {
        var screen by remember { mutableStateOf<Screen>(Screen.Loading) }
        var loadProgress by remember { mutableStateOf(0f) }

        // Preload all sprite assets once; show the loading panel until done.
        LaunchedEffect(Unit) {
            application.spriteCache.preload { loaded, total ->
                loadProgress = loaded.toFloat() / total.toFloat()
            }
            screen = Screen.Title
        }

        Box(modifier = Modifier.fillMaxSize().background(TS.PanelDark)) {
            when (val s = screen) {
                Screen.Loading -> LoadingScreen(loadProgress)
                Screen.Title -> TitleScreen(
                    storage = application.storage,
                    onContinue = { latest ->
                        if (latest != null) screen = Screen.InGame(latest)
                    },
                    onSinglePlayer = { screen = Screen.WorldList },
                    onSettings = { screen = Screen.Settings },
                )
                Screen.WorldList -> WorldListScreen(
                    storage = application.storage,
                    onBack = { screen = Screen.Title },
                    onCreateNew = { screen = Screen.CreateWorld },
                    onPlay = { record -> screen = Screen.InGame(record) },
                )
                Screen.CreateWorld -> CreateWorldScreen(
                    storage = application.storage,
                    onBack = { screen = Screen.WorldList },
                    onCreated = { record -> screen = Screen.InGame(record) },
                )
                Screen.Settings -> SettingsScreen(
                    storage = application.storage,
                    onBack = { screen = Screen.Title },
                )
                is Screen.InGame -> GameScreen(
                    application = application,
                    world = s.world,
                    onExit = { screen = Screen.WorldList },
                )
            }
        }
    }
}
