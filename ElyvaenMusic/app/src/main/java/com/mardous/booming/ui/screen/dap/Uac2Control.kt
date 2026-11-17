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
 * Settings.Global stores the user's choice for shell/tests, but the
 * authoritative state is kept in SharedPreferences because some ROMs drop
 * custom global keys across reboots.
 */
object Uac2Control {
    private const val TAG = "ElyvaenUac2"
    private const val PREFS_NAME = "elyvaen_uac2"
    private const val PREF_ENABLED = "enabled"
    private const val PREF_AUTOSTART = "autostart"
    private const val PREF_ADB_SAVED = "adb_saved"
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
        val modeNative: Boolean = true,
        val hostVolume: Int = 0,
        val hostMute: Boolean = false,
        val streaming: Boolean = false,
        val errors: Long = 0
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean {
        val prefs = prefs(context)
        if (prefs.contains(PREF_ENABLED)) {
            return prefs.getBoolean(PREF_ENABLED, false)
        }
        val legacy = runCatching {
            Settings.Global.getInt(context.contentResolver, SETTING_KEY, -1)
        }.getOrDefault(-1)
        if (legacy >= 0) {
            prefs.edit().putBoolean(PREF_ENABLED, legacy == 1).apply()
            return legacy == 1
        }
        return false
    }

    fun isAutoStart(context: Context): Boolean {
        val prefs = prefs(context)
        if (prefs.contains(PREF_AUTOSTART)) {
            return prefs.getBoolean(PREF_AUTOSTART, true)
        }
        val legacy = runCatching {
            Settings.Global.getInt(context.contentResolver, AUTOSTART_KEY, 1)
        }.getOrDefault(1)
        prefs.edit().putBoolean(PREF_AUTOSTART, legacy == 1).apply()
        return legacy == 1
    }

    fun setAutoStart(context: Context, enabled: Boolean): Boolean = runCatching {
        prefs(context).edit().putBoolean(PREF_AUTOSTART, enabled).apply()
        Settings.Global.putInt(
            context.contentResolver,
            AUTOSTART_KEY,
            if (enabled) 1 else 0
        )
        true
    }.getOrDefault(false)

    fun isAdbEnabled(context: Context): Boolean = runCatching {
        Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.ADB_ENABLED,
            0
        ) == 1
    }.getOrDefault(false)

    /**
     * Windows does not bind usbaudio2 when the composite gadget also exposes
     * the ADB interface. Remember whether USB debugging was on and turn it
     * off while UAC2 is active; restore it when the DAC is disabled.
     */
    private fun applyAdbMode(context: Context, uac2Enabled: Boolean): Boolean =
        runCatching {
            val prefs = prefs(context)
            if (uac2Enabled) {
                val wasEnabled = isAdbEnabled(context)
                prefs.edit().putBoolean(PREF_ADB_SAVED, wasEnabled).apply()
                if (wasEnabled) {
                    Settings.Global.putInt(
                        context.contentResolver,
                        Settings.Global.ADB_ENABLED,
                        0
                    )
                }
            } else {
                val restore = prefs.getBoolean(PREF_ADB_SAVED, false)
                if (restore) {
                    Settings.Global.putInt(
                        context.contentResolver,
                        Settings.Global.ADB_ENABLED,
                        1
                    )
                }
                prefs.edit().putBoolean(PREF_ADB_SAVED, false).apply()
            }
            true
        }.onFailure {
            Log.w(TAG, "cannot adjust ADB for UAC2", it)
        }.getOrDefault(false)

    fun refresh(context: Context): LinkStatus {
        val enabled = isEnabled(context)
        var rate = 0
        var native = true
        var modeNative = true
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
                        "mode" -> modeNative = value == "1"
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
            modeNative = modeNative,
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
            if (enabled) {
                // Do this before linking UAC2: Windows only loads its audio
                // driver when the ADB interface is not part of the gadget.
                applyAdbMode(context, true)
                runCatching {
                    context.startForegroundService(playbackService)
                }.onFailure {
                    Log.w(TAG, "cannot start UAC2 playback service", it)
                }
            } else {
                runCatching {
                    context.stopService(playbackService)
                }.onFailure {
                    Log.w(TAG, "cannot stop UAC2 playback service", it)
                }
            }
            context.sendBroadcast(command)
            prefs(context).edit().putBoolean(PREF_ENABLED, enabled).apply()
            Settings.Global.putInt(
                context.contentResolver,
                SETTING_KEY,
                if (enabled) 1 else 0
            )
            if (!enabled) {
                applyAdbMode(context, false)
            }
            true
        }.onFailure {
            Log.w(TAG, "cannot send UAC2 command", it)
        }.getOrDefault(false)
    }
}
