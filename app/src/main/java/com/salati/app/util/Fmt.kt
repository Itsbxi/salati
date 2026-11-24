package com.salati.app.util

import android.content.Context
import android.text.format.DateFormat
import com.salati.app.R
import java.time.LocalTime
import java.util.Locale

/** Formatting helpers. Digits are always 0-9 so Arabic and English screens look the same. */
object Fmt {

    fun time(ctx: Context, t: LocalTime): String {
        if (DateFormat.is24HourFormat(ctx)) {
            return String.format(Locale.US, "%02d:%02d", t.hour, t.minute)
        }
        val h = if (t.hour % 12 == 0) 12 else t.hour % 12
        val suffix = ctx.getString(if (t.hour < 12) R.string.am else R.string.pm)
        return String.format(Locale.US, "%d:%02d %s", h, t.minute, suffix)
    }

    fun duration(ctx: Context, millis: Long): String {
        val totalMin = if (millis <= 0) 0L else (millis + 59_999L) / 60_000L
        val h = totalMin / 60
        val m = totalMin % 60
        return when {
            h > 0 && m > 0 -> ctx.getString(R.string.dur_h_m, h.toString(), m.toString())
            h > 0 -> ctx.getString(R.string.dur_h, h.toString())
            else -> ctx.getString(R.string.dur_m, m.toString())
        }
    }
}
