package com.farmerbb.taskbar.virtualdisplay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.min

/** Transparent gamepad overlay that maps its controls to the active virtual display. */
internal class VirtualMobaGamepadView(
    context: Context,
    private val displayRect: () -> RectF,
    private val displaySize: () -> Pair<Int, Int>,
    private val emitTouchFrame: (Int, IntArray, FloatArray, FloatArray) -> Unit,
    private val onMessage: (String) -> Unit
) : View(context) {
    private enum class Control(val id: Int, val label: String, val x: Float, val y: Float) {
        MOVE(0, "", .12f, .76f),
        SKILL_1(1, "1", .79f, .70f),
        SKILL_2(2, "2", .86f, .61f),
        SKILL_3(3, "3", .93f, .52f),
        ATTACK(4, "ATK", .94f, .79f),
        SPELL(5, "SP", .97f, .42f),
        REGEN(6, "HP", .86f, .43f),
        RECALL(7, "REC", .75f, .51f)
    }

    private data class Active(val control: Control, var x: Float, var y: Float)
    private val prefs = context.getSharedPreferences("virtual_display_gamepad", Context.MODE_PRIVATE)
    private val density = resources.displayMetrics.density
    private val positions = Control.entries.associateWith { c ->
        Pair(prefs.getFloat("${c.name}_x", c.x), prefs.getFloat("${c.name}_y", c.y))
    }.toMutableMap()
    private val active = LinkedHashMap<Int, Active>()
    private var editing = false
    var isEditing: Boolean
        get() = editing
        set(value) {
            editing = value
            active.clear()
            invalidateOverlay()
        }
    var sizeScale: Float = prefs.getFloat("size_scale", 1f).coerceIn(.65f, 1.5f)
        private set
    private var dragging: Control? = null
    private var stickDx = 0f
    private var stickDy = 0f
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(1.5f) }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
    }

    init {
        isClickable = true
        isFocusable = true
        contentDescription = "Overlay gamepad virtual"
    }

    fun setSizeScale(value: Float) {
        sizeScale = value.coerceIn(.65f, 1.5f)
        prefs.edit().putFloat("size_scale", sizeScale).apply()
        invalidateOverlay()
    }

    fun refreshOverlay() = invalidateOverlay()

    fun cancelActive() {
        if (active.isNotEmpty()) emitSnapshot(MotionEvent.ACTION_CANCEL)
        active.clear()
        stickDx = 0f
        stickDy = 0f
        invalidateOverlay()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val rect = displayRect()
        if (rect.width() <= 0 || rect.height() <= 0) return
        val buttonBase = min(rect.width() * .038f, rect.height() * .070f) * sizeScale
        Control.entries.forEach { control ->
            val point = positions.getValue(control)
            val cx = rect.left + rect.width() * point.first
            val cy = rect.top + rect.height() * point.second
            if (control == Control.MOVE) {
                val radius = min(rect.width() * .105f, rect.height() * .19f) * sizeScale
                drawGlassCircle(canvas, cx, cy, radius, Color.argb(145, 59, 145, 255), Color.argb(112, 14, 47, 127), editing)
                fill.shader = null
                fill.color = Color.argb(220, 28, 102, 211)
                canvas.drawCircle(cx + stickDx, cy + stickDy, radius * .37f, fill)
                stroke.color = Color.argb(230, 179, 220, 255)
                canvas.drawCircle(cx + stickDx, cy + stickDy, radius * .37f, stroke)
            } else {
                val radius = buttonBase * if (control == Control.ATTACK) 1.24f else .88f
                val innerColor: Int
                val edgeColor: Int
                when (control) {
                    Control.ATTACK -> { innerColor = Color.rgb(255, 185, 74); edgeColor = Color.rgb(151, 57, 32) }
                    Control.SPELL -> { innerColor = Color.rgb(67, 207, 255); edgeColor = Color.rgb(25, 83, 184) }
                    Control.REGEN -> { innerColor = Color.rgb(105, 237, 157); edgeColor = Color.rgb(23, 115, 82) }
                    Control.RECALL -> { innerColor = Color.rgb(199, 148, 255); edgeColor = Color.rgb(75, 49, 157) }
                    else -> { innerColor = Color.rgb(92, 177, 255); edgeColor = Color.rgb(29, 71, 177) }
                }
                drawGlassCircle(canvas, cx, cy, radius, innerColor, edgeColor, editing)
                text.textSize = min(dp(if (control == Control.ATTACK) 11f else 10f), radius * .65f)
                canvas.drawText(control.label, cx, cy + text.textSize * .34f, text)
            }
            if (editing) {
                val hitRadius = if (control == Control.MOVE) min(rect.width() * .105f, rect.height() * .19f) * sizeScale
                    else buttonBase * (if (control == Control.ATTACK) 1.24f else .88f)
                stroke.color = if (dragging == control) Color.WHITE else Color.argb(210, 178, 232, 255)
                stroke.strokeWidth = dp(2f)
                canvas.drawCircle(cx, cy, hitRadius + dp(4f), stroke)
            }
        }
        if (editing) {
            fill.shader = null
            fill.color = Color.argb(195, 4, 13, 31)
            val top = (rect.top + dp(8f)).coerceAtLeast(0f)
            canvas.drawRoundRect(RectF(rect.left + dp(8f), top, rect.right - dp(8f), top + dp(29f)), dp(9f), dp(9f), fill)
            text.textSize = dp(11f)
            canvas.drawText("ATUR GAMEPAD • SERET ANALOG / TOMBOL", rect.centerX(), top + dp(19f), text)
        }
    }

    private fun drawGlassCircle(canvas: Canvas, x: Float, y: Float, radius: Float, innerColor: Int, edgeColor: Int, outline: Boolean) {
        fill.color = edgeColor
        canvas.drawCircle(x, y, radius, fill)
        fill.color = innerColor
        canvas.drawCircle(x, y, radius * .90f, fill)
        stroke.color = if (outline) Color.argb(230, 212, 243, 255) else Color.argb(155, 159, 211, 255)
        stroke.strokeWidth = dp(if (outline) 2f else 1f)
        canvas.drawCircle(x, y, radius, stroke)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val rect = displayRect()
        if (rect.width() <= 0 || rect.height() <= 0) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                val hostId = event.getPointerId(index)
                val control = if (editing) controlAt(event.getX(index), event.getY(index), rect, editHit = true)
                    else controlAt(event.getX(index), event.getY(index), rect, editHit = false)
                if (control == null) return active.isNotEmpty()
                if (editing) {
                    dragging = control
                    moveControl(control, event.getX(index), event.getY(index), rect)
                    invalidateOverlay()
                    return true
                }
                if (active.size >= 5) {
                    onMessage("Gamepad mendukung maksimal 5 sentuhan bersamaan.")
                    return true
                }
                if (active.values.any { it.control == control }) return true
                active[hostId] = Active(control, event.getX(index), event.getY(index))
                sendPointerTransition(control, down = true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (editing) {
                    dragging?.let { moveControl(it, event.x, event.y, rect); invalidateOverlay() }
                    return true
                }
                if (active.isEmpty()) return false
                for (i in 0 until event.pointerCount) {
                    val id = event.getPointerId(i)
                    active[id]?.let { it.x = event.getX(i); it.y = event.getY(i) }
                }
                active.values.forEach { item ->
                    if (item.control == Control.MOVE) {
                        val p = positions.getValue(Control.MOVE)
                        val cx = rect.left + rect.width() * p.first
                        val cy = rect.top + rect.height() * p.second
                        val max = min(rect.width() * .075f, rect.height() * .12f) * sizeScale
                        var dx = item.x - cx; var dy = item.y - cy
                        val length = hypot(dx, dy)
                        if (length > max && length > 0f) { dx *= max / length; dy *= max / length }
                        stickDx = dx; stickDy = dy
                    }
                }
                emitSnapshot(MotionEvent.ACTION_MOVE)
                invalidateOverlay()
                return true
            }
            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> {
                if (editing) {
                    dragging = null
                    invalidateOverlay()
                    return true
                }
                val id = event.getPointerId(event.actionIndex)
                if (active.containsKey(id)) {
                    val liftedControl = active.getValue(id).control
                    sendPointerTransition(liftedControl, down = false)
                    active.remove(id)
                    if (liftedControl == Control.MOVE) { stickDx = 0f; stickDy = 0f }
                }
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    stickDx = 0f; stickDy = 0f
                    performClick()
                }
                invalidateOverlay()
                return active.isNotEmpty() || event.actionMasked == MotionEvent.ACTION_UP
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelActive()
                dragging = null
                return true
            }
        }
        return editing || active.isNotEmpty()
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    private fun sendPointerTransition(control: Control, down: Boolean) {
        val pointers = active.entries.sortedBy { it.value.control.id }
        val action = if (pointers.size == 1) {
            if (down) MotionEvent.ACTION_DOWN else MotionEvent.ACTION_UP
        } else {
            val pointerIndex = pointers.indexOfFirst { it.value.control == control }.coerceAtLeast(0)
            (if (down) MotionEvent.ACTION_POINTER_DOWN else MotionEvent.ACTION_POINTER_UP) or
                (pointerIndex shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        }
        emitTouchFrame(action, pointers.map { it.value.control.id }.toIntArray(),
            pointers.map { targetPoint(it.value.control, it.value.x, it.value.y).first }.toFloatArray(),
            pointers.map { targetPoint(it.value.control, it.value.x, it.value.y).second }.toFloatArray())
    }

    private fun emitSnapshot(action: Int) {
        val pointers = active.values.sortedBy { it.control.id }
        if (pointers.isEmpty()) return
        val points = pointers.map { targetPoint(it.control, it.x, it.y) }
        emitTouchFrame(action, pointers.map { it.control.id }.toIntArray(), points.map { it.first }.toFloatArray(), points.map { it.second }.toFloatArray())
    }

    private fun targetPoint(control: Control, x: Float, y: Float): Pair<Float, Float> {
        val rect = displayRect()
        val size = displaySize()
        val p = positions.getValue(control)
        if (control == Control.MOVE) {
            val cx = rect.left + rect.width() * p.first
            val cy = rect.top + rect.height() * p.second
            val max = min(rect.width() * .075f, rect.height() * .12f) * sizeScale
            val dx = ((x - cx) / max.coerceAtLeast(1f)).coerceIn(-1f, 1f)
            val dy = ((y - cy) / max.coerceAtLeast(1f)).coerceIn(-1f, 1f)
            return ((p.first + dx * .08f).coerceIn(0f, .999f) * size.first) to
                ((p.second + dy * .14f).coerceIn(0f, .999f) * size.second)
        }
        val isAim = control == Control.SKILL_1 || control == Control.SKILL_2 || control == Control.SKILL_3 || control == Control.SPELL
        val point = if (isAim && active.values.any { it.control == control }) {
            ((x - rect.left) / rect.width().coerceAtLeast(1f)).coerceIn(.01f, .99f) to
                ((y - rect.top) / rect.height().coerceAtLeast(1f)).coerceIn(.01f, .99f)
        } else p.first to p.second
        return (point.first.coerceIn(0f, .999f) * size.first) to
            (point.second.coerceIn(0f, .999f) * size.second)
    }

    private fun controlAt(x: Float, y: Float, rect: RectF, editHit: Boolean): Control? {
        if (!rect.contains(x, y)) return null
        val base = min(rect.width() * .038f, rect.height() * .070f) * sizeScale
        return Control.entries.minByOrNull { control ->
            val p = positions.getValue(control)
            hypot(x - (rect.left + rect.width() * p.first), y - (rect.top + rect.height() * p.second))
        }?.takeIf { control ->
            val p = positions.getValue(control)
            val radius = if (control == Control.MOVE) min(rect.width() * .105f, rect.height() * .19f) * sizeScale
                else base * (if (control == Control.ATTACK) 1.24f else .88f)
            hypot(x - (rect.left + rect.width() * p.first), y - (rect.top + rect.height() * p.second)) <= radius + if (editHit) dp(14f) else 0f
        }
    }

    private fun moveControl(control: Control, x: Float, y: Float, rect: RectF) {
        if (!rect.contains(x, y)) return
        val px = ((x - rect.left) / rect.width().coerceAtLeast(1f)).coerceIn(.02f, .98f)
        val py = ((y - rect.top) / rect.height().coerceAtLeast(1f)).coerceIn(.02f, .98f)
        positions[control] = px to py
        prefs.edit().putFloat("${control.name}_x", px).putFloat("${control.name}_y", py).apply()
    }

    private fun invalidateOverlay() {
        val rect = displayRect()
        if (rect.width() <= 0f || rect.height() <= 0f) {
            super.invalidate()
            return
        }
        val margin = dp(36f).toInt()
        postInvalidateOnAnimation(
            (rect.left.toInt() - margin).coerceAtLeast(0),
            (rect.top.toInt() - margin).coerceAtLeast(0),
            (rect.right.toInt() + margin).coerceAtMost(width),
            (rect.bottom.toInt() + margin).coerceAtMost(height)
        )
    }

    private fun dp(value: Float): Float = value * density
}
