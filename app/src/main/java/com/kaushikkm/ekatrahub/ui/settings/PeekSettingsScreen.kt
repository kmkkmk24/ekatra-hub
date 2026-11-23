package com.kaushikkm.ekatrahub.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaushikkm.ekatrahub.peek.PeekEdge
import com.kaushikkm.ekatrahub.peek.PeekSettings

private fun canDrawOverlays(context: Context) = Settings.canDrawOverlays(context)

private fun batteryUnrestricted(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeekSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { PeekSettings.get(context) }
    val config by settings.config.collectAsStateWithLifecycle()

    var canDraw by remember { mutableStateOf(canDrawOverlays(context)) }
    var batteryOk by remember { mutableStateOf(batteryUnrestricted(context)) }
    var showOverlayDisclosure by rememberSaveable { mutableStateOf(false) }
    var enableWhenGranted by rememberSaveable { mutableStateOf(false) }

    // Coming back from Android's Settings: re-check permissions and finish turning Peek on.
    LifecycleResumeEffect(Unit) {
        canDraw = canDrawOverlays(context)
        batteryOk = batteryUnrestricted(context)
        if (enableWhenGranted && canDraw) {
            settings.setEnabled(true)
            enableWhenGranted = false
            showOverlayDisclosure = false
        }
        settings.requestRefresh()
        onPauseOrDispose { }
    }

    val peekOn = config.enabled && canDraw

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Swipe in from the handle on the edge of your screen to peek at Ekatra from any app. " +
                    "Let go early to go back to what you were doing, or keep sliding to open it fully. " +
                    "A tap on the handle opens it straight away.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Peek handle", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (peekOn) "On" else "Off",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = peekOn,
                    onCheckedChange = { on ->
                        when {
                            !on -> settings.setEnabled(false)
                            canDraw -> settings.setEnabled(true)
                            else -> showOverlayDisclosure = true
                        }
                    },
                )
            }

            if (showOverlayDisclosure && !canDraw) {
                OverlayDisclosureCard(
                    onAllow = {
                        enableWhenGranted = true
                        context.startActivity(
                            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")),
                        )
                    },
                    onCancel = { showOverlayDisclosure = false },
                )
            }

            if (peekOn) {
                Text("Edge", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = config.edge == PeekEdge.LEFT,
                        onClick = { settings.setEdge(PeekEdge.LEFT) },
                        label = { Text("Left") },
                    )
                    FilterChip(
                        selected = config.edge == PeekEdge.RIGHT,
                        onClick = { settings.setEdge(PeekEdge.RIGHT) },
                        label = { Text("Right") },
                    )
                }

                Text("Position on the edge", style = MaterialTheme.typography.titleSmall)
                Slider(value = config.position, onValueChange = settings::setPosition)

                Text("Handle length", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = config.lengthDp.toFloat(),
                    onValueChange = { settings.setLength(it.toInt()) },
                    valueRange = PeekSettings.MIN_LENGTH.toFloat()..PeekSettings.MAX_LENGTH.toFloat(),
                )
                Text(
                    "The handle moves as you adjust these, so you can see it on the side of the screen. " +
                        "Android lets an app claim only about 200 dp of an edge from the Back gesture, so " +
                        "on a very long handle, swipes near its ends may trigger Back instead - start from the middle.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

            }

            HorizontalDivider()
            HubOnlySection(
                enabled = config.hubOnly,
                onEnable = { settings.setHubOnly(true) },
                onDisable = { settings.setHubOnly(false) },
            )

            if (peekOn || config.hubOnly) {
                if (!batteryOk) {
                    InfoCard(
                        title = "Keep Ekatra reliable",
                        body = "Some phones (especially Samsung, Xiaomi and OnePlus) stop background apps to save " +
                            "battery, which can make the handle disappear or notifications go missing. On the " +
                            "next screen tap Battery and choose Unrestricted.",
                    ) {
                        OutlinedButton(onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
                            )
                        }) { Text("Open battery settings") }
                    }
                }
            }
        }
    }
}

/**
 * Hub-only mode: after Ekatra saves a notification, it removes it from the phone's notification
 * shade, so the Hub becomes the one place your notifications live.
 */
@Composable
private fun HubOnlySection(enabled: Boolean, onEnable: () -> Unit, onDisable: () -> Unit) {
    var confirming by rememberSaveable { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Hub-only mode", style = MaterialTheme.typography.titleMedium)
            Text(
                "Keep notifications in Ekatra only, not in the phone's notification shade",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = { on -> if (on) confirming = true else onDisable() },
        )
    }

    if (confirming && !enabled) {
        InfoCard(
            title = "Before you turn this on",
            body = "• New notifications are saved in Ekatra, then removed from the notification shade, " +
                "status bar and lock screen. They may flash at the top for a moment first.\n" +
                "• Sounds and vibration still play as normal.\n" +
                "• Calls, alarms, timers, navigation, music players and system alerts are never removed.\n" +
                "• Use Peek to check your notifications from anywhere.\n" +
                "• Turning this off later won't bring back ones already removed - they stay in Ekatra.",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    onEnable()
                    confirming = false
                }) { Text("Turn on") }
                TextButton(onClick = { confirming = false }) { Text("Cancel") }
            }
        }
    }
}

/** Prominent disclosure for "Display over other apps", shown before sending the user to Settings. */
@Composable
private fun OverlayDisclosureCard(onAllow: () -> Unit, onCancel: () -> Unit) {
    InfoCard(
        title = "One more permission",
        body = "To show the handle and slide the Hub over other apps, Android needs you to allow " +
            "\"Display over other apps\" for Ekatra. This only lets Ekatra draw on top - it can't see or " +
            "read what other apps show.\n\nOn the next screen, find Ekatra, switch it on, then come back.",
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onAllow) { Text("Allow") }
            TextButton(onClick = onCancel) { Text("Not now") }
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String, actions: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            actions()
        }
    }
}

/** Shown at the top of the timeline until Peek is set up (or dismissed). */
@Composable
fun PeekBanner(onSetUp: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { PeekSettings.get(context) }
    val config by settings.config.collectAsStateWithLifecycle()
    val dismissed by settings.bannerDismissed.collectAsStateWithLifecycle()
    if (config.enabled || dismissed) return

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Open Ekatra from any app", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("Turn on Peek: swipe in from the screen edge.", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onSetUp) { Text("Set up") }
            IconButton(onClick = settings::dismissBanner) {
                Icon(Icons.Filled.Close, contentDescription = "Dismiss")
            }
        }
    }
}
