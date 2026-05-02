package com.tinyswords.realmwar.ui.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tinyswords.realmwar.TinySwordsApp
import com.tinyswords.realmwar.data.WorldRecord
import com.tinyswords.realmwar.data.WorldSettingsData
import com.tinyswords.realmwar.ui.screen.CreateWorldScreen
import com.tinyswords.realmwar.ui.screen.GameScreen
import com.tinyswords.realmwar.ui.screen.GenerationScreen
import com.tinyswords.realmwar.ui.screen.SettingsScreen
import com.tinyswords.realmwar.ui.screen.TitleScreen
import com.tinyswords.realmwar.ui.screen.WorldListScreen

sealed class Route {
    data object Title : Route()
    data object WorldList : Route()
    data object CreateWorld : Route()
    data object Settings : Route()
    data class Generating(val record: WorldRecord) : Route()
    data class Playing(val record: WorldRecord) : Route()
}

@Composable
fun TinySwordsRoot() {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as TinySwordsApp
    var route: Route by rememberSaveable(stateSaver = RouteSaver) { mutableStateOf(Route.Title) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            when (val r = route) {
                Route.Title -> TitleScreen(
                    storage = app.worldStorage,
                    onContinue = { latest -> route = Route.Generating(latest) },
                    onSinglePlayer = { route = Route.WorldList },
                    onSettings = { route = Route.Settings }
                )
                Route.WorldList -> WorldListScreen(
                    storage = app.worldStorage,
                    onBack = { route = Route.Title },
                    onCreate = { route = Route.CreateWorld },
                    onPlay = { record -> route = Route.Generating(record) },
                    onDelete = { rec -> app.worldStorage.deleteWorld(rec.id) }
                )
                Route.CreateWorld -> CreateWorldScreen(
                    onBack = { route = Route.WorldList },
                    onCreate = { name, settings ->
                        val rec = app.worldStorage.createWorld(name, settings)
                        route = Route.Generating(rec)
                    }
                )
                Route.Settings -> SettingsScreen(onBack = { route = Route.Title })
                is Route.Generating -> GenerationScreen(
                    record = r.record,
                    onReady = { route = Route.Playing(r.record) }
                )
                is Route.Playing -> GameScreen(
                    record = r.record,
                    onExit = {
                        route = Route.Title
                    }
                )
            }
        }
    }
}

private val RouteSaver = androidx.compose.runtime.saveable.Saver<Route, Any>(
    save = { route ->
        when (route) {
            Route.Title -> "title"
            Route.WorldList -> "world-list"
            Route.CreateWorld -> "create"
            Route.Settings -> "settings"
            is Route.Generating -> arrayOf("gen", route.record.id)
            is Route.Playing -> arrayOf("play", route.record.id)
        }
    },
    restore = { saved ->
        // We restart at title on process death to avoid restoring a partial game.
        Route.Title
    }
)
