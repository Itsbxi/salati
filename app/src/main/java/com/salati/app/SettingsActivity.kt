package com.salati.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.salati.app.alarm.Notifications
import com.salati.app.alarm.Scheduler
import com.salati.app.data.Prayer
import com.salati.app.data.PrayerStore
import com.salati.app.data.Prefs
import com.salati.app.databinding.ActivitySettingsBinding
import com.salati.app.util.LocaleHelper
import com.salati.app.util.Perms
import com.salati.app.util.Sound
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class SettingsActivity : AppCompatActivity() {

    private lateinit var b: ActivitySettingsBinding
    private lateinit var prefs: Prefs

    private val pickAudio =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: Exception) {
                    // some providers do not allow persistable access; the file may stop working later
                }
                prefs.customAdhanUri = uri.toString()
                prefs.customAdhanName = displayName(uri)
                refresh()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        b = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(b.root)
        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle(R.string.settings)

        b.rowLanguage.setOnClickListener { chooseLanguage() }
        b.rowLocation.setOnClickListener { startActivity(Intent(this, SetupActivity::class.java)) }
        b.rowMethod.setOnClickListener { chooseMethod() }
        b.rowSchool.setOnClickListener { chooseSchool() }
        b.btnChooseSound.setOnClickListener { pickAudio.launch(arrayOf("audio/*")) }
        b.btnResetSound.setOnClickListener {
            prefs.customAdhanUri = null
            prefs.customAdhanName = null
            refresh()
        }
        b.btnTest.setOnClickListener { testCall() }
        b.rowBattery.setOnClickListener { Perms.requestIgnoreBattery(this) }
        b.rowNotif.setOnClickListener { Perms.openNotificationSettings(this) }

        for (p in Prayer.values()) {
            val sw = MaterialSwitch(this)
            sw.setText(p.nameRes)
            sw.isChecked = prefs.isPrayerEnabled(p)
            sw.setOnCheckedChangeListener { _, checked ->
                prefs.setPrayerEnabled(p, checked)
                Scheduler.scheduleNext(applicationContext)
            }
            b.toggles.addView(sw)
        }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun refresh() {
        val arabic = LocaleHelper.isArabic(this)
        b.valLanguage.text = when (prefs.language) {
            "en" -> "English"
            "ar" -> "العربية"
            else -> getString(R.string.system_default)
        }
        b.valLocation.text = getString(R.string.location_fmt, prefs.city, prefs.countryName(arabic))
        b.valMethod.text = methodName(prefs.method)
        b.valSchool.text = getString(if (prefs.school == 1) R.string.school_hanafi else R.string.school_standard)
        b.valSound.text = prefs.customAdhanName
            ?: getString(if (Sound.hasBundledAdhan(this)) R.string.sound_bundled else R.string.sound_default)
    }

    private fun methodName(id: Int): String {
        val ids = resources.getIntArray(R.array.method_ids)
        val names = resources.getStringArray(R.array.method_names)
        val i = ids.indexOf(id)
        return if (i >= 0) names[i] else names[0]
    }

    private fun chooseLanguage() {
        val codes = arrayOf("system", "en", "ar")
        val items = arrayOf(getString(R.string.system_default), "English", "العربية")
        val current = codes.indexOf(prefs.language).coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.language)
            .setSingleChoiceItems(items, current) { dialog, which ->
                dialog.dismiss()
                LocaleHelper.apply(this, codes[which])
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun chooseMethod() {
        val ids = resources.getIntArray(R.array.method_ids)
        val names = resources.getStringArray(R.array.method_names)
        val current = ids.indexOf(prefs.method).coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.calc_method)
            .setSingleChoiceItems(names, current) { dialog, which ->
                dialog.dismiss()
                changeTiming(ids[which], prefs.school)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun chooseSchool() {
        val items = arrayOf(getString(R.string.school_standard), getString(R.string.school_hanafi))
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.asr_calc)
            .setSingleChoiceItems(items, prefs.school.coerceIn(0, 1)) { dialog, which ->
                dialog.dismiss()
                changeTiming(prefs.method, which)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Downloads the times with the new setting first and only keeps it if that worked. */
    private fun changeTiming(newMethod: Int, newSchool: Int) {
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    PrayerStore(applicationContext).fetchAndStore(prefs.countryApi, prefs.city, newMethod, newSchool)
                }.isSuccess
            }
            if (ok) {
                prefs.method = newMethod
                prefs.school = newSchool
                Scheduler.scheduleNext(applicationContext)
                refresh()
            }
            Toast.makeText(
                this@SettingsActivity,
                if (ok) R.string.updated_ok else R.string.times_unavailable,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /** Rings the call right now so you can check sound, buttons and permissions. */
    private fun testCall() {
        val end = System.currentTimeMillis() + 30 * 60 * 1000L
        Notifications.startCall(applicationContext, Prayer.DHUHR, LocalDate.now(), end, true)
    }

    private fun displayName(uri: Uri): String? = try {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (e: Exception) {
        null
    }
}
