package com.salati.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.salati.app.data.DayTimes
import com.salati.app.data.Prayer
import com.salati.app.data.PrayerStore
import com.salati.app.data.Prefs
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Decides when the next call / reminder must ring and sets ONE exact alarm for it.
 * Every time something changes (alarm fired, "I prayed" tapped, new times, reboot...)
 * scheduleNext() is simply called again and the plan is recomputed from scratch.
 */
object Scheduler {
    const val ACTION_ALARM = "com.salati.app.ALARM"
    const val EXTRA_KIND = "kind"
    const val EXTRA_PRAYER = "prayer"
    const val EXTRA_DATE = "date"
    const val EXTRA_END = "end"
    const val EXTRA_TEST = "test"
    private const val REQUEST_CODE = 7001

    enum class Kind { CALL, REMIND }

    data class Event(
        val kind: Kind,
        val prayer: Prayer,
        val day: DayTimes,
        val at: ZonedDateTime,
        val end: ZonedDateTime
    )

    /**
     * Gap until the next reminder: a quarter of the time left before the next prayer,
     * never less than 2 minutes and never more than 30 minutes.
     * So the closer the next prayer gets, the more often you are reminded.
     */
    fun intervalFor(remaining: Duration): Duration {
        val minutes = (remaining.toMinutes() / 4).coerceIn(2L, 30L)
        return Duration.ofMinutes(minutes)
    }

    /** First reminder after [now] in the series that starts at the call ([start]). Null when the time is over. */
    fun nextReminder(start: ZonedDateTime, end: ZonedDateTime, now: ZonedDateTime): ZonedDateTime? {
        var t = start
        var guard = 0
        while (!t.isAfter(now) && guard < 1000) {
            t = t.plus(intervalFor(Duration.between(t, end)))
            guard++
        }
        return if (t.isAfter(end.minusMinutes(1))) null else t
    }

    fun computeNext(ctx: Context, now: ZonedDateTime = PrayerStore(ctx).cityNow()): Event? {
        val prefs = Prefs(ctx)
        val store = PrayerStore(ctx)
        val today = now.toLocalDate()
        var best: Event? = null
        for (offset in -1L..1L) {
            val date = today.plusDays(offset)
            val day = store.getDay(date) ?: continue
            for (p in Prayer.values()) {
                if (!prefs.isPrayerEnabled(p) || prefs.isPrayed(date, p)) continue
                val start = day.start(p)
                var end = day.windowEnd(p)
                if (!end.isAfter(start)) end = start.plusMinutes(30)
                val ev: Event? = when {
                    now.isBefore(start) -> Event(Kind.CALL, p, day, start, end)
                    now.isBefore(end) ->
                        nextReminder(start, end, now)?.let { Event(Kind.REMIND, p, day, it, end) }
                    else -> null
                }
                val cur = best
                if (ev != null && (cur == null || ev.at.isBefore(cur.at))) best = ev
            }
        }
        return best
    }

    fun scheduleNext(context: Context) {
        val ctx = context.applicationContext
        val prefs = Prefs(ctx)
        if (!prefs.setupDone) return
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        prefs.pruneOld(LocalDate.now())
        val ev = try {
            computeNext(ctx)
        } catch (e: Exception) {
            null
        }
        if (ev == null) {
            am.cancel(pendingIntent(ctx, null))
            return
        }
        val pi = pendingIntent(ctx, ev)
        val at = ev.at.toInstant().toEpochMilli()
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun pendingIntent(ctx: Context, ev: Event?): PendingIntent {
        val intent = Intent(ctx, AlarmReceiver::class.java).setAction(ACTION_ALARM)
        if (ev != null) {
            intent.putExtra(EXTRA_KIND, ev.kind.name)
            intent.putExtra(EXTRA_PRAYER, ev.prayer.name)
            intent.putExtra(EXTRA_DATE, ev.day.date.toString())
            intent.putExtra(EXTRA_END, ev.end.toInstant().toEpochMilli())
        }
        return PendingIntent.getBroadcast(
            ctx,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
