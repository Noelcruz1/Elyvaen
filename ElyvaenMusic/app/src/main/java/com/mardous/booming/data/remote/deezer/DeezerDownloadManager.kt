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

package com.mardous.booming.data.remote.deezer

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import com.kyant.taglib.Picture
import com.kyant.taglib.TagLib
import com.mardous.booming.data.remote.deezer.model.DeezerAlbumRef
import com.mardous.booming.data.remote.deezer.model.DeezerArtistRef
import com.mardous.booming.data.remote.deezer.model.DeezerTrackItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

private const val DOWNLOAD_STORE = "deezer_downloads"
private const val DOWNLOAD_STORE_KEY = "completed_v2"

@Serializable
private data class StoredDownload(
    val track: DeezerTrackItem,
    val uri: String
)

sealed interface DeezerDownloadState {
    data object Queued : DeezerDownloadState
    data class Downloading(val progress: Float) : DeezerDownloadState
    data object Tagging : DeezerDownloadState
    data class Done(val uri: Uri) : DeezerDownloadState
    data class Failed(val message: String) : DeezerDownloadState
}

data class DeezerDownloadEntry(
    val track: DeezerTrackItem,
    val state: DeezerDownloadState
)

/**
 * Downloads a Deezer track into the local Music library.
 *
 * This is intentionally independent from the playback service: once the file is
 * in MediaStore the normal Elyvaen library/player can use it like any other
 * local song.
 */
