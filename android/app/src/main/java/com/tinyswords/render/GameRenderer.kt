package com.tinyswords.render

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.tinyswords.game.*
import kotlin.math.*

/**
 * Compose-Canvas based renderer. The DrawScope receives a transformed coordinate space so we
 * can issue draw calls in world coordinates directly. Layering matches the web game:
 *   1. terrain (water + land tiles + tinted biome wash)
 *   2. decorations (rocks/bushes — back layer)
 *   3. resources & animals
 *   4. buildings & units (Y-sorted for correct depth)
 *   5. projectiles
 *   6. effects (particles)
 *   7. selection ring overlay
 *
 * Sprite frames are estimated from sheet dimensions (assumed square frames, 6 cols typical).
 * The web game has the exact frame counts hardcoded; we approximate them so the assets we
 * don't have explicit metadata for still display reasonably.
 */
class GameRenderer(private val sprites: SpriteCache) {

    fun draw(scope: DrawScope, game: Game, viewW: Float, viewH: Float) {
        game.camera.viewW = viewW; game.camera.viewH = viewH
        // Lerp toward target zoom for smooth pinch.
        game.camera.zoom += (game.camera.targetZoom - game.camera.zoom).coerceIn(-0.2f, 0.2f) * 0.25f
        game.camera.clamp()
        val cam = game.camera

        scope.translate(-cam.x * cam.zoom, -cam.y * cam.zoom) {
            scale(cam.zoom, cam.zoom, pivot = Offset(cam.x, cam.y)) {
                drawTerrain(this, game)
                drawDecorBack(this, game)
                drawResources(this, game)
                drawEntitiesYSorted(this, game)
                drawProjectiles(this, game)
                drawEffects(this, game)
                drawSelection(this, game)
            }
        }
    }

    // ------------------------------------------------------------------------------- Terrain

    private fun drawTerrain(scope: DrawScope, game: Game) {
        val w = game.world; val cam = game.camera
        val tileMin = ((cam.x) / C.TILE).toInt() - 1
        val tileMax = ((cam.x + cam.viewW / cam.zoom) / C.TILE).toInt() + 1
        val tileMinY = ((cam.y) / C.TILE).toInt() - 1
        val tileMaxY = ((cam.y + cam.viewH / cam.zoom) / C.TILE).toInt() + 1
        for (ty in max(0, tileMinY)..min(w.rows - 1, tileMaxY)) {
            for (tx in max(0, tileMin)..min(w.cols - 1, tileMax)) {
                val land = w.isLandTile(tx, ty)
                val px = tx * C.TILE.toFloat(); val py = ty * C.TILE.toFloat()
                if (!land) {
                    scope.drawRect(Color(0xFF1F6773), Offset(px, py), Size(C.TILE.toFloat(), C.TILE.toFloat()))
                    // Subtle diagonal shimmer.
                    scope.drawRect(Color(0x261F8FA0), Offset(px, py), Size(C.TILE.toFloat(), C.TILE.toFloat()))
                } else {
                    val tint = biomeColor(w.biomeAt(tx, ty))
                    scope.drawRect(tint, Offset(px, py), Size(C.TILE.toFloat(), C.TILE.toFloat()))
                    // Edge darken if neighbor is water — faux shoreline.
                    if (!w.isLandTile(tx, ty + 1)) {
                        scope.drawRect(Color(0x33000000), Offset(px, py + C.TILE - 6f), Size(C.TILE.toFloat(), 6f))
                    }
                    if (!w.isLandTile(tx - 1, ty)) {
                        scope.drawRect(Color(0x33000000), Offset(px, py), Size(4f, C.TILE.toFloat()))
                    }
                }
            }
        }
    }

    private fun biomeColor(b: Int) = when (b) {
        0 -> Color(0xFF7CAE5E)
        1 -> Color(0xFFB6A65C)
        2 -> Color(0xFF9CB54B)
        3 -> Color(0xFF638A55)
        else -> Color(0xFF82BB6A)
    }

    // ------------------------------------------------------------------------------- Decor

