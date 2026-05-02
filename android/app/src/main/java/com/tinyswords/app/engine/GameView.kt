package com.tinyswords.app.engine

import android.content.Context
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.tinyswords.app.game.*
import com.tinyswords.app.game.entities.*
import com.tinyswords.app.util.dist
import com.tinyswords.app.util.dist2
import kotlin.math.*

class GameView(
    context: Context,
    private val simulation: GameSimulation,
    private val renderer: GameRenderer,
    private val onSelectionChanged: () -> Unit,
    private val onGameOver: (winner: Int) -> Unit
) : SurfaceView(context), SurfaceHolder.Callback, Runnable {

    private var gameThread: Thread? = null
    private var running = false
    private var lastFrameTime: Long = 0
    private var gameOverFired = false

    // Touch state
    private var primaryPointerId = -1
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var touchStartWorldX = 0f
    private var touchStartWorldY = 0f
    private var touchStartTime = 0L
    private var isDragging = false
    private var isPanning = false
    private var longPressTriggered = false

    // Pinch zoom
    private var pinchActive = false
    private var pinchStartDist = 0f
    private var pinchStartZoom = 1f

    // Selection drag box
    private var dragSelectActive = false
    private var dragSelectStartX = 0f
    private var dragSelectStartY = 0f

    // Minimap
    var minimapExpanded = false
    private var minimapTouching = false

    // Vibrator
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    val state get() = simulation.state

    init {
        holder.addCallback(this)
        holder.setFormat(PixelFormat.OPAQUE)
        isFocusable = true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        running = true
        lastFrameTime = System.nanoTime()
        gameThread = Thread(this).also { it.start() }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        running = false
        gameThread?.join()
    }

    override fun run() {
        while (running) {
            val now = System.nanoTime()
            val dt = ((now - lastFrameTime) / 1_000_000_000f).coerceIn(0.001f, MAX_DT)
            lastFrameTime = now

            // Update
            simulation.update(dt)

            // Check game over (fire callback only once)
            if (state.gameOver && !gameOverFired) {
                gameOverFired = true
                post { onGameOver(state.winnerFaction) }
            }

            // Render
            val canvas = holder.lockCanvas() ?: continue
            try {
                val viewW = canvas.width.toFloat()
                val viewH = canvas.height.toFloat()
                renderer.render(canvas, state, viewW, viewH)

                // Draw minimap overlay
                drawMinimapOverlay(canvas, viewW, viewH)
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }

            // Target ~60fps
            val frameTime = (System.nanoTime() - now) / 1_000_000
            if (frameTime < 16) {
                Thread.sleep(16 - frameTime)
            }
        }
    }

    private fun drawMinimapOverlay(canvas: Canvas, viewW: Float, viewH: Float) {
        val mapW = if (minimapExpanded) 298f else 220f
        val mapH = if (minimapExpanded) 200f else 150f
        val mapX = viewW - mapW - 14f
        val mapY = viewH - mapH - 14f

        canvas.save()
        canvas.translate(mapX, mapY)
        canvas.clipRect(0f, 0f, mapW, mapH)

        // Minimap background
        val bgPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.argb(200, 20, 40, 50)
        }
        canvas.drawRect(0f, 0f, mapW, mapH, bgPaint)

        renderer.renderMinimap(canvas, state, mapW, mapH)

        // Border
        val borderPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.argb(180, 100, 80, 60)
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawRect(0f, 0f, mapW, mapH, borderPaint)

        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val viewW = width.toFloat()
        val viewH = height.toFloat()

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> handleTouchDown(event, viewW, viewH)
            MotionEvent.ACTION_POINTER_DOWN -> handlePointerDown(event)
            MotionEvent.ACTION_MOVE -> handleTouchMove(event, viewW, viewH)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> handleTouchUp(event, viewW, viewH)
            MotionEvent.ACTION_POINTER_UP -> handlePointerUp(event)
        }
        return true
    }

    private fun handleTouchDown(event: MotionEvent, viewW: Float, viewH: Float) {
        primaryPointerId = event.getPointerId(0)
        touchStartX = event.x
        touchStartY = event.y
        touchStartTime = System.currentTimeMillis()
        isDragging = false
        isPanning = false
        longPressTriggered = false
        dragSelectActive = false
        pinchActive = false

        // Check minimap touch
        val mapW = if (minimapExpanded) 298f else 220f
        val mapH = if (minimapExpanded) 200f else 150f
        val mapX = viewW - mapW - 14f
        val mapY = viewH - mapH - 14f

        if (event.x >= mapX && event.x <= mapX + mapW && event.y >= mapY && event.y <= mapY + mapH) {
            minimapTouching = true
            handleMinimapTouch(event.x - mapX, event.y - mapY, mapW, mapH)
            return
        }

        // Convert to world coords
        val worldPos = screenToWorld(event.x, event.y, viewW, viewH)
        touchStartWorldX = worldPos.first
        touchStartWorldY = worldPos.second
    }

    private fun handlePointerDown(event: MotionEvent) {
        if (event.pointerCount == 2) {
            pinchActive = true
            pinchStartDist = getPinchDistance(event)
            pinchStartZoom = state.camera.targetZoom
        }
    }

    private fun handleTouchMove(event: MotionEvent, viewW: Float, viewH: Float) {
        if (minimapTouching) {
            val mapW = if (minimapExpanded) 298f else 220f
            val mapH = if (minimapExpanded) 200f else 150f
            val mapX = viewW - mapW - 14f
            val mapY = viewH - mapH - 14f
            handleMinimapTouch(event.x - mapX, event.y - mapY, mapW, mapH)
            return
        }

        // Pinch zoom
        if (pinchActive && event.pointerCount >= 2) {
            val newDist = getPinchDistance(event)
            if (pinchStartDist > 0f) {
                val scale = newDist / pinchStartDist
                state.camera.targetZoom = (pinchStartZoom * scale).coerceIn(CAMERA_MIN_ZOOM, CAMERA_MAX_ZOOM)
            }
            return
        }

        val dx = event.x - touchStartX
        val dy = event.y - touchStartY
        val dragDist = sqrt(dx * dx + dy * dy)

        if (dragDist > 15f && !isDragging) {
            isDragging = true
        }

        if (isDragging && !longPressTriggered) {
            // Pan camera
            isPanning = true
            val cam = state.camera
            cam.x -= dx / cam.zoom
            cam.y -= dy / cam.zoom

            // Clamp camera
            cam.x = cam.x.coerceIn(0f, state.worldW)
            cam.y = cam.y.coerceIn(0f, state.worldH)

            touchStartX = event.x
            touchStartY = event.y
        }
    }

    private fun handleTouchUp(event: MotionEvent, viewW: Float, viewH: Float) {
        if (minimapTouching) {
            minimapTouching = false
            return
        }

        val touchDuration = System.currentTimeMillis() - touchStartTime
        val worldPos = screenToWorld(event.x, event.y, viewW, viewH)
        val wx = worldPos.first
        val wy = worldPos.second

        if (!isDragging && !pinchActive) {
            // Tap
            if (touchDuration < 250) {
                handleTap(wx, wy)
            } else if (touchDuration >= 500 && !longPressTriggered) {
                // Long press = context order (right-click equivalent)
                handleContextOrder(wx, wy)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(50)
                }
            }
        }

        primaryPointerId = -1
        pinchActive = false
        isPanning = false
    }

    private fun handlePointerUp(event: MotionEvent) {
        if (event.pointerCount <= 2) {
            pinchActive = false
        }
    }

    private fun handleTap(wx: Float, wy: Float) {
        // Check if we're in building placement mode
        if (state.placingBuilding != null) {
            val type = state.placingBuilding!!
            val building = simulation.economy.placeBuilding(type, 0, wx, wy, asFoundation = true)
            if (building != null) {
                state.placingBuilding = null
            }
            return
        }

        // Try to select entity at tap position
        val entity = findEntityAt(wx, wy)

        if (entity != null) {
            // Clear previous selection
            clearSelection()

            when (entity) {
                is GameUnit -> {
                    entity.selected = true
                    state.selected.add(entity)
                }
                is GameBuilding -> {
                    entity.selected = true
                    state.selected.add(entity)
                }
                is GameResource -> {
                    state.selected.add(entity)
                }
            }
        } else {
            clearSelection()
        }

        onSelectionChanged()
    }

    private fun handleContextOrder(wx: Float, wy: Float) {
        val selectedUnits = state.selected.filterIsInstance<GameUnit>().filter { it.faction == 0 }
        if (selectedUnits.isEmpty()) return

        // Check what's at the target
        val targetEntity = findEntityAt(wx, wy)

        when {
            // Attack enemy
            targetEntity is GameUnit && targetEntity.faction != 0 -> {
                simulation.orderAttack(selectedUnits, targetEntity)
            }
            targetEntity is GameBuilding && targetEntity.faction != 0 -> {
                simulation.orderAttack(selectedUnits, targetEntity)
            }
            // Harvest resource
            targetEntity is GameResource && !targetEntity.depleted -> {
                simulation.orderHarvest(selectedUnits, targetEntity)
            }
            // Repair building
            targetEntity is GameBuilding && targetEntity.faction == 0 && (targetEntity.hp < targetEntity.maxHp || targetEntity.buildProgress < 1f) -> {
                simulation.orderRepair(selectedUnits, targetEntity)
            }
            // Set rally for selected building
            state.selected.firstOrNull() is GameBuilding && (state.selected.first() as GameBuilding).faction == 0 -> {
                val building = state.selected.first() as GameBuilding
                building.rallyX = wx
                building.rallyY = wy
                building.hasRally = true
            }
            // Move
            else -> {
                simulation.orderMove(selectedUnits, wx, wy)
            }
        }

        onSelectionChanged()
    }

    private fun findEntityAt(wx: Float, wy: Float): GameEntity? {
        // Check units first (smaller, harder to tap)
        var bestUnit: GameUnit? = null
        var bestUnitDist = 30f * 30f

        for (u in state.units) {
            if (u.dead || u.garrisoned) continue
            val d = dist2(wx, wy, u.x, u.y)
            if (d < bestUnitDist) {
                bestUnitDist = d
                bestUnit = u
            }
        }
        if (bestUnit != null) return bestUnit

        // Check buildings
        for (b in state.buildings) {
            if (b.dead) continue
            val def = BUILDINGS[b.type] ?: continue
            val hw = def.placeW / 2 + 10f
            val hh = def.placeH / 2 + 10f
            if (wx >= b.x - hw && wx <= b.x + hw && wy >= b.y - hh && wy <= b.y + hh) {
                return b
            }
        }

        // Check resources
        var bestRes: GameResource? = null
        var bestResDist = 40f * 40f
        for (r in state.resources) {
            if (r.dead || r.depleted) continue
            val d = dist2(wx, wy, r.x, r.y)
            if (d < bestResDist) {
                bestResDist = d
                bestRes = r
            }
        }
        return bestRes
    }

    fun clearSelection() {
        for (e in state.selected) {
            when (e) {
                is GameUnit -> e.selected = false
                is GameBuilding -> e.selected = false
            }
        }
        state.selected.clear()
    }

    fun selectAllUnits() {
        clearSelection()
        for (u in state.units) {
            if (u.dead || u.garrisoned || u.faction != 0) continue
            u.selected = true
            state.selected.add(u)
        }
        onSelectionChanged()
    }

    fun selectAllMilitary() {
        clearSelection()
        for (u in state.units) {
            if (u.dead || u.garrisoned || u.faction != 0 || u.type == "worker") continue
            u.selected = true
            state.selected.add(u)
        }
        onSelectionChanged()
    }

    private fun handleMinimapTouch(mx: Float, my: Float, mapW: Float, mapH: Float) {
        val worldX = (mx / mapW) * state.worldW
        val worldY = (my / mapH) * state.worldH
        state.camera.x = worldX.coerceIn(0f, state.worldW)
        state.camera.y = worldY.coerceIn(0f, state.worldH)
    }

    private fun screenToWorld(sx: Float, sy: Float, viewW: Float, viewH: Float): Pair<Float, Float> {
        val cam = state.camera
        val wx = cam.x + (sx - viewW / 2f) / cam.zoom
        val wy = cam.y + (sy - viewH / 2f) / cam.zoom
        return Pair(wx, wy)
    }

    private fun getPinchDistance(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        val dx = event.getX(0) - event.getX(1)
        val dy = event.getY(0) - event.getY(1)
        return sqrt(dx * dx + dy * dy)
    }

    fun pause() {
        state.paused = true
    }

    fun resume() {
        state.paused = false
    }

    fun destroy() {
        running = false
        gameThread?.join()
    }
}
