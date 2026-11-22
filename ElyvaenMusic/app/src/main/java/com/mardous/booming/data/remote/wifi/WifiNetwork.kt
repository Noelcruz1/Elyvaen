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

package com.mardous.booming.data.remote.wifi

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.NetworkInterface

object WifiNetwork {

    fun isWifiConnected(context: Context): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = manager.activeNetwork ?: return false
        return manager.getNetworkCapabilities(network)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }

    /**
     * IPv4 used by the browser on the same Wi-Fi. The Wi-Fi interface is
     * preferred over other interfaces (for example a VPN tunnel).
     */
    fun localIpv4(): String? {
        val addresses = runCatching {
            NetworkInterface.getNetworkInterfaces()
                ?.toList()
                ?.filter { it.isUp && !it.isLoopback }
                ?.sortedBy { if (it.name.startsWith("wlan")) 0 else 1 }
                ?.flatMap { it.inetAddresses.toList() }
                .orEmpty()
        }.getOrDefault(emptyList())
        val ipv4 = addresses.filterIsInstance<Inet4Address>()
        return (ipv4.firstOrNull { it.isSiteLocalAddress } ?: ipv4.firstOrNull())
            ?.hostAddress
    }
}
