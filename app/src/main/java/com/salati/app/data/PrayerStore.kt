package com.salati.app.data

import android.content.Context
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Keeps the downloaded months on the phone so the app also works offline. */
class PrayerStore(context: Context) {
    private val prefs = Prefs(context)

    private fun key(year: Int, month: Int) = "cal_${year}_$month"

    /**
     * Downloads this month and next month for a place and caches them.
     * Throws if the current month cannot be downloaded.
     */
    fun fetchAndStore(countryApi: String, city: String, method: Int, school: Int) {
        val sig = "$countryApi|$city|$method|$school"
        val today = LocalDate.now()
        val offsets = if (today.dayOfMonth <= 2) listOf(0, 1, -1) else listOf(0, 1)
        for (i in offsets) {
            val d = today.plusMonths(i.toLong())
            try {
                val obj = PrayerApi.fetchMonth(countryApi, city, method, school, d.year, d.monthValue)
                obj.put("sig", sig)
                prefs.cachePut(key(d.year, d.monthValue), obj.toString())
            } catch (e: Exception) {
                if (i == 0) throw e
            }
        }
    }

    /** Re-downloads with the saved place and settings. */
    fun refresh() = fetchAndStore(prefs.countryApi, prefs.city, prefs.method, prefs.school)

    fun getDay(date: LocalDate): DayTimes? {
        val raw = prefs.cacheGet(key(date.year, date.monthValue)) ?: return null
        try {
            val root = JSONObject(raw)
            if (root.optString("sig") != prefs.locationSignature()) return null
            val zone = ZoneId.of(root.getString("tz"))
            val days = root.getJSONArray("days")
            val want = date.toString()
            for (i in 0 until days.length()) {
                val d = days.getJSONObject(i)
                if (d.getString("g") == want) return parse(d, date, zone)
            }
        } catch (e: Exception) {
            // corrupt cache: treat as missing
        }
        return null
    }

    fun cityZone(): ZoneId {
        val today = LocalDate.now()
        for (d in listOf(today, today.plusMonths(1), today.minusMonths(1))) {
            val raw = prefs.cacheGet(key(d.year, d.monthValue)) ?: continue
            try {
                val o = JSONObject(raw)
                if (o.optString("sig") == prefs.locationSignature()) return ZoneId.of(o.getString("tz"))
            } catch (e: Exception) {
                // ignore and try the next month
            }
        }
        return ZoneId.systemDefault()
    }

    /** "Now" in the city's own time zone. */
    fun cityNow(): ZonedDateTime = ZonedDateTime.now(cityZone())

    private fun time(s: String): LocalTime {
        val p = s.split(":")
        return LocalTime.of(p[0].trim().toInt() % 24, p[1].trim().toInt())
    }

    private fun parse(d: JSONObject, date: LocalDate, zone: ZoneId): DayTimes {
        val times = mapOf(
            Prayer.FAJR to time(d.getString("fajr")),
            Prayer.DHUHR to time(d.getString("dhuhr")),
            Prayer.ASR to time(d.getString("asr")),
            Prayer.MAGHRIB to time(d.getString("maghrib")),
            Prayer.ISHA to time(d.getString("isha"))
        )
        val mid = d.optString("mid", "")
        return DayTimes(
            date = date,
            zone = zone,
            times = times,
            sunrise = time(d.getString("sunrise")),
            midnight = if (mid.contains(":")) time(mid) else null,
            hijriDay = d.optString("hd", ""),
            hijriMonthEn = d.optString("hmen", ""),
            hijriMonthAr = d.optString("hmar", ""),
            hijriYear = d.optString("hy", "")
        )
    }
}
