package com.kaushikkm.ekatrahub.peek

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PeekEdge { LEFT, RIGHT }

data class PeekConfig(
    val enabled: Boolean,
    val edge: PeekEdge,
    /** 0 = top of the screen, 1 = bottom. */
    val position: Float,
    /** Length of the handle in dp. */
    val lengthDp: Int,
    /** Hub-only mode: remove saved notifications from the phone's notification shade. */
    val hubOnly: Boolean,
    /** BlackBerry-style blinking LED while there are unread notifications. */
    val ledEnabled: Boolean,
    val ledColor: LedColor,
    val ledPosition: LedPosition,
    /** A dot-only notification so the LED shows on the Always On Display / lock screen. */
    val aodDot: Boolean,
    /** Experimental: blink that dot on the sleeping screen. */
    val aodBlink: Boolean,
    /** Seconds between blinks on the sleeping screen: one of [AOD_BLINK_GAPS]. */
    val aodBlinkGapSec: Int,
    /**
     * Apps that light the LED, each with its own colour (at most [MAX_LED_APPS]).
     * Empty = every app lights it, in [ledColor].
     */
    val ledApps: List<LedApp>,
)

data class LedApp(val packageName: String, val color: LedColor)

const val MAX_LED_APPS = 5

/** The blink-gap presets offered in Settings. */
// Shorter gaps would wake the AOD again before it switches off, keeping the screen lit.
val AOD_BLINK_GAPS = listOf(20, 30, 60, 120)

/**
 * LED colours. Each has a matching coloured-circle emoji, used for the dot on the sleeping
 * screen (Samsung draws notification text in white, but emoji keep their own colours).
 */
enum class LedColor(val label: String, val argb: Int, val emoji: String) {
    RED("Red", 0xFFFF1F1F.toInt(), "🔴"), // 🔴
    ORANGE("Orange", 0xFFFF8A00.toInt(), "🟠"), // 🟠
    YELLOW("Yellow", 0xFFFFD60A.toInt(), "🟡"), // 🟡
    GREEN("Green", 0xFF22E05A.toInt(), "🟢"), // 🟢
    BLUE("Blue", 0xFF2F8CFF.toInt(), "🔵"), // 🔵
    PURPLE("Purple", 0xFFB45CFF.toInt(), "🟣"), // 🟣
    WHITE("White", 0xFFFFFFFF.toInt(), "⚪"), // ⚪
}

/**
 * Which colours the LED should show right now, given how many unread notifications each app has.
 * With no LED apps chosen: one colour, if anything at all is unread. Otherwise: one colour per
 * chosen app that has something unread, in the order they were added.
 */
fun ledColorsFor(config: PeekConfig, unreadByApp: Map<String, Int>): List<LedColor> =
    if (config.ledApps.isEmpty()) {
        if (unreadByApp.values.any { it > 0 }) listOf(config.ledColor) else emptyList()
    } else {
        config.ledApps.filter { (unreadByApp[it.packageName] ?: 0) > 0 }.map { it.color }
    }

/** How many unread notifications currently count towards the LED. */
fun ledUnreadFor(config: PeekConfig, unreadByApp: Map<String, Int>): Int =
    if (config.ledApps.isEmpty()) {
        unreadByApp.values.sum()
    } else {
        config.ledApps.sumOf { unreadByApp[it.packageName] ?: 0 }
    }

enum class LedPosition(val label: String) {
    CAMERA("Next to camera"),
    LEFT("Top left"),
    RIGHT("Top right"),
}

/**
 * Peek preferences, stored in a small private settings file on the phone.
 *
 * The settings screen (in the app) and the edge handle (in the background service) run in the
 * same process and share this one instance, so moving a slider moves the handle instantly.
 */
class PeekSettings private constructor(private val prefs: SharedPreferences) {

    private val _config = MutableStateFlow(read())
    val config: StateFlow<PeekConfig> = _config.asStateFlow()

    /** Bumped when something outside these settings changed, e.g. a permission was granted. */
    private val _refresh = MutableStateFlow(0)
    val refresh: StateFlow<Int> = _refresh.asStateFlow()

    private val _bannerDismissed = MutableStateFlow(prefs.getBoolean(KEY_BANNER_DISMISSED, false))
    val bannerDismissed: StateFlow<Boolean> = _bannerDismissed.asStateFlow()

