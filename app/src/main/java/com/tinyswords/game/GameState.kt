package com.tinyswords.game

data class Faction(
    val id: Int,
    val name: String,
    val color: androidx.compose.ui.graphics.Color,
    val ai: Boolean = false,
    val alive: Boolean = true,
    var res: MutableMap<String, Double> = mutableMapOf("wood" to 200.0, "food" to 200.0, "gold" to 0.0)
)

data class Unit(
    val id: Int,
    val type: String,
    val faction: Int,
    var x: Double,
    var y: Double,
    var r: Double = 12.0,
    var hp: Double,
    val maxHp: Double,
    var order: String = "idle",
    var target: Any? = null,
    var anim: Double = 0.0,
    var face: Int = 1,
    var dead: Boolean = false,
    var garrisoned: Boolean = false
)

data class Building(
    val id: Int,
    val type: String,
    val faction: Int,
    var x: Double,
    var y: Double,
    val w: Double,
    val h: Double,
    var hp: Double,
    val maxHp: Double,
    var build: Double = 1.0,
    var dead: Boolean = false
)

data class Resource(
    val id: Int,
    val type: String,
    var x: Double,
    var y: Double,
    var amt: Double,
    val maxAmt: Double,
    var dead: Boolean = false
)

class GameState {
    val factions = mutableListOf(
        Faction(0, "Player", androidx.compose.ui.graphics.Color(0xFF61B7D9)),
        Faction(1, "Rival 1", androidx.compose.ui.graphics.Color(0xFFDB6060), ai = true)
    )
    val units = mutableListOf<Unit>()
    val buildings = mutableListOf<Building>()
    val resources = mutableListOf<Resource>()

    var time = 0.0
    var cameraX = 0.0
    var cameraY = 0.0
    var cameraZoom = 1.0f

    // Selected items (can be Units or Buildings)
    val selected = mutableListOf<Any>()
}
