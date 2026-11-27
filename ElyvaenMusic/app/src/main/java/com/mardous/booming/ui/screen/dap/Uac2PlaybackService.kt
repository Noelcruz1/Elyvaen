package com.mardous.booming.ui.screen.dap

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.PlaybackParams
import android.os.Build
import android.os.IBinder
import android.os.Process
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * Plays the UAC2 FIFO through AudioTrack at the host's native rate.
 *
 * elyvaen-uac2d captured the USB gadget at 44.1/48/96/192 kHz, converts to
 * float32 and writes it unchanged to /data/local/tmp/elyvaen_uac2.pcm. The
 * rate is published in /data/local/tmp/elyvaen_uac2.rate. This service
 * creates an AudioTrack at that rate (up to 192 kHz), so the Android HAL and
 * the WCD9370 codec run at the native rate instead of a forced 48 kHz.
 *
 * The service fills half of a ~200 ms AudioTrack buffer before calling
 * play(), then keeps the host/DAC clock drift centered with a tiny
 * pitch-preserving-free speed trim. The trim uses speed == pitch, which
 * avoids Android's WSOLA time-stretcher; with pitch == speed the correction
 * is an ordinary low-ratio resample (at most 500 ppm, inaudible).
 */
class Uac2PlaybackService : Service() {
    companion object {
        private const val TAG = "ElyvaenUac2Play"
        private const val FIFO = "/data/local/tmp/elyvaen_uac2.pcm"
        private const val RATE_FILE = "/data/local/tmp/elyvaen_uac2.rate"
        private const val FALLBACK_RATE = 48000
        private const val CHANNELS = 2
        private const val FRAME_BYTES = CHANNELS * 4
        private const val CHANNEL_ID = "elyvaen_uac2"
        private const val NOTIFICATION_ID = 225
        private const val MAX_RATE = 192000
        private const val TARGET_BUFFER_MS = 200
        private const val PREFILL_DIVISOR = 2
        private const val RATE_CHECK_MS = 250L
        private const val TRIM_INTERVAL_MS = 250L
        private const val TRIM_STEP = 0.0003
        private const val TRIM_EPSILON = 0.00002
    }

