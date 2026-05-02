package com.tinyswords.game

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 8-directional A* on a coarse 32px grid. We rebuild the cost grid lazily from the world's
 * land map plus building/resource footprints — full rebuild is cheap enough at our world size
 * (~30k cells), and far simpler than incremental updates.
 *
 * Mirrors the web game's `js/systems/pathfinding.js`. The web version smooths paths with
 * line-of-sight shortcuts; we do the same in [smooth].
 */
class Pathfinder(val world: WorldData) {
    val cellSize = C.PATH_CELL
    val gw = C.WORLD_W / cellSize
    val gh = C.WORLD_H / cellSize
    private val blocked = BooleanArray(gw * gh)

    fun rebuild(buildings: List<Building>, resources: List<GameResource>) {
        for (i in blocked.indices) blocked[i] = false
        // Water = blocked.
        for (gy in 0 until gh) for (gx in 0 until gw) {
            val px = gx * cellSize + cellSize / 2f
            val py = gy * cellSize + cellSize / 2f
            if (!world.isLandPx(px, py)) blocked[gy * gw + gx] = true
        }
        // Buildings block their footprint.
        for (b in buildings) if (b.alive) {
            val minx = ((b.x - b.w * 0.5f) / cellSize).toInt().coerceAtLeast(0)
            val maxx = ((b.x + b.w * 0.5f) / cellSize).toInt().coerceAtMost(gw - 1)
            val miny = ((b.y - b.h * 0.5f) / cellSize).toInt().coerceAtLeast(0)
            val maxy = ((b.y + b.h * 0.5f) / cellSize).toInt().coerceAtMost(gh - 1)
            for (gy in miny..maxy) for (gx in minx..maxx) blocked[gy * gw + gx] = true
        }
        // Stationary resources also block (animals don't).
        for (r in resources) if (r.alive && r.animal == null) {
            val gx = (r.x / cellSize).toInt().coerceIn(0, gw - 1)
            val gy = (r.y / cellSize).toInt().coerceIn(0, gh - 1)
            blocked[gy * gw + gx] = true
        }
    }

    fun isBlockedPx(x: Float, y: Float): Boolean {
        val gx = (x / cellSize).toInt(); val gy = (y / cellSize).toInt()
        if (gx < 0 || gy < 0 || gx >= gw || gy >= gh) return true
        return blocked[gy * gw + gx]
    }

    /** Find a path from world coords (sx, sy) to (gx, gy). Returns null if no path exists. */
    fun find(sx: Float, sy: Float, gx: Float, gy: Float): MutableList<Float>? {
        val s = pxToCell(sx, sy)
        var g = pxToCell(gx, gy)
        if (s == -1 || g == -1) return null
        // If goal is blocked, find nearest open neighbor (so right-clicking on the edge of a
        // building still issues a usable order).
        if (blocked[g]) {
            g = nearestFree(g) ?: return null
        }
        val open = IntArray(gw * gh)
        val from = IntArray(gw * gh) { -1 }
        val cost = FloatArray(gw * gh) { Float.POSITIVE_INFINITY }
        val priority = FloatArray(gw * gh) { Float.POSITIVE_INFINITY }
        val inOpen = BooleanArray(gw * gh)
        var openSize = 0
        cost[s] = 0f
        priority[s] = heuristic(s, g)
        open[openSize++] = s
        inOpen[s] = true

        var iter = 0
        while (openSize > 0) {
            if (++iter > 32000) return null
            // Pick smallest-priority cell (linear scan; works for our small grid).
            var pickIdx = 0
            for (i in 1 until openSize) if (priority[open[i]] < priority[open[pickIdx]]) pickIdx = i
            val cur = open[pickIdx]
            open[pickIdx] = open[--openSize]
            inOpen[cur] = false
            if (cur == g) return reconstruct(from, cur, sx, sy)
            val cx = cur % gw; val cy = cur / gw
            for (dy in -1..1) for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                val nx = cx + dx; val ny = cy + dy
                if (nx < 0 || ny < 0 || nx >= gw || ny >= gh) continue
                val ni = ny * gw + nx
                if (blocked[ni]) continue
                // Disallow cutting corners through diagonal squeezes.
                if (dx != 0 && dy != 0) {
                    if (blocked[cy * gw + nx] || blocked[ny * gw + cx]) continue
                }
                val step = if (dx != 0 && dy != 0) 1.4142f else 1f
                val ng = cost[cur] + step
                if (ng < cost[ni]) {
                    cost[ni] = ng
                    from[ni] = cur
                    priority[ni] = ng + heuristic(ni, g)
                    if (!inOpen[ni]) { open[openSize++] = ni; inOpen[ni] = true }
                }
            }
        }
        return null
    }

    private fun nearestFree(start: Int): Int? {
        val visited = BooleanArray(gw * gh)
        val queue = ArrayDeque<Int>()
        queue += start; visited[start] = true
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            if (!blocked[cur]) return cur
            val cx = cur % gw; val cy = cur / gw
            for (dy in -1..1) for (dx in -1..1) {
                val nx = cx + dx; val ny = cy + dy
                if (nx < 0 || ny < 0 || nx >= gw || ny >= gh) continue
                val ni = ny * gw + nx
                if (visited[ni]) continue
                visited[ni] = true; queue += ni
            }
        }
        return null
    }

    private fun reconstruct(from: IntArray, end: Int, sx: Float, sy: Float): MutableList<Float> {
        val cells = mutableListOf<Int>()
        var cur = end
        while (cur != -1) { cells += cur; cur = from[cur] }
        cells.reverse()
        // Convert to pixel coords.
        val raw = mutableListOf<Float>()
        raw += sx; raw += sy
        for (c in cells) {
            val px = (c % gw) * cellSize + cellSize * 0.5f
            val py = (c / gw) * cellSize + cellSize * 0.5f
            raw += px; raw += py
        }
        return smooth(raw)
    }

    /** Drop waypoints we can skip via straight-line LOS — yields more natural movement. */
    private fun smooth(raw: MutableList<Float>): MutableList<Float> {
        if (raw.size <= 4) return raw
        val out = mutableListOf<Float>()
        out += raw[0]; out += raw[1]
        var i = 0
        while (i < raw.size - 2) {
            var j = raw.size - 2
            while (j > i + 2) {
                if (lineOfSight(raw[i], raw[i + 1], raw[j], raw[j + 1])) break
                j -= 2
            }
            out += raw[j]; out += raw[j + 1]
            i = j
        }
        return out
    }

    private fun lineOfSight(x0: Float, y0: Float, x1: Float, y1: Float): Boolean {
        val dx = x1 - x0; val dy = y1 - y0
        val steps = max(abs(dx), abs(dy)) / (cellSize * 0.5f)
        val n = max(2, steps.toInt())
        for (k in 0..n) {
            val t = k / n.toFloat()
            if (isBlockedPx(x0 + dx * t, y0 + dy * t)) return false
        }
        return true
    }

    private fun heuristic(a: Int, b: Int): Float {
        val ax = a % gw; val ay = a / gw
        val bx = b % gw; val by = b / gw
        val dx = abs(ax - bx).toFloat(); val dy = abs(ay - by).toFloat()
        return (max(dx, dy) - min(dx, dy)) + 1.4142f * min(dx, dy)
    }

    private fun pxToCell(x: Float, y: Float): Int {
        val gx = (x / cellSize).toInt(); val gy = (y / cellSize).toInt()
        if (gx < 0 || gy < 0 || gx >= gw || gy >= gh) return -1
        return gy * gw + gx
    }
}
