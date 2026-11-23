package com.kaushikkm.ekatrahub.peek

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import com.kaushikkm.ekatrahub.EkatraApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/**
 * The BlackBerry red LED, recreated on screen.
 *
 * Phones like the Galaxy S25 have no notification light, so Ekatra draws a tiny glowing dot
 * at the top of the screen that blinks while there are unread notifications in Ekatra -
 * readable from across the room, like the original.
 *
 * Limits (Android rules, not choices):
 *  - It can only show while the screen is on. Apps can't draw on a sleeping screen or on
 *    Samsung's Always On Display.
 *  - It never takes touches: taps on that spot go straight to the app underneath.
 */
class LedController(private val service: Service) {

    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val settings = PeekSettings.get(service)
    private val dao = (service.application as EkatraApp).database.notificationDao()
    private val density = service.resources.displayMetrics.density
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val screenOn = MutableStateFlow(service.getSystemService(PowerManager::class.java).isInteractive)
    private var led: LedView? = null
    private var lastState: State? = null

    /** [colors]: one per LED app with unread notifications (or one, if all apps count). */
    private data class State(val config: PeekConfig, val colors: List<LedColor>, val screenOn: Boolean)

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            screenOn.value = intent.action == Intent.ACTION_SCREEN_ON
        }
    }

    fun start() {
        ContextCompat.registerReceiver(
            service,
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        scope.launch {
            val unreadByApp = dao.observeApps().map { apps -> apps.associate { it.packageName to it.unread } }
            combine(settings.config, settings.refresh, unreadByApp, screenOn) { config, _, unread, on ->
                State(config, ledColorsFor(config, unread), on)
            }.distinctUntilChanged().collect { render(it) }
        }
    }

    fun stop() {
        runCatching { service.unregisterReceiver(screenReceiver) }
        remove()
        scope.cancel()
    }

    /** Screen rotated / resized: re-place the LED. */
    fun refresh() {
        remove()
        lastState?.let { render(it) }
    }

    private fun render(state: State) {
        lastState = state
        val show = state.config.ledEnabled && state.colors.isNotEmpty() && state.screenOn &&
            Settings.canDrawOverlays(service)
        if (!show) {
            remove()
            return
        }
        val view = led ?: LedView(service).also { v ->
            try {
                windowManager.addView(v, params(state.config.ledPosition))
                led = v
            } catch (e: RuntimeException) {
                Log.w("EkatraLed", "Could not show LED", e)
                return
            }
        }
        view.colors = state.colors.map { it.argb }
        runCatching { windowManager.updateViewLayout(view, params(state.config.ledPosition)) }
    }

    private fun remove() {
        led?.let { runCatching { windowManager.removeViewImmediate(it) } }
        led = null
    }

    private fun params(position: LedPosition): WindowManager.LayoutParams {
        val size = (LED_VIEW_DP * density).toInt()
        val screenWidth = service.resources.displayMetrics.widthPixels
        val statusBar = statusBarHeight()
        val margin = (14 * density).toInt()
        val (x, y) = when (position) {
            LedPosition.CAMERA -> {
                val cutout = topCutout()
                if (cutout != null) {
                    // Just right of the punch-hole camera, where a real LED would sit.
                    (cutout.right + (4 * density).toInt()) to (cutout.centerY() - size / 2)
                } else {
                    (screenWidth / 2 + (14 * density).toInt()) to ((statusBar - size) / 2).coerceAtLeast(0)
                }
            }
            LedPosition.LEFT -> margin to statusBar
            LedPosition.RIGHT -> (screenWidth - size - margin) to statusBar
        }
        // Android 12+ forces any overlay that lets taps pass through ("not touchable") down to
        // 80% opacity. Next to the camera the LED sits in the status-bar row, where taps go to
        // the status bar anyway - so there we skip that flag and get full laser brightness.
        // In the corners it sits over apps, so it must let taps through (and stays at 80%).
        val passTouches = position != LedPosition.CAMERA
        return WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                (if (passTouches) WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else 0) or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            this.x = x
            this.y = y
            alpha = if (passTouches) 0.8f else 1f
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                fitInsetsTypes = 0 // allowed to sit up in the status-bar / camera row
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            title = "Ekatra LED"
        }
    }

    @SuppressLint("DiscouragedApi", "InternalInsetResource")
    private fun statusBarHeight(): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return windowManager.currentWindowMetrics.windowInsets.getInsets(WindowInsets.Type.statusBars()).top
        }
        val id = service.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) service.resources.getDimensionPixelSize(id) else (24 * density).toInt()
    }

    /** Bounds of the camera hole at the top of the screen, if the phone has one. */
    private fun topCutout(): Rect? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val cutout = windowManager.currentWindowMetrics.windowInsets.displayCutout ?: return null
        return cutout.boundingRects.firstOrNull { it.top <= 0 || it.top < it.height() }
    }

    private companion object {
        const val LED_VIEW_DP = 16
    }
}

