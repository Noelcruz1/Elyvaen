package com.mardous.booming.ui.screen.deezer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mardous.booming.data.model.Album
import com.mardous.booming.data.model.Artist
import com.mardous.booming.data.model.Song
import com.mardous.booming.data.repository.Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TopSong(
    val song: Song,
    val plays: Int
)

data class StatsUiState(
    val loading: Boolean = true,
    val totalDurationMs: Long = 0,
    val totalPlays: Int = 0,
    val uniqueTracks: Int = 0,
    val topSongs: List<TopSong> = emptyList(),
    val topArtists: List<Artist> = emptyList(),
    val topAlbums: List<Album> = emptyList(),
    val history: List<Song> = emptyList()
) {
    val isEmpty: Boolean
        get() = totalPlays == 0 && topSongs.isEmpty() && history.isEmpty()
}

class StatsViewModel(
    private val repository: Repository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val state = withContext(Dispatchers.IO) {
                runCatching {
                    val songs = repository.playCountSongs()
                    val counts = repository.findSongsInPlayCount(songs)
                    val byId = counts.associateBy { it.id }
                    val totalPlays = counts.sumOf { it.playCount }
                    val totalDuration = songs.sumOf { song ->
                        song.duration * (byId[song.id]?.playCount ?: 0)
                    }
                    StatsUiState(
                        loading = false,
                        totalDurationMs = totalDuration,
                        totalPlays = totalPlays,
                        uniqueTracks = counts.size,
                        topSongs = songs.take(10).map { song ->
                            TopSong(song, byId[song.id]?.playCount ?: 0)
                        },
                        topArtists = repository.topArtists().take(8),
                        topAlbums = repository.topAlbums().take(8),
                        history = repository.historySongs().take(10)
                    )
                }.getOrElse {
                    StatsUiState(loading = false)
                }
            }
            _uiState.value = state
        }
    }
}
