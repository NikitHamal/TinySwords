package com.tinyswords.app.game.pathfinding

import com.tinyswords.app.game.*
import com.tinyswords.app.game.entities.GameUnit
import com.tinyswords.app.game.entities.PathPoint
import com.tinyswords.app.util.dist
import kotlin.math.*

class Pathfinder(private val state: GameState) {

    data class PathSearchResult(
        val path: List<PathPoint>?,
        val visitedNodes: Int,
        val direct: Boolean,
        val reachable: Boolean
    )

    private val dx = intArrayOf(1, -1, 0, 0, 1, 1, -1, -1)
    private val dy = intArrayOf(0, 0, 1, -1, 1, -1, 1, -1)
    private val moveCost = intArrayOf(10, 10, 10, 10, 14, 14, 14, 14)

    private var cachedCols = 0
    private var cachedRows = 0
    private var searchStamp = 1
    private var gScore = IntArray(0)
    private var parent = IntArray(0)
    private var touched = IntArray(0)
    private var closed = IntArray(0)
    private val heap = IntMinHeap()

    fun findPath(startX: Float, startY: Float, goalX: Float, goalY: Float, maxNodes: Int = 32000): List<PathPoint>? {
        return findPathDetailed(startX, startY, goalX, goalY, maxNodes).path
    }

    fun findPathDetailed(startX: Float, startY: Float, goalX: Float, goalY: Float, maxNodes: Int = 32000): PathSearchResult {
        val pc = state.pathCols
        val pr = state.pathRows
        if (pc <= 0 || pr <= 0 || state.pathGrid.isEmpty()) return PathSearchResult(null, 0, direct = false, reachable = false)
        ensureGrid(pc, pr)
        beginSearch()

        var sc = (startX / PATH_CELL).toInt().coerceIn(0, pc - 1)
        var sr = (startY / PATH_CELL).toInt().coerceIn(0, pr - 1)
        var gc = (goalX / PATH_CELL).toInt().coerceIn(0, pc - 1)
        var gr = (goalY / PATH_CELL).toInt().coerceIn(0, pr - 1)

        val snappedStart = snapToWalkable(sc, sr, 10) ?: return PathSearchResult(null, 0, direct = false, reachable = false)
        sc = snappedStart.first
        sr = snappedStart.second
        val snappedGoal = snapToWalkable(gc, gr, 24)
        if (snappedGoal != null) {
            gc = snappedGoal.first
            gr = snappedGoal.second
        }

        val startIdx = sr * pc + sc
        val goalIdx = gr * pc + gc
        if (startIdx == goalIdx) return PathSearchResult(listOf(PathPoint(goalX, goalY)), 0, direct = false, reachable = true)

        setG(startIdx, 0)
        parent[startIdx] = -1
        heap.clear()
        heap.push(startIdx, octileDistanceCost(sc, sr, gc, gr))

        var visited = 0
        val nodeLimit = maxNodes.coerceAtLeast(1)
        while (!heap.isEmpty() && visited < nodeLimit) {
            val current = heap.pop()
            if (closed[current] == searchStamp) continue
            closed[current] = searchStamp
            visited++

            if (current == goalIdx) {
                return PathSearchResult(reconstructPath(current, goalX, goalY), visited, direct = false, reachable = true)
            }

            val cc = current % pc
            val cr = current / pc
            val currentG = getG(current)
            for (i in 0..7) {
                val nc = cc + dx[i]
                val nr = cr + dy[i]
                if (nc < 0 || nc >= pc || nr < 0 || nr >= pr) continue
                val nIdx = nr * pc + nc
                if (state.pathGrid[nIdx].toInt() != 0) continue
                if (closed[nIdx] == searchStamp) continue

                if (i >= 4) {
                    val sideA = cr * pc + nc
                    val sideB = nr * pc + cc
                    if (state.pathGrid[sideA].toInt() != 0 || state.pathGrid[sideB].toInt() != 0) continue
                }

                val candidateG = currentG + moveCost[i]
                if (touched[nIdx] != searchStamp || candidateG < gScore[nIdx]) {
                    setG(nIdx, candidateG)
                    parent[nIdx] = current
                    heap.push(nIdx, candidateG + octileDistanceCost(nc, nr, gc, gr))
                }
            }
        }

        return PathSearchResult(null, visited, direct = false, reachable = false)
    }

    fun prepareUnitPath(unit: GameUnit, targetX: Float, targetY: Float): List<PathPoint>? {
        return prepareUnitPathDetailed(unit, targetX, targetY).path
    }

    fun prepareUnitPathDetailed(unit: GameUnit, targetX: Float, targetY: Float, maxNodes: Int = 9000): PathSearchResult {
        val d = dist(unit.x, unit.y, targetX, targetY)
        val samples = (d / 96f).toInt().coerceIn(9, 36)
        if (isSegmentWalkable(unit.x, unit.y, targetX, targetY, samples)) {
            return PathSearchResult(null, 0, direct = true, reachable = true)
        }

        val distanceBudget = if (d > 2600f) 9000 else 6000
        val result = findPathDetailed(unit.x, unit.y, targetX, targetY, maxNodes = min(maxNodes, distanceBudget))
        val rawPath = result.path ?: return result
        return result.copy(path = smoothPath(rawPath))
    }