    private fun drawDecorBack(scope: DrawScope, game: Game) {
        val cam = game.camera
        for (d in game.world.decors) {
            if (!inView(d.x, d.y, cam, 200f)) continue
            when (d.kind) {
                DecorKind.ROCK -> sprites.get(Assets.rockSheet(d.x.toInt() and 3))?.let {
                    scope.drawImage(it, IntOffset((d.x - 32).toInt(), (d.y - 32).toInt()))
                } ?: scope.drawCircle(Color(0xFF8B8576), 14f, Offset(d.x, d.y))
                DecorKind.BUSH -> sprites.get(Assets.bushSheet(d.x.toInt() and 3))?.let {
                    val frames = 6 // typical bush sheet
                    drawSprite(scope, it, d.x, d.y, frames, frame = ((game.time * 1.05f).toInt()) % frames)
                } ?: scope.drawCircle(Color(0xFF3A6E33), 18f, Offset(d.x, d.y))
                DecorKind.WATER_ROCK -> sprites.get(Assets.waterRockSheet(d.x.toInt() and 3))?.let {
                    val frames = 4
                    drawSprite(scope, it, d.x, d.y, frames, frame = ((game.time * 3.5f).toInt()) % frames)
                } ?: scope.drawCircle(Color(0xFF555F66), 14f, Offset(d.x, d.y))
            }
        }
    }

    // ------------------------------------------------------------------------------- Resources

    private fun drawResources(scope: DrawScope, game: Game) {
        val cam = game.camera
        for (r in game.resources) {
            if (!inView(r.x, r.y, cam, 200f)) continue
            when (r.kind) {
                ResourceKind.WOOD -> {
                    sprites.get(Assets.treeSheet(r.variant))?.let {
                        // Tree sheets have 4 sway frames + a stump. Use first frame for live trees.
                        val frames = 4
                        val frame = if (r.amount < r.maxAmount * 0.3f) 3 else (game.time.toInt() % frames)
                        drawSprite(scope, it, r.x, r.y - 36, frames, frame, scale = 0.6f)
                    } ?: scope.drawCircle(Color(0xFF2A5A2A), 22f, Offset(r.x, r.y))
                }
                ResourceKind.GOLD -> {
                    sprites.get(Assets.goldSheet(r.variant))?.let {
                        val frames = 6
                        drawSprite(scope, it, r.x, r.y - 16, frames, frame = (game.time * 0.8f).toInt() % frames, scale = 0.6f)
                    } ?: scope.drawCircle(Color(0xFFD8B250), 16f, Offset(r.x, r.y))
                }
                ResourceKind.FOOD -> {
                    if (r.animal != null) {
                        sprites.get(Assets.animalSheet(r.animal!!, r.animState))?.let {
                            val frames = 6
                            drawAnimal(scope, it, r.x, r.y, frames, frame = (r.animTimer * 7f).toInt() % frames, dir = r.animDir)
                        } ?: scope.drawCircle(Color(0xFFB85C42), 12f, Offset(r.x, r.y))
                    } else {
                        sprites.get(Assets.meatSheet())?.let {
                            scope.drawImage(it, IntOffset((r.x - 16).toInt(), (r.y - 16).toInt()))
                        } ?: scope.drawCircle(Color(0xFFB85C42), 10f, Offset(r.x, r.y))
                    }
                }
            }
        }
    }

    // --------------------------------------------------------------------- Buildings + Units (Y-sorted)

    private fun drawEntitiesYSorted(scope: DrawScope, game: Game) {
        val cam = game.camera
        // Combine buildings + units, sort by Y for correct overlap.
        val items = ArrayList<Pair<Float, () -> kotlin.Unit>>(game.units.size + game.buildings.size)
        for (b in game.buildings) {
            if (!inView(b.x, b.y, cam, 320f)) continue
            items += b.y to { drawBuilding(scope, b) }
        }
        for (u in game.units) {
            if (!inView(u.x, u.y, cam, 200f)) continue
            items += u.y to { drawUnit(scope, game, u) }
        }
        items.sortBy { it.first }
        for ((_, run) in items) run()
    }

