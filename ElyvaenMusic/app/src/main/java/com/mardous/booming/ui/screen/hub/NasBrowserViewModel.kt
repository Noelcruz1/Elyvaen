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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mardous.booming.data.local.NasSettings
import com.mardous.booming.data.local.NasSettingsStore
import com.mardous.booming.data.remote.nas.NasClient
import com.mardous.booming.data.remote.nas.NasDownloadManager
import com.mardous.booming.data.remote.nas.NasDownloadProgress
import com.mardous.booming.data.remote.nas.NasDownloadResult
import com.mardous.booming.data.remote.nas.NasEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NasBrowserUiState(
    val configured: Boolean = false,
    val loading: Boolean = false,
    val currentPath: String = "",
    val entries: List<NasEntry> = emptyList(),
    val settings: NasSettings = NasSettings(),
    val error: String? = null,
    val downloading: Boolean = false,
    val progress: NasDownloadProgress? = null,
    val result: NasDownloadResult? = null
)

class NasBrowserViewModel(
    private val store: NasSettingsStore,
    private val client: NasClient,
    private val downloadManager: NasDownloadManager
) : ViewModel() {

    private val _state = MutableStateFlow(NasBrowserUiState())
    val state = _state.asStateFlow()

    private var loadJob: Job? = null
    private var downloadJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        load(_state.value.currentPath)
    }

    fun open(entry: NasEntry) {
        if (entry.isDirectory) {
            load(entry.path)
        } else if (!downloadManager.isSupportedFile(entry.name)) {
            _state.update { it.copy(error = "No es un archivo de audio compatible") }
        } else {
            downloadFile(entry)
        }
    }

    fun load(path: String) {
        val settings = store.load()
        if (settings.host.isBlank()) {
            _state.update {
                it.copy(
                    configured = false,
                    loading = false,
                    settings = settings,
                    entries = emptyList(),
                    error = null
                )
            }
            return
        }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    configured = true,
                    loading = true,
                    settings = settings,
                    currentPath = path,
                    error = null,
                    result = null
                )
            }
            try {
                val entries = client.withSession(settings) { session -> session.list(path) }
                _state.update {
                    it.copy(loading = false, entries = entries, error = null)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        loading = false,
                        entries = emptyList(),
                        error = readableError(t)
                    )
                }
            }
        }
    }

    /**
     * @return true when the view model handled the back gesture by moving to
     * the parent folder; false when the screen itself should close.
     */
    fun navigateUp(): Boolean {
        val path = _state.value.currentPath.trim('/')
        if (path.isBlank()) return false
        load(path.substringBeforeLast('/', ""))
        return true
    }

    fun downloadCurrentFolder() {
        if (_state.value.downloading) return
        val settings = store.load()
        if (settings.host.isBlank()) {
            _state.update { it.copy(configured = false, error = "Configura el NAS primero") }
            return
        }
        val path = _state.value.currentPath
        downloadJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    downloading = true,
                    progress = null,
                    result = null,
                    error = null
                )
            }
            try {
                val result = client.withSession(settings) { session ->
                    downloadManager.downloadFolder(session, path) { progress ->
                        _state.update { it.copy(progress = progress) }
                    }
                }
                _state.update {
                    it.copy(downloading = false, progress = null, result = result)
                }
            } catch (cancellation: CancellationException) {
                _state.update { it.copy(downloading = false, progress = null) }
                throw cancellation
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        downloading = false,
                        progress = null,
                        error = readableError(t)
                    )
                }
            }
        }
    }

    private fun downloadFile(entry: NasEntry) {
        if (_state.value.downloading) return
        val settings = store.load()
        downloadJob = viewModelScope.launch {
            _state.update {
                it.copy(downloading = true, progress = null, result = null, error = null)
            }
            try {
                val result = client.withSession(settings) { session ->
                    downloadManager.downloadFile(session, entry) { progress ->
                        _state.update { it.copy(progress = progress) }
                    }
                }
                _state.update {
                    it.copy(downloading = false, progress = null, result = result)
                }
            } catch (cancellation: CancellationException) {
                _state.update { it.copy(downloading = false, progress = null) }
                throw cancellation
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        downloading = false,
                        progress = null,
                        error = readableError(t)
                    )
                }
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        _state.update { it.copy(downloading = false, progress = null) }
    }

    fun dismissMessages() {
        _state.update { it.copy(error = null, result = null) }
    }

    private fun readableError(t: Throwable): String {
        val message = t.message.orEmpty()
        return when {
            message.contains("STATUS_LOGON_FAILURE", true) -> "Credenciales rechazadas"
            message.contains("STATUS_OBJECT_NAME_NOT_FOUND", true) -> "Ruta no encontrada"
            message.contains("STATUS_ACCESS_DENIED", true) -> "Acceso denegado"
            message.contains("timeout", true) -> "El NAS no respondió a tiempo"
            message.isBlank() -> "No se pudo conectar con el NAS"
            else -> message
        }
    }

    override fun onCleared() {
        loadJob?.cancel()
        downloadJob?.cancel()
        super.onCleared()
    }
}
