package com.tinyswords.realmwar.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF

/**
 * Lightweight sprite sheet helper. Most Tiny Swords sheets are square N x N frames laid out in a
 * single row OR a 4-row top-down direction sheet. The renderer figures out frame size and picks
 * the row/column based on facing + animation time.
 */
class SpriteSheet(
    val bitmap: Bitmap,
    val frameWidth: Int,
    val frameHeight: Int,
    val rows: Int = 1,
    val fps: Float = 8f
) {
    val cols: Int get() = (bitmap.width / frameWidth).coerceAtLeast(1)

    fun draw(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        scale: Float,
        time: Float,
        rowIndex: Int = 0,
        paint: Paint
    ) {
        val frame = ((time * fps).toInt() % cols).coerceAtLeast(0)
        val row = rowIndex.coerceIn(0, (rows - 1).coerceAtLeast(0))
        val srcLeft = frame * frameWidth
        val srcTop = row * frameHeight
        val src = Rect(srcLeft, srcTop, srcLeft + frameWidth, srcTop + frameHeight)
        val dstW = frameWidth * scale
        val dstH = frameHeight * scale
        val dst = RectF(cx - dstW / 2f, cy - dstH / 2f, cx + dstW / 2f, cy + dstH / 2f)
        canvas.drawBitmap(bitmap, src, dst, paint)
    }
}
