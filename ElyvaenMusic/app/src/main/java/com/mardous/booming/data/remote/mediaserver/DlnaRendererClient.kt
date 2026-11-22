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

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Xml
import com.mardous.booming.data.model.Song
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.Locale

data class DlnaRenderer(
    val name: String,
    val location: String,
    val controlUrl: String,
    val udn: String
)

/**
 * Discovers UPnP MediaRenderers (TVs, AV receivers, DLNA speakers) and sends
 * the current song to their AVTransport endpoint.
 */
class DlnaRendererClient(
    private val context: Context,
    private val client: OkHttpClient
) {

    suspend fun discover(timeoutMs: Int = 3500): List<DlnaRenderer> {
        val lock = acquireMulticastLock()
        return try {
            val locations = searchLocations(timeoutMs)
            locations.mapNotNull { location ->
                runCatching { loadRenderer(location) }.getOrNull()
            }.distinctBy { it.udn.ifBlank { it.location } }
        } finally {
            runCatching { if (lock?.isHeld == true) lock.release() }
        }
    }

    fun play(renderer: DlnaRenderer, mediaUrl: String, song: Song): Result<Unit> = runCatching {
        val metadata = buildMetadata(song, mediaUrl)
        val setUri = soap(
            "AVTransport",
            "SetAVTransportURI",
            "<InstanceID>0</InstanceID>" +
                    "<CurrentURI>${DlnaXml.escape(mediaUrl)}</CurrentURI>" +
                    "<CurrentURIMetaData>${DlnaXml.escape(metadata)}</CurrentURIMetaData>"
        )
        postSoap(renderer.controlUrl, "AVTransport", "SetAVTransportURI", setUri)
        val play = soap(
            "AVTransport",
            "Play",
            "<InstanceID>0</InstanceID><Speed>1</Speed>"
        )
        postSoap(renderer.controlUrl, "AVTransport", "Play", play)
    }

    private fun searchLocations(timeoutMs: Int): List<String> {
        val locations = linkedSetOf<String>()
        val group = InetAddress.getByName(SSDP_ADDRESS)
        DatagramSocket().use { socket ->
            socket.soTimeout = timeoutMs
            socket.broadcast = true
            val query = "M-SEARCH * HTTP/1.1\r\n" +
                    "HOST: $SSDP_ADDRESS:1900\r\n" +
                    "MAN: \"ssdp:discover\"\r\n" +
                    "MX: 2\r\n" +
                    "ST: urn:schemas-upnp-org:device:MediaRenderer:1\r\n" +
                    "\r\n"
            val queryBytes = query.toByteArray(Charsets.UTF_8)
            socket.send(DatagramPacket(queryBytes, queryBytes.size, group, SSDP_PORT))
            val buffer = ByteArray(4096)
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val message = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    val location = parseHeader(message, "location") ?: continue
                    if (location.startsWith("http://") || location.startsWith("https://")) {
                        locations += location
                    }
                    if (locations.size >= MAX_RENDERERS) break
                } catch (_: Exception) {
                    break
                }
            }
        }
        return locations.toList()
    }

    private fun loadRenderer(location: String): DlnaRenderer? {
        val request = Request.Builder().url(location).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val xml = response.body.string()
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            parser.setInput(xml.reader())
            var friendlyName: String? = null
            var udn = ""
            var inService = false
            var serviceType = ""
            var controlUrl: String? = null
            var avControlUrl: String? = null
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "friendlyName" -> friendlyName = parser.nextText()
                        "UDN" -> udn = parser.nextText().removePrefix("uuid:")
                        "service" -> {
                            inService = true
                            serviceType = ""
                            controlUrl = null
                        }

                        "serviceType" -> if (inService) serviceType = parser.nextText()
                        "controlURL" -> if (inService) controlUrl = parser.nextText()
                    }
                } else if (event == XmlPullParser.END_TAG && parser.name == "service") {
                    if (serviceType.contains("AVTransport", ignoreCase = true) &&
                        !controlUrl.isNullOrBlank()
                    ) {
                        avControlUrl = resolveUrl(location, controlUrl)
                    }
                    inService = false
                }
                event = parser.next()
            }
            if (avControlUrl.isNullOrBlank()) return null
            return DlnaRenderer(
                name = friendlyName?.takeIf { it.isNotBlank() } ?: "TV / dispositivo DLNA",
                location = location,
                controlUrl = avControlUrl,
                udn = udn
            )
        }
    }

    private fun resolveUrl(base: String, relative: String): String {
        return base.toHttpUrlOrNull()?.resolve(relative)?.toString() ?: relative
    }

    private fun postSoap(
        controlUrl: String,
        service: String,
        action: String,
        body: String
    ) {
        val request = Request.Builder()
            .url(controlUrl)
            .post(body.toRequestBody(SOAP_MIME.toMediaType()))
            .header("SOAPACTION", "\"urn:schemas-upnp-org:service:$service:1#$action\"")
            .header("Content-Type", SOAP_MIME)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("El dispositivo rechazó $action (HTTP ${response.code})")
            }
        }
    }

    private fun soap(service: String, action: String, body: String): String {
        return """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"
 s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
 <s:Body><u:$action xmlns:u="urn:schemas-upnp-org:service:$service:1">
 $body
 </u:$action></s:Body></s:Envelope>"""
    }

    private fun buildMetadata(song: Song, mediaUrl: String): String {
        val mime = DlnaXml.mimeType(song)
        return """<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/"
 xmlns:dc="http://purl.org/dc/elements/1.1/"
 xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">
 <item id="song:${song.id}" parentID="0" restricted="1">
  <dc:title>${DlnaXml.escape(song.title)}</dc:title>
  <dc:creator>${DlnaXml.escape(song.artistName)}</dc:creator>
  <upnp:artist>${DlnaXml.escape(song.artistName)}</upnp:artist>
  <upnp:album>${DlnaXml.escape(song.albumName)}</upnp:album>
  <upnp:class>object.item.audioItem.musicTrack</upnp:class>
  <res protocolInfo="http-get:*:$mime:*">${DlnaXml.escape(mediaUrl)}</res>
 </item>
</DIDL-Lite>"""
    }

    private fun parseHeader(message: String, name: String): String? {
        return message.lineSequence().firstOrNull {
            it.substringBefore(':', "").trim().equals(name, ignoreCase = true)
        }?.substringAfter(':')?.trim()
    }

    private fun acquireMulticastLock(): WifiManager.MulticastLock? {
        return runCatching {
            context.getSystemService(WifiManager::class.java)
                ?.createMulticastLock("Elyvaen:DlnaSender")
                ?.apply {
                    setReferenceCounted(false)
                    acquire()
                }
        }.getOrNull()
    }

    companion object {
        private const val SSDP_ADDRESS = "239.255.255.250"
        private const val SSDP_PORT = 1900
        private const val MAX_RENDERERS = 12
        private const val SOAP_MIME = "text/xml; charset=utf-8"
    }
}
