package com.kaushikkm.ekatrahub.ui

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.kaushikkm.ekatrahub.service.HubListenerService

/** Checking and requesting the "Notification access" special permission. */
object NotificationAccess {

    fun isGranted(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    /**
     * Apps can't grant themselves this permission - there is no pop-up for it.
     * The best we can do is open the right Settings page so the user flips the switch.
     */
    fun openSettings(context: Context) {
        val fallback = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        val direct = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+: jump straight to Ekatra's own switch.
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(context, HubListenerService::class.java).flattenToString(),
            )
        } else {
            fallback
        }
        try {
            context.startActivity(direct)
        } catch (e: ActivityNotFoundException) {
            // Some phone brands remove the direct page; show the full list instead.
            context.startActivity(fallback)
        }
    }
}
