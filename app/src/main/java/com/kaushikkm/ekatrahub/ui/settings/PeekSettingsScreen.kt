package com.kaushikkm.ekatrahub.ui.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.kaushikkm.ekatrahub.EkatraApp
import com.kaushikkm.ekatrahub.peek.AOD_BLINK_GAPS
import com.kaushikkm.ekatrahub.peek.MAX_LED_APPS
import com.kaushikkm.ekatrahub.peek.PeekConfig
import com.kaushikkm.ekatrahub.ui.timeline.AppIcon
import com.kaushikkm.ekatrahub.peek.LedColor
import com.kaushikkm.ekatrahub.peek.LedPosition
import com.kaushikkm.ekatrahub.peek.PeekEdge
import com.kaushikkm.ekatrahub.peek.PeekSettings

private fun canDrawOverlays(context: Context) = Settings.canDrawOverlays(context)

private const val FEATURE_PEEK = "peek"
private const val FEATURE_LED = "led"

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
    // Which feature ("peek" or "led") is waiting for the "Display over other apps" permission.
    var pendingFeature by rememberSaveable { mutableStateOf<String?>(null) }

    // Coming back from Android's Settings: re-check permissions and finish turning the feature on.
    LifecycleResumeEffect(Unit) {
        canDraw = canDrawOverlays(context)
        batteryOk = batteryUnrestricted(context)
        if (canDraw) {
            when (pendingFeature) {
                FEATURE_PEEK -> settings.setEnabled(true)
                FEATURE_LED -> settings.setLedEnabled(true)
            }
            pendingFeature = null
        }
        settings.requestRefresh()
        onPauseOrDispose { }
    }

    val peekOn = config.enabled && canDraw
    val ledOn = config.ledEnabled && canDraw
    val askOverlay: @Composable (String) -> Unit = { feature ->
        if (pendingFeature == feature && !canDraw) {
            OverlayDisclosureCard(
                onAllow = {
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")),
                    )
                },
                onCancel = { pendingFeature = null },
            )
        }
    }

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Peek", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Swipe in from the edge handle to see Ekatra over any app. " +
                            "Let go to return, slide further to open, or tap the handle.",
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
                            else -> pendingFeature = FEATURE_PEEK
                        }
                    },
                )
            }

            askOverlay(FEATURE_PEEK)

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

                Text("Height", style = MaterialTheme.typography.titleSmall)
                Slider(value = config.position, onValueChange = settings::setPosition)

                Text("Length", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = config.lengthDp.toFloat(),
                    onValueChange = { settings.setLength(it.toInt()) },
                    valueRange = PeekSettings.MIN_LENGTH.toFloat()..PeekSettings.MAX_LENGTH.toFloat(),
                )
                Text(
                    "On a long handle, start your swipe near the middle. Near the ends, Back may trigger instead.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider()

            // ---- Notification LED ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Notification LED", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Blinks at the top of the screen while you have unread notifications.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = ledOn,
                    onCheckedChange = { on ->
                        when {
                            !on -> settings.setLedEnabled(false)
                            canDraw -> settings.setLedEnabled(true)
                            else -> pendingFeature = FEATURE_LED
                        }
                    },
                )
            }

            askOverlay(FEATURE_LED)

            if (ledOn || config.aodDot) {
                LedAppsSection(config = config, settings = settings)
            }

            if (ledOn) {
                Text("Position", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LedPosition.entries.forEach { position ->
                        FilterChip(
                            selected = config.ledPosition == position,
                            onClick = { settings.setLedPosition(position) },
                            label = { Text(position.label, maxLines = 1) },
                        )
                    }
                }
            }

            SleepingScreenLed(
                enabled = config.aodDot,
                blink = config.aodBlink,
                blinkGapSec = config.aodBlinkGapSec,
                onEnabledChange = settings::setAodDot,
                onBlinkChange = settings::setAodBlink,
                onBlinkGapChange = settings::setAodBlinkGap,
            )

            HorizontalDivider()
            HubOnlySection(
                enabled = config.hubOnly,
                onEnable = { settings.setHubOnly(true) },
                onDisable = { settings.setHubOnly(false) },
            )

            if (peekOn || ledOn || config.hubOnly) {
                if (!batteryOk) {
                    InfoCard(
                        title = "Keep Ekatra running",
                        body = "Tap below, then Battery › Unrestricted.",
                    ) {
                        OutlinedButton(onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
                            )
                        }) { Text("Open app settings") }
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
                "Notifications live only in Ekatra. They're cleared from the notification shade.",
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
            title = "Turn on Hub-only mode?",
            body = "• New notifications are cleared from the shade, status bar and lock screen.\n" +
                "• Sounds and vibration still play.\n" +
                "• Calls, alarms, timers, navigation and music are never cleared.\n" +
                "• Cleared notifications stay in Ekatra, even if you turn this off later.",
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

/**
 * The LED on the sleeping screen: a dot-only notification that Samsung's Always On Display and
 * the lock screen show. Android 13+ requires asking before an app posts any notification.
 */
@Composable
private fun SleepingScreenLed(
    enabled: Boolean,
    blink: Boolean,
    blinkGapSec: Int,
    onEnabledChange: (Boolean) -> Unit,
    onBlinkChange: (Boolean) -> Unit,
    onBlinkGapChange: (Int) -> Unit,
) {
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onEnabledChange(true)
    }
    val context = LocalContext.current

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("LED on the sleeping screen", style = MaterialTheme.typography.titleMedium)
            Text(
                "Shows your LED colours on the Always On Display while the screen is off.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = { on ->
                val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                when {
                    !on -> onEnabledChange(false)
                    needsPermission -> askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else -> onEnabledChange(true)
                }
            },
        )
    }

    if (enabled) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Blink", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Lights up the dots on the sleeping screen at regular intervals.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = blink, onCheckedChange = onBlinkChange)
        }
        if (blink) {
            Text("Blink every", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AOD_BLINK_GAPS.forEach { seconds ->
                    FilterChip(
                        selected = blinkGapSec == seconds,
                        onClick = { onBlinkGapChange(seconds) },
                        label = { Text("${seconds}s") },
                    )
                }
            }
        }
        // The one phone setting this needs, depending on whether Blink is on.
        InfoCard(
            title = "Set up your phone",
            body = "Settings › Lock screen and AOD › Always On Display › When to show › " +
                if (blink) "Tap to show" else "Show always",
        ) {}
    }
}

