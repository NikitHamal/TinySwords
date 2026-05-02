package com.tinyswords.realmwar.game

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import com.tinyswords.realmwar.audio.SoundBank
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The single source of truth for an in-progress match. Compose UI listens
 * to [tick] (incremented every frame) plus a few exposed [MutableState]s
 * to redraw without us having to wrap thousands of entities in observable
 * snapshots — too slow.
 */
class GameEngine(
    val state: GameState,
    private val sound: SoundBank,
) {
    var paused: MutableState<Boolean> = mutableStateOf(false)
    var fast: MutableState<Boolean> = mutableStateOf(false)
    var tick: MutableState<Long> = mutableStateOf(0L)
    var toast: MutableState<String?> = mutableStateOf(null)
    private var toastUntil: Long = 0L

    /** Selection lives here so the HUD can observe it. */
    val selection: SnapshotStateList<Int> = mutableListOf<Int>().toMutableStateList()
    /** "place this building next tap" intent set by the build menu. */
    var placingBuilding: MutableState<BuildingType?> = mutableStateOf(null)

    private val sims = SimulationSystems(this)
    private val ai = AiSystems(this)

    private var loopJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun start() {
        if (loopJob?.isActive == true) return
        loopJob = scope.launch {
            var last = System.nanoTime()
            while (true) {
                val now = System.nanoTime()
                val dt = ((now - last) / 1e9).toFloat().coerceAtMost(MAX_DT)
                last = now
                if (!paused.value) advance(dt * if (fast.value) 2f else 1f)
                tick.value = tick.value + 1
                delay(if (fast.value) 8L else 16L)
            }
        }
    }

    fun stop() {
        loopJob?.cancel()
        scope.cancel()
    }

    private fun advance(dt: Float) {
        state.time += dt
        sims.updateUnits(dt)
        sims.updateBuildings(dt)
        sims.updateProjectiles(dt)
        sims.updateEffects(dt)
        sims.updateResources(dt)
        ai.update(dt)
        if (toast.value != null && System.currentTimeMillis() > toastUntil) toast.value = null
    }

    fun showToast(message: String, durationSec: Float = 4f) {
        toast.value = message
        toastUntil = System.currentTimeMillis() + (durationSec * 1000).toLong()
    }

    /** Issue a movement order to currently selected units. */
    fun orderMove(wx: Float, wy: Float) {
        val grouped = ownedSelection()
        if (grouped.isEmpty()) return
        val center = grouped.fold(Vec2(0f, 0f)) { acc, u -> Vec2(acc.x + u.x, acc.y + u.y) }
        center.x /= grouped.size; center.y /= grouped.size
        val angle = atan2(wy - center.y, wx - center.x)
        for ((i, u) in grouped.withIndex()) {
            val (ox, oy) = formationOffset(i, grouped.size, 44f)
            val tx = wx + cos(angle) * ox - sin(angle) * oy
            val ty = wy + sin(angle) * ox + cos(angle) * oy
            u.targetX = tx; u.targetY = ty
            u.targetId = -1
            u.assignedRole = null; u.harvesting = false
            u.state = UnitState.MOVING
        }
    }

    fun orderAttack(targetId: Int) {
        for (u in ownedSelection()) {
            u.targetId = targetId
            val target = entityById(targetId) ?: return
            u.targetX = target.x; u.targetY = target.y
            u.state = UnitState.ATTACKING
            u.harvesting = false; u.assignedRole = null
        }
    }

    fun orderGather(resourceId: Int) {
        val res = state.resources.firstOrNull { it.id == resourceId } ?: return
        for (u in ownedSelection().filter { it.type == UnitType.WORKER }) {
            u.targetId = resourceId
            u.assignedRole = res.resType
            u.harvesting = false
            u.state = UnitState.GATHERING
            u.targetX = res.x; u.targetY = res.y
        }
    }

    fun orderStop() {
        for (u in ownedSelection()) {
            u.targetX = null; u.targetY = null
            u.targetId = -1; u.harvesting = false
            u.assignedRole = null
            u.state = UnitState.IDLE
        }
    }

    /** Set rally for selected production buildings. */
    fun orderRally(wx: Float, wy: Float) {
        val player = state.playerFaction()
        for (id in selection) {
            val b = state.buildings.firstOrNull { it.id == id && it.faction == player.def.id } ?: continue
            if (b.def.trains.isNotEmpty()) {
                b.rallyX = wx; b.rallyY = wy
                showToast("Rally point set for ${b.def.type.displayName}", 1.5f)
            }
        }
    }

    /** Player-side build placement. */
    fun tryPlaceBuilding(type: BuildingType, wx: Float, wy: Float): Boolean {
        val player = state.playerFaction()
        val def = BUILDING_DEFS[type]!!
        if (!player.res.canAfford(def.cost)) {
            showToast("Need ${formatCost(def.cost)}.", 2.5f); return false
        }
        if (!isValidPlacement(type, wx, wy)) {
            showToast("Cannot build there.", 2f); return false
        }
        // For tower: instant; everything else: foundation + worker construction.
        player.res.pay(def.cost)
        val b = Building(state.nextEntityId(), type, def)
        b.x = wx; b.y = wy
        b.maxHp = def.hp.toFloat()
        b.hp = if (type == BuildingType.TOWER) b.maxHp else b.maxHp * 0.18f
        b.faction = player.def.id
        b.phase = if (type == BuildingType.TOWER) BuildingPhase.COMPLETE else BuildingPhase.FOUNDATION
        b.buildProgress = if (type == BuildingType.TOWER) 1f else 0f
        state.buildings += b
        if (type == BuildingType.TOWER) {
            // tower spawns its built-in archer (faithful to web build)
            val def2 = UNIT_DEFS[UnitType.ARCHER]!!
            val u = Unit(state.nextEntityId(), UnitType.ARCHER, def2)
            u.x = wx; u.y = wy + 14f
            u.faction = player.def.id
            u.maxHp = def2.maxHp.toFloat(); u.hp = u.maxHp
            state.units += u
            player.popUsed += def2.popCost
        } else {
            // Walk all idle nearby workers to construct
            val nearby = state.units
                .filter { it.faction == player.def.id && it.type == UnitType.WORKER && !it.dead }
                .sortedBy { hypot(it.x - wx, it.y - wy) }
                .take(2)
            for (w in nearby) {
                w.targetId = b.id
                w.targetX = wx; w.targetY = wy
                w.state = UnitState.BUILDING
            }
        }
        if (type == BuildingType.HOUSE) player.popCap += def.popProvided
        showToast("${def.type.displayName} placed.", 1.5f)
        placingBuilding.value = null
        return true
    }

    fun isValidPlacement(type: BuildingType, wx: Float, wy: Float): Boolean {
        if (!state.isLand(wx, wy)) return false
        val def = BUILDING_DEFS[type]!!
        val r = footprintRect(def, wx, wy)
        for (b in state.buildings) {
            if (b.dead) continue
            val br = footprintRect(b.def, b.x, b.y)
            if (rectIntersect(r, br)) return false
        }
        for (res in state.resources) {
            if (res.dead) continue
            val rr = floatArrayOf(res.x - 24f, res.y - 24f, res.x + 24f, res.y + 24f)
            if (rectIntersect(r, rr)) return false
        }
        return true
    }

    fun queueTraining(buildingId: Int, type: UnitType): Boolean {
        val b = state.buildings.firstOrNull { it.id == buildingId } ?: return false
        if (b.faction != state.playerFaction().def.id) return false
        if (type !in b.def.trains) return false
        val def = UNIT_DEFS[type]!!
        val player = state.playerFaction()
        if (player.popUsed + def.popCost > player.popCap) {
            showToast("Population cap reached. Build a House.", 2f); return false
        }
        if (!player.res.canAfford(def.cost)) {
            showToast("Need ${formatCost(def.cost)}.", 2.5f); return false
        }
        player.res.pay(def.cost)
        b.trainQueue.addLast(type)
        if (b.trainingType == null) {
            b.trainingType = b.trainQueue.removeFirst()
            b.trainingTimeLeft = UNIT_DEFS[b.trainingType!!]!!.buildTimeSec
        }
        showToast("Training ${def.type.displayName}", 1f)
        return true
    }

    /** Returns selected entities owned by the player. */
    fun ownedSelection(): List<Unit> {
        val pid = state.playerFaction().def.id
        return state.units.filter { it.id in selection && it.faction == pid && !it.dead }
    }

    fun selectedBuildings(): List<Building> {
        return state.buildings.filter { it.id in selection && !it.dead }
    }

    fun entityById(id: Int): Entity? {
        for (u in state.units) if (u.id == id) return u
        for (b in state.buildings) if (b.id == id) return b
        for (r in state.resources) if (r.id == id) return r
        return null
    }

    private fun footprintRect(def: BuildingDef, x: Float, y: Float): FloatArray {
        val w = def.placeW.toFloat()
        val h = def.placeH.toFloat()
        val cy = y + def.placeYOffset
        return floatArrayOf(x - w / 2f, cy - h / 2f, x + w / 2f, cy + h / 2f)
    }

    private fun rectIntersect(a: FloatArray, b: FloatArray): Boolean =
        a[0] < b[2] && a[2] > b[0] && a[1] < b[3] && a[3] > b[1]

    fun playSound(name: String, where: Vec2? = null) {
        when (name) {
            "sword" -> sound.sword()
            "arrow" -> sound.arrow()
            "arrowHit" -> sound.arrowHit()
            "heal" -> sound.heal()
            "battle" -> sound.battle()
            "run" -> sound.run()
        }
    }
}

internal fun formationOffset(index: Int, count: Int, spacing: Float): Pair<Float, Float> {
    if (count <= 1) return 0f to 0f
    val cols = ceilSqrt(count)
    val rows = (count + cols - 1) / cols
    return ((index % cols) - (cols - 1) / 2f) * spacing to
        ((index / cols) - (rows - 1) / 2f) * spacing
}

private fun ceilSqrt(n: Int): Int {
    var c = 1
    while (c * c < n) c++
    return c
}

internal fun formatCost(c: Stockpile): String = buildString {
    if (c.wood > 0) append("W${c.wood} ")
    if (c.gold > 0) append("G${c.gold} ")
    if (c.food > 0) append("F${c.food}")
}.trim()

internal fun distance(ax: Float, ay: Float, bx: Float, by: Float): Float =
    hypot(ax - bx, ay - by)

internal fun lerpClamp(v: Float, lo: Float, hi: Float): Float =
    max(lo, min(hi, v))
