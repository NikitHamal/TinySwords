package com.tinyswords.realmwar.assets

import com.tinyswords.realmwar.game.BUILDING_DEFS
import com.tinyswords.realmwar.game.BuildingType
import com.tinyswords.realmwar.game.FACTIONS
import com.tinyswords.realmwar.game.FactionKey
import com.tinyswords.realmwar.game.UnitType

/**
 * String table mapping logical sprite ids to bundled paths underneath
 * `assets/tinyswords/`. The Tiny Swords pixel-art pack uses spaces in
 * directory names, which Android `AssetManager` handles fine.
 */
object AssetPaths {
    private const val BASE = "tinyswords/pack/"
    private const val SOUNDS = "tinyswords/sounds/"

    private fun colorFolder(key: FactionKey) = when (key) {
        FactionKey.BLUE -> "Blue"
        FactionKey.RED -> "Red"
        FactionKey.YELLOW -> "Yellow"
        FactionKey.PURPLE -> "Purple"
        FactionKey.BLACK -> "Black"
    }

    val terrain: Map<String, String> = mapOf(
        "tileGrass" to BASE + "Terrain/Tileset/Tilemap_color1.png",
        "tileAlt" to BASE + "Terrain/Tileset/Tilemap_color2.png",
        "tileMoss" to BASE + "Terrain/Tileset/Tilemap_color3.png",
        "tileDeep" to BASE + "Terrain/Tileset/Tilemap_color4.png",
        "tileWarm" to BASE + "Terrain/Tileset/Tilemap_color5.png",
        "water" to BASE + "Terrain/Tileset/Water Background color.png",
        "waterFoam" to BASE + "Terrain/Tileset/Water Foam.png",
        "shadow" to BASE + "Terrain/Tileset/Shadow.png",
    )

    val resources: Map<String, String> = mapOf(
        "tree1" to BASE + "Terrain/Resources/Wood/Trees/Tree1.png",
        "tree2" to BASE + "Terrain/Resources/Wood/Trees/Tree2.png",
        "tree3" to BASE + "Terrain/Resources/Wood/Trees/Tree3.png",
        "tree4" to BASE + "Terrain/Resources/Wood/Trees/Tree4.png",
        "stump1" to BASE + "Terrain/Resources/Wood/Trees/Stump 1.png",
        "stump2" to BASE + "Terrain/Resources/Wood/Trees/Stump 2.png",
        "gold1" to BASE + "Terrain/Resources/Gold/Gold Stones/Gold Stone 1.png",
        "gold2" to BASE + "Terrain/Resources/Gold/Gold Stones/Gold Stone 2.png",
        "gold3" to BASE + "Terrain/Resources/Gold/Gold Stones/Gold Stone 3.png",
        "gold4" to BASE + "Terrain/Resources/Gold/Gold Stones/Gold Stone 4.png",
        "sheepIdle" to BASE + "Terrain/Resources/Meat/Sheep/Sheep_Idle.png",
        "sheepMove" to BASE + "Terrain/Resources/Meat/Sheep/Sheep_Move.png",
        "meat" to BASE + "Terrain/Resources/Meat/Meat Resource/Meat Resource.png",
        "resWood" to BASE + "Terrain/Resources/Wood/Wood Resource/Wood Resource.png",
        "resGold" to BASE + "Terrain/Resources/Gold/Gold Resource/Gold_Resource.png",
        "resFood" to BASE + "Terrain/Resources/Meat/Meat Resource/Meat Resource.png",
    )

    val decor: Map<String, String> = mapOf(
        "bush1" to BASE + "Terrain/Decorations/Bushes/Bushe1.png",
        "bush2" to BASE + "Terrain/Decorations/Bushes/Bushe2.png",
        "bush3" to BASE + "Terrain/Decorations/Bushes/Bushe3.png",
        "bush4" to BASE + "Terrain/Decorations/Bushes/Bushe4.png",
        "rock1" to BASE + "Terrain/Decorations/Rocks/Rock1.png",
        "rock2" to BASE + "Terrain/Decorations/Rocks/Rock2.png",
        "rock3" to BASE + "Terrain/Decorations/Rocks/Rock3.png",
        "rock4" to BASE + "Terrain/Decorations/Rocks/Rock4.png",
    )

    val ui: Map<String, String> = mapOf(
        "iconMove" to BASE + "UI Elements/UI Elements/Icons/Icon_01.png",
        "iconAttack" to BASE + "UI Elements/UI Elements/Swords/Swords.png",
        "iconStop" to BASE + "UI Elements/UI Elements/Buttons/TinyRoundRedButton.png",
        "iconBuild" to BASE + "UI Elements/UI Elements/Icons/Icon_08.png",
        "iconRally" to BASE + "UI Elements/UI Elements/Ribbons/SmallRibbons.png",
        "iconRepair" to BASE + "Terrain/Resources/Tools/Tool_04.png",
        "uiBarBase" to BASE + "UI Elements/UI Elements/Bars/SmallBar_Base.png",
        "uiBarFill" to BASE + "UI Elements/UI Elements/Bars/SmallBar_Fill.png",
    )

