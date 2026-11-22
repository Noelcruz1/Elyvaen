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

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.MediaStore
import com.mardous.booming.extensions.hasQ
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class StorageVolumeInfo(
    val id: String,
    val description: String,
    val isPrimary: Boolean,
    val isRemovable: Boolean,
    val isMounted: Boolean,
    val totalBytes: Long,
    val freeBytes: Long
)

data class HubStorageSummary(
    val volumes: List<StorageVolumeInfo> = emptyList(),
    val libraryTracks: Int = 0,
    val libraryBytes: Long = 0,
    val deezerBytes: Long = 0,
    val importBytes: Long = 0,
    val cacheBytes: Long = 0
) {
    val removableVolumes: List<StorageVolumeInfo>
        get() = volumes.filter { it.isRemovable && it.isMounted }

    val internalVolume: StorageVolumeInfo?
        get() = volumes.firstOrNull { it.isPrimary } ?: volumes.firstOrNull()
}

/**
 * Read-only overview of the volumes and the Elyvaen library. Cache cleaning
 * only touches app caches: downloads and imported audio are never removed.
 */
class StorageOverview(private val context: Context) {

    suspend fun snapshot(): HubStorageSummary = withContext(Dispatchers.IO) {
        val volumes = readVolumes()
        val library = readLibraryStats()
        HubStorageSummary(
            volumes = volumes,
            libraryTracks = library.tracks,
            libraryBytes = library.bytes,
            deezerBytes = library.deezerBytes,
            importBytes = library.importBytes,
            cacheBytes = cacheSize()
        )
    }

    suspend fun clearCache(): Long = withContext(Dispatchers.IO) {
        val before = cacheSize()
        deleteContents(context.cacheDir)
        deleteContents(context.externalCacheDir)
        deleteContents(context.codeCacheDir)
        before
    }

    private fun readVolumes(): List<StorageVolumeInfo> {
        val manager = context.getSystemService(StorageManager::class.java) ?: return emptyList()
        return manager.storageVolumes.mapNotNull { volume ->
            val mounted = volume.state == Environment.MEDIA_MOUNTED
            val path = volumePath(volume)
            val stat = path?.let { runCatching { StatFs(it) }.getOrNull() }
            val total = stat?.let { it.blockCountLong * it.blockSizeLong } ?: 0L
            val free = stat?.let { it.availableBlocksLong * it.blockSizeLong } ?: 0L
            StorageVolumeInfo(
                id = volume.uuid ?: volume.getDescription(context),
                description = volume.getDescription(context).orEmpty().ifBlank { "Almacenamiento" },
                isPrimary = volume.isPrimary,
                isRemovable = volume.isRemovable,
                isMounted = mounted,
                totalBytes = total,
                freeBytes = free
            )
        }
    }

    private fun volumePath(volume: android.os.storage.StorageVolume): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return volume.directory?.absolutePath
        }
        @Suppress("DEPRECATION")
        return runCatching {
            volume.javaClass.getMethod("getPath").invoke(volume) as? String
        }.getOrNull()
    }

    private fun readLibraryStats(): LibraryStats {
        val projection = if (hasQ()) {
            arrayOf(
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.RELATIVE_PATH
            )
        } else {
            @Suppress("DEPRECATION")
            arrayOf(
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.DATA
            )
        }
        var tracks = 0
        var totalBytes = 0L
        var deezerBytes = 0L
        var importBytes = 0L
        runCatching {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                null,
                null
            )?.use { cursor ->
                val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val pathIndex = if (hasQ()) {
                    cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
                } else {
                    @Suppress("DEPRECATION")
                    cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                }
                while (cursor.moveToNext()) {
                    tracks++
                    val size = cursor.getLong(sizeIndex).coerceAtLeast(0)
                    totalBytes += size
                    val path = cursor.getString(pathIndex).orEmpty()
                    when {
                        path.contains("/Elyvaen/Deezer/") || path.startsWith("Music/Elyvaen/Deezer/") ->
                            deezerBytes += size

                        path.contains("/Elyvaen/Import/") || path.startsWith("Music/Elyvaen/Import/") ->
                            importBytes += size
                    }
                }
            }
        }
        return LibraryStats(tracks, totalBytes, deezerBytes, importBytes)
    }

    private fun cacheSize(): Long {
        return listOfNotNull(context.cacheDir, context.externalCacheDir, context.codeCacheDir)
            .sumOf { directorySize(it) }
    }

    private fun directorySize(file: File?): Long {
        if (file == null || !file.exists()) return 0L
        if (file.isFile) return file.length()
        return file.listFiles()?.sumOf { directorySize(it) } ?: 0L
    }

    private fun deleteContents(directory: File?) {
        directory?.listFiles()?.forEach { child ->
            runCatching { child.deleteRecursively() }
        }
    }

    private data class LibraryStats(
        val tracks: Int,
        val bytes: Long,
        val deezerBytes: Long,
        val importBytes: Long
    )
}
