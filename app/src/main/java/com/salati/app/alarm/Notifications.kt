package com.salati.app.alarm

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.salati.app.MainActivity
import com.salati.app.R
import com.salati.app.data.Prayer
import com.salati.app.util.Fmt
import com.salati.app.util.LocaleHelper
import com.salati.app.util.Sound
import java.time.LocalDate

object Notifications {
    const val CH_CALL = "call_v1"
    const val CH_REMIND = "remind_v1"
    const val ACTION_PRAYED = "com.salati.app.PRAYED"
    const val ACTION_WILL_PRAY = "com.salati.app.WILL_PRAY"

    fun id(p: Prayer): Int = 2000 + p.ordinal

    fun ensureChannels(ctx: Context) {
        val lc = LocaleHelper.localized(ctx)
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // The call channel is silent on purpose: AdhanService plays the adhan itself.
        val call = NotificationChannel(
            CH_CALL, lc.getString(R.string.channel_call), NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = lc.getString(R.string.channel_call_desc)
            setSound(null, null)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 600, 300, 600, 300, 600)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        val remind = NotificationChannel(
            CH_REMIND, lc.getString(R.string.channel_remind), NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = lc.getString(R.string.channel_remind_desc)
            setSound(
                Sound.reminderUri(ctx),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 400, 250, 400)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannels(listOf(call, remind))
    }

    private fun actionIntent(
        ctx: Context,
        action: String,
        prayer: Prayer,
        date: LocalDate,
        test: Boolean
    ): PendingIntent {
        val i = Intent(ctx, ActionReceiver::class.java)
            .setAction(action)
            .putExtra(Scheduler.EXTRA_PRAYER, prayer.name)
            .putExtra(Scheduler.EXTRA_DATE, date.toString())
            .putExtra(Scheduler.EXTRA_TEST, test)
        val code = 100 + prayer.ordinal * 2 + (if (action == ACTION_PRAYED) 0 else 1)
        return PendingIntent.getBroadcast(
            ctx, code, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    fun build(
        ctx: Context,
        prayer: Prayer,
        date: LocalDate,
        endMs: Long,
        channel: String,
        test: Boolean
    ): Notification {
        ensureChannels(ctx)
        val lc = LocaleHelper.localized(ctx)
        var title = lc.getString(R.string.notif_title, lc.getString(prayer.nameRes))
        if (test) title += " " + lc.getString(R.string.test_tag)
        val left = Fmt.duration(lc, endMs - System.currentTimeMillis())
        val text = lc.getString(R.string.notif_left, left)
        val open = PendingIntent.getActivity(
            ctx, 1, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .setAutoCancel(false)
            .addAction(0, lc.getString(R.string.action_prayed), actionIntent(ctx, ACTION_PRAYED, prayer, date, test))
            .addAction(0, lc.getString(R.string.action_will_pray), actionIntent(ctx, ACTION_WILL_PRAY, prayer, date, test))
            .build()
    }

    /** The adhan call at prayer time: a foreground service plays the sound and shows the buttons. */
    fun startCall(ctx: Context, prayer: Prayer, date: LocalDate, endMs: Long, test: Boolean = false) {
        val i = Intent(ctx, AdhanService::class.java)
            .putExtra(Scheduler.EXTRA_PRAYER, prayer.name)
            .putExtra(Scheduler.EXTRA_DATE, date.toString())
            .putExtra(Scheduler.EXTRA_END, endMs)
            .putExtra(Scheduler.EXTRA_TEST, test)
        try {
            ContextCompat.startForegroundService(ctx, i)
        } catch (e: Exception) {
            // Android refused to start the service (e.g. no exact-alarm permission): fall back to a normal notification.
            showReminder(ctx, prayer, date, endMs, test)
        }
    }

    /** A follow-up reminder: normal notification with the reminder sound. */
    fun showReminder(ctx: Context, prayer: Prayer, date: LocalDate, endMs: Long, test: Boolean = false) {
        post(ctx, id(prayer), build(ctx, prayer, date, endMs, CH_REMIND, test))
    }

    private fun post(ctx: Context, id: Int, n: Notification) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        try {
            NotificationManagerCompat.from(ctx).notify(id, n)
        } catch (e: SecurityException) {
            // notifications not allowed
        }
    }
}
