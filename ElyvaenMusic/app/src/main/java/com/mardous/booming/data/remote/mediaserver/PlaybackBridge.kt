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

import android.content.ComponentName
import android.content.Context
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.mardous.booming.data.model.Song
import com.mardous.booming.playback.PlaybackService
import com.mardous.booming.playback.buildPlayableMediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

data class PlaybackSnapshot(
    val connected: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val queueSize: Int = 0
)

/**
 * Small bridge used by the web remote. It connects to the existing
 * PlaybackService's MediaSession and never creates a second player.
 */
class PlaybackBridge(private val context: Context) {

    private val mutex = Mutex()

    @Volatile
    private var controller: MediaController? = null

    @Volatile
    private var future: ListenableFuture<MediaController>? = null

    @Volatile
    private var closed = false

    suspend fun snapshot(): PlaybackSnapshot = withContext(Dispatchers.Main) {
        val current = controller ?: connect() ?: return@withContext PlaybackSnapshot()
        val metadata = current.mediaMetadata
        PlaybackSnapshot(
            connected = true,
            title = metadata.title?.toString().orEmpty(),
            artist = metadata.artist?.toString().orEmpty(),
            album = metadata.albumTitle?.toString().orEmpty(),
            isPlaying = current.isPlaying,
            positionMs = current.currentPosition.coerceAtLeast(0L),
            durationMs = current.duration.let { if (it == C.TIME_UNSET) 0L else it },
            queueSize = current.mediaItemCount
        )
    }

    suspend fun toggle() = withController { if (it.isPlaying) it.pause() else it.play() }

    suspend fun next() = withController { it.seekToNextMediaItem() }

    suspend fun previous() = withController { it.seekToPreviousMediaItem() }

    suspend fun seekTo(positionMs: Long) = withController {
        it.seekTo(positionMs.coerceAtLeast(0L))
    }

    suspend fun playSong(song: Song): Boolean {
        return withController {
            it.setMediaItem(buildPlayableMediaItem(song))
            it.prepare()
            it.play()
            true
        } ?: false
    }

    fun close() {
        closed = true
        val current = controller
        val pending = future
        controller = null
        future = null
        val release = Runnable {
            runCatching { current?.release() }
            pending?.let { runCatching { MediaController.releaseFuture(it) } }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            release.run()
        } else {
            ContextCompat.getMainExecutor(context).execute(release)
        }
    }

    private suspend fun <T> withController(block: (MediaController) -> T): T? {
        return withContext(Dispatchers.Main) {
            val current = controller ?: connect() ?: return@withContext null
            runCatching { block(current) }.getOrNull()
        }
    }

    private suspend fun connect(): MediaController? = mutex.withLock {
        if (closed) return null
        controller?.let { return it }
        withContext(Dispatchers.Main) {
            try {
                val token = SessionToken(
                    context,
                    ComponentName(context, PlaybackService::class.java)
                )
                val pending = MediaController.Builder(context, token).buildAsync()
                future = pending
                val connected = withTimeout(CONNECT_TIMEOUT_MS) { pending.await() }
                controller = connected
                connected
            } catch (_: Exception) {
                null
            }
        }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 3_000L
    }
}
