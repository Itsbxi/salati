package com.salati.app.data

import com.salati.app.R
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

enum class Prayer(val nameRes: Int) {
    FAJR(R.string.fajr),
    DHUHR(R.string.dhuhr),
    ASR(R.string.asr),
    MAGHRIB(R.string.maghrib),
    ISHA(R.string.isha)
}

/** All times of one calendar day, in the time zone of the chosen city. */
data class DayTimes(
    val date: LocalDate,
    val zone: ZoneId,
    val times: Map<Prayer, LocalTime>,
    val sunrise: LocalTime,
    val midnight: LocalTime?,
    val hijriDay: String,
    val hijriMonthEn: String,
    val hijriMonthAr: String,
    val hijriYear: String
) {
    fun start(p: Prayer): ZonedDateTime = ZonedDateTime.of(date, times.getValue(p), zone)

    fun sunriseAt(): ZonedDateTime = ZonedDateTime.of(date, sunrise, zone)

    /** End of Isha: Islamic midnight, or civil midnight if the API did not give it. */
    fun midnightAt(): ZonedDateTime {
        val isha = times.getValue(Prayer.ISHA)
        val m = midnight ?: return ZonedDateTime.of(date.plusDays(1), LocalTime.MIDNIGHT, zone)
        return if (m.isBefore(isha)) {
            ZonedDateTime.of(date.plusDays(1), m, zone)
        } else {
            ZonedDateTime.of(date, m, zone)
        }
    }

    /** The moment this prayer's time is over (when the next one starts). */
    fun windowEnd(p: Prayer): ZonedDateTime = when (p) {
        Prayer.FAJR -> sunriseAt()
        Prayer.DHUHR -> start(Prayer.ASR)
        Prayer.ASR -> start(Prayer.MAGHRIB)
        Prayer.MAGHRIB -> start(Prayer.ISHA)
        Prayer.ISHA -> midnightAt()
    }
}
