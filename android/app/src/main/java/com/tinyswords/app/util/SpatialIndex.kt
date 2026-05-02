package com.tinyswords.app.util

import com.tinyswords.app.game.entities.GameEntity
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Allocation-light grid index used by the simulation, renderer and touch hit tests.
 *
 * The old rebuild path cleared the map and allocated a new MutableList for every
 * occupied bucket. On larger worlds this happened many times per second and caused
 * visible GC spikes on Android. Bucket lists are now pooled and reused across
 * rebuilds; query APIs keep their reusable result buffer behavior.
 */
class SpatialIndex<T : GameEntity>(private val cellSize: Float) {
    private val buckets = HashMap<Long, MutableList<T>>(256)
    private val bucketPool = ArrayList<MutableList<T>>(256)
    private val resultBuffer = ArrayList<T>(96)

    fun clear() {
        if (buckets.isEmpty()) return
        for (list in buckets.values) {
            list.clear()
            bucketPool.add(list)
        }
        buckets.clear()
    }

    fun insert(entity: T) {
        val key = bucketKey(entity.x, entity.y)
        val list = buckets.getOrPut(key) { obtainBucket() }
        list.add(entity)
    }

    fun rebuild(entities: List<T>) {
        clear()
        for (i in entities.indices) {
            val e = entities[i]
            if (!e.dead) insert(e)
        }
    }

    fun query(x: Float, y: Float, radius: Int = 1): List<T> {
        resultBuffer.clear()
        val bx = floor(x / cellSize).toInt()
        val by = floor(y / cellSize).toInt()
        for (dy in -radius..radius) {
            for (dx in -radius..radius) {
                buckets[packKey(bx + dx, by + dy)]?.let { bucket ->
                    for (i in bucket.indices) resultBuffer.add(bucket[i])
                }
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
                    for (i in bucket.indices) {
                        val entity = bucket[i]
                        if (entity.x >= left && entity.x <= right && entity.y >= top && entity.y <= bottom) {
                            out.add(entity)
                        }
                    }
                }
            }
        }
    }

    private fun obtainBucket(): MutableList<T> {
        return if (bucketPool.isNotEmpty()) bucketPool.removeAt(bucketPool.lastIndex) else ArrayList(12)
    }

    private fun bucketKey(x: Float, y: Float): Long = packKey(floor(x / cellSize).toInt(), floor(y / cellSize).toInt())

    private fun packKey(bx: Int, by: Int): Long = (bx.toLong() shl 32) or (by.toLong() and 0xFFFFFFFFL)
}
