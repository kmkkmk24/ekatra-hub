package com.kaushikkm.ekatrahub.peek

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.kaushikkm.ekatrahub.EkatraApp
import com.kaushikkm.ekatrahub.R
import com.kaushikkm.ekatrahub.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The LED on the SLEEPING screen.
 *
 * Apps can't draw on a sleeping screen, but Samsung's Always On Display shows notifications.
 * So while the screen sleeps and you have unread notifications, Ekatra posts one notification
 * (no sound, no vibration). The AOD shows it as Ekatra's icon with the unread count.
 *
 * The notification's text is the LED: 🔴. Experimental blink (made for AOD "Tap to show"):
 * every 20/30/60/120 s the dot is re-posted as a new notification, which wakes the Always On
 * Display for a few seconds to show it, then the AOD goes dark again.
 * While the screen is off Android puts apps to sleep, so blinking holds a "partial wake lock"
 * (processor awake, screen off), which costs a little battery (~1%/hour). To keep that bounded
 * it only blinks for 30 minutes after the newest notification arrives.
 */
class AodDotController(private val service: Service) {

    private val manager = NotificationManagerCompat.from(service)
    private val settings = PeekSettings.get(service)
    private val dao = (service.application as EkatraApp).database.notificationDao()
    private val power = service.getSystemService(PowerManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val screenOn = MutableStateFlow(power.isInteractive)
    private var blinkJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastUnread = 0
    private var wasScreenOn = true
    private var newestAt = 0L

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            screenOn.value = intent.action == Intent.ACTION_SCREEN_ON
        }
    }

    fun start() {
        createChannel()
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
            combine(settings.config, unreadByApp, screenOn, settings.refresh) { config, byApp, on, _ ->
                Triple(config, byApp, on)
            }.collect { (config, byApp, on) ->
                // Only notifications from the chosen LED apps count (or all, if none chosen).
                val unread = ledUnreadFor(config, byApp)
                if (unread > lastUnread) newestAt = System.currentTimeMillis()
                lastUnread = unread
                update(config, ledColorsFor(config, byApp), unread, on)
            }
        }
    }

    fun stop() {
        runCatching { service.unregisterReceiver(screenReceiver) }
        blinkJob?.cancel()
        releaseWakeLock()
        hideDot()
        scope.cancel()
    }

    private fun update(config: PeekConfig, colors: List<LedColor>, unread: Int, screenIsOn: Boolean) {
        blinkJob?.cancel()
        releaseWakeLock()
        val justSlept = wasScreenOn && !screenIsOn
        wasScreenOn = screenIsOn

        // The dot only exists while the screen is ASLEEP. While it's on, the on-screen LED does
        // the job - and a High-importance notification would pop up a banner at you.
        if (!config.aodDot || colors.isEmpty() || screenIsOn || !canPost()) {
            hideDot()
            return
        }
        // The screen just went to sleep: post the dot fresh, so the Always On Display treats it
        // as a brand-new notification.
        if (justSlept) hideDot()
        if (!config.aodBlink) {
            showDot(unread, colors)
            return
        }
        // BLINK BY WAKING THE AOD: with "Tap to show", Samsung's Always On Display only lights up
        // for a brand-new notification, shows it for a few seconds, then goes dark. So each
        // blink re-posts the dot as new: the AOD wakes, shows the red dot, then switches off -
        // a blink every 20/30/60/120 s. (Updating in place doesn't wake it.)
        val gapMs = config.aodBlinkGapSec * 1000L
        blinkJob = scope.launch {
            acquireWakeLock()
            try {
                showDot(unread, colors)
                while (isActive && System.currentTimeMillis() - newestAt < BLINK_FOR_MS) {
                    delay(gapMs)
                    hideDot()
                    showDot(unread, colors)
                }
            } finally {
                releaseWakeLock()
            }
        }
    }

    private fun canPost(): Boolean =
        manager.areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(service, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED)

    @SuppressLint("MissingPermission") // checked in canPost()
    private fun showDot(unread: Int, colors: List<LedColor>) {
        val open = PendingIntent.getActivity(
            service,
            0,
            Intent(service, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // Built to look like an ordinary message notification (as Telegram's does), because
        // Samsung's Always On Display leaves out:
        //  - "silent" notifications (so: Default importance, but the channel has no sound),
        //  - ongoing / status notifications (so: not ongoing, no "status" category),
        //  - empty ones (so: a title).
        // The title is the LED itself: one coloured-circle emoji per LED app with unread
        // notifications (e.g. 🟢 🔴). Emoji are drawn in their own colours, even though Samsung
        // draws notification text in white. (Samsung always shows the app's icon beside it.)
        val notification = NotificationCompat.Builder(service, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_led_dot)
            .setColor(colors.first().argb)
            .setContentTitle(colors.joinToString(" ") { it.emoji })
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setShowWhen(false)
            .setNumber(unread)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    private fun hideDot() {
        manager.cancel(NOTIFICATION_ID)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val system = service.getSystemService(NotificationManager::class.java)
        // v1 of this channel was "Low" importance, which Samsung treats as Silent and keeps off
        // the Always On Display. A channel's importance can't be raised after creation, so the
        // old one is deleted and a new "Default" one (alerting, but with no sound) is created.
        OLD_CHANNEL_IDS.forEach(system::deleteNotificationChannel)
        // v3: High importance, like the chat channels Samsung's Always On Display does show
        // (Default wasn't enough). Still no sound and no vibration.
        val channel = NotificationChannel(CHANNEL_ID, "Unread light", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "A dot on the lock screen and Always On Display while you have unread notifications"
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
            setSound(null, null)
        }
        system.createNotificationChannel(channel)
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Ekatra:AodBlink").apply {
            setReferenceCounted(false)
            acquire(BLINK_FOR_MS + 60_000) // hard safety limit, in case anything goes wrong
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private companion object {
        val OLD_CHANNEL_IDS = listOf("unread_light", "unread_light_v2")
        const val CHANNEL_ID = "unread_light_v3"
        const val NOTIFICATION_ID = 7001
        const val BLINK_FOR_MS = 30 * 60 * 1000L
    }
}