    @Volatile
    private var running = false
    @Volatile
    private var input: FileInputStream? = null
    private var thread: Thread? = null
    private var track: AudioTrack? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Elyvaen USB DAC",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        if (!running) {
            running = true
            thread = Thread({ playLoop() }, "elyvaen-uac2-fifo").also {
                it.start()
            }
        }
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("Elyvaen USB DAC")
            .setContentText("Reproduciendo desde USB")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
    }

    private fun readRateOrNull(): Int? {
        return runCatching {
            File(RATE_FILE).readText().trim().toInt()
        }.getOrNull()?.takeIf { it in 8000..MAX_RATE }
    }

    private fun readRate(): Int = readRateOrNull() ?: FALLBACK_RATE

    private fun buildTrack(rate: Int): AudioTrack {
        val min = AudioTrack.getMinBufferSize(
            rate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_FLOAT
        ).coerceAtLeast(0)
        val desired = (rate.toLong() * TARGET_BUFFER_MS / 1000L)
            .toInt()
            .coerceAtLeast(1) * FRAME_BYTES
        val bufferBytes = maxOf(min * 4, desired, 32768)
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(bufferBytes)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_POWER_SAVING)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    private fun playLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        while (running) {
            var stream: FileInputStream? = null
            var audio: AudioTrack? = null
            var watcher: Thread? = null
            var rate = FALLBACK_RATE
            try {
                // Blocks until the daemon creates/opens the FIFO. The daemon
                // publishes the rate before opening, so read it afterwards.
                val inStream = FileInputStream(FIFO)
                stream = inStream
                input = inStream
                rate = readRate()

                val player = buildTrack(rate)
                audio = player
                synchronized(this) { track = player }
                Log.i(TAG, "fifo connected at $rate Hz")

                watcher = Thread({
                    while (running && !Thread.currentThread().isInterrupted) {
                        try {
                            Thread.sleep(RATE_CHECK_MS)
                        } catch (_: InterruptedException) {
                            break
                        }
                        val newRate = readRateOrNull()
                        if (newRate != null && newRate != rate) {
                            Log.i(TAG, "host rate $rate -> $newRate")
                            // Unblock the reader; the loop reopens with the new
                            // rate and a freshly prefilled AudioTrack.
                            runCatching { inStream.close() }
                            break
                        }
                    }
                }, "elyvaen-uac2-rate").also {
                    it.isDaemon = true
                    it.start()
                }

                val buf = ByteArray(16384)
                val bb = ByteBuffer.allocateDirect(buf.size)
                    .order(ByteOrder.LITTLE_ENDIAN)
                var carry = 0
                var writtenFrames = 0L
                var started = false
                var appliedRatio = 1.0
                var lastTrim = 0L

                while (running) {
                    val n = inStream.read(buf, carry, buf.size - carry)
                    if (n <= 0) {
                        break
                    }
                    val total = carry + n
                    val usable = total and (FRAME_BYTES - 1).inv()
                    carry = total - usable
                    if (carry > 0) {
                        System.arraycopy(buf, usable, buf, 0, carry)
                    }
                    if (usable <= 0) {
                        continue
                    }

                    bb.clear()
                    bb.put(buf, 0, usable)
                    bb.flip()
                    while (bb.hasRemaining() && running) {
                        val written = player.write(
                            bb,
                            bb.remaining(),
                            AudioTrack.WRITE_BLOCKING
                        )
                        if (written <= 0) {
                            throw IllegalStateException("AudioTrack.write=$written")
                        }
                        writtenFrames += written / FRAME_BYTES
                    }

                    if (!started) {
                        val prefillFrames = (player.bufferSizeInFrames / PREFILL_DIVISOR)
                            .coerceAtLeast(rate / 25)
                        if (writtenFrames >= prefillFrames) {
                            player.play()
                            started = true
                            Log.i(
                                TAG,
                                "playing at $rate Hz, prefill=$prefillFrames frames"
                            )
                        }
                        continue
                    }

                    val now = SystemClock.elapsedRealtime()
                    if (now - lastTrim >= TRIM_INTERVAL_MS) {
                        val cap = player.bufferSizeInFrames.toLong()
                        val head = player.playbackHeadPosition.toLong() and
                                0xffffffffL
                        val diff = writtenFrames - head
                        val buffered = if (diff in 0..cap * 2L) diff else cap / 2
                        val low = cap / 4
                        val high = cap * 3 / 4
                        val ratio = if (buffered in low..high) {
                            1.0
                        } else {
                            val target = cap / 2.0
                            val error = (buffered - target) / target
                            (1.0 + error * TRIM_STEP)
                                .coerceIn(0.9995, 1.0005)
                        }
                        if (abs(ratio - appliedRatio) > TRIM_EPSILON) {
                            runCatching {
                                player.playbackParams = PlaybackParams()
                                    .setSpeed(ratio.toFloat())
                                    // speed == pitch avoids WSOLA/Sonic; this
                                    // is a tiny resample, not time-stretch.
                                    .setPitch(ratio.toFloat())
                            }
                            appliedRatio = ratio
                        }
                        lastTrim = now
                    }
                }
            } catch (t: Throwable) {
                if (running) {
                    Log.w(TAG, "fifo error: $t")
                }
            } finally {
                watcher?.interrupt()
                if (input === stream) {
                    input = null
                }
                if (audio != null) {
                    try {
                        audio.stop()
                    } catch (_: Throwable) {
                    }
                    try {
                        audio.release()
                    } catch (_: Throwable) {
                    }
                    synchronized(this) {
                        if (track === audio) {
                            track = null
                        }
                    }
                }
                try {
                    stream?.close()
                } catch (_: Throwable) {
                }
            }
            if (running) {
                try {
                    Thread.sleep(200)
                } catch (e: InterruptedException) {
                    break
                }
            }
        }
    }

    override fun onDestroy() {
        running = false
        runCatching { input?.close() }
        thread?.interrupt()
        runCatching { thread?.join(500) }
        synchronized(this) {
            try {
                track?.stop()
            } catch (_: Throwable) {
            }
            try {
                track?.release()
            } catch (_: Throwable) {
            }
            track = null
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
