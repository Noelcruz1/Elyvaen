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

package com.mardous.booming.data.remote.nas

import android.net.Uri
import android.util.Xml
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import com.mardous.booming.data.local.NasProtocol
import com.mardous.booming.data.local.NasSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.sftp.OpenMode
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.transport.verification.FingerprintVerifier
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import org.xmlpull.v1.XmlPullParser
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class NasEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0,
    val modified: Long = 0
)

interface NasSession : Closeable {
    suspend fun list(path: String): List<NasEntry>
    suspend fun download(path: String, output: OutputStream): Long
    suspend fun upload(input: InputStream, path: String, size: Long)
    suspend fun mkdir(path: String)
}

/**
 * Browsing and transfer client for the NAS configured in the Hub. The session
 * is opened once per screen action so a folder download does not
 * re-authenticate for every file.
 */
class NasClient(private val okHttpClient: OkHttpClient) {

    suspend fun <T> withSession(
        settings: NasSettings,
        block: suspend (NasSession) -> T
    ): T = withContext(Dispatchers.IO) {
        val session: NasSession = when (settings.protocol) {
            NasProtocol.SMB -> SmbNasSession(settings)
            NasProtocol.WEBDAV -> WebDavNasSession(settings, okHttpClient)
            NasProtocol.SFTP -> SftpNasSession(settings)
        }
        try {
            block(session)
        } finally {
            runCatching { session.close() }
        }
    }
}

private class SmbNasSession(settings: NasSettings) : NasSession {

    private val connection = SMBClient().connect(
        settings.host.trim(),
        settings.port.takeIf { it > 0 } ?: 445
    )
    private val session = connection.authenticate(
        if (settings.username.isBlank()) {
            AuthenticationContext.anonymous()
        } else {
            AuthenticationContext(
                settings.username,
                settings.password.toCharArray(),
                settings.domain.ifBlank { null }
            )
        }
    )
    private val share = session.connectShare(settings.share.trim()) as? DiskShare
        ?: throw IllegalStateException("El recurso no es una carpeta SMB")
    private val basePath = settings.path.trim().trim('/', '\\')

    override suspend fun list(path: String): List<NasEntry> {
        val remote = join(basePath, path)
        return share.list(remote)
            .asSequence()
            .filter { it.fileName != "." && it.fileName != ".." }
            .mapNotNull { info ->
                val name = info.fileName ?: return@mapNotNull null
                if (name.isBlank()) return@mapNotNull null
                val directory =
                    (info.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value) != 0L
                NasEntry(
                    name = name,
                    path = join(path, name),
                    isDirectory = directory,
                    size = info.endOfFile,
                    modified = runCatching { info.lastWriteTime.toEpochMillis() }.getOrDefault(0L)
                )
            }
            .sortedWith(compareBy<NasEntry> { !it.isDirectory }.thenBy { it.name.lowercase(Locale.ROOT) })
            .toList()
    }

    override suspend fun download(path: String, output: OutputStream): Long {
        val remote = join(basePath, path)
        share.openFile(
            remote,
            setOf(AccessMask.GENERIC_READ),
            null,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OPEN,
            null
        ).use { file ->
            file.inputStream.use { input ->
                return input.copyTo(output, DEFAULT_BUFFER_SIZE)
            }
        }
    }

    override suspend fun upload(input: InputStream, path: String, size: Long) {
        val remote = join(basePath, path)
        ensureDirectories(remote.substringBeforeLast('/', ""))
        share.openFile(
            remote,
            setOf(AccessMask.GENERIC_WRITE),
            null,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OVERWRITE_IF,
            null
        ).use { file ->
            file.outputStream.use { output ->
                input.copyTo(output, DEFAULT_BUFFER_SIZE)
            }
        }
    }

    override suspend fun mkdir(path: String) {
        ensureDirectories(join(basePath, path))
    }

    private fun ensureDirectories(path: String) {
        val normalized = path.trim('/', '\\')
        if (normalized.isBlank()) return
        var current = ""
        normalized.split('/', '\\').filter { it.isNotBlank() }.forEach { segment ->
            current = if (current.isBlank()) segment else "$current/$segment"
            if (!share.folderExists(current)) {
                share.mkdir(current)
            }
        }
    }

    override fun close() {
        runCatching { share.close() }
        runCatching { connection.close() }
    }

    private fun join(base: String, child: String): String {
        return listOf(base.trim('/', '\\'), child.trim('/', '\\'))
            .filter { it.isNotBlank() }
            .joinToString("/")
    }
}

