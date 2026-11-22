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

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kyant.taglib.TagLib
import com.mardous.booming.R
import com.mardous.booming.data.local.EditTarget
import com.mardous.booming.data.local.MetadataReader
import com.mardous.booming.data.local.MetadataWriter
import com.mardous.booming.data.model.Song
import com.mardous.booming.data.remote.musicbrainz.model.MusicBrainzRecording
import com.mardous.booming.data.repository.Repository
import com.mardous.booming.extensions.media.asAlbumCoverUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    MissingTrackNumber(R.string.metadata_health_issue_track),
    MissingCover(R.string.metadata_health_issue_cover)
}

data class DuplicateGroup(
    val title: String,
    val artist: String,
    val songs: List<Song>
)

sealed interface AutoFixState {
    data object Idle : AutoFixState
    data class Running(val done: Int, val total: Int) : AutoFixState
    data class Finished(val fixed: Int, val skipped: Int, val failed: Int) : AutoFixState
    data class Error(val message: String?) : AutoFixState
}

data class MetadataHealthState(
    val loading: Boolean = true,
    val scanningCovers: Boolean = false,
    val total: Int = 0,
    val healthy: Int = 0,
    val issues: Map<MetadataIssue, List<Song>> = emptyMap(),
    val duplicates: List<DuplicateGroup> = emptyList(),
    val issuesBySong: Map<Long, List<MetadataIssue>> = emptyMap()
) {
    val score: Int
        get() = if (total == 0) 100 else (healthy * 100) / total

    val unhealthy: Int
        get() = issuesBySong.size

    val duplicateSongs: Int
        get() = duplicates.sumOf { it.songs.size }
}

