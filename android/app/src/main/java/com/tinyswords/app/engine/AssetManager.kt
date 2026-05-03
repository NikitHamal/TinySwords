package com.tinyswords.app.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.IOException

/**
 * Lightweight asset loader for the standalone native Android version.
 *
 * The previous app attempted to preload a very large portion of the Tiny Swords
 * pack and also referenced several web asset paths incorrectly. That caused
 * heavy memory pressure and many ugly fallback drawings. This loader keeps a
 * canonical key -> asset path manifest, loads sprites lazily, and remembers
 * missing keys so the render loop never retries failed loads every frame.
 */
class AssetManager(private val context: Context) {
    private val cache = HashMap<String, Bitmap>(128)
    private val missing = HashSet<String>()
    private val paths = HashMap<String, String>(256)
    private val opaqueKeys = HashSet<String>()

    private val opts = BitmapFactory.Options().apply {
        inScaled = false
        inDither = false
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    private val optsOpaque = BitmapFactory.Options().apply {
        inScaled = false
        inDither = false
        inPreferredConfig = Bitmap.Config.RGB_565
    }

    init {
        registerTerrain()
        registerResources()
        registerDecor()
        registerBuildings()
        registerUnits()
        registerAnimals()
        registerUiAndFx()
    }

    fun preload() {
        // Keep startup stable: preload only assets that are required immediately
        // for the first frame. Units/buildings/resources are loaded on demand.
        val critical = listOf("tileGrass", "tileWarm", "tileAlt", "tileMoss", "tileDeep", "water", "waterFoam", "shadow")
        critical.forEach { get(it) }
    }

    fun getOrLoad(path: String): Bitmap? {
        cache[path]?.let { if (!it.isRecycled) return it }
        if (missing.contains(path)) return null
        return loadFromAssets(path, opts)?.also { cache[path] = it } ?: run {
            missing.add(path)
            null
        }
    }

    private fun loadFromAssets(path: String, opts: BitmapFactory.Options): Bitmap? {
        return try {
            context.assets.open(path).use { stream -> BitmapFactory.decodeStream(stream, null, opts) }
        } catch (_: IOException) {
            null
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun add(key: String, path: String) {
        paths[key] = path
    }

    private fun addOpaque(key: String, path: String) {
        paths[key] = path
        opaqueKeys.add(key)
    }

    fun get(key: String): Bitmap? {
        cache[key]?.let { if (!it.isRecycled) return it }
        if (missing.contains(key)) return null
        val path = paths[key] ?: run {
            missing.add(key)
            return null
        }
        val useOpaqueOpts = opaqueKeys.contains(key)
        val decodeOpts = if (useOpaqueOpts) optsOpaque else opts
        val bitmap = loadFromAssets(path, decodeOpts)
        return if (bitmap != null) {
            cache[key] = bitmap
            bitmap
        } else {
            missing.add(key)
            null
        }
    }

    fun registeredKeys(): List<String> = paths.keys.toList()

    fun pathForKey(key: String): String? = paths[key]

    fun decodeForTexture(key: String): Bitmap? {
        val path = paths[key] ?: return null
        val decodeOpts = if (opaqueKeys.contains(key)) optsOpaque else opts
        return loadFromAssets(path, decodeOpts)
    }

    fun textureBounds(key: String): Pair<Int, Int>? {
        val path = paths[key] ?: return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.assets.open(path).use { stream -> BitmapFactory.decodeStream(stream, null, bounds) }
            if (bounds.outWidth > 0 && bounds.outHeight > 0) Pair(bounds.outWidth, bounds.outHeight) else null
        } catch (_: IOException) {
            null
        } catch (_: RuntimeException) {
            null
        }
    }

    fun releaseDecodedBitmaps() {
        for ((_, bitmap) in cache) {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
        cache.clear()
    }

    private fun registerTerrain() {
        val tileDir = "Tiny Swords (Free Pack)/Terrain/Tileset"
        addOpaque("tileGrass", "$tileDir/Tilemap_color1.png")
        addOpaque("tileWarm", "$tileDir/Tilemap_color2.png")
        addOpaque("tileAlt", "$tileDir/Tilemap_color3.png")
        addOpaque("tileMoss", "$tileDir/Tilemap_color4.png")
        addOpaque("tileDeep", "$tileDir/Tilemap_color5.png")
        addOpaque("water", "$tileDir/Water Background color.png")
        add("waterFoam", "$tileDir/Water Foam.png")
        add("shadow", "$tileDir/Shadow.png")
    }

    private fun registerResources() {
        val resDir = "Tiny Swords (Free Pack)/Terrain/Resources"
        for (i in 1..4) add("tree$i", "$resDir/Wood/Trees/Tree$i.png")
        for (i in 1..4) add("stump$i", "$resDir/Wood/Trees/Stump $i.png")
        add("resWood", "$resDir/Wood/Wood Resource/Wood Resource.png")
        add("resGold", "$resDir/Gold/Gold Resource/Gold_Resource.png")
        add("resFood", "$resDir/Meat/Meat Resource/Meat Resource.png")
        for (i in 1..6) {
            add("gold$i", "$resDir/Gold/Gold Stones/Gold Stone $i.png")
            add("gold${i}_hl", "$resDir/Gold/Gold Stones/Gold Stone ${i}_Highlight.png")
        }
        add("meat", "$resDir/Meat/Meat Resource/Meat Resource.png")
        add("sheepIdle", "$resDir/Meat/Sheep/Sheep_Idle.png")
        add("sheepMove", "$resDir/Meat/Sheep/Sheep_Move.png")
        add("sheepGrass", "$resDir/Meat/Sheep/Sheep_Grass.png")
    }

    private fun registerDecor() {
        val decorDir = "Tiny Swords (Free Pack)/Terrain/Decorations"
        for (i in 1..4) add("bush$i", "$decorDir/Bushes/Bushe$i.png")
        for (i in 1..4) add("rock$i", "$decorDir/Rocks/Rock$i.png")
        for (i in 1..8) add("cloud$i", "$decorDir/Clouds/Clouds_${i.toString().padStart(2, '0')}.png")
        for (i in 1..4) add("waterRock$i", "$decorDir/Rocks in the Water/Water Rocks_${i.toString().padStart(2, '0')}.png")
        add("rubberDuck", "$decorDir/Rubber Duck/Rubber duck.png")
    }

    private fun registerBuildings() {
        val factions = listOf("Blue" to "blue", "Red" to "red", "Yellow" to "yellow", "Purple" to "purple", "Black" to "black")
        val files = mapOf(
            "castle" to "Castle.png",
            "house" to "House1.png",
            "house2" to "House2.png",
            "house3" to "House3.png",
            "barracks" to "Barracks.png",
            "archery" to "Archery.png",
            "tower" to "Tower.png",
            "monastery" to "Monastery.png"
        )
        for ((folder, key) in factions) {
            val bDir = "Tiny Swords (Free Pack)/Buildings/$folder Buildings"
            for ((type, file) in files) add("b_${key}_$type", "$bDir/$file")
        }
    }

    private fun registerUnits() {
        val factions = listOf("Blue" to "blue", "Red" to "red", "Yellow" to "yellow", "Purple" to "purple", "Black" to "black")
        for ((folder, key) in factions) {
            val uDir = "Tiny Swords (Free Pack)/Units/$folder Units"
            add("u_${key}_worker_idle", "$uDir/Pawn/Pawn_Idle.png")
            add("u_${key}_worker_run", "$uDir/Pawn/Pawn_Run.png")
            add("u_${key}_worker_chop", "$uDir/Pawn/Pawn_Interact Axe.png")
            add("u_${key}_worker_mine", "$uDir/Pawn/Pawn_Interact Pickaxe.png")
            add("u_${key}_worker_build", "$uDir/Pawn/Pawn_Interact Hammer.png")
            add("u_${key}_worker_fight", "$uDir/Pawn/Pawn_Interact Knife.png")
            add("u_${key}_worker_carry_wood", "$uDir/Pawn/Pawn_Run Wood.png")
            add("u_${key}_worker_carry_gold", "$uDir/Pawn/Pawn_Run Gold.png")
            add("u_${key}_worker_carry_food", "$uDir/Pawn/Pawn_Run Meat.png")
            add("u_${key}_worker_idle_wood", "$uDir/Pawn/Pawn_Idle Wood.png")
            add("u_${key}_worker_idle_gold", "$uDir/Pawn/Pawn_Idle Gold.png")
            add("u_${key}_worker_idle_food", "$uDir/Pawn/Pawn_Idle Meat.png")

            add("u_${key}_warrior_idle", "$uDir/Warrior/Warrior_Idle.png")
            add("u_${key}_warrior_run", "$uDir/Warrior/Warrior_Run.png")
            add("u_${key}_warrior_attack", "$uDir/Warrior/Warrior_Attack1.png")

            add("u_${key}_archer_idle", "$uDir/Archer/Archer_Idle.png")
            add("u_${key}_archer_run", "$uDir/Archer/Archer_Run.png")
            add("u_${key}_archer_shoot", "$uDir/Archer/Archer_Shoot.png")
            add("u_${key}_arrow", "$uDir/Archer/Arrow.png")

            add("u_${key}_lancer_idle", "$uDir/Lancer/Lancer_Idle.png")
            add("u_${key}_lancer_run", "$uDir/Lancer/Lancer_Run.png")
            add("u_${key}_lancer_attack", "$uDir/Lancer/Lancer_Right_Attack.png")

            add("u_${key}_monk_idle", "$uDir/Monk/Idle.png")
            add("u_${key}_monk_run", "$uDir/Monk/Run.png")
            add("u_${key}_monk_heal", "$uDir/Monk/Heal.png")
            add("u_${key}_monk_attack", "$uDir/Monk/Heal.png")
        }
    }

    private fun registerAnimals() {
        val baseDir = "CraftPix Hunt Animals"
        add("animalDeerIdle", "$baseDir/Deer/Deer_Idle.png")
        add("animalDeerWalk", "$baseDir/Deer/Deer_Walk.png")
        add("animalDeerRun", "$baseDir/Deer/Deer_Run.png")
        add("animalDeerHurt", "$baseDir/Deer/Deer_Hurt.png")
        add("animalDeerDeath", "$baseDir/Deer/Deer_Death.png")
        add("animalDeerShadow", "$baseDir/Deer/Deer_Shadow.png")

        add("animalBoarIdle", "$baseDir/Boar/Boar_Idle.png")
        add("animalBoarWalk", "$baseDir/Boar/Boar_Walk.png")
        add("animalBoarRun", "$baseDir/Boar/Boar_Run.png")
        add("animalBoarHurt", "$baseDir/Boar/Boar_Hurt.png")
        add("animalBoarDeath", "$baseDir/Boar/Boar_Death.png")
        add("animalBoarAttack", "$baseDir/Boar/Boar_Attack.png")
        add("animalBoarShadow", "$baseDir/Boar/Boar_shadow.png")

        add("animalHareIdle", "$baseDir/Hare/Hare_Idle.png")
        add("animalHareWalk", "$baseDir/Hare/Hare_Walk.png")
        add("animalHareRun", "$baseDir/Hare/Hare_Run.png")
        add("animalHareHurt", "$baseDir/Hare/Hare_Hurt.png")
        add("animalHareDeath", "$baseDir/Hare/Hare_Death.png")
        add("animalHareShadow", "$baseDir/Hare/Hare_Shadow.png")

        add("animalFoxIdle", "$baseDir/Fox/Fox_Idle.png")
        add("animalFoxWalk", "$baseDir/Fox/Fox_walk.png")
        add("animalFoxRun", "$baseDir/Fox/Fox_Run.png")
        add("animalFoxHurt", "$baseDir/Fox/Fox_Hurt.png")
        add("animalFoxDeath", "$baseDir/Fox/Fox_Death.png")
        add("animalFoxShadow", "$baseDir/Fox/Fox_Shadow.png")

        add("animalGrouseIdle", "$baseDir/Black_grouse/Black_grouse_Idle.png")
        add("animalGrouseWalk", "$baseDir/Black_grouse/Black_grouse_Walk.png")
        add("animalGrouseRun", "$baseDir/Black_grouse/Black_grouse_Flight.png")
        add("animalGrouseFlight", "$baseDir/Black_grouse/Black_grouse_Flight.png")
        add("animalGrouseHurt", "$baseDir/Black_grouse/Black_grouse_Hurt.png")
        add("animalGrouseDeath", "$baseDir/Black_grouse/Black_grouse_Death.png")
        add("animalGrouseShadow", "$baseDir/Black_grouse/Black_grouse_Shadow.png")
    }

    private fun registerUiAndFx() {
        val uiDir = "Tiny Swords (Free Pack)/UI Elements/UI Elements"
        add("uiBarBase", "$uiDir/Bars/SmallBar_Base.png")
        add("uiBarFill", "$uiDir/Bars/SmallBar_Fill.png")
        add("iconMove", "$uiDir/Icons/Icon_01.png")
        add("iconBuild", "$uiDir/Icons/Icon_08.png")
        add("iconStop", "$uiDir/Buttons/TinyRoundRedButton.png")
        add("iconAttack", "$uiDir/Swords/Swords.png")
        add("cursorSelect", "$uiDir/Cursors/Cursor_04.png")
        add("cursorAction", "$uiDir/Cursors/Cursor_02.png")
        add("dust", "Tiny Swords (Free Pack)/Particle FX/Dust_01.png")
        add("explosion", "Tiny Swords (Free Pack)/Particle FX/Explosion_01.png")
        add("fire", "Tiny Swords (Free Pack)/Particle FX/Fire_03.png")
        add("waterSplash", "Tiny Swords (Free Pack)/Particle FX/Water Splash.png")
    }

    fun destroy() {
        for ((_, bitmap) in cache) {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
        cache.clear()
        missing.clear()
    }
}
