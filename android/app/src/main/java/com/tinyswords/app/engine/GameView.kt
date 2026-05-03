package com.tinyswords.app.engine

import android.content.Context
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.tinyswords.app.game.*
import com.tinyswords.app.game.entities.*
import com.tinyswords.app.util.dist2
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.*

class GameView(
    context: Context,
    private val simulation: GameSimulation,
    private val renderer: GameRenderer,
    private val onSelectionChanged: () -> Unit,
    private val onGameOver: (winner: Int) -> Unit
) : SurfaceView(context), SurfaceHolder.Callback, Runnable {

    private var gameThread: Thread? = null
    @Volatile private var running = false
    private var lastFrameTime: Long = 0L
    private var gameOverFired = false
    private val commandQueue = ConcurrentLinkedQueue<() -> Unit>()
    private var fixedStepAccumulator = 0f
    private val minimapBgPaint = android.graphics.Paint().apply { color = android.graphics.Color.argb(210, 18, 35, 42) }
    private val minimapBorderPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(210, 170, 137, 96)
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 2.3f
    }

    private var primaryPointerId = -1
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var touchStartTime = 0L
    private var isDragging = false
    private var isPanning = false

    private var pinchActive = false
    private var pinchStartDist = 0f
    private var pinchStartZoom = 1f
    private var pinchFocusWorldX = 0f
    private var pinchFocusWorldY = 0f
    private var pinchFocusScreenX = 0f
    private var pinchFocusScreenY = 0f

    var minimapExpanded = false
    private var minimapTouching = false
    private var attackMoveArmed = false
    private var dragCandidateBuilding: GameBuilding? = null
    private var draggingBuilding: GameBuilding? = null
    private var buildingDragOffsetX = 0f
    private var buildingDragOffsetY = 0f
    private var originalBuildingX = 0f
    private var originalBuildingY = 0f
    private val buildingDragQueryBuffer = ArrayList<GameBuilding>(32)
    private val resourceDragQueryBuffer = ArrayList<GameResource>(64)

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
        isClickable = true
        keepScreenOn = true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (running) return
        running = true
        lastFrameTime = System.nanoTime()
        gameThread = Thread(this, "TinySwordsGameLoop").also { it.start() }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        synchronized(state) { clampCamera(width.toFloat(), height.toFloat()) }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        running = false
        try { gameThread?.join(600) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
        gameThread = null
    }

    override fun run() {
        val targetFrameNs = 16_666_667L
        val fixedStep = 1f / 60f
        val maxStepsPerFrame = 4

        while (running) {
            val frameStart = System.nanoTime()
            val rawDt = ((frameStart - lastFrameTime) / 1_000_000_000f).coerceIn(0.001f, 0.10f)
            lastFrameTime = frameStart
            fixedStepAccumulator = (fixedStepAccumulator + rawDt).coerceAtMost(fixedStep * maxStepsPerFrame)

            var canvas: Canvas? = null
            try {
                canvas = lockCanvasFast()
                if (canvas == null) {
                    Thread.sleep(4)
                    continue
                }
                synchronized(state) {
                    drainCommands()

                    var steps = 0
                    while (fixedStepAccumulator >= fixedStep && steps < maxStepsPerFrame) {
                        simulation.update(fixedStep)
                        fixedStepAccumulator -= fixedStep
                        steps++
                    }
                    if (steps == maxStepsPerFrame && fixedStepAccumulator >= fixedStep) {
                        fixedStepAccumulator = 0f
                    }

                    if (state.gameOver && !gameOverFired) {
                        gameOverFired = true
                        post { onGameOver(state.winnerFaction) }
                    }
                    val viewW = canvas.width.toFloat()
                    val viewH = canvas.height.toFloat()
                    clampCamera(viewW, viewH)
                    renderer.render(canvas, state, viewW, viewH)
                    drawMinimapOverlay(canvas, viewW, viewH)
                }
            } catch (t: Throwable) {
                Log.e("TinySwords", "Game loop error", t)
                try { Thread.sleep(80) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
            } finally {
                if (canvas != null) {
                    try { holder.unlockCanvasAndPost(canvas) } catch (_: Throwable) { }
                }
            }

            val elapsedNs = System.nanoTime() - frameStart
            val remainingNs = targetFrameNs - elapsedNs
            if (remainingNs > 1_200_000L) {
                val sleepMs = remainingNs / 1_000_000L
                val sleepNs = (remainingNs % 1_000_000L).toInt()
                try { Thread.sleep(sleepMs, sleepNs) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
            } else if (remainingNs > 0L) {
                Thread.yield()
            }
        }
    }

    private fun lockCanvasFast(): Canvas? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                holder.lockHardwareCanvas()
            } catch (_: Throwable) {
                holder.lockCanvas()
            }
        } else {
            holder.lockCanvas()
        }
    }

    private fun drainCommands() {
        while (true) {
            val action = commandQueue.poll() ?: break
            action.invoke()
        }
    }

    fun runCommand(action: () -> Unit) {
        commandQueue.add(action)
    }

    private data class MiniRect(val x: Float, val y: Float, val w: Float, val h: Float)

    private fun minimapRect(viewW: Float, viewH: Float): MiniRect {
        val w = if (minimapExpanded) (viewW * 0.30f).coerceIn(220f, 340f) else (viewW * 0.18f).coerceIn(150f, 220f)
        val h = (w * 0.68f).coerceIn(96f, if (minimapExpanded) 230f else 150f)
        return MiniRect(viewW - w - 12f, 12f, w, h)
    }

    private fun drawMinimapOverlay(canvas: Canvas, viewW: Float, viewH: Float) {
        val r = minimapRect(viewW, viewH)
        canvas.save()
        canvas.translate(r.x, r.y)
        canvas.clipRect(0f, 0f, r.w, r.h)

        canvas.drawRoundRect(0f, 0f, r.w, r.h, 8f, 8f, minimapBgPaint)
        renderer.renderMinimap(canvas, state, r.w, r.h, viewW, viewH)

        canvas.drawRoundRect(1f, 1f, r.w - 1f, r.h - 1f, 8f, 8f, minimapBorderPaint)
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val viewW = width.toFloat()
        val viewH = height.toFloat()
        synchronized(state) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> handleTouchDown(event, viewW, viewH)
                MotionEvent.ACTION_POINTER_DOWN -> handlePointerDown(event, viewW, viewH)
                MotionEvent.ACTION_MOVE -> handleTouchMove(event, viewW, viewH)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> handleTouchUp(event, viewW, viewH)
                MotionEvent.ACTION_POINTER_UP -> handlePointerUp(event)
            }
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
        pinchActive = false

        val mini = minimapRect(viewW, viewH)
        if (event.x >= mini.x && event.x <= mini.x + mini.w && event.y >= mini.y && event.y <= mini.y + mini.h) {
            minimapTouching = true
            handleMinimapTouch(event.x - mini.x, event.y - mini.y, mini.w, mini.h, viewW, viewH)
            return
        }
        val (wx, wy) = screenToWorld(event.x, event.y, viewW, viewH)
        state.pointerWorldX = wx
        state.pointerWorldY = wy
        dragCandidateBuilding = (findEntityAt(wx, wy) as? GameBuilding)?.takeIf { it.faction == 0 && !it.dead }
        draggingBuilding = null
        dragCandidateBuilding?.let { b ->
            buildingDragOffsetX = b.x - wx
            buildingDragOffsetY = b.y - wy
            originalBuildingX = b.x
            originalBuildingY = b.y
        }
    }

    private fun handlePointerDown(event: MotionEvent, viewW: Float, viewH: Float) {
        if (event.pointerCount == 2) {
            pinchActive = true
            pinchStartDist = getPinchDistance(event)
            pinchStartZoom = state.camera.zoom
            pinchFocusScreenX = (event.getX(0) + event.getX(1)) * 0.5f
            pinchFocusScreenY = (event.getY(0) + event.getY(1)) * 0.5f
            val focus = screenToWorld(pinchFocusScreenX, pinchFocusScreenY, viewW, viewH)
            pinchFocusWorldX = focus.first
            pinchFocusWorldY = focus.second
        }
    }

    private fun handleTouchMove(event: MotionEvent, viewW: Float, viewH: Float) {
        if (minimapTouching) {
            val mini = minimapRect(viewW, viewH)
            handleMinimapTouch(event.x - mini.x, event.y - mini.y, mini.w, mini.h, viewW, viewH)
            return
        }
        if (pinchActive && event.pointerCount >= 2) {
            val newDist = getPinchDistance(event)
            if (pinchStartDist > 0f) {
                val scale = newDist / pinchStartDist
                val newZoom = (pinchStartZoom * scale).coerceIn(CAMERA_MIN_ZOOM, CAMERA_MAX_ZOOM)
                val midX = (event.getX(0) + event.getX(1)) * 0.5f
                val midY = (event.getY(0) + event.getY(1)) * 0.5f
                val cam = state.camera
                cam.zoom = newZoom
                cam.targetZoom = newZoom
                cam.x = pinchFocusWorldX - (midX - viewW / 2f) / newZoom
                cam.y = pinchFocusWorldY - (midY - viewH / 2f) / newZoom
                clampCamera(viewW, viewH)
            }
            return
        }

        val (wx, wy) = screenToWorld(event.x, event.y, viewW, viewH)
        state.pointerWorldX = wx
        state.pointerWorldY = wy

        val dx = event.x - touchStartX
        val dy = event.y - touchStartY
        val dragDist = sqrt(dx * dx + dy * dy)
        val heldMs = System.currentTimeMillis() - touchStartTime

        val candidate = dragCandidateBuilding
        if (!pinchActive && candidate != null && (draggingBuilding != null || (heldMs >= 430L && dragDist > 6f))) {
            if (draggingBuilding == null) {
                draggingBuilding = candidate
                clearSelectionLocked()
                candidate.selected = true
                state.selected.add(candidate)
                haptic(24)
                post { onSelectionChanged() }
            }
            val nx = wx + buildingDragOffsetX
            val ny = wy + buildingDragOffsetY
            if (canMoveBuilding(candidate, nx, ny)) {
                candidate.x = nx
                candidate.y = ny
            }
            return
        }

        if (dragDist > 14f && !isDragging) isDragging = true

        if (isDragging) {
            isPanning = true
            val cam = state.camera
            cam.x -= dx / cam.zoom
            cam.y -= dy / cam.zoom
            clampCamera(viewW, viewH)
            touchStartX = event.x
            touchStartY = event.y
        }
    }

    private fun handleTouchUp(event: MotionEvent, viewW: Float, viewH: Float) {
        if (minimapTouching) {
            minimapTouching = false
            return
        }
        val (wx, wy) = screenToWorld(event.x, event.y, viewW, viewH)
        state.pointerWorldX = wx
        state.pointerWorldY = wy
        val touchDuration = System.currentTimeMillis() - touchStartTime
        if (draggingBuilding != null) {
            val b = draggingBuilding!!
            if (!canMoveBuilding(b, b.x, b.y)) {
                b.x = originalBuildingX
                b.y = originalBuildingY
            }
            simulation.worldGenerator.rebuildPathGrid()
            state.rebuildSpatialIndices()
            state.effects.add(GameEffect("moveMark", b.x, b.y, maxTime = 0.42f, scale = 1.25f))
            haptic(18)
            post { onSelectionChanged() }
        } else if (!isDragging && !pinchActive) {
            if (touchDuration >= 520) handleContextOrder(wx, wy) else handleTap(wx, wy)
        }
        primaryPointerId = -1
        pinchActive = false
        isPanning = false
        dragCandidateBuilding = null
        draggingBuilding = null
    }

    private fun handlePointerUp(event: MotionEvent) {
        if (event.pointerCount <= 2) pinchActive = false
    }

    private fun handleTap(wx: Float, wy: Float) {
        if (state.placingBuilding != null) {
            val type = state.placingBuilding!!
            val selectedWorkers = state.selected.filterIsInstance<GameUnit>().filter { it.faction == 0 && it.type == "worker" && !it.dead && !it.garrisoned }
            val building = simulation.economy.placeBuilding(type, 0, wx, wy, asFoundation = true)
            if (building != null) {
                state.placingBuilding = null
                if (selectedWorkers.isNotEmpty()) {
                    simulation.orderRepair(selectedWorkers, building)
                }
                haptic(24)
            }
            post { onSelectionChanged() }
            return
        }

        val entity = findEntityAt(wx, wy)
        if (entity != null && state.selected.size == 1 && state.selected[0] === entity) {
            clearSelectionLocked()
            attackMoveArmed = false
            post { onSelectionChanged() }
            return
        }
        val selectedUnits = state.selected.filterIsInstance<GameUnit>().filter { it.faction == 0 && !it.dead && !it.garrisoned }
        val selectedBuilding = state.selected.firstOrNull() as? GameBuilding

        if (selectedUnits.isNotEmpty()) {
            when {
                entity is GameUnit && entity.faction != 0 -> simulation.orderAttack(selectedUnits, entity)
                entity is GameBuilding && entity.faction != 0 -> simulation.orderAttack(selectedUnits, entity)
                entity is GameResource && !entity.depleted -> {
                    val workers = selectedUnits.filter { it.type == "worker" }
                    if (workers.isNotEmpty()) simulation.orderHarvest(workers, entity)
                }
                entity is GameBuilding && entity.faction == 0 && selectedUnits.any { it.type == "worker" } && (entity.hp < entity.maxHp || entity.buildProgress < 1f) -> {
                    simulation.orderRepair(selectedUnits.filter { it.type == "worker" }, entity)
                }
                entity is GameUnit && entity.faction == 0 -> selectSingle(entity)
                entity is GameBuilding && entity.faction == 0 -> selectSingle(entity)
                else -> {
                    simulation.orderMove(selectedUnits, wx, wy, attackMoveArmed)
                    state.effects.add(GameEffect("moveMark", wx, wy, maxTime = 0.48f, scale = if (attackMoveArmed) 1.35f else 1.0f))
                }
            }
            attackMoveArmed = false
            post { onSelectionChanged() }
            return
        }

        if (selectedBuilding != null && selectedBuilding.faction == 0 && !selectedBuilding.dead) {
            if (entity == null || (entity is GameResource) || (entity is GameUnit && entity.faction != 0) || (entity is GameBuilding && entity.faction != 0)) {
                selectedBuilding.rallyX = wx
                selectedBuilding.rallyY = wy
                selectedBuilding.hasRally = true
                state.effects.add(GameEffect("moveMark", wx, wy, maxTime = 0.48f, scale = 1.1f))
                haptic(18)
                post { onSelectionChanged() }
                return
            }
        }

        if (entity != null) selectSingle(entity) else clearSelectionLocked()
        post { onSelectionChanged() }
    }

    private fun handleContextOrder(wx: Float, wy: Float) {
        val selectedUnits = state.selected.filterIsInstance<GameUnit>().filter { it.faction == 0 && !it.dead && !it.garrisoned }
        val target = findEntityAt(wx, wy)
        if (selectedUnits.isNotEmpty()) {
            when {
                target is GameUnit && target.faction != 0 -> simulation.orderAttack(selectedUnits, target)
                target is GameBuilding && target.faction != 0 -> simulation.orderAttack(selectedUnits, target)
                target is GameResource && !target.depleted -> simulation.orderHarvest(selectedUnits.filter { it.type == "worker" }, target)
                target is GameBuilding && target.faction == 0 && (target.hp < target.maxHp || target.buildProgress < 1f) -> simulation.orderRepair(selectedUnits.filter { it.type == "worker" }, target)
                else -> {
                    simulation.orderMove(selectedUnits, wx, wy, attackMove = true)
                    state.effects.add(GameEffect("moveMark", wx, wy, maxTime = 0.48f, scale = 1.35f))
                }
            }
            haptic(36)
            post { onSelectionChanged() }
            return
        }
        val selectedBuilding = state.selected.firstOrNull() as? GameBuilding
        if (selectedBuilding != null && selectedBuilding.faction == 0) {
            selectedBuilding.rallyX = wx
            selectedBuilding.rallyY = wy
            selectedBuilding.hasRally = true
            state.effects.add(GameEffect("moveMark", wx, wy, maxTime = 0.48f, scale = 1.1f))
            haptic(24)
            post { onSelectionChanged() }
        }
    }

    private fun findEntityAt(wx: Float, wy: Float): GameEntity? {
        var bestUnit: GameUnit? = null
        var bestUnitDist = 34f * 34f
        for (u in state.units) {
            if (u.dead || u.garrisoned) continue
            val d = dist2(wx, wy, u.x, u.y)
            if (d < bestUnitDist) {
                bestUnitDist = d
                bestUnit = u
            }
        }
        if (bestUnit != null) return bestUnit

        for (b in state.buildings.asReversed()) {
            if (b.dead) continue
            val def = BUILDINGS[b.type] ?: continue
            val left = b.x - def.w / 2f - 10f
            val right = b.x + def.w / 2f + 10f
            val top = b.y - def.h + def.placeYOffset - 8f
            val bottom = b.y + def.placeYOffset + 12f
            if (wx in left..right && wy in top..bottom) return b
        }

        var bestRes: GameResource? = null
        var bestResDist = 48f * 48f
        for (r in state.resources) {
            if (r.dead || r.depleted) continue
            val ix = resourceInteractionX(r)
            val iy = resourceInteractionY(r)
            val radius = (resourceFootprint(r) + when (r.type) { ResourceType.TREE -> 30f; ResourceType.GOLD -> 18f; ResourceType.FOOD -> 16f }).coerceAtLeast(28f)
            val d = dist2(wx, wy, ix, iy)
            if (d < min(bestResDist, radius * radius)) {
                bestResDist = d
                bestRes = r
            }
        }
        return bestRes
    }

    private fun selectSingle(entity: GameEntity) {
        clearSelectionLocked()
        when (entity) {
            is GameUnit -> {
                entity.selected = true
                state.selected.add(entity)
            }
            is GameBuilding -> {
                entity.selected = true
                state.selected.add(entity)
            }
            is GameResource -> state.selected.add(entity)
        }
    }

    private fun clearSelectionLocked() {
        for (e in state.selected) {
            when (e) {
                is GameUnit -> e.selected = false
                is GameBuilding -> e.selected = false
            }
        }
        state.selected.clear()
    }

    fun clearSelection() = runCommand {
        clearSelectionLocked()
        post { onSelectionChanged() }
    }

    fun selectAllUnits() = runCommand {
        clearSelectionLocked()
        for (u in state.units) {
            if (u.dead || u.garrisoned || u.faction != 0) continue
            u.selected = true
            state.selected.add(u)
        }
        post { onSelectionChanged() }
    }

    fun selectAllWorkers() = runCommand {
        clearSelectionLocked()
        for (u in state.units) {
            if (u.dead || u.garrisoned || u.faction != 0 || u.type != "worker") continue
            u.selected = true
            state.selected.add(u)
        }
        post { onSelectionChanged() }
    }

    fun selectAllMilitary() = runCommand {
        clearSelectionLocked()
        for (u in state.units) {
            if (u.dead || u.garrisoned || u.faction != 0 || u.type == "worker") continue
            u.selected = true
            state.selected.add(u)
        }
        post { onSelectionChanged() }
    }

    fun focusPlayerBase() = runCommand {
        val castle = state.buildings.firstOrNull { it.faction == 0 && it.type == "castle" && !it.dead }
        if (castle != null) {
            state.camera.x = castle.x
            state.camera.y = castle.y
            state.camera.targetZoom = 1.0f
        }
    }

    fun armAttackMove() = runCommand {
        attackMoveArmed = true
        haptic(18)
    }

    fun cancelPlacement() = runCommand {
        state.placingBuilding = null
        attackMoveArmed = false
        post { onSelectionChanged() }
    }

    private fun resourceInteractionX(res: GameResource): Float = res.x

    private fun resourceInteractionY(res: GameResource): Float = when {
        res.isAnimal -> res.y
        res.type == ResourceType.TREE && res.depleted -> res.y - 14f
        res.type == ResourceType.TREE -> res.y - 42f
        res.type == ResourceType.GOLD -> res.y - 16f
        else -> res.y - 2f
    }

    private fun resourceFootprint(res: GameResource): Float = when {
        res.isAnimal -> ((HUNT_ANIMALS[res.animalKind]?.radius ?: 12f) + 2f).coerceAtLeast(12f)
        res.type == ResourceType.TREE && res.depleted -> 18f
        res.type == ResourceType.TREE -> 34f
        res.type == ResourceType.GOLD -> 24f
        else -> 16f
    }

    private fun canMoveBuilding(building: GameBuilding, x: Float, y: Float): Boolean {
        val def = BUILDINGS[building.type] ?: return false
        if (!state.isSafeLand(x, y, def.placeW / 2f)) return false

        val pad = def.placeW.coerceAtLeast(def.placeH) + 96f
        state.buildingIndex.queryRect(x - pad, y - pad, x + pad, y + pad, buildingDragQueryBuffer)
        for (other in buildingDragQueryBuffer) {
            if (other.dead || other.id == building.id) continue
            val od = BUILDINGS[other.type] ?: continue
            if (rectsOverlap(x, y, def.placeW, def.placeH, other.x, other.y, od.placeW, od.placeH)) return false
        }

        state.resourceIndex.queryRect(x - pad, y - pad, x + pad, y + pad, resourceDragQueryBuffer)
        for (r in resourceDragQueryBuffer) {
            if (r.dead || r.depleted) continue
            val blockRadius = if (r.isAnimal) (HUNT_ANIMALS[r.animalKind]?.radius ?: 12f) else when (r.type) {
                ResourceType.TREE -> 30f
                ResourceType.GOLD -> 24f
                ResourceType.FOOD -> 16f
            }
            if (dist2(x, y, r.x, r.y) < (def.placeW / 2f + blockRadius) * (def.placeW / 2f + blockRadius)) return false
        }
        return true
    }

    private fun rectsOverlap(ax: Float, ay: Float, aw: Float, ah: Float, bx: Float, by: Float, bw: Float, bh: Float): Boolean {
        return ax - aw / 2f < bx + bw / 2f && ax + aw / 2f > bx - bw / 2f && ay - ah / 2f < by + bh / 2f && ay + ah / 2f > by - bh / 2f
    }

    private fun handleMinimapTouch(mx: Float, my: Float, mapW: Float, mapH: Float, viewW: Float, viewH: Float) {
        val worldX = (mx / mapW) * state.worldW
        val worldY = (my / mapH) * state.worldH
        state.camera.x = worldX.coerceIn(0f, state.worldW)
        state.camera.y = worldY.coerceIn(0f, state.worldH)
        clampCamera(viewW, viewH)
    }

    private fun screenToWorld(sx: Float, sy: Float, viewW: Float, viewH: Float): Pair<Float, Float> {
        val cam = state.camera
        val wx = cam.x + (sx - viewW / 2f) / cam.zoom
        val wy = cam.y + (sy - viewH / 2f) / cam.zoom
        return Pair(wx, wy)
    }

    private fun clampCamera(viewW: Float, viewH: Float) {
        val cam = state.camera
        val halfW = (viewW / 2f) / cam.zoom
        val halfH = (viewH / 2f) / cam.zoom
        cam.x = cam.x.coerceIn(halfW.coerceAtMost(state.worldW / 2f), (state.worldW - halfW).coerceAtLeast(state.worldW / 2f))
        cam.y = cam.y.coerceIn(halfH.coerceAtMost(state.worldH / 2f), (state.worldH - halfH).coerceAtLeast(state.worldH / 2f))
    }

    private fun getPinchDistance(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        val dx = event.getX(0) - event.getX(1)
        val dy = event.getY(0) - event.getY(1)
        return sqrt(dx * dx + dy * dy)
    }

    private fun haptic(ms: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(ms)
        }
    }

    fun pause() { runCommand { state.paused = true } }
    fun resume() { runCommand { state.paused = false } }

    fun destroy() {
        running = false
        try { gameThread?.join(600) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
    }
}