    private fun drawBuilding(scope: DrawScope, b: Building) {
        val img = sprites.get(Assets.buildingSheet(b.faction, b.type))
        // Building sheets in this pack are static (single frame). Draw centered, anchored to feet.
        if (img != null) {
            val w = b.w; val h = b.h
            val targetW = w * 1.2f
            val targetH = h * 1.4f
            val sx = (b.x - targetW * 0.5f).toInt(); val sy = (b.y - targetH * 0.85f).toInt()
            scope.drawImage(
                img,
                dstOffset = IntOffset(sx, sy),
                dstSize = IntSize(targetW.toInt(), targetH.toInt()),
                filterQuality = FilterQuality.None
            )
        } else {
            // Fallback geometric representation.
            val tint = Color(C.FACTION_COLORS[b.faction].toLong() or 0xFF000000L)
            scope.drawRect(tint, Offset(b.x - b.w * 0.5f, b.y - b.h * 0.7f), Size(b.w, b.h))
        }
        // Construction veil + progress.
        if (b.buildProgress < 1f) {
            scope.drawRect(
                Color(0x80000000), Offset(b.x - b.w * 0.5f, b.y - b.h * 0.85f),
                Size(b.w, b.h * 1.4f * (1f - b.buildProgress))
            )
        }
        // HP bar above building.
        val barW = b.w * 0.8f
        val hpct = (b.hp / b.maxHp).coerceIn(0f, 1f)
        scope.drawRect(Color(0xCC000000), Offset(b.x - barW * 0.5f, b.y - b.h * 0.95f), Size(barW, 6f))
        scope.drawRect(barColor(hpct), Offset(b.x - barW * 0.5f, b.y - b.h * 0.95f), Size(barW * hpct, 6f))
        // Build progress bar.
        if (b.buildProgress < 1f) {
            scope.drawRect(Color(0xCC000000), Offset(b.x - barW * 0.5f, b.y - b.h * 0.95f + 8f), Size(barW, 4f))
            scope.drawRect(Color(0xFFF2CF63), Offset(b.x - barW * 0.5f, b.y - b.h * 0.95f + 8f), Size(barW * b.buildProgress, 4f))
        }
        // Train queue indicator.
        if (b.queue.isNotEmpty()) {
            val q = b.queue.first()
            scope.drawRect(Color(0xCC000000), Offset(b.x - barW * 0.5f, b.y - b.h * 0.95f + 14f), Size(barW, 3f))
            scope.drawRect(Color(0xFF78D777), Offset(b.x - barW * 0.5f, b.y - b.h * 0.95f + 14f), Size(barW * q.progress, 3f))
        }
    }

    private fun drawUnit(scope: DrawScope, game: Game, u: GameUnit) {
        val img = sprites.get(Assets.unitSheet(u.faction, u.type, u.animState))
        // Soft shadow under unit.
        scope.drawOval(Color(0x66000000), Offset(u.x - 12f, u.y + 4f), Size(24f, 8f))
        if (img != null) {
            val frames = 6 // most unit sheets have 6 frames per row
            val rows = 1
            // Anim speed by state.
            val fps = when (u.animState) { 1 -> 9f; 2 -> 8f; 3 -> 7f; else -> 4f }
            val frame = (u.anim * fps).toInt() % frames
            drawSprite(scope, img, u.x, u.y, frames = frames, frame = frame, scale = 0.36f, flipX = u.facing < 0, anchorBottom = true)
        } else {
            val tint = Color(C.FACTION_COLORS[u.faction].toLong() or 0xFF000000L)
            scope.drawCircle(tint, 12f, Offset(u.x, u.y - 6f))
        }
        // HP bar.
        val barW = 28f
        val hpct = (u.hp / u.maxHp).coerceIn(0f, 1f)
        if (hpct < 1f) {
            scope.drawRect(Color(0xCC000000), Offset(u.x - barW * 0.5f, u.y - 32f), Size(barW, 4f))
            scope.drawRect(barColor(hpct), Offset(u.x - barW * 0.5f, u.y - 32f), Size(barW * hpct, 4f))
        }
        // Carrying indicator.
        if (u.carryAmount > 0) {
            val c = when (u.carrying) {
                ResourceKind.WOOD -> Color(0xFF8B5A3C)
                ResourceKind.GOLD -> Color(0xFFF2CF63)
                ResourceKind.FOOD -> Color(0xFFE36B62)
                null -> Color.White
            }
            scope.drawRect(c, Offset(u.x - 4f, u.y - 28f), Size(8f, 6f))
        }
    }

    // ------------------------------------------------------------------------------- Projectiles

    private fun drawProjectiles(scope: DrawScope, game: Game) {
        for (p in game.projectiles) {
            val arrow = sprites.get(Assets.arrowSheet(p.faction))
            if (arrow != null) {
                // Arrows in this pack already face right; we don't rotate, only draw at midpoint.
                scope.drawImage(arrow, IntOffset((p.x - 12).toInt(), (p.y - 4).toInt()))
            } else {
                scope.drawCircle(Color(0xFFFBFFE7), 3f, Offset(p.x, p.y))
            }
        }
    }

    // ------------------------------------------------------------------------------- Effects