private class WebDavNasSession(
    private val settings: NasSettings,
    private val client: OkHttpClient
) : NasSession {

    private val baseSegments = settings.path.trim('/')
        .split('/')
        .filter { it.isNotBlank() }
    private val baseUrl: HttpUrl = HttpUrl.Builder()
        .scheme(if (settings.useTls) "https" else "http")
        .host(settings.host.trim())
        .apply { settings.port.takeIf { it > 0 }?.let { port(it) } }
        .build()

    override suspend fun list(path: String): List<NasEntry> {
        val url = urlFor(path)
        val body = PROPFIND_BODY.toRequestBody("application/xml; charset=utf-8".toMediaType())
        val request = requestBuilder(url)
            .method("PROPFIND", body)
            .header("Depth", "1")
            .header("Content-Type", "application/xml; charset=utf-8")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException(httpError("listar", response.code))
            }
            val xml = response.body.byteStream()
            val currentUrlPath = url.encodedPath.trimEnd('/')
            return parseList(xml, path, currentUrlPath)
        }
    }

    override suspend fun download(path: String, output: OutputStream): Long {
        val request = requestBuilder(urlFor(path)).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException(httpError("descargar", response.code))
            }
            val body = response.body
            body.byteStream().use { input ->
                return input.copyTo(output, DEFAULT_BUFFER_SIZE)
            }
        }
    }

    override suspend fun upload(input: InputStream, path: String, size: Long) {
        mkdir(path.substringBeforeLast('/', ""))
        val body = InputStreamRequestBody(input, size)
        val request = requestBuilder(urlFor(path))
            .put(body)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException(httpError("subir", response.code))
            }
        }
    }

    override suspend fun mkdir(path: String) {
        val normalized = path.trim('/')
        if (normalized.isBlank()) return
        var current = ""
        normalized.split('/').filter { it.isNotBlank() }.forEach { segment ->
            current = if (current.isBlank()) segment else "$current/$segment"
            val request = requestBuilder(urlFor(current))
                .method("MKCOL", "".toRequestBody(null))
                .build()
            client.newCall(request).execute().use { response ->
                if (response.code != 201 && response.code != 405 && response.code != 301) {
                    throw IllegalStateException(httpError("crear carpeta", response.code))
                }
            }
        }
    }

    override fun close() = Unit

    private fun requestBuilder(url: HttpUrl): Request.Builder {
        val builder = Request.Builder().url(url)
        if (settings.username.isNotBlank()) {
            builder.header(
                "Authorization",
                Credentials.basic(settings.username, settings.password)
            )
        }
        return builder
    }

    private fun urlFor(path: String): HttpUrl {
        val builder = baseUrl.newBuilder()
        baseSegments.forEach { builder.addPathSegment(it) }
        path.trim('/').split('/').filter { it.isNotBlank() }.forEach { builder.addPathSegment(it) }
        return builder.build()
    }

    private fun parseList(
        input: InputStream,
        currentPath: String,
        currentUrlPath: String
    ): List<NasEntry> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(input, null)

        val entries = mutableListOf<NasEntry>()
        var href: String? = null
        var displayName: String? = null
        var size = 0L
        var modified = 0L
        var directory = false

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "response" -> {
                        href = null
                        displayName = null
                        size = 0L
                        modified = 0L
                        directory = false
                    }

                    "href" -> href = parser.nextText()
                    "displayname" -> displayName = parser.nextText()
                    "getcontentlength" -> {
                        size = parser.nextText().trim().toLongOrNull() ?: 0L
                    }

                    "getlastmodified" -> {
                        modified = parseHttpDate(parser.nextText())
                    }

                    "collection" -> directory = true
                }
            } else if (event == XmlPullParser.END_TAG && parser.name == "response") {
                val entry = buildEntry(
                    href = href,
                    displayName = displayName,
                    size = size,
                    modified = modified,
                    directory = directory,
                    currentPath = currentPath,
                    currentUrlPath = currentUrlPath
                )
                if (entry != null) entries += entry
            }
            event = parser.next()
        }
        return entries.sortedWith(
            compareBy<NasEntry> { !it.isDirectory }.thenBy { it.name.lowercase(Locale.ROOT) }
        )
    }

    private fun buildEntry(
        href: String?,
        displayName: String?,
        size: Long,
        modified: Long,
        directory: Boolean,
        currentPath: String,
        currentUrlPath: String
    ): NasEntry? {
        if (href.isNullOrBlank()) return null
        val hrefPath = Uri.parse(href).path?.trimEnd('/').orEmpty()
        val decodedCurrentPath = Uri.decode(currentUrlPath).trimEnd('/')
        if (hrefPath == currentUrlPath ||
            hrefPath == decodedCurrentPath ||
            Uri.decode(hrefPath).trimEnd('/') == decodedCurrentPath
        ) {
            return null
        }
        val name = displayName?.takeIf { it.isNotBlank() }
            ?: Uri.parse(href).pathSegments.lastOrNull()?.takeIf { it.isNotBlank() }
            ?: return null
        if (name == "." || name == "..") return null
        val path = listOf(currentPath.trim('/'), name)
            .filter { it.isNotBlank() }
            .joinToString("/")
        return NasEntry(name, path, directory, size, modified)
    }

    private fun parseHttpDate(value: String): Long {
        return runCatching {
            ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)
                .toInstant()
                .toEpochMilli()
        }.getOrDefault(0L)
    }

    private fun httpError(action: String, code: Int): String {
        return when (code) {
            401, 403 -> "Credenciales rechazadas al $action"
            404 -> "Ruta no encontrada al $action"
            else -> "HTTP $code al $action"
        }
    }

    private class InputStreamRequestBody(
        private val input: InputStream,
        private val length: Long
    ) : RequestBody() {
        override fun contentType(): MediaType? = null
        override fun contentLength(): Long = length
        override fun writeTo(sink: BufferedSink) {
            input.source().use { source -> sink.writeAll(source) }
        }
    }

    companion object {
        private const val PROPFIND_BODY = """<?xml version="1.0" encoding="utf-8"?>
<d:propfind xmlns:d="DAV:">
  <d:prop>
    <d:displayname/>
    <d:getcontentlength/>
    <d:getlastmodified/>
    <d:resourcetype/>
  </d:prop>
</d:propfind>"""
    }
}

