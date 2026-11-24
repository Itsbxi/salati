package com.salati.app.util

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.salati.app.data.Prefs
import java.util.Locale

object LocaleHelper {

    /** A context whose resources follow the language chosen inside the app (used for notifications). */
    fun localized(base: Context): Context {
        val lang = Prefs(base).language
        if (lang == "system") return base
        val config = Configuration(base.resources.configuration)
        config.setLocale(Locale.forLanguageTag(lang))
        return base.createConfigurationContext(config)
    }

    /** Saves and applies the language ("system", "en" or "ar"). Recreates open screens. */
    fun apply(context: Context, lang: String) {
        Prefs(context).language = lang
        val list = if (lang == "system") {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(lang)
        }
        AppCompatDelegate.setApplicationLocales(list)
    }

    fun isArabic(context: Context): Boolean =
        context.resources.configuration.locales[0].language == "ar"
}
