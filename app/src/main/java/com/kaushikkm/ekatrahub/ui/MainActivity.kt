package com.kaushikkm.ekatrahub.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import com.kaushikkm.ekatrahub.ui.settings.PeekBanner
import com.kaushikkm.ekatrahub.ui.settings.PeekSettingsScreen
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.kaushikkm.ekatrahub.ui.onboarding.DisclosureScreen
import com.kaushikkm.ekatrahub.ui.theme.EkatraTheme
import com.kaushikkm.ekatrahub.ui.timeline.TimelineScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // draw behind the status/navigation bars for a modern look
        setContent {
            EkatraTheme { EkatraRoot() }
        }
    }
}

@Composable
private fun EkatraRoot() {
    val context = LocalContext.current
    var hasAccess by remember { mutableStateOf(NotificationAccess.isGranted(context)) }

    // Re-check every time the app comes back to the foreground, e.g. returning from Settings.
    LifecycleResumeEffect(Unit) {
        hasAccess = NotificationAccess.isGranted(context)
        onPauseOrDispose { }
    }

    // A tiny two-screen "navigation": the timeline, or Peek settings.
    var showSettings by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = showSettings) { showSettings = false }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        val screen = when {
            !hasAccess -> Screen.Disclosure
            showSettings -> Screen.Settings
            else -> Screen.Timeline
        }
        Crossfade(targetState = screen, label = "root") { target ->
            when (target) {
                Screen.Disclosure -> DisclosureScreen(onAllow = { NotificationAccess.openSettings(context) })
                Screen.Settings -> PeekSettingsScreen(onBack = { showSettings = false })
                Screen.Timeline -> TimelineScreen(
                    onOpenSettings = { showSettings = true },
                    banner = { PeekBanner(onSetUp = { showSettings = true }) },
                )
            }
        }
    }
}

private enum class Screen { Disclosure, Timeline, Settings }
