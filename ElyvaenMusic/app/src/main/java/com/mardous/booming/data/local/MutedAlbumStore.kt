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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Albums that stay browsable in the Albums section but are excluded from the
 * global song lists and from shuffle/random playback.
 *
 * This is softer than [HiddenLibraryStore]: the album and its detail page are
 * still visible, so it can be played explicitly or restored.
 */
object MutedAlbumStore {

    private const val PREFERENCES = "muted_albums"
    private const val KEY_ITEMS = "items"

    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()

    private var preferences: SharedPreferences? = null

    private val _items = MutableStateFlow<List<HiddenLibraryItem>>(emptyList())
    val items = _items.asStateFlow()

    @Volatile
    var ids: Set<Long> = emptySet()
        private set

    val current: List<HiddenLibraryItem>
        get() = _items.value

    fun init(context: Context) {
        if (preferences != null) return
        synchronized(lock) {
            if (preferences != null) return
            val prefs = context.applicationContext
                .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            preferences = prefs
            val restored = read(prefs)
            _items.value = restored
            ids = restored.mapTo(mutableSetOf()) { it.id }
        }
    }

    fun mute(albums: Collection<Album>) {
        if (albums.isEmpty()) return
        synchronized(lock) {
            val updated = _items.value.toMutableList()
            albums.forEach { album ->
                if (updated.none { it.id == album.id }) {
                    updated.add(
                        HiddenLibraryItem(
                            type = HiddenLibraryStore.TYPE_ALBUM,
                            id = album.id,
                            name = album.name,
                            subtitle = album.artistName
                        )
                    )
                }
            }
            commit(updated)
        }
    }

    fun unmute(id: Long) {
        synchronized(lock) {
            commit(_items.value.filterNot { it.id == id })
        }
    }

    fun unmute(item: HiddenLibraryItem) = unmute(item.id)

    fun unmuteAll() {
        synchronized(lock) {
            commit(emptyList())
        }
    }

    fun isMuted(albumId: Long): Boolean = albumId in ids

    private fun commit(items: List<HiddenLibraryItem>) {
        _items.value = items
        ids = items.mapTo(mutableSetOf()) { it.id }
        preferences?.edit()?.putString(KEY_ITEMS, json.encodeToString(items))?.apply()
    }

    private fun read(prefs: SharedPreferences): List<HiddenLibraryItem> {
        val raw = prefs.getString(KEY_ITEMS, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<HiddenLibraryItem>>(raw)
        }.getOrDefault(emptyList())
    }
}
