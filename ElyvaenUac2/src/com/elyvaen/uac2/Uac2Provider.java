package com.elyvaen.uac2;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import java.lang.reflect.Method;

/**
 * Property bridge for the Elyvaen DAP.
 *
 * The provider process is forced into the system_app SELinux domain by
 * plat_seapp_contexts, which is the only app domain allowed to write
 * system_prop. The music app stays in priv_app (full storage access) and
 * calls this provider through ContentResolver.call().
 */
public class Uac2Provider extends ContentProvider {
    private static final String TAG = "ElyvaenUac2Bridge";

    private static final String PROP_ENABLED = "persist.elyvaen.uac2";
    private static final String PROP_AUTO = "sys.elyvaen.usbdac.auto";
    private static final String PROP_STATE = "sys.elyvaen.usbdac.state";

    private Method setMethod;
    private Method getMethod;

    @Override
    public boolean onCreate() {
        try {
            Class<?> systemProperties = Class.forName("android.os.SystemProperties");
            setMethod = systemProperties.getMethod(
                    "set", String.class, String.class);
            getMethod = systemProperties.getMethod(
                    "get", String.class, String.class);
        } catch (Throwable t) {
            Log.e(TAG, "android.os.SystemProperties unavailable", t);
        }
        return true;
    }

    private boolean setProperty(String key, String value) {
        if (setMethod == null) {
            return false;
        }
        try {
            setMethod.invoke(null, key, value);
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "cannot set " + key + "=" + value, t);
            return false;
        }
    }

    private String getProperty(String key, String fallback) {
        if (getMethod == null) {
            return fallback;
        }
        try {
            Object value = getMethod.invoke(null, key, fallback);
            return value instanceof String ? (String) value : fallback;
        } catch (Throwable t) {
            return fallback;
        }
    }

    private void updatePlaybackService(boolean enabled) {
        try {
            Intent intent = new Intent(getContext(),
                    Uac2PlaybackService.class);
            if (enabled) {
                if (Build.VERSION.SDK_INT >= 26) {
                    getContext().startForegroundService(intent);
                } else {
                    getContext().startService(intent);
                }
            } else {
                getContext().stopService(intent);
            }
        } catch (Throwable t) {
            Log.e(TAG, "playback service", t);
        }
    }

    /**
     * Debug aid: play a 1 kHz tone through AudioTrack so the Android HAL
     * configures the real output path. Used to dump the mixer state while
     * local audio is active.
     */
    private void startTone() {
        new Thread(new Runnable() {
            @Override
            public void run() {
            final int sr = 48000;
            final int frames = 960; /* 20 cycles of 1 kHz */
            short[] buf = new short[frames * 2];
            for (int i = 0; i < frames; i++) {
                short s = (short) (Math.sin(2.0 * Math.PI * 1000.0 * i / sr)
                        * 0.3 * 32767.0);
                buf[i * 2] = s;
                buf[i * 2 + 1] = s;
            }
            int min = AudioTrack.getMinBufferSize(sr,
                    AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT);
            AudioTrack track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sr)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .build())
                    .setBufferSizeInBytes(Math.max(min, buf.length * 4))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build();
            track.play();
            long end = System.currentTimeMillis() + 30000;
            while (System.currentTimeMillis() < end) {
                track.write(buf, 0, buf.length);
            }
            try {
                track.stop();
            } catch (Throwable ignored) {
            }
            track.release();
            }
        }, "elyvaen-tone").start();
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Bundle result = new Bundle();
        if ("set".equals(method)) {
            boolean enabled = "1".equals(arg);
            updatePlaybackService(enabled);
            boolean enabledOk = setProperty(PROP_ENABLED, enabled ? "1" : "0");
            // The vendor init .rc relays persist.elyvaen.uac2 to
            // sys.elyvaen.usbdac.auto. The direct set is a fast path and
            // only makes sense if the persistent choice was accepted.
            if (enabledOk) {
                setProperty(PROP_AUTO, enabled ? "1" : "0");
            }
            result.putBoolean("ok", enabledOk);
            result.putBoolean("enabled", enabled);
        } else if ("state".equals(method)) {
            result.putBoolean(
                    "enabled", "1".equals(getProperty(PROP_ENABLED, "0")));
            result.putString("state", getProperty(PROP_STATE, ""));
        } else if ("mixdump".equals(method)) {
            /* Force a 0 -> 1 transition so the init trigger fires on
             * every call. The one-shot service logs all controls. */
            setProperty("sys.elyvaen.mixdump", "0");
            result.putBoolean(
                    "ok", setProperty("sys.elyvaen.mixdump", "1"));
        } else if ("playtone".equals(method)) {
            startTone();
            result.putBoolean("ok", true);
        } else {
            result.putBoolean("ok", false);
            Log.w(TAG, "unknown method: " + method);
        }
        return result;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        return 0;
    }
}
