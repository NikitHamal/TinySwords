package com.tinyswords.input

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.tinyswords.game.*

/**
 * Touch input router. Combines pinch-zoom, pan, tap, and long-press into a single Modifier.
 * Tap = select; long-press = context order (move/attack/harvest based on what's under the finger).
 *
 * Multi-touch panning uses [detectTransformGestures]; single-finger pan only kicks in once the
 * user drags past a small threshold so taps still register reliably.
 */
class TouchHandler(
    val game: Game,
    val sim: Simulation,
    val onPlaceBuilding: (BuildingType, Float, Float) -> kotlin.Unit
) {
    var placing: BuildingType? = null
    var canvasW: Float = 1f
    var canvasH: Float = 1f
}

/** A composable Modifier wrapper that wires touch input to the game. */
@Composable
fun Modifier.gameInput(handler: TouchHandler): Modifier = this
    .pointerInput(Unit) {
        detectTransformGestures { _, pan, zoom, _ ->
            handler.game.camera.x -= pan.x / handler.game.camera.zoom
            handler.game.camera.y -= pan.y / handler.game.camera.zoom
            handler.game.camera.targetZoom = (handler.game.camera.targetZoom * zoom).coerceIn(C.ZOOM_MIN, C.ZOOM_MAX)
            handler.game.camera.clamp()
            handler.game.bumpRevision()
        }
    }
    .pointerInput(Unit) {
        detectTapGestures(
            onTap = { off ->
                val (wx, wy) = handler.game.camera.screenToWorld(off.x, off.y)
                if (handler.placing != null) {
                    handler.onPlaceBuilding(handler.placing!!, wx, wy)
                    handler.placing = null
                } else {
                    val ent = pickAt(handler.game, wx, wy)
                    handler.game.selection.clear()
                    if (ent != null) handler.game.selection += ent.id
                }
                handler.game.bumpRevision()
            },
            onLongPress = { off ->
                val (wx, wy) = handler.game.camera.screenToWorld(off.x, off.y)
                issueContextOrder(handler.game, handler.sim, wx, wy)
            }
        )
    }

/** Hit-test in priority order: units, then buildings, then resources. */
fun pickAt(game: Game, wx: Float, wy: Float): Entity? {
    var best: Entity? = null
    var bestD = 32f * 32f
    for (u in game.units) {
        val dx = u.x - wx; val dy = (u.y - 8f) - wy
        val d = dx * dx + dy * dy
        if (d < bestD) { bestD = d; best = u }
    }
    if (best != null) return best
    for (b in game.buildings) {
        if (wx in (b.x - b.w * 0.55f)..(b.x + b.w * 0.55f) &&
            wy in (b.y - b.h * 0.85f)..(b.y + b.h * 0.25f)
        ) return b
    }
    for (r in game.resources) {
        val dx = r.x - wx; val dy = r.y - wy
        if (dx * dx + dy * dy < 32f * 32f) return r
    }
    return null
}

/**
 * Long-press order. The semantics mirror the web build's right-click context:
 *   - tap on enemy entity = attack
 *   - tap on a resource node = harvest (workers) or attack (others)
 *   - tap on a friendly construction site = build/repair
 *   - tap on empty ground = move
 */
fun issueContextOrder(game: Game, sim: Simulation, wx: Float, wy: Float) {
    val owned = game.selection.mapNotNull { game.findEntity(it) }
        .filterIsInstance<GameUnit>()
        .filter { it.faction == game.playerFaction }
    if (owned.isEmpty()) return
    val target = pickAt(game, wx, wy)
    when (target) {
        is GameUnit -> if (target.faction != game.playerFaction)
            owned.forEach { sim.issueAttack(it, target) } else owned.forEach { sim.issueMove(it, wx, wy) }
        is Building -> if (target.faction != game.playerFaction)
            owned.forEach { sim.issueAttack(it, target) }
        else if (target.buildProgress < 1f || target.hp < target.maxHp)
            owned.filter { it.type == UnitType.WORKER }.forEach { sim.issueBuild(it, target) }
        else owned.forEach { sim.issueMove(it, wx, wy) }
        is GameResource -> {
            owned.filter { it.type == UnitType.WORKER }.forEach { sim.issueHarvest(it, target) }
            owned.filter { it.type != UnitType.WORKER }.forEach { sim.issueMove(it, wx, wy) }
        }
        else -> owned.forEach { sim.issueMove(it, wx, wy) }
    }
    game.bumpRevision()
}
