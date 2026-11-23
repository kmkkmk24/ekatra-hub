package com.kaushikkm.ekatrahub.peek

import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.kaushikkm.ekatrahub.ui.MainActivity
import com.kaushikkm.ekatrahub.ui.theme.EkatraTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.math.min

/**
 * Peek & Flow for Android.
 *
 * Two floating windows ("Display over other apps"):
 *  1. The HANDLE - a slim strip on the screen edge, always present while Peek is on.
 *  2. The PANEL  - the Hub itself. Added the moment your finger touches the handle,
 *     slides in following your finger, and removed again when it closes.
 *
 * It lives inside the notification listener service, which Android already keeps running,
 * so Peek needs no extra background service or permanent notification.
 */
class PeekController(private val service: Service) {

    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val settings = PeekSettings.get(service)
    private val density = service.resources.displayMetrics.density

    // The UI dispatcher includes a frame clock, which Compose animations need.
    private val scope = CoroutineScope(SupervisorJob() + AndroidUiDispatcher.Main)
    private val owner = OverlayOwner(service.application)

    /** 0 = Hub hidden, 1 = Hub fully open. Drives the panel's position and the dimming. */
    private val progress = Animatable(0f)

    private var config: PeekConfig = settings.config.value
    private var handle: HandleView? = null
    private var panel: PanelHost? = null
    private var panelOpen = false
    private var settleJob: Job? = null

    // ---- lifecycle -------------------------------------------------------------------------

    fun start() {
        owner.create()
        scope.launch {
            combine(settings.config, settings.refresh) { c, _ -> c }.collect { applyConfig(it) }
        }
        ContextCompat.registerReceiver(
            service,
            systemEventsReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS) // Home / Recents pressed
                addAction(Intent.ACTION_SCREEN_OFF)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    fun stop() {
        runCatching { service.unregisterReceiver(systemEventsReceiver) }
        removePanel()
        removeHandle()
        owner.destroy()
        scope.cancel()
    }

    /** Screen rotated / resized: re-place the handle. */
    fun refresh() {
        removePanel()
        applyConfig(settings.config.value, force = true)
    }

    private val systemEventsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (panel != null) close()
        }
    }

    // ---- handle ----------------------------------------------------------------------------

    private fun applyConfig(newConfig: PeekConfig, force: Boolean = false) {
        val edgeChanged = newConfig.edge != config.edge
        config = newConfig
        if (!newConfig.enabled || !Settings.canDrawOverlays(service)) {
            removePanel()
            removeHandle()
            return
        }
        val existing = handle
        if (existing == null || edgeChanged || force) {
            removeHandle()
            addHandle()
        } else {
            safely { windowManager.updateViewLayout(existing, handleParams()) }
        }
    }

    private fun addHandle() {
        val view = HandleView(service, config.edge)
        view.setOnTouchListener(::onHandleTouch)
        if (safely { windowManager.addView(view, handleParams()) }) handle = view
    }

    private fun removeHandle() {
        handle?.let { safely { windowManager.removeViewImmediate(it) } }
        handle = null
    }

    private fun handleParams(): WindowManager.LayoutParams {
        val height = (config.lengthDp * density).toInt()
        val screenHeight = service.resources.displayMetrics.heightPixels
        return WindowManager.LayoutParams(
            (HANDLE_TOUCH_WIDTH_DP * density).toInt(),
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or if (config.edge == PeekEdge.LEFT) Gravity.LEFT else Gravity.RIGHT
            y = ((screenHeight - height) * config.position).toInt().coerceAtLeast(0)
            title = "Ekatra Peek handle"
        }
    }

    // ---- the gesture -----------------------------------------------------------------------

    private var downX = 0f
    private var dragging = false
    private var passedHalfway = false
    private var velocityTracker: VelocityTracker? = null
    private val touchSlop = ViewConfiguration.get(service).scaledTouchSlop

    @SuppressLint("ClickableViewAccessibility")
    private fun onHandleTouch(view: View, event: MotionEvent): Boolean {
        // Distance moved *away from* the edge, whichever edge the handle is on.
        val direction = if (config.edge == PeekEdge.LEFT) 1f else -1f
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                dragging = false
                passedHalfway = false
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
                // Add the panel right away (still off-screen) so it's ready the moment you drag.
                showPanel()
            }

            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                val dx = (event.rawX - downX) * direction
                if (!dragging && dx > touchSlop) dragging = true
                if (dragging) {
                    val p = (dx / panelWidthPx()).coerceIn(0f, 1f)
                    settleJob?.cancel()
                    scope.launch { progress.snapTo(p) }
                    // A light tick when you pass the point where letting go opens the Hub.
                    val nowPast = p >= OPEN_THRESHOLD
                    if (nowPast != passedHalfway) {
                        passedHalfway = nowPast
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    }
                }
            }

            MotionEvent.ACTION_UP -> {
                val tracker = velocityTracker
                tracker?.addMovement(event)
                tracker?.computeCurrentVelocity(1000)
                val velocity = (tracker?.xVelocity ?: 0f) * direction
                tracker?.recycle()
                velocityTracker = null
                val flingThreshold = FLING_VELOCITY_DP * density
                when {
                    !dragging -> open() // a simple tap on the handle opens the Hub
                    velocity > flingThreshold -> open() // flicked inward
                    velocity < -flingThreshold -> close() // flicked back to the edge
                    progress.value >= OPEN_THRESHOLD -> open() // Flow: kept going
                    else -> close() // Peek: let go early
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                velocityTracker?.recycle()
                velocityTracker = null
                close()
            }
        }
        return true
    }

    // ---- panel -----------------------------------------------------------------------------

    private fun panelWidthPx(): Float {
        val metrics = service.resources.displayMetrics
        return min(metrics.widthPixels - PANEL_GAP_DP * density, PANEL_MAX_WIDTH_DP * density)
    }

    private fun showPanel() {
        if (panel != null) return
        val host = PanelHost(service, onBack = ::close, onFocusLost = { if (panelOpen) close() })
        owner.attachTo(host)
        val widthDp = (panelWidthPx() / density).dp
        val edge = config.edge
        host.addView(
            ComposeView(service).apply {
                setContent {
                    EkatraTheme {
                        PeekPanel(
                            progress = { progress.value },
                            edge = edge,
                            panelWidth = widthDp,
                            onClose = ::close,
                            onPanelDrag = ::dragPanel,
                            onPanelDragEnd = ::endPanelDrag,
                            onOpenApp = ::openApp,
                        )
                    }
                }
            },
        )
        if (safely { windowManager.addView(host, panelParams(focusable = false)) }) panel = host
    }

    private fun open() {
        val host = panel ?: return
        panelOpen = true
        // Focusable now, so the search box can use the keyboard and Back closes the Hub.
        safely { windowManager.updateViewLayout(host, panelParams(focusable = true)) }
        settleJob?.cancel()
        settleJob = scope.launch {
            progress.animateTo(1f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow))
        }
    }

    /** Logo tapped in Peek: open the full Ekatra app and slide Peek away. */
    private fun openApp() {
        val intent = Intent(service, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        safely { service.startActivity(intent) }
        close()
    }

    /** Finger pushing the open panel back towards its edge ([towardsClosePx] > 0) or out again. */
    private fun dragPanel(towardsClosePx: Float) {
        if (panel == null) return
        settleJob?.cancel()
        val p = (progress.value - towardsClosePx / panelWidthPx()).coerceIn(0f, 1f)
        scope.launch { progress.snapTo(p) }
    }

    private fun endPanelDrag(velocityTowardsClose: Float) {
        val flingThreshold = FLING_VELOCITY_DP * density
        when {
            velocityTowardsClose > flingThreshold -> close()
            velocityTowardsClose < -flingThreshold -> open()
            progress.value < CLOSE_THRESHOLD -> close()
            else -> open()
        }
    }

    private fun close() {
        if (panel == null) return
        panelOpen = false
        settleJob?.cancel()
        settleJob = scope.launch {
            progress.animateTo(0f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium))
            removePanel()
        }
    }

    private fun removePanel() {
        panel?.let { safely { windowManager.removeViewImmediate(it) } }
        panel = null
        panelOpen = false
        scope.launch { progress.snapTo(0f) }
    }

    private fun panelParams(focusable: Boolean): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                if (focusable) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            title = "Ekatra Peek"
        }

    /** Window operations can fail if the user revokes "Display over other apps" mid-way. */
    private inline fun safely(block: () -> Unit): Boolean = try {
        block()
        true
    } catch (e: RuntimeException) {
        Log.w(TAG, "Overlay window operation failed", e)
        false
    }

    private companion object {
        const val TAG = "EkatraPeek"
        const val HANDLE_TOUCH_WIDTH_DP = 20
        const val PANEL_GAP_DP = 48
        const val PANEL_MAX_WIDTH_DP = 480
        const val OPEN_THRESHOLD = 0.4f
        const val CLOSE_THRESHOLD = 0.65f
        const val FLING_VELOCITY_DP = 600
    }
}

