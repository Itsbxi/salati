package com.salati.app.alarm

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import com.salati.app.data.Prayer
import com.salati.app.util.Sound
import java.time.LocalDate

/**
 * Plays the adhan at prayer time. It runs as a foreground service so Android keeps it alive
 * while the sound plays, then leaves the notification (with the two buttons) on screen.
 */
class AdhanService : Service() {

    private var player: MediaPlayer? = null
    private var focusRequest: AudioFocusRequest? = null
    private val handler = Handler(Looper.getMainLooper())

    private val attrs: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prayer = intent?.getStringExtra(Scheduler.EXTRA_PRAYER)
            ?.let { n -> Prayer.values().firstOrNull { it.name == n } } ?: Prayer.DHUHR
        val date = intent?.getStringExtra(Scheduler.EXTRA_DATE)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()
        val endMs = intent?.getLongExtra(Scheduler.EXTRA_END, 0L) ?: 0L
        val test = intent?.getBooleanExtra(Scheduler.EXTRA_TEST, false) ?: false

        try {
            val n = Notifications.build(this, prayer, date, endMs, Notifications.CH_CALL, test)
            ServiceCompat.startForeground(
                this,
                Notifications.id(prayer),
                n,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } catch (e: Exception) {
            // Could not become a foreground service: show a plain reminder instead.
            Notifications.showReminder(this, prayer, date, endMs, test)
            stopSelf()
            return START_NOT_STICKY
        }
        startAudio()
        return START_NOT_STICKY
    }

    private fun startAudio() {
        stopAudio()
        requestFocus()
        for (uri in Sound.adhanCandidates(this)) {
            val mp = MediaPlayer()
            try {
                mp.setAudioAttributes(attrs)
                mp.setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
                mp.setDataSource(applicationContext, uri)
                mp.setOnCompletionListener { finishAudio() }
                mp.setOnErrorListener { _, _, _ ->
                    finishAudio()
                    true
                }
                mp.prepare()
                mp.start()
                player = mp
                handler.postDelayed({ finishAudio() }, MAX_PLAY_MS)
                return
            } catch (e: Exception) {
                try {
                    mp.release()
                } catch (e2: Exception) {
                    // ignore
                }
            }
        }
        // Nothing playable: just keep the notification.
        finishAudio()
    }

    /** Sound is over: let the service end but keep the notification so the buttons stay usable. */
    private fun finishAudio() {
        handler.removeCallbacksAndMessages(null)
        stopAudio()
        abandonFocus()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    private fun stopAudio() {
        val mp = player ?: return
        player = null
        try {
            if (mp.isPlaying) mp.stop()
        } catch (e: Exception) {
            // ignore
        }
        try {
            mp.release()
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun requestFocus() {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(attrs)
            .build()
        focusRequest = req
        am.requestAudioFocus(req)
    }

    private fun abandonFocus() {
        val req = focusRequest ?: return
        focusRequest = null
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.abandonAudioFocusRequest(req)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        stopAudio()
        abandonFocus()
        super.onDestroy()
    }

    companion object {
        /** Safety stop so a very long file can never play forever. */
        private const val MAX_PLAY_MS = 6 * 60 * 1000L
    }
}
