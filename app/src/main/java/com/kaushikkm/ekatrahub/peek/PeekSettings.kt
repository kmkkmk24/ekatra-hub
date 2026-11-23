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
)

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
    )

    private fun update(block: SharedPreferences.Editor.() -> Unit) {
        prefs.edit(action = block)
        _config.value = read()
    }

    fun setEnabled(enabled: Boolean) = update { putBoolean(KEY_ENABLED, enabled) }
    fun setEdge(edge: PeekEdge) = update { putString(KEY_EDGE, edge.name) }
    fun setPosition(position: Float) = update { putFloat(KEY_POSITION, position.coerceIn(0f, 1f)) }
    fun setLength(lengthDp: Int) = update { putInt(KEY_LENGTH, lengthDp.coerceIn(MIN_LENGTH, MAX_LENGTH)) }

    fun setHubOnly(enabled: Boolean) = update { putBoolean(KEY_HUB_ONLY, enabled) }

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
        private const val KEY_BANNER_DISMISSED = "banner_dismissed"

        @Volatile private var instance: PeekSettings? = null

        fun get(context: Context): PeekSettings = instance ?: synchronized(this) {
            instance ?: PeekSettings(
                context.applicationContext.getSharedPreferences("peek", Context.MODE_PRIVATE),
            ).also { instance = it }
        }
    }
}
