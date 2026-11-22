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
import android.net.Uri
import android.os.Build
import com.mardous.booming.data.model.Album
import com.mardous.booming.data.model.Artist
import com.mardous.booming.data.model.Playlist
import com.mardous.booming.data.model.Song
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.InputStream
import java.net.URLDecoder
import java.util.Locale

/**
 * One local HTTP endpoint with three faces:
 *  - /dlna/...  UPnP MediaServer (description, ContentDirectory, media)
 *  - /dav/token/...  read-only WebDAV library
 *  - /web/token/...  browser remote control
 *
 * Only LAN access is intended. The web/WebDAV paths carry an ephemeral token.
 */
class MediaHttpServer(
    private val context: Context,
    private val index: MediaLibraryIndex,
    private val bridge: PlaybackBridge,
    private val token: String,
    private val uuid: String,
    port: Int,
    private val friendlyName: String
) : NanoHTTPD(port) {

    @Volatile
    var ipAddress: String = "127.0.0.1"

    private val baseUrl: String
        get() = "http://$ipAddress:listeningPort"

    private val webRoot = "/web/$token"
    private val davRoot = "/dav/$token"

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri ?: "/"
        return try {
            when {
                uri == "/dlna/description.xml" -> description()
                uri == "/dlna/ContentDirectory/scpd.xml" -> contentDirectoryScpd()
                uri == "/dlna/ConnectionManager/scpd.xml" -> connectionManagerScpd()
                uri == "/dlna/ContentDirectory/control" -> contentDirectoryControl(session)
                uri == "/dlna/ConnectionManager/control" -> connectionManagerControl(session)
                uri.startsWith("/dlna/media/") -> media(session, uri.removePrefix("/dlna/media/"))
                uri == webRoot || uri == "$webRoot/" -> webPage()
                uri.startsWith("$webRoot/api/") -> webApi(session)
                uri == davRoot || uri.startsWith("$davRoot/") -> dav(session)
                else -> text(Response.Status.NOT_FOUND, "No encontrado")
            }
        } catch (t: Throwable) {
            text(Response.Status.INTERNAL_ERROR, t.message ?: "Error del servidor")
        }
    }

    // ---------------------------------------------------------------------
    // UPnP description and SCPD
    // ---------------------------------------------------------------------

    private fun description(): Response {
        val model = Build.MODEL.orEmpty().ifBlank { "Android DAP" }
        val xml = """<?xml version="1.0" encoding="utf-8"?>
<root xmlns="urn:schemas-upnp-org:device-1-0"
      xmlns:dlna="urn:schemas-dlna-org:device-1-0">
  <specVersion><major>1</major><minor>0</minor></specVersion>
  <device>
    <deviceType>urn:schemas-upnp-org:device:MediaServer:1</deviceType>
    <friendlyName>${DlnaXml.escape(friendlyName)}</friendlyName>
    <manufacturer>Elyvaen</manufacturer>
    <modelName>Elyvaen DAP</modelName>
    <modelDescription>${DlnaXml.escape("Biblioteca local · $model")}</modelDescription>
    <modelNumber>1.8</modelNumber>
    <UDN>uuid:$uuid</UDN>
    <dlna:X_DLNADOC>DMS-1.50</dlna:X_DLNADOC>
    <serviceList>
      <service>
        <serviceType>urn:schemas-upnp-org:service:ContentDirectory:1</serviceType>
        <serviceId>urn:upnp-org:serviceId:ContentDirectory</serviceId>
        <SCPDURL>/dlna/ContentDirectory/scpd.xml</SCPDURL>
        <controlURL>/dlna/ContentDirectory/control</controlURL>
        <eventSubURL>/dlna/ContentDirectory/event</eventSubURL>
      </service>
      <service>
        <serviceType>urn:schemas-upnp-org:service:ConnectionManager:1</serviceType>
        <serviceId>urn:upnp-org:serviceId:ConnectionManager</serviceId>
        <SCPDURL>/dlna/ConnectionManager/scpd.xml</SCPDURL>
        <controlURL>/dlna/ConnectionManager/control</controlURL>
        <eventSubURL>/dlna/ConnectionManager/event</eventSubURL>
      </service>
    </serviceList>
    <presentationURL>$webRoot/</presentationURL>
  </device>
</root>"""
        return text(Response.Status.OK, xml, MIME_XML)
    }

    private fun contentDirectoryScpd(): Response {
        val xml = """<?xml version="1.0" encoding="utf-8"?>
<scpd xmlns="urn:schemas-upnp-org:service-1-0">
  <specVersion><major>1</major><minor>0</minor></specVersion>
  <actionList>
    <action>
      <name>Browse</name>
      <argumentList>
        <argument><name>ObjectID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_ObjectID</relatedStateVariable></argument>
        <argument><name>BrowseFlag</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_BrowseFlag</relatedStateVariable></argument>
        <argument><name>Filter</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_Filter</relatedStateVariable></argument>
        <argument><name>StartingIndex</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_Index</relatedStateVariable></argument>
        <argument><name>RequestedCount</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_Count</relatedStateVariable></argument>
        <argument><name>SortCriteria</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_SortCriteria</relatedStateVariable></argument>
        <argument><name>Result</name><direction>out</direction><relatedStateVariable>A_ARG_TYPE_Result</relatedStateVariable></argument>
        <argument><name>NumberReturned</name><direction>out</direction><relatedStateVariable>A_ARG_TYPE_Count</relatedStateVariable></argument>
        <argument><name>TotalMatches</name><direction>out</direction><relatedStateVariable>A_ARG_TYPE_Count</relatedStateVariable></argument>
        <argument><name>UpdateID</name><direction>out</direction><relatedStateVariable>A_ARG_TYPE_UpdateID</relatedStateVariable></argument>
      </argumentList>
    </action>
    <action>
      <name>GetSearchCapabilities</name>
      <argumentList><argument><name>SearchCaps</name><direction>out</direction><relatedStateVariable>SearchCapabilities</relatedStateVariable></argument></argumentList>
    </action>
    <action>
      <name>GetSortCapabilities</name>
      <argumentList><argument><name>SortCaps</name><direction>out</direction><relatedStateVariable>SortCapabilities</relatedStateVariable></argument></argumentList>
    </action>
    <action>
      <name>GetSystemUpdateID</name>
      <argumentList><argument><name>Id</name><direction>out</direction><relatedStateVariable>SystemUpdateID</relatedStateVariable></argument></argumentList>
    </action>
  </actionList>
  <serviceStateTable>
    <stateVariable sendEvents="no"><name>A_ARG_TYPE_ObjectID</name><dataType>string</dataType></stateVariable>
    <stateVariable sendEvents="no"><name>A_ARG_TYPE_Result</name><dataType>string</dataType></stateVariable>
    <stateVariable sendEvents="no"><name>A_ARG_TYPE_BrowseFlag</name><dataType>string</dataType><allowedValueList><allowedValue>BrowseMetadata</allowedValue><allowedValue>BrowseDirectChildren</allowedValue></allowedValueList></stateVariable>
    <stateVariable sendEvents="no"><name>A_ARG_TYPE_Filter</name><dataType>string</dataType></stateVariable>
    <stateVariable sendEvents="no"><name>A_ARG_TYPE_SortCriteria</name><dataType>string</dataType></stateVariable>
    <stateVariable sendEvents="no"><name>A_ARG_TYPE_Index</name><dataType>ui4</dataType></stateVariable>
    <stateVariable sendEvents="no"><name>A_ARG_TYPE_Count</name><dataType>ui4</dataType></stateVariable>
    <stateVariable sendEvents="no"><name>A_ARG_TYPE_UpdateID</name><dataType>ui4</dataType></stateVariable>
    <stateVariable sendEvents="no"><name>SearchCapabilities</name><dataType>string</dataType></stateVariable>
    <stateVariable sendEvents="no"><name>SortCapabilities</name><dataType>string</dataType></stateVariable>
    <stateVariable sendEvents="no"><name>SystemUpdateID</name><dataType>ui4</dataType></stateVariable>
  </serviceStateTable>
</scpd>"""
        return text(Response.Status.OK, xml, MIME_XML)
    }

    private fun connectionManagerScpd(): Response {
        val xml = """<?xml version="1.0" encoding="utf-8"?>
<scpd xmlns="urn:schemas-upnp-org:service-1-0">
  <specVersion><major>1</major><minor>0</minor></specVersion>
  <actionList>
    <action><name>GetProtocolInfo</name><argumentList>
      <argument><name>Source</name><direction>out</direction><relatedStateVariable>SourceProtocolInfo</relatedStateVariable></argument>
      <argument><name>Sink</name><direction>out</direction><relatedStateVariable>SinkProtocolInfo</relatedStateVariable></argument>
    </argumentList></action>
  </actionList>
  <serviceStateTable>
    <stateVariable sendEvents="yes"><name>SourceProtocolInfo</name><dataType>string</dataType></stateVariable>
    <stateVariable sendEvents="yes"><name>SinkProtocolInfo</name><dataType>string</dataType></stateVariable>
  </serviceStateTable>
</scpd>"""
        return text(Response.Status.OK, xml, MIME_XML)
    }

    private fun contentDirectoryControl(session: IHTTPSession): Response {
        val body = readBody(session)
        val action = session.headers["soapaction"]?.substringAfter('#')?.trim('"')
            ?: session.headers["SOAPACTION"]?.substringAfter('#')?.trim('"')
            ?: return soapError("Falta SOAPACTION")
        return when (action) {
            "Browse" -> browseAction(body)
            "GetSearchCapabilities" -> soapResponse(
                "ContentDirectory",
                "GetSearchCapabilities",
                "<SearchCaps></SearchCaps>"
            )

            "GetSortCapabilities" -> soapResponse(
                "ContentDirectory",
                "GetSortCapabilities",
                "<SortCaps>dc:title,upnp:artist,upnp:album</SortCaps>"
            )

            "GetSystemUpdateID" -> soapResponse(
                "ContentDirectory",
                "GetSystemUpdateID",
                "<Id>1</Id>"
            )

            else -> soapError("Acción no soportada: $action")
        }
    }

    private fun connectionManagerControl(session: IHTTPSession): Response {
        readBody(session)
        val action = session.headers["soapaction"]?.substringAfter('#')?.trim('"')
        return if (action == "GetProtocolInfo") {
            soapResponse(
                "ConnectionManager",
                "GetProtocolInfo",
                "<Source>http-get:*:audio/flac:*,http-get:*:audio/mpeg:*,http-get:*:audio/mp4:*,http-get:*:audio/ogg:*</Source><Sink></Sink>"
            )
        } else {
            soapError("Acción no soportada")
        }
    }

    private fun browseAction(body: String): Response {
        val objectId = xmlValue(body, "ObjectID") ?: "0"
        val flag = xmlValue(body, "BrowseFlag") ?: "BrowseDirectChildren"
        val start = xmlValue(body, "StartingIndex")?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val requested = xmlValue(body, "RequestedCount")?.toIntOrNull() ?: 0
        val snapshot = snapshot()
        val browse = browse(snapshot, objectId, flag, start, requested)
        return soapResponse(
            "ContentDirectory",
            "Browse",
            "<Result>${DlnaXml.escape(browse.didl)}</Result>" +
                    "<NumberReturned>${browse.returned}</NumberReturned>" +
                    "<TotalMatches>${browse.total}</TotalMatches>" +
                    "<UpdateID>1</UpdateID>"
        )
    }

    private fun browse(
        library: MediaLibrarySnapshot,
        objectId: String,
        flag: String,
        startIndex: Int,
        requestedCount: Int
    ): BrowseResult {
        val parent = parentOf(objectId)
        val allItems = mutableListOf<String>()
        var total = 0

        when {
            objectId == "0" -> {
                val containers = listOf(
                    Triple("artists", "Artistas", library.artists.size),
                    Triple("albums", "Álbumes", library.albums.size),
                    Triple("songs", "Canciones", library.songs.size),
                    Triple("playlists", "Listas", library.playlists.size)
                )
                allItems += containers.map { (id, title, count) ->
                    DlnaXml.container(id, "0", title, count)
                }
                total = containers.size
            }

            objectId == "artists" -> {
                allItems += library.artists.map { artist ->
                    DlnaXml.container("artist:${artist.id}", "artists", artist.name, artist.songs.size)
                }
                total = library.artists.size
            }

            objectId.startsWith("artist:") -> {
                val id = objectId.substringAfter(':').toLongOrNull() ?: -1L
                val songs = library.artistSongs(id)
                allItems += songs.map { DlnaXml.item(it, objectId, baseUrl, uuid) }
                total = songs.size
            }

            objectId == "albums" -> {
                allItems += library.albums.map { album ->
                    DlnaXml.container("album:${album.id}", "albums", album.name, album.songCount)
                }
                total = library.albums.size
            }

            objectId.startsWith("album:") -> {
                val id = objectId.substringAfter(':').toLongOrNull() ?: -1L
                val songs = library.albumSongs(id)
                allItems += songs.map { DlnaXml.item(it, objectId, baseUrl, uuid) }
                total = songs.size
            }

            objectId == "songs" -> {
                allItems += library.songs.map { DlnaXml.item(it, "songs", baseUrl, uuid) }
                total = library.songs.size
            }

            objectId == "playlists" -> {
                allItems += library.playlists.map { playlist ->
                    DlnaXml.container(
                        "playlist:${playlist.id}",
                        "playlists",
                        playlist.name,
                        library.playlistSongs[playlist.id]?.size ?: 0
                    )
                }
                total = library.playlists.size
            }

            objectId.startsWith("playlist:") -> {
                val id = objectId.substringAfter(':').toLongOrNull() ?: -1L
                val songs = library.playlistSongs[id].orEmpty()
                allItems += songs.map { DlnaXml.item(it, objectId, baseUrl, uuid) }
                total = songs.size
            }

            objectId.startsWith("song:") -> {
                val id = objectId.substringAfter(':').toLongOrNull() ?: -1L
                val song = library.song(id)
                if (song != null) {
                    allItems += DlnaXml.item(song, parent, baseUrl, uuid)
                }
                total = if (song != null) 1 else 0
            }
        }

        if (flag == "BrowseMetadata") {
            val metadata = when {
                objectId == "0" -> DlnaXml.container("0", "-1", "Elyvaen", total)
                objectId == "artists" || objectId == "albums" ||
                        objectId == "songs" || objectId == "playlists" ->
                    DlnaXml.container(objectId, "0", objectId, total)

                objectId.startsWith("artist:") -> {
                    val name = library.artists.firstOrNull {
                        it.id == objectId.substringAfter(':').toLongOrNull()
                    }?.name ?: "Artista"
                    DlnaXml.container(objectId, "artists", name, total)
                }

                objectId.startsWith("album:") -> {
                    val name = library.albums.firstOrNull {
                        it.id == objectId.substringAfter(':').toLongOrNull()
                    }?.name ?: "Álbum"
                    DlnaXml.container(objectId, "albums", name, total)
                }

                objectId.startsWith("playlist:") -> {
                    val name = library.playlists.firstOrNull {
                        it.id == objectId.substringAfter(':').toLongOrNull()
                    }?.name ?: "Lista"
                    DlnaXml.container(objectId, "playlists", name, total)
                }

                else -> allItems.firstOrNull().orEmpty()
            }
            return BrowseResult(DlnaXml.didl(metadata), if (metadata.isBlank()) 0 else 1, 1)
        }

        val from = startIndex.coerceIn(0, allItems.size)
        val to = if (requestedCount <= 0) {
            allItems.size
        } else {
            (from + requestedCount).coerceAtMost(allItems.size)
        }
        val page = allItems.subList(from, to)
        return BrowseResult(DlnaXml.didl(page.joinToString("")), total, page.size)
    }

    private fun parentOf(objectId: String): String {
        return when {
            objectId == "0" -> "-1"
            objectId.startsWith("artist:") -> "artists"
            objectId.startsWith("album:") -> "albums"
            objectId.startsWith("playlist:") -> "playlists"
            objectId.startsWith("song:") -> "songs"
            else -> "0"
        }
    }

    // ---------------------------------------------------------------------
    // Media
    // ---------------------------------------------------------------------

    private fun media(session: IHTTPSession, idValue: String): Response {
        val id = idValue.substringBefore('.').toLongOrNull()
            ?: return text(Response.Status.BAD_REQUEST, "ID no válido")
        val song = snapshot().song(id)
            ?: return text(Response.Status.NOT_FOUND, "Pista no encontrada")
        return streamSong(session, song)
    }

    private fun streamSong(session: IHTTPSession, song: Song): Response {
        val mime = DlnaXml.mimeType(song)
        val total = when {
            song.size > 0L -> song.size
            song.data.isNotBlank() -> java.io.File(song.data).takeIf { it.isFile }?.length() ?: 0L
            else -> 0L
        }
        val range = parseRange(session.headers["range"], total)
        val input = openSong(song)
            ?: return text(Response.Status.NOT_FOUND, "No se pudo abrir el archivo")
        val start = range?.first ?: 0L
        val length = range?.let { it.second - it.first + 1 } ?: total
        if (start > 0) skipFully(input, start)
        val status = if (range != null) Response.Status.PARTIAL_CONTENT else Response.Status.OK
        val response = newFixedLengthResponse(status, mime, input, length)
        response.addHeader("Accept-Ranges", "bytes")
        response.addHeader("transferMode.dlna.org", "Streaming")
        response.addHeader(
            "contentFeatures.dlna.org",
            "DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000"
        )
        if (range != null) {
            response.addHeader("Content-Range", "bytes $start-${range.second}/$total")
        }
        return response
    }

    private fun openSong(song: Song): InputStream? {
        return runCatching {
            context.contentResolver.openInputStream(song.uri)
                ?: if (song.data.isNotBlank()) java.io.File(song.data).inputStream() else null
        }.getOrNull()
    }

    private fun parseRange(header: String?, total: Long): Pair<Long, Long>? {
        if (header.isNullOrBlank() || total <= 0L) return null
        if (!header.startsWith("bytes=", ignoreCase = true)) return null
        val spec = header.substringAfter('=').substringBefore(',')
        val startText = spec.substringBefore('-').trim()
        val endText = spec.substringAfter('-', "").trim()
        if (startText.isBlank()) return null
        val start = startText.toLongOrNull() ?: return null
        val end = endText.toLongOrNull()?.coerceAtMost(total - 1) ?: (total - 1)
        if (start !in 0 until total || end < start) return null
        return start to end
    }

    private fun skipFully(input: InputStream, count: Long) {
        var remaining = count
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped <= 0) {
                if (input.read() < 0) break
                remaining--
            } else {
                remaining -= skipped
            }
        }
    }

    // ---------------------------------------------------------------------
    // WebDAV
    // ---------------------------------------------------------------------

    private fun dav(session: IHTTPSession): Response {
        val relative = session.uri.orEmpty()
            .removePrefix(davRoot)
            .trim('/')
            .let { URLDecoder.decode(it, Charsets.UTF_8.name()) }
        val library = snapshot()
        val tree = DavTree(library)
        val node = tree.node(relative) ?: return text(Response.Status.NOT_FOUND, "No encontrado")
        return when (session.method) {
            Method.OPTIONS -> text(Response.Status.OK, "").apply {
                addHeader("DAV", "1")
                addHeader("Allow", "OPTIONS, PROPFIND, GET, HEAD")
            }

            Method.PROPFIND -> {
                readBody(session)
                val depth = session.headers["depth"]?.toIntOrNull() ?: 1
                davResponse(node, tree, depth)
            }

            Method.GET -> {
                val song = node.song ?: return text(Response.Status.FORBIDDEN, "Es una carpeta")
                streamSong(session, song)
            }

            Method.HEAD -> {
                val song = node.song ?: return text(Response.Status.FORBIDDEN, "Es una carpeta")
                val length = song.size.coerceAtLeast(0L)
                newFixedLengthResponse(
                    Response.Status.OK,
                    DlnaXml.mimeType(song),
                    java.io.ByteArrayInputStream(ByteArray(0)),
                    0
                ).apply {
                    addHeader("Content-Length", length.toString())
                }
            }

            else -> text(Response.Status.METHOD_NOT_ALLOWED, "Solo lectura")
        }
    }

    private fun davResponse(node: DavNode, tree: DavTree, depth: Int): Response {
        val nodes = mutableListOf(node)
        if (depth != 0) nodes += node.children.mapNotNull { tree.node(it) }
        val xml = buildString {
            append("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
            append("<D:multistatus xmlns:D=\"DAV:\">")
            nodes.forEach { entry ->
                append("<D:response>")
                append("<D:href>${davHref(entry.path, entry.isDirectory)}</D:href>")
                append("<D:propstat><D:prop>")
                append("<D:displayname>${DlnaXml.escape(entry.name.ifBlank { "Elyvaen" })}</D:displayname>")
                if (entry.isDirectory) {
                    append("<D:resourcetype><D:collection/></D:resourcetype>")
                } else {
                    append("<D:resourcetype/>")
                    append("<D:getcontentlength>${entry.song?.size ?: 0L}</D:getcontentlength>")
                    append("<D:getcontenttype>${DlnaXml.escape(DlnaXml.mimeType(entry.song!!))}</D:getcontenttype>")
                }
                append("<D:getlastmodified>${java.util.Date().toGMTString()}</D:getlastmodified>")
                append("</D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat>")
                append("</D:response>")
            }
            append("</D:multistatus>")
        }
        return text(Response.Status.MULTI_STATUS, xml, MIME_XML).apply {
            addHeader("DAV", "1")
        }
    }

    private fun davHref(path: String, directory: Boolean): String {
        val encoded = DlnaXml.encodedPath(path)
        val suffix = if (directory && encoded.isNotEmpty()) "/" else ""
        return "$davRoot/$encoded$suffix".ifEmpty { davRoot }
    }

    // ---------------------------------------------------------------------
    // Web remote
    // ---------------------------------------------------------------------

    private fun webPage(): Response {
        val library = snapshot()
        val songs = library.songs.take(300)
        val html = """<!doctype html>
<html lang="es"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Elyvaen · Control</title>
<style>
:root{color-scheme:dark}*{box-sizing:border-box}body{margin:0;min-height:100vh;font-family:system-ui,sans-serif;
background:radial-gradient(circle at 20% 0%,#153b3c 0,#080b0d 50%,#050607 100%);color:#edf7f5;padding:22px}
main{width:min(720px,100%);margin:auto}.eyebrow{color:#6fe0c8;font-weight:800;letter-spacing:.18em;font-size:12px}
h1{font-size:clamp(26px,6vw,40px);margin:6px 0}.muted{color:#a9bcb8}.card{background:#11191c;border:1px solid #2c4a48;
border-radius:24px;padding:20px;margin-top:18px;box-shadow:0 20px 60px #0006}.now{font-size:22px;font-weight:800;margin:6px 0}
.artist{color:#a9bcb8;margin-bottom:16px}.controls{display:flex;gap:12px;align-items:center;justify-content:center;margin:16px 0}
button{border:0;border-radius:999px;background:#6fe0c8;color:#07211d;font-weight:800;padding:12px 18px;cursor:pointer;font-size:15px}
button.ghost{background:#1d302e;color:#d9fff6}input[type=range]{width:100%;accent-color:#6fe0c8}
ul{list-style:none;padding:0;margin:14px 0 0;max-height:420px;overflow:auto}li{display:flex;gap:10px;justify-content:space-between;
align-items:center;padding:9px 0;border-top:1px solid #203533;font-size:14px}li button{padding:7px 12px;font-size:12px}
a{color:#6fe0c8}footer{color:#6f8580;font-size:12px;margin-top:18px;line-height:1.6}
</style></head><body><main>
<div class="eyebrow">ELYVAEN · CONTROL WEB</div>
<h1>Tu música, desde cualquier pantalla</h1>
<p class="muted">Controla la reproducción del DAP desde esta página. Solo funciona en la misma red Wi-Fi.</p>
<section class="card"><div id="title" class="now">Cargando…</div><div id="artist" class="artist"></div>
<input id="seek" type="range" min="0" max="1000" value="0">
<div class="controls">
<button class="ghost" onclick="cmd('previous')">⏮</button>
<button onclick="cmd('toggle')">⏯</button>
<button class="ghost" onclick="cmd('next')">⏭</button>
</div><div id="status" class="muted"></div></section>
<section class="card"><div class="now">Biblioteca</div><div class="muted">Toca una canción para reproducirla en el DAP.</div>
<ul>${songs.joinToString("") { song ->
            "<li><span>${DlnaXml.escape(song.title)}<br><small>${DlnaXml.escape(song.artistName)}</small></span>" +
                    "<button onclick=\"playSong(${song.id})\">▶</button></li>"
        }}</ul></section>
<footer>WebDAV: <a href="$davRoot/">$davRoot/</a> · Servidor DLNA activo como
<b>${DlnaXml.escape(friendlyName)}</b> · Cierra el servidor desde Elyvaen cuando termines.</footer>
</main><script>
const base = '$webRoot/api';
let duration = 0;
async function refresh(){
  try {
    const r = await fetch(base + '/state', {cache:'no-store'});
    const s = await r.json();
    document.getElementById('title').textContent = s.title || 'Sin reproducción';
    document.getElementById('artist').textContent = s.artist || '';
    document.getElementById('status').textContent = s.connected ? (s.isPlaying ? 'Reproduciendo' : 'Pausado') : 'Sin conexión al reproductor';
    duration = s.durationMs || 0;
    const seek = document.getElementById('seek');
    if (document.activeElement !== seek && duration > 0) seek.value = Math.round((s.positionMs / duration) * 1000);
  } catch(e) {}
}
async function cmd(action){ await fetch(base + '/' + action, {method:'POST'}); refresh(); }
async function playSong(id){ await fetch(base + '/play?songId=' + id, {method:'POST'}); refresh(); }
document.getElementById('seek').addEventListener('change', async (e) => {
  if (duration > 0) await fetch(base + '/seek?position=' + Math.round(duration * e.target.value / 1000), {method:'POST'});
});
refresh(); setInterval(refresh, 2000);
</script></body></html>"""
        return text(Response.Status.OK, html, MIME_HTML)
    }

    private fun webApi(session: IHTTPSession): Response {
        val action = session.uri.orEmpty().removePrefix("$webRoot/api/").substringBefore('?')
        return when (action) {
            "state" -> {
                val state = runBlocking(Dispatchers.IO) { bridge.snapshot() }
                jsonResponse(
                    """{"connected":${state.connected},"title":${DlnaXml.json(state.title)},
                    |"artist":${DlnaXml.json(state.artist)},"album":${DlnaXml.json(state.album)},
                    |"isPlaying":${state.isPlaying},"positionMs":${state.positionMs},
                    |"durationMs":${state.durationMs},"queueSize":${state.queueSize}}""".trimMargin()
                )
            }

            "toggle" -> {
                runBlocking(Dispatchers.IO) { bridge.toggle() }
                jsonResponse("""{"ok":true}""")
            }

            "next" -> {
                runBlocking(Dispatchers.IO) { bridge.next() }
                jsonResponse("""{"ok":true}""")
            }

            "previous" -> {
                runBlocking(Dispatchers.IO) { bridge.previous() }
                jsonResponse("""{"ok":true}""")
            }

            "seek" -> {
                val position = session.parms["position"]?.toLongOrNull() ?: 0L
                runBlocking(Dispatchers.IO) { bridge.seekTo(position) }
                jsonResponse("""{"ok":true}""")
            }

            "play" -> {
                val id = session.parms["songId"]?.toLongOrNull() ?: 0L
                val song = snapshot().song(id)
                val ok = if (song != null) {
                    runBlocking(Dispatchers.IO) { bridge.playSong(song) }
                } else false
                jsonResponse("""{"ok":$ok}""")
            }

            else -> text(Response.Status.NOT_FOUND, "No encontrado")
        }
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private fun snapshot(): MediaLibrarySnapshot = runBlocking(Dispatchers.IO) { index.get() }

    private fun readBody(session: IHTTPSession): String {
        val length = session.headers["content-length"]?.toLongOrNull() ?: 0L
        if (length <= 0L) return ""
        val body = ByteArray(length.toInt())
        var read = 0
        while (read < body.size) {
            val count = session.inputStream.read(body, read, body.size - read)
            if (count < 0) break
            read += count
        }
        return String(body, 0, read, Charsets.UTF_8)
    }

    private fun xmlValue(body: String, tag: String): String? {
        val value = Regex("(?s)<$tag[^>]*>(.*?)</$tag>")
            .find(body)?.groupValues?.getOrNull(1) ?: return null
        return value
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
            .trim()
    }

    private fun soapResponse(service: String, action: String, body: String): Response {
        val xml = """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"
 s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
 <s:Body><u:${action}Response xmlns:u="urn:schemas-upnp-org:service:$service:1">
 $body
 </u:${action}Response></s:Body></s:Envelope>"""
        return text(Response.Status.OK, xml, MIME_XML)
    }

    private fun soapError(message: String): Response = soapResponse(
        "ContentDirectory",
        "Error",
        "<errorDescription>${DlnaXml.escape(message)}</errorDescription>"
    )

    private fun jsonResponse(body: String): Response =
        text(Response.Status.OK, body, MIME_JSON)

    private fun text(
        status: Response.Status,
        body: String,
        mime: String = "text/plain; charset=utf-8"
    ): Response {
        return newFixedLengthResponse(status, mime, body).apply {
            addHeader("Cache-Control", "no-store")
            addHeader("Connection", "close")
        }
    }

    private data class BrowseResult(
        val didl: String,
        val total: Int,
        val returned: Int
    )

    private data class DavNode(
        val path: String,
        val name: String,
        val isDirectory: Boolean,
        val song: Song? = null,
        val children: MutableList<String> = mutableListOf()
    )

    private class DavTree(library: MediaLibrarySnapshot) {
        private val nodes = LinkedHashMap<String, DavNode>()

        init {
            nodes[""] = DavNode("", "Elyvaen", true)
            nodes["Music"] = DavNode("Music", "Music", true)
            nodes[""]!!.children += "Music"
            library.songs.forEach { song ->
                val artist = DlnaXml.safeSegment(song.artistName, "Unknown Artist")
                val album = DlnaXml.safeSegment(song.albumName, "Unknown Album")
                val base = if (song.trackNumber > 0) {
                    "%02d - %s".format(Locale.US, song.trackNumber, song.title)
                } else {
                    song.title
                }
                val fileName = DlnaXml.safeSegment(base, "Track ${song.id}") +
                        "." + DlnaXml.davFileExtension(song)
                val artistPath = "Music/$artist"
                val albumPath = "$artistPath/$album"
                ensureDirectory(artistPath, artist, "Music")
                ensureDirectory(albumPath, album, artistPath)
                var path = "$albumPath/$fileName"
                var suffix = 2
                while (nodes.containsKey(path)) {
                    path = "$albumPath/${fileName.substringBeforeLast('.')} ($suffix)." +
                            fileName.substringAfterLast('.')
                    suffix++
                }
                nodes[path] = DavNode(path, fileName, false, song)
                nodes[albumPath]!!.children += path
            }
        }

        fun node(path: String): DavNode? {
            val normalized = path.trim('/')
            return nodes[normalized]
        }

        private fun ensureDirectory(path: String, name: String, parent: String) {
            if (nodes.containsKey(path)) return
            nodes[path] = DavNode(path, name, true)
            nodes[parent]?.children?.add(path)
        }
    }

    companion object {
        const val DEFAULT_PORT = 8200
        private const val MIME_XML = "application/xml; charset=utf-8"
        private const val MIME_JSON = "application/json; charset=utf-8"
        private const val MIME_HTML = "text/html; charset=utf-8"
    }
}
