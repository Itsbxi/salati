package com.salati.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.ZoneId

/** Talks to the free AlAdhan prayer-times API and to a free city-list API. */
object PrayerApi {

    /** The server answered but does not know this city. */
    class NotFound(message: String) : IOException(message)

    private const val BASE = "https://api.aladhan.com/v1"

    /** Downloads one whole month and returns it in a compact form (see PrayerStore). */
    fun fetchMonth(
        country: String,
        city: String,
        method: Int,
        school: Int,
        year: Int,
        month: Int
    ): JSONObject {
        val url = StringBuilder("$BASE/calendarByCity/$year/$month")
        url.append("?city=").append(URLEncoder.encode(city, "UTF-8"))
        url.append("&country=").append(URLEncoder.encode(country, "UTF-8"))
        url.append("&school=").append(school)
        if (method >= 0) url.append("&method=").append(method)
        return compact(JSONObject(get(url.toString())))
    }

    private fun compact(root: JSONObject): JSONObject {
        if (root.optInt("code") != 200) throw NotFound("API code ${root.optInt("code")}")
        val data = root.getJSONArray("data")
        if (data.length() == 0) throw NotFound("No data")
        val days = JSONArray()
        var tz = ""
        for (i in 0 until data.length()) {
            val o = data.getJSONObject(i)
            val timings = o.getJSONObject("timings")
            val date = o.getJSONObject("date")
            // Gregorian date comes as DD-MM-YYYY
            val g = date.getJSONObject("gregorian").getString("date").split("-")
            val hijri = date.getJSONObject("hijri")
            val hMonth = hijri.optJSONObject("month")
            if (tz.isEmpty()) tz = o.optJSONObject("meta")?.optString("timezone", "") ?: ""
            days.put(
                JSONObject()
                    .put("g", "${g[2]}-${g[1]}-${g[0]}")
                    .put("fajr", clean(timings.getString("Fajr")))
                    .put("sunrise", clean(timings.getString("Sunrise")))
                    .put("dhuhr", clean(timings.getString("Dhuhr")))
                    .put("asr", clean(timings.getString("Asr")))
                    .put("maghrib", clean(timings.getString("Maghrib")))
                    .put("isha", clean(timings.getString("Isha")))
                    .put("mid", clean(timings.optString("Midnight", "")))
                    .put("hd", hijri.optString("day", ""))
                    .put("hmen", hMonth?.optString("en", "") ?: "")
                    .put("hmar", hMonth?.optString("ar", "") ?: "")
                    .put("hy", hijri.optString("year", ""))
            )
        }
        if (tz.isEmpty()) tz = ZoneId.systemDefault().id
        return JSONObject().put("tz", tz).put("days", days)
    }

    /** "05:12 (EET)" -> "05:12" */
    private fun clean(s: String): String = s.trim().substringBefore(' ')

    /** City suggestions for a country. Returns an empty list if the service is unavailable. */
    fun fetchCities(countryName: String): List<String> {
        val c = URL("https://countriesnow.space/api/v0.1/countries/cities")
            .openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 12_000
            c.readTimeout = 20_000
            c.requestMethod = "POST"
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            val body = JSONObject().put("country", countryName).toString()
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            if (c.responseCode !in 200..299) return emptyList()
            val text = c.inputStream.bufferedReader().use { it.readText() }
            val o = JSONObject(text)
            if (o.optBoolean("error", true)) return emptyList()
            val arr = o.getJSONArray("data")
            val list = ArrayList<String>()
            for (i in 0 until arr.length()) list.add(arr.getString(i))
            return list.distinct().sorted()
        } catch (e: Exception) {
            return emptyList()
        } finally {
            c.disconnect()
        }
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 15_000
            c.readTimeout = 25_000
            c.setRequestProperty("Accept", "application/json")
            val code = c.responseCode
            if (code == 400 || code == 404) throw NotFound("HTTP $code")
            if (code !in 200..299) throw IOException("HTTP $code")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }
}
