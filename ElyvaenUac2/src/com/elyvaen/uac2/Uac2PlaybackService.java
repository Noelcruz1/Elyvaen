package com.elyvaen.uac2;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Plays the UAC2 capture stream through AudioTrack.
 *
 * elyvaen-uac2d writes float32/48 kHz stereo to a FIFO. This service reads
 * it and feeds AudioTrack with USAGE_MEDIA, so the Android audio HAL applies
 * the real output path, volume and DSP processing (the raw PCM path does not
 * get the HAL's stream calibration on this device).
 */
public class Uac2PlaybackService extends Service {
    private static final String TAG = "ElyvaenUac2Play";
    private static final String FIFO = "/data/local/tmp/elyvaen_uac2.pcm";
    private static final int RATE = 48000;
    private static final int CHANNELS = 2;
    private static final int FRAME_BYTES = CHANNELS * 4;
    private static final String CHANNEL_ID = "elyvaen_uac2";

    private volatile boolean running;
    private Thread thread;

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Elyvaen USB DAC",
                    NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(1, buildNotification());
        if (!running) {
            running = true;
            thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    playLoop();
                }
            }, "elyvaen-uac2-fifo");
            thread.start();
        }
        return START_STICKY;
    }

    private Notification buildNotification() {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= 26) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }
        return builder
                .setContentTitle("Elyvaen USB DAC")
                .setContentText("Reproduciendo desde USB")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .build();
    }

    private void playLoop() {
        while (running) {
            FileInputStream in = null;
            AudioTrack track = null;
            try {
                in = new FileInputStream(FIFO);
                int min = AudioTrack.getMinBufferSize(RATE,
                        AudioFormat.CHANNEL_OUT_STEREO,
                        AudioFormat.ENCODING_PCM_FLOAT);
                track = new AudioTrack.Builder()
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build())
                        .setAudioFormat(new AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                                .setSampleRate(RATE)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                                .build())
                        .setBufferSizeInBytes(Math.max(min * 4, 32768))
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build();
                track.play();
                Log.i(TAG, "fifo connected");

                byte[] buf = new byte[16384];
                ByteBuffer bb = ByteBuffer.allocateDirect(buf.length)
                        .order(ByteOrder.LITTLE_ENDIAN);
                long total = 0;

                while (running) {
                    int n = in.read(buf);
                    if (n <= 0) {
                        break;
                    }
                    int usable = n & ~(FRAME_BYTES - 1);
                    if (usable <= 0) {
                        continue;
                    }
                    bb.clear();
                    bb.put(buf, 0, usable);
                    bb.flip();
                    while (bb.hasRemaining() && running) {
                        int written = track.write(bb, bb.remaining(),
                                AudioTrack.WRITE_BLOCKING);
                        if (written <= 0) {
                            break;
                        }
                    }
                    total += usable;
                }
                Log.i(TAG, "fifo ended after " + total + " bytes");
            } catch (Throwable t) {
                Log.w(TAG, "fifo error: " + t);
            } finally {
                if (track != null) {
                    try {
                        track.stop();
                    } catch (Throwable ignored) {
                    }
                    track.release();
                }
                if (in != null) {
                    try {
                        in.close();
                    } catch (Throwable ignored) {
                    }
                }
            }
            if (running) {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
    }

    @Override
    public void onDestroy() {
        running = false;
        if (thread != null) {
            thread.interrupt();
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