/**
 * The LED dot, drawn like a laser point:
 *  - a small, hard-edged core in full colour (no soft fade at the edge),
 *  - a white-hot centre, like a real LED die,
 *  - only a thin, faint halo around it.
 * It snaps on and off like hardware: ~60 ms on, holds at full brightness, quick fade off,
 * then dark for ~2.4 s. Between blinks the animation is fully stopped (almost no battery).
 */
@SuppressLint("ViewConstructor")
private class LedView(context: Context) : View(context) {

    /** Colours to blink, one per blink in turn (e.g. WhatsApp green, then Gmail red). */
    var colors: List<Int> = listOf(0xFFFF1F1F.toInt())
        set(value) {
            if (field != value && value.isNotEmpty()) {
                field = value
                nextColor = 0
                color = value[0]
            }
        }
    private var nextColor = 0

    private var color: Int = 0xFFFF1F1F.toInt()
        set(value) {
            if (field != value) {
                field = value
                buildPaints()
                invalidate()
            }
        }

    private val d = resources.displayMetrics.density
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val pulse: ValueAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = ON_MS
        interpolator = LinearInterpolator()
        addUpdateListener { alpha = brightness(it.animatedFraction) }
        addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                alpha = 0f
                if (isAttachedToWindow) postDelayed(restart, OFF_MS)
            }
        })
    }
    private val restart: Runnable = Runnable {
        if (isAttachedToWindow) {
            // Next app's colour for this blink.
            nextColor = (nextColor + 1) % colors.size
            color = colors[nextColor]
            pulse.start()
        }
    }

    init {
        alpha = 0f
        buildPaints()
    }

    /** Snap on (first 10%), hold at full (to 75%), quick fade off (to 100%). */
    private fun brightness(t: Float): Float = when {
        t < 0.10f -> t / 0.10f
        t < 0.75f -> 1f
        else -> (1f - (t - 0.75f) / 0.25f).coerceAtLeast(0f)
    }

    // Plain circles: fading them needs no extra off-screen layer.
    override fun hasOverlappingRendering() = false

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        buildPaints()
    }

    private fun buildPaints() {
        corePaint.color = color
        // White-hot centre: the LED colour mixed 65% towards white.
        hotPaint.color = blendTowardsWhite(color, 0.65f)
        if (width > 0) {
            val r = width / 2f
            val glow = (color and 0x00FFFFFF) or 0x59000000
            val clear = color and 0x00FFFFFF
            val coreEdge = CORE_RADIUS_DP * d / r
            haloPaint.shader = RadialGradient(
                r, r, r,
                intArrayOf(glow, glow, clear),
                floatArrayOf(0f, coreEdge, 1f),
                Shader.TileMode.CLAMP,
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        val c = width / 2f
        canvas.drawCircle(c, c, c, haloPaint) // thin glow
        canvas.drawCircle(c, c, CORE_RADIUS_DP * d, corePaint) // sharp coloured core
        canvas.drawCircle(c, c, HOT_RADIUS_DP * d, hotPaint) // white-hot centre
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        pulse.start()
    }

    override fun onDetachedFromWindow() {
        // Cancel first: cancelling fires onAnimationEnd, which schedules the next pulse -
        // then remove that scheduled pulse.
        pulse.cancel()
        removeCallbacks(restart)
        super.onDetachedFromWindow()
    }

    private companion object {
        const val CORE_RADIUS_DP = 3.2f
        const val HOT_RADIUS_DP = 1.3f
        const val ON_MS = 600L
        const val OFF_MS = 2_400L

        fun blendTowardsWhite(color: Int, amount: Float): Int {
            fun mix(channel: Int) = (channel + (255 - channel) * amount).toInt().coerceIn(0, 255)
            val r = mix((color shr 16) and 0xFF)
            val g = mix((color shr 8) and 0xFF)
            val b = mix(color and 0xFF)
            return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
}
