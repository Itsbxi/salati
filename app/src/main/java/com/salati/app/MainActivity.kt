package com.salati.app

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.color.MaterialColors
import com.salati.app.alarm.AdhanService
import com.salati.app.alarm.Notifications
import com.salati.app.alarm.Scheduler
import com.salati.app.data.Prayer
import com.salati.app.data.PrayerStore
import com.salati.app.data.Prefs
import com.salati.app.databinding.ActivityMainBinding
import com.salati.app.databinding.ItemPrayerBinding
import com.salati.app.util.Fmt
import com.salati.app.util.LocaleHelper
import com.salati.app.util.Perms
import com.salati.app.work.RefreshWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var prefs: Prefs
    private lateinit var store: PrayerStore

    private val rows = ArrayList<ItemPrayerBinding>()
    private var warnKind = 0
    private var askedManually = false

    // One row per line on screen; the null prayer is "Sunrise" (not a prayer, no checkbox).
    private val rowDefs = listOf(
        R.string.fajr to Prayer.FAJR,
        R.string.sunrise to null,
        R.string.dhuhr to Prayer.DHUHR,
        R.string.asr to Prayer.ASR,
        R.string.maghrib to Prayer.MAGHRIB,
        R.string.isha to Prayer.ISHA
    )

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted && askedManually) Perms.openNotificationSettings(this)
            askedManually = false
            refreshUi()
        }

    // Kept in a field: SharedPreferences only holds listeners weakly.
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refreshUi() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        if (!prefs.setupDone) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }
        store = PrayerStore(this)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        setSupportActionBar(b.toolbar)
        buildRows()

        b.warnButton.setOnClickListener { onWarnAction() }
        b.warnDismiss.setOnClickListener {
            prefs.hideBatteryHint = true
            refreshUi()
        }

        if (Build.VERSION.SDK_INT >= 33 && !prefs.askedNotifications &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            prefs.askedNotifications = true
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Keeps the countdown fresh while the screen is visible.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    refreshUi()
                    delay(20_000)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (::b.isInitialized) prefs.register(prefListener)
    }

    override fun onStop() {
        if (::b.isInitialized) prefs.unregister(prefListener)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (!::b.isInitialized) return
        RefreshWorker.schedulePeriodic(this)
        Scheduler.scheduleNext(this)
        refreshUi()
        if (store.getDay(store.cityNow().toLocalDate()) == null) RefreshWorker.runOnce(this)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_settings -> {
            startActivity(Intent(this, SettingsActivity::class.java))
            true
        }
        R.id.action_refresh -> {
            manualRefresh()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun buildRows() {
        for ((nameRes, prayer) in rowDefs) {
            val r = ItemPrayerBinding.inflate(layoutInflater, b.rows, false)
            r.prayerName.setText(nameRes)
            if (prayer == null) r.prayerCheck.visibility = View.INVISIBLE
            b.rows.addView(r.root)
            rows.add(r)
        }
    }

    private fun manualRefresh() {
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { store.refresh() }.isSuccess }
            Scheduler.scheduleNext(applicationContext)
            Toast.makeText(
                this@MainActivity,
                if (ok) R.string.updated_ok else R.string.times_unavailable,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun refreshUi() {
        if (!::b.isInitialized) return
        val arabic = LocaleHelper.isArabic(this)
        val now = store.cityNow()
        val today = now.toLocalDate()
        val day = store.getDay(today)

        b.location.text = getString(R.string.location_fmt, prefs.city, prefs.countryName(arabic))
        updateWarning()

        if (day == null) {
            b.content.visibility = View.GONE
            b.status.visibility = View.VISIBLE
            b.status.setText(R.string.loading_times)
            b.dateGreg.text = ""
            b.dateHijri.text = ""
            return
        }
        b.content.visibility = View.VISIBLE
        b.status.visibility = View.GONE

        val locale = resources.configuration.locales[0]
        b.dateGreg.text = today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", locale))
        val hijriMonth = if (arabic) day.hijriMonthAr else day.hijriMonthEn
        b.dateHijri.text = getString(R.string.hijri_fmt, day.hijriDay, hijriMonth, day.hijriYear)

        // Next prayer card
        val upcoming = Prayer.values().firstOrNull { day.start(it).isAfter(now) }
        val nextPrayer: Prayer
        val nextTime: ZonedDateTime
        if (upcoming != null) {
            nextPrayer = upcoming
            nextTime = day.start(upcoming)
        } else {
            nextPrayer = Prayer.FAJR
            nextTime = store.getDay(today.plusDays(1))?.start(Prayer.FAJR)
                ?: day.start(Prayer.FAJR).plusDays(1)
        }
        b.nextName.setText(nextPrayer.nameRes)
        b.nextTime.text = Fmt.time(this, nextTime.toLocalTime())
        b.nextIn.text = getString(
            R.string.in_time,
            Fmt.duration(this, Duration.between(now, nextTime).toMillis())
        )

        // List of times
        val highlight = MaterialColors.getColor(b.rows, com.google.android.material.R.attr.colorSurfaceVariant)
        rowDefs.forEachIndexed { i, (_, p) ->
            val row = rows[i]
            val time = if (p == null) day.sunrise else day.times.getValue(p)
            row.prayerTime.text = Fmt.time(this, time)
            row.root.setBackgroundColor(if (p != null && upcoming == p) highlight else Color.TRANSPARENT)
            if (p != null) {
                val cb = row.prayerCheck
                cb.setOnCheckedChangeListener(null)
                cb.isChecked = prefs.isPrayed(today, p)
                cb.setOnCheckedChangeListener { _, checked -> onPrayedToggled(today, p, checked) }
            }
        }
    }

    private fun onPrayedToggled(date: LocalDate, p: Prayer, checked: Boolean) {
        prefs.setPrayed(date, p, checked)
        if (checked) {
            NotificationManagerCompat.from(this).cancel(Notifications.id(p))
            stopService(Intent(this, AdhanService::class.java))
        }
        Scheduler.scheduleNext(this)
    }

    /** Shows the most important problem that would stop the call from arriving, if any. */
    private fun updateWarning() {
        var kind = 0
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            kind = 1
        } else if (Build.VERSION.SDK_INT >= 31 &&
            !(getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
        ) {
            kind = 2
        } else if (!prefs.hideBatteryHint &&
            !(getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)
        ) {
            kind = 3
        }
        warnKind = kind
        b.warnCard.visibility = if (kind == 0) View.GONE else View.VISIBLE
        b.warnDismiss.visibility = if (kind == 3) View.VISIBLE else View.GONE
        b.warnText.setText(
            when (kind) {
                1 -> R.string.warn_notif
                2 -> R.string.warn_alarm
                else -> R.string.warn_battery
            }
        )
    }

    private fun onWarnAction() {
        when (warnKind) {
            1 -> {
                askedManually = true
                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            2 -> startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))
            )
            3 -> Perms.requestIgnoreBattery(this)
        }
    }
}
