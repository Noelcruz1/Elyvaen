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
import com.mardous.booming.data.remote.deezer.DeezerAccount
import com.mardous.booming.data.remote.deezer.DeezerAccountStore
import com.mardous.booming.data.remote.deezer.DeezerDownloadManager
import com.mardous.booming.data.remote.deezer.DeezerPrivateClient
import com.mardous.booming.data.remote.deezer.DeezerService
import com.mardous.booming.data.remote.deezer.DeezerSmartLogin
import com.mardous.booming.data.remote.deezer.DeezerStreamingClient
import com.mardous.booming.data.remote.deezer.DeezerStreamingUnauthorizedException
import com.mardous.booming.data.remote.deezer.DeezerTokenStore
import com.mardous.booming.data.remote.deezer.model.DeezerAlbumDetail
import com.mardous.booming.data.remote.deezer.model.DeezerAlbumItem
import com.mardous.booming.data.remote.deezer.model.DeezerArtistItem
import com.mardous.booming.data.remote.deezer.model.DeezerPlaylistDetail
import com.mardous.booming.data.remote.deezer.model.DeezerPlaylistItem
import com.mardous.booming.data.remote.deezer.model.DeezerTrackItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

data class DeezerLibrary(
    val artists: List<DeezerArtistItem> = emptyList(),
    val albums: List<DeezerAlbumItem> = emptyList(),
    val playlists: List<DeezerPlaylistItem> = emptyList(),
    val tracks: List<DeezerTrackItem> = emptyList()
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
    val account: DeezerAccount? = null,
    val connectLoading: Boolean = false,
    val connectError: Boolean = false,
    val library: DeezerLibrary? = null,
    val libraryLoading: Boolean = false,
    val libraryError: Boolean = false,
    val favoriteError: Boolean = false,
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
    val detailError: Boolean = false,
    val streamingActive: Boolean = false,
    val smartLoginUrl: String? = null,
    val smartLoginCode: String? = null,
    val smartLoginLoading: Boolean = false,
    val smartLoginPolling: Boolean = false,
    val smartLoginError: Boolean = false
)

