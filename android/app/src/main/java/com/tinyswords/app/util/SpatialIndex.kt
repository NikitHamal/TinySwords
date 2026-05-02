package com.tinyswords.app.util

import com.tinyswords.app.game.entities.GameEntity
import kotlin.math.ceil
import kotlin.math.floor

class SpatialIndex<T : GameEntity>(private val cellSize: Float) {
    private val buckets = HashMap<Long, MutableList<T>>()
    private val resultBuffer = ArrayList<T>(64)

    fun clear() {
        buckets.clear()
    }

    fun insert(entity: T) {
        val key = bucketKey(entity.x, entity.y)
        buckets.getOrPut(key) { mutableListOf() }.add(entity)
    }

    fun rebuild(entities: List<T>) {
        clear()
        for (e in entities) {
            if (!e.dead) insert(e)
        }
    }

    fun query(x: Float, y: Float, radius: Int = 1): List<T> {
        resultBuffer.clear()
        val bx = floor(x / cellSize).toInt()
        val by = floor(y / cellSize).toInt()
        for (dy in -radius..radius) {
            for (dx in -radius..radius) {
                val key = packKey(bx + dx, by + dy)
                buckets[key]?.let { resultBuffer.addAll(it) }
            }
        }
        return resultBuffer
    }

    fun queryRange(x: Float, y: Float, range: Float): List<T> {
        val reach = ceil(range / cellSize).toInt().coerceAtLeast(1)
        return query(x, y, reach)
    }

    fun queryRect(left: Float, top: Float, right: Float, bottom: Float, out: MutableList<T>) {
        out.clear()
        val bx0 = floor(left / cellSize).toInt()
        val by0 = floor(top / cellSize).toInt()
        val bx1 = floor(right / cellSize).toInt()
        val by1 = floor(bottom / cellSize).toInt()
        for (by in by0..by1) {
            for (bx in bx0..bx1) {
                buckets[packKey(bx, by)]?.let { bucket ->
                    for (entity in bucket) {
                        if (entity.x >= left && entity.x <= right && entity.y >= top && entity.y <= bottom) {
                            out.add(entity)
                        }
                    }
                }
            }
        }
    }

    private fun bucketKey(x: Float, y: Float): Long = packKey(floor(x / cellSize).toInt(), floor(y / cellSize).toInt())

    private fun packKey(bx: Int, by: Int): Long = (bx.toLong() shl 32) or (by.toLong() and 0xFFFFFFFFL)
}
