package com.mardous.booming.ui.screen.dap

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import java.io.File

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
    private const val AUTOSTART_KEY = "elyvaen_uac2_autostart"
    private const val BRIDGE_PACKAGE = "com.elyvaen.uac2"
    private const val BRIDGE_RECEIVER = "com.elyvaen.uac2.Uac2Receiver"
    private const val ACTION_SET = "com.elyvaen.uac2.action.SET"
    private const val EXTRA_ENABLED = "enabled"
    private const val RATE_FILE = "/data/local/tmp/elyvaen_uac2.rate"
    private const val MODE_FILE = "/data/local/tmp/elyvaen_uac2.mode"
    private const val STATUS_FILE = "/data/local/tmp/elyvaen_uac2.status"

    data class LinkStatus(
        val enabled: Boolean = false,
        val rate: Int = 0,
        val native: Boolean = true,
        val hostVolume: Int = 0,
        val hostMute: Boolean = false,
        val streaming: Boolean = false,
        val errors: Long = 0
    )

    fun isEnabled(context: Context): Boolean = runCatching {
        Settings.Global.getInt(context.contentResolver, SETTING_KEY, 0) == 1
    }.getOrDefault(false)

    fun isAutoStart(context: Context): Boolean = runCatching {
        Settings.Global.getInt(context.contentResolver, AUTOSTART_KEY, 1) == 1
    }.getOrDefault(true)

    fun setAutoStart(context: Context, enabled: Boolean): Boolean = runCatching {
        Settings.Global.putInt(
            context.contentResolver,
            AUTOSTART_KEY,
            if (enabled) 1 else 0
        )
        true
    }.getOrDefault(false)

    fun refresh(context: Context): LinkStatus {
        val enabled = isEnabled(context)
        var rate = 0
        var native = true
        var hostVolume = 0
        var hostMute = false
        var streaming = false
        var errors = 0L
        runCatching {
            File(STATUS_FILE).forEachLine { line ->
                val parts = line.split('=', limit = 2)
                if (parts.size == 2) {
                    val value = parts[1]
                    when (parts[0]) {
                        "rate" -> rate = value.toIntOrNull() ?: rate
                        "native" -> native = value == "1"
                        "host_volume" -> hostVolume = value.toIntOrNull() ?: hostVolume
                        "host_mute" -> hostMute = value == "1"
                        "streaming" -> streaming = value == "1"
                        "errors" -> errors = value.toLongOrNull() ?: errors
                    }
                }
            }
        }
        if (rate == 0) {
            runCatching {
                File(RATE_FILE).readText().trim().toInt()
            }.onSuccess { rate = it }
        }
        return LinkStatus(
            enabled = enabled,
            rate = rate,
            native = native,
            hostVolume = hostVolume,
            hostMute = hostMute,
            streaming = streaming,
            errors = errors
        )
    }

    /**
     * The daemon polls this file and reopens the stream when the user switches
     * between native host rate and the 48 kHz compatibility resampler.
     */
    fun setQuality(context: Context, native: Boolean): Boolean = runCatching {
        val file = File(MODE_FILE)
        file.writeText(if (native) "native\n" else "48000\n")
        file.setReadable(true, false)
        true
    }.onFailure {
        Log.w(TAG, "cannot write UAC2 quality mode", it)
    }.getOrDefault(false)

    /**
     * Starts the app's own foreground service when the persisted UAC2 choice
     * is on. Called from MainActivity.onCreate so the DAC is ready after a
     * reboot/home launch without toggling the switch again.
     */
    fun ensureService(context: Context) {
        if (!isEnabled(context) || !isAutoStart(context)) {
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
            // The app is in the foreground, so it can start its own
            // foreground service even on Android 12.
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