/** The visible handle: a soft lotus-pink pill hugging the screen edge. */
@SuppressLint("ViewConstructor")
private class HandleView(context: Context, private val edge: PeekEdge) : View(context) {
    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xB3F6B8D1.toInt() }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x66000000
        style = Paint.Style.STROKE
        strokeWidth = 1f * d
    }
    private val pill = RectF()

    private companion object {
        const val MAX_EXCLUSION_DP = 200
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        // With gesture navigation, swiping in from an edge means "Back". Exclude the handle area
        // so swipes that start on it reach Ekatra instead. Back still works everywhere else.
        // Android honours at most 200dp of exclusion per edge, so on a longer handle we claim
        // the middle 200dp; on the rest of it Back may win on some phones.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val maxPx = (MAX_EXCLUSION_DP * d).toInt()
            val top = ((height - maxPx) / 2).coerceAtLeast(0)
            systemGestureExclusionRects = listOf(Rect(0, top, width, (top + maxPx).coerceAtMost(height)))
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = 5f * d
        val inset = 2f * d
        val left = if (edge == PeekEdge.LEFT) inset else width - inset - w
        pill.set(left, 0f, left + w, height.toFloat())
        canvas.drawRoundRect(pill, w, w, paint)
        canvas.drawRoundRect(pill, w, w, outline)
    }
}

/** Root of the panel window: turns the Back key and focus loss into "close the Hub". */
@SuppressLint("ViewConstructor")
private class PanelHost(
    context: Context,
    private val onBack: () -> Unit,
    private val onFocusLost: () -> Unit,
) : FrameLayout(context) {
    private var hadFocus = false

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) onBack()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) hadFocus = true else if (hadFocus) onFocusLost()
    }
}