    private fun drawEffects(scope: DrawScope, game: Game) {
        for (e in game.effects) {
            val a = ((1f - e.t / e.ttl) * 255).toInt().coerceIn(0, 255)
            when (e.kind) {
                EffectKind.HIT -> scope.drawCircle(Color(0xFFFFC85B).copy(alpha = a / 255f), 8f + e.t * 30f, Offset(e.x, e.y))
                EffectKind.BOOM -> scope.drawCircle(Color(0xFFE36B62).copy(alpha = a / 255f), 12f + e.t * 60f, Offset(e.x, e.y))
                EffectKind.HEAL -> scope.drawCircle(Color(0xFF78D777).copy(alpha = a / 255f), 10f + e.t * 24f, Offset(e.x, e.y))
                EffectKind.DUST -> scope.drawCircle(Color(0x40FFFFFF), 6f + e.t * 12f, Offset(e.x, e.y))
                EffectKind.SPLASH -> scope.drawCircle(Color(0xFFA0E0F0).copy(alpha = a / 255f), 8f + e.t * 18f, Offset(e.x, e.y))
            }
        }
    }

    // ------------------------------------------------------------------------------- Selection

    private fun drawSelection(scope: DrawScope, game: Game) {
        for (id in game.selection) {
            when (val ent = game.findEntity(id)) {
                is GameUnit -> scope.drawCircle(
                    Color(0xFFF2CF63), radius = 18f, center = Offset(ent.x, ent.y + 4f),
                    style = Stroke(width = 2f)
                )
                is Building -> scope.drawRect(
                    Color(0xFFF2CF63),
                    topLeft = Offset(ent.x - ent.w * 0.55f, ent.y - ent.h * 0.85f),
                    size = Size(ent.w * 1.1f, ent.h * 1.1f),
                    style = Stroke(width = 2f)
                )
                else -> {}
            }
        }
    }

    // ------------------------------------------------------------------------------- helpers

    private fun barColor(pct: Float) =
        if (pct > 0.5f) Color(0xFF78D777) else if (pct > 0.25f) Color(0xFFF2CF63) else Color(0xFFE36B62)

    private fun inView(x: Float, y: Float, cam: Camera, pad: Float): Boolean {
        val left = cam.x - pad; val top = cam.y - pad
        val right = cam.x + cam.viewW / cam.zoom + pad
        val bottom = cam.y + cam.viewH / cam.zoom + pad
        return x in left..right && y in top..bottom
    }

    /** Draw a sprite frame from a horizontal sheet. */
    private fun drawSprite(
        scope: DrawScope,
        img: androidx.compose.ui.graphics.ImageBitmap,
        x: Float, y: Float, frames: Int, frame: Int,
        scale: Float = 1f, flipX: Boolean = false, anchorBottom: Boolean = false
    ) {
        val fw = img.width / frames
        val fh = img.height
        val drawW = fw * scale; val drawH = fh * scale
        val left = (x - drawW / 2f).toInt()
        val top = (if (anchorBottom) (y - drawH * 0.85f) else (y - drawH / 2f)).toInt()
        val canvas = scope.drawContext.canvas.nativeCanvas
        val src = android.graphics.Rect(frame * fw, 0, frame * fw + fw, fh)
        val dst = android.graphics.RectF(left.toFloat(), top.toFloat(), (left + drawW), (top + drawH))
        if (flipX) {
            canvas.save()
            canvas.scale(-1f, 1f, x, y)
        }
        // Cast ImageBitmap -> AndroidBitmap.
        val androidBmp = img.asAndroidBitmap()
        if (androidBmp != null) {
            val paint = android.graphics.Paint().apply { isFilterBitmap = false; isAntiAlias = false }
            canvas.drawBitmap(androidBmp, src, dst, paint)
        }
        if (flipX) canvas.restore()
    }

    private fun drawAnimal(
        scope: DrawScope,
        img: androidx.compose.ui.graphics.ImageBitmap,
        x: Float, y: Float,
        frames: Int, frame: Int, dir: Int
    ) {
        // Animal sheets are typically a 4-direction grid: rows = direction (D, U, L, R), cols = frames.
        val rows = 4
        val fw = img.width / frames
        val fh = img.height / rows
        val scale = 0.5f
        val drawW = fw * scale; val drawH = fh * scale
        val left = (x - drawW / 2f).toInt(); val top = (y - drawH * 0.8f).toInt()
        val canvas = scope.drawContext.canvas.nativeCanvas
        val row = dir.coerceIn(0, rows - 1)
        val src = android.graphics.Rect(frame * fw, row * fh, frame * fw + fw, row * fh + fh)
        val dst = android.graphics.RectF(left.toFloat(), top.toFloat(), left + drawW, top + drawH)
        val androidBmp = img.asAndroidBitmap() ?: return
        val paint = android.graphics.Paint().apply { isFilterBitmap = false; isAntiAlias = false }
        canvas.drawBitmap(androidBmp, src, dst, paint)
    }
}
