package com.salati.app.util

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import com.salati.app.data.Prefs

object Sound {

    private fun rawUri(ctx: Context, name: String): Uri? {
        val id = ctx.resources.getIdentifier(name, "raw", ctx.packageName)
        return if (id != 0) Uri.parse("android.resource://${ctx.packageName}/$id") else null
    }

    /** True when an adhan.mp3 was put into res/raw before building. */
    fun hasBundledAdhan(ctx: Context): Boolean =
        ctx.resources.getIdentifier("adhan", "raw", ctx.packageName) != 0

    /** Sounds to try, best first: the file picked in Settings, res/raw/adhan, then the phone's alarm sound. */
    fun adhanCandidates(ctx: Context): List<Uri> {
        val list = ArrayList<Uri>()
        Prefs(ctx).customAdhanUri?.let { list.add(Uri.parse(it)) }
        rawUri(ctx, "adhan")?.let { list.add(it) }
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)?.let { list.add(it) }
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)?.let { list.add(it) }
        return list
    }

    /** Short sound for the follow-up reminders: res/raw/reminder, else the default notification sound. */
    fun reminderUri(ctx: Context): Uri =
        rawUri(ctx, "reminder") ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
}
