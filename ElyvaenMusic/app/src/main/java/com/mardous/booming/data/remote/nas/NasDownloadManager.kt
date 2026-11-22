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
import com.mardous.booming.data.local.AudioImportManager
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
}