    private fun read() = PeekConfig(
        enabled = prefs.getBoolean(KEY_ENABLED, false),
        edge = runCatching { PeekEdge.valueOf(prefs.getString(KEY_EDGE, null) ?: "") }.getOrDefault(PeekEdge.LEFT),
        position = prefs.getFloat(KEY_POSITION, 0.35f),
        lengthDp = prefs.getInt(KEY_LENGTH, 140),
        hubOnly = prefs.getBoolean(KEY_HUB_ONLY, false),
        ledEnabled = prefs.getBoolean(KEY_LED_ENABLED, false),
        ledColor = runCatching { LedColor.valueOf(prefs.getString(KEY_LED_COLOR, null) ?: "") }.getOrDefault(LedColor.RED),
        ledPosition = runCatching { LedPosition.valueOf(prefs.getString(KEY_LED_POSITION, null) ?: "") }
            .getOrDefault(LedPosition.CAMERA),
        aodDot = prefs.getBoolean(KEY_AOD_DOT, false),
        aodBlink = prefs.getBoolean(KEY_AOD_BLINK, false),
        aodBlinkGapSec = prefs.getInt(KEY_AOD_BLINK_GAP, 30).takeIf { it in AOD_BLINK_GAPS } ?: 30,
        ledApps = decodeLedApps(prefs.getString(KEY_LED_APPS, null)),
    )

    // Stored as "com.whatsapp=GREEN;org.telegram.messenger=BLUE".
    private fun decodeLedApps(raw: String?): List<LedApp> =
        raw.orEmpty().split(';').mapNotNull { entry ->
            val (pkg, color) = entry.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
            val c = runCatching { LedColor.valueOf(color) }.getOrNull() ?: return@mapNotNull null
            LedApp(pkg, c)
        }.distinctBy { it.packageName }.take(MAX_LED_APPS)

    private fun saveLedApps(apps: List<LedApp>) = update {
        putString(KEY_LED_APPS, apps.take(MAX_LED_APPS).joinToString(";") { "${it.packageName}=${it.color.name}" })
    }

    fun addLedApp(packageName: String, color: LedColor) {
        val apps = _config.value.ledApps
        if (apps.size >= MAX_LED_APPS || apps.any { it.packageName == packageName }) return
        saveLedApps(apps + LedApp(packageName, color))
    }

    fun setLedAppColor(packageName: String, color: LedColor) =
        saveLedApps(_config.value.ledApps.map { if (it.packageName == packageName) it.copy(color = color) else it })

    fun removeLedApp(packageName: String) =
        saveLedApps(_config.value.ledApps.filterNot { it.packageName == packageName })

    private fun update(block: SharedPreferences.Editor.() -> Unit) {
        prefs.edit(action = block)
        _config.value = read()
    }

    fun setEnabled(enabled: Boolean) = update { putBoolean(KEY_ENABLED, enabled) }
    fun setEdge(edge: PeekEdge) = update { putString(KEY_EDGE, edge.name) }
    fun setPosition(position: Float) = update { putFloat(KEY_POSITION, position.coerceIn(0f, 1f)) }
    fun setLength(lengthDp: Int) = update { putInt(KEY_LENGTH, lengthDp.coerceIn(MIN_LENGTH, MAX_LENGTH)) }

    fun setHubOnly(enabled: Boolean) = update { putBoolean(KEY_HUB_ONLY, enabled) }

    fun setLedEnabled(enabled: Boolean) = update { putBoolean(KEY_LED_ENABLED, enabled) }
    fun setLedColor(color: LedColor) = update { putString(KEY_LED_COLOR, color.name) }
    fun setLedPosition(position: LedPosition) = update { putString(KEY_LED_POSITION, position.name) }
    fun setAodDot(enabled: Boolean) = update { putBoolean(KEY_AOD_DOT, enabled) }
    fun setAodBlink(enabled: Boolean) = update { putBoolean(KEY_AOD_BLINK, enabled) }
    fun setAodBlinkGap(seconds: Int) = update { putInt(KEY_AOD_BLINK_GAP, seconds) }

    fun dismissBanner() {
        prefs.edit { putBoolean(KEY_BANNER_DISMISSED, true) }
        _bannerDismissed.value = true
    }

    fun requestRefresh() {
        _refresh.value++
    }

    companion object {
        const val MIN_LENGTH = 80
        const val MAX_LENGTH = 400

        private const val KEY_ENABLED = "enabled"
        private const val KEY_EDGE = "edge"
        private const val KEY_POSITION = "position"
        private const val KEY_LENGTH = "length"
        private const val KEY_HUB_ONLY = "hub_only"
        private const val KEY_LED_ENABLED = "led_enabled"
        private const val KEY_LED_COLOR = "led_color"
        private const val KEY_LED_POSITION = "led_position"
        private const val KEY_AOD_DOT = "aod_dot"
        private const val KEY_AOD_BLINK = "aod_blink"
        private const val KEY_AOD_BLINK_GAP = "aod_blink_gap"
        private const val KEY_LED_APPS = "led_apps"
        private const val KEY_BANNER_DISMISSED = "banner_dismissed"

        @Volatile private var instance: PeekSettings? = null

        fun get(context: Context): PeekSettings = instance ?: synchronized(this) {
            instance ?: PeekSettings(
                context.applicationContext.getSharedPreferences("peek", Context.MODE_PRIVATE),
            ).also { instance = it }
        }
    }
}
