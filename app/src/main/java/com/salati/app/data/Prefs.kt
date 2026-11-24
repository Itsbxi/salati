package com.salati.app.data

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate

class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("salati", Context.MODE_PRIVATE)
    private val cache: SharedPreferences =
        context.applicationContext.getSharedPreferences("salati_cache", Context.MODE_PRIVATE)

    var setupDone: Boolean
        get() = sp.getBoolean("setup_done", false)
        set(v) { sp.edit().putBoolean("setup_done", v).apply() }

    var countryCode: String
        get() = sp.getString("country_code", "") ?: ""
        set(v) { sp.edit().putString("country_code", v).apply() }

    /** Country name sent to the prayer-times API. */
    var countryApi: String
        get() = sp.getString("country_api", "") ?: ""
        set(v) { sp.edit().putString("country_api", v).apply() }

    var countryEn: String
        get() = sp.getString("country_en", "") ?: ""
        set(v) { sp.edit().putString("country_en", v).apply() }

    var countryAr: String
        get() = sp.getString("country_ar", "") ?: ""
        set(v) { sp.edit().putString("country_ar", v).apply() }

    var city: String
        get() = sp.getString("city", "") ?: ""
        set(v) { sp.edit().putString("city", v).apply() }

    /** -1 = let the API choose the calculation method for the location. */
    var method: Int
        get() = sp.getInt("method", -1)
        set(v) { sp.edit().putInt("method", v).apply() }

    /** 0 = Standard (Shafi, Maliki, Hanbali), 1 = Hanafi. */
    var school: Int
        get() = sp.getInt("school", 0)
        set(v) { sp.edit().putInt("school", v).apply() }

    /** "system", "en" or "ar". */
    var language: String
        get() = sp.getString("language", "system") ?: "system"
        set(v) { sp.edit().putString("language", v).apply() }

    var customAdhanUri: String?
        get() = sp.getString("adhan_uri", null)
        set(v) { sp.edit().putString("adhan_uri", v).apply() }

    var customAdhanName: String?
        get() = sp.getString("adhan_name", null)
        set(v) { sp.edit().putString("adhan_name", v).apply() }

    var askedNotifications: Boolean
        get() = sp.getBoolean("asked_notifications", false)
        set(v) { sp.edit().putBoolean("asked_notifications", v).apply() }

    var hideBatteryHint: Boolean
        get() = sp.getBoolean("hide_battery_hint", false)
        set(v) { sp.edit().putBoolean("hide_battery_hint", v).apply() }

    fun countryName(arabic: Boolean): String =
        if (arabic) countryAr.ifEmpty { countryEn } else countryEn

    fun isPrayerEnabled(p: Prayer): Boolean = sp.getBoolean("enabled_${p.name}", true)

    fun setPrayerEnabled(p: Prayer, enabled: Boolean) {
        sp.edit().putBoolean("enabled_${p.name}", enabled).apply()
    }

    private fun prayedKey(d: LocalDate, p: Prayer) = "prayed_${d}_${p.name}"

    fun isPrayed(d: LocalDate, p: Prayer): Boolean = sp.getBoolean(prayedKey(d, p), false)

    fun setPrayed(d: LocalDate, p: Prayer, prayed: Boolean) {
        val e = sp.edit()
        if (prayed) e.putBoolean(prayedKey(d, p), true) else e.remove(prayedKey(d, p))
        e.apply()
    }

    /** Forget "prayed" marks older than 10 days. */
    fun pruneOld(today: LocalDate) {
        val limit = today.minusDays(10).toString()
        val e = sp.edit()
        for (k in sp.all.keys) {
            if (k.startsWith("prayed_") && k.length >= 17 && k.substring(7, 17) < limit) e.remove(k)
        }
        e.apply()
    }

    /** Identifies which place/settings the cached times belong to. */
    fun locationSignature(): String = "$countryApi|$city|$method|$school"

    fun cacheGet(key: String): String? = cache.getString(key, null)

    fun cachePut(key: String, value: String) {
        cache.edit().putString(key, value).apply()
    }

    fun register(l: SharedPreferences.OnSharedPreferenceChangeListener) {
        sp.registerOnSharedPreferenceChangeListener(l)
        cache.registerOnSharedPreferenceChangeListener(l)
    }

    fun unregister(l: SharedPreferences.OnSharedPreferenceChangeListener) {
        sp.unregisterOnSharedPreferenceChangeListener(l)
        cache.unregisterOnSharedPreferenceChangeListener(l)
    }
}
