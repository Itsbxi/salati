package com.salati.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.salati.app.data.Prayer
import com.salati.app.data.Prefs
import java.time.LocalDate

/** Handles the two buttons on the notification: "I prayed" and "I will pray". */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val prayer = intent.getStringExtra(Scheduler.EXTRA_PRAYER)
            ?.let { n -> Prayer.values().firstOrNull { it.name == n } }
        val date = intent.getStringExtra(Scheduler.EXTRA_DATE)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val test = intent.getBooleanExtra(Scheduler.EXTRA_TEST, false)

        // Both buttons silence the adhan and clear the notification.
        app.stopService(Intent(app, AdhanService::class.java))
        if (prayer != null) NotificationManagerCompat.from(app).cancel(Notifications.id(prayer))

        // "I prayed" also stops all further reminders for this prayer.
        // "I will pray" keeps them coming (the next one is already planned).
        if (!test && prayer != null && date != null && intent.action == Notifications.ACTION_PRAYED) {
            Prefs(app).setPrayed(date, prayer, true)
        }
        Scheduler.scheduleNext(app)
    }
}