/**
 * Which apps light the LED, each with its own colour (up to 5). Used by both the on-screen LED
 * and the sleeping-screen dot. With none chosen, every app lights it in one colour.
 */
@Composable
private fun LedAppsSection(config: PeekConfig, settings: PeekSettings) {
    val context = LocalContext.current
    val knownApps by remember {
        (context.applicationContext as EkatraApp).database.notificationDao().observeApps()
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    var picking by remember { mutableStateOf(false) }
    // Names for chosen apps that haven't sent Ekatra a notification yet.
    val installedLabels = rememberInstalledApps().toMap()

    Text("LED apps", style = MaterialTheme.typography.titleSmall)
    Text(
        if (config.ledApps.isEmpty()) {
            "All apps light the LED in this colour. Choose up to $MAX_LED_APPS apps to give each its own colour."
        } else {
            "Only these apps light the LED. With several unread, it shows each colour in turn."
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (config.ledApps.isEmpty()) {
        // One colour for everything.
        ColorSwatches(selected = config.ledColor, onSelect = settings::setLedColor)
    } else {
        config.ledApps.forEach { ledApp ->
            val label = knownApps.firstOrNull { it.packageName == ledApp.packageName }?.appLabel
                ?: installedLabels[ledApp.packageName] ?: ledApp.packageName
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(ledApp.packageName, label, size = 28.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        IconButton(onClick = { settings.removeLedApp(ledApp.packageName) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove $label")
                        }
                    }
                    ColorSwatches(
                        selected = ledApp.color,
                        onSelect = { settings.setLedAppColor(ledApp.packageName, it) },
                    )
                }
            }
        }
    }

    if (config.ledApps.size < MAX_LED_APPS) {
        OutlinedButton(onClick = { picking = true }) {
            Text(if (config.ledApps.isEmpty()) "Choose apps" else "Add app (${config.ledApps.size}/$MAX_LED_APPS)")
        }
    } else {
        Text(
            "Maximum $MAX_LED_APPS apps. Remove one to add another.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (picking) {
        AppPickerDialog(
            exclude = config.ledApps.map { it.packageName }.toSet(),
            recent = knownApps.map { it.packageName to it.appLabel },
            onPick = { packageName ->
                // Give each new app the first colour not used yet.
                val used = config.ledApps.map { it.color }.toSet()
                val color = LedColor.entries.firstOrNull { it !in used } ?: LedColor.RED
                settings.addLedApp(packageName, color)
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

/** Every app on the phone that has a launcher icon: package name to label, A-Z. */
@Composable
private fun rememberInstalledApps(): List<Pair<String, String>> {
    val context = LocalContext.current
    val apps by produceState(initialValue = emptyList<Pair<String, String>>()) {
        value = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(launcher, 0)
                .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
                .filter { it.first != context.packageName }
                .distinctBy { it.first }
                .sortedBy { it.second.lowercase() }
        }
    }
    return apps
}

/**
 * Pick an app to light the LED: every installed app, with a search box. Apps Ekatra has
 * already received notifications from are listed first.
 */
@Composable
private fun AppPickerDialog(
    exclude: Set<String>,
    recent: List<Pair<String, String>>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val installed = rememberInstalledApps()
    var query by remember { mutableStateOf("") }
    val q = query.trim().lowercase()
    fun List<Pair<String, String>>.matching() =
        filter { it.first !in exclude && (q.isEmpty() || it.second.lowercase().contains(q)) }

    val recentShown = recent.matching()
    val recentPackages = recentShown.map { it.first }.toSet()
    val othersShown = installed.matching().filter { it.first !in recentPackages }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose an app") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search apps") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    if (recentShown.isNotEmpty()) {
                        item(key = "h-recent") { PickerHeader("In Ekatra") }
                        items(recentShown, key = { "r-" + it.first }) { (pkg, label) ->
                            PickerRow(pkg, label) { onPick(pkg) }
                        }
                    }
                    item(key = "h-all") { PickerHeader(if (installed.isEmpty()) "Loading apps…" else "All apps") }
                    items(othersShown, key = { "a-" + it.first }) { (pkg, label) ->
                        PickerRow(pkg, label) { onPick(pkg) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun PickerHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun PickerRow(packageName: String, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(packageName, label, size = 32.dp)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** A row of colour circles to pick an LED colour. */
@Composable
private fun ColorSwatches(selected: LedColor, onSelect: (LedColor) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        LedColor.entries.forEach { color ->
            val isSelected = color == selected
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent)
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(Color(color.argb))
                    .clickable(onClickLabel = color.label) { onSelect(color) }
                    .semantics { contentDescription = "${color.label}${if (isSelected) ", selected" else ""}" },
            )
        }
    }
}

/** Prominent disclosure for "Display over other apps", shown before sending the user to Settings. */
@Composable
private fun OverlayDisclosureCard(onAllow: () -> Unit, onCancel: () -> Unit) {
    InfoCard(
        title = "Allow \"Appear on top\"",
        body = "Ekatra needs this to show the handle, Hub and LED over other apps. " +
            "It can't see what's on your screen.\n\nOn the next screen, switch on Ekatra, then come back.",
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
                Text("Turn on Peek and swipe in from the edge.", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onSetUp) { Text("Set up") }
            IconButton(onClick = settings::dismissBanner) {
                Icon(Icons.Filled.Close, contentDescription = "Dismiss")
            }
        }
    }
}
