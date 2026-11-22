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

import android.webkit.MimeTypeMap
import com.mardous.booming.data.model.Song
import java.net.URLEncoder
import java.util.Locale

object DlnaXml {

    const val DIDL_HEADER = "<DIDL-Lite " +
            "xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" " +
            "xmlns:dc=\"http://purl.org/dc/elements/1.1/\" " +
            "xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\">"

    fun didl(content: String): String = "$DIDL_HEADER$content</DIDL-Lite>"

    fun container(
        id: String,
        parentId: String,
        title: String,
        childCount: Int = 0,
        upnpClass: String = "object.container.storageFolder"
    ): String {
        return "<container id=\"${escape(id)}\" parentID=\"${escape(parentId)}\" " +
                "restricted=\"1\" childCount=\"$childCount\">" +
                "<dc:title>${escape(title)}</dc:title>" +
                "<upnp:class>$upnpClass</upnp:class>" +
                "</container>"
    }

    fun item(song: Song, parentId: String, baseUrl: String, serverUuid: String): String {
        val mime = mimeType(song)
        val url = "$baseUrl/dlna/media/${song.id}"
        val protocol = "http-get:*:$mime:DLNA.ORG_OP=01;DLNA.ORG_CI=0;" +
                "DLNA.ORG_FLAGS=01700000000000000000000000000000"
        return "<item id=\"song:${song.id}\" parentID=\"${escape(parentId)}\" restricted=\"1\">" +
                "<dc:title>${escape(song.title.ifBlank { song.fileName })}</dc:title>" +
                "<dc:creator>${escape(song.artistName)}</dc:creator>" +
                "<upnp:artist>${escape(song.artistName)}</upnp:artist>" +
                "<upnp:album>${escape(song.albumName)}</upnp:album>" +
                (song.genreName?.takeIf { it.isNotBlank() }?.let {
                    "<upnp:genre>${escape(it)}</upnp:genre>"
                } ?: "") +
                "<upnp:class>object.item.audioItem.musicTrack</upnp:class>" +
                "<res protocolInfo=\"${escape(protocol)}\" " +
                "duration=\"${duration(song.duration)}\" size=\"${song.size}\">" +
                "${escape(url)}</res>" +
                "</item>"
    }

    fun mimeType(song: Song): String {
        val extension = extension(song)
        return MIME_TYPES[extension]
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: "audio/mpeg"
    }

    fun extension(song: Song): String {
        return song.data.substringAfterLast('.', "").lowercase(Locale.ROOT)
            .takeIf { it.length in 1..5 }
            ?: "mp3"
    }

    fun duration(millis: Long): String {
        val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    }

    fun davFileExtension(song: Song): String = extension(song)

    fun safeSegment(value: String, fallback: String): String {
        return value
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
            .trim()
            .trim('.')
            .take(120)
            .ifBlank { fallback }
    }

    fun encodedPath(path: String): String {
        return path.split('/')
            .filter { it.isNotBlank() }
            .joinToString("/") { segment ->
                URLEncoder.encode(segment, Charsets.UTF_8.name()).replace("+", "%20")
            }
    }

    fun escape(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    fun json(value: String): String {
        return "\"" + value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r") + "\""
    }

    private val MIME_TYPES = mapOf(
        "flac" to "audio/flac",
        "mp3" to "audio/mpeg",
        "m4a" to "audio/mp4",
        "aac" to "audio/aac",
        "ogg" to "audio/ogg",
        "opus" to "audio/opus",
        "wav" to "audio/wav",
        "aif" to "audio/aiff",
        "aiff" to "audio/aiff",
        "wma" to "audio/x-ms-wma",
        "ape" to "audio/x-ape",
        "dsf" to "audio/dsf",
        "dff" to "audio/dff",
        "mka" to "audio/x-matroska"
    )
}
