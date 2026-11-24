package com.salati.app.util

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings

object Perms {

    /** Asks Android to stop battery saving from blocking the app's alarms. */
    fun requestIgnoreBattery(a: Activity) {
        try {
            a.startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${a.packageName}"))
            )
        } catch (e: Exception) {
            try {
                a.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (e2: Exception) {
                // nothing else to try
            }
        }
    }

    fun openNotificationSettings(a: Activity) {
        try {
            a.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, a.packageName)
            )
        } catch (e: Exception) {
            // ignore
        }
    }
}
