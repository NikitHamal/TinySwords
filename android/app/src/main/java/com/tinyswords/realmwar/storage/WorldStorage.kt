package com.tinyswords.realmwar.storage

import android.content.Context
import android.content.SharedPreferences
import com.tinyswords.realmwar.game.Difficulty
import com.tinyswords.realmwar.game.GraphicsTier
import com.tinyswords.realmwar.game.MapStyle
import com.tinyswords.realmwar.game.ResourceDensity
import com.tinyswords.realmwar.game.WorldSettings
import com.tinyswords.realmwar.game.WorldSize
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class GlobalSettings(
    val volume: Float = 0.8f,
    val autosave: Boolean = true,
    val edgePan: Boolean = true,
    val graphics: GraphicsTier = GraphicsTier.BALANCED,
)

/**
 * One row in the world list. The game-state blob is opaque JSON the
 * simulation knows how to read; the metadata fields drive the menus.
 */
data class WorldRecord(
    val id: String,
    var name: String,
    val seed: String,
    val createdAtMs: Long,
    var lastPlayedAtMs: Long,
    var settings: WorldSettings,
    var stateJson: String? = null,
)

/**
 * SharedPreferences-backed catalogue of saved worlds + global settings.
 *
 * The web version stored everything in localStorage. We keep the same model
 * but split the JSON into one preference key per world so a corrupt save
 * doesn't take everything down with it.
 */
