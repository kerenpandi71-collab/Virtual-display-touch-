package com.farmerbb.taskbar.virtualdisplay

import android.app.AlertDialog
import android.app.Presentation
import android.hardware.display.DisplayManager

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import java.util.concurrent.Executors
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.KeyCharacterMap
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.EditorInfo
import android.view.KeyEvent
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ScrollView
import android.widget.SeekBar
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.ComponentActivity

/** Main touchpad UI. Preview fills its frame; touchpad maps to the fixed 720 x 1280 virtual display. */
class VirtualDisplayTouchActivity : ComponentActivity() {
    private data class DirectTouchFrame(
        val controller: VirtualDisplayController,
        val action: Int,
        val pointerIds: IntArray,
        val xs: FloatArray,
        val ys: FloatArray,
        val finalUp: Boolean
    )
    private lateinit var status: TextView
    private lateinit var runtimeLog: TextView
    private lateinit var runtimeLogScroll: ScrollView
    private val runtimeLogBuffer = StringBuilder()
    private var targetWidth = 960
    private var targetHeight = 820
    private var targetDpi = 160
    private var cursorSensitivity = 0.45f
    private var touchpadHeightPercent = 36
    private var touchpadWidthPercent = 27
    private var touchpadLandscapeHeightPercent = 94
    private var touchpadPortraitHeightPercent = 30
    private var touchpadPortraitWidthPercent = 100
    private var touchpadPortraitHasCustomPosition = false
    private var touchpadLandscapeHasCustomPosition = false
    private var touchpadPortraitXPercent = 50
    private var touchpadPortraitYPercent = 70
    private var touchpadLandscapeXPercent = 0
    private var touchpadLandscapeYPercent = 50
    private var touchpadDragStartX = 0f
    private var touchpadDragStartY = 0f
    private var touchpadDragOriginX = 0
    private var touchpadDragOriginY = 0
    private var touchpadDragStarted = false
    private lateinit var touchpadWidthValue: TextView
    private lateinit var resolutionWidthInput: EditText
    private lateinit var resolutionHeightInput: EditText
    private lateinit var resolutionDpiInput: EditText
    private var resolutionEditorLandscape = false
    private lateinit var resolutionProfileLabel: TextView
    private lateinit var touchpadSizeValue: TextView
    private lateinit var sensitivityValue: TextView
    private lateinit var touchpadSizeSeekBar: SeekBar
    private lateinit var shizukuBridge: ShizukuBridge
    private var virtualDisplayController: VirtualDisplayController? = null
    private var recentAppsPresentation: Presentation? = null
    private var preview: SurfaceView? = null
    private var cursorOverlay: View? = null
    private val mouseInputExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "SakuDirectMouseInput").apply { isDaemon = true }
    }
    // Keep keyboard batches independent from high-frequency trackpad/mouse events.
    private val keyboardInputExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "SakuCyberdeckKeyboard").apply { isDaemon = true }
    }
    private lateinit var runtimeLogToggle: Button
    private lateinit var bottomPanel: LinearLayout
    private lateinit var keyboardPanel: LinearLayout
    private lateinit var imeBridgeEditText: EditText
    private var keyboardPanelVisible = false
    private var trackpadPanelEnabled = false
    private var physicalScreenDimmed = false
    private lateinit var physicalScreenDimButton: Button
    private var assistantBallView: TextView? = null
    private var assistantBallPortraitXPercent = 95
    private var assistantBallPortraitYPercent = 50
    private var assistantBallLandscapeXPercent = 95
    private var assistantBallLandscapeYPercent = 50
    @Volatile private var systemKeyboardRequested = false
    private var keyboardWidthPercent = 100
    private var keyboardHeightDp = 240
    private var keyboardHasCustomPosition = false
    private var keyboardLandscapeHasCustomPosition = false
    private var keyboardLandscapeXPercent = 100
    private var keyboardLandscapeYPercent = 50
    private var keyboardPositionX = 0
    private var keyboardPositionY = 0
    private var keyboardDragStartX = 0f
    private var keyboardDragStartY = 0f
    private var keyboardDragOriginX = 0
    private var keyboardDragOriginY = 0
    private var cyberShift = false
    private var cyberCtrl = false
    private var cyberAlt = false
    private var cyberSymbols = false
    private val keyboardRepeatHandler = Handler(Looper.getMainLooper())
    private lateinit var panelToggleButton: Button
    private lateinit var directTouchButton: Button
    private lateinit var previewSizeSeekBar: SeekBar
    private lateinit var panelPositionSeekBar: SeekBar
    private lateinit var previewSizeValue: TextView
    private lateinit var panelPositionValue: TextView
    private var previewSizePercent = 100
    private var previewSizePortraitPercent = 100
    private var previewSizeLandscapePercent = 100
    private var keyboardLandscapeWidthPercent = 100
    private var keyboardLandscapeHeightDp = 240
    private var panelPositionPercent = 0
    private var directTouchDown = false
    // 0 = trackpad on preview, 1 = direct touch, 2 = disabled
    private var previewInputMode = 1
    private val gestureHandler = Handler(Looper.getMainLooper())
    private var longPressTask: Runnable? = null
    private var touchpadFingerDown = false
    private var dragActive = false
    private lateinit var runtimeLogPanel: LinearLayout
    private var suppressKeyboardWatcher = false
    private val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val cursorStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.BLACK
    }
    private var cursorPulseRadius = 13f
    private var cursorX = targetWidth / 2f
    private var cursorY = targetHeight / 2f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var downX = 0f
    private var downY = 0f
    private var pointerMoved = false
    private var hadMultiTouch = false
    private var scrollGestureActive = false
    private var lastScrollCentroidX = 0f
    private var lastScrollCentroidY = 0f

    private val directTouchQueueLock = Any()
    private val directTouchQueue = java.util.ArrayDeque<DirectTouchFrame>()
    private var directTouchDrainScheduled = false
    private val mouseMoveLock = Any()
    private var pendingMouseX = Float.NaN
    private var pendingMouseY = Float.NaN
    private var mouseMoveDrainScheduled = false

    private val scrollLock = Any()
    private var pendingScrollHorizontal = 0f
    private var pendingScrollVertical = 0f
    private var pendingScrollX = 0f
    private var pendingScrollY = 0f
    private var scrollDrainScheduled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val landscapeAtLaunch = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val displayPrefs = getSharedPreferences("saku_desktop_resolution", Context.MODE_PRIVATE)
        val resolutionSuffix = if (landscapeAtLaunch) "landscape" else "portrait"
        targetWidth = displayPrefs.getInt("${resolutionSuffix}_width", 960).coerceIn(320, 2560)
        targetHeight = displayPrefs.getInt("${resolutionSuffix}_height", 820).coerceIn(320, 2560)
        targetDpi = displayPrefs.getInt("${resolutionSuffix}_dpi", 160).coerceIn(40, 640)
        resolutionEditorLandscape = landscapeAtLaunch
        previewSizePortraitPercent = displayPrefs.getInt("preview_portrait_percent", 100).coerceIn(50, 100)
        previewSizeLandscapePercent = displayPrefs.getInt("preview_landscape_percent", 100).coerceIn(50, 100)
        previewSizePercent = if (landscapeAtLaunch) previewSizeLandscapePercent else previewSizePortraitPercent
        val keyboardPrefs = getSharedPreferences("saku_desktop_keyboard", Context.MODE_PRIVATE)
        keyboardWidthPercent = keyboardPrefs.getInt("width_percent", 100).coerceIn(55, 100)
        keyboardHeightDp = keyboardPrefs.getInt("height_dp", 240).coerceIn(170, 360)
        keyboardLandscapeWidthPercent = keyboardPrefs.getInt("landscape_width_percent", 100).coerceIn(55, 100)
        keyboardLandscapeHeightDp = keyboardPrefs.getInt("landscape_height_dp", 240).coerceIn(170, 360)
        keyboardHasCustomPosition = keyboardPrefs.getBoolean("custom_position", false)
        keyboardLandscapeHasCustomPosition = keyboardPrefs.getBoolean("landscape_custom_position", false)
        keyboardLandscapeXPercent = keyboardPrefs.getInt("landscape_x_percent", 100).coerceIn(0, 100)
        keyboardLandscapeYPercent = keyboardPrefs.getInt("landscape_y_percent", 50).coerceIn(0, 100)
        keyboardPositionX = keyboardPrefs.getInt("position_x", 0)
        keyboardPositionY = keyboardPrefs.getInt("position_y", 0)
        val touchpadPrefs = getSharedPreferences("saku_desktop_touchpad", Context.MODE_PRIVATE)
        touchpadWidthPercent = touchpadPrefs.getInt("width_percent", 27).coerceIn(18, 45)
        touchpadLandscapeHeightPercent = touchpadPrefs.getInt("height_percent", 94).coerceIn(40, 100)
        touchpadPortraitHeightPercent = touchpadPrefs.getInt("portrait_height_percent", 30).coerceIn(15, 65)
        touchpadPortraitWidthPercent = touchpadPrefs.getInt("portrait_width_percent", 100).coerceIn(60, 100)
        // Migrate once from old shared position keys. They could retain a stale
        // right-side position and override the new left-side landscape default.
        val landscapePositionV2 = touchpadPrefs.getBoolean("landscape_position_v2", false)
        touchpadLandscapeHasCustomPosition = landscapePositionV2 &&
            touchpadPrefs.getBoolean("landscape_custom_position", false)
        touchpadLandscapeXPercent = if (landscapePositionV2) {
            touchpadPrefs.getInt("landscape_x_percent", 0).coerceIn(0, 100)
        } else 0
        touchpadLandscapeYPercent = if (landscapePositionV2) {
            touchpadPrefs.getInt("landscape_y_percent", 50).coerceIn(0, 100)
        } else 50
        if (!landscapePositionV2) {
            touchpadPrefs.edit()
                .putBoolean("landscape_position_v2", true)
                .putBoolean("landscape_custom_position", false)
                .putInt("landscape_x_percent", 0)
                .putInt("landscape_y_percent", 50)
                .apply()
        }
        touchpadPortraitHasCustomPosition = touchpadPrefs.getBoolean("portrait_custom_position", false)
        touchpadPortraitXPercent = touchpadPrefs.getInt("portrait_x_percent", 50).coerceIn(0, 100)
        touchpadPortraitYPercent = touchpadPrefs.getInt("portrait_y_percent", 70).coerceIn(0, 100)
        // Keep the IME host on the phone display; never auto-open Gboard on Activity launch.
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
        )
        applyImmersiveMode()

        // SurfaceView tetap memenuhi root; toolbar, kursor, touchpad, dan log adalah overlay.
        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(5, 10, 32)) }
        preview = SurfaceView(this).apply {
            contentDescription = "Desktop virtual display 960 x 820"
            setOnTouchListener { view, event ->
                when (previewInputMode) {
                    0 -> handleTouch(event, view.width, view.height)
                    1 -> handleDirectTouch(event, view.width, view.height)
                    else -> false
                }
            }
            holder.setFixedSize(targetWidth, targetHeight)
            holder.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) {
                    virtualDisplayController?.updateSurface(holder.surface)
                }
                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                    virtualDisplayController?.updateSurface(holder.surface)
                    cursorOverlay?.invalidate()
                }
                override fun surfaceDestroyed(holder: SurfaceHolder) = Unit
            })
        }
        root.addView(preview, FrameLayout.LayoutParams(-1, dp(240), Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        cursorOverlay = object : View(this) {
            override fun onDraw(canvas: Canvas) { super.onDraw(canvas); drawDextopCursor(canvas) }
        }.apply {
            isClickable = false
            isFocusable = false
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = "Kursor putih Saku Desktop"
        }
        root.addView(cursorOverlay, FrameLayout.LayoutParams(-1, -1))
        // Keep the existing overlay cursor visible; only its icon is changed to a mouse pointer.

        shizukuBridge = ShizukuBridge(this) { message ->
            runOnUiThread {
                if (::status.isInitialized) status.text = message
                appendRuntimeLog(message)
            }
        }

        fun actionButton(label: String, action: () -> Unit): Button = Button(this).apply {
            text = label
            textSize = 9f
            isAllCaps = false
            setTextColor(Color.rgb(225, 249, 255))
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            minHeight = dp(32); minimumHeight = dp(32)
            minWidth = 0; minimumWidth = 0
            setPadding(dp(3), 0, dp(3), 0)
            background = GradientDrawable().apply {
                setColor(Color.rgb(13, 42, 73))
                cornerRadius = dp(7).toFloat()
                setStroke(dp(1), Color.rgb(0, 190, 235))
            }
            setOnClickListener { action() }
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(4), dp(6), dp(4))
            background = roundedPanel(220, 12)
        }
        topBar.addView(TextView(this).apply {
            text = "CYBER DECK  //  VIRTUAL DISPLAY CONTROL"
            textSize = 12f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(-1, dp(20)))

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        actions.addView(actionButton("Hubungkan") { shizukuBridge.connect() }, LinearLayout.LayoutParams(0, dp(34), 1.2f))
        actions.addView(actionButton("Root") {
            shizukuBridge.connect { connected ->
                if (!connected) {
                    runOnUiThread { status.text = "Hubungkan Shizuku sebelum meminta root." }
                } else {
                    keyboardInputExecutor.execute {
                        val result = shizukuBridge.requestRootAccess()
                        runOnUiThread {
                            status.text = result
                            appendRuntimeLog(result)
                        }
                    }
                }
            }
        }, LinearLayout.LayoutParams(0, dp(34), 0.65f))
        actions.addView(actionButton("Mulai") { startOwnedVirtualDisplay() }, LinearLayout.LayoutParams(0, dp(34), 0.75f))
        actions.addView(actionButton("Cek") {
            val id = virtualDisplayController?.displayId ?: -1
            if (id < 0) status.text = "Mulai virtual display dahulu." else Thread({
                val detail = shizukuBridge.getDisplayDiagnostics(id)
                runOnUiThread {
                    appendRuntimeLog("Diagnostik display $id\n$detail")
                    status.text = "Diagnostik selesai • detail di Log"
                }
            }, "SakuDisplayDiagnostics").start()
        }, LinearLayout.LayoutParams(0, dp(34), 0.5f))
        actions.addView(actionButton("Tutup") {
            runCatching { recentAppsPresentation?.dismiss() }
            recentAppsPresentation = null
            virtualDisplayController?.close()
            virtualDisplayController = null
            clearPreview()
            status.text = "Virtual display ditutup."
        }, LinearLayout.LayoutParams(0, dp(34), 0.65f))
        runtimeLogToggle = actionButton("Log") { toggleRuntimeLog() }
        actions.addView(runtimeLogToggle, LinearLayout.LayoutParams(0, dp(34), 0.48f))
        panelToggleButton = actionButton("Panel") { toggleInputPanel() }
        // Legacy trackpad panel removed; input uses Direct Touch on the screen.
        directTouchButton = actionButton("Touch: ON") { toggleDirectTouch() }
        actions.addView(directTouchButton, LinearLayout.LayoutParams(0, dp(34), 0.78f))
        topBar.addView(actions)
        val displayPowerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        physicalScreenDimButton = actionButton("Redupkan layar HP") { togglePhysicalScreenDim() }
        displayPowerRow.addView(
            physicalScreenDimButton,
            LinearLayout.LayoutParams(0, dp(32), 1f)
        )
        displayPowerRow.addView(TextView(this).apply {
            text = "Virtual Display tetap aktif"
            textSize = 9f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(0, dp(32), 1f))
        topBar.addView(displayPowerRow)
        val previewSizeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        previewSizeValue = TextView(this).apply { text = "Desktop ${previewSizePercent}%"; textSize = 9f; setTextColor(Color.WHITE) }
        previewSizeRow.addView(previewSizeValue, LinearLayout.LayoutParams(dp(84), dp(26)))
        previewSizeSeekBar = SeekBar(this).apply {
            max = 50
            progress = previewSizePercent - 50
            contentDescription = "Ukuran preview desktop 50 sampai 100 persen"
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    previewSizePercent = 50 + progress
                    if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) previewSizeLandscapePercent = previewSizePercent else previewSizePortraitPercent = previewSizePercent
                    getSharedPreferences("saku_desktop_resolution", Context.MODE_PRIVATE).edit()
                        .putInt("preview_portrait_percent", previewSizePortraitPercent)
                        .putInt("preview_landscape_percent", previewSizeLandscapePercent).apply()
                    previewSizeValue.text = "Desktop ${previewSizePercent}%"
                    updateDesktopLayout(root.width, root.height)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        previewSizeRow.addView(previewSizeSeekBar, LinearLayout.LayoutParams(0, dp(28), 1f))
        topBar.addView(previewSizeRow)
        val panelPositionRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        panelPositionValue = TextView(this).apply { text = "Tinggi layar 100%"; textSize = 9f; setTextColor(Color.WHITE) }
        panelPositionRow.addView(panelPositionValue, LinearLayout.LayoutParams(dp(84), dp(26)))
        panelPositionSeekBar = SeekBar(this).apply {
            max = 100
            progress = 100
            panelPositionPercent = 100
            contentDescription = "Tinggi layar virtual 0 sampai 100 persen"
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    panelPositionPercent = progress
                    panelPositionValue.text = "Tinggi layar ${panelPositionPercent}%"
                    updateDesktopLayout(root.width, root.height)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        panelPositionRow.addView(panelPositionSeekBar, LinearLayout.LayoutParams(0, dp(28), 1f))
        topBar.addView(panelPositionRow)
        val resolutionProfileRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        resolutionProfileLabel = TextView(this).apply {
            text = if (resolutionEditorLandscape) "Profil: Landscape" else "Profil: Portrait"
            textSize = 10f; setTextColor(Color.WHITE)
        }
        resolutionProfileRow.addView(resolutionProfileLabel, LinearLayout.LayoutParams(0, dp(30), 1f))
        resolutionProfileRow.addView(actionButton("Portrait") { selectResolutionProfile(false) }, LinearLayout.LayoutParams(dp(86), dp(30)))
        resolutionProfileRow.addView(actionButton("Landscape") { selectResolutionProfile(true) }, LinearLayout.LayoutParams(dp(92), dp(30)))
        topBar.addView(resolutionProfileRow)
        val resolutionRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        fun resolutionInput(initial: String, hintText: String, widthDp: Int) = EditText(this).apply {
            setText(initial); hint = hintText; textSize = 10f; setSingleLine(true)
            setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setPadding(dp(4), 0, dp(4), 0)
            background = roundedPanel(180, 6)
            layoutParams = LinearLayout.LayoutParams(dp(widthDp), dp(32)).apply { rightMargin = dp(3) }
        }
        resolutionWidthInput = resolutionInput(targetWidth.toString(), "Lebar", 62)
        resolutionHeightInput = resolutionInput(targetHeight.toString(), "Tinggi", 58)
        resolutionDpiInput = resolutionInput(targetDpi.toString(), "DPI", 48)
        resolutionRow.addView(resolutionWidthInput)
        resolutionRow.addView(resolutionHeightInput)
        resolutionRow.addView(resolutionDpiInput)
        resolutionRow.addView(actionButton("Terapkan") { applyDisplaySettings() },
            LinearLayout.LayoutParams(0, dp(32), 1f))
        topBar.addView(resolutionRow)
        val controlRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        sensitivityValue = TextView(this).apply { text = "Kursor 45%"; textSize = 9f; setTextColor(Color.WHITE) }
        controlRow.addView(sensitivityValue, LinearLayout.LayoutParams(dp(76), dp(25)))
        val sensitivitySeekBar = SeekBar(this).apply {
            max = 80; progress = 25
            contentDescription = "Sensitivitas kursor"
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    cursorSensitivity = (20 + progress) / 100f
                    sensitivityValue.text = "Kursor ${(cursorSensitivity * 100).toInt()}%"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        controlRow.addView(sensitivitySeekBar, LinearLayout.LayoutParams(0, dp(25), 1f))
        topBar.addView(controlRow)
        status = TextView(this).apply {
            text = "Hubungkan Shizuku, lalu tekan Mulai."
            textSize = 9f
            maxLines = 2
            setTextColor(Color.rgb(105, 221, 245))
            setPadding(dp(2), dp(2), dp(2), 0)
        }
        topBar.addView(status, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        topBar.addView(actionButton("Sembunyikan panel pengaturan") {
            topBar.visibility = View.GONE
        }, LinearLayout.LayoutParams(-1, dp(30)))
        // Semua pengaturan berada di panel yang dibuka dari bola asisten.
        topBar.visibility = View.GONE
        root.addView(topBar, FrameLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP).apply {
            leftMargin = dp(7); rightMargin = dp(7); topMargin = dp(7)
        })
        val assistantBall = TextView(this).apply {
            text = "V"
            textSize = 19f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setOrientation(GradientDrawable.Orientation.TL_BR)
                setColors(intArrayOf(Color.rgb(0, 220, 255), Color.rgb(68, 54, 235), Color.rgb(148, 42, 255)))
                setStroke(dp(2), Color.rgb(116, 239, 255))
            }
            elevation = dp(12).toFloat()
            contentDescription = "Bola asisten Saku Desktop. Ketuk untuk pengaturan, seret untuk memindahkan."
            isClickable = true
        }
        val ballParams = FrameLayout.LayoutParams(dp(52), dp(52), Gravity.END or Gravity.CENTER_VERTICAL).apply {
            rightMargin = dp(12)
        }
        var ballDownX = 0f
        var ballDownY = 0f
        var ballStartX = 0f
        var ballStartY = 0f
        var ballDragged = false
        assistantBall.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    ballDownX = event.rawX; ballDownY = event.rawY
                    ballStartX = view.left.toFloat().coerceIn(0f, (root.width - view.width).coerceAtLeast(0).toFloat())
                    ballStartY = view.top.toFloat().coerceIn(0f, (root.height - view.height).coerceAtLeast(0).toFloat())
                    ballDragged = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - ballDownX
                    val dy = event.rawY - ballDownY
                    if (kotlin.math.abs(dx) > dp(5) || kotlin.math.abs(dy) > dp(5)) ballDragged = true
                    if (ballDragged) {
                        ballParams.gravity = Gravity.TOP or Gravity.START
                        ballParams.leftMargin = (ballStartX + dx).toInt().coerceIn(0, (root.width - view.width).coerceAtLeast(0))
                        ballParams.topMargin = (ballStartY + dy).toInt().coerceIn(0, (root.height - view.height).coerceAtLeast(0))
                        root.updateViewLayout(view, ballParams)
                        val maxX = (root.width - view.width).coerceAtLeast(1)
                        val maxY = (root.height - view.height).coerceAtLeast(1)
                        val xPercent = (ballParams.leftMargin.toFloat() / maxX * 100).toInt().coerceIn(0, 100)
                        val yPercent = (ballParams.topMargin.toFloat() / maxY * 100).toInt().coerceIn(0, 100)
                        val landscapeNow = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                        if (landscapeNow) {
                            assistantBallLandscapeXPercent = xPercent
                            assistantBallLandscapeYPercent = yPercent
                        } else {
                            assistantBallPortraitXPercent = xPercent
                            assistantBallPortraitYPercent = yPercent
                        }
                        getSharedPreferences("saku_assistant_ball", Context.MODE_PRIVATE).edit()
                            .putInt(if (landscapeNow) "landscape_x" else "portrait_x", xPercent)
                            .putInt(if (landscapeNow) "landscape_y" else "portrait_y", yPercent)
                            .apply()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!ballDragged) {
                        val menu = android.widget.PopupMenu(this, view)
                        menu.menu.add(0, 1, 0, "Pengaturan Cyber Deck")
                        menu.menu.add(0, 2, 1, if (systemKeyboardRequested) "Sembunyikan Keyboard Android" else "Keyboard Android")
                        menu.menu.add(0, 3, 2, "Keyboard Custom")
                        menu.menu.add(0, 4, 3, if (keyboardPanelVisible) "Sembunyikan Keyboard Custom" else "Tampilkan Keyboard Custom")
                        val launcherPrefs = getSharedPreferences("saku_desktop_launcher", Context.MODE_PRIVATE)
                        val launcherEnabled = launcherPrefs.getBoolean("enabled", true)
                        menu.menu.add(0, 5, 4, "Buka Saku Desktop Launcher").isEnabled = launcherEnabled
                        menu.menu.add(0, 6, 5, "Launcher bawaan VDT aktif").apply {
                            isCheckable = true
                            isChecked = launcherEnabled
                        }
                        menu.menu.add(0, 7, 6, "◀ Back")
                        menu.menu.add(0, 8, 7, "⌂ Home")
                        menu.menu.add(0, 9, 8, "▢ Recent")
                        menu.setOnMenuItemClickListener { item ->
                            when (item.itemId) {
                                1 -> topBar.visibility = View.VISIBLE
                                2 -> toggleSystemKeyboardBridge()
                                3 -> showVirtualKeyboard()
                                4 -> {
                                    keyboardPanelVisible = !keyboardPanelVisible
                                    keyboardPanel.visibility = if (keyboardPanelVisible) View.VISIBLE else View.GONE
                                    updateDesktopLayout(root.width, root.height)
                                }
                                5 -> showDexLauncherOnVirtualDisplay()
                                6 -> {
                                    val enabled = !launcherPrefs.getBoolean("enabled", true)
                                    launcherPrefs.edit().putBoolean("enabled", enabled).apply()
                                    appendRuntimeLog(if (enabled) "Launcher bawaan VDT diaktifkan." else "Launcher bawaan VDT dinonaktifkan.")
                                    status.text = if (enabled) "Launcher bawaan VDT aktif. Buka dari bola asisten bila diperlukan." else "Launcher bawaan VDT nonaktif; launcher eksternal dapat digunakan."
                                    if (!enabled) sendVirtualNavigationKey(android.view.KeyEvent.KEYCODE_HOME)
                                }
                                7 -> sendVirtualNavigationKey(android.view.KeyEvent.KEYCODE_BACK)
                                8 -> sendVirtualNavigationKey(android.view.KeyEvent.KEYCODE_HOME)
                                9 -> showVirtualDisplayRecentTasks()
                            }
                            true
                        }
                        menu.show()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
        root.addView(assistantBall, ballParams)
        assistantBallView = assistantBall
        val ballPrefs = getSharedPreferences("saku_assistant_ball", Context.MODE_PRIVATE)
        assistantBallPortraitXPercent = ballPrefs.getInt("portrait_x", 95).coerceIn(0, 100)
        assistantBallPortraitYPercent = ballPrefs.getInt("portrait_y", 50).coerceIn(0, 100)
        assistantBallLandscapeXPercent = ballPrefs.getInt("landscape_x", 95).coerceIn(0, 100)
        assistantBallLandscapeYPercent = ballPrefs.getInt("landscape_y", 50).coerceIn(0, 100)
        assistantBall.post {
            val maxX = (root.width - assistantBall.width).coerceAtLeast(0)
            val maxY = (root.height - assistantBall.height).coerceAtLeast(0)
            val landscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            val xPercent = if (landscape) assistantBallLandscapeXPercent else assistantBallPortraitXPercent
            val yPercent = if (landscape) assistantBallLandscapeYPercent else assistantBallPortraitYPercent
            ballParams.gravity = Gravity.TOP or Gravity.START
            ballParams.leftMargin = (maxX * xPercent / 100f).toInt()
            ballParams.topMargin = (maxY * yPercent / 100f).toInt()
            root.updateViewLayout(assistantBall, ballParams)
        }
        // Keyboard sederhana bergaya Gboard: tombol besar, baris standar, tanpa tombol fungsi yang memenuhi ruang.
        // Area preview, trackpad, dan jalur Direct Touch sengaja tidak diubah.
        keyboardPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(5), dp(5), dp(5), dp(5))
            background = roundedPanel(242, 16)
            // Keep the keyboard above the bottom trackpad panel in both drawing
            // and touch-dispatch order; otherwise the trackpad steals key taps.
            elevation = dp(18).toFloat()
            translationZ = dp(2).toFloat()
            contentDescription = "Keyboard virtual sederhana"
        }
        val keyboardTitle = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val keyboardDragHandle = TextView(this).apply {
            text = "Keyboard  ⋮⋮"
            textSize = 12f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(Color.rgb(225, 231, 239))
            gravity = Gravity.CENTER_VERTICAL
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        val params = keyboardPanel.layoutParams as? FrameLayout.LayoutParams
                        if (params != null) {
                            val landscapeNow = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                            if (landscapeNow) {
                                keyboardLandscapeHasCustomPosition = true
                                val maxX = (root.width - keyboardPanel.width).coerceAtLeast(1)
                                val maxY = (root.height - keyboardPanel.height).coerceAtLeast(1)
                                keyboardLandscapeXPercent = (keyboardPanel.left.toFloat() / maxX * 100).toInt().coerceIn(0, 100)
                                keyboardLandscapeYPercent = (keyboardPanel.top.toFloat() / maxY * 100).toInt().coerceIn(0, 100)
                            } else {
                                keyboardHasCustomPosition = true
                            }
                            keyboardPositionX = keyboardPanel.x.toInt().coerceIn(0, (root.width - keyboardPanel.width).coerceAtLeast(0))
                            keyboardPositionY = keyboardPanel.y.toInt().coerceIn(0, (root.height - keyboardPanel.height).coerceAtLeast(0))
                            params.gravity = Gravity.TOP or Gravity.START
                            params.leftMargin = keyboardPositionX
                            params.topMargin = keyboardPositionY
                            params.rightMargin = 0
                            params.bottomMargin = 0
                            keyboardPanel.layoutParams = params
                            keyboardDragStartX = event.rawX
                            keyboardDragStartY = event.rawY
                            keyboardDragOriginX = keyboardPositionX
                            keyboardDragOriginY = keyboardPositionY
                        }
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val params = keyboardPanel.layoutParams as? FrameLayout.LayoutParams
                        if (params != null) {
                            keyboardPositionX = (keyboardDragOriginX + event.rawX - keyboardDragStartX).toInt()
                                .coerceIn(0, (root.width - keyboardPanel.width).coerceAtLeast(0))
                            keyboardPositionY = (keyboardDragOriginY + event.rawY - keyboardDragStartY).toInt()
                                .coerceIn(0, (root.height - keyboardPanel.height).coerceAtLeast(0))
                            params.gravity = Gravity.TOP or Gravity.START
                            params.leftMargin = keyboardPositionX
                            params.topMargin = keyboardPositionY
                            params.rightMargin = 0
                            params.bottomMargin = 0
                            root.updateViewLayout(keyboardPanel, params)
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        val landscapeNow = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                        if (landscapeNow) {
                            val maxX = (root.width - keyboardPanel.width).coerceAtLeast(1)
                            val maxY = (root.height - keyboardPanel.height).coerceAtLeast(1)
                            keyboardLandscapeXPercent = (keyboardPositionX.toFloat() / maxX * 100).toInt().coerceIn(0, 100)
                            keyboardLandscapeYPercent = (keyboardPositionY.toFloat() / maxY * 100).toInt().coerceIn(0, 100)
                        }
                        val keyboardPositionPrefs = getSharedPreferences("saku_desktop_keyboard", Context.MODE_PRIVATE).edit()
                        if (landscapeNow) {
                            keyboardPositionPrefs.putBoolean("landscape_custom_position", true)
                                .putInt("landscape_x_percent", keyboardLandscapeXPercent)
                                .putInt("landscape_y_percent", keyboardLandscapeYPercent)
                        } else {
                            keyboardPositionPrefs.putBoolean("custom_position", true)
                                .putInt("position_x", keyboardPositionX)
                                .putInt("position_y", keyboardPositionY)
                        }
                        keyboardPositionPrefs.apply()
                        true
                    }
                    else -> true
                }
            }
        }
        keyboardTitle.addView(keyboardDragHandle, LinearLayout.LayoutParams(0, dp(24), 1f))
        keyboardTitle.addView(actionButton("W−") {
            if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) keyboardLandscapeWidthPercent = (keyboardLandscapeWidthPercent - 5).coerceAtLeast(55) else keyboardWidthPercent = (keyboardWidthPercent - 5).coerceAtLeast(55)
            persistKeyboardSize()
            updateDesktopLayout(root.width, root.height)
        }, LinearLayout.LayoutParams(dp(35), dp(27)))
        keyboardTitle.addView(actionButton("W+") {
            if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) keyboardLandscapeWidthPercent = (keyboardLandscapeWidthPercent + 5).coerceAtMost(100) else keyboardWidthPercent = (keyboardWidthPercent + 5).coerceAtMost(100)
            persistKeyboardSize()
            updateDesktopLayout(root.width, root.height)
        }, LinearLayout.LayoutParams(dp(35), dp(27)))
        keyboardTitle.addView(actionButton("H−") {
            if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) keyboardLandscapeHeightDp = (keyboardLandscapeHeightDp - 15).coerceAtLeast(170) else keyboardHeightDp = (keyboardHeightDp - 15).coerceAtLeast(170)
            persistKeyboardSize()
            updateDesktopLayout(root.width, root.height)
        }, LinearLayout.LayoutParams(dp(35), dp(27)))
        keyboardTitle.addView(actionButton("H+") {
            if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) keyboardLandscapeHeightDp = (keyboardLandscapeHeightDp + 15).coerceAtMost(360) else keyboardHeightDp = (keyboardHeightDp + 15).coerceAtMost(360)
            persistKeyboardSize()
            updateDesktopLayout(root.width, root.height)
        }, LinearLayout.LayoutParams(dp(35), dp(27)))
        keyboardTitle.addView(actionButton("×") {
            keyboardPanelVisible = false
            keyboardPanel.visibility = View.GONE
            updateDesktopLayout(root.width, root.height)
        }, LinearLayout.LayoutParams(dp(32), dp(27)))
        keyboardPanel.addView(keyboardTitle)

        val layoutSelector = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val layoutButtons = mutableListOf<Button>()
        listOf("NORMAL", "CODING", "PC").forEachIndexed { index, label ->
            val button = actionButton(label) { }
            button.tag = index
            layoutButtons.add(button)
            layoutSelector.addView(button, LinearLayout.LayoutParams(0, dp(27), 1f).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) })
        }
        keyboardPanel.addView(layoutSelector, LinearLayout.LayoutParams(-1, dp(31)))
        val editActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        listOf("Cut", "Copy", "Paste", "Select all").forEach { label ->
            editActions.addView(actionButton(label) { sendKeyboardEditCommand(label) }, LinearLayout.LayoutParams(0, dp(29), 1f).apply { setMargins(dp(2), dp(1), dp(2), dp(1)) })
        }
        keyboardPanel.addView(editActions, LinearLayout.LayoutParams(-1, dp(31)))
        val keyboardRows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        keyboardPanel.addView(keyboardRows, LinearLayout.LayoutParams(-1, 0, 1f))
        fun keyRow(labels: List<String>) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
            labels.forEach { label ->
                val utility = label in setOf("SHIFT", "CTRL", "ALT", "ESC", "TAB", "⌫", "ENTER", "SPACE")
                val button = cyberKey(label, utility, repeatable = label == "⌫") {
                    when (label) {
                        "SHIFT" -> { cyberShift = !cyberShift; updateModifierLabels() }
                        "CTRL" -> { cyberCtrl = !cyberCtrl; updateModifierLabels() }
                        "ALT" -> { cyberAlt = !cyberAlt; updateModifierLabels() }
                        "SPACE" -> sendKeyboardKey(android.view.KeyEvent.KEYCODE_SPACE)
                        "⌫" -> sendKeyboardKey(android.view.KeyEvent.KEYCODE_DEL)
                        "ENTER" -> sendKeyboardKey(android.view.KeyEvent.KEYCODE_ENTER)
                        "TAB" -> sendKeyboardKey(android.view.KeyEvent.KEYCODE_TAB)
                        "ESC" -> sendKeyboardKey(android.view.KeyEvent.KEYCODE_ESCAPE)
                        "↑" -> sendKeyboardKey(android.view.KeyEvent.KEYCODE_DPAD_UP)
                        "↓" -> sendKeyboardKey(android.view.KeyEvent.KEYCODE_DPAD_DOWN)
                        "←" -> sendKeyboardKey(android.view.KeyEvent.KEYCODE_DPAD_LEFT)
                        "→" -> sendKeyboardKey(android.view.KeyEvent.KEYCODE_DPAD_RIGHT)
                        "HOME" -> sendKeyboardKey(android.view.KeyEvent.KEYCODE_MOVE_HOME)
                        "END" -> sendKeyboardKey(android.view.KeyEvent.KEYCODE_MOVE_END)
                        "PG↑" -> sendKeyboardKey(android.view.KeyEvent.KEYCODE_PAGE_UP)
                        "PG↓" -> sendKeyboardKey(android.view.KeyEvent.KEYCODE_PAGE_DOWN)
                        "PRT" -> sendKeyboardKey(android.view.KeyEvent.KEYCODE_SYSRQ)
                        "INS" -> sendKeyboardKey(android.view.KeyEvent.KEYCODE_INSERT)
                        "DEL" -> sendKeyboardKey(android.view.KeyEvent.KEYCODE_FORWARD_DEL)
                        else -> {
                            val functionNumber = if (label.startsWith("F")) label.drop(1).toIntOrNull() else null
                            if (functionNumber != null && functionNumber in 1..12) {
                                sendKeyboardKey(android.view.KeyEvent.KEYCODE_F1 + functionNumber - 1)
                            } else {
                                sendTypedText(if (cyberShift && label.length == 1 && label[0].isLetter()) label.uppercase() else label)
                                if (cyberShift && label.any { it.isLetter() }) { cyberShift = false; updateModifierLabels() }
                            }
                        }
                    }
                }
                button.tag = label
                row.addView(button, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, if (label == "SPACE") 3f else if (utility) 1.5f else 1f).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) })
            }
            keyboardRows.addView(row, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        fun rebuildKeyboardRows() {
            keyboardRows.removeAllViews()
            cyberShift = false; cyberCtrl = false; cyberAlt = false
            val rows = when (keyboardLayoutMode) {
                1 -> listOf(
                    listOf("ESC","TAB","CTRL","ALT","SHIFT","⌫"),
                    listOf("1","2","3","4","5","6","7","8","9","0"),
                    listOf("Q","W","E","R","T","Y","U","I","O","P"),
                    listOf("A","S","D","F","G","H","J","K","L"),
                    listOf("SHIFT","Z","X","C","V","B","N","M","ENTER"),
                    listOf("{","}","[","]","(",")",";",":","="),
                    listOf(",","SPACE",".","/","\\","-","+"),
                    listOf("↑","↓","←","→","HOME","END")
                )
                2 -> listOf(
                    listOf("ESC","F1","F2","F3","F4","F5","F6"),
                    listOf("F7","F8","F9","F10","F11","F12","PRT"),
                    listOf("1","2","3","4","5","6","7","8","9","0","-","=","⌫"),
                    listOf("TAB","Q","W","E","R","T","Y","U","I","O","P","[","]","\\"),
                    listOf("CTRL","A","S","D","F","G","H","J","K","L",";","'","ENTER"),
                    listOf("SHIFT","Z","X","C","V","B","N","M",",",".","/","SHIFT"),
                    listOf("ALT","SPACE","CTRL","←","↑","↓","→"),
                    listOf("HOME","END","PG↑","PG↓","INS","DEL")
                )
                else -> listOf(
                    listOf("1","2","3","4","5","6","7","8","9","0"),
                    listOf("Q","W","E","R","T","Y","U","I","O","P"),
                    listOf("A","S","D","F","G","H","J","K","L"),
                    listOf("SHIFT","Z","X","C","V","B","N","M","⌫"),
                    listOf("?123",",","SPACE",".","ENTER")
                )
            }
            rows.forEach { keyRow(it) }
            updateModifierLabels()
        }
        rebuildKeyboardRows()
        layoutButtons.forEach { button ->
            button.setOnClickListener {
                keyboardLayoutMode = (button.tag as? Int ?: 0).coerceIn(0, 2)
                getSharedPreferences("saku_desktop_keyboard", Context.MODE_PRIVATE).edit().putInt("layout_mode", keyboardLayoutMode).apply()
                rebuildKeyboardRows()
            }
        }
        root.addView(keyboardPanel, FrameLayout.LayoutParams(-1, dp(keyboardHeightDp), Gravity.BOTTOM).apply {
            leftMargin = dp(7); rightMargin = dp(7); bottomMargin = dp(12)
        })
        keyboardPanel.visibility = View.GONE

        bottomPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(7), dp(4), dp(7), dp(7))
            background = roundedPanel(250, 15)
            elevation = dp(8).toFloat()
        }
        val panelHeader = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val touchpadMoveHandle = TextView(this).apply {
            text = "⠿ PINDAH"
            textSize = 9f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(7), 0, dp(7), 0)
            contentDescription = "Seret untuk memindahkan posisi touchpad"
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.rgb(47, 83, 108))
                cornerRadius = dp(6).toFloat()
            }
        }
        panelHeader.addView(touchpadMoveHandle, LinearLayout.LayoutParams(dp(68), dp(26)).apply {
            rightMargin = dp(5)
        })
        panelHeader.addView(TextView(this).apply {
            text = "CYBER TRACKPAD  •  1 jari gerak  /  2 jari scroll"
            textSize = 9f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
        }, LinearLayout.LayoutParams(0, dp(28), 1f))
        panelHeader.addView(actionButton("🖱 Preview") {
            setPreviewInputMode(0)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(28)))
        panelHeader.addView(actionButton("⌨") {
            keyboardPanelVisible = !keyboardPanelVisible
            keyboardPanel.visibility = if (keyboardPanelVisible) View.VISIBLE else View.GONE
            updateDesktopLayout(root.width, root.height)
        }, LinearLayout.LayoutParams(dp(38), dp(28)))
        panelHeader.addView(actionButton("⚙") {
            topBar.visibility = View.VISIBLE
            status.text = "Pengaturan resolusi dan DPI tersedia di panel Saku Desktop."
        }, LinearLayout.LayoutParams(dp(38), dp(28)))
        touchpadMoveHandle.isClickable = true
        touchpadMoveHandle.isFocusable = true
        touchpadMoveHandle.setOnTouchListener { _, event ->
            val params = bottomPanel.layoutParams as? FrameLayout.LayoutParams
                ?: return@setOnTouchListener false
            val landscapeNow = resources.configuration.orientation ==
                android.content.res.Configuration.ORIENTATION_LANDSCAPE
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchpadMoveHandle.parent?.requestDisallowInterceptTouchEvent(true)
                    // Match the keyboard's drag behavior: capture the actual view position,
                    // then apply every raw-pointer delta directly (no threshold-induced lag).
                    touchpadDragOriginX = bottomPanel.x.toInt().coerceIn(
                        0, (root.width - bottomPanel.width).coerceAtLeast(0))
                    touchpadDragOriginY = bottomPanel.y.toInt().coerceIn(
                        0, (root.height - bottomPanel.height).coerceAtLeast(0))
                    touchpadDragStartX = event.rawX
                    touchpadDragStartY = event.rawY
                    touchpadDragStarted = true
                    if (landscapeNow) touchpadLandscapeHasCustomPosition = true
                    else touchpadPortraitHasCustomPosition = true
                    params.gravity = Gravity.TOP or Gravity.START
                    params.leftMargin = touchpadDragOriginX
                    params.topMargin = touchpadDragOriginY
                    params.rightMargin = 0
                    params.bottomMargin = 0
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val maxX = (root.width - bottomPanel.width).coerceAtLeast(0)
                    val maxY = (root.height - bottomPanel.height).coerceAtLeast(0)
                    val x = (touchpadDragOriginX + event.rawX - touchpadDragStartX)
                        .toInt().coerceIn(0, maxX)
                    val y = (touchpadDragOriginY + event.rawY - touchpadDragStartY)
                        .toInt().coerceIn(0, maxY)
                    params.gravity = Gravity.TOP or Gravity.START
                    params.leftMargin = x
                    params.topMargin = y
                    params.rightMargin = 0
                    params.bottomMargin = 0
                    root.updateViewLayout(bottomPanel, params)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val maxX = (root.width - bottomPanel.width).coerceAtLeast(1)
                    val maxY = (root.height - bottomPanel.height).coerceAtLeast(1)
                    val xPercent = (params.leftMargin.toFloat() / maxX * 100).toInt().coerceIn(0, 100)
                    val yPercent = (params.topMargin.toFloat() / maxY * 100).toInt().coerceIn(0, 100)
                    if (landscapeNow) {
                        touchpadLandscapeXPercent = xPercent
                        touchpadLandscapeYPercent = yPercent
                    } else {
                        touchpadPortraitXPercent = xPercent
                        touchpadPortraitYPercent = yPercent
                    }
                    persistTouchpadSettings()
                    touchpadDragStarted = false
                    touchpadMoveHandle.parent?.requestDisallowInterceptTouchEvent(false)
                    true
                }
                else -> true
            }
        }
        bottomPanel.addView(panelHeader)
        val stage = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(10, 25, 57))
            contentDescription = "Touchpad: gerak 1 jari, tap klik, 2 jari scroll, tahan 1 detik lalu geser untuk drag"
        }
        stage.setOnTouchListener { _, event -> handleTouch(event, stage.width, stage.height) }
        bottomPanel.addView(stage, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(bottomPanel, FrameLayout.LayoutParams(-1, dp(210), Gravity.BOTTOM).apply {
            leftMargin = dp(7); rightMargin = dp(7); bottomMargin = dp(7)
        })
        root.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
            updateDesktopLayout(right - left, bottom - top)
        }

        runtimeLog = TextView(this).apply {
            textSize = 9f
            setTextColor(Color.rgb(190, 236, 249))
            setTextIsSelectable(true)
            text = "Runtime log siap. Hasil Shizuku dan error muncul di sini."
            setPadding(dp(7), dp(5), dp(7), dp(5))
            setOnLongClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Saku Simple runtime log", runtimeLogBuffer.toString()))
                appendRuntimeLog("Log disalin ke clipboard.")
                true
            }
        }
        runtimeLogScroll = ScrollView(this).apply {
            isFillViewport = true
            addView(runtimeLog, FrameLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        runtimeLogPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(4), dp(6), dp(6))
            background = roundedPanel(245, 12)
            elevation = dp(12).toFloat()
            visibility = View.GONE
        }
        val logHeader = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        logHeader.addView(TextView(this).apply {
            text = "RUNTIME LOG • tahan isi untuk salin"
            textSize = 10f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, dp(28), 1f))
        logHeader.addView(actionButton("Tutup") { toggleRuntimeLog(false) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(28)))
        runtimeLogPanel.addView(logHeader)
        runtimeLogPanel.addView(runtimeLogScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(runtimeLogPanel, FrameLayout.LayoutParams(-1, dp(185), Gravity.TOP).apply {
            leftMargin = dp(8); rightMargin = dp(8); topMargin = dp(102)
        })

        // IME bridge: field 1x1 transparan tetap berada di Activity utama (display 0).
        // IME virtual display sendiri disembunyikan oleh ShizukuUserService.
        imeBridgeEditText = object : EditText(this) {
            override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
                val base = super.onCreateInputConnection(outAttrs) ?: return null
                return object : InputConnectionWrapper(base, false) {
                    // Keep intermediate Gboard composition on the host proxy. Some IMEs
                    // finalize a word with finishComposingText rather than commitText, so
                    // retain the latest composing value and flush it exactly once.
                    private var pendingComposition = ""

                    override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
                        pendingComposition = text?.toString().orEmpty()
                        return super.setComposingText(text, newCursorPosition)
                    }

                    override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                        val committed = text?.toString().orEmpty()
                        if (committed.isNotEmpty()) sendImeBridgeText(committed)
                        pendingComposition = ""
                        return super.commitText(text, newCursorPosition)
                    }

                    override fun finishComposingText(): Boolean {
                        pendingComposition.takeIf { it.isNotEmpty() }?.let(::sendImeBridgeText)
                        pendingComposition = ""
                        return super.finishComposingText()
                    }

                    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                        if (beforeLength > 0) sendImeBridgeDelete(beforeLength)
                        if (afterLength > 0) sendImeBridgeForwardDelete(afterLength)
                        return super.deleteSurroundingText(beforeLength, afterLength)
                    }

                    override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean {
                        if (beforeLength > 0) sendImeBridgeDelete(beforeLength)
                        if (afterLength > 0) sendImeBridgeForwardDelete(afterLength)
                        return super.deleteSurroundingTextInCodePoints(beforeLength, afterLength)
                    }

                    override fun sendKeyEvent(event: KeyEvent): Boolean {
                        if (event.action == KeyEvent.ACTION_DOWN) {
                            when (event.keyCode) {
                                KeyEvent.KEYCODE_DEL -> {
                                    sendImeBridgeDelete(1)
                                }
                                KeyEvent.KEYCODE_ENTER -> sendImeBridgeText("\n")
                                else -> event.unicodeChar.takeIf { it > 0 }?.let {
                                    sendImeBridgeText(String(Character.toChars(it)))
                                }
                            }
                        }
                        return true
                    }
                }
            }
        }.apply {
            setSingleLine(false)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_NONE
            // Stay attached/focusable for a valid InputConnection while remaining invisible.
            alpha = 0.01f
            visibility = View.VISIBLE
            setTextColor(Color.TRANSPARENT)
            setHintTextColor(Color.TRANSPARENT)
            background = null
            // Host the IME proxy on the physical phone display (display 0).
            // It must remain focusable so Gboard cannot attach to the virtual display.
            isFocusable = true
            isFocusableInTouchMode = true
            setShowSoftInputOnFocus(true)
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            contentDescription = "Jembatan input Gboard ke virtual display"

        }
        // Keep the proxy attached to the host window (display 0).
        // GONE would remove its InputConnection and let Android route IME elsewhere.
        imeBridgeEditText.visibility = View.VISIBLE
        root.addView(imeBridgeEditText, FrameLayout.LayoutParams(dp(1), dp(1), Gravity.TOP or Gravity.START))
        setContentView(root)
    }

    private fun roundedPanel(alpha: Int, radius: Int) = GradientDrawable().apply {
        setOrientation(GradientDrawable.Orientation.TL_BR)
        setColors(intArrayOf(
            Color.argb(alpha.coerceIn(0, 255), 12, 27, 68),
            Color.argb(alpha.coerceIn(0, 255), 7, 13, 42)
        ))
        cornerRadius = dp(radius).toFloat()
        setStroke(dp(1), Color.argb(145, 48, 190, 255))
    }

    private fun togglePhysicalScreenDim() {
        physicalScreenDimmed = !physicalScreenDimmed
        val params = window.attributes
        params.screenBrightness = if (physicalScreenDimmed) 0.02f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = params
        if (::physicalScreenDimButton.isInitialized) {
            physicalScreenDimButton.text = if (physicalScreenDimmed) "Pulihkan layar HP" else "Redupkan layar HP"
        }
        status.text = if (physicalScreenDimmed) {
            "Layar HP diredupkan • Virtual Display tetap berjalan"
        } else {
            "Kecerahan layar HP kembali mengikuti pengaturan sistem"
        }
        appendRuntimeLog(status.text.toString())
    }

    private fun restorePhysicalScreenBrightness() {
        if (!physicalScreenDimmed) return
        physicalScreenDimmed = false
        val params = window.attributes
        params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = params
        if (::physicalScreenDimButton.isInitialized) physicalScreenDimButton.text = "Redupkan layar HP"
    }

    private fun applyImmersiveMode() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersiveMode()
    }

    private fun showVirtualDisplayRecentTasks() {
        // Recent is a toggle: tapping the nav button while the overview is visible
        // dismisses it instead of rebuilding the same presentation.
        recentAppsPresentation?.let { current ->
            current.dismiss()
            if (recentAppsPresentation === current) recentAppsPresentation = null
            return
        }
        val displayId = virtualDisplayController?.displayId ?: -1
        if (displayId < 0) {
            status.text = "Mulai virtual display dahulu."
            return
        }
        status.text = "Memuat Recent display $displayId…"
        keyboardInputExecutor.execute {
            val result = shizukuBridge.getOpenTasks(displayId)
            runOnUiThread {
                appendRuntimeLog("Recent display $displayId\\n$result")
                renderRecentAppsGrid(displayId, result)
                status.text = "Recent display $displayId diperbarui."
            }
        }
    }

    /** Forkdex-style Open Tasks overview hosted only on the selected virtual display. */
    private fun renderRecentAppsGrid(displayId: Int, result: String) {
        val display = (getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).getDisplay(displayId)
        if (display == null) { status.text = "Virtual display $displayId tidak ditemukan."; return }
        recentAppsPresentation?.dismiss()
        val presentation = Presentation(this, display)
        recentAppsPresentation = presentation
        val ctx = presentation.context
        val density = ctx.resources.displayMetrics.density
        fun d(value: Int) = (value * density).toInt().coerceAtLeast(1)

        data class OpenTask(val id: Int, val component: String, val packageName: String)
        val tasks = Regex("""taskId=(\d+)\s*•\s*([^\s]+)""")
            .findAll(result)
            .mapNotNull { match ->
                val id = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
                val component = match.groupValues[2]
                val packageName = component.substringBefore('/')
                if (packageName.isBlank()) return@mapNotNull null
                OpenTask(id, component, packageName)
            }.distinctBy { it.id }.toList()

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(d(14), d(14), d(14), d(12))
            setBackgroundColor(Color.rgb(25, 29, 36))
            isFocusable = true
        }
        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(ctx).apply {
            text = "Open tasks (${tasks.size})"
            textSize = 18f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, d(48), 1f))
        header.addView(Button(ctx).apply {
            text = "Refresh"
            isAllCaps = false
            setOnClickListener { showVirtualDisplayRecentTasks() }
        }, LinearLayout.LayoutParams(d(92), d(40)))
        header.addView(Button(ctx).apply {
            text = "Tutup"
            isAllCaps = false
            setOnClickListener {
                presentation.dismiss()
                if (recentAppsPresentation === presentation) recentAppsPresentation = null
            }
        }, LinearLayout.LayoutParams(d(76), d(40)))
        root.addView(header)

        if (tasks.isEmpty()) {
            root.addView(TextView(ctx).apply {
                text = if (result.isBlank()) "Tidak ada aplikasi terbuka pada display ini."
                    else result
                textSize = 14f
                setTextColor(Color.LTGRAY)
                gravity = Gravity.CENTER
                setPadding(d(20), d(28), d(20), d(28))
            }, LinearLayout.LayoutParams(-1, 0, 1f))
        } else {
            val scroll = ScrollView(ctx).apply { isFillViewport = true }
            val grid = android.widget.GridLayout(ctx).apply {
                columnCount = if (ctx.resources.configuration.screenWidthDp >= 900) 4 else 3
                useDefaultMargins = false
                alignmentMode = android.widget.GridLayout.ALIGN_BOUNDS
            }
            tasks.forEach { task ->
                val appInfo = try {
                    packageManager.getApplicationInfo(task.packageName, 0)
                } catch (_: Throwable) { null }
                val label = try {
                    appInfo?.let { packageManager.getApplicationLabel(it).toString() }
                } catch (_: Throwable) { null } ?: task.packageName
                val tile = FrameLayout(ctx).apply {
                    background = GradientDrawable().apply {
                        cornerRadius = d(6).toFloat()
                        setColor(Color.rgb(36, 42, 51))
                        setStroke(d(1), Color.rgb(66, 82, 101))
                    }
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        presentation.dismiss()
                        if (recentAppsPresentation === presentation) recentAppsPresentation = null
                        keyboardInputExecutor.execute {
                            val message = shizukuBridge.focusOpenTask(displayId, task.id)
                            runOnUiThread {
                                status.text = message
                                appendRuntimeLog(message)
                                // Do not reopen the overview after selecting an app.
                                // The selected task should take over the display directly.
                            }
                        }
                    }
                }
                val content = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(d(8), d(8), d(8), d(6))
                }
                content.addView(android.widget.ImageView(ctx).apply {
                    setImageDrawable(
                        try { appInfo?.let { packageManager.getApplicationIcon(it) } }
                        catch (_: Throwable) { null } ?: getDrawable(android.R.drawable.sym_def_app_icon)
                    )
                    scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                }, LinearLayout.LayoutParams(d(42), d(42)))
                content.addView(TextView(ctx).apply {
                    text = label
                    textSize = 12f
                    setTextColor(Color.WHITE)
                    typeface = Typeface.DEFAULT_BOLD
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    gravity = Gravity.CENTER
                }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(5) })
                content.addView(TextView(ctx).apply {
                    text = "Task #${task.id}"
                    textSize = 10f
                    setTextColor(Color.LTGRAY)
                    gravity = Gravity.CENTER
                })
                tile.addView(content, FrameLayout.LayoutParams(-1, -1))
                val close = TextView(ctx).apply {
                    text = "×"
                    textSize = 21f
                    gravity = Gravity.CENTER
                    setTextColor(Color.LTGRAY)
                    contentDescription = "Tutup $label"
                    setPadding(d(4), 0, d(4), 0)
                    setOnClickListener {
                        keyboardInputExecutor.execute {
                            val message = shizukuBridge.closeOpenTask(displayId, task.id)
                            runOnUiThread {
                                status.text = message
                                appendRuntimeLog(message)
                                showVirtualDisplayRecentTasks()
                            }
                        }
                    }
                }
                tile.addView(close, FrameLayout.LayoutParams(d(32), d(32), Gravity.TOP or Gravity.END))
                val params = android.widget.GridLayout.LayoutParams().apply {
                    width = 0
                    height = d(112)
                    columnSpec = android.widget.GridLayout.spec(
                        android.widget.GridLayout.UNDEFINED, 1f
                    )
                    setMargins(d(4), d(4), d(4), d(4))
                }
                grid.addView(tile, params)
            }
            scroll.addView(grid, ViewGroup.LayoutParams(-1, -2))
            root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f).apply {
                topMargin = d(12)
            })
        }

        presentation.setContentView(root)
        presentation.setOnDismissListener {
            if (recentAppsPresentation === presentation) recentAppsPresentation = null
        }
        presentation.show()
        presentation.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        )
    }

    private fun sendVirtualNavigationKey(keyCode: Int) {
        val displayId = virtualDisplayController?.displayId ?: -1
        if (displayId < 0) {
            status.text = "Mulai virtual display dahulu."
            return
        }

        // When the built-in launcher is enabled, Home means return to its app
        // grid rather than sending the system HOME key (which may be ignored by
        // secondary-display policy on some Android builds).
        if (keyCode == android.view.KeyEvent.KEYCODE_HOME &&
            getSharedPreferences("saku_desktop_launcher", Context.MODE_PRIVATE)
                .getBoolean("enabled", true)) {
            showDexLauncherOnVirtualDisplay()
            return
        }
        // Back and system Home (when the built-in launcher is disabled) use
        // InputManager injection with the virtual display ID.
        sendVirtualKey(keyCode)
    }

    private fun sendVirtualKey(keyCode: Int) {
        val displayId = virtualDisplayController?.displayId ?: -1
        if (displayId < 0) {
            status.text = "Virtual display belum aktif."
            return
        }
        keyboardInputExecutor.execute {
            val accepted = virtualDisplayController?.injectKeyEvents(
                intArrayOf(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.ACTION_UP),
                intArrayOf(keyCode, keyCode),
                intArrayOf(0, 0)
            ) ?: false
            runOnUiThread {
                if (!accepted) {
                    status.text = "Tombol tidak diterima display virtual."
                    appendRuntimeLog("KeyEvent keyCode=$keyCode gagal: ${shizukuBridge.getLastError()}")
                } else {
                    status.text = "Tombol dikirim ke display virtual • keyCode=$keyCode"
                }
            }
        }
    }

    private fun toggleDirectTouch() {
        setPreviewInputMode((previewInputMode + 1) % 3)
    }

    private fun setPreviewInputMode(mode: Int) {
        previewInputMode = mode.coerceIn(0, 2)
        directTouchButton.text = when (previewInputMode) {
            0 -> "Trackpad"
            1 -> "Direct"
            else -> "Touch: OFF"
        }
        preview?.isClickable = previewInputMode != 2
        when (previewInputMode) {
            0 -> {
                bottomPanel.visibility = View.GONE
                panelToggleButton.text = "Panel+"
                status.text = "Trackpad preview aktif • 1 jari kursor/tap, 2 jari scroll, tahan lalu geser untuk drag."
            }
            1 -> {
                bottomPanel.visibility = View.GONE
                panelToggleButton.text = "Panel+"
                status.text = "Direct Touch aktif • sentuhan diteruskan langsung ke desktop virtual."
            }
            else -> {
                status.text = "Input preview nonaktif • sentuhan pada preview diabaikan."
            }
        }
        updateDesktopLayout((preview?.parent as? View)?.width ?: 0, (preview?.parent as? View)?.height ?: 0)
    }

    private fun handleDirectTouch(event: MotionEvent, width: Int, height: Int): Boolean {
        if (width <= 0 || height <= 0) return true
        if (event.pointerCount > 5) {
            status.text = "Direct Touch mendukung maksimal 5 jari."
            return true
        }
        val count = event.pointerCount
        val xs = FloatArray(count) { i ->
            (event.getX(i) / width.toFloat() * targetWidth).coerceIn(0f, (targetWidth - 1).toFloat())
        }
        val ys = FloatArray(count) { i ->
            (event.getY(i) / height.toFloat() * targetHeight).coerceIn(0f, (targetHeight - 1).toFloat())
        }
        cursorX = xs[0]
        cursorY = ys[0]
        cursorOverlay?.invalidate()
        val action = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> MotionEvent.ACTION_DOWN
            MotionEvent.ACTION_POINTER_DOWN -> MotionEvent.ACTION_POINTER_DOWN or
                (event.actionIndex shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
            MotionEvent.ACTION_MOVE -> MotionEvent.ACTION_MOVE
            MotionEvent.ACTION_POINTER_UP -> MotionEvent.ACTION_POINTER_UP or
                (event.actionIndex shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
            MotionEvent.ACTION_UP -> MotionEvent.ACTION_UP
            MotionEvent.ACTION_CANCEL -> MotionEvent.ACTION_CANCEL
            else -> return true
        }
        val controller = virtualDisplayController
        if (controller == null || controller.displayId < 0) {
            if (action == MotionEvent.ACTION_DOWN) status.text = "Mulai virtual display melalui Shizuku dahulu."
            return true
        }
        if (event.actionMasked == MotionEvent.ACTION_DOWN) directTouchDown = true
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) directTouchDown = false
        val pointerIds = IntArray(count) { i -> event.getPointerId(i) }
        enqueueDirectTouchFrame(
            DirectTouchFrame(
                controller = controller,
                action = action,
                pointerIds = pointerIds,
                xs = xs,
                ys = ys,
                finalUp = event.actionMasked == MotionEvent.ACTION_UP
            )
        )
        return true
    }

    /**
     * Keep Direct Touch transitions ordered, but collapse consecutive stale MOVE samples.
     * This prevents a slow Binder injection from building an unbounded queue of obsolete
     * coordinates while never dropping DOWN, POINTER_DOWN, POINTER_UP, UP or CANCEL.
     */
    private fun enqueueDirectTouchFrame(frame: DirectTouchFrame) {
        var scheduleDrain = false
        synchronized(directTouchQueueLock) {
            if ((frame.action and MotionEvent.ACTION_MASK) == MotionEvent.ACTION_MOVE &&
                directTouchQueue.peekLast()?.let {
                    (it.action and MotionEvent.ACTION_MASK) == MotionEvent.ACTION_MOVE
                } == true
            ) {
                directTouchQueue.removeLast()
            }
            directTouchQueue.addLast(frame)
            if (!directTouchDrainScheduled) {
                directTouchDrainScheduled = true
                scheduleDrain = true
            }
        }
        if (!scheduleDrain) return
        mouseInputExecutor.execute {
            while (true) {
                val next = synchronized(directTouchQueueLock) {
                    if (directTouchQueue.isEmpty()) {
                        directTouchDrainScheduled = false
                        null
                    } else directTouchQueue.removeFirst()
                } ?: break
                val accepted = next.controller.injectTouchPoints(
                    next.action, next.pointerIds, next.xs, next.ys
                )
                if (!accepted) {
                    runOnUiThread {
                        appendRuntimeLog(
                            "Direct Touch multi-jari gagal • action=${next.action} • ${shizukuBridge.getLastError()}"
                        )
                        status.text = "Direct Touch gagal • periksa Log."
                    }
                } else if (next.finalUp) {
                    runOnUiThread { pulseDextopCursor() }
                }
            }
        }
    }

    private fun toggleInputPanel() {
        // Legacy trackpad panel is permanently disabled; use on-screen Direct Touch.
        trackpadPanelEnabled = false
        if (::bottomPanel.isInitialized) bottomPanel.visibility = View.GONE
    }


    private fun showVirtualKeyboard() {
        keyboardPanelVisible = true
        keyboardPanel.visibility = View.VISIBLE
        updateDesktopLayout((preview?.parent as? View)?.width ?: 0, (preview?.parent as? View)?.height ?: 0)
        status.text = "Keyboard Cyberdeck aktif • terpisah dari preview dan trackpad."
    }

    private fun toggleSystemKeyboardBridge() {
        if (systemKeyboardRequested) {
            systemKeyboardRequested = false
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(imeBridgeEditText.windowToken, 0)
            imeBridgeEditText.clearFocus()
            status.text = "Gboard disembunyikan • fokus virtual display tetap tersedia."
        } else {
            showSystemKeyboardBridge()
        }
    }

    private fun showSystemKeyboardBridge() {
        val targetDisplayId = virtualDisplayController?.displayId ?: -1
        if (targetDisplayId < 0) {
            status.text = "Mulai virtual display dahulu."
            return
        }
        // Gboard must be hosted by this Activity on the physical built-in display 0.
        val hostDisplayId = if (android.os.Build.VERSION.SDK_INT >= 30) display?.displayId ?: -1 else 0
        if (hostDisplayId != 0) {
            appendRuntimeLog("Gboard dibatalkan: Activity berada di display $hostDisplayId, bukan built-in display 0.")
            status.text = "Buka kontrol VDT dari layar HP utama (Display 0) untuk memakai Gboard."
            return
        }
        if (::keyboardPanel.isInitialized && keyboardPanelVisible) {
            keyboardPanel.visibility = View.VISIBLE
        }
        val root = preview?.parent as? View
        if (root != null) updateDesktopLayout(root.width, root.height)

        systemKeyboardRequested = true
        imeBridgeEditText.visibility = View.VISIBLE
        imeBridgeEditText.isFocusable = true
        imeBridgeEditText.isFocusableInTouchMode = true
        imeBridgeEditText.requestFocus()

        // Some OEM builds ignore showSoftInput until the host window is attached and focused.
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val handler = Handler(Looper.getMainLooper())
        var attempts = 0
        val showTask = object : Runnable {
            override fun run() {
                if (!systemKeyboardRequested || isFinishing || isDestroyed) return
                attempts++
                imeBridgeEditText.requestFocus()
                val windowReady = window.decorView.isAttachedToWindow &&
                    window.decorView.hasWindowFocus() &&
                    imeBridgeEditText.windowToken != null
                val shown = if (windowReady) {
                    imm.showSoftInput(imeBridgeEditText, InputMethodManager.SHOW_IMPLICIT)
                } else false
                appendRuntimeLog(
                    "Gboard host=0 target=$targetDisplayId attempt=$attempts " +
                        "windowReady=$windowReady requested=$shown active=${imm.isActive(imeBridgeEditText)}"
                )
                if (shown || imm.isActive(imeBridgeEditText)) {
                    status.text = "Gboard diminta di layar HP (Display 0)."
                } else if (attempts < 6) {
                    handler.postDelayed(this, 180L)
                } else {
                    status.text = "Gboard gagal dibuka • pastikan Activity VDT berada di layar HP utama."
                }
            }
        }
        imeBridgeEditText.post(showTask)
    }

    private fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"

    private fun sendImeBridgeText(text: String) {
        val displayId = virtualDisplayController?.displayId ?: -1
        if (displayId < 0 || text.isEmpty()) return
        keyboardInputExecutor.execute {
            // The shell text command handles ordinary Latin text. It cannot reliably
            // represent every committed Unicode character, so use clipboard paste for
            // non-ASCII input and as a fallback if the display-targeted command fails.
            val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
            val requiresClipboard = normalized.any { it.code !in 0x20..0x7E }
            var accepted = !requiresClipboard
            if (!requiresClipboard) {
                val chunks = normalized.split('\n')
                accepted = true
                chunks.forEachIndexed { index, chunk ->
                    if (chunk.isNotEmpty()) {
                        val encoded = chunk.replace("%", "%25").replace(" ", "%s")
                        val sent = shizukuBridge.runShellCommand(
                            "input -d $displayId text ${shellQuote(encoded)}"
                        )
                        if (!sent) accepted = false
                    }
                    if (index < chunks.lastIndex) {
                        if (!shizukuBridge.runShellCommand("input -d $displayId keyevent 66")) {
                            accepted = false
                        }
                    }
                }
            }

            if (!accepted || requiresClipboard) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                accepted = runCatching {
                    clipboard.setPrimaryClip(ClipData.newPlainText("Saku keyboard", text))
                    shizukuBridge.runShellCommand("input -d $displayId keyevent 279")
                }.getOrDefault(false)
            }
            if (!accepted) runOnUiThread {
                appendRuntimeLog("Teks Gboard gagal dikirim ke display $displayId: ${shizukuBridge.getLastError()}")
                status.text = "Input Gboard gagal • periksa fokus aplikasi dan izin Shizuku."
            }
        }
    }

    private fun sendImeBridgeForwardDelete(count: Int) {
        val displayId = virtualDisplayController?.displayId ?: -1
        if (displayId < 0 || count <= 0) return
        keyboardInputExecutor.execute {
            val amount = count.coerceIn(1, 64)
            val actions = IntArray(amount * 2) { if (it % 2 == 0) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP }
            val codes = IntArray(amount * 2) { KeyEvent.KEYCODE_FORWARD_DEL }
            val metas = IntArray(amount * 2)
            val accepted = virtualDisplayController?.injectKeyEvents(actions, codes, metas) ?: false
            if (!accepted) runOnUiThread {
                appendRuntimeLog("Forward-delete gagal: ${shizukuBridge.getLastError()}")
                status.text = "Penghapusan karakter setelah kursor gagal."
            }
        }
    }

    private fun sendImeBridgeDelete(count: Int) {
        val displayId = virtualDisplayController?.displayId ?: -1
        if (displayId < 0 || count <= 0) return
        keyboardInputExecutor.execute {
            val amount = count.coerceIn(1, 64)
            val actions = IntArray(amount * 2) { if (it % 2 == 0) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP }
            val codes = IntArray(amount * 2) { KeyEvent.KEYCODE_DEL }
            val metas = IntArray(amount * 2)
            val accepted = virtualDisplayController?.injectKeyEvents(actions, codes, metas) ?: false
            if (!accepted) runOnUiThread {
                appendRuntimeLog("Backspace gagal: ${shizukuBridge.getLastError()}")
                status.text = "Backspace gagal dikirim ke virtual display."
            }
        }
    }

    private fun cyberKey(label: String, utility: Boolean, repeatable: Boolean = false, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            textSize = when (label) {
                "SPACE" -> 11f
                "?123", "SHIFT", "ENTER", "⌫" -> 12f
                else -> 14f
            }
            isAllCaps = false
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(if (utility) Color.rgb(242, 245, 250) else Color.rgb(31, 36, 44))
            background = GradientDrawable().apply {
                cornerRadius = dp(9).toFloat()
                setColor(if (utility) Color.rgb(82, 96, 116) else Color.rgb(225, 230, 237))
                setStroke(dp(1), if (utility) Color.rgb(105, 119, 139) else Color.rgb(208, 215, 225))
            }
            minHeight = 0; minimumHeight = 0; minWidth = 0; minimumWidth = 0
            setPadding(dp(2), 0, dp(2), 0)
            stateListAnimator = null
            val normalColor = if (utility) Color.rgb(82, 96, 116) else Color.rgb(225, 230, 237)
            val pressedColor = Color.rgb(91, 178, 235)
            val normalTextColor = if (utility) Color.rgb(242, 245, 250) else Color.rgb(31, 36, 44)
            val pressedTextColor = Color.WHITE
            var repeatTask: Runnable? = null
            var repeating = false
            setOnTouchListener { keyView, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        keyView.scaleX = 1.08f
                        keyView.scaleY = 1.08f
                        (keyView.background as? GradientDrawable)?.setColor(pressedColor)
                        (keyView as? TextView)?.setTextColor(pressedTextColor)
                        if (repeatable) {
                            repeating = true
                            action()
                            val task = object : Runnable {
                                override fun run() {
                                    if (repeating) {
                                        action()
                                        keyboardRepeatHandler.postDelayed(this, 55L)
                                    }
                                }
                            }
                            repeatTask = task
                            keyboardRepeatHandler.postDelayed(task, 420L)
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        repeating = false
                        repeatTask?.let(keyboardRepeatHandler::removeCallbacks)
                        repeatTask = null
                        keyView.scaleX = 1f
                        keyView.scaleY = 1f
                        (keyView.background as? GradientDrawable)?.setColor(normalColor)
                        (keyView as? TextView)?.setTextColor(normalTextColor)
                        if (!repeatable && event.actionMasked == MotionEvent.ACTION_UP) action()
                        true
                    }
                    else -> true
                }
            }
        }

    private fun symbolForCyberdeckKey(label: String): String = when (label.uppercase()) {
        "Q" -> "@"; "W" -> "#"; "E" -> "\$"; "R" -> "%"; "T" -> "&"
        "Y" -> "*"; "U" -> "("; "I" -> ")"; "O" -> "-"; "P" -> "+"
        "A" -> "="; "S" -> "/"; "D" -> "?"; "F" -> "!"; "G" -> ":"
        "H" -> ";"; "J" -> "["; "K" -> "]"; "L" -> "{"
        "Z" -> "}"; "X" -> "_"; "C" -> "\\"; "V" -> "|"
        "B" -> "~"; "N" -> "<"; "M" -> ">"
        "1" -> "!"; "2" -> "@"; "3" -> "#"; "4" -> "\$"; "5" -> "%"
        "6" -> "&"; "7" -> "*"; "8" -> "("; "9" -> ")"; "0" -> "="
        else -> label
    }

    private fun refreshCyberdeckLabels() {
        if (!::keyboardPanel.isInitialized) return
        for (i in 0 until keyboardPanel.childCount) {
            val row = keyboardPanel.getChildAt(i) as? LinearLayout ?: continue
            for (j in 0 until row.childCount) {
                val button = row.getChildAt(j) as? Button ?: continue
                val original = button.tag as? String ?: continue
                if (original == "?123") {
                    button.text = if (cyberSymbols) "ABC" else "?123"
                } else if (original.length == 1 && original[0].isLetterOrDigit()) {
                    button.text = if (cyberSymbols) symbolForCyberdeckKey(original) else original
                }
            }
        }
        updateModifierLabels()
    }

    private fun updateModifierLabels() {
        if (!::keyboardPanel.isInitialized) return
        for (i in 0 until keyboardPanel.childCount) {
            val row = keyboardPanel.getChildAt(i) as? LinearLayout ?: continue
            for (j in 0 until row.childCount) {
                val button = row.getChildAt(j) as? Button ?: continue
                when (button.tag as? String) {
                    "SHIFT" -> button.text = if (cyberShift) "SHIFT •" else "SHIFT"
                    "CTRL" -> button.text = if (cyberCtrl) "CTRL •" else "CTRL"
                    "ALT" -> button.text = if (cyberAlt) "ALT •" else "ALT"
                }
            }
        }
    }

    private fun sendKeyboardEditCommand(label: String) {
        val keyCode = when (label) {
            "Cut" -> android.view.KeyEvent.KEYCODE_CUT
            "Copy" -> android.view.KeyEvent.KEYCODE_COPY
            "Paste" -> android.view.KeyEvent.KEYCODE_PASTE
            "Select all" -> android.view.KeyEvent.KEYCODE_A
            else -> return
        }
        val displayId = virtualDisplayController?.displayId ?: -1
        if (displayId < 0) {
            status.text = "Mulai virtual display dahulu."
            return
        }
        val meta = if (label == "Select all") android.view.KeyEvent.META_CTRL_ON else 0
        keyboardInputExecutor.execute {
            val accepted = virtualDisplayController?.injectKeyEvents(
                intArrayOf(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.ACTION_UP),
                intArrayOf(keyCode, keyCode),
                intArrayOf(meta, meta)
            ) ?: false
            runOnUiThread {
                if (accepted) {
                    status.text = "$label dikirim ke aplikasi pada display $displayId."
                    appendRuntimeLog("Keyboard edit: $label • display=$displayId")
                } else {
                    status.text = "$label gagal dikirim."
                    appendRuntimeLog("Keyboard edit gagal ($label): ${shizukuBridge.getLastError()}")
                }
            }
        }
    }

    private fun sendKeyboardKey(keyCode: Int) {
        val meta = (if (cyberShift) android.view.KeyEvent.META_SHIFT_ON else 0) or
            (if (cyberCtrl) android.view.KeyEvent.META_CTRL_ON else 0) or
            (if (cyberAlt) android.view.KeyEvent.META_ALT_ON else 0)
        if ((virtualDisplayController?.displayId ?: -1) < 0) {
            status.text = "Mulai virtual display dahulu."
            return
        }
        keyboardInputExecutor.execute {
            val accepted = virtualDisplayController?.injectKeyEvents(
                intArrayOf(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.ACTION_UP),
                intArrayOf(keyCode, keyCode),
                intArrayOf(meta, meta)
            ) ?: false
            if (!accepted) runOnUiThread {
                status.text = "Tombol keyboard gagal dikirim."
                appendRuntimeLog("Cyberdeck keyCode=$keyCode gagal: " + shizukuBridge.getLastError())
            }
        }
    }

    private fun sendTypedText(text: String) {
        // Build KeyEvents and call Shizuku Binder off the UI thread.
        val shift = cyberShift
        val ctrl = cyberCtrl
        val alt = cyberAlt
        keyboardInputExecutor.execute {
            val events = runCatching {
                KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD).getEvents(text.toCharArray())
            }.getOrNull()
            if (events.isNullOrEmpty()) {
                runOnUiThread { appendRuntimeLog("Keyboard: karakter tidak didukung KeyCharacterMap: ${text.take(16)}") }
                return@execute
            }
            val actions = IntArray(events.size) { events[it].action }
            val codes = IntArray(events.size) { events[it].keyCode }
            val modifierMeta = (if (shift) android.view.KeyEvent.META_SHIFT_ON else 0) or
                (if (ctrl) android.view.KeyEvent.META_CTRL_ON else 0) or
                (if (alt) android.view.KeyEvent.META_ALT_ON else 0)
            val meta = IntArray(events.size) { events[it].metaState or modifierMeta }
            val accepted = virtualDisplayController?.injectKeyEvents(actions, codes, meta) ?: false
            if (!accepted) runOnUiThread {
                status.text = "Teks keyboard gagal dikirim. Periksa Log."
                appendRuntimeLog("Injeksi key event gagal: ${shizukuBridge.getLastError()}")
            }
        }
    }

    private fun toggleRuntimeLog(forceShow: Boolean? = null) {
        val show = forceShow ?: (runtimeLogPanel.visibility != View.VISIBLE)
        runtimeLogPanel.visibility = if (show) View.VISIBLE else View.GONE
        runtimeLogToggle.text = if (show) "Log−" else "Log"
    }

    private fun queueNativeMouseButton(action: Int, x: Float, y: Float) {
        mouseInputExecutor.execute {
            val accepted = virtualDisplayController?.injectMouseButton(action, x, y) ?: false
            if (!accepted && action != MotionEvent.ACTION_MOVE) runOnUiThread {
                appendRuntimeLog("Mouse button gagal: ${shizukuBridge.getLastError()}")
            }
        }
    }

    private fun queueNativeMouseClick(x: Float, y: Float) {
        mouseInputExecutor.execute {
            val accepted = virtualDisplayController?.injectTouchClick(x, y) ?: false
            runOnUiThread {
                if (accepted) {
                    pulseDextopCursor()
                    status.text = "Tap layar • x=${x.toInt()} y=${y.toInt()}"
                } else {
                    status.text = "Klik ditolak InputManager. Periksa Log."
                    appendRuntimeLog("Klik gagal: ${shizukuBridge.getLastError()}")
                }
            }
        }
    }

    override fun onStop() {
        restorePhysicalScreenBrightness()
        super.onStop()
    }

    override fun onDestroy() {
        longPressTask?.let(gestureHandler::removeCallbacks)
        if (dragActive) virtualDisplayController?.injectMouseButton(MotionEvent.ACTION_UP, cursorX, cursorY)
        mouseInputExecutor.shutdownNow()
        keyboardInputExecutor.shutdownNow()
        runCatching { recentAppsPresentation?.dismiss() }
        recentAppsPresentation = null
        virtualDisplayController?.close()
        virtualDisplayController = null
        clearPreview()
        if (::shizukuBridge.isInitialized) shizukuBridge.close()
        super.onDestroy()
    }

    private fun startOwnedVirtualDisplay() {
        val surface = preview?.holder?.surface
        if (surface == null || !surface.isValid) {
            status.text = "Surface preview belum siap. Tunggu sebentar lalu coba lagi."
            return
        }
        if (virtualDisplayController == null) {
            virtualDisplayController = VirtualDisplayController(
                this,
                shizukuBridge,
                onStatus = { message ->
                    status.text = message
                    appendRuntimeLog(message)
                }
            )
        }
        status.text = "Membuat display langsung ke SurfaceView • ${targetWidth}×${targetHeight} • densityDpi=${targetDpi}…"
        appendRuntimeLog("Output langsung ke SurfaceView • ${targetWidth}×${targetHeight} • ${targetDpi}dpi; ImageReader/Bitmap copy tidak digunakan.")
        preview?.holder?.setFixedSize(targetWidth, targetHeight)
        virtualDisplayController?.start(
            width = targetWidth,
            height = targetHeight,
            densityDpi = targetDpi,
            outputSurface = surface
        )
        Handler(Looper.getMainLooper()).postDelayed({
            if (getSharedPreferences("saku_desktop_launcher", Context.MODE_PRIVATE)
                    .getBoolean("enabled", true)) {
                showDexLauncherOnVirtualDisplay()
            } else {
                appendRuntimeLog("Auto-launch dilewati: launcher bawaan VDT dinonaktifkan.")
            }
        }, 1800L)
    }

    private fun showDexLauncherOnVirtualDisplay() {
        if (!getSharedPreferences("saku_desktop_launcher", Context.MODE_PRIVATE)
                .getBoolean("enabled", true)) {
            status.text = "Launcher bawaan VDT sedang nonaktif."
            return
        }
        val id = virtualDisplayController?.displayId ?: -1
        if (id < 0) {
            status.text = "Mulai virtual display dahulu sebelum membuka launcher."
            return
        }
        val component = "$packageName/com.farmerbb.taskbar.virtualdisplay.DexLauncherActivity"
        status.text = "Membuka Saku Desktop pada virtual display $id…"
        keyboardInputExecutor.execute {
            val result = shizukuBridge.launchAppOnDisplay(id, component)
            runOnUiThread {
                status.text = result
                appendRuntimeLog("Launcher Saku Desktop • $result")
            }
        }
    }

    private fun appendRuntimeLog(message: String) {
        if (!::runtimeLog.isInitialized) return
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
            .format(java.util.Date())
        runtimeLogBuffer.append("[").append(timestamp).append("] ")
            .append(message).append('\n')
        if (runtimeLogBuffer.length > 12000) {
            runtimeLogBuffer.delete(0, runtimeLogBuffer.length - 9000)
        }
        runtimeLog.text = runtimeLogBuffer.toString()
        if (::runtimeLogScroll.isInitialized) {
            runtimeLogScroll.post { runtimeLogScroll.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun clearPreview() {
        val holder = preview?.holder ?: return
        if (!holder.surface.isValid) return
        var canvas: Canvas? = null
        try {
            canvas = holder.lockCanvas()
            canvas?.drawColor(Color.rgb(7, 24, 45))
        } catch (error: Throwable) {
            Log.w("SakuPreview", "Gagal membersihkan preview", error)
        } finally {
            if (canvas != null) runCatching { holder.unlockCanvasAndPost(canvas) }
        }
        cursorOverlay?.invalidate()
    }

    /** Draw a classic mouse arrow while preserving the existing overlay position and movement. */
    private fun drawDextopCursor(canvas: Canvas) {
        val surface = preview ?: return
        if (surface.width <= 0 || surface.height <= 0) return
        val pointerX = surface.left + (cursorX / targetWidth) * surface.width
        val pointerY = surface.top + (cursorY / targetHeight) * surface.height
        val scale = resources.displayMetrics.density
        val arrow = Path().apply {
            // The top-left tip is the cursor hotspot, matching a desktop mouse pointer.
            moveTo(pointerX, pointerY)
            lineTo(pointerX, pointerY + 25f * scale)
            lineTo(pointerX + 6.5f * scale, pointerY + 19f * scale)
            lineTo(pointerX + 12f * scale, pointerY + 31f * scale)
            lineTo(pointerX + 17f * scale, pointerY + 28.5f * scale)
            lineTo(pointerX + 11.5f * scale, pointerY + 17f * scale)
            lineTo(pointerX + 21f * scale, pointerY + 16f * scale)
            close()
        }
        if (cursorPulseRadius > 13f) {
            canvas.drawCircle(pointerX + 8f * scale, pointerY + 15f * scale,
                cursorPulseRadius * scale, cursorStrokePaint)
        }
        canvas.drawPath(arrow, cursorPaint)
        canvas.drawPath(arrow, cursorStrokePaint)
    }

    private fun pulseDextopCursor() {
        cursorPulseRadius = 18f
        cursorOverlay?.invalidate()
        cursorOverlay?.postDelayed({
            cursorPulseRadius = 13f
            cursorOverlay?.invalidate()
        }, 100L)
    }

    /** Dispatch the newest pointer coordinate through the Shizuku InputManager path. */
    private fun queueNativeMouseMove(x: Float, y: Float) {
        var scheduleDrain = false
        synchronized(mouseMoveLock) {
            pendingMouseX = x
            pendingMouseY = y
            if (!mouseMoveDrainScheduled) {
                mouseMoveDrainScheduled = true
                scheduleDrain = true
            }
        }
        if (!scheduleDrain) return
        mouseInputExecutor.execute {
            while (true) {
                val point = synchronized(mouseMoveLock) {
                    if (pendingMouseX.isNaN() || pendingMouseY.isNaN()) {
                        mouseMoveDrainScheduled = false
                        null
                    } else {
                        val latest = pendingMouseX to pendingMouseY
                        pendingMouseX = Float.NaN
                        pendingMouseY = Float.NaN
                        latest
                    }
                } ?: break
                virtualDisplayController?.injectMouseMove(point.first, point.second)
            }
        }
    }

    /** Accumulate fractional two-finger scroll deltas so input stays smooth without a backlog. */
    private fun queueNativeMouseScroll(x: Float, y: Float, horizontal: Float, vertical: Float) {
        if (kotlin.math.abs(horizontal) < 0.0005f && kotlin.math.abs(vertical) < 0.0005f) return
        var scheduleDrain = false
        synchronized(scrollLock) {
            pendingScrollX = x
            pendingScrollY = y
            pendingScrollHorizontal += horizontal
            pendingScrollVertical += vertical
            if (!scrollDrainScheduled) {
                scrollDrainScheduled = true
                scheduleDrain = true
            }
        }
        if (!scheduleDrain) return
        mouseInputExecutor.execute {
            while (true) {
                val batch = synchronized(scrollLock) {
                    if (kotlin.math.abs(pendingScrollHorizontal) < 0.0005f &&
                        kotlin.math.abs(pendingScrollVertical) < 0.0005f
                    ) {
                        scrollDrainScheduled = false
                        null
                    } else {
                        val values = floatArrayOf(
                            pendingScrollX, pendingScrollY,
                            pendingScrollHorizontal, pendingScrollVertical
                        )
                        pendingScrollHorizontal = 0f
                        pendingScrollVertical = 0f
                        values
                    }
                } ?: break
                virtualDisplayController?.injectMouseScroll(batch[0], batch[1], batch[2], batch[3])
            }
        }
    }

    private fun handleTouch(event: MotionEvent, width: Int, height: Int): Boolean {
        if (width <= 0 || height <= 0) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchpadFingerDown = true
                lastTouchX = event.x
                lastTouchY = event.y
                downX = event.x
                downY = event.y
                pointerMoved = false
                hadMultiTouch = false
                scrollGestureActive = false
                longPressTask?.let(gestureHandler::removeCallbacks)
                longPressTask = Runnable {
                    if (touchpadFingerDown && !hadMultiTouch && !pointerMoved &&
                        (virtualDisplayController?.displayId ?: -1) >= 0
                    ) {
                        dragActive = true
                        queueNativeMouseButton(MotionEvent.ACTION_DOWN, cursorX, cursorY)
                        status.text = "Drag aktif • geser jari lalu lepaskan."
                    }
                }.also { gestureHandler.postDelayed(it, 500L) }
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                longPressTask?.let(gestureHandler::removeCallbacks)
                if (dragActive) {
                    queueNativeMouseButton(MotionEvent.ACTION_UP, cursorX, cursorY)
                    dragActive = false
                }
                if (event.pointerCount >= 2) {
                    hadMultiTouch = true
                    scrollGestureActive = true
                    val centroid = touchCentroid(event)
                    lastScrollCentroidX = centroid.first
                    lastScrollCentroidY = centroid.second
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (hadMultiTouch) {
                    if (scrollGestureActive && event.pointerCount >= 2) {
                        val centroid = touchCentroid(event)
                        val dx = centroid.first - lastScrollCentroidX
                        val dy = centroid.second - lastScrollCentroidY
                        lastScrollCentroidX = centroid.first
                        lastScrollCentroidY = centroid.second
                        queueNativeMouseScroll(
                            cursorX, cursorY,
                            horizontal = -dx / dp(12).toFloat(),
                            vertical = -dy / dp(12).toFloat()
                        )
                    }
                    return true
                }

                val x = event.x
                val y = event.y
                val deltaX = x - lastTouchX
                val deltaY = y - lastTouchY
                if (kotlin.math.abs(x - downX) > dp(6) || kotlin.math.abs(y - downY) > dp(6)) {
                    pointerMoved = true
                    // Do not cancel: timer decides whether the hold was stationary long enough.
                }
                cursorX = (cursorX + deltaX * targetWidth / width.toFloat() * cursorSensitivity)
                    .coerceIn(0f, (targetWidth - 1).toFloat())
                cursorY = (cursorY + deltaY * targetHeight / height.toFloat() * cursorSensitivity)
                    .coerceIn(0f, (targetHeight - 1).toFloat())
                lastTouchX = x
                lastTouchY = y
                cursorOverlay?.invalidate()
                if (dragActive) queueNativeMouseButton(MotionEvent.ACTION_MOVE, cursorX, cursorY)
                else queueNativeMouseMove(cursorX, cursorY)
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                hadMultiTouch = true
                scrollGestureActive = event.pointerCount > 2
                return true
            }

            MotionEvent.ACTION_UP -> {
                touchpadFingerDown = false
                longPressTask?.let(gestureHandler::removeCallbacks)
                if (dragActive) {
                    queueNativeMouseButton(MotionEvent.ACTION_UP, cursorX, cursorY)
                    dragActive = false
                } else if (!hadMultiTouch && !pointerMoved) {
                    val controller = virtualDisplayController
                    if (controller == null || controller.displayId < 0) {
                        status.text = "Buat virtual display lewat Shizuku sebelum mengklik."
                    } else {
                        val clickX = cursorX.coerceIn(0f, (targetWidth - 1).toFloat())
                        val clickY = cursorY.coerceIn(0f, (targetHeight - 1).toFloat())
                        mouseInputExecutor.execute {
                            val clicked = controller.injectTouchClick(clickX, clickY)
                            runOnUiThread {
                                if (clicked) {
                                    pulseDextopCursor()
                                    status.text = "Tap layar • x=${clickX.toInt()} y=${clickY.toInt()}"
                                } else {
                                    status.text = "Klik mouse ditolak InputManager. Periksa Runtime Log."
                                    appendRuntimeLog("Injeksi tap layar gagal di x=${clickX.toInt()} y=${clickY.toInt()}: ${shizukuBridge.getLastError()}")
                                }
                            }
                        }
                    }
                }
                pointerMoved = false
                hadMultiTouch = false
                scrollGestureActive = false
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                touchpadFingerDown = false
                longPressTask?.let(gestureHandler::removeCallbacks)
                if (dragActive) {
                    queueNativeMouseButton(MotionEvent.ACTION_UP, cursorX, cursorY)
                    dragActive = false
                }
                pointerMoved = false
                hadMultiTouch = false
                scrollGestureActive = false
                return true
            }
        }
        return true
    }

    private fun touchCentroid(event: MotionEvent): Pair<Float, Float> =
        ((event.getX(0) + event.getX(1)) / 2f) to ((event.getY(0) + event.getY(1)) / 2f)

    private fun applyDisplaySettings() {
        val width = resolutionWidthInput.text.toString().toIntOrNull()
        val height = resolutionHeightInput.text.toString().toIntOrNull()
        val dpi = resolutionDpiInput.text.toString().toIntOrNull()
        if (width == null || height == null || dpi == null ||
            width !in 320..2560 || height !in 320..2560 || dpi !in 40..640
        ) {
            status.text = "Resolusi harus 320–2560 px dan DPI 40–640."
            return
        }
        val resolutionSuffix = if (resolutionEditorLandscape) "landscape" else "portrait"
        val currentLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        getSharedPreferences("saku_desktop_resolution", Context.MODE_PRIVATE).edit()
            .putInt("${resolutionSuffix}_width", width)
            .putInt("${resolutionSuffix}_height", height)
            .putInt("${resolutionSuffix}_dpi", dpi)
            .apply()
        if (resolutionEditorLandscape != currentLandscape) {
            status.text = "Profil ${if (resolutionEditorLandscape) "Landscape" else "Portrait"} tersimpan: ${width}×${height} • ${dpi} DPI. Profil aktif tidak berubah sampai orientasi diganti."
            return
        }
        targetWidth = width
        targetHeight = height
        targetDpi = dpi
        cursorX = cursorX.coerceIn(0f, (targetWidth - 1).toFloat())
        cursorY = cursorY.coerceIn(0f, (targetHeight - 1).toFloat())
        preview?.contentDescription = "Desktop virtual display ${targetWidth} x ${targetHeight} • ${targetDpi} DPI"
        preview?.holder?.setFixedSize(targetWidth, targetHeight)
        if ((virtualDisplayController?.displayId ?: -1) >= 0) {
            startOwnedVirtualDisplay()
        } else {
            status.text = "Pengaturan disimpan: ${targetWidth}×${targetHeight} • ${targetDpi} DPI. Tekan Mulai."
        }
        updateDesktopLayout((preview?.parent as? View)?.width ?: 0, (preview?.parent as? View)?.height ?: 0)
    }

    private fun selectResolutionProfile(landscape: Boolean) {
        resolutionEditorLandscape = landscape
        val prefs = getSharedPreferences("saku_desktop_resolution", Context.MODE_PRIVATE)
        val suffix = if (landscape) "landscape" else "portrait"
        val width = prefs.getInt("${suffix}_width", 960).coerceIn(320, 2560)
        val height = prefs.getInt("${suffix}_height", 820).coerceIn(320, 2560)
        val dpi = prefs.getInt("${suffix}_dpi", 160).coerceIn(40, 640)
        resolutionProfileLabel.text = "Profil: ${if (landscape) "Landscape" else "Portrait"}"
        resolutionWidthInput.setText(width.toString())
        resolutionHeightInput.setText(height.toString())
        resolutionDpiInput.setText(dpi.toString())
        status.text = "Mengedit profil ${if (landscape) "Landscape" else "Portrait"}. Perubahan profil lain tetap aman."
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        val landscape = newConfig.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val prefs = getSharedPreferences("saku_desktop_resolution", Context.MODE_PRIVATE)
        val suffix = if (landscape) "landscape" else "portrait"
        targetWidth = prefs.getInt("${suffix}_width", 960).coerceIn(320, 2560)
        targetHeight = prefs.getInt("${suffix}_height", 820).coerceIn(320, 2560)
        targetDpi = prefs.getInt("${suffix}_dpi", 160).coerceIn(40, 640)
        cursorX = cursorX.coerceIn(0f, (targetWidth - 1).toFloat())
        cursorY = cursorY.coerceIn(0f, (targetHeight - 1).toFloat())
        preview?.contentDescription = "Desktop virtual display ${targetWidth} x ${targetHeight} • ${targetDpi} DPI"
        preview?.holder?.setFixedSize(targetWidth, targetHeight)
        selectResolutionProfile(landscape)
        preview?.post {
            val parent = preview?.parent as? View
            if ((virtualDisplayController?.displayId ?: -1) >= 0) startOwnedVirtualDisplay()
            if (parent != null) updateDesktopLayout(parent.width, parent.height)
            assistantBallView?.post {
                val ball = assistantBallView ?: return@post
                val params = ball.layoutParams as? FrameLayout.LayoutParams ?: return@post
                val maxX = (parent?.width ?: 0) - ball.width
                val maxY = (parent?.height ?: 0) - ball.height
                val xPercent = if (landscape) assistantBallLandscapeXPercent else assistantBallPortraitXPercent
                val yPercent = if (landscape) assistantBallLandscapeYPercent else assistantBallPortraitYPercent
                params.gravity = Gravity.TOP or Gravity.START
                params.leftMargin = ((maxX.coerceAtLeast(0)) * xPercent / 100f).toInt()
                params.topMargin = ((maxY.coerceAtLeast(0)) * yPercent / 100f).toInt()
                ball.layoutParams = params
                ball.bringToFront()
            }
        }
    }
    private fun updateDesktopLayout(rootWidth: Int, rootHeight: Int) {
        if (rootWidth <= 0 || rootHeight <= 0 || !::bottomPanel.isInitialized) return
        val landscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val aspect = targetWidth.toFloat() / targetHeight.toFloat()
        val previewParams = preview?.layoutParams as? FrameLayout.LayoutParams
        val panelParams = bottomPanel.layoutParams as? FrameLayout.LayoutParams
        val keyboardParams = keyboardPanel.layoutParams as? FrameLayout.LayoutParams

        if (landscape) {
            // Landscape desktop uses the full available screen. Keep the virtual
            // display aspect ratio and center it; input overlays float above it.
            val desktopWidth = rootWidth
            val requestedHeight = (rootHeight * panelPositionPercent / 100).coerceAtLeast(1)
            val desktopHeight = minOf(requestedHeight, (desktopWidth / aspect).toInt().coerceAtLeast(1))
            val fittedWidth = minOf(desktopWidth, (desktopHeight * aspect).toInt().coerceAtLeast(1))
            previewParams?.let {
                it.width = fittedWidth
                it.height = desktopHeight
                it.gravity = Gravity.CENTER
                it.leftMargin = 0; it.rightMargin = 0; it.topMargin = 0; it.bottomMargin = 0
                preview?.layoutParams = it
            }

            // Trackpad is a floating overlay and does not reserve a side column.
            panelParams?.let {
                if (keyboardPanelVisible || !trackpadPanelEnabled) {
                    bottomPanel.visibility = View.GONE
                    it.width = 0
                    it.height = 0
                } else {
                    bottomPanel.visibility = View.VISIBLE
                    it.width = (rootWidth * touchpadWidthPercent / 100)
                        .coerceIn(dp(120), (rootWidth * 45 / 100).coerceAtLeast(dp(120)))
                    it.height = (rootHeight * touchpadLandscapeHeightPercent / 100)
                        .coerceIn(dp(130), rootHeight)
                }
                if (touchpadLandscapeHasCustomPosition) {
                    val maxX = (rootWidth - it.width).coerceAtLeast(0)
                    val maxY = (rootHeight - it.height).coerceAtLeast(0)
                    it.gravity = Gravity.TOP or Gravity.LEFT
                    it.leftMargin = (maxX * touchpadLandscapeXPercent / 100f).toInt()
                    it.topMargin = (maxY * touchpadLandscapeYPercent / 100f).toInt()
                } else {
                    it.gravity = Gravity.LEFT or Gravity.CENTER_VERTICAL
                    it.leftMargin = dp(6)
                    it.topMargin = 0
                }
                it.rightMargin = 0; it.bottomMargin = 0
                bottomPanel.layoutParams = it
            }

            // Do not place the keyboard in a left/right side column. When the
            // in-app keyboard is requested, show it as a centered bottom overlay.
            keyboardParams?.let {
                keyboardPanel.visibility = if (keyboardPanelVisible) View.VISIBLE else View.GONE
                it.width = (rootWidth * keyboardLandscapeWidthPercent / 100)
                    .coerceIn(dp(220).coerceAtMost(rootWidth), rootWidth)
                it.height = dp(keyboardLandscapeHeightDp).coerceAtMost(rootHeight)
                if (keyboardLandscapeHasCustomPosition) {
                    val maxX = (rootWidth - it.width).coerceAtLeast(0)
                    val maxY = (rootHeight - it.height).coerceAtLeast(0)
                    it.gravity = Gravity.TOP or Gravity.START
                    it.leftMargin = (maxX * keyboardLandscapeXPercent / 100f).toInt()
                    it.topMargin = (maxY * keyboardLandscapeYPercent / 100f).toInt()
                    it.rightMargin = 0
                    it.bottomMargin = 0
                } else {
                    it.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    it.leftMargin = dp(7); it.rightMargin = dp(7)
                    it.topMargin = 0; it.bottomMargin = dp(8)
                }
                keyboardPanel.layoutParams = it
            }
        } else {
            // Portrait preview fills the app height, matching the Cyber Saku Dex
            // full-height preview behavior. Width remains controlled by its slider.
            val desktopWidth = (rootWidth * previewSizePercent / 100)
                .coerceIn(dp(180).coerceAtMost(rootWidth), rootWidth)
            val desktopHeight = (rootHeight * panelPositionPercent / 100)
                .coerceIn(dp(180).coerceAtMost(rootHeight), rootHeight)
            previewParams?.let {
                it.width = desktopWidth
                it.height = desktopHeight
                it.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                it.leftMargin = 0; it.rightMargin = 0
                it.topMargin = 0; it.bottomMargin = 0
                preview?.layoutParams = it
            }
            panelParams?.let {
                bottomPanel.visibility = if (trackpadPanelEnabled && !keyboardPanelVisible) View.VISIBLE else View.GONE
                val panelHeight = (rootHeight * touchpadPortraitHeightPercent / 100)
                    .coerceIn(dp(120), (rootHeight * 65 / 100).coerceAtLeast(dp(120)))
                it.width = (rootWidth * touchpadPortraitWidthPercent / 100)
                    .coerceIn(dp(150).coerceAtMost(rootWidth), rootWidth)
                it.height = panelHeight
                if (touchpadPortraitHasCustomPosition) {
                    val maxX = (rootWidth - it.width).coerceAtLeast(0)
                    val maxY = (rootHeight - it.height).coerceAtLeast(0)
                    it.gravity = Gravity.TOP or Gravity.START
                    it.leftMargin = (maxX * touchpadPortraitXPercent / 100f).toInt()
                    it.topMargin = (maxY * touchpadPortraitYPercent / 100f).toInt()
                    it.rightMargin = 0; it.bottomMargin = 0
                } else {
                    it.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    it.topMargin = (rootHeight * 70 / 100).coerceAtMost(rootHeight - panelHeight)
                    it.bottomMargin = 0
                    it.leftMargin = 0; it.rightMargin = 0
                }
                bottomPanel.layoutParams = it
            }
            keyboardParams?.let {
                keyboardPanel.visibility = if (keyboardPanelVisible) View.VISIBLE else View.GONE
                val availableKeyboardWidth = (rootWidth - dp(14)).coerceAtLeast(dp(260).coerceAtMost(rootWidth))
                val keyboardWidth = (availableKeyboardWidth * keyboardWidthPercent / 100).coerceIn(dp(260).coerceAtMost(rootWidth), rootWidth)
                val keyboardHeight = dp(keyboardHeightDp).coerceAtMost(rootHeight)
                val desiredGravity = if (keyboardHasCustomPosition) Gravity.TOP or Gravity.START else Gravity.BOTTOM
                if (keyboardHasCustomPosition) {
                    keyboardPositionX = keyboardPositionX.coerceIn(0, (rootWidth - keyboardWidth).coerceAtLeast(0))
                    keyboardPositionY = keyboardPositionY.coerceIn(0, (rootHeight - keyboardHeight).coerceAtLeast(0))
                    it.leftMargin = keyboardPositionX; it.topMargin = keyboardPositionY
                    it.rightMargin = 0; it.bottomMargin = 0
                } else {
                    it.leftMargin = dp(7); it.rightMargin = dp(7)
                    it.bottomMargin = dp(12); it.topMargin = 0
                }
                it.width = keyboardWidth
                it.height = keyboardHeight
                it.gravity = desiredGravity
                keyboardPanel.layoutParams = it
            }
        }
    }

    private fun persistKeyboardSize() {
        getSharedPreferences("saku_desktop_keyboard", Context.MODE_PRIVATE).edit()
            .putInt("width_percent", keyboardWidthPercent)
            .putInt("height_dp", keyboardHeightDp)
            .putInt("landscape_width_percent", keyboardLandscapeWidthPercent)
            .putInt("landscape_height_dp", keyboardLandscapeHeightDp)
            .apply()
    }

    private fun persistTouchpadSettings() {
        getSharedPreferences("saku_desktop_touchpad", Context.MODE_PRIVATE).edit()
            .putInt("width_percent", touchpadWidthPercent)
            .putInt("height_percent", touchpadLandscapeHeightPercent)
            .putInt("portrait_height_percent", touchpadPortraitHeightPercent)
            .putInt("portrait_width_percent", touchpadPortraitWidthPercent)
            .putBoolean("landscape_position_v2", true)
            .putBoolean("landscape_custom_position", touchpadLandscapeHasCustomPosition)
            .putInt("landscape_x_percent", touchpadLandscapeXPercent)
            .putInt("landscape_y_percent", touchpadLandscapeYPercent)
            .putBoolean("portrait_custom_position", touchpadPortraitHasCustomPosition)
            .putInt("portrait_x_percent", touchpadPortraitXPercent)
            .putInt("portrait_y_percent", touchpadPortraitYPercent)
            .apply()
    }

    private fun topBarHeight(): Int {
        val parent = status.parent as? View
        return parent?.height ?: dp(170)
    }

    private fun statusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else dp(24)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt().coerceAtLeast(1)
}