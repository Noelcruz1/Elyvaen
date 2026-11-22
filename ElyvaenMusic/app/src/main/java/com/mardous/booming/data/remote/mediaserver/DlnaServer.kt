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

package com.mardous.booming.data.remote.mediaserver

import java.net.DatagramPacket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Minimal SSDP advertiser for the UPnP MediaServer device. TVs discover the
 * HTTP description through this socket and then browse ContentDirectory.
 */
class DlnaServer(
    private val ipAddress: String,
    private val port: Int,
    private val uuid: String,
    private val friendlyName: String
) {

    private val running = AtomicBoolean(false)
    private var socket: MulticastSocket? = null
    private var thread: Thread? = null

    private val location: String
        get() = "http://$ipAddress:$port/dlna/description.xml"

    fun start() {
        if (!running.compareAndSet(false, true)) return
        val group = InetAddress.getByName(SSDP_ADDRESS)
        val wifiInterface = findWifiInterface(ipAddress)
        val multicast = MulticastSocket(null).apply {
            reuseAddress = true
            bind(InetSocketAddress(SSDP_PORT))
            timeToLive = 4
        }
        socket = multicast
        runCatching {
            if (wifiInterface != null) {
                multicast.joinGroup(InetSocketAddress(group, SSDP_PORT), wifiInterface)
            } else {
                @Suppress("DEPRECATION")
                multicast.joinGroup(group)
            }
        }
        sendNotifications("ssdp:alive")
        thread = Thread({ loop() }, "elyvaen-ssdp").also { it.start() }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        runCatching { sendNotifications("ssdp:byebye") }
        runCatching { socket?.close() }
        socket = null
        thread?.interrupt()
        thread = null
    }

    fun serverName(): String = friendlyName

    private fun loop() {
        val buffer = ByteArray(2048)
        while (running.get()) {
            try {
                val packet = DatagramPacket(buffer, buffer.size)
                socket?.receive(packet) ?: break
                val message = String(packet.data, 0, packet.length, Charsets.UTF_8)
                if (!message.startsWith("M-SEARCH", ignoreCase = true)) continue
                if (!message.contains("ssdp:discover", ignoreCase = true)) continue
                val headers = parseHeaders(message)
                val st = headers["st"] ?: continue
                val response = buildResponse(st)
                val bytes = response.toByteArray(Charsets.UTF_8)
                socket?.send(DatagramPacket(bytes, bytes.size, packet.address, packet.port))
            } catch (_: Exception) {
                if (!running.get()) break
            }
        }
    }

    private fun buildResponse(st: String): String {
        val usn = if (st == "upnp:rootdevice" || st == "ssdp:all") {
            "uuid:$uuid"
        } else {
            "uuid:$uuid::$st"
        }
        return buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("CACHE-CONTROL: max-age=1800\r\n")
            append("DATE: ${java.util.Date()}\r\n")
            append("EXT:\r\n")
            append("LOCATION: $location\r\n")
            append("SERVER: $SERVER\r\n")
            append("ST: $st\r\n")
            append("USN: $usn\r\n")
            append("BOOTID.UPNP.ORG: 1\r\n")
            append("CONFIGID.UPNP.ORG: 1\r\n")
            append("\r\n")
        }
    }

    private fun sendNotifications(nts: String) {
        val targets = listOf(
            "upnp:rootdevice",
            "uuid:$uuid",
            "urn:schemas-upnp-org:device:MediaServer:1",
            "urn:schemas-upnp-org:service:ContentDirectory:1",
            "urn:schemas-upnp-org:service:ConnectionManager:1"
        )
        val group = InetAddress.getByName(SSDP_ADDRESS)
        targets.forEach { nt ->
            val usn = if (nt == "upnp:rootdevice") "uuid:$uuid::$nt" else "uuid:$uuid::$nt"
            val message = buildString {
                append("NOTIFY * HTTP/1.1\r\n")
                append("HOST: $SSDP_ADDRESS:$SSDP_PORT\r\n")
                append("CACHE-CONTROL: max-age=1800\r\n")
                append("LOCATION: $location\r\n")
                append("NT: $nt\r\n")
                append("NTS: $nts\r\n")
                append("SERVER: $SERVER\r\n")
                append("USN: $usn\r\n")
                append("BOOTID.UPNP.ORG: 1\r\n")
                append("CONFIGID.UPNP.ORG: 1\r\n")
                append("\r\n")
            }
            val bytes = message.toByteArray(Charsets.UTF_8)
            runCatching {
                socket?.send(DatagramPacket(bytes, bytes.size, group, SSDP_PORT))
            }
        }
    }

    private fun parseHeaders(message: String): Map<String, String> {
        return message.lineSequence()
            .drop(1)
            .mapNotNull { line ->
                val index = line.indexOf(':')
                if (index <= 0) null
                else line.substring(0, index).trim().lowercase() to
                        line.substring(index + 1).trim()
            }
            .toMap()
    }

    private fun findWifiInterface(ip: String): NetworkInterface? {
        return runCatching {
            NetworkInterface.getNetworkInterfaces()
                ?.toList()
                ?.firstOrNull { network ->
                    network.isUp && !network.isLoopback && network.inetAddresses
                        .toList()
                        .any { address -> address is Inet4Address && address.hostAddress == ip }
                }
        }.getOrNull()
    }

    companion object {
        private const val SSDP_ADDRESS = "239.255.255.250"
        private const val SSDP_PORT = 1900
        private const val SERVER = "Android/12 UPnP/1.1 Elyvaen/1.8"
    }
}
