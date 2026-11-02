package com.farmerbb.taskbar.virtualdisplay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import kotlin.math.abs

/**
 * Floating native-screen trackpad. Cursor coordinates are absolute coordinates on built-in
 * display 0; the virtual display does not need to be running.
 */
class NativeTrackpadService : Service() {
    private lateinit var bridge: ShizukuBridge
    private var windowManager: WindowManager? = null
    private var pad: View? = null
    private var cursorX = 0f
    private var cursorY = 0f
    private var screenWidth = 1
    private var screenHeight = 1
    private var lastX = 0f
    private var lastY = 0f
    private var lastCentroidY = 0f
    private var lastCentroidX = 0f
    private var sensitivity = 1.8f
    private var multiFinger = false
    @Volatile private var shizukuConnected = false
    private var trackpadLabel: TextView? = null

    override fun onCreate() {
        super.onCreate()
        bridge = ShizukuBridge(applicationContext) { message ->
            Log.i(TAG, "Shizuku: $message")
        }
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager?.defaultDisplay?.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels.coerceAtLeast(1)
        screenHeight = metrics.heightPixels.coerceAtLeast(1)
        cursorX = screenWidth / 2f
        cursorY = screenHeight / 2f
        bridge.connect { connected ->
            shizukuConnected = connected
            Log.i(TAG, if (connected) "Shizuku siap untuk Native Trackpad" else "Shizuku belum siap untuk Native Trackpad")
            trackpadLabel?.post {
                trackpadLabel?.text = if (connected) "SAKU TRACKPAD • SIAP" else "SHIZUKU BELUM SIAP"
                trackpadLabel?.alpha = if (connected) 0.58f else 0.9f
            }
        }
        showTrackpad()
    }

    private fun showTrackpad() {
        if (pad != null) return
        val label = TextView(this).apply {
            text = if (shizukuConnected) "SAKU TRACKPAD • SIAP" else "MENGHUBUNGKAN SHIZUKU..."
            textSize = 11f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            alpha = 0.58f
            background = GradientDrawable().apply {
                setColor(Color.argb(105, 25, 35, 55))
                cornerRadius = 28f
                setStroke(1, Color.argb(90, 255, 255, 255))
            }
            setOnTouchListener { _, event -> handleTrackpad(event) }
        }
        trackpadLabel = label
        val type = if (android.os.Build.VERSION.SDK_INT >= 26) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            (screenWidth * 0.72f).toInt().coerceAtLeast(240),
            (screenHeight * 0.28f).toInt().coerceAtLeast(150),
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = (screenHeight * 0.04f).toInt()
        }
        runCatching {
            windowManager?.addView(label, params)
            pad = label
        }.onFailure { Log.e(TAG, "Gagal menampilkan panel Native Trackpad", it) }
    }

    private fun handleTrackpad(event: MotionEvent): Boolean {
        if (!shizukuConnected) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                multiFinger = false
                lastX = event.x
                lastY = event.y
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                multiFinger = true
                lastCentroidX = centroidX(event)
                lastCentroidY = centroidY(event)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount >= 2 || multiFinger) {
                    val cx = centroidX(event)
                    val cy = centroidY(event)
                    val dx = cx - lastCentroidX
                    val dy = cy - lastCentroidY
                    if (abs(dx) + abs(dy) > 0.5f) {
                        bridge.injectMouseScroll(
                            0, cursorX, cursorY,
                            -dx / 90f, -dy / 90f
                        )
                    }
                    lastCentroidX = cx
                    lastCentroidY = cy
                } else {
                    val dx = event.x - lastX
                    val dy = event.y - lastY
                    cursorX = (cursorX + dx * sensitivity).coerceIn(0f, (screenWidth - 1).toFloat())
                    cursorY = (cursorY + dy * sensitivity).coerceIn(0f, (screenHeight - 1).toFloat())
                    bridge.injectHover(0, cursorX, cursorY)
                }
                lastX = event.x
                lastY = event.y
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!multiFinger) bridge.injectMouseClick(0, cursorX, cursorY)
                multiFinger = false
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                multiFinger = false
                return true
            }
        }
        return true
    }

    private fun centroidX(event: MotionEvent): Float {
        var total = 0f
        for (i in 0 until event.pointerCount) total += event.getX(i)
        return total / event.pointerCount.coerceAtLeast(1)
    }

    private fun centroidY(event: MotionEvent): Float {
        var total = 0f
        for (i in 0 until event.pointerCount) total += event.getY(i)
        return total / event.pointerCount.coerceAtLeast(1)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        runCatching { pad?.let { windowManager?.removeView(it) } }
        pad = null
        trackpadLabel = null
        if (::bridge.isInitialized) bridge.closeConnectionOnly()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "SakuNativeTrackpad"
    }
}
