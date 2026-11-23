package com.kaushikkm.ekatrahub.service

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.notification.StatusBarNotification
import android.util.Log
import com.kaushikkm.ekatrahub.data.NotificationEntity
import java.lang.ref.WeakReference

/**
 * Remembers what tapping each notification should do, so tapping it in Ekatra opens the exact
 * chat / email / screen - just like tapping it in the notification shade.
 *
 * Why only in memory? The "what to open" is a PendingIntent: a sealed token created by the other
 * app that only Android can redeem. It can't be saved to a database. After a phone restart we
 * fall back to opening the app's home screen.
 */
object NotificationActions {

    private class Entry(
        val contentIntent: PendingIntent?,
        val autoCancel: Boolean,
        /** The app's own "Mark as read" button (WhatsApp, Telegram, Gmail... provide one). */
        val markReadIntent: PendingIntent?,
        /** The app's own "Reply" button with a text box (RemoteInput), if it has one. */
        val replyAction: Notification.Action?,
    )

    private fun findReplyAction(n: Notification): Notification.Action? {
        val actions = n.actions ?: return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            actions.firstOrNull { it.semanticAction == Notification.Action.SEMANTIC_ACTION_REPLY }
                ?.takeIf { !it.remoteInputs.isNullOrEmpty() }
                ?.let { return it }
        }
        return actions.firstOrNull { action -> action.remoteInputs?.any { it.allowFreeFormInput } == true }
    }

    internal fun hasReplyAction(n: Notification): Boolean = findReplyAction(n) != null

    // Replies sent from Ekatra in the last minute, so their echo isn't added as a notification.
    private val recentReplies = ArrayDeque<Triple<String, String, Long>>()

    private fun recordSent(key: String, text: String) = synchronized(recentReplies) {
        recentReplies.addLast(Triple(key, text.trim(), System.currentTimeMillis()))
        while (recentReplies.size > 20) recentReplies.removeFirst()
    }

    internal fun wasJustSent(key: String, text: String): Boolean = synchronized(recentReplies) {
        val cutoff = System.currentTimeMillis() - 60_000
        recentReplies.any { (k, t, at) -> k == key && t == text && at >= cutoff }
    }

    fun canReply(item: NotificationEntity): Boolean =
        synchronized(entries) { entries[item.sbnKey]?.replyAction != null }

    /**
     * In-place reply - the same mechanism the notification shade's reply box uses (RemoteInput):
     * we fill in the app's reply form and press its Reply button. The app sends the message.
     */
    fun reply(context: Context, item: NotificationEntity, text: String): Boolean {
        val action = synchronized(entries) { entries[item.sbnKey]?.replyAction } ?: return false
        val inputs = action.remoteInputs ?: return false
        val results = Bundle().apply { inputs.forEach { putCharSequence(it.resultKey, text) } }
        val fillIn = Intent()
        RemoteInput.addResultsToIntent(inputs, fillIn, results)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            RemoteInput.setResultsSource(fillIn, RemoteInput.SOURCE_FREE_FORM_INPUT)
        }
        return try {
            recordSent(item.sbnKey, text)
            action.actionIntent.send(context, 0, fillIn)
            true
        } catch (e: PendingIntent.CanceledException) {
            false
        }
    }

    private val MARK_READ_TITLE = Regex("""\bmark\b.*\bread\b""", RegexOption.IGNORE_CASE)

    private fun findMarkReadAction(n: Notification): PendingIntent? {
        val actions = n.actions ?: return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            actions.firstOrNull { it.semanticAction == Notification.Action.SEMANTIC_ACTION_MARK_AS_READ }
                ?.let { return it.actionIntent }
        }
        // Older apps don't label their buttons for machines, so fall back to the button text.
        return actions.firstOrNull { action ->
            action.remoteInputs.isNullOrEmpty() && MARK_READ_TITLE.containsMatchIn(action.title ?: "")
        }?.actionIntent
    }

    // Keeps the most recent 500; the oldest are forgotten first.
    private val entries = object : LinkedHashMap<String, Entry>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?) = size > 500
    }

    private var serviceRef = WeakReference<HubListenerService>(null)

    internal fun attach(service: HubListenerService) {
        serviceRef = WeakReference(service)
    }

    internal fun detach() {
        serviceRef.clear()
    }

    internal fun remember(sbn: StatusBarNotification) {
        val n = sbn.notification
        val entry = Entry(
            contentIntent = n.contentIntent,
            autoCancel = n.flags and Notification.FLAG_AUTO_CANCEL != 0,
            markReadIntent = findMarkReadAction(n),
            replyAction = findReplyAction(n),
        )
        synchronized(entries) { entries[sbn.key] = entry }
    }

    /** Removes a notification from the phone's notification shade, if it's still there. */
    fun dismissFromShade(key: String) {
        serviceRef.get()?.dismissFromShade(key)
    }

    /**
     * Marks a notification as read at the source when possible: presses the app's own
     * "Mark as read" button (so WhatsApp etc. show it as read too), then clears it from the shade.
     */
    fun markReadAtSource(item: NotificationEntity) {
        val entry = synchronized(entries) { entries[item.sbnKey] }
        entry?.markReadIntent?.let { runCatching { it.send() } }
        serviceRef.get()?.dismissFromShade(item.sbnKey)
    }

    /** Opens what this notification points to. Returns false if nothing could be opened. */
    fun open(context: Context, item: NotificationEntity): Boolean {
        val entry = synchronized(entries) { entries[item.sbnKey] }
        val sent = entry?.contentIntent?.let { pendingIntent ->
            try {
                pendingIntent.send(context, 0, null, null, null, null, launchOptions())
                true
            } catch (e: PendingIntent.CanceledException) {
                false // the app withdrew it (e.g. the message was already read elsewhere)
            }
        } ?: false

        if (sent) {
            // Behave like a normal tap: notifications that disappear when tapped, disappear.
            if (entry?.autoCancel == true) serviceRef.get()?.dismissFromShade(item.sbnKey)
            return true
        }

        // Fallback: open the app itself.
        val launch = context.packageManager.getLaunchIntentForPackage(item.packageName) ?: return false
        return try {
            context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: RuntimeException) {
            Log.w("EkatraOpen", "Could not open ${item.packageName}", e)
            false
        }
    }

    /**
     * Android 14+ blocks apps from popping up screens while in the background unless they
     * explicitly allow it. Ekatra is visible (the Hub is on screen) when you tap, so we allow it.
     */
    @Suppress("DEPRECATION")
    private fun launchOptions(): Bundle? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ActivityOptions.makeBasic().apply {
                setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            }.toBundle()
        } else {
            null
        }
}
