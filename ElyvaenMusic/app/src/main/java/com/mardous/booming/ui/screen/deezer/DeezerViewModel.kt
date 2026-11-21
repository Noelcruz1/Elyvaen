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

package com.mardous.booming.ui.screen.deezer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mardous.booming.data.remote.deezer.DeezerService
import com.mardous.booming.data.remote.deezer.model.DeezerAlbumDetail
import com.mardous.booming.data.remote.deezer.model.DeezerAlbumItem
import com.mardous.booming.data.remote.deezer.model.DeezerArtistItem
import com.mardous.booming.data.remote.deezer.model.DeezerPlaylistDetail
import com.mardous.booming.data.remote.deezer.model.DeezerPlaylistItem
import com.mardous.booming.data.remote.deezer.model.DeezerTrackItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DeezerSearchType {
    Tracks,
    Albums,
    Artists,
    Playlists
}

data class DeezerChart(
    val tracks: List<DeezerTrackItem> = emptyList(),
    val albums: List<DeezerAlbumItem> = emptyList(),
    val playlists: List<DeezerPlaylistItem> = emptyList(),
    val artists: List<DeezerArtistItem> = emptyList()
)

sealed interface DeezerDetail {
    data class Album(val album: DeezerAlbumDetail) : DeezerDetail
    data class Artist(
        val artist: DeezerArtistItem,
        val topTracks: List<DeezerTrackItem>,
        val albums: List<DeezerAlbumItem>
    ) : DeezerDetail
    data class Playlist(val playlist: DeezerPlaylistDetail) : DeezerDetail
}

data class DeezerUiState(
    val chartLoading: Boolean = true,
    val chartError: Boolean = false,
    val chart: DeezerChart = DeezerChart(),
    val query: String = "",
    val searchType: DeezerSearchType = DeezerSearchType.Tracks,
    val searchLoading: Boolean = false,
    val searchError: Boolean = false,
    val tracks: List<DeezerTrackItem> = emptyList(),
    val albums: List<DeezerAlbumItem> = emptyList(),
    val artists: List<DeezerArtistItem> = emptyList(),
    val playlists: List<DeezerPlaylistItem> = emptyList(),
    val detail: DeezerDetail? = null,
    val detailLoading: Boolean = false,
    val detailError: Boolean = false
)

class DeezerViewModel(
    private val deezerService: DeezerService
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeezerUiState())
    val uiState = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        loadChart()
    }

    fun loadChart() {
        viewModelScope.launch {
            _uiState.update { it.copy(chartLoading = true, chartError = false) }
            val chart = coroutineScope {
                val tracks = async { runCatching { deezerService.chartTracks() }.getOrNull() }
                val albums = async { runCatching { deezerService.chartAlbums() }.getOrNull() }
                val playlists = async { runCatching { deezerService.chartPlaylists() }.getOrNull() }
                val artists = async { runCatching { deezerService.chartArtists() }.getOrNull() }
                DeezerChart(
                    tracks = tracks.await()?.data.orEmpty(),
                    albums = albums.await()?.data.orEmpty(),
                    playlists = playlists.await()?.data.orEmpty(),
                    artists = artists.await()?.data.orEmpty()
                )
            }
            _uiState.update {
                it.copy(
                    chartLoading = false,
                    chartError = chart.tracks.isEmpty() && chart.albums.isEmpty() &&
                            chart.playlists.isEmpty() && chart.artists.isEmpty(),
                    chart = chart
                )
            }
        }
    }

    fun onQueryChange(query: String) {
        _uiState.update {
            it.copy(
                query = query,
                searchError = false,
                searchLoading = query.isNotBlank(),
                tracks = emptyList(),
                albums = emptyList(),
                artists = emptyList(),
                playlists = emptyList()
            )
        }
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(searchLoading = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(350)
            search(query, _uiState.value.searchType)
        }
    }

    fun onSearchTypeChange(type: DeezerSearchType) {
        _uiState.update {
            it.copy(
                searchType = type,
                tracks = emptyList(),
                albums = emptyList(),
                artists = emptyList(),
                playlists = emptyList()
            )
        }
        val query = _uiState.value.query
        if (query.isBlank()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            search(query, type)
        }
    }

    private suspend fun search(query: String, type: DeezerSearchType) {
        _uiState.update { it.copy(searchLoading = true, searchError = false) }
        runCatching {
            when (type) {
                DeezerSearchType.Tracks -> {
                    val result = deezerService.searchTracks(query)
                    _uiState.update { it.copy(tracks = result.data) }
                }
                DeezerSearchType.Albums -> {
                    val result = deezerService.searchAlbums(query)
                    _uiState.update { it.copy(albums = result.data) }
                }
                DeezerSearchType.Artists -> {
                    val result = deezerService.searchArtists(query)
                    _uiState.update { it.copy(artists = result.data) }
                }
                DeezerSearchType.Playlists -> {
                    val result = deezerService.searchPlaylists(query)
                    _uiState.update { it.copy(playlists = result.data) }
                }
            }
        }.onFailure {
            if (it is CancellationException) return@onFailure
            if (_uiState.value.query == query && _uiState.value.searchType == type) {
                _uiState.update { it.copy(searchError = true) }
            }
        }
        if (_uiState.value.query == query && _uiState.value.searchType == type) {
            _uiState.update { it.copy(searchLoading = false) }
        }
    }

    fun openAlbum(id: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(detailLoading = true, detailError = false) }
            runCatching { deezerService.albumDetail(id) }
                .onSuccess { album ->
                    _uiState.update {
                        it.copy(detail = DeezerDetail.Album(album), detailLoading = false)
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(detailLoading = false, detailError = true) }
                }
        }
    }

    fun openArtist(id: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(detailLoading = true, detailError = false) }
            runCatching {
                coroutineScope {
                    val artist = async { deezerService.artistDetail(id) }
                    val top = async { deezerService.artistTopTracks(id) }
                    val albums = async { deezerService.artistAlbums(id) }
                    DeezerDetail.Artist(
                        artist = artist.await(),
                        topTracks = top.await().data,
                        albums = albums.await().data
                    )
                }
            }.onSuccess { detail ->
                _uiState.update { it.copy(detail = detail, detailLoading = false) }
            }.onFailure {
                _uiState.update { it.copy(detailLoading = false, detailError = true) }
            }
        }
    }

    fun openPlaylist(id: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(detailLoading = true, detailError = false) }
            runCatching { deezerService.playlistDetail(id) }
                .onSuccess { playlist ->
                    _uiState.update {
                        it.copy(detail = DeezerDetail.Playlist(playlist), detailLoading = false)
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(detailLoading = false, detailError = true) }
                }
        }
    }

    fun closeDetail() {
        _uiState.update { it.copy(detail = null, detailError = false, detailLoading = false) }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _uiState.update {
            it.copy(
                query = "",
                searchLoading = false,
                searchError = false,
                tracks = emptyList(),
                albums = emptyList(),
                artists = emptyList(),
                playlists = emptyList()
            )
        }
    }
}
