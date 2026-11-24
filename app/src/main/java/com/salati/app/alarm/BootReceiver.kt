package com.salati.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.salati.app.data.Prefs
import com.salati.app.work.RefreshWorker

/** Re-plans the alarms after a reboot, app update, clock/time-zone change or a new alarm permission. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        if (!Prefs(app).setupDone) return
        RefreshWorker.schedulePeriodic(app)
        Scheduler.scheduleNext(app)
    }
}