    fun isSegmentWalkable(ax: Float, ay: Float, bx: Float, by: Float, samples: Int = 9): Boolean {
        val pc = state.pathCols
        val pr = state.pathRows
        if (pc <= 0 || pr <= 0 || state.pathGrid.isEmpty()) return false
        for (i in 0..samples) {
            val t = i.toFloat() / samples
            val px = ax + (bx - ax) * t
            val py = ay + (by - ay) * t
            if (state.isWater(px, py)) return false
            val col = (px / PATH_CELL).toInt()
            val row = (py / PATH_CELL).toInt()
            if (col in 0 until pc && row in 0 until pr) {
                if (state.pathGrid[row * pc + col].toInt() != 0) return false
            } else return false
        }
        return true
    }

    private fun ensureGrid(pc: Int, pr: Int) {
        val total = pc * pr
        if (pc == cachedCols && pr == cachedRows && gScore.size == total) return
        cachedCols = pc
        cachedRows = pr
        gScore = IntArray(total)
        parent = IntArray(total)
        touched = IntArray(total)
        closed = IntArray(total)
        heap.ensureCapacity(total.coerceAtMost(32768))
        searchStamp = 1
    }

    private fun beginSearch() {
        searchStamp++
        if (searchStamp == Int.MAX_VALUE) {
            touched.fill(0)
            closed.fill(0)
            searchStamp = 1
        }
    }

    private fun setG(index: Int, value: Int) {
        touched[index] = searchStamp
        gScore[index] = value
    }

    private fun getG(index: Int): Int = if (touched[index] == searchStamp) gScore[index] else Int.MAX_VALUE / 4

    private fun octileDistanceCost(ac: Int, ar: Int, bc: Int, br: Int): Int {
        val hx = abs(bc - ac)
        val hy = abs(br - ar)
        return max(hx, hy) * 10 + min(hx, hy) * 4
    }

    private fun snapToWalkable(col: Int, row: Int, maxDist: Int): Pair<Int, Int>? {
        val pc = state.pathCols
        val pr = state.pathRows
        if (col in 0 until pc && row in 0 until pr && state.pathGrid[row * pc + col].toInt() == 0) return Pair(col, row)
        for (r in 1..maxDist) {
            for (dr in -r..r) for (dc in -r..r) {
                if (abs(dr) != r && abs(dc) != r) continue
                val nc = col + dc
                val nr = row + dr
                if (nc in 0 until pc && nr in 0 until pr && state.pathGrid[nr * pc + nc].toInt() == 0) return Pair(nc, nr)
            }
        }
        return null
    }

    private fun reconstructPath(goalIndex: Int, goalX: Float, goalY: Float): List<PathPoint> {
        val pc = cachedCols
        val result = ArrayList<PathPoint>(96)
        var current = goalIndex
        var guard = 0
        while (current >= 0 && guard < 256) {
            val c = current % pc
            val r = current / pc
            result.add(PathPoint((c + 0.5f) * PATH_CELL, (r + 0.5f) * PATH_CELL))
            current = parent[current]
            guard++
        }
        result.reverse()
        if (result.isNotEmpty()) result[result.lastIndex] = PathPoint(goalX, goalY)
        return result
    }

    private fun smoothPath(path: List<PathPoint>): List<PathPoint> {
        if (path.size <= 2) return path
        val smoothed = ArrayList<PathPoint>(path.size)
        smoothed.add(path[0])
        var i = 0
        while (i < path.size - 1) {
            var furthest = i + 1
            for (j in i + 2 until path.size) {
                if (isSegmentWalkable(path[i].x, path[i].y, path[j].x, path[j].y)) furthest = j else break
            }
            smoothed.add(path[furthest])
            i = furthest
        }
        return smoothed
    }

    private class IntMinHeap(initialCapacity: Int = 256) {
        private var nodes = IntArray(initialCapacity)
        private var priorities = IntArray(initialCapacity)
        private var size = 0

        fun ensureCapacity(capacity: Int) {
            if (nodes.size >= capacity) return
            var newCap = nodes.size
            while (newCap < capacity) newCap *= 2
            nodes = nodes.copyOf(newCap)
            priorities = priorities.copyOf(newCap)
        }

        fun clear() { size = 0 }
        fun isEmpty(): Boolean = size == 0

        fun push(node: Int, priority: Int) {
            if (size >= nodes.size) {
                nodes = nodes.copyOf(nodes.size * 2)
                priorities = priorities.copyOf(priorities.size * 2)
            }
            var i = size++
            while (i > 0) {
                val parentIndex = (i - 1) ushr 1
                if (priorities[parentIndex] <= priority) break
                nodes[i] = nodes[parentIndex]
                priorities[i] = priorities[parentIndex]
                i = parentIndex
            }
            nodes[i] = node
            priorities[i] = priority
        }

        fun pop(): Int {
            val result = nodes[0]
            val lastNode = nodes[--size]
            val lastPriority = priorities[size]
            var i = 0
            while (true) {
                val left = i * 2 + 1
                if (left >= size) break
                val right = left + 1
                var child = left
                if (right < size && priorities[right] < priorities[left]) child = right
                if (priorities[child] >= lastPriority) break
                nodes[i] = nodes[child]
                priorities[i] = priorities[child]
                i = child
            }
            nodes[i] = lastNode
            priorities[i] = lastPriority
            return result
        }
    }
}
