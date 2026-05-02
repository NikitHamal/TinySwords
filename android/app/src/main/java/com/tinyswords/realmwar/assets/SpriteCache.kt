package com.tinyswords.realmwar.assets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Lazily decodes pixel-art bitmaps from `assets/`.
 *
 * - PNGs use ARGB_8888 with no upscaling — pixel art renders crisply via
 *   `FilterQuality.None` at the draw site.
 * - Bitmaps are cached in a process-wide [ConcurrentHashMap]; cold starts
 *   pay decode cost once.
 */
class SpriteCache(private val context: Context) {
    private val rawBitmaps = ConcurrentHashMap<String, Bitmap>()
    private val composeBitmaps = ConcurrentHashMap<String, ImageBitmap>()

    /** Decode every required asset upfront so the first frame doesn't stutter. */
    suspend fun preload(progress: (loaded: Int, total: Int) -> Unit) = withContext(Dispatchers.IO) {
        val all = AssetPaths.all
        val total = all.size
        var loaded = 0
        for ((key, path) in all) {
            decodeOrNull(path)?.let {
                rawBitmaps[key] = it
                composeBitmaps[key] = it.asImageBitmap()
            }
            loaded += 1
            progress(loaded, total)
        }
    }

    fun bitmap(key: String): Bitmap? = rawBitmaps[key]
    fun image(key: String): ImageBitmap? = composeBitmaps[key]

    private fun decodeOrNull(path: String): Bitmap? {
        return try {
            context.assets.open(path).use { stream ->
                val opts = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inScaled = false
                    inDither = false
                }
                BitmapFactory.decodeStream(stream, null, opts)
            }
        } catch (io: IOException) {
            Log.w(TAG, "Missing asset: $path")
            null
        } catch (oom: OutOfMemoryError) {
            Log.w(TAG, "OOM decoding $path", oom)
            null
        }
    }

    companion object { private const val TAG = "SpriteCache" }
}
