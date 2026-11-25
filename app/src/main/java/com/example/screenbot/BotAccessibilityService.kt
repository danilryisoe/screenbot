package com.example.screenbot

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class BotAccessibilityService : AccessibilityService() {

    companion object {
        var instance: BotAccessibilityService? = null
    }

    private val handler = Handler(Looper.getMainLooper())
    private val vision = VisionEngine()
    private val combat = CombatController()

    private var isRunning = false
    private var currentMode = BotMode.SHOWDOWN_FARM

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var hudStatusText: TextView? = null
    private var hudToggleBtn: Button? = null
    private var hudModeBtn: Button? = null

    private val tickIntervalMs = 750L

    private val loopRunnable = object : Runnable {
        override fun run() {
            if (isRunning) {
                captureAndAct()
            }
            handler.postDelayed(this, tickIntervalMs)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        showFloatingHud()
        handler.post(loopRunnable)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() {
        isRunning = false
        updateHud()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(loopRunnable)
        overlayView?.let { windowManager?.removeView(it) }
        instance = null
    }

    private fun showFloatingHud() {
        if (!Settings.canDrawOverlays(this)) return

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(200, 20, 24, 32))
            setPadding(18, 14, 18, 14)
        }

        hudStatusText = TextView(this).apply {
            text = "ScreenBot: Пауза"
            setTextColor(Color.WHITE)
            textSize = 11f
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        hudToggleBtn = Button(this).apply {
            text = "▶ Старт"
            textSize = 11f
            setOnClickListener {
                isRunning = !isRunning
                updateHud()
            }
        }

        hudModeBtn = Button(this).apply {
            text = "Режим: ШД"
            textSize = 11f
            setOnClickListener {
                val values = BotMode.values()
                currentMode = values[(currentMode.ordinal + 1) % values.size]
                updateHud()
            }
        }

        row.addView(hudToggleBtn)
        row.addView(hudModeBtn)
        container.addView(hudStatusText)
        container.addView(row)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 140
        }

        // Перетаскивание плавающего окна пальцем
        var initX = 0
        var initY = 0
        var touchX = 0f
        var touchY = 0f
        hudStatusText?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initX = params.x
                    initY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initX + (event.rawX - touchX).toInt()
                    params.y = initY + (event.rawY - touchY).toInt()
                    windowManager?.updateViewLayout(container, params)
                    true
                }
                else -> false
            }
        }

        overlayView = container
        windowManager?.addView(container, params)
    }

    private fun updateHud(extraStatus: String? = null) {
        handler.post {
            hudToggleBtn?.text = if (isRunning) "⏸ Стоп" else "▶ Старт"
            hudModeBtn?.text = when (currentMode) {
                BotMode.SHOWDOWN_FARM -> "ШД"
                BotMode.AGGRESSIVE -> "Пуш"
                BotMode.ANTI_AFK -> "АФК"
            }
            hudStatusText?.text = extraStatus ?: if (isRunning) "Активен (${currentMode.title})" else "ScreenBot: Пауза"
        }
    }

    private fun captureAndAct() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return

        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            mainExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    val hw = Bitmap.wrapHardwareBuffer(screenshot.hardwareBuffer, screenshot.colorSpace)
                    val bmp = hw?.copy(Bitmap.Config.ARGB_8888, false)
                    screenshot.hardwareBuffer.close()
                    hw?.recycle()

                    if (bmp != null) {
                        val analysis = vision.analyze(bmp)
                        val w = bmp.width.toFloat()
                        val h = bmp.height.toFloat()
                        bmp.recycle()
                        executeAnalysis(analysis, w, h)
                    }
                }

                override fun onFailure(errorCode: Int) {
                    updateHud("Ошибка кадра: $errorCode")
                }
            }
        )
    }

    private fun executeAnalysis(analysis: FrameAnalysis, w: Float, h: Float) {
        updateHud(analysis.state.label)

        when (analysis.state) {
            GameState.LOBBY, GameState.END_SCREEN -> {
                // Нажимаем "Играть" / "Выйти" в правом нижнем углу
                tap(w * 0.89f, h * 0.88f)
            }
            GameState.POPUP -> {
                // Закрываем всплывающее окно/награду тапом в нейтральную зону и "Далее"
                tap(w * 0.89f, h * 0.88f)
            }
            GameState.MATCHMAKING, GameState.UNKNOWN -> {
                // Ждём загрузки матча
            }
            GameState.IN_BATTLE -> {
                val action = combat.decide(analysis, currentMode)

                // 1. Движение левым джойстиком
                action.moveAngleRad?.let { angle ->
                    val joyX = w * 0.17f
                    val joyY = h * 0.73f
                    val offset = combat.angleToOffset(angle, h * 0.14f)
                    swipe(joyX, joyY, joyX + offset.x, joyY + offset.y, action.moveDurationMs)
                }

                // 2. Ульта с прицелом или автоатака
                if (action.shouldUseSuper && action.aimAngleRad != null) {
                    handler.postDelayed({
                        val superX = w * 0.75f
                        val superY = h * 0.78f
                        val aim = combat.angleToOffset(action.aimAngleRad, h * 0.12f)
                        swipe(superX, superY, superX + aim.x, superY + aim.y, 160L)
                    }, 120L)
                } else if (action.shouldAttack) {
                    handler.postDelayed({
                        tap(w * 0.86f, h * 0.74f)
                    }, 140L)
                }
            }
        }
    }

    private fun tap(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 55L)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    private fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long) {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }
}
