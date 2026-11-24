package com.salati.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.salati.app.data.Prayer
import com.salati.app.data.Prefs
import java.time.LocalDate

/** Rings when the exact alarm set by Scheduler goes off. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val prayer = intent.getStringExtra(Scheduler.EXTRA_PRAYER)
            ?.let { n -> Prayer.values().firstOrNull { it.name == n } }
        val date = intent.getStringExtra(Scheduler.EXTRA_DATE)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val endMs = intent.getLongExtra(Scheduler.EXTRA_END, 0L)
        val kind = intent.getStringExtra(Scheduler.EXTRA_KIND)

        if (prayer != null && date != null) {
            val prefs = Prefs(app)
            if (prefs.isPrayerEnabled(prayer) && !prefs.isPrayed(date, prayer)) {
                if (kind == Scheduler.Kind.CALL.name) {
                    Notifications.startCall(app, prayer, date, endMs)
                } else {
                    Notifications.showReminder(app, prayer, date, endMs)
                }
            }
        }
        // Plan the next call/reminder.
        Scheduler.scheduleNext(app)
    }
}
