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

package com.mardous.booming.data.local

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.mardous.booming.extensions.hasQ
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.Locale

data class ImportProgress(
    val total: Int,
    val copied: Int,
    val failed: Int,
    val currentName: String? = null,
    val done: Boolean = false
)

data class ImportResult(
    val total: Int,
    val copied: Int,
    val failed: Int,
    val skipped: Int
)

/**
 * Copies audio from a SAF tree (USB OTG, SD card or any folder) into the
 * public Elyvaen library and publishes it in MediaStore. The source volume is
 * only read: no file is moved or deleted.
 */
class AudioImportManager(private val context: Context) {

    private val resolver = context.contentResolver

    suspend fun importTree(
        treeUri: Uri,
        onProgress: (ImportProgress) -> Unit
    ): ImportResult = withContext(Dispatchers.IO) {
        val rootName = queryDocumentName(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
            ?: "USB"
        val candidates = mutableListOf<ImportCandidate>()
        collectChildren(
            treeUri = treeUri,
            parentDocumentId = DocumentsContract.getTreeDocumentId(treeUri),
            relativeFolder = "Import/${sanitizeSegment(rootName)}",
            output = candidates
        )

        var copied = 0
        var failed = 0
        var skipped = 0
        onProgress(ImportProgress(candidates.size, 0, 0))

        candidates.forEachIndexed { index, candidate ->
            currentCoroutineContext().ensureActive()
            onProgress(
                ImportProgress(
                    total = candidates.size,
                    copied = copied,
                    failed = failed,
                    currentName = candidate.displayName
                )
            )
            try {
                resolver.openInputStream(candidate.uri)?.use { input ->
                    publishAudio(
                        input = input,
                        displayName = candidate.displayName,
                        mimeType = candidate.mimeType,
                        folder = candidate.relativeFolder
                    )
                    copied++
                } ?: run {
                    failed++
                }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                failed++
            }
            if (index == candidates.lastIndex) {
                onProgress(
                    ImportProgress(
                        total = candidates.size,
                        copied = copied,
                        failed = failed,
                        currentName = null
                    )
                )
            }
        }

        ImportResult(
            total = candidates.size,
            copied = copied,
            failed = failed,
            skipped = skipped
        )
    }

    /**
     * Publishes one audio stream into Music/Elyvaen/<folder>. The stream is
     * closed by the caller. Safe to call from the Wi-Fi upload server.
     */
    fun publishAudio(
        input: InputStream,
        displayName: String,
        mimeType: String?,
        folder: String
    ): Uri {
        val safeName = sanitizeFileName(displayName)
        val safeFolder = sanitizeFolder(folder)
        val resolvedMime = resolveMimeType(safeName, mimeType)
            ?: throw IllegalArgumentException("Formato de audio no compatible")
        val relativePath = "${Environment.DIRECTORY_MUSIC}/Elyvaen/$safeFolder"

        if (hasQ()) {
            val uniqueName = uniqueMediaStoreName(relativePath, safeName)
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, uniqueName)
                put(MediaStore.Audio.Media.MIME_TYPE, resolvedMime)
                put(MediaStore.Audio.Media.RELATIVE_PATH, relativePath)
                put(MediaStore.Audio.Media.IS_MUSIC, 1)
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("No se pudo crear el archivo en Música")
            try {
                resolver.openOutputStream(uri, "w")?.use { output ->
                    input.copyTo(output, DEFAULT_BUFFER_SIZE)
                } ?: throw IllegalStateException("No se pudo escribir el archivo")
                values.clear()
                values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (t: Throwable) {
                runCatching { resolver.delete(uri, null, null) }
                throw t
            }
            return uri
        }

        @Suppress("DEPRECATION")
        val directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            "Elyvaen/$safeFolder"
        )
        if (!directory.exists() && !directory.mkdirs()) {
            throw IllegalStateException("No se pudo crear la carpeta de importación")
        }
        val target = uniqueLegacyFile(directory, safeName)
        target.outputStream().use { output ->
            input.copyTo(output, DEFAULT_BUFFER_SIZE)
        }
        MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), null, null)
        return Uri.fromFile(target)
    }

    fun isSupportedFile(name: String): Boolean {
        return extensionOf(name) in SUPPORTED_EXTENSIONS
    }

    fun resolveMimeType(name: String, provided: String? = null): String? {
        if (provided?.startsWith("audio/") == true && provided != "audio/*") {
            return provided
        }
        val extension = extensionOf(name)
        MIME_TYPES[extension]?.let { return it }
        return MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(extension)
            ?.takeIf { it.startsWith("audio/") }
    }

    private fun collectChildren(
        treeUri: Uri,
        parentDocumentId: String,
        relativeFolder: String,
        output: MutableList<ImportCandidate>
    ) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            parentDocumentId
        )
        resolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            ),
            null,
            null,
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val documentId = cursor.getString(0) ?: continue
                val displayName = cursor.getString(1) ?: continue
                val mimeType = cursor.getString(2).orEmpty()
                if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                    collectChildren(
                        treeUri = treeUri,
                        parentDocumentId = documentId,
                        relativeFolder = "$relativeFolder/${sanitizeSegment(displayName)}",
                        output = output
                    )
                } else {
                    val extension = extensionOf(displayName)
                    if (extension in SUPPORTED_EXTENSIONS) {
                        output += ImportCandidate(
                            uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId),
                            displayName = displayName,
                            mimeType = mimeType,
                            relativeFolder = relativeFolder
                        )
                    }
                }
            }
        }
    }

    private fun queryDocumentName(treeUri: Uri, documentId: String): String? {
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
        return resolver.query(
            documentUri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }

    private fun uniqueMediaStoreName(relativePath: String, displayName: String): String {
        val base = displayName.substringBeforeLast('.', displayName)
        val extension = displayName.substringAfterLast('.', "")
        var candidate = displayName
        var suffix = 2
        while (mediaStoreNameExists(relativePath, candidate)) {
            candidate = if (extension.isBlank()) {
                "$base ($suffix)"
            } else {
                "$base ($suffix).$extension"
            }
            suffix++
        }
        return candidate
    }

    private fun mediaStoreNameExists(relativePath: String, displayName: String): Boolean {
        val normalizedPath = relativePath.trimEnd('/') + "/"
        val selection = "(${MediaStore.Audio.Media.RELATIVE_PATH} = ? OR " +
                "${MediaStore.Audio.Media.RELATIVE_PATH} = ?) AND " +
                "${MediaStore.Audio.Media.DISPLAY_NAME} = ?"
        return resolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Media._ID),
            selection,
            arrayOf(relativePath, normalizedPath, displayName),
            null
        )?.use { it.moveToFirst() } ?: false
    }

    private fun uniqueLegacyFile(directory: File, displayName: String): File {
        val base = displayName.substringBeforeLast('.', displayName)
        val extension = displayName.substringAfterLast('.', "")
        var candidate = File(directory, displayName)
        var suffix = 2
        while (candidate.exists()) {
            candidate = File(
                directory,
                if (extension.isBlank()) "$base ($suffix)" else "$base ($suffix).$extension"
            )
            suffix++
        }
        return candidate
    }

    private fun sanitizeFolder(folder: String): String {
        return folder.split('/')
            .map { sanitizeSegment(it) }
            .filter { it.isNotBlank() }
            .joinToString("/")
            .ifBlank { "Import" }
    }

    private fun sanitizeFileName(name: String): String {
        val extension = extensionOf(name)
        val base = if (extension.isBlank()) name else name.dropLast(extension.length + 1)
        val safeBase = sanitizeSegment(base).take(160).ifBlank { "audio" }
        return if (extension.isBlank()) safeBase else "$safeBase.$extension"
    }

    companion object {
        val SUPPORTED_EXTENSIONS = setOf(
            "flac", "mp3", "m4a", "aac", "ogg", "opus", "wav", "aif", "aiff",
            "wma", "ape", "dsf", "dff", "mka"
        )

        private val MIME_TYPES = mapOf(
            "flac" to "audio/flac",
            "mp3" to "audio/mpeg",
            "m4a" to "audio/mp4",
            "aac" to "audio/aac",
            "ogg" to "audio/ogg",
            "opus" to "audio/opus",
            "wav" to "audio/x-wav",
            "aif" to "audio/x-aiff",
            "aiff" to "audio/x-aiff",
            "wma" to "audio/x-ms-wma",
            "ape" to "audio/x-ape",
            "dsf" to "audio/dsf",
            "dff" to "audio/dff",
            "mka" to "audio/x-matroska"
        )

    }

    private fun extensionOf(name: String): String {
        return name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    }

    private fun sanitizeSegment(value: String): String {
        return value
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
            .trim()
            .trim('.')
            .take(120)
            .ifBlank { "Sin nombre" }
    }

    private data class ImportCandidate(
        val uri: Uri,
        val displayName: String,
        val mimeType: String,
        val relativeFolder: String
    )
}
