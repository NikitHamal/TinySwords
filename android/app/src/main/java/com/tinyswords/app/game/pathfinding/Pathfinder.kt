package com.tinyswords.app.game.pathfinding

import com.tinyswords.app.game.*
import com.tinyswords.app.game.entities.GameUnit
import com.tinyswords.app.game.entities.PathPoint
import com.tinyswords.app.util.dist
import java.util.*
import kotlin.math.*

class Pathfinder(private val state: GameState) {

    private data class Node(
        val col: Int, val row: Int,
        var g: Int = Int.MAX_VALUE,
        var f: Int = Int.MAX_VALUE,
        var parentCol: Int = -1, var parentRow: Int = -1,
        var open: Boolean = false, var closed: Boolean = false
    )

    private val dx = intArrayOf(1, -1, 0, 0, 1, 1, -1, -1)
    private val dy = intArrayOf(0, 0, 1, -1, 1, -1, 1, -1)
    private val cost = intArrayOf(10, 10, 10, 10, 14, 14, 14, 14)

    fun findPath(startX: Float, startY: Float, goalX: Float, goalY: Float, maxNodes: Int = 32000): List<PathPoint>? {
        val pc = state.pathCols
        val pr = state.pathRows

        var sc = (startX / PATH_CELL).toInt().coerceIn(0, pc - 1)
        var sr = (startY / PATH_CELL).toInt().coerceIn(0, pr - 1)
        var gc = (goalX / PATH_CELL).toInt().coerceIn(0, pc - 1)
        var gr = (goalY / PATH_CELL).toInt().coerceIn(0, pr - 1)

        // Snap to nearest walkable cell if blocked
        val snappedStart = snapToWalkable(sc, sr, 10) ?: return null
        sc = snappedStart.first
        sr = snappedStart.second
        val snappedGoal = snapToWalkable(gc, gr, 24)
        if (snappedGoal != null) { gc = snappedGoal.first; gr = snappedGoal.second }

        if (sc == gc && sr == gr) return listOf(PathPoint(goalX, goalY))

        val nodes = HashMap<Long, Node>(maxNodes / 2)
        val openSet = PriorityQueue<Node>(256, compareBy { it.f })

        fun key(c: Int, r: Int): Long = (c.toLong() shl 32) or (r.toLong() and 0xFFFFFFFFL)
        fun getNode(c: Int, r: Int): Node = nodes.getOrPut(key(c, r)) { Node(c, r) }

        val startNode = getNode(sc, sr)
        startNode.g = 0
        startNode.f = heuristic(sc, sr, gc, gr)
        startNode.open = true
        openSet.add(startNode)

        var visited = 0

        while (openSet.isNotEmpty() && visited < maxNodes) {
            val current = openSet.poll()!!
            if (current.closed) continue
            current.closed = true
            current.open = false
            visited++

            if (current.col == gc && current.row == gr) {
                return reconstructPath(nodes, current, goalX, goalY)
            }

            for (i in 0..7) {
                val nc = current.col + dx[i]
                val nr = current.row + dy[i]

                if (nc < 0 || nc >= pc || nr < 0 || nr >= pr) continue
                if (state.pathGrid[nr * pc + nc].toInt() != 0) continue

                // Diagonal: check adjacent cells
                if (i >= 4) {
                    val c1 = current.col + dx[i]
                    val c2 = current.col
                    val r1 = current.row
                    val r2 = current.row + dy[i]
                    if (c1 in 0 until pc && r1 in 0 until pr && state.pathGrid[r1 * pc + c1].toInt() != 0) continue
                    if (c2 in 0 until pc && r2 in 0 until pr && state.pathGrid[r2 * pc + c2].toInt() != 0) continue
                }

                val neighbor = getNode(nc, nr)
                if (neighbor.closed) continue

                val newG = current.g + cost[i]
                if (newG < neighbor.g) {
                    neighbor.g = newG
                    neighbor.f = newG + heuristic(nc, nr, gc, gr)
                    neighbor.parentCol = current.col
                    neighbor.parentRow = current.row
                    if (!neighbor.open) {
                        neighbor.open = true
                        openSet.add(neighbor)
                    }
                }
            }
        }

        return null // No path found
    }

