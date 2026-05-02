package com.tinyswords.realmwar.data

import android.content.Context
import com.tinyswords.realmwar.game.GameConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * Persistent storage for the player's worlds. Saves to filesDir/worlds/<uuid>.json. The actual
 * runtime state is captured by [WorldSaveData], which mirrors the JS `TinySwordsStorage` shape.
 */
class WorldStorage(private val ctx: Context) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val dir: File = File(ctx.filesDir, "worlds").apply { mkdirs() }
    private val indexFile = File(dir, "_index.json")

    private fun readIndex(): MutableList<WorldRecord> {
        if (!indexFile.exists()) return mutableListOf()
        return try {
            json.decodeFromString(ListSerializer(WorldRecord.serializer()), indexFile.readText())
                .toMutableList()
        } catch (_: Throwable) { mutableListOf() }
    }

    private fun writeIndex(records: List<WorldRecord>) {
        indexFile.writeText(json.encodeToString(ListSerializer(WorldRecord.serializer()), records))
    }

    fun listWorlds(): List<WorldRecord> = readIndex().sortedByDescending { it.lastPlayed }

    fun createWorld(name: String, settings: WorldSettingsData): WorldRecord {
        val all = readIndex()
        val record = WorldRecord(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { "Unnamed Realm" },
            seed = settings.seed.ifBlank { System.currentTimeMillis().toString() },
            settings = settings,
            createdAt = System.currentTimeMillis(),
            lastPlayed = System.currentTimeMillis(),
            playtimeSeconds = 0
        )
        all.add(0, record)
        writeIndex(all)
        return record
    }

    fun deleteWorld(id: String) {
        val all = readIndex().filterNot { it.id == id }.toMutableList()
        writeIndex(all)
        File(dir, "$id.json").takeIf { it.exists() }?.delete()
    }

    fun saveState(id: String, save: WorldSaveData) {
        File(dir, "$id.json").writeText(json.encodeToString(WorldSaveData.serializer(), save))
        val all = readIndex()
        val idx = all.indexOfFirst { it.id == id }
        if (idx >= 0) {
            all[idx] = all[idx].copy(lastPlayed = System.currentTimeMillis(), playtimeSeconds = save.elapsedTime.toLong())
            writeIndex(all)
        }
    }

    fun loadState(id: String): WorldSaveData? {
        val f = File(dir, "$id.json")
        if (!f.exists()) return null
        return try { json.decodeFromString(WorldSaveData.serializer(), f.readText()) } catch (_: Throwable) { null }
    }

    fun mostRecent(): WorldRecord? = listWorlds().firstOrNull()
}

@Serializable
data class WorldRecord(
    val id: String,
    val name: String,
    val seed: String,
    val settings: WorldSettingsData,
    val createdAt: Long,
    val lastPlayed: Long,
    val playtimeSeconds: Long
)

@Serializable
data class WorldSettingsData(
    val seed: String = "",
    val size: String = GameConfig.WorldSize.LARGE.name,
    val mapStyle: String = GameConfig.MapStyle.CROSSROADS.name,
    val difficulty: String = GameConfig.Difficulty.NORMAL.name,
    val density: String = GameConfig.ResourceDensity.RICH.name,
    val graphics: String = GameConfig.GraphicsQuality.BALANCED.name,
    val rivals: Int = 4,
    val autosave: Boolean = true
)

@Serializable
data class WorldSaveData(
    val elapsedTime: Float,
    val factions: List<SavedFaction>,
    val units: List<SavedUnit>,
    val buildings: List<SavedBuilding>,
    val resources: List<SavedResource>
)

@Serializable
data class SavedFaction(val id: Int, val wood: Int, val gold: Int, val food: Int, val alive: Boolean)

@Serializable
data class SavedUnit(
    val type: String,
    val factionId: Int,
    val x: Float,
    val y: Float,
    val hp: Float,
    val carryType: String? = null,
    val carryAmount: Int = 0
)

@Serializable
data class SavedBuilding(
    val type: String,
    val factionId: Int,
    val x: Float,
    val y: Float,
    val hp: Float,
    val built: Boolean,
    val buildProgress: Float
)

@Serializable
data class SavedResource(
    val type: String,
    val x: Float,
    val y: Float,
    val amount: Int,
    val animal: String? = null,
    val animalHp: Float = 0f
)