    /** Logical id → asset path for every per-faction sprite the game uses. */
    val factionSprites: Map<String, String> = buildMap {
        for (f in FACTIONS) {
            val color = colorFolder(f.key)
            for ((type, def) in BUILDING_DEFS) {
                put("b_${f.key.name.lowercase()}_${type.name.lowercase()}",
                    BASE + "Buildings/$color Buildings/${def.file}")
            }
            put("u_${f.key.name.lowercase()}_${UnitType.WORKER.name.lowercase()}_idle",
                BASE + "Units/$color Units/Pawn/Pawn_Idle.png")
            put("u_${f.key.name.lowercase()}_${UnitType.WORKER.name.lowercase()}_run",
                BASE + "Units/$color Units/Pawn/Pawn_Run.png")
            put("u_${f.key.name.lowercase()}_${UnitType.WORKER.name.lowercase()}_chop",
                BASE + "Units/$color Units/Pawn/Pawn_Interact Axe.png")
            put("u_${f.key.name.lowercase()}_${UnitType.WORKER.name.lowercase()}_mine",
                BASE + "Units/$color Units/Pawn/Pawn_Interact Pickaxe.png")
            put("u_${f.key.name.lowercase()}_${UnitType.WORKER.name.lowercase()}_build",
                BASE + "Units/$color Units/Pawn/Pawn_Interact Hammer.png")
            put("u_${f.key.name.lowercase()}_${UnitType.WORKER.name.lowercase()}_carryWood",
                BASE + "Units/$color Units/Pawn/Pawn_Run Wood.png")
            put("u_${f.key.name.lowercase()}_${UnitType.WORKER.name.lowercase()}_carryGold",
                BASE + "Units/$color Units/Pawn/Pawn_Run Gold.png")
            put("u_${f.key.name.lowercase()}_${UnitType.WORKER.name.lowercase()}_carryFood",
                BASE + "Units/$color Units/Pawn/Pawn_Run Meat.png")
            put("u_${f.key.name.lowercase()}_${UnitType.WARRIOR.name.lowercase()}_idle",
                BASE + "Units/$color Units/Warrior/Warrior_Idle.png")
            put("u_${f.key.name.lowercase()}_${UnitType.WARRIOR.name.lowercase()}_run",
                BASE + "Units/$color Units/Warrior/Warrior_Run.png")
            put("u_${f.key.name.lowercase()}_${UnitType.WARRIOR.name.lowercase()}_attack",
                BASE + "Units/$color Units/Warrior/Warrior_Attack1.png")
            put("u_${f.key.name.lowercase()}_${UnitType.ARCHER.name.lowercase()}_idle",
                BASE + "Units/$color Units/Archer/Archer_Idle.png")
            put("u_${f.key.name.lowercase()}_${UnitType.ARCHER.name.lowercase()}_run",
                BASE + "Units/$color Units/Archer/Archer_Run.png")
            put("u_${f.key.name.lowercase()}_${UnitType.ARCHER.name.lowercase()}_attack",
                BASE + "Units/$color Units/Archer/Archer_Shoot.png")
            put("u_${f.key.name.lowercase()}_${UnitType.LANCER.name.lowercase()}_idle",
                BASE + "Units/$color Units/Lancer/Lancer_Idle.png")
            put("u_${f.key.name.lowercase()}_${UnitType.LANCER.name.lowercase()}_run",
                BASE + "Units/$color Units/Lancer/Lancer_Run.png")
            put("u_${f.key.name.lowercase()}_${UnitType.LANCER.name.lowercase()}_attack",
                BASE + "Units/$color Units/Lancer/Lancer_Right_Attack.png")
            put("u_${f.key.name.lowercase()}_${UnitType.MONK.name.lowercase()}_idle",
                BASE + "Units/$color Units/Monk/Idle.png")
            put("u_${f.key.name.lowercase()}_${UnitType.MONK.name.lowercase()}_run",
                BASE + "Units/$color Units/Monk/Run.png")
            put("u_${f.key.name.lowercase()}_${UnitType.MONK.name.lowercase()}_attack",
                BASE + "Units/$color Units/Monk/Heal.png")
            put("arrow_${f.key.name.lowercase()}",
                BASE + "Units/$color Units/Archer/Arrow.png")
        }
    }

    val sounds: Map<String, String> = mapOf(
        "arrow" to SOUNDS + "arrow.mp3",
        "arrowHit" to SOUNDS + "arrow_hit.mp3",
        "battle" to SOUNDS + "battle.mp3",
        "heal" to SOUNDS + "heal.mp3",
        "run" to SOUNDS + "run.mp3",
        "sword" to SOUNDS + "sword.mp3",
    )

    val all: Map<String, String> = buildMap {
        putAll(terrain); putAll(resources); putAll(decor); putAll(ui); putAll(factionSprites)
    }

    fun unitIdle(type: UnitType, key: FactionKey) =
        "u_${key.name.lowercase()}_${type.name.lowercase()}_idle"

    fun unitRun(type: UnitType, key: FactionKey) =
        "u_${key.name.lowercase()}_${type.name.lowercase()}_run"

    fun unitAttack(type: UnitType, key: FactionKey) =
        "u_${key.name.lowercase()}_${type.name.lowercase()}_attack"

    fun building(key: FactionKey, type: BuildingType) =
        "b_${key.name.lowercase()}_${type.name.lowercase()}"

    fun arrow(key: FactionKey) = "arrow_${key.name.lowercase()}"
}
