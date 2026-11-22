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

import android.content.Context
import android.provider.MediaStore
import com.mardous.booming.data.local.AudioImportManager
import com.mardous.booming.extensions.hasQ
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

data class NasDownloadProgress(
    val totalFiles: Int,
    val completedFiles: Int,
    val failedFiles: Int,
    val currentName: String?,
    val bytes: Long
)

data class NasDownloadResult(
    val totalFiles: Int,
    val downloadedFiles: Int,
    val failedFiles: Int,
    val skippedFiles: Int,
    val bytes: Long
)

data class NasSyncResult(
    val totalFiles: Int,
    val downloadedFiles: Int,
    val unchangedFiles: Int,
    val conflictFiles: Int,
    val failedFiles: Int,
    val skippedFiles: Int,
    val bytes: Long
)

/**
 * Downloads a NAS file or a whole folder and publishes each supported audio
 * file through [AudioImportManager] into Music/Elyvaen/NAS.
 */
class NasDownloadManager(
    private val context: Context,
    private val importer: AudioImportManager
) {

    fun isSupportedFile(name: String): Boolean = importer.isSupportedFile(name)

    suspend fun downloadFolder(
        session: NasSession,
        folderPath: String,
        onProgress: (NasDownloadProgress) -> Unit
    ): NasDownloadResult = withContext(Dispatchers.IO) {
        val audioFiles = mutableListOf<NasEntry>()
        var skipped = 0
        collectAudioFiles(session, folderPath, audioFiles) { skipped++ }

        var completed = 0
        var failed = 0
        var bytes = 0L
        onProgress(NasDownloadProgress(audioFiles.size, 0, 0, null, 0))

        audioFiles.forEachIndexed { index, entry ->
            currentCoroutineContext().ensureActive()
            onProgress(
                NasDownloadProgress(
                    totalFiles = audioFiles.size,
                    completedFiles = completed,
                    failedFiles = failed,
                    currentName = entry.name,
                    bytes = bytes
                )
            )
            val copied = runCatching { downloadOne(session, entry) }.getOrNull()
            if (copied != null) {
                completed++
                bytes += copied
            } else {
                failed++
            }
            if (index == audioFiles.lastIndex) {
                onProgress(
                    NasDownloadProgress(
                        totalFiles = audioFiles.size,
                        completedFiles = completed,
                        failedFiles = failed,
                        currentName = null,
                        bytes = bytes
                    )
                )
            }
        }

        NasDownloadResult(
            totalFiles = audioFiles.size,
            downloadedFiles = completed,
            failedFiles = failed,
            skippedFiles = skipped,
            bytes = bytes
        )
    }

    /**
     * Incremental sync: downloads only files that are not already in the
     * local NAS library. Files with the same name but a different size are
     * reported as conflicts and never overwritten.
     */
    suspend fun syncFolder(
        session: NasSession,
        folderPath: String,
        onProgress: (NasDownloadProgress) -> Unit
    ): NasSyncResult = withContext(Dispatchers.IO) {
        val audioFiles = mutableListOf<NasEntry>()
        var skipped = 0
        collectAudioFiles(session, folderPath, audioFiles) { skipped++ }

        var downloaded = 0
        var unchanged = 0
        var conflicts = 0
        var failed = 0
        var processed = 0
        var bytes = 0L
        onProgress(NasDownloadProgress(audioFiles.size, 0, 0, null, 0))

        audioFiles.forEachIndexed { index, entry ->
            currentCoroutineContext().ensureActive()
            when (localFileStatus(entry)) {
                LocalFileStatus.UNCHANGED -> unchanged++
                LocalFileStatus.CONFLICT -> conflicts++
                LocalFileStatus.MISSING -> {
                    val copied = runCatching { downloadOne(session, entry) }.getOrNull()
                    if (copied != null) {
                        downloaded++
                        bytes += copied
                    } else {
                        failed++
                    }
                }
            }
            processed++
            onProgress(
                NasDownloadProgress(
                    totalFiles = audioFiles.size,
                    completedFiles = processed,
                    failedFiles = failed,
                    currentName = if (index < audioFiles.lastIndex) audioFiles[index + 1].name else null,
                    bytes = bytes
                )
            )
        }

        NasSyncResult(
            totalFiles = audioFiles.size,
            downloadedFiles = downloaded,
            unchangedFiles = unchanged,
            conflictFiles = conflicts,
            failedFiles = failed,
            skippedFiles = skipped,
            bytes = bytes
        )
    }

    suspend fun downloadFile(
        session: NasSession,
        entry: NasEntry,
        onProgress: (NasDownloadProgress) -> Unit
    ): NasDownloadResult = withContext(Dispatchers.IO) {
        onProgress(NasDownloadProgress(1, 0, 0, entry.name, 0))
        val copied = runCatching { downloadOne(session, entry) }.getOrNull()
        val result = NasDownloadResult(
            totalFiles = 1,
            downloadedFiles = if (copied != null) 1 else 0,
            failedFiles = if (copied == null) 1 else 0,
            skippedFiles = 0,
            bytes = copied ?: 0L
        )
        onProgress(
            NasDownloadProgress(
                totalFiles = 1,
                completedFiles = result.downloadedFiles,
                failedFiles = result.failedFiles,
                currentName = null,
                bytes = result.bytes
            )
        )
        result
    }

    private suspend fun collectAudioFiles(
        session: NasSession,
        path: String,
        output: MutableList<NasEntry>,
        onSkipped: () -> Unit
    ) {
        session.list(path).forEach { entry ->
            currentCoroutineContext().ensureActive()
            if (entry.isDirectory) {
                collectAudioFiles(session, entry.path, output, onSkipped)
            } else if (importer.isSupportedFile(entry.name)) {
                output += entry
            } else {
                onSkipped()
            }
        }
    }

    private fun localFileStatus(entry: NasEntry): LocalFileStatus {
        if (!hasQ()) return LocalFileStatus.MISSING
        val parent = entry.path.substringBeforeLast('/', "")
        val relativePath = "Music/Elyvaen/NAS/" +
                (if (parent.isBlank()) "" else "$parent/")
        val selection = "${MediaStore.Audio.Media.RELATIVE_PATH} = ? AND " +
                "${MediaStore.Audio.Media.DISPLAY_NAME} = ?"
        return context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Media.SIZE),
            selection,
            arrayOf(relativePath, entry.name),
            null
        )?.use { cursor ->
            if (!cursor.moveToFirst()) {
                LocalFileStatus.MISSING
            } else if (entry.size <= 0L ||
                cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)) == entry.size
            ) {
                LocalFileStatus.UNCHANGED
            } else {
                LocalFileStatus.CONFLICT
            }
        } ?: LocalFileStatus.MISSING
    }

    private suspend fun downloadOne(session: NasSession, entry: NasEntry): Long {
        val temp = File.createTempFile("nas-download-", ".part", context.cacheDir)
        try {
            val bytes = temp.outputStream().use { output ->
                session.download(entry.path, output)
            }
            if (bytes <= 0L) {
                throw IllegalStateException("Archivo vacío")
            }
            val folder = "NAS/" + entry.path.substringBeforeLast('/', "")
            temp.inputStream().use { input ->
                importer.publishAudio(
                    input = input,
                    displayName = entry.name,
                    mimeType = null,
                    folder = folder
                )
            }
            return bytes
        } finally {
            temp.delete()
        }
    }

    private enum class LocalFileStatus {
        MISSING,
        UNCHANGED,
        CONFLICT
    }
}
