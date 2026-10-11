package com.openpets.android

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.SystemClock

/**
 * Plays a short notification beep when pet activity arrives.
 *
 * Keeps a single prepared [MediaPlayer] (reused via seekTo/start) on the ALARM
 * stream, and [warmUp]s the output at startup — a freshly created player's first
 * play can be swallowed while the audio output powers up.
 */
class SoundNotifier(private val context: Context) {

    private var player: MediaPlayer? = null
    private var customUri: String? = null
    private val lock = Any()
    private var lastPlayedAt = 0L

    private fun attributes(): AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private fun sourceUri(): Uri =
        customUri?.let { Uri.parse(it) }
            ?: Uri.parse("android.resource://${context.packageName}/${R.raw.openpets_beep}")

    private fun ensurePlayer(): MediaPlayer? = synchronized(lock) {
        player?.let { return it }
        return try {
            val mp = MediaPlayer().apply {
                setAudioAttributes(attributes())
                setDataSource(context, sourceUri())
                setOnErrorListener { p, _, _ ->
                    p.reset()
                    true
                }
                prepare()
            }
            player = mp
            mp
        } catch (_: Exception) {
            null
        }
    }

    fun play(force: Boolean = false) {
        val now = SystemClock.uptimeMillis()
        if (!force && now - lastPlayedAt < MIN_INTERVAL_MS) return
        lastPlayedAt = now
        Thread {
            try {
                val mp = ensurePlayer() ?: return@Thread
                synchronized(lock) {
                    try {
                        mp.seekTo(0)
                    } catch (_: Exception) {
                    }
                    mp.start()
                }
            } catch (_: Exception) {
            }
        }.apply { isDaemon = true }.start()
    }

    /** Uses a user-picked sound (content:// URI) instead of the bundled beep. */
    fun setCustomUri(uri: String?) {
        synchronized(lock) {
            if (uri != customUri) {
                customUri = uri
                try {
                    player?.release()
                } catch (_: Exception) {
                }
                player = null
            }
        }
    }

    /** Primes the audio output so the first real beep isn't lost. */
    fun warmUp() {
        Thread {
            try {
                val mp = ensurePlayer() ?: return@Thread
                synchronized(lock) {
                    mp.setVolume(0f, 0f)
                    mp.start()
                    Thread.sleep(150)
                    mp.pause()
                    mp.seekTo(0)
                    mp.setVolume(1f, 1f)
                }
            } catch (_: Exception) {
            }
        }.apply { isDaemon = true }.start()
    }

    /**
     * Diagnostic: forces the alarm stream to max volume and plays the bundled
     * sound on a loop for ~2.5s so it is unmistakable.
     */
    fun playTest() {
        Thread {
            var testPlayer: MediaPlayer? = null
            try {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, max, 0)

                testPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build(),
                    )
                    setDataSource(context, sourceUri())
                    isLooping = true
                    prepare()
                    start()
                }
                Thread.sleep(2500)
            } catch (_: Exception) {
            } finally {
                try {
                    testPlayer?.stop()
                } catch (_: Exception) {
                }
                try {
                    testPlayer?.release()
                } catch (_: Exception) {
                }
            }
        }.apply { isDaemon = true }.start()
    }

    fun release() {
        synchronized(lock) {
            try {
                player?.release()
            } catch (_: Exception) {
            }
            player = null
        }
    }

    companion object {
        private const val MIN_INTERVAL_MS = 1_200L

        /** Reactions that warrant a notification sound (attention/terminal states). */
        val NOTIFY_REACTIONS: Set<String> = setOf("success", "error", "waving", "celebrating")
    }
}
