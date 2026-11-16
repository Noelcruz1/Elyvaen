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
 * elyvaen-uac2d captures the USB gadget at 44.1/48/96/192 kHz, converts to
 * float32 and writes it unchanged to /data/local/tmp/elyvaen_uac2.pcm. The
 * rate is published in /data/local/tmp/elyvaen_uac2.rate. This service
 * creates an AudioTrack at that rate (up to 192 kHz), so the Android HAL and
 * the WCD9370 codec run at the native rate instead of a forced 48 kHz.
 *
 * A small playback-speed trim compensates the host/DAC clock drift; the
 * pitch stays at 1.0 and the correction is limited to ±500 ppm.
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
        private const val MAX_RATE = 192000
    }

    @Volatile
    private var running = false
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
        startForeground(1, buildNotification())
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

    private fun readRate(): Int {
        val rate = runCatching {
            File(RATE_FILE).readText().trim().toInt()
        }.getOrDefault(FALLBACK_RATE)
        return if (rate in 8000..MAX_RATE) rate else FALLBACK_RATE
    }

    private fun buildTrack(rate: Int): AudioTrack {
        val min = AudioTrack.getMinBufferSize(
            rate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_FLOAT
        )
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
            .setBufferSizeInBytes(maxOf(min * 4, 32768))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    private fun playLoop() {
        while (running) {
            var input: FileInputStream? = null
            var audio: AudioTrack? = null
            var rate = FALLBACK_RATE
            try {
                // Blocks until the daemon opens the FIFO writer. The daemon
                // publishes the rate before opening, so read it afterwards.
                val inStream = FileInputStream(FIFO)
                input = inStream
                rate = readRate()
                var current = buildTrack(rate)
                audio = current
                synchronized(this) { track = current }
                current.play()
                Log.i(TAG, "fifo connected at $rate Hz")

                val buf = ByteArray(32768)
                val bb = ByteBuffer.allocateDirect(buf.size)
                    .order(ByteOrder.LITTLE_ENDIAN)
                var writtenFrames = 0L
                var lastRateCheck = SystemClock.elapsedRealtime()

                while (running) {
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastRateCheck >= 200) {
                        val newRate = readRate()
                        if (newRate != rate) {
                            Log.i(TAG, "host rate $rate -> $newRate")
                            try {
                                current.stop()
                            } catch (_: Throwable) {
                            }
                            current.release()
                            // Drop buffered samples of the old rate.
                            val available = runCatching {
                                inStream.available()
                            }.getOrDefault(0)
                            if (available > 0) {
                                runCatching { inStream.skip(available.toLong()) }
                            }
                            rate = newRate
                            current = buildTrack(rate)
                            audio = current
                            synchronized(this) { track = current }
                            current.play()
                            writtenFrames = 0L
                        }
                        lastRateCheck = now
                    }

                    val n = inStream.read(buf)
                    if (n <= 0) {
                        break
                    }
                    val usable = n and (FRAME_BYTES - 1).inv()
                    if (usable <= 0) {
                        continue
                    }
                    bb.clear()
                    bb.put(buf, 0, usable)
                    bb.flip()

                    // Drift trim: keep the FIFO + AudioTrack queue near half
                    // of its capacity. The pitch is fixed at 1.0.
                    val available = runCatching {
                        inStream.available()
                    }.getOrDefault(0)
                    val head = current.playbackHeadPosition.toLong() and
                            0xffffffffL
                    val buffered = (writtenFrames - head) and 0xffffffffL
                    val fill = available / FRAME_BYTES + buffered
                    val target = (current.bufferSizeInFrames / 2 + 4096)
                        .toLong().coerceAtLeast(4096L)
                    val delta = (fill - target).toDouble()
                    val speed = (1.0 + delta / target.toDouble() * 0.0002)
                        .coerceIn(0.9995, 1.0005)
                    if (abs(speed - 1.0) > 1e-6) {
                        runCatching {
                            current.playbackParams = PlaybackParams()
                                .setSpeed(speed.toFloat())
                                .setPitch(1.0f)
                        }
                    }

                    while (bb.hasRemaining() && running) {
                        val written = current.write(
                            bb,
                            bb.remaining(),
                            AudioTrack.WRITE_BLOCKING
                        )
                        if (written <= 0) {
                            break
                        }
                    }
                    writtenFrames += usable / FRAME_BYTES
                }
            } catch (t: Throwable) {
                Log.w(TAG, "fifo error: $t")
            } finally {
                if (audio != null) {
                    try {
                        audio.stop()
                    } catch (_: Throwable) {
                    }
                    audio.release()
                    synchronized(this) {
                        if (track === audio) {
                            track = null
                        }
                    }
                }
                try {
                    input?.close()
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
        synchronized(this) {
            try {
                track?.stop()
            } catch (_: Throwable) {
            }
            track?.release()
            track = null
        }
        thread?.interrupt()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