class WorldStorage(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("tinyswords-storage", Context.MODE_PRIVATE)

    fun loadWorlds(): List<WorldRecord> {
        val list = mutableListOf<WorldRecord>()
        val ids = prefs.getString(KEY_WORLD_IDS, "") ?: ""
        for (id in ids.split(',').filter { it.isNotBlank() }) {
            val raw = prefs.getString(worldKey(id), null) ?: continue
            try {
                list += parseWorld(JSONObject(raw))
            } catch (_: Throwable) { /* skip corrupted entries */ }
        }
        return list.sortedByDescending { it.lastPlayedAtMs }
    }

    fun loadWorld(id: String): WorldRecord? {
        val raw = prefs.getString(worldKey(id), null) ?: return null
        return runCatching { parseWorld(JSONObject(raw)) }.getOrNull()
    }

    fun saveWorld(record: WorldRecord) {
        prefs.edit().apply {
            putString(worldKey(record.id), serializeWorld(record).toString())
            val ids = (prefs.getString(KEY_WORLD_IDS, "") ?: "")
                .split(',').filter { it.isNotBlank() }.toMutableList()
            if (record.id !in ids) ids += record.id
            putString(KEY_WORLD_IDS, ids.joinToString(","))
        }.apply()
    }

    fun deleteWorld(id: String) {
        prefs.edit().apply {
            remove(worldKey(id))
            val ids = (prefs.getString(KEY_WORLD_IDS, "") ?: "")
                .split(',').filter { it.isNotBlank() && it != id }
            putString(KEY_WORLD_IDS, ids.joinToString(","))
        }.apply()
    }

    fun createWorld(name: String, settings: WorldSettings): WorldRecord {
        val id = UUID.randomUUID().toString()
        val resolvedName = name.ifBlank { "Unnamed World" }
        val seed = settings.seed.ifBlank { System.currentTimeMillis().toString(36) }
        val now = System.currentTimeMillis()
        val record = WorldRecord(
            id = id, name = resolvedName, seed = seed,
            createdAtMs = now, lastPlayedAtMs = now,
            settings = settings.copy(seed = seed),
            stateJson = null,
        )
        saveWorld(record)
        return record
    }

    fun globalSettings(): GlobalSettings {
        val raw = prefs.getString(KEY_GLOBAL_SETTINGS, null) ?: return GlobalSettings()
        return runCatching {
            val o = JSONObject(raw)
            GlobalSettings(
                volume = o.optDouble("volume", 0.8).toFloat(),
                autosave = o.optBoolean("autosave", true),
                edgePan = o.optBoolean("edgePan", true),
                graphics = parseGraphics(o.optString("graphics", "balanced")),
            )
        }.getOrDefault(GlobalSettings())
    }

    fun saveGlobalSettings(s: GlobalSettings) {
        val o = JSONObject()
            .put("volume", s.volume)
            .put("autosave", s.autosave)
            .put("edgePan", s.edgePan)
            .put("graphics", s.graphics.name.lowercase())
        prefs.edit().putString(KEY_GLOBAL_SETTINGS, o.toString()).apply()
    }

    // ----- (De)serialization helpers ----------------------------------------

    private fun serializeWorld(record: WorldRecord): JSONObject = JSONObject()
        .put("id", record.id)
        .put("name", record.name)
        .put("seed", record.seed)
        .put("createdAtMs", record.createdAtMs)
        .put("lastPlayedAtMs", record.lastPlayedAtMs)
        .put("settings", serializeSettings(record.settings))
        .put("state", record.stateJson?.let { JSONObject(it) } ?: JSONObject.NULL)

    private fun parseWorld(o: JSONObject): WorldRecord {
        val rawState = o.opt("state")
        val stateJson = if (rawState is JSONObject) rawState.toString() else null
        return WorldRecord(
            id = o.getString("id"),
            name = o.getString("name"),
            seed = o.optString("seed", ""),
            createdAtMs = o.optLong("createdAtMs", 0L),
            lastPlayedAtMs = o.optLong("lastPlayedAtMs", 0L),
            settings = parseSettings(o.getJSONObject("settings")),
            stateJson = stateJson,
        )
    }

    private fun serializeSettings(s: WorldSettings) = JSONObject()
        .put("size", s.size.name.lowercase())
        .put("mapStyle", s.mapStyle.name.lowercase())
        .put("difficulty", s.difficulty.name.lowercase())
        .put("resourceDensity", s.resourceDensity.name.lowercase())
        .put("rivals", s.rivals)
        .put("seed", s.seed)
        .put("autosave", s.autosave)
        .put("graphics", s.graphics.name.lowercase())

    private fun parseSettings(o: JSONObject): WorldSettings {
        return WorldSettings(
            size = parseEnum(o.optString("size"), WorldSize.values(), WorldSize.LARGE) { it.name.lowercase() },
            mapStyle = parseEnum(o.optString("mapStyle"), MapStyle.values(), MapStyle.CROSSROADS) { it.name.lowercase() },
            difficulty = parseEnum(o.optString("difficulty"), Difficulty.values(), Difficulty.NORMAL) { it.name.lowercase() },
            resourceDensity = parseEnum(o.optString("resourceDensity"), ResourceDensity.values(), ResourceDensity.RICH) { it.name.lowercase() },
            rivals = o.optInt("rivals", 4).coerceIn(0, 4),
            seed = o.optString("seed", ""),
            autosave = o.optBoolean("autosave", true),
            graphics = parseGraphics(o.optString("graphics", "balanced")),
        )
    }

    private fun parseGraphics(s: String): GraphicsTier =
        parseEnum(s, GraphicsTier.values(), GraphicsTier.BALANCED) { it.name.lowercase() }

    private fun <T> parseEnum(raw: String, all: Array<T>, default: T, lower: (T) -> String): T {
        val needle = raw.lowercase().replace("_", "")
        return all.firstOrNull { lower(it).replace("_", "") == needle } ?: default
    }

    private fun worldKey(id: String) = "world:$id"

    @Suppress("unused")
    fun exportRecordToJsonArray(records: List<WorldRecord>): JSONArray {
        val arr = JSONArray()
        for (r in records) arr.put(serializeWorld(r))
        return arr
    }

    private companion object {
        const val KEY_WORLD_IDS = "world_ids"
        const val KEY_GLOBAL_SETTINGS = "global_settings"
    }
}
