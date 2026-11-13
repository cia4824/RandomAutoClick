package com.example.randomautoclicker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class AutoClickService : AccessibilityService() {

    companion object {
        const val PREFS_NAME = "auto_click_prefs"
        const val KEY_MIN_DELAY = "min_delay_ms"
        const val KEY_MAX_DELAY = "max_delay_ms"
        const val KEY_RANDOM_RADIUS = "random_radius_px"
        const val KEY_RANDOM_ORDER = "random_target_order"

        var instance: AutoClickService? = null
            private set
    }

    private data class TargetItem(
        val view: TargetCircleView,
        val params: WindowManager.LayoutParams
    )

    private lateinit var windowManager: WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private var controlPanelView: LinearLayout? = null
    private var controlPanelParams: WindowManager.LayoutParams? = null
    private var btnPlayPause: TextView? = null
    private var btnOrderMode: TextView? = null

    private val targets = mutableListOf<TargetItem>()
    private var isOverlayVisible = false
    private var isRunning = false
    private var currentIndex = 0

    private var minDelayMs = 300L
    private var maxDelayMs = 800L
    private var randomRadiusPx = 45
    private var isRandomOrder = false

    private val clickRunnable = Runnable { performClickStep() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        loadSettings()
        showOverlay()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    }

    override fun onInterrupt() {
        stopClicking()
    }

    override fun onDestroy() {
        super.onDestroy()
        hideOverlay()
        instance = null
    }

    fun reloadSettings() {
        loadSettings()
        updateOrderButtonUI()
        targets.forEach { target ->
            target.view.requestLayout()
            target.view.invalidate()
            try {
                windowManager.updateViewLayout(target.view, target.params)
            } catch (_: Exception) {
            }
        }
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        minDelayMs = prefs.getLong(KEY_MIN_DELAY, 300L).coerceAtLeast(50L)
        maxDelayMs = prefs.getLong(KEY_MAX_DELAY, 800L).coerceAtLeast(minDelayMs)
        randomRadiusPx = prefs.getInt(KEY_RANDOM_RADIUS, 45).coerceIn(0, 500)
        isRandomOrder = prefs.getBoolean(KEY_RANDOM_ORDER, false)
    }

    fun toggleOverlayVisibility() {
        if (isOverlayVisible) {
            hideOverlay()
        } else {
            showOverlay()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showOverlay() {
        if (isOverlayVisible) return
        isOverlayVisible = true

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(6), dp(8), dp(6), dp(8))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#DD222222"))
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), Color.parseColor("#55FFFFFF"))
            }
        }

        val dragHandle = TextView(this).apply {
            text = "⠿"
            textSize = 18f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(2), dp(8), dp(6))
        }
        panel.addView(dragHandle)

        btnPlayPause = createPanelButton("▶", "#2E7D32") {
            if (isRunning) stopClicking() else startClicking()
        }
        panel.addView(btnPlayPause)

        val btnAdd = createPanelButton("+", "#1976D2") {
            if (!isRunning) addTarget()
        }
        panel.addView(btnAdd)

        val btnRemove = createPanelButton("-", "#757575") {
            if (!isRunning) removeLastTarget()
        }
        panel.addView(btnRemove)

        btnOrderMode = createPanelButton("➡️", "#EF6C00") {
            isRandomOrder = !isRandomOrder
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_RANDOM_ORDER, isRandomOrder)
                .apply()
            updateOrderButtonUI()
        }
        updateOrderButtonUI()
        panel.addView(btnOrderMode)

        val btnClose = createPanelButton("✕", "#C62828") {
            hideOverlay()
        }
        panel.addView(btnClose)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16)
            y = dp(160)
        }

        attachDragListener(dragHandle, panel, params)

        controlPanelView = panel
        controlPanelParams = params
        windowManager.addView(panel, params)

        if (targets.isEmpty()) {
            addTarget()
        }
    }

    private fun hideOverlay() {
        stopClicking()
        isOverlayVisible = false

        targets.forEach { target ->
            try {
                windowManager.removeView(target.view)
            } catch (_: Exception) {
            }
        }
        targets.clear()

        controlPanelView?.let { panel ->
            try {
                windowManager.removeView(panel)
            } catch (_: Exception) {
            }
        }
        controlPanelView = null
        controlPanelParams = null
    }

    private fun addTarget() {
        if (targets.size >= 15) return

        val index = targets.size + 1
        val targetView = TargetCircleView(this, index)
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels

        val offset = (targets.size * dp(28)) % dp(140)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (screenWidth / 2 - dp(26)) + offset
            y = (screenHeight / 2 - dp(26)) + offset
        }

        attachDragListener(targetView, targetView, params)
        windowManager.addView(targetView, params)
        targets.add(TargetItem(targetView, params))
    }

    private fun removeLastTarget() {
        if (targets.isEmpty()) return
        val last = targets.removeAt(targets.lastIndex)
        try {
            windowManager.removeView(last.view)
        } catch (_: Exception) {
        }
    }

    private fun startClicking() {
        if (isRunning) return
        if (targets.isEmpty()) addTarget()

        isRunning = true
        currentIndex = 0
        btnPlayPause?.text = "⏸"
        (btnPlayPause?.background as? GradientDrawable)?.setColor(Color.parseColor("#D84315"))

        targets.forEach { target ->
            target.params.flags = target.params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            target.view.invalidate()
            try {
                windowManager.updateViewLayout(target.view, target.params)
            } catch (_: Exception) {
            }
        }

        handler.postDelayed(clickRunnable, 150L)
    }

    private fun stopClicking() {
        isRunning = false
        handler.removeCallbacks(clickRunnable)
        btnPlayPause?.text = "▶"
        (btnPlayPause?.background as? GradientDrawable)?.setColor(Color.parseColor("#2E7D32"))

        targets.forEach { target ->
            target.params.flags = target.params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            target.view.invalidate()
            try {
                windowManager.updateViewLayout(target.view, target.params)
            } catch (_: Exception) {
            }
        }
    }

    private fun performClickStep() {
        if (!isRunning || targets.isEmpty()) return

        val target = if (isRandomOrder) {
            targets[Random.nextInt(targets.size)]
        } else {
            val item = targets[currentIndex % targets.size]
            currentIndex = (currentIndex + 1) % targets.size
            item
        }

        val loc = IntArray(2)
        target.view.getLocationOnScreen(loc)
        val centerX = loc[0] + target.view.width / 2f
        val centerY = loc[1] + target.view.height / 2f

        val (rawX, rawY) = if (randomRadiusPx > 0) {
            val angle = Random.nextDouble(0.0, 2.0 * Math.PI)
            val dist = randomRadiusPx * sqrt(Random.nextDouble(0.0, 1.0))
            val x = (centerX + dist * cos(angle)).toFloat()
            val y = (centerY + dist * sin(angle)).toFloat()
            x to y
        } else {
            centerX to centerY
        }

        val clickX = rawX.coerceAtLeast(1f)
        val clickY = rawY.coerceAtLeast(1f)

        val path = Path().apply {
            moveTo(clickX, clickY)
        }
        val pressDuration = Random.nextLong(40L, 85L)
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, pressDuration))
            .build()

        val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                scheduleNextClick()
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                scheduleNextClick()
            }
        }, null)

        if (!dispatched) {
            scheduleNextClick()
        }
    }

    private fun scheduleNextClick() {
        if (!isRunning) return
        val delay = if (maxDelayMs > minDelayMs) {
            Random.nextLong(minDelayMs, maxDelayMs + 1)
        } else {
            minDelayMs
        }
        handler.postDelayed(clickRunnable, delay)
    }

    private fun updateOrderButtonUI() {
        btnOrderMode?.text = if (isRandomOrder) "🎲" else "➡️"
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachDragListener(
        touchView: View,
        layoutView: View,
        params: WindowManager.LayoutParams
    ) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        touchView.setOnTouchListener { _, event ->
            if (isRunning && layoutView is TargetCircleView) return@setOnTouchListener false

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    try {
                        windowManager.updateViewLayout(layoutView, params)
                    } catch (_: Exception) {
                    }
                    true
                }

                else -> false
            }
        }
    }

    private fun createPanelButton(label: String, bgColor: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            textSize = 16f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.parseColor(bgColor))
                cornerRadius = dp(10).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply {
                topMargin = dp(6)
            }
            setOnClickListener { onClick() }
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private inner class TargetCircleView(
        context: Context,
        private val indexNumber: Int
    ) : View(context) {

        private val zoneFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#262196F3")
        }

        private val zoneStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(2).toFloat()
            pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
            color = Color.parseColor("#AA1976D2")
        }

        private val centerCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#CC1976D2")
        }

        private val centerBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(2).toFloat()
            color = Color.WHITE
        }

        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = dp(14).toFloat()
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val minSize = dp(52)
            val zoneDiameter = randomRadiusPx * 2 + dp(8)
            val size = max(minSize, zoneDiameter)
            setMeasuredDimension(size, size)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f

            if (isRunning) {
                zoneFillPaint.color = Color.parseColor("#224CAF50")
                zoneStrokePaint.color = Color.parseColor("#AA2E7D32")
                centerCirclePaint.color = Color.parseColor("#AA2E7D32")
            } else {
                zoneFillPaint.color = Color.parseColor("#262196F3")
                zoneStrokePaint.color = Color.parseColor("#AA1976D2")
                centerCirclePaint.color = Color.parseColor("#CC1976D2")
            }

            if (randomRadiusPx > 0) {
                val r = randomRadiusPx.toFloat()
                canvas.drawCircle(cx, cy, r, zoneFillPaint)
                canvas.drawCircle(cx, cy, r, zoneStrokePaint)
            }

            val coreRadius = dp(16).toFloat()
            canvas.drawCircle(cx, cy, coreRadius, centerCirclePaint)
            canvas.drawCircle(cx, cy, coreRadius, centerBorderPaint)

            val textY = cy - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(indexNumber.toString(), cx, textY, textPaint)
        }
    }
}