class DeezerDownloadManager(
    private val context: Context,
    private val client: DeezerPrivateClient,
    private val accountStore: DeezerAccountStore
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store = context.getSharedPreferences(DOWNLOAD_STORE, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _entries = MutableStateFlow(restoreEntries())
    val entries = _entries.asStateFlow()

    private val _states = MutableStateFlow(_entries.value.mapValues { it.value.state })
    val states = _states.asStateFlow()

    init {
        // Older builds kept no history, so import what is already in the
        // Deezer folder once. The query runs off the main thread.
        if (store.getString(DOWNLOAD_STORE_KEY, null) == null) {
            scope.launch {
                val scanned = scanExistingDownloads()
                if (scanned.isNotEmpty()) {
                    _entries.update { current -> scanned + current }
                    _states.update { current ->
                        scanned.mapValues { it.value.state } + current
                    }
                }
                persistEntries()
            }
        }
    }

    private fun restoreEntries(): Map<Long, DeezerDownloadEntry> = runCatching {
        val raw = store.getString(DOWNLOAD_STORE_KEY, null) ?: return emptyMap()
        json.decodeFromString<List<StoredDownload>>(raw).associate { stored ->
            stored.track.id to DeezerDownloadEntry(
                track = stored.track,
                state = DeezerDownloadState.Done(Uri.parse(stored.uri))
            )
        }
    }.getOrDefault(emptyMap())

    /**
     * One-time import of files this manager wrote before it kept a history.
     * A later clear writes an empty list, so the folder is not rescanned.
     */
    private fun scanExistingDownloads(): Map<Long, DeezerDownloadEntry> = runCatching {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val supportsRelativePath = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val pathColumn = if (supportsRelativePath) {
            MediaStore.Audio.Media.RELATIVE_PATH
        } else {
            MediaStore.Audio.Media.DATA
        }
        val selection = "$pathColumn LIKE ?"
        val arguments = if (supportsRelativePath) {
            arrayOf("Music/Elyvaen/Deezer/%")
        } else {
            arrayOf("%/Music/Elyvaen/Deezer/%")
        }
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION
        )
        val albumArtBase = Uri.parse("content://media/external/audio/albumart")
        val restored = mutableMapOf<Long, DeezerDownloadEntry>()
        context.contentResolver.query(collection, projection, selection, arguments, null)
            ?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                while (cursor.moveToNext()) {
                    val mediaId = cursor.getLong(idColumn)
                    val albumId = cursor.getLong(albumIdColumn)
                    val track = DeezerTrackItem(
                        id = mediaId,
                        title = cursor.getString(titleColumn).orEmpty(),
                        duration = (cursor.getLong(durationColumn) / 1000L).toInt(),
                        artist = DeezerArtistRef(
                            name = cursor.getString(artistColumn).orEmpty()
                        ),
                        album = DeezerAlbumRef(
                            title = cursor.getString(albumColumn).orEmpty(),
                            coverSmall = if (albumId > 0) {
                                ContentUris.withAppendedId(albumArtBase, albumId).toString()
                            } else {
                                null
                            }
                        )
                    )
                    restored[mediaId] = DeezerDownloadEntry(
                        track = track,
                        state = DeezerDownloadState.Done(
                            ContentUris.withAppendedId(collection, mediaId)
                        )
                    )
                }
            }
        restored
    }.getOrDefault(emptyMap())

    private fun persistEntries() {
        val completed = _entries.value.values.mapNotNull { entry ->
            val state = entry.state as? DeezerDownloadState.Done ?: return@mapNotNull null
            StoredDownload(track = entry.track, uri = state.uri.toString())
        }
        store.edit().putString(DOWNLOAD_STORE_KEY, json.encodeToString(completed)).apply()
    }

    private fun updateState(track: DeezerTrackItem, state: DeezerDownloadState) {
        _states.update { it + (track.id to state) }
        _entries.update { it + (track.id to DeezerDownloadEntry(track, state)) }
        if (state is DeezerDownloadState.Done) {
            persistEntries()
        }
    }

    fun clearFinished() {
        _entries.update { map ->
            map.filterValues {
                it.state !is DeezerDownloadState.Done && it.state !is DeezerDownloadState.Failed
            }
        }
        _states.update { map ->
            map.filterValues {
                it !is DeezerDownloadState.Done && it !is DeezerDownloadState.Failed
            }
        }
        persistEntries()
    }

    fun downloadTrack(track: DeezerTrackItem, trackNumber: Int? = null) {
        if (!accountStore.isConnected || !client.isConnected) {
            updateState(track, DeezerDownloadState.Failed("Conecta tu cuenta de Deezer"))
            return
        }
        val current = _states.value[track.id]
        if (current is DeezerDownloadState.Queued ||
            current is DeezerDownloadState.Downloading ||
            current is DeezerDownloadState.Tagging ||
            current is DeezerDownloadState.Done
        ) {
            return
        }
        scope.launch {
            downloadInternal(track, trackNumber)
        }
    }

    fun downloadAll(tracks: List<DeezerTrackItem>) {
        if (!accountStore.isConnected || !client.isConnected) {
            tracks.forEach {
                updateState(it, DeezerDownloadState.Failed("Conecta tu cuenta de Deezer"))
            }
            return
        }
        scope.launch {
            tracks.forEachIndexed { index, track ->
                val current = _states.value[track.id]
                if (current is DeezerDownloadState.Done ||
                    current is DeezerDownloadState.Downloading ||
                    current is DeezerDownloadState.Queued ||
                    current is DeezerDownloadState.Tagging
                ) {
                    return@forEachIndexed
                }
                downloadInternal(track, index + 1)
            }
        }
    }

    private suspend fun downloadInternal(track: DeezerTrackItem, trackNumber: Int?) {
        var tempFile: File? = null
        try {
            updateState(track, DeezerDownloadState.Queued)
            val stream = client.openStream(track.id.toString())
            val extension = if (stream.format.startsWith("FLAC")) "flac" else "mp3"
            tempFile = File(context.cacheDir, "deezer-${track.id}.$extension")
            updateState(track, DeezerDownloadState.Downloading(0f))
            client.download(stream, tempFile) { progress ->
                updateState(track, DeezerDownloadState.Downloading(progress))
            }
            if (!hasValidHeader(tempFile, extension)) {
                throw DeezerApiException("La desencriptación de Deezer falló")
            }
            updateState(track, DeezerDownloadState.Tagging)
            val cover = track.imageUrl?.let { client.downloadBytes(it) }
            writeTags(tempFile, track, trackNumber, cover)
            val uri = saveToMediaStore(tempFile, track, stream.mimeType, extension)
            updateState(track, DeezerDownloadState.Done(uri))
        } catch (t: Throwable) {
            updateState(track, DeezerDownloadState.Failed(t.message ?: "Error de descarga"))
        } finally {
            tempFile?.delete()
        }
    }

    private fun writeTags(
        file: File,
        track: DeezerTrackItem,
        trackNumber: Int?,
        cover: ByteArray?
    ) {
        runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE).use { fd ->
                val properties = TagLib.getMetadata(fd.dup().detachFd(), false)
                    ?.propertyMap ?: hashMapOf()
                properties["TITLE"] = arrayOf(track.title)
                properties["ARTIST"] = arrayOf(track.artistName)
                properties["ALBUM"] = arrayOf(track.albumName.ifBlank { "Deezer" })
                properties["ALBUMARTIST"] = arrayOf(track.artistName)
                if (trackNumber != null) {
                    properties["TRACKNUMBER"] = arrayOf(trackNumber.toString())
                }
                TagLib.savePropertyMap(fd.dup().detachFd(), properties)
                if (cover != null) {
                    TagLib.savePictures(
                        fd.dup().detachFd(),
                        arrayOf(
                            Picture(
                                data = cover,
                                description = "Deezer cover",
                                pictureType = "Front Cover",
                                mimeType = "image/jpeg"
                            )
                        )
                    )
                }
            }
        }
    }

    private fun saveToMediaStore(
        file: File,
        track: DeezerTrackItem,
        mimeType: String,
        extension: String
    ): Uri {
        val artist = sanitize(track.artistName.ifBlank { "Unknown Artist" })
        val album = sanitize(track.albumName.ifBlank { "Singles" })
        val name = "${sanitize(track.title)}.$extension"
        val resolver = context.contentResolver

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, name)
                put(MediaStore.Audio.Media.TITLE, track.title)
                put(MediaStore.Audio.Media.ARTIST, track.artistName)
                put(MediaStore.Audio.Media.ALBUM, track.albumName)
                put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
                put(
                    MediaStore.Audio.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_MUSIC}/Elyvaen/Deezer/$artist/$album"
                )
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("No se pudo crear el archivo en Música")
            resolver.openOutputStream(uri)?.use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
            } ?: error("No se pudo escribir el archivo")
            values.clear()
            values.put(MediaStore.Audio.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            @Suppress("DEPRECATION")
            val path = File(
                Environment.getExternalStorageDirectory(),
                "${Environment.DIRECTORY_MUSIC}/Elyvaen/Deezer/$artist/$album/$name"
            ).absolutePath
            MediaScannerConnection.scanFile(context, arrayOf(path), null, null)
            return uri
        }

        @Suppress("DEPRECATION")
        val directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            "Elyvaen/Deezer/$artist/$album"
        )
        if (!directory.exists() && !directory.mkdirs()) {
            error("No se pudo crear la carpeta de descargas")
        }
        val target = File(directory, name)
        file.inputStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), null, null)
        return Uri.fromFile(target)
    }

    private fun hasValidHeader(file: File, extension: String): Boolean {
        val header = ByteArray(4)
        val read = file.inputStream().use { it.read(header) }
        if (read < 4) return false
        return if (extension == "flac") {
            String(header, Charsets.US_ASCII) == "fLaC"
        } else {
            header[0] == 'I'.code.toByte() ||
                    String(header, Charsets.US_ASCII).startsWith("ID3") ||
                    header[0] == 0xFF.toByte()
        }
    }

    private fun sanitize(value: String): String {
        val cleaned = value.replace(Regex("""[\\/:*?"<>|]"""), "_").trim()
        return cleaned.ifBlank { "Unknown" }.take(80)
    }
}
