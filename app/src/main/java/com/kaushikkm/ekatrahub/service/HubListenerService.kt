package com.kaushikkm.ekatrahub.service

import android.app.Notification
import android.content.res.Configuration
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.kaushikkm.ekatrahub.EkatraApp
import com.kaushikkm.ekatrahub.data.NotificationEntity
import com.kaushikkm.ekatrahub.peek.AodDotController
import com.kaushikkm.ekatrahub.peek.LedController
import com.kaushikkm.ekatrahub.peek.PeekController
import com.kaushikkm.ekatrahub.peek.PeekSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * The heart of Ekatra. Once the user grants "Notification access", Android starts this
 * service and calls onNotificationPosted() for EVERY notification from EVERY app.
 *
 * Security note (why Android guards this so heavily): this permission can see OTPs,
 * bank alerts and private messages. Banking trojans abuse it, which is why the user has
 * to enable it manually in Settings, why Play requires a disclosure screen, and why
 * Ekatra has no internet permission at all.
 */
class HubListenerService : NotificationListenerService() {

    // Background scope: database writes never run on the main thread.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dao by lazy { (application as EkatraApp).database.notificationDao() }
    private val labelCache = ConcurrentHashMap<String, String>()
    private val settings by lazy { PeekSettings.get(this) }

    /** The Peek edge handle + Hub overlay. Lives as long as Android keeps this listener connected. */
    private var peek: PeekController? = null

    /** The on-screen "BlackBerry LED". Same lifetime as Peek. */
    private var led: LedController? = null

    /** The LED on the sleeping screen (Always On Display), via a dot-only notification. */
    private var aodDot: AodDotController? = null

    /** Called when access is granted (and after reboots): import what's already in the shade. */
    override fun onListenerConnected() {
        NotificationActions.attach(this)
        if (peek == null) peek = PeekController(this).also { it.start() }
        if (led == null) led = LedController(this).also { it.start() }
        if (aodDot == null) aodDot = AodDotController(this).also { it.start() }
        val active = runCatching { activeNotifications }.getOrNull() ?: return
        active.forEach(NotificationActions::remember)
        val entities = active.mapNotNull { it.toEntityOrNull() }
        scope.launch { dao.insertAll(entities) }
    }

    override fun onListenerDisconnected() {
        NotificationActions.detach()
        peek?.stop()
        peek = null
        led?.stop()
        led = null
        aodDot?.stop()
        aodDot = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // Remember the tap action FIRST - Hub-only mode may remove it from the shade below.
        NotificationActions.remember(sbn)
        // After you reply, chat apps re-post the notification showing YOUR message ("You: Hi").
        // That's not a new notification for you, so don't add it to the timeline.
        val ownReply = isOwnReply(sbn)
        val entity = if (ownReply) null else sbn.toEntityOrNull()
        if (entity != null) scope.launch { dao.insert(entity) }

        if (settings.config.value.hubOnly && shouldHideFromShade(sbn, saved = entity != null || ownReply)) {
            dismissFromShade(sbn.key)
        }
    }

