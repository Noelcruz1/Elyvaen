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
import android.content.SharedPreferences
import com.mardous.booming.data.model.Album
import com.mardous.booming.data.model.Artist
import com.mardous.booming.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Content that is archived from the library without deleting its files.
 *
 * Hidden albums, artists and tracks are filtered out of every library query,
 * but they stay in MediaStore and can be restored at any time.
 */
object HiddenLibraryStore {

    const val TYPE_ALBUM = "album"
    const val TYPE_ARTIST = "artist"
    const val TYPE_TRACK = "track"

    private const val PREFERENCES = "hidden_library"
    private const val KEY_ITEMS = "items"

    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()

    private var preferences: SharedPreferences? = null

    private val _items = MutableStateFlow<List<HiddenLibraryItem>>(emptyList())
    val items = _items.asStateFlow()

    val current: List<HiddenLibraryItem>
        get() = _items.value

    fun init(context: Context) {
        if (preferences != null) return
        synchronized(lock) {
            if (preferences != null) return
            val prefs = context.applicationContext
                .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            preferences = prefs
            _items.value = read(prefs)
        }
    }

    fun hideAlbums(albums: Collection<Album>) {
        addAll(albums.map {
            HiddenLibraryItem(TYPE_ALBUM, it.id, it.name, it.artistName)
        })
    }

    fun hideArtists(artists: Collection<Artist>) {
        addAll(artists.map {
            HiddenLibraryItem(TYPE_ARTIST, it.id, it.name, count = it.albumCount)
        })
    }

    fun hideSongs(songs: Collection<Song>) {
        addAll(songs.map {
            HiddenLibraryItem(TYPE_TRACK, it.id, it.title, it.songInfo())
        })
    }

    fun restore(item: HiddenLibraryItem) {
        synchronized(lock) {
            val updated = _items.value.filterNot { it.sameAs(item) }
            commit(updated)
        }
    }

    fun restoreAll() {
        synchronized(lock) {
            commit(emptyList())
        }
    }

    fun isAlbumHidden(id: Long): Boolean =
        _items.value.any { it.type == TYPE_ALBUM && it.id == id }

    fun isArtistHidden(name: String): Boolean =
        _items.value.any { it.type == TYPE_ARTIST && it.name.equals(name, ignoreCase = true) }

    fun isSongHidden(id: Long): Boolean =
        _items.value.any { it.type == TYPE_TRACK && it.id == id }

    private fun addAll(newItems: Collection<HiddenLibraryItem>) {
        if (newItems.isEmpty()) return
        synchronized(lock) {
            val updated = _items.value.toMutableList()
            newItems.forEach { candidate ->
                if (candidate.name.isNotBlank() && updated.none { it.sameAs(candidate) }) {
                    updated.add(candidate)
                }
            }
            commit(updated)
        }
    }

    private fun commit(items: List<HiddenLibraryItem>) {
        _items.value = items
        preferences?.edit()?.putString(KEY_ITEMS, json.encodeToString(items))?.apply()
    }

    private fun read(prefs: SharedPreferences): List<HiddenLibraryItem> {
        val raw = prefs.getString(KEY_ITEMS, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<HiddenLibraryItem>>(raw)
        }.getOrDefault(emptyList())
    }

    private fun HiddenLibraryItem.sameAs(other: HiddenLibraryItem): Boolean {
        if (type != other.type) return false
        return when (type) {
            TYPE_ARTIST -> name.equals(other.name, ignoreCase = true)
            else -> id == other.id
        }
    }

    private fun Song.songInfo(): String = listOf(artistName, albumName)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
}

@Serializable
data class HiddenLibraryItem(
    val type: String,
    val id: Long = 0,
    val name: String,
    val subtitle: String = "",
    val count: Int = 0
)
