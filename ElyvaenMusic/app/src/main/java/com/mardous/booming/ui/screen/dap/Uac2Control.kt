package com.mardous.booming.ui.screen.dap

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log

/**
 * Native UAC2 control for the Elyvaen fork.
 *
 * The main app runs in the priv_app domain (full storage/media access, like
 * the original Booming), but priv_app cannot write system properties.
 *
 * A tiny preinstalled bridge (com.elyvaen.uac2) runs in the system_app
 * domain and listens for an explicit broadcast:
 *
 *   Intent("com.elyvaen.uac2.action.SET")
 *       .setComponent(ComponentName("com.elyvaen.uac2",
 *                                   "com.elyvaen.uac2.Uac2Receiver"))
 *       .putExtra("enabled", true/false)
 *
 * Explicit intents bypass Android 12 package visibility on this ROM, unlike
 * ContentResolver.call() to the bridge provider. The bridge writes:
 *
 *   persist.elyvaen.uac2=1/0 -> sys.elyvaen.usbdac.auto=1/0
 *   sys.elyvaen.usbdac.auto=1/0 -> start/stop elyvaen-usb
 *
 * Settings.Global stores the user's choice for the switch state.
 */
object Uac2Control {
    private const val TAG = "ElyvaenUac2"
    private const val SETTING_KEY = "elyvaen_uac2_enabled"
    private const val BRIDGE_PACKAGE = "com.elyvaen.uac2"
    private const val BRIDGE_RECEIVER = "com.elyvaen.uac2.Uac2Receiver"
    private const val ACTION_SET = "com.elyvaen.uac2.action.SET"
    private const val EXTRA_ENABLED = "enabled"

    data class Status(val enabled: Boolean, val state: String)

    fun isEnabled(context: Context): Boolean = runCatching {
        Settings.Global.getInt(context.contentResolver, SETTING_KEY, 0) == 1
    }.getOrDefault(false)

    fun refresh(context: Context): Status = Status(isEnabled(context), "")

    /**
     * Starts the app's own foreground service when the persisted UAC2 choice
     * is on. Called from MainActivity.onCreate so the DAC is ready after a
     * reboot/home launch without toggling the switch again.
     */
    fun ensureService(context: Context) {
        if (!isEnabled(context)) {
            return
        }
        val playbackService = Intent(context, Uac2PlaybackService::class.java)
        runCatching {
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(playbackService)
            } else {
                context.startService(playbackService)
            }
        }.onFailure {
            Log.w(TAG, "cannot ensure UAC2 playback service", it)
        }
    }

    fun setEnabled(context: Context, enabled: Boolean): Boolean {
        val command = Intent(ACTION_SET).apply {
            component = ComponentName(BRIDGE_PACKAGE, BRIDGE_RECEIVER)
            putExtra(EXTRA_ENABLED, enabled)
        }
        val playbackService = Intent(context, Uac2PlaybackService::class.java)
        return runCatching {
            // The app is in the foreground, so it can start the bridge's
            // foreground service even on Android 12. The service opens the
            // FIFO before the bridge enables the UAC2 daemon.
            runCatching {
                if (enabled) {
                    context.startForegroundService(playbackService)
                } else {
                    context.stopService(playbackService)
                }
            }.onFailure {
                Log.w(TAG, "cannot control UAC2 playback service", it)
            }
            context.sendBroadcast(command)
            Settings.Global.putInt(
                context.contentResolver,
                SETTING_KEY,
                if (enabled) 1 else 0
            )
            true
        }.onFailure {
            Log.w(TAG, "cannot send UAC2 command", it)
        }.getOrDefault(false)
    }
}
