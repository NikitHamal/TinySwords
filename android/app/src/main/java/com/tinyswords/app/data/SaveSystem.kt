package com.tinyswords.app.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.tinyswords.app.game.*
import com.tinyswords.app.game.entities.*

class SaveSystem(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("tinyswords_saves", Context.MODE_PRIVATE)
    private val settingsPrefs: SharedPreferences = context.getSharedPreferences("tinyswords_settings", Context.MODE_PRIVATE)
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    companion object {
        const val SAVE_INDEX_KEY = "world_index_v2"
        const val MAX_WORLDS = 48
    }

    // ── Global Settings ──

    data class GlobalSettings(
        var autosave: Boolean = true,
        var volume: Float = 0.8f,
        var graphics: String = "balanced"
    )

    fun loadGlobalSettings(): GlobalSettings {
        val json = settingsPrefs.getString("global_settings", null) ?: return GlobalSettings()
        return try {
            gson.fromJson(json, GlobalSettings::class.java)
        } catch (e: Exception) {
            GlobalSettings()
        }
    }

    fun saveGlobalSettings(settings: GlobalSettings) {
        settingsPrefs.edit().putString("global_settings", gson.toJson(settings)).commit()
    }

    // ── World Save/Load ──

    data class WorldMeta(
        val id: String,
        val name: String,
        val createdAt: Long,
        var updatedAt: Long,
        val settings: WorldSettings,
        var playTime: Float = 0f
    )

    fun listWorlds(): List<WorldMeta> {
        val indexed = mutableListOf<WorldMeta>()
        val json = prefs.getString(SAVE_INDEX_KEY, null)
        if (json != null) {
            try {
                val type = object : TypeToken<List<WorldMeta>>() {}.type
                val list: List<WorldMeta> = gson.fromJson(json, type)
                indexed.addAll(list)
            } catch (_: Exception) {
                // Fall through to save-key recovery below.
            }
        }

        // Defensive recovery: Save & Exit writes the payload and index, but on
        // some devices the index can be lost after process death. A saved world
        // should still appear if its save_<id> payload exists.
        val byId = indexed.associateBy { it.id }.toMutableMap()
        for ((key, value) in prefs.all) {
            if (!key.startsWith("save_") || value !is String) continue
            val id = key.removePrefix("save_")
            if (byId.containsKey(id)) continue
            try {
                val payload = gson.fromJson(value, SavePayload::class.java)
                val stamp = if (payload.savedAt > 0L) payload.savedAt else System.currentTimeMillis()
                byId[id] = WorldMeta(
                    id = id,
                    name = "Recovered Realm",
                    createdAt = stamp,
                    updatedAt = stamp,
                    settings = payload.settings ?: WorldSettings(),
                    playTime = payload.time
                )
            } catch (_: Exception) { }
        }
        return byId.values.sortedByDescending { it.updatedAt }.take(MAX_WORLDS)
    }

    private fun saveWorldIndex(worlds: List<WorldMeta>) {
        prefs.edit().putString(SAVE_INDEX_KEY, gson.toJson(worlds)).commit()
    }

    fun createWorldMeta(name: String, settings: WorldSettings): WorldMeta {
        val meta = WorldMeta(
            id = "world-${System.currentTimeMillis()}-${(Math.random() * 10000).toInt()}",
            name = name,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            settings = settings
        )
        val worlds = listWorlds().toMutableList()
        worlds.add(0, meta)
        if (worlds.size > MAX_WORLDS) worlds.subList(MAX_WORLDS, worlds.size).clear()
        saveWorldIndex(worlds)
        return meta
    }

    fun deleteWorld(id: String) {
        val worlds = listWorlds().toMutableList()
        worlds.removeAll { it.id == id }
        saveWorldIndex(worlds)
        prefs.edit().remove("save_$id").commit()
    }

    // ── Game State Serialization ──

    data class SavePayload(
        val schema: Int = 3,
        val worldW: Float,
        val worldH: Float,
        val time: Float,
        val cameraX: Float,
        val cameraY: Float,
        val cameraZoom: Float,
        val factions: List<FactionSave>,
        val units: List<UnitSave>,
        val buildings: List<BuildingSave>,
        val resources: List<ResourceSave>,
        val formationMode: String,
        val settings: WorldSettings? = null,
        val savedAt: Long = 0L
    )

    data class FactionSave(
        val id: Int,
        val wood: Float, val gold: Float, val food: Float,
        val alive: Boolean,
        val underAttack: Float,
        val aiTimer: Float,
        val aiAttackTimer: Float,
        val aiExpansion: Int
    )

    data class UnitSave(
        val id: Int, val type: String, val faction: Int,
        val x: Float, val y: Float,
        val hp: Int, val maxHp: Int,
        val order: String, val targetId: Int,
        val goalX: Float, val goalY: Float, val hasGoal: Boolean,
        val face: Int, val workerRole: String,
        val carrying: String?, val carryAmount: Float,
        val hold: Boolean
    )

    data class BuildingSave(
        val id: Int, val type: String, val faction: Int,
        val x: Float, val y: Float,
        val hp: Int, val maxHp: Int,
        val buildProgress: Float,
        val rallyX: Float, val rallyY: Float, val hasRally: Boolean,
        val queue: List<TrainSlotSave>
    )

    data class TrainSlotSave(val unitType: String, val progress: Float, val trainTime: Float)

    data class ResourceSave(
        val id: Int, val type: String,
        val x: Float, val y: Float,
        val amount: Float, val maxAmount: Float,
        val depleted: Boolean, val variant: Int,
        val isAnimal: Boolean, val animalKind: String,
        val animalHp: Float, val animalMaxHp: Float,
        val animalDir: Int = 0
    )

    fun saveGame(worldId: String, state: GameState) {
        val payload = SavePayload(
            worldW = state.worldW,
            worldH = state.worldH,
            time = state.time,
            cameraX = state.camera.x,
            cameraY = state.camera.y,
            cameraZoom = state.camera.zoom,
            factions = state.factions.map { f ->
                FactionSave(f.id, f.wood, f.gold, f.food, f.alive, f.underAttack,
                    f.aiState.timer, f.aiState.attackTimer, f.aiState.expansion)
            },
            units = state.units.filter { !it.dead }.map { u ->
                UnitSave(u.id, u.type, u.faction, u.x, u.y, u.hp, u.maxHp,
                    u.order.name, u.targetId, u.goalX, u.goalY, u.hasGoal,
                    u.face, u.workerRole.name, u.carrying, u.carryAmount, u.hold)
            },
            buildings = state.buildings.filter { !it.dead }.map { b ->
                BuildingSave(b.id, b.type, b.faction, b.x, b.y, b.hp, b.maxHp,
                    b.buildProgress, b.rallyX, b.rallyY, b.hasRally,
                    b.queue.map { TrainSlotSave(it.unitType, it.progress, it.trainTime) })
            },
            resources = state.resources.filter { !it.dead }.map { r ->
                ResourceSave(r.id, r.type.name, r.x, r.y, r.amount, r.maxAmount,
                    r.depleted, r.variant, r.isAnimal, r.animalKind, r.animalHp, r.animalMaxHp, r.animalDir)
            },
            formationMode = state.formationMode,
            settings = state.settings,
            savedAt = System.currentTimeMillis()
        )

        prefs.edit().putString("save_$worldId", gson.toJson(payload)).commit()

        // Update world meta
        val worlds = listWorlds().toMutableList()
        val now = System.currentTimeMillis()
        val existing = worlds.find { it.id == worldId }
        if (existing != null) {
            existing.updatedAt = now
            existing.playTime = state.time
        } else {
            worlds.add(0, WorldMeta(
                id = worldId,
                name = "Realm ${now % 1000}",
                createdAt = now,
                updatedAt = now,
                settings = state.settings,
                playTime = state.time
            ))
        }
        saveWorldIndex(worlds.sortedByDescending { it.updatedAt }.take(MAX_WORLDS))
    }

    fun loadGame(worldId: String, state: GameState): Boolean {
        val json = prefs.getString("save_$worldId", null) ?: return false
        return try {
            val payload = gson.fromJson(json, SavePayload::class.java)
            applySave(payload, state)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun applySave(payload: SavePayload, state: GameState) {
        state.time = payload.time
        state.camera.x = payload.cameraX
        state.camera.y = payload.cameraY
        state.camera.zoom = payload.cameraZoom
        state.camera.targetZoom = payload.cameraZoom
        state.formationMode = payload.formationMode

        // Apply saved settings if present, preserving graphics mode
        if (payload.settings != null) {
            state.settings = payload.settings.copy(graphics = payload.settings.graphics ?: "balanced")
        }

        // Factions
        for (fs in payload.factions) {
            val f = state.factions.getOrNull(fs.id) ?: continue
            f.wood = fs.wood; f.gold = fs.gold; f.food = fs.food
            f.alive = fs.alive; f.underAttack = fs.underAttack
            f.aiState.timer = fs.aiTimer
            f.aiState.attackTimer = fs.aiAttackTimer
            f.aiState.expansion = fs.aiExpansion
        }

        // Clear runtime state that is restored from the save. Terrain/decor are
        // regenerated deterministically from the world seed before this method is
        // called, so we keep decor but replace gameplay entities.
        state.selected.clear()
        state.units.clear()
        state.buildings.clear()
        state.resources.clear()
        state.projectiles.clear()
        state.effects.clear()

        // Restore units
        for (us in payload.units) {
            val u = GameUnit.create(us.type, us.faction, us.x, us.y) { us.id }
            u.hp = us.hp; u.maxHp = us.maxHp
            u.order = try { UnitOrder.valueOf(us.order) } catch (e: Exception) { UnitOrder.IDLE }
            u.targetId = us.targetId
            u.goalX = us.goalX; u.goalY = us.goalY; u.hasGoal = us.hasGoal
            u.face = us.face
            u.workerRole = try { WorkerRole.valueOf(us.workerRole) } catch (e: Exception) { WorkerRole.AUTO }
            u.carrying = us.carrying; u.carryAmount = us.carryAmount; u.hold = us.hold
            state.units.add(u)
            state.ensureNextIdGreaterThan(u.id)
        }

        // Restore buildings
        for (bs in payload.buildings) {
            val b = GameBuilding.create(bs.type, bs.faction, bs.x, bs.y, { bs.id }, bs.buildProgress >= 1f)
            b.hp = bs.hp; b.maxHp = bs.maxHp; b.buildProgress = bs.buildProgress
            b.rallyX = bs.rallyX; b.rallyY = bs.rallyY; b.hasRally = bs.hasRally
            b.queue.addAll(bs.queue.map { TrainSlot(it.unitType, it.progress, it.trainTime) })
            state.buildings.add(b)
            state.ensureNextIdGreaterThan(b.id)
        }

        // Restore resources
        for (rs in payload.resources) {
            val r = GameResource().apply {
                id = rs.id
                type = try { ResourceType.valueOf(rs.type) } catch (e: Exception) { ResourceType.TREE }
                x = rs.x; y = rs.y
                amount = rs.amount; maxAmount = rs.maxAmount
                depleted = rs.depleted; variant = rs.variant
                isAnimal = rs.isAnimal; animalKind = rs.animalKind
                animalHp = rs.animalHp; animalMaxHp = rs.animalMaxHp
                animalDir = rs.animalDir
            }
            state.resources.add(r)
            state.ensureNextIdGreaterThan(r.id)
        }

        val allEntities = mutableListOf<GameEntity>()
        allEntities.addAll(state.units)
        allEntities.addAll(state.buildings)
        allEntities.addAll(state.resources)
        val byId = allEntities.associateBy { it.id }
        for (u in state.units) {
            u.target = byId[u.targetId]
        }
        state.rebuildSpatialIndices()
        state.spatialRebuildTimer = 0.10f
    }
}
