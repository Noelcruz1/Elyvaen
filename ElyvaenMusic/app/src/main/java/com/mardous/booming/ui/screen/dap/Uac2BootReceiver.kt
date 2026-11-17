package com.mardous.booming.ui.screen.dap

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Restores the UAC2 bridge after a real reboot, even before the user opens
 * the app. The activity also calls ensureService(), but this receiver makes
 * the DAC available as soon as Android finishes booting.
 */
class Uac2BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Uac2Control.ensureService(context)
        }
    }
}
