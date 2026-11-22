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

package com.mardous.booming.ui.screen.hub

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mardous.booming.data.local.LocalVideo
import com.mardous.booming.data.local.MediaServerController
import com.mardous.booming.data.local.MediaServerState
import com.mardous.booming.data.local.VideoLibrary
import com.mardous.booming.data.model.Song
import com.mardous.booming.data.remote.mediaserver.DlnaRenderer
import com.mardous.booming.data.remote.mediaserver.DlnaRendererClient
import com.mardous.booming.playback.QueueStateHolder
import com.mardous.booming.playback.visualizer.AudioVisualizerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class SalaUiState(
    val scanning: Boolean = false,
    val sending: Boolean = false,
    val renderers: List<DlnaRenderer> = emptyList(),
    val videos: List<LocalVideo> = emptyList(),
    val message: String? = null,
    val error: String? = null
)

class SalaViewModel(
    private val context: Context,
    private val rendererClient: DlnaRendererClient,
    private val mediaServerController: MediaServerController,
    private val videoLibrary: VideoLibrary,
    queueStateHolder: QueueStateHolder,
    visualizerState: AudioVisualizerState
) : ViewModel() {

    val currentSong = queueStateHolder.currentSong.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        Song.emptySong
    )
    val waveform = visualizerState.waveform
    val mediaServer = mediaServerController.state

    private val _state = MutableStateFlow(SalaUiState())
    val state = _state.asStateFlow()

    init {
        refreshVideos()
    }

    fun refreshVideos() {
        viewModelScope.launch {
            val videos = videoLibrary.videos()
            _state.update { it.copy(videos = videos) }
        }
    }

    fun scanRenderers() {
        if (_state.value.scanning) return
        viewModelScope.launch {
            _state.update { it.copy(scanning = true, error = null, message = null) }
            val renderers = withContext(Dispatchers.IO) {
                runCatching { rendererClient.discover() }.getOrDefault(emptyList())
            }
            _state.update {
                it.copy(
                    scanning = false,
                    renderers = renderers,
                    message = if (renderers.isEmpty()) {
                        "No se encontraron TVs o dispositivos DLNA"
                    } else {
                        "${renderers.size} dispositivo(s) encontrados"
                    }
                )
            }
        }
    }

    fun sendTo(renderer: DlnaRenderer) {
        if (_state.value.sending) return
        val song = currentSong.value
        if (song.id <= 0L) {
            _state.update { it.copy(error = "No hay ninguna canción sonando") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(sending = true, error = null, message = null) }
            try {
                mediaServerController.start()
                val server = withTimeoutOrNull(8_000) {
                    mediaServerController.state.first { it.running && it.ip != null }
                } ?: throw IllegalStateException("Activa el servidor multimedia en H3")
                val ip = server.ip ?: throw IllegalStateException("Sin IP Wi-Fi")
                val url = "http://$ip:${server.port}/dlna/media/${song.id}"
                withContext(Dispatchers.IO) {
                    rendererClient.play(renderer, url, song).getOrThrow()
                }
                _state.update {
                    it.copy(
                        sending = false,
                        message = "Enviado a ${renderer.name}: ${song.title}"
                    )
                }
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        sending = false,
                        error = t.message ?: "No se pudo enviar a la TV"
                    )
                }
            }
        }
    }

    fun getMediaServerState(): MediaServerState = mediaServer.value

    fun dismissMessages() {
        _state.update { it.copy(message = null, error = null) }
    }
}
