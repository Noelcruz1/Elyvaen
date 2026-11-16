package com.mardous.booming.ui.screen.dap

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.IBinder
import android.util.Log
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Plays the UAC2 FIFO through AudioTrack.
 *
 * elyvaen-uac2d captures the USB gadget and writes float32/48 kHz stereo
 * to /data/local/tmp/elyvaen_uac2.pcm. This service feeds it to the Android
 * audio HAL with USAGE_MEDIA, so the same output path, volume and DSP
 * processing used by local playback are applied to the USB DAC stream.
 */
class Uac2PlaybackService : Service() {
    companion object {
        private const val TAG = "ElyvaenUac2Play"
        private const val FIFO = "/data/local/tmp/elyvaen_uac2.pcm"
        private const val RATE = 48000
        private const val CHANNELS = 2
        private const val FRAME_BYTES = CHANNELS * 4
        private const val CHANNEL_ID = "elyvaen_uac2"
    }

    @Volatile
    private var running = false
    private var thread: Thread? = null

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

    private fun playLoop() {
        while (running) {
            var input: FileInputStream? = null
            var track: AudioTrack? = null
            try {
                input = FileInputStream(FIFO)
                val min = AudioTrack.getMinBufferSize(
                    RATE,
                    AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_FLOAT
                )
                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                            .setSampleRate(RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .build()
                    )
                    .setBufferSizeInBytes(maxOf(min * 4, 32768))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                track.play()
                Log.i(TAG, "fifo connected")

                val buf = ByteArray(16384)
                val bb = ByteBuffer.allocateDirect(buf.size)
                    .order(ByteOrder.LITTLE_ENDIAN)
                while (running) {
                    val n = input.read(buf)
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
                    while (bb.hasRemaining() && running) {
                        val written = track.write(
                            bb,
                            bb.remaining(),
                            AudioTrack.WRITE_BLOCKING
                        )
                        if (written <= 0) {
                            break
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "fifo error: $t")
            } finally {
                try {
                    track?.stop()
                } catch (_: Throwable) {
                }
                track?.release()
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
        thread?.interrupt()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