class MetadataHealthViewModel(
    private val context: Context,
    private val repository: Repository
) : ViewModel() {

    private val _state = MutableStateFlow(MetadataHealthState())
    val state = _state.asStateFlow()

    private val _autoFixState = MutableStateFlow<AutoFixState>(AutoFixState.Idle)
    val autoFixState = _autoFixState.asStateFlow()

    private var lastSongs: List<Song> = emptyList()
    private var autoFixRunning = false

    init {
        refresh()
    }

    fun refresh() {
        _autoFixState.value = AutoFixState.Idle
        viewModelScope.launch {
            loadHealth()
        }
    }

    private suspend fun loadHealth() {
        _state.update { it.copy(loading = true) }
        val songs = withContext(Dispatchers.IO) {
            runCatching { repository.allSongs() }.getOrDefault(emptyList())
        }
        lastSongs = songs
        val issues = linkedMapOf<MetadataIssue, List<Song>>(
            MetadataIssue.MissingTitle to songs.filter { it.title.isBlank() },
            MetadataIssue.MissingArtist to songs.filter { isUnknown(it.artistName) },
            MetadataIssue.MissingAlbum to songs.filter { isUnknown(it.albumName) },
            MetadataIssue.MissingTrackNumber to songs.filter { it.trackNumber <= 0 },
            MetadataIssue.MissingCover to emptyList()
        )
        val issuesBySong = buildIssuesBySong(issues)
        val coreUnhealthyIds = buildSet {
            issues[MetadataIssue.MissingTitle]?.forEach { add(it.id) }
            issues[MetadataIssue.MissingArtist]?.forEach { add(it.id) }
            issues[MetadataIssue.MissingAlbum]?.forEach { add(it.id) }
        }
        val duplicates = buildDuplicateGroups(songs)
        _state.value = MetadataHealthState(
            loading = false,
            scanningCovers = false,
            total = songs.size,
            healthy = songs.count { it.id !in coreUnhealthyIds },
            issues = issues,
            duplicates = duplicates,
            issuesBySong = issuesBySong
        )
    }

    /**
     * Reads embedded artwork for one track per album. It is deliberately a
     * separate action because scanning the whole library can take a few
     * seconds; a retriever failure is treated as "unknown", not as missing.
     */
    fun scanCovers() {
        if (_state.value.scanningCovers || lastSongs.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(scanningCovers = true) }
            val missing = withContext(Dispatchers.IO) {
                lastSongs
                    .filter { it.albumId > 0 }
                    .groupBy { it.albumId }
                    .values
                    .mapNotNull { group -> group.firstOrNull() }
                    .filterNot { hasEmbeddedCover(it) }
            }
            val issues = _state.value.issues + (MetadataIssue.MissingCover to missing)
            _state.update {
                it.copy(
                    scanningCovers = false,
                    issues = issues,
                    issuesBySong = buildIssuesBySong(issues)
                )
            }
        }
    }

    /**
     * Conservative batch fix: only empty artist, album and year fields are
     * filled from MusicBrainz. Existing tags are never overwritten and each
     * file is rescanned by MetadataWriter.
     */
    fun autoFixFields() {
        if (autoFixRunning) return
        val candidates = _state.value.issuesBySong.keys
            .mapNotNull { id -> lastSongs.firstOrNull { it.id == id } }
            .filter { song ->
                val songIssues = _state.value.issuesBySong[song.id].orEmpty()
                song.title.isNotBlank() &&
                        !isUnknown(song.artistName) &&
                        songIssues.contains(MetadataIssue.MissingAlbum)
            }
            .take(MAX_AUTO_FIX)
        if (candidates.isEmpty()) {
            _autoFixState.value = AutoFixState.Finished(0, 0, 0)
            return
        }
        autoFixRunning = true
        viewModelScope.launch {
            var fixed = 0
            var skipped = 0
            var failed = 0
            candidates.forEachIndexed { index, song ->
                _autoFixState.value = AutoFixState.Running(index, candidates.size)
                val result = runCatching { autoFixSong(song) }
                if (result.getOrDefault(false)) {
                    fixed++
                } else if (result.exceptionOrNull() != null) {
                    failed++
                } else {
                    skipped++
                }
                if (index < candidates.lastIndex) delay(MUSICBRAINZ_DELAY_MS)
            }
            autoFixRunning = false
            loadHealth()
            _autoFixState.value = AutoFixState.Finished(fixed, skipped, failed)
        }
    }

    fun clearAutoFixState() {
        _autoFixState.value = AutoFixState.Idle
    }

    private suspend fun autoFixSong(song: Song): Boolean {
        val artistQuery = song.artistName.takeUnless { isUnknown(it) }.orEmpty()
        val musicBrainz = repository.musicBrainzRecording(song.title, artistQuery)
            ?.recordings
            ?.let { pickRecording(it, song.title, artistQuery) }
            ?.let { metadataFrom(it) }
        val needsDeezer = musicBrainz == null ||
                musicBrainz.album == null ||
                musicBrainz.year == null
        val deezer = if (needsDeezer) {
            repository.deezerMetadata(song.artistName, song.title)
        } else {
            null
        }
        val artist = musicBrainz?.artist
        val album = musicBrainz?.album ?: deezer?.album
        val year = musicBrainz?.year ?: deezer?.year
        val changes = linkedMapOf<String, String>()
        if (isUnknown(song.artistName) && !artist.isNullOrBlank()) {
            changes[MetadataReader.ARTIST] = artist
            changes[MetadataReader.ALBUM_ARTIST] = artist
        }
        if (isUnknown(song.albumName) && !album.isNullOrBlank()) {
            changes[MetadataReader.ALBUM] = album
        }
        if (song.year <= 0 && year != null) {
            changes[MetadataReader.YEAR] = year.toString()
        }
        if (changes.isEmpty()) return false
        val writer = MetadataWriter()
        writer.propertyMap(changes)
        return withContext(Dispatchers.IO) {
            writer.write(context, EditTarget.song(song)).isSuccess
        }
    }

    private fun pickRecording(
        recordings: List<MusicBrainzRecording>,
        title: String,
        artist: String
    ): MusicBrainzRecording? {
        val sameTitle = recordings.filter { it.title.equals(title, ignoreCase = true) }
        if (sameTitle.isEmpty()) return null
        if (artist.isNotBlank()) {
            return sameTitle.firstOrNull { recording ->
                recording.artistCredit.any { it.name.contains(artist, ignoreCase = true) }
            } ?: sameTitle.firstOrNull()
        }
        return sameTitle.firstOrNull()
    }

    private fun metadataFrom(recording: MusicBrainzRecording): CandidateMetadata {
        val artist = recording.artistCredit
            .map { it.name }
            .filter { it.isNotBlank() }
            .joinToString(", ")
            .takeIf { it.isNotBlank() }
        val release = recording.releases.firstOrNull()
        val year = (recording.firstReleaseDate ?: release?.date)
            ?.take(4)
            ?.toIntOrNull()
        return CandidateMetadata(
            artist = artist,
            album = release?.title?.takeIf { it.isNotBlank() },
            year = year
        )
    }

    private fun hasEmbeddedCover(song: Song): Boolean {
        val embedded = try {
            context.contentResolver.openFileDescriptor(song.uri, "r")?.use { fd ->
                TagLib.getFrontCover(fd.dup().detachFd()) != null
            } ?: return true
        } catch (_: Exception) {
            return true
        }
        if (embedded) return true
        if (song.albumId <= 0) return false
        return try {
            context.contentResolver.openInputStream(song.albumId.asAlbumCoverUri())
                ?.use { true } ?: false
        } catch (_: Exception) {
            true
        }
    }

    private fun buildIssuesBySong(issues: Map<MetadataIssue, List<Song>>): Map<Long, List<MetadataIssue>> {
        val result = mutableMapOf<Long, MutableList<MetadataIssue>>()
        issues.forEach { (issue, affected) ->
            affected.forEach { song ->
                result.getOrPut(song.id) { mutableListOf() }.add(issue)
            }
        }
        return result.mapValues { it.value.toList() }
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
            .map { (_, duplicates) ->
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

    private data class CandidateMetadata(
        val artist: String?,
        val album: String?,
        val year: Int?
    )

    companion object {
        const val MAX_AUTO_FIX = 10
        private const val MUSICBRAINZ_DELAY_MS = 1_100L

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
