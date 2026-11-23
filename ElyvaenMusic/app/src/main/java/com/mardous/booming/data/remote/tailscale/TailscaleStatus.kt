/*
 * Copyright (c) 2026 Christians Martínez Alvarado
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.mardous.booming.data.remote.tailscale

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

data class TailscaleInfo(
    val installed: Boolean = false,
    val vpnActive: Boolean = false,
    val tailnetAddress: String? = null
) {
    val connected: Boolean
        get() = installed && vpnActive && tailnetAddress != null
}

/**
 * Read-only Tailscale detection. Elyvaen never embeds Tailscale or stores its
 * credentials; it only checks whether the official app is installed and
 * whether a VPN with a CGNAT/tailnet address is active.
 */
class TailscaleStatus(private val context: Context) {

    fun snapshot(): TailscaleInfo {
        val installed = isInstalled()
        val vpnActive = isVpnActive()
        return TailscaleInfo(
            installed = installed,
            vpnActive = vpnActive,
            tailnetAddress = if (vpnActive) findTailnetAddress() else null
        )
    }

    fun isInstalled(): Boolean = runCatching {
        context.packageManager.getPackageInfo(TAILSCALE_PACKAGE, 0)
        true
    }.getOrDefault(false)

    fun isVpnActive(): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = manager.activeNetwork ?: return false
        return manager.getNetworkCapabilities(network)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
    }

    fun isMagicDnsHost(host: String): Boolean =
        normalizeHost(host).endsWith(".ts.net", ignoreCase = true)

    fun isTailnetIpv4(host: String): Boolean {
        val parts = normalizeHost(host).split('.')
        if (parts.size != 4) return false
        val numbers = IntArray(4)
        for (index in 0 until 4) {
            numbers[index] = parts[index].toIntOrNull() ?: return false
        }
        if (numbers.any { it !in 0..255 }) return false
        return numbers[0] == 100 && numbers[1] in 64..127
    }

    fun requiresTailscale(host: String): Boolean =
        isMagicDnsHost(host) || isTailnetIpv4(host)

    /**
     * Returns a human-readable reason when a MagicDNS or tailnet host cannot
     * work without the official Tailscale app connected.
     */
    fun connectionError(host: String): String? {
        if (!requiresTailscale(host)) return null
        val info = snapshot()
        if (!info.installed) {
            return "Instala Tailscale y conéctate para usar MagicDNS"
        }
        if (!info.vpnActive) {
            return "Activa Tailscale para acceder a este host"
        }
        return null
    }

    fun openApp() {
        val launchIntent = context.packageManager
            .getLaunchIntentForPackage(TAILSCALE_PACKAGE)
        if (launchIntent != null) {
            context.startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } else {
            val market = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("market://details?id=$TAILSCALE_PACKAGE")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val web = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=$TAILSCALE_PACKAGE")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(market) }
                .recoverCatching { context.startActivity(web) }
        }
    }

    private fun findTailnetAddress(): String? = runCatching {
        Collections.list(NetworkInterface.getNetworkInterfaces())
            .asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { Collections.list(it.inetAddresses).asSequence() }
            .filterIsInstance<Inet4Address>()
            .mapNotNull { it.hostAddress }
            .firstOrNull { isTailnetIpv4(it) }
    }.getOrNull()

    private fun normalizeHost(raw: String): String {
        var host = raw.trim()
        if (host.startsWith("https://", ignoreCase = true)) {
            host = host.substring(8)
        } else if (host.startsWith("http://", ignoreCase = true)) {
            host = host.substring(7)
        }
        return host
            .substringBefore('/')
            .substringBefore(':')
            .trimEnd('.')
    }

    companion object {
        const val TAILSCALE_PACKAGE = "com.tailscale.ipn"
    }
}
