package com.tinyswords.app.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import java.io.IOException

class AssetManager(private val context: Context) {
    private val cache = HashMap<String, Bitmap>(256)
    private val opts = BitmapFactory.Options().apply {
        inScaled = false
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }

    fun preload() {
        // Preload critical assets on background thread
        loadTerrainTiles()
        loadUnitSprites()
        loadBuildingSprites()
        loadResourceSprites()
        loadUISprites()
        loadEffectSprites()
        loadAnimalSprites()
        loadDecorSprites()
    }

    fun get(key: String): Bitmap? = cache[key]

    fun getOrLoad(path: String): Bitmap? {
        cache[path]?.let { return it }
        return loadFromAssets(path)?.also { cache[path] = it }
    }

    private fun loadFromAssets(path: String): Bitmap? {
        return try {
            context.assets.open(path).use { stream ->
                BitmapFactory.decodeStream(stream, null, opts)
            }
        } catch (e: IOException) {
            null
        }
    }

    private fun load(key: String, path: String) {
        loadFromAssets(path)?.let { cache[key] = it }
    }

    private fun loadTerrainTiles() {
        val tileDir = "Tiny Swords (Free Pack)/Terrain/Tileset"
        load("tileGrass", "$tileDir/Tilemap_color1.png")
        load("tileWarm", "$tileDir/Tilemap_color2.png")
        load("tileAlt", "$tileDir/Tilemap_color3.png")
        load("tileMoss", "$tileDir/Tilemap_color4.png")
        load("tileDeep", "$tileDir/Tilemap_color5.png")
        load("water", "$tileDir/Water.png")
        load("waterFoam", "$tileDir/Foam.png")
    }

    private fun loadUnitSprites() {
        val factions = listOf("Blue", "Red", "Yellow", "Purple", "Black")
        val factionKeys = listOf("blue", "red", "yellow", "purple", "black")
        val baseDir = "Tiny Swords (Free Pack)/Units"

        for ((fi, fName) in factions.withIndex()) {
            val fKey = factionKeys[fi]
            val uDir = "$baseDir/$fName Units"

            // Warrior
            load("u_${fKey}_warrior_idle", "$uDir/Warrior/Warrior_Idle.png")
            load("u_${fKey}_warrior_run", "$uDir/Warrior/Warrior_Run.png")
            load("u_${fKey}_warrior_attack", "$uDir/Warrior/Warrior_Attack1.png")

            // Archer
            load("u_${fKey}_archer_idle", "$uDir/Archer/Archer_Idle.png")
            load("u_${fKey}_archer_run", "$uDir/Archer/Archer_Run.png")
            load("u_${fKey}_archer_shoot", "$uDir/Archer/Archer_Shoot.png")
            load("u_${fKey}_arrow", "$uDir/Archer/Arrow.png")

            // Worker (Pawn)
            load("u_${fKey}_worker_idle", "$uDir/Pawn/Pawn_Idle.png")
            load("u_${fKey}_worker_run", "$uDir/Pawn/Pawn_Run.png")
            load("u_${fKey}_worker_chop", "$uDir/Pawn/Pawn_Interact Axe.png")
            load("u_${fKey}_worker_mine", "$uDir/Pawn/Pawn_Interact Pickaxe.png")
            load("u_${fKey}_worker_build", "$uDir/Pawn/Pawn_Interact Hammer.png")
            load("u_${fKey}_worker_fight", "$uDir/Pawn/Pawn_Interact Knife.png")
            load("u_${fKey}_worker_carry_wood", "$uDir/Pawn/Pawn_Run Wood.png")
            load("u_${fKey}_worker_carry_gold", "$uDir/Pawn/Pawn_Run Gold.png")
            load("u_${fKey}_worker_carry_food", "$uDir/Pawn/Pawn_Run Meat.png")
            load("u_${fKey}_worker_idle_wood", "$uDir/Pawn/Pawn_Idle Wood.png")
            load("u_${fKey}_worker_idle_gold", "$uDir/Pawn/Pawn_Idle Gold.png")
            load("u_${fKey}_worker_idle_food", "$uDir/Pawn/Pawn_Idle Meat.png")

            // Lancer
            load("u_${fKey}_lancer_idle", "$uDir/Lancer/Lancer_Idle.png")
            load("u_${fKey}_lancer_run", "$uDir/Lancer/Lancer_Run.png")
            load("u_${fKey}_lancer_attack", "$uDir/Lancer/Lancer_Right_Attack.png")

            // Monk
            load("u_${fKey}_monk_idle", "$uDir/Monk/Idle.png")
            load("u_${fKey}_monk_run", "$uDir/Monk/Run.png")
            load("u_${fKey}_monk_heal", "$uDir/Monk/Heal.png")
        }
    }

    private fun loadBuildingSprites() {
        val factions = listOf("Blue", "Red", "Yellow", "Purple", "Black")
        val factionKeys = listOf("blue", "red", "yellow", "purple", "black")
        val baseDir = "Tiny Swords (Free Pack)/Buildings"

        for ((fi, fName) in factions.withIndex()) {
            val fKey = factionKeys[fi]
            val bDir = "$baseDir/$fName Buildings"

            load("b_${fKey}_castle", "$bDir/Castle.png")
            load("b_${fKey}_house", "$bDir/House.png")
            load("b_${fKey}_barracks", "$bDir/Barracks.png")
            load("b_${fKey}_archery", "$bDir/Archery.png")
            load("b_${fKey}_tower", "$bDir/Tower.png")
            load("b_${fKey}_monastery", "$bDir/Monastery.png")
        }
    }

