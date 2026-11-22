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

import com.mardous.booming.data.model.Album
import com.mardous.booming.data.model.Artist
import com.mardous.booming.data.model.Playlist
import com.mardous.booming.data.model.Song
import com.mardous.booming.data.repository.Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

data class MediaLibrarySnapshot(
    val songs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val playlistSongs: Map<Long, List<Song>> = emptyMap(),
    val loadedAt: Long = 0L
) {
    private val songsById by lazy(LazyThreadSafetyMode.NONE) { songs.associateBy { it.id } }
    private val songsByAlbum by lazy(LazyThreadSafetyMode.NONE) { songs.groupBy { it.albumId } }
    private val songsByArtist by lazy(LazyThreadSafetyMode.NONE) { songs.groupBy { it.artistId } }

    fun song(id: Long): Song? = songsById[id]

    fun albumSongs(id: Long): List<Song> = songsByAlbum[id].orEmpty()

    fun artistSongs(id: Long): List<Song> = songsByArtist[id].orEmpty()
}

/**
 * Snapshot of the visible library used by the DLNA and WebDAV endpoints. A
 * short cache avoids rebuilding the tree for every request from a TV.
 */
class MediaLibraryIndex(private val repository: Repository) {

    private val mutex = Mutex()

    @Volatile
    private var cached: MediaLibrarySnapshot? = null

    suspend fun get(force: Boolean = false): MediaLibrarySnapshot = mutex.withLock {
        val now = System.currentTimeMillis()
        val current = cached
        if (!force && current != null &&
            now - current.loadedAt < TimeUnit.SECONDS.toMillis(CACHE_SECONDS)
        ) {
            return current
        }
        val snapshot = withContext(Dispatchers.IO) {
            val songs = repository.allSongs()
            val albums = repository.allAlbums()
            val artists = repository.allArtists()
            val playlists = runCatching { repository.devicePlaylists() }.getOrDefault(emptyList())
            val playlistSongs = playlists.associate { playlist ->
                playlist.id to runCatching {
                    repository.devicePlaylistSongs(playlist)
                }.getOrDefault(emptyList())
            }
            MediaLibrarySnapshot(
                songs = songs,
                albums = albums,
                artists = artists,
                playlists = playlists,
                playlistSongs = playlistSongs,
                loadedAt = now
            )
        }
        cached = snapshot
        snapshot
    }

    fun invalidate() {
        cached = null
    }

    companion object {
        private const val CACHE_SECONDS = 60L
    }
}
