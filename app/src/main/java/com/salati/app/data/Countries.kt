package com.salati.app.data

import java.text.Collator
import java.util.Locale

class Country(
    val code: String,
    val en: String,
    val ar: String,
    /** Name sent to the prayer-times / city-list APIs. */
    val api: String,
    private val label: String
) {
    override fun toString(): String = label
}

object Countries {
    private val apiNames = mapOf(
        "US" to "United States", "GB" to "United Kingdom", "PS" to "Palestine",
        "CD" to "Democratic Republic of the Congo", "CG" to "Republic of the Congo",
        "HK" to "Hong Kong", "MO" to "Macau", "MM" to "Myanmar", "TR" to "Turkey",
        "CI" to "Ivory Coast", "CZ" to "Czech Republic", "SZ" to "Eswatini",
        "VI" to "US Virgin Islands", "KR" to "South Korea", "KP" to "North Korea",
        "RU" to "Russia", "XK" to "Kosovo", "BA" to "Bosnia and Herzegovina"
    )
    private val arabicNames = mapOf("PS" to "فلسطين", "XK" to "كوسوفو")

    /** Every country, named in English and Arabic, sorted for the current UI language. */
    fun all(arabicUi: Boolean): List<Country> {
        val english = Locale.ENGLISH
        val arabic = Locale("ar")
        val out = ArrayList<Country>()
        val codes = Locale.getISOCountries().toMutableList()
        codes.add("XK")
        for (code in codes) {
            val loc = Locale("", code)
            var en = apiNames[code] ?: loc.getDisplayCountry(english)
            if (en.isBlank() || en == code) continue
            en = en.replace(" & ", " and ").replace("St. ", "Saint ").substringBefore(" (").trim()
            var ar = arabicNames[code] ?: loc.getDisplayCountry(arabic)
            if (ar.isBlank() || ar == code) ar = en
            val api = apiNames[code] ?: en
            val label = if (arabicUi) "$ar · $en" else "$en · $ar"
            out.add(Country(code, en, ar, api, label))
        }
        val collator = Collator.getInstance(if (arabicUi) arabic else english)
        out.sortWith(Comparator { a, b -> collator.compare(a.toString(), b.toString()) })
        return out
    }
}