    private fun loadResourceSprites() {
        val resDir = "Tiny Swords (Free Pack)/Terrain/Resources"

        // Trees
        for (i in 1..4) {
            load("tree$i", "$resDir/Wood/Trees/Tree0$i.png")
        }
        load("stump1", "$resDir/Wood/Wood Resource/Stump01.png")
        load("stump2", "$resDir/Wood/Wood Resource/Stump02.png")

        // Gold
        for (i in 1..6) {
            load("gold$i", "$resDir/Gold/Gold Stones/GoldStone0$i.png")
        }

        // Meat
        load("meat", "$resDir/Meat/Meat Resource/Meat01.png")

        // Sheep
        load("sheepIdle", "$resDir/Meat/Sheep/Sheep_Idle.png")
        load("sheepMove", "$resDir/Meat/Sheep/Sheep_Move.png")
    }

    private fun loadAnimalSprites() {
        val baseDir = "CraftPix Hunt Animals"

        // Deer
        load("animalDeerIdle", "$baseDir/Deer/Deer_Idle.png")
        load("animalDeerWalk", "$baseDir/Deer/Deer_Walk.png")
        load("animalDeerRun", "$baseDir/Deer/Deer_Run.png")
        load("animalDeerHurt", "$baseDir/Deer/Deer_Hurt.png")
        load("animalDeerDeath", "$baseDir/Deer/Deer_Death.png")
        load("animalDeerShadow", "$baseDir/Deer/Deer_Shadow.png")

        // Boar
        load("animalBoarIdle", "$baseDir/Boar/Boar_Idle.png")
        load("animalBoarWalk", "$baseDir/Boar/Boar_Walk.png")
        load("animalBoarRun", "$baseDir/Boar/Boar_Run.png")
        load("animalBoarHurt", "$baseDir/Boar/Boar_Hurt.png")
        load("animalBoarDeath", "$baseDir/Boar/Boar_Death.png")
        load("animalBoarAttack", "$baseDir/Boar/Boar_Attack.png")

        // Hare
        load("animalHareIdle", "$baseDir/Hare/Hare_Idle.png")
        load("animalHareWalk", "$baseDir/Hare/Hare_Walk.png")
        load("animalHareRun", "$baseDir/Hare/Hare_Run.png")
        load("animalHareHurt", "$baseDir/Hare/Hare_Hurt.png")
        load("animalHareDeath", "$baseDir/Hare/Hare_Death.png")

        // Fox
        load("animalFoxIdle", "$baseDir/Fox/Fox_Idle.png")
        load("animalFoxWalk", "$baseDir/Fox/Fox_Walk.png")
        load("animalFoxRun", "$baseDir/Fox/Fox_Run.png")
        load("animalFoxHurt", "$baseDir/Fox/Fox_Hurt.png")
        load("animalFoxDeath", "$baseDir/Fox/Fox_Death.png")

        // Black Grouse
        load("animalGrouseIdle", "$baseDir/Black_grouse/Black_grouse_Idle.png")
        load("animalGrouseWalk", "$baseDir/Black_grouse/Black_grouse_Walk.png")
        load("animalGrouseRun", "$baseDir/Black_grouse/Black_grouse_Flight.png")
        load("animalGrouseHurt", "$baseDir/Black_grouse/Black_grouse_Hurt.png")
        load("animalGrouseDeath", "$baseDir/Black_grouse/Black_grouse_Death.png")
    }

    private fun loadUISprites() {
        val uiDir = "Tiny Swords (Free Pack)/UI Elements/UI Elements"
        load("uiBarBase", "$uiDir/Bars/SmallBar_Base.png")
        load("uiBarFill", "$uiDir/Bars/SmallBar_Fill.png")
    }

    private fun loadEffectSprites() {
        val fxDir = "Tiny Swords (Free Pack)/Particle FX"
        load("fxDust", "$fxDir/Dust_01.png")
        load("fxExplosion", "$fxDir/Explosion_01.png")
        load("fxFire", "$fxDir/Fire_03.png")
    }

    private fun loadDecorSprites() {
        val decorDir = "Tiny Swords (Free Pack)/Terrain/Decorations"

        // Bushes
        for (i in 1..4) {
            load("bush$i", "$decorDir/Bushes/Bush0$i.png")
        }

        // Rocks
        for (i in 1..4) {
            load("rock$i", "$decorDir/Rocks/Rock0$i.png")
        }

        // Clouds
        for (i in 1..8) {
            load("cloud$i", "$decorDir/Clouds/Cloud0$i.png")
        }

        // Water rocks
        for (i in 1..4) {
            load("waterRock$i", "$decorDir/Rocks in the Water/WaterRock0$i.png")
        }

        // Rubber duck
        load("rubberDuck", "$decorDir/Rubber Duck/RubberDuck.png")
    }

    fun destroy() {
        for ((_, bitmap) in cache) {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
        cache.clear()
    }
}
