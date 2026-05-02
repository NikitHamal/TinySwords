package com.tinyswords.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * Loads & caches sprites from the asset pack on demand. The asset pack is shared with the
 * web build via gradle's `assets.srcDirs` — see app/build.gradle.kts.
 *
 * All assets are pixel-art PNGs; we load them with `inScaled = false` to keep their native
 * pixel dimensions (no DPI scaling) so the renderer can draw them with nearest-neighbor
 * sampling for that crisp retro look.
 */
class SpriteCache(private val ctx: Context) {
    private val cache = HashMap<String, ImageBitmap>()
    private val missing = HashSet<String>()

    /** Returns the bitmap for [path] or null if it's missing from assets. */
    fun get(path: String): ImageBitmap? {
        cache[path]?.let { return it }
        if (missing.contains(path)) return null
        return try {
            ctx.assets.open(path).use { stream ->
                val opts = BitmapFactory.Options().apply { inScaled = false; inPreferredConfig = Bitmap.Config.ARGB_8888 }
                val bmp = BitmapFactory.decodeStream(stream, null, opts)
                if (bmp == null) {
                    missing += path; null
                } else {
                    val ib = bmp.asImageBitmap()
                    cache[path] = ib; ib
                }
            }
        } catch (e: Exception) {
            // Asset packs sometimes have inconsistent capitalization (e.g. "Bushe1" vs "Bush1") —
            // log once at INFO so the renderer can fall back to a colored placeholder silently.
            if (path !in missing) {
                Log.i("SpriteCache", "missing asset: $path (${e.message})")
                missing += path
            }
            null
        }
    }

    fun preload(paths: List<String>) {
        for (p in paths) get(p)
    }
}

/**
 * Canonical asset paths. Keep these in one place so renaming a sprite is a one-line change.
 * Paths follow the `assets/Tiny Swords (Free Pack)/...` layout that ships with the web game.
 */
object Assets {
    private const val TS = "Tiny Swords (Free Pack)"

    private val factionFolder = arrayOf("Blue Units", "Red Units", "Yellow Units", "Purple Units", "Black Units")
    private val factionBuilding = arrayOf("Blue Buildings", "Red Buildings", "Yellow Buildings", "Purple Buildings", "Black Buildings")
    private val arrowColor = arrayOf("blue", "red", "yellow", "purple", "black")

    fun unitSheet(faction: Int, type: com.tinyswords.game.UnitType, state: Int): String {
        val ff = factionFolder[faction.coerceIn(0, 4)]
        return when (type) {
            com.tinyswords.game.UnitType.WORKER -> when (state) {
                1 -> "$TS/Units/$ff/Pawn/Pawn_Run.png"
                3 -> "$TS/Units/$ff/Pawn/Pawn_Interact Hammer.png"
                else -> "$TS/Units/$ff/Pawn/Pawn_Idle.png"
            }
            com.tinyswords.game.UnitType.WARRIOR -> when (state) {
                1 -> "$TS/Units/$ff/Warrior/Warrior_Run.png"
                2 -> "$TS/Units/$ff/Warrior/Warrior_Attack1.png"
                else -> "$TS/Units/$ff/Warrior/Warrior_Idle.png"
            }
            com.tinyswords.game.UnitType.ARCHER -> when (state) {
                1 -> "$TS/Units/$ff/Archer/Archer_Run.png"
                2 -> "$TS/Units/$ff/Archer/Archer_Shoot.png"
                else -> "$TS/Units/$ff/Archer/Archer_Idle.png"
            }
            com.tinyswords.game.UnitType.LANCER -> when (state) {
                1 -> "$TS/Units/$ff/Lancer/Lancer_Run.png"
                2 -> "$TS/Units/$ff/Lancer/Lancer_Right_Attack.png"
                else -> "$TS/Units/$ff/Lancer/Lancer_Idle.png"
            }
            com.tinyswords.game.UnitType.MONK -> when (state) {
                1 -> "$TS/Units/$ff/Monk/Run.png"
                2, 4 -> "$TS/Units/$ff/Monk/Heal.png"
                else -> "$TS/Units/$ff/Monk/Idle.png"
            }
        }
    }

    fun buildingSheet(faction: Int, type: com.tinyswords.game.BuildingType): String {
        val ff = factionBuilding[faction.coerceIn(0, 4)]
        val name = when (type) {
            com.tinyswords.game.BuildingType.CASTLE -> "Castle.png"
            com.tinyswords.game.BuildingType.HOUSE -> "House1.png"
            com.tinyswords.game.BuildingType.BARRACKS -> "Barracks.png"
            com.tinyswords.game.BuildingType.ARCHERY -> "Archery.png"
            com.tinyswords.game.BuildingType.TOWER -> "Tower.png"
            com.tinyswords.game.BuildingType.MONASTERY -> "Monastery.png"
        }
        return "$TS/Buildings/$ff/$name"
    }

    fun treeSheet(variant: Int) = "$TS/Terrain/Resources/Wood/Trees/Tree${(variant % 4) + 1}.png"
    fun goldSheet(variant: Int) = "$TS/Terrain/Resources/Gold/Gold Stone ${(variant % 6) + 1}.png"
    fun meatSheet() = "$TS/Terrain/Resources/Meat/Meat Resource/Meat Resource.png"

    fun animalSheet(kind: com.tinyswords.game.AnimalKind, state: Int): String {
        val folder = when (kind) {
            com.tinyswords.game.AnimalKind.DEER -> "Deer"
            com.tinyswords.game.AnimalKind.BOAR -> "Boar"
            com.tinyswords.game.AnimalKind.HARE -> "Hare"
            com.tinyswords.game.AnimalKind.FOX -> "Fox"
            com.tinyswords.game.AnimalKind.GROUSE -> "Black_grouse"
            com.tinyswords.game.AnimalKind.SHEEP -> "Sheep"
        }
        if (kind == com.tinyswords.game.AnimalKind.SHEEP)
            return "$TS/Terrain/Resources/Meat/Sheep/Sheep_Idle.png"
        val s = when (state) { 1 -> "Walk"; 2 -> "Run"; else -> "Idle" }
        val name = when (kind) {
            com.tinyswords.game.AnimalKind.GROUSE -> "Black_grouse_$s.png"
            com.tinyswords.game.AnimalKind.DEER -> "Deer_$s.png"
            com.tinyswords.game.AnimalKind.BOAR -> "Boar_$s.png"
            com.tinyswords.game.AnimalKind.HARE -> "Hare_$s.png"
            com.tinyswords.game.AnimalKind.FOX -> "Fox_$s.png"
            else -> "Sheep_Idle.png"
        }
        return "CraftPix Hunt Animals/$folder/$name"
    }

    fun arrowSheet(faction: Int) = "$TS/Effects/${arrowColor[faction.coerceIn(0, 4)]}Arrow.png"

    fun rockSheet(variant: Int) = "$TS/Terrain/Decorations/Rocks/Rock${(variant % 4) + 1}.png"
    fun bushSheet(variant: Int) = "$TS/Terrain/Decorations/Bushes/Bushe${(variant % 4) + 1}.png"
    fun waterRockSheet(variant: Int) =
        "$TS/Terrain/Decorations/Rocks in the Water/Water Rocks_0${(variant % 4) + 1}.png"

    fun tileset(biome: Int) = "$TS/Terrain/Tileset/Tilemap_color${(biome % 5) + 1}.png"
    fun water() = "$TS/Terrain/Tileset/Water Background color.png"
}