class DeezerViewModel(
    private val deezerService: DeezerService,
    private val privateClient: DeezerPrivateClient,
    private val accountStore: DeezerAccountStore,
    private val downloadManager: DeezerDownloadManager,
    private val streamingClient: DeezerStreamingClient,
    private val tokenStore: DeezerTokenStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        DeezerUiState(streamingActive = tokenStore.isActive)
    )
    val uiState = _uiState.asStateFlow()

    val downloadStates = downloadManager.states
    val downloads = downloadManager.entries

    private var searchJob: Job? = null
    private var smartLoginJob: Job? = null

    init {
        loadChart()
        restoreAccount()
    }

    fun setAlbumSaved(albumId: Long, saved: Boolean) {
        changeFavorite { privateClient.setAlbumSaved(albumId, saved) }
    }

    fun setArtistSaved(artistId: Long, saved: Boolean) {
        changeFavorite { privateClient.setArtistSaved(artistId, saved) }
    }

    fun setTrackSaved(trackId: Long, saved: Boolean) {
        changeFavorite { privateClient.setTrackSaved(trackId, saved) }
    }

    fun clearFavoriteError() {
        _uiState.update { it.copy(favoriteError = false) }
    }

    private fun changeFavorite(block: () -> Unit) {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { block() } }
                .onSuccess { loadLibrary() }
                .onFailure { error ->
                    android.util.Log.w(
                        "DeezerFavorite",
                        "favorite change failed: ${error.message}"
                    )
                    _uiState.update { it.copy(favoriteError = true) }
                }
        }
    }

    private fun restoreAccount() {
        if (!accountStore.isConnected) return
        viewModelScope.launch {
            val account = privateClient.restore()
            if (account != null) {
                _uiState.update { it.copy(account = account) }
                loadLibrary()
            }
        }
    }

    fun connect(arl: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(connectLoading = true, connectError = false) }
            runCatching { privateClient.connect(arl) }
                .onSuccess { account ->
                    _uiState.update {
                        it.copy(account = account, connectLoading = false)
                    }
                    loadLibrary()
                }
                .onFailure {
                    _uiState.update {
                        it.copy(connectLoading = false, connectError = true)
                    }
                }
        }
    }

    fun disconnect() {
        smartLoginJob?.cancel()
        privateClient.disconnect()
        tokenStore.clear()
        _uiState.update {
            it.copy(
                account = null,
                connectError = false,
                library = null,
                libraryLoading = false,
                libraryError = false,
                streamingActive = false,
                smartLoginUrl = null,
                smartLoginCode = null,
                smartLoginLoading = false,
                smartLoginPolling = false,
                smartLoginError = false
            )
        }
    }

    fun startSmartLogin() {
        if (_uiState.value.smartLoginLoading || _uiState.value.smartLoginPolling) return
        smartLoginJob?.cancel()
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    smartLoginLoading = true,
                    smartLoginError = false,
                    smartLoginUrl = null,
                    smartLoginCode = null
                )
            }
            streamingClient.requestSmartLogin()
                .onSuccess { data ->
                    _uiState.update {
                        it.copy(
                            smartLoginLoading = false,
                            smartLoginPolling = true,
                            smartLoginUrl = data.url,
                            smartLoginCode = data.code
                        )
                    }
                    pollSmartLogin(data)
                }
                .onFailure {
                    _uiState.update {
                        it.copy(
                            smartLoginLoading = false,
                            smartLoginPolling = false,
                            smartLoginError = true
                        )
                    }
                }
        }
    }

    private fun pollSmartLogin(data: DeezerSmartLogin) {
        smartLoginJob = viewModelScope.launch {
            val interval = data.pollingIntervalSeconds * 1000L
            val deadline = System.currentTimeMillis() + data.ttlSeconds * 1000L
            while (System.currentTimeMillis() < deadline) {
                delay(interval)
                val token = streamingClient.pollSmartLogin(data.code).getOrNull()
                if (token != null) {
                    tokenStore.accessToken = token.accessToken
                    tokenStore.userId = token.userId
                    _uiState.update {
                        it.copy(
                            streamingActive = true,
                            smartLoginUrl = null,
                            smartLoginCode = null,
                            smartLoginLoading = false,
                            smartLoginPolling = false,
                            smartLoginError = false
                        )
                    }
                    return@launch
                }
            }
            _uiState.update {
                it.copy(
                    smartLoginUrl = null,
                    smartLoginCode = null,
                    smartLoginPolling = false,
                    smartLoginError = true
                )
            }
        }
    }

    fun cancelSmartLogin() {
        smartLoginJob?.cancel()
        smartLoginJob = null
        _uiState.update {
            it.copy(
                smartLoginUrl = null,
                smartLoginCode = null,
                smartLoginLoading = false,
                smartLoginPolling = false
            )
        }
    }

    fun disconnectStreaming() {
        smartLoginJob?.cancel()
        smartLoginJob = null
        tokenStore.clear()
        _uiState.update {
            it.copy(
                streamingActive = false,
                smartLoginUrl = null,
                smartLoginCode = null,
                smartLoginLoading = false,
                smartLoginPolling = false,
                smartLoginError = false
            )
        }
    }

    fun clearStreamingError() {
        _uiState.update { it.copy(smartLoginError = false) }
    }

    suspend fun resolveStreamUrl(trackId: Long): Result<String> {
        val token = tokenStore.accessToken?.takeIf { it.isNotBlank() }
            ?: return Result.failure(IllegalStateException("Activa el streaming completo de Deezer"))
        val result = streamingClient.streamUrls(trackId, token)
        val error = result.exceptionOrNull()
        if (error != null) {
            if (error is DeezerStreamingUnauthorizedException) {
                tokenStore.clear()
                _uiState.update {
                    it.copy(
                        streamingActive = false,
                        smartLoginError = true
                    )
                }
            }
            return Result.failure(error)
        }
        val url = result.getOrThrow().bestUrl
        return if (url.isNullOrBlank()) {
            Result.failure(IllegalStateException("Deezer no devolvió una URL reproducible"))
        } else {
            Result.success(url)
        }
    }

    fun loadLibrary() {
        if (!privateClient.isConnected) return
        viewModelScope.launch {
            _uiState.update { it.copy(libraryLoading = true, libraryError = false) }
            val previous = _uiState.value.library
            val artistResult = async(Dispatchers.IO) {
                runCatching { privateClient.savedArtists() }
            }
            val albumResult = async(Dispatchers.IO) {
                runCatching { privateClient.savedAlbums() }
            }
            val playlistResult = async(Dispatchers.IO) {
                runCatching { privateClient.savedPlaylists() }
            }
            val trackResult = async(Dispatchers.IO) {
                runCatching { privateClient.savedTracks() }
            }
            val artists = artistResult.await()
            val albums = albumResult.await()
            val playlists = playlistResult.await()
            val tracks = trackResult.await()
            val loaded = listOf<Result<*>>(artists, albums, playlists, tracks)
            loaded.forEachIndexed { index, result ->
                result.exceptionOrNull()?.let { error ->
                    android.util.Log.w(
                        "DeezerLibrary",
                        "section $index could not load: ${error.message}"
                    )
                }
            }
            val library = DeezerLibrary(
                artists = artists.getOrElse { previous?.artists.orEmpty() },
                albums = albums.getOrElse { previous?.albums.orEmpty() },
                playlists = playlists.getOrElse { previous?.playlists.orEmpty() },
                tracks = tracks.getOrElse { previous?.tracks.orEmpty() }
            )
            val allFailed = loaded.all { it.isFailure }
            _uiState.update {
                it.copy(
                    library = if (allFailed) previous else library,
                    libraryLoading = false,
                    libraryError = allFailed
                )
            }
        }
    }

    fun downloadTrack(track: DeezerTrackItem) {
        downloadManager.downloadTrack(track)
    }

    fun clearDownloadsHistory() {
        downloadManager.clearFinished()
    }

    fun downloadAll(tracks: List<DeezerTrackItem>) {
        downloadManager.downloadAll(tracks)
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
