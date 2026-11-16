package com.elyvaen.uac2;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import java.lang.reflect.Method;

/**
 * Explicit-broadcast entry point for the Elyvaen music app.
 *
 * Explicit intents are not filtered by Android 12 package visibility, which
 * makes this more reliable than ContentResolver.call() on this ROM. The
 * receiver is protected by WRITE_SECURE_SETTINGS and runs in the system_app
 * domain (see plat_seapp_contexts), so it can write system properties.
 */
public class Uac2Receiver extends BroadcastReceiver {
    static final String ACTION_SET = "com.elyvaen.uac2.action.SET";
    static final String EXTRA_ENABLED = "enabled";

    private static final String TAG = "ElyvaenUac2Bridge";
    private static final String PROP_ENABLED = "persist.elyvaen.uac2";
    private static final String PROP_AUTO = "sys.elyvaen.usbdac.auto";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_SET.equals(intent.getAction())) {
            return;
        }
        boolean enabled = intent.getBooleanExtra(EXTRA_ENABLED, false);
        boolean ok = setProperty(PROP_ENABLED, enabled ? "1" : "0");
        if (ok) {
            setProperty(PROP_AUTO, enabled ? "1" : "0");
        }
        Log.i(TAG, "set enabled=" + enabled + " ok=" + ok);
    }

    private static void updatePlaybackService(Context context, boolean enabled) {
        try {
            Intent intent = new Intent(context, Uac2PlaybackService.class);
            if (enabled) {
                if (Build.VERSION.SDK_INT >= 26) {
                    context.startForegroundService(intent);
                } else {
                    context.startService(intent);
                }
            } else {
                context.stopService(intent);
            }
        } catch (Throwable t) {
            Log.e(TAG, "playback service", t);
        }
    }

    private static boolean setProperty(String key, String value) {
        try {
            Class<?> systemProperties = Class.forName("android.os.SystemProperties");
            Method set = systemProperties.getMethod(
                    "set", String.class, String.class);
            set.invoke(null, key, value);
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "cannot set " + key + "=" + value, t);
            return false;
        }
    }
}
