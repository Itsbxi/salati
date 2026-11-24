package com.salati.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.salati.app.alarm.Scheduler
import com.salati.app.data.Countries
import com.salati.app.data.Country
import com.salati.app.data.PrayerApi
import com.salati.app.data.PrayerStore
import com.salati.app.data.Prefs
import com.salati.app.databinding.ActivitySetupBinding
import com.salati.app.util.LocaleHelper
import com.salati.app.work.RefreshWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** First screen: where do you live? Also used to change the location later. */
class SetupActivity : AppCompatActivity() {

    private lateinit var b: ActivitySetupBinding
    private lateinit var prefs: Prefs
    private var countries: List<Country> = emptyList()
    private var selected: Country? = null
    private var cityJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        b = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(b.root)

        val arabic = LocaleHelper.isArabic(this)
        countries = Countries.all(arabic)
        b.countryInput.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, countries)
        )
        b.countryInput.setOnItemClickListener { parent, _, position, _ ->
            pick(parent.getItemAtPosition(position) as Country)
        }
        b.countryInput.setOnClickListener { b.countryInput.showDropDown() }
        b.countryInput.doAfterTextChanged { b.countryLayout.error = null }
        b.cityInput.doAfterTextChanged {
            b.cityLayout.error = null
            b.error.visibility = View.GONE
        }

        b.langButton.setOnClickListener { LocaleHelper.apply(this, if (arabic) "en" else "ar") }
        b.continueButton.setOnClickListener { submit() }

        // Changing an existing location: start from the saved one.
        if (prefs.setupDone) {
            val c = countries.firstOrNull { it.code == prefs.countryCode }
            if (c != null) {
                selected = c
                b.countryInput.setText(c.toString(), false)
                loadCities(c)
            }
            b.cityInput.setText(prefs.city, false)
        }
    }

    private fun pick(c: Country) {
        selected = c
        b.cityInput.setText("", false)
        loadCities(c)
    }

    /** Suggests cities for the chosen country; typing a city by hand always works too. */
    private fun loadCities(c: Country) {
        cityJob?.cancel()
        b.cityProgress.visibility = View.VISIBLE
        cityJob = lifecycleScope.launch {
            val cities = withContext(Dispatchers.IO) { PrayerApi.fetchCities(c.api) }
            b.cityProgress.visibility = View.GONE
            b.cityInput.setAdapter(
                ArrayAdapter(this@SetupActivity, android.R.layout.simple_dropdown_item_1line, cities)
            )
        }
    }

    private fun setLoading(on: Boolean) {
        b.cityProgress.visibility = if (on) View.VISIBLE else View.GONE
        b.continueButton.isEnabled = !on
    }

    private fun submit() {
        val typed = b.countryInput.text.toString().trim()
        val country = selected?.takeIf { it.toString() == typed }
            ?: countries.firstOrNull { it.toString().equals(typed, ignoreCase = true) }
        if (country == null) {
            b.countryLayout.error = getString(R.string.err_pick_country)
            return
        }
        val city = b.cityInput.text.toString().trim()
        if (city.isEmpty()) {
            b.cityLayout.error = getString(R.string.err_enter_city)
            return
        }

        setLoading(true)
        lifecycleScope.launch {
            // Download this month first: it proves the city exists before we save anything.
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    PrayerStore(applicationContext).fetchAndStore(country.api, city, prefs.method, prefs.school)
                }
            }
            setLoading(false)
            val error = result.exceptionOrNull()
            if (error != null) {
                b.error.text = getString(
                    if (error is PrayerApi.NotFound) R.string.err_city_not_found else R.string.err_network
                )
                b.error.visibility = View.VISIBLE
                return@launch
            }
            prefs.countryCode = country.code
            prefs.countryApi = country.api
            prefs.countryEn = country.en
            prefs.countryAr = country.ar
            prefs.city = city
            prefs.setupDone = true
            RefreshWorker.schedulePeriodic(applicationContext)
            Scheduler.scheduleNext(applicationContext)
            startActivity(
                Intent(this@SetupActivity, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
            finish()
        }
    }
}
