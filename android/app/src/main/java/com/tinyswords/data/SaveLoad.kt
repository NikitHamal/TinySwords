package com.tinyswords.data

import android.content.Context
import com.tinyswords.game.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Save/load to a single JSON file in the app's private storage. We persist enough state to
 * restore the simulation faithfully on relaunch — entity arrays + faction resources + camera.
 *
 * The world's terrain is regenerated from [SaveBlob.seed] on load; that's both smaller and
 * keeps map data deterministic if we tweak generation later.
 */
private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

@Serializable
data class SaveBlob(
    val version: Int = 1,
    val seed: String,
    val time: Float,
    val camX: Float, val camY: Float, val camZoom: Float,
    val factions: List<Faction>,
    val units: List<GameUnit>,
    val buildings: List<Building>,
    val resources: List<GameResource>,
    val nextId: Int
)

object SaveLoad {
    private const val FILE = "tinyswords_save.json"

    fun save(ctx: Context, game: Game, seed: String) {
        val nextId = (game.units.maxOfOrNull { it.id } ?: 0)
            .coerceAtLeast(game.buildings.maxOfOrNull { it.id } ?: 0)
            .coerceAtLeast(game.resources.maxOfOrNull { it.id } ?: 0) + 1
        val blob = SaveBlob(
            seed = seed, time = game.time,
            camX = game.camera.x, camY = game.camera.y, camZoom = game.camera.zoom,
            factions = game.factions.toList(),
            units = game.units.toList(),
            buildings = game.buildings.toList(),
            resources = game.resources.toList(),
            nextId = nextId
        )
        ctx.openFileOutput(FILE, Context.MODE_PRIVATE).use {
            it.write(json.encodeToString(blob).toByteArray())
        }
    }

    fun hasSave(ctx: Context): Boolean = ctx.fileList().contains(FILE)

    fun load(ctx: Context): SaveBlob? {
        if (!hasSave(ctx)) return null
        return try {
            val text = ctx.openFileInput(FILE).bufferedReader().use { it.readText() }
            json.decodeFromString<SaveBlob>(text)
        } catch (e: Exception) { null }
    }

    fun delete(ctx: Context) { ctx.deleteFile(FILE) }
}
