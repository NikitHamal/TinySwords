package com.tinyswords.realmwar.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.tinyswords.realmwar.game.GameConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lazy bitmap cache that loads pixel-art sprites from the in-APK assets folder, which is
 * back-stopped by the existing /assets directory in the repo (configured via sourceSets in
 * build.gradle.kts). Sprites stay in memory once loaded; the wrapper only loads what the renderer
 * actually asks for to avoid blowing the heap.
 */
class AssetLibrary private constructor(
    private val ctx: Context
) {
    private val cache = HashMap<String, Bitmap?>(256)

    suspend fun preloadCore() = withContext(Dispatchers.IO) {
        // Pre-cache the most frequently accessed sprites so the title and the first frames of the
        // game don't stall on the IO dispatcher. Anything missing is logged & treated as a no-op.
        val keys = mutableListOf<String>()
        keys += listOf(
            "Terrain/Tileset/Tilemap_color1.png",
            "Terrain/Tileset/Tilemap_color2.png",
            "Terrain/Tileset/Water Background color.png",
            "Terrain/Resources/Wood/Trees/Tree1.png",
            "Terrain/Resources/Wood/Trees/Tree2.png",
            "Terrain/Resources/Gold/Gold Stones/Gold Stone 1.png",
            "Terrain/Resources/Meat/Meat Resource/Meat Resource.png",
            "Terrain/Decorations/Bushes/Bushe1.png",
            "Terrain/Decorations/Bushes/Bushe2.png",
            "Terrain/Decorations/Rocks/Rock1.png"
        )
        for (f in GameConfig.FACTIONS) {
            keys += "Buildings/${f.folder} Buildings/Castle.png"
            keys += "Buildings/${f.folder} Buildings/House1.png"
            keys += "Buildings/${f.folder} Buildings/Barracks.png"
            keys += "Buildings/${f.folder} Buildings/Archery.png"
            keys += "Buildings/${f.folder} Buildings/Tower.png"
            keys += "Buildings/${f.folder} Buildings/Monastery.png"
            keys += "Units/${f.folder} Units/Pawn/Pawn_Idle.png"
            keys += "Units/${f.folder} Units/Pawn/Pawn_Run.png"
            keys += "Units/${f.folder} Units/Warrior/Warrior_Idle.png"
            keys += "Units/${f.folder} Units/Warrior/Warrior_Run.png"
            keys += "Units/${f.folder} Units/Archer/Archer_Idle.png"
            keys += "Units/${f.folder} Units/Archer/Archer_Run.png"
        }
        keys.forEach { loadInternal("Tiny Swords (Free Pack)/$it") }
    }

    fun get(relativeAssetPath: String): Bitmap? = cache[relativeAssetPath] ?: loadInternal(relativeAssetPath)

    fun building(faction: GameConfig.FactionDef, type: String): Bitmap? {
        val def = GameConfig.BUILDINGS[type] ?: return null
        return get("Tiny Swords (Free Pack)/Buildings/${faction.folder} Buildings/${def.file}")
    }

    fun unit(faction: GameConfig.FactionDef, type: String, action: String): Bitmap? {
        val folder = faction.folder
        val sub = when (type) {
            "worker" -> "Pawn"
            "warrior" -> "Warrior"
            "archer" -> "Archer"
            "lancer" -> "Lancer"
            "monk" -> "Monk"
            else -> return null
        }
        val file = when (type) {
            "worker" -> when (action) {
                "idle" -> "Pawn_Idle.png"
                "run" -> "Pawn_Run.png"
                "wood" -> "Pawn_Run Wood.png"
                "gold" -> "Pawn_Run Gold.png"
                "food" -> "Pawn_Run Meat.png"
                "chop" -> "Pawn_Interact Axe.png"
                "mine" -> "Pawn_Interact Pickaxe.png"
                "build" -> "Pawn_Interact Hammer.png"
                else -> "Pawn_Idle.png"
            }
            "warrior" -> if (action == "run") "Warrior_Run.png" else if (action == "attack") "Warrior_Attack1.png" else "Warrior_Idle.png"
            "archer" -> if (action == "run") "Archer_Run.png" else if (action == "attack") "Archer_Shoot.png" else "Archer_Idle.png"
            "lancer" -> if (action == "run") "Lancer_Run.png" else if (action == "attack") "Lancer_Right_Attack.png" else "Lancer_Idle.png"
            "monk" -> if (action == "run") "Run.png" else if (action == "attack") "Heal.png" else "Idle.png"
            else -> return null
        }
        return get("Tiny Swords (Free Pack)/Units/$folder Units/$sub/$file")
    }

    fun resource(type: String): Bitmap? = when (type) {
        "tree" -> get("Tiny Swords (Free Pack)/Terrain/Resources/Wood/Trees/Tree1.png")
        "gold" -> get("Tiny Swords (Free Pack)/Terrain/Resources/Gold/Gold Stones/Gold Stone 1.png")
        "food" -> get("Tiny Swords (Free Pack)/Terrain/Resources/Meat/Meat Resource/Meat Resource.png")
        else -> null
    }

    fun decor(kind: String): Bitmap? = when {
        kind.startsWith("bush") -> get("Tiny Swords (Free Pack)/Terrain/Decorations/Bushes/Bushe${kind.last()}.png")
        kind.startsWith("rock") -> get("Tiny Swords (Free Pack)/Terrain/Decorations/Rocks/Rock${kind.last()}.png")
        else -> null
    }

    fun arrow(faction: GameConfig.FactionDef): Bitmap? =
        get("Tiny Swords (Free Pack)/Units/${faction.folder} Units/Archer/Arrow.png")

    fun animal(animalKey: String, action: String): Bitmap? {
        val folder = animalKey.replaceFirstChar { it.uppercase() }
        val file = when (animalKey) {
            "deer" -> when (action) { "idle" -> "Deer_Idle.png"; "walk" -> "Deer_Walk.png"; "run" -> "Deer_Run.png"; else -> "Deer_Idle.png" }
            "boar" -> when (action) { "idle" -> "Boar_Idle.png"; "walk" -> "Boar_Walk.png"; "run" -> "Boar_Run.png"; else -> "Boar_Idle.png" }
            "hare" -> when (action) { "idle" -> "Hare_Idle.png"; "walk" -> "Hare_Walk.png"; "run" -> "Hare_Run.png"; else -> "Hare_Idle.png" }
            "fox" -> when (action) { "idle" -> "Fox_Idle.png"; "walk" -> "Fox_walk.png"; "run" -> "Fox_Run.png"; else -> "Fox_Idle.png" }
            "grouse" -> when (action) { "idle" -> "Black_grouse_Idle.png"; "walk" -> "Black_grouse_Walk.png"; "run" -> "Black_grouse_Flight.png"; else -> "Black_grouse_Idle.png" }
            else -> return null
        }
        val animalFolder = if (animalKey == "grouse") "Black_grouse" else folder
        return get("CraftPix Hunt Animals/$animalFolder/$file")
    }

    private fun loadInternal(path: String): Bitmap? {
        if (cache.containsKey(path)) return cache[path]
        val bmp = try {
            ctx.assets.open(path).use { input ->
                BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inScaled = false
                })
            }
        } catch (t: Throwable) {
            null
        }
        cache[path] = bmp
        return bmp
    }

    companion object {
        @Volatile private var instance: AssetLibrary? = null
        fun get(ctx: Context): AssetLibrary = instance ?: synchronized(this) {
            instance ?: AssetLibrary(ctx.applicationContext).also { instance = it }
        }
    }
}