    fun prepareUnitPath(unit: GameUnit, targetX: Float, targetY: Float): List<PathPoint>? {
        val d = dist(unit.x, unit.y, targetX, targetY)

        // Direct line fast path. Most Tiny Swords movement is across open grass;
        // avoid A* unless sampled terrain/building obstacles actually block the ray.
        val samples = (d / 96f).toInt().coerceIn(9, 36)
        if (isSegmentWalkable(unit.x, unit.y, targetX, targetY, samples)) {
            return null // Move directly
        }

        val budget = if (d > 2600f) 9000 else 6000
        val path = findPath(unit.x, unit.y, targetX, targetY, maxNodes = budget) ?: return null

        // Smooth path
        return smoothPath(path)
    }

    fun isSegmentWalkable(ax: Float, ay: Float, bx: Float, by: Float, samples: Int = 9): Boolean {
        for (i in 0..samples) {
            val t = i.toFloat() / samples
            val px = ax + (bx - ax) * t
            val py = ay + (by - ay) * t
            if (state.isWater(px, py)) return false
            val col = (px / PATH_CELL).toInt()
            val row = (py / PATH_CELL).toInt()
            if (col in 0 until state.pathCols && row in 0 until state.pathRows) {
                if (state.pathGrid[row * state.pathCols + col].toInt() != 0) return false
            }
        }
        return true
    }

    private fun heuristic(ac: Int, ar: Int, bc: Int, br: Int): Int {
        val dx = abs(bc - ac)
        val dy = abs(br - ar)
        return max(dx, dy) * 10 + min(dx, dy) * 4
    }

    private fun snapToWalkable(col: Int, row: Int, maxDist: Int): Pair<Int, Int>? {
        val pc = state.pathCols
        val pr = state.pathRows
        if (col in 0 until pc && row in 0 until pr && state.pathGrid[row * pc + col].toInt() == 0) {
            return Pair(col, row)
        }
        for (r in 1..maxDist) {
            for (dr in -r..r) for (dc in -r..r) {
                if (abs(dr) != r && abs(dc) != r) continue
                val nc = col + dc
                val nr = row + dr
                if (nc in 0 until pc && nr in 0 until pr && state.pathGrid[nr * pc + nc].toInt() == 0) {
                    return Pair(nc, nr)
                }
            }
        }
        return null
    }

    private fun reconstructPath(nodes: Map<Long, Node>, end: Node, goalX: Float, goalY: Float): List<PathPoint> {
        val result = mutableListOf<PathPoint>()
        var current: Node? = end
        fun key(c: Int, r: Int): Long = (c.toLong() shl 32) or (r.toLong() and 0xFFFFFFFFL)

        while (current != null && result.size < 128) {
            result.add(PathPoint(
                (current.col + 0.5f) * PATH_CELL,
                (current.row + 0.5f) * PATH_CELL
            ))
            if (current.parentCol < 0) break
            current = nodes[key(current.parentCol, current.parentRow)]
        }

        result.reverse()

        // Replace last point with exact goal
        if (result.isNotEmpty()) {
            result[result.lastIndex] = PathPoint(goalX, goalY)
        }

        return result
    }

    private fun smoothPath(path: List<PathPoint>): List<PathPoint> {
        if (path.size <= 2) return path
        val smoothed = mutableListOf(path[0])
        var i = 0
        while (i < path.size - 1) {
            var furthest = i + 1
            for (j in i + 2 until path.size) {
                if (isSegmentWalkable(path[i].x, path[i].y, path[j].x, path[j].y)) {
                    furthest = j
                } else break
            }
            smoothed.add(path[furthest])
            i = furthest
        }
        return smoothed
    }
}