private class SftpNasSession(settings: NasSettings) : NasSession {

    private val ssh = newSshClient()
    private val sftp: SFTPClient = try {
        require(settings.hostKey.isNotBlank()) { "Falta la huella SSH del servidor" }
        ssh.addHostKeyVerifier(FingerprintVerifier.getInstance(settings.hostKey.trim()))
        ssh.connect(
            settings.host.trim(),
            settings.port.takeIf { it > 0 } ?: 22
        )
        ssh.authPassword(settings.username, settings.password)
        ssh.newSFTPClient()
    } catch (t: Throwable) {
        runCatching { ssh.close() }
        throw t
    }
    private val basePath = settings.path.trim().trimEnd('/')

    override suspend fun list(path: String): List<NasEntry> {
        return sftp.ls(remote(path))
            .asSequence()
            .filter { it.name != "." && it.name != ".." }
            .mapNotNull { info ->
                val name = info.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                NasEntry(
                    name = name,
                    path = join(path, name),
                    isDirectory = info.isDirectory,
                    size = info.attributes.size,
                    modified = info.attributes.mtime * 1000L
                )
            }
            .sortedWith(
                compareBy<NasEntry> { !it.isDirectory }
                    .thenBy { it.name.lowercase(Locale.ROOT) }
            )
            .toList()
    }

    override suspend fun download(path: String, output: OutputStream): Long {
        sftp.open(remote(path)).use { file ->
            file.RemoteFileInputStream().use { input ->
                return input.copyTo(output, DEFAULT_BUFFER_SIZE)
            }
        }
    }

    override suspend fun upload(input: InputStream, path: String, size: Long) {
        val target = remote(path)
        val parent = target.substringBeforeLast('/', "")
        if (parent.isNotBlank() && parent != target) {
            sftp.mkdirs(parent)
        }
        sftp.open(
            target,
            setOf(OpenMode.WRITE, OpenMode.CREAT, OpenMode.TRUNC)
        ).use { file ->
            file.RemoteFileOutputStream().use { output ->
                input.copyTo(output, DEFAULT_BUFFER_SIZE)
            }
        }
    }

    override suspend fun mkdir(path: String) {
        val target = remote(path)
        if (target.isNotBlank() && target != ".") {
            sftp.mkdirs(target)
        }
    }

    override fun close() {
        runCatching { sftp.close() }
        runCatching { ssh.close() }
    }

    private fun remote(path: String): String {
        val child = path.trim('/')
        return when {
            basePath.isBlank() && child.isBlank() -> "."
            basePath.isBlank() -> child
            child.isBlank() -> basePath
            else -> "$basePath/$child"
        }
    }

    private fun join(base: String, child: String): String {
        return listOf(base.trim('/'), child.trim('/'))
            .filter { it.isNotBlank() }
            .joinToString("/")
    }
}

private const val DEFAULT_BUFFER_SIZE = 64 * 1024
