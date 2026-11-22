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

package com.mardous.booming.ui.screen.health

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mardous.booming.R
import com.mardous.booming.data.model.Song
import com.mardous.booming.data.repository.Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

enum class MetadataIssue(@StringRes val labelRes: Int) {
    MissingTitle(R.string.metadata_health_issue_title),
    MissingArtist(R.string.metadata_health_issue_artist),
    MissingAlbum(R.string.metadata_health_issue_album),
    MissingYear(R.string.metadata_health_issue_year),
    MissingTrackNumber(R.string.metadata_health_issue_track)
}

data class DuplicateGroup(
    val title: String,
    val artist: String,
    val songs: List<Song>
)

data class MetadataHealthState(
    val loading: Boolean = true,
    val total: Int = 0,
    val healthy: Int = 0,
    val issues: Map<MetadataIssue, List<Song>> = emptyMap(),
    val duplicates: List<DuplicateGroup> = emptyList(),
    val issuesBySong: Map<Long, List<MetadataIssue>> = emptyMap()
) {
    val score: Int
        get() = if (total == 0) 100 else (healthy * 100) / total

    val unhealthy: Int
        get() = (total - healthy).coerceAtLeast(0)

    val duplicateSongs: Int
        get() = duplicates.sumOf { it.songs.size }
}

class MetadataHealthViewModel(
    private val repository: Repository
) : ViewModel() {

    private val _state = MutableStateFlow(MetadataHealthState())
    val state = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val songs = withContext(Dispatchers.IO) {
                runCatching { repository.allSongs() }.getOrDefault(emptyList())
            }
            val issues = linkedMapOf<MetadataIssue, List<Song>>(
                MetadataIssue.MissingTitle to songs.filter { it.title.isBlank() },
                MetadataIssue.MissingArtist to songs.filter { isUnknown(it.artistName) },
                MetadataIssue.MissingAlbum to songs.filter { isUnknown(it.albumName) },
                MetadataIssue.MissingYear to songs.filter { it.year <= 0 },
                MetadataIssue.MissingTrackNumber to songs.filter { it.trackNumber <= 0 }
            )
            val issuesBySong = mutableMapOf<Long, MutableList<MetadataIssue>>()
            issues.forEach { (issue, affected) ->
                affected.forEach { song ->
                    issuesBySong.getOrPut(song.id) { mutableListOf() }.add(issue)
                }
            }
            val unhealthyIds = issuesBySong.keys
            val duplicates = buildDuplicateGroups(songs)
            _state.value = MetadataHealthState(
                loading = false,
                total = songs.size,
                healthy = songs.count { it.id !in unhealthyIds },
                issues = issues,
                duplicates = duplicates,
                issuesBySong = issuesBySong.mapValues { it.value.toList() }
            )
        }
    }

    private fun buildDuplicateGroups(songs: List<Song>): List<DuplicateGroup> {
        val groups = mutableMapOf<Pair<String, String>, MutableList<Song>>()
        songs.forEach { song ->
            val title = normalize(song.title)
            if (title.isBlank()) return@forEach
            val artist = if (isUnknown(song.artistName)) normalize(song.albumName) else normalize(song.artistName)
            groups.getOrPut(title to artist) { mutableListOf() }.add(song)
        }
        return groups
            .filterValues { it.size > 1 }
            .map { (key, duplicates) ->
                DuplicateGroup(
                    title = duplicates.first().title,
                    artist = duplicates.first().artistName,
                    songs = duplicates.sortedBy { it.id }
                )
            }
            .sortedBy { it.title.lowercase(Locale.getDefault()) }
    }

    private fun normalize(value: String): String = value
        .lowercase(Locale.getDefault())
        .replace(Regex("\\(.*?\\)"), " ")
        .replace(Regex("\\[.*?]"), " ")
        .replace(Regex("[^\\p{L}\\p{N} ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun isUnknown(value: String?): Boolean {
        val normalized = value?.trim()?.lowercase(Locale.getDefault()).orEmpty()
        return normalized.isBlank() || normalized in unknownValues
    }

    companion object {
        private val unknownValues = setOf(
            "<unknown>",
            "unknown",
            "unknown artist",
            "unknown album",
            "artista desconocido",
            "artista desconocida",
            "album desconocido",
            "álbum desconocido",
            "desconocido",
            "desconocida"
        )
    }
}