    /**
     * A notification left the shade. If the APP removed it (you read the chat in WhatsApp) or you
     * tapped it there, it's been seen - mark it read in Ekatra too. Removals Ekatra itself did
     * (Hub-only mode) or swipe-aways in the shade don't count as reading.
     */
    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap, reason: Int) {
        if (reason == REASON_APP_CANCEL || reason == REASON_APP_CANCEL_ALL || reason == REASON_CLICK) {
            scope.launch { dao.markReadByKey(sbn.key) }
        }
    }

    /** Removes a notification from the phone's notification shade / status bar / lock screen. */
    internal fun dismissFromShade(key: String) {
        runCatching { cancelNotification(key) }
    }

    /**
     * Hub-only mode safety rules. We only ever remove a notification from the shade if
     * Ekatra saved it, and NEVER remove: calls, alarms, timers, navigation, media players,
     * ongoing/progress ones, system notifications, or anything that opens full-screen.
     */
    private fun shouldHideFromShade(sbn: StatusBarNotification, saved: Boolean): Boolean {
        val n = sbn.notification
        if (!sbn.isClearable) return false
        if (sbn.packageName == packageName || sbn.packageName in SYSTEM_PACKAGES) return false
        if (n.fullScreenIntent != null) return false
        if (n.category in ALWAYS_SHOW_CATEGORIES) return false
        val isGroupSummary = n.flags and Notification.FLAG_GROUP_SUMMARY != 0
        return saved || isGroupSummary
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        peek?.refresh()
        led?.refresh()
    }

    override fun onDestroy() {
        peek?.stop()
        peek = null
        led?.stop()
        led = null
        aodDot?.stop()
        aodDot = null
        scope.cancel()
        super.onDestroy()
    }

    private fun StatusBarNotification.toEntityOrNull(): NotificationEntity? {
        if (packageName == this@HubListenerService.packageName) return null
        val n = notification
        // Skip noise: group summaries ("3 new messages") duplicate their children,
        // and ongoing ones (music player, downloads, navigation) aren't messages.
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return null
        if (isOngoing) return null

        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty().trim()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty().trim()
        if (title.isEmpty() && text.isEmpty()) return null

        return NotificationEntity(
            fullText = fullTextOf(n, text),
            hadReply = NotificationActions.hasReplyAction(n),
            packageName = packageName,
            appLabel = labelFor(packageName),
            title = title,
            text = text,
            subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
            category = n.category,
            postedAt = postTime,
            whenTime = if (n.`when` > 0) n.`when` else postTime,
            sbnKey = key,
        )
    }

    /**
     * Collects everything readable in a notification, beyond its one-line text:
     *  - chat apps (MessagingStyle): every message in the conversation, "Name: message"
     *  - inbox-style (Gmail, SMS summaries): every line
     *  - summary / sub-text lines
     * Returns null if there's nothing more than [text].
     */
    private fun fullTextOf(n: Notification, text: String): String? {
        val extras = n.extras
        val parts = mutableListOf<String>()
        val messages = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)?.messages.orEmpty()
        if (messages.size > 1) {
            messages.forEach { m -> parts += "${m.person?.name ?: "You"}: ${m.text}" }
        } else {
            parts += text
            extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach { parts += it.toString() }
            extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.let { parts += it.toString() }
            extras.getCharSequence(Notification.EXTRA_INFO_TEXT)?.let { parts += it.toString() }
        }
        val full = parts.map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString("\n")
        return full.takeIf { it.length > text.length }
    }

    /** Is this re-post just showing a reply YOU sent (from Ekatra or the app)? */
    @Suppress("DEPRECATION")
    private fun isOwnReply(sbn: StatusBarNotification): Boolean {
        val n = sbn.notification
        val extras = n.extras
        val text = (extras.getCharSequence(Notification.EXTRA_TEXT) ?: "").toString().trim()
        if (text.isNotEmpty() && NotificationActions.wasJustSent(sbn.key, text)) return true
        // Android's own record of replies typed into this notification.
        val history = extras.getCharSequenceArray(Notification.EXTRA_REMOTE_INPUT_HISTORY)
        if (text.isNotEmpty() && history?.any { it.toString().trim() == text } == true) return true
        // Chat apps: the newest message is from you (no sender, or the "user" person).
        // Only trusted when other messages DO name their senders - some apps leave every
        // sender blank, and we must never hide a real incoming message by mistake.
        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n) ?: return false
        val messages = style.messages
        val last = messages.lastOrNull() ?: return false
        fun isMe(m: NotificationCompat.MessagingStyle.Message): Boolean {
            val p = m.person ?: return true
            return (p.key != null && p.key == style.user.key) || (p.name != null && p.name == style.user.name)
        }
        return isMe(last) && messages.any { !isMe(it) }
    }

    /** "com.whatsapp" -> "WhatsApp". Cached because the lookup is relatively slow. */
    private fun labelFor(pkg: String): String = labelCache.getOrPut(pkg) {
        runCatching {
            val pm = packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
    }

    private companion object {
        val SYSTEM_PACKAGES = setOf("android", "com.android.systemui", "com.android.phone", "com.android.server.telecom")
        val ALWAYS_SHOW_CATEGORIES = setOf(
            Notification.CATEGORY_CALL,
            Notification.CATEGORY_ALARM,
            Notification.CATEGORY_STOPWATCH,
            Notification.CATEGORY_NAVIGATION,
            Notification.CATEGORY_TRANSPORT,
            Notification.CATEGORY_SERVICE,
            Notification.CATEGORY_PROGRESS,
            Notification.CATEGORY_SYSTEM,
        )
    }
}
