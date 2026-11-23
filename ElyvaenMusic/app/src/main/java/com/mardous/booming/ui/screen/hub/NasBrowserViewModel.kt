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
import com.mardous.booming.data.local.NasSettings
import com.mardous.booming.data.local.NasSettingsStore
import com.mardous.booming.data.local.NasProtocol
import com.mardous.booming.data.local.backup.BackupContent
import com.mardous.booming.data.local.backup.BackupManager
import com.mardous.booming.data.remote.nas.NasClient
import com.mardous.booming.data.remote.nas.NasDownloadManager
import com.mardous.booming.data.remote.nas.NasDownloadProgress
import com.mardous.booming.data.remote.nas.NasDownloadResult
import com.mardous.booming.data.remote.nas.NasEntry
import com.mardous.booming.data.remote.nas.NasSyncResult
import com.mardous.booming.data.remote.tailscale.TailscaleStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class NasBackupResult(
    val name: String,
    val bytes: Long
)

data class NasBrowserUiState(
    val configured: Boolean = false,
    val loading: Boolean = false,
    val currentPath: String = "",
    val entries: List<NasEntry> = emptyList(),
    val settings: NasSettings = NasSettings(),
    val error: String? = null,
    val downloading: Boolean = false,
    val progress: NasDownloadProgress? = null,
    val result: NasDownloadResult? = null,
    val syncResult: NasSyncResult? = null,
    val backingUp: Boolean = false,
    val backupResult: NasBackupResult? = null
)

class NasBrowserViewModel(
    private val context: Context,
    private val store: NasSettingsStore,
    private val client: NasClient,
    private val downloadManager: NasDownloadManager,
    private val backupManager: BackupManager,
    private val tailscaleStatus: TailscaleStatus
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
        val tailscaleError = connectionGuard(settings)
        if (tailscaleError != null) {
            _state.update {
                it.copy(
                    configured = true,
                    loading = false,
                    settings = settings,
                    currentPath = path,
                    entries = emptyList(),
                    error = tailscaleError,
                    result = null,
                    syncResult = null,
                    backupResult = null
                )
            }
            return
        }
        loadJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    configured = true,
                    loading = true,
                    settings = settings,
                    currentPath = path,
                    error = null,
                    result = null,
                    syncResult = null,
                    backupResult = null
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
        if (_state.value.downloading || _state.value.backingUp) return
        val settings = store.load()
        if (settings.host.isBlank()) {
            _state.update { it.copy(configured = false, error = "Configura el NAS primero") }
            return
        }
        connectionGuard(settings)?.let { error ->
            _state.update { it.copy(error = error) }
            return
        }
        val path = _state.value.currentPath
        downloadJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    downloading = true,
                    progress = null,
                    result = null,
                    syncResult = null,
                    backupResult = null,
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

    fun syncCurrentFolder() {
        if (_state.value.downloading || _state.value.backingUp) return
        val settings = store.load()
        if (settings.host.isBlank()) {
            _state.update { it.copy(configured = false, error = "Configura el NAS primero") }
            return
        }
        connectionGuard(settings)?.let { error ->
            _state.update { it.copy(error = error) }
            return
        }
        val path = _state.value.currentPath
        downloadJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    downloading = true,
                    progress = null,
                    result = null,
                    syncResult = null,
                    backupResult = null,
                    error = null
                )
            }
            try {
                val result = client.withSession(settings) { session ->
                    downloadManager.syncFolder(session, path) { progress ->
                        _state.update { it.copy(progress = progress) }
                    }
                }
                _state.update {
                    it.copy(downloading = false, progress = null, syncResult = result)
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

    fun backupToNas() {
        if (_state.value.downloading || _state.value.backingUp) return
        val settings = store.load()
        if (settings.host.isBlank()) {
            _state.update { it.copy(configured = false, error = "Configura el NAS primero") }
            return
        }
        connectionGuard(settings)?.let { error ->
            _state.update { it.copy(error = error) }
            return
        }
        val path = _state.value.currentPath
        viewModelScope.launch {
            _state.update {
                it.copy(
                    backingUp = true,
                    backupResult = null,
                    result = null,
                    syncResult = null,
                    error = null
                )
            }
            val stamp = System.currentTimeMillis()
            val file = File(context.cacheDir, "elyvaen-backup-$stamp.bmgbak")
            try {
                val created = withContext(Dispatchers.IO) {
                    backupManager.createBackupFile(file, BACKUP_CONTENTS)
                }
                if (!created || !file.isFile) {
                    throw IllegalStateException("No se pudo crear la copia de seguridad")
                }
                val remotePath = listOf(path.trim('/'), file.name)
                    .filter { it.isNotBlank() }
                    .joinToString("/")
                client.withSession(settings) { session ->
                    file.inputStream().use { input ->
                        session.upload(input, remotePath, file.length())
                    }
                }
                _state.update {
                    it.copy(
                        backingUp = false,
                        backupResult = NasBackupResult(file.name, file.length())
                    )
                }
            } catch (cancellation: CancellationException) {
                _state.update { it.copy(backingUp = false) }
                throw cancellation
            } catch (t: Throwable) {
                _state.update {
                    it.copy(backingUp = false, error = readableError(t))
                }
            } finally {
                file.delete()
            }
        }
    }

    private fun downloadFile(entry: NasEntry) {
        if (_state.value.downloading || _state.value.backingUp) return
        val settings = store.load()
        connectionGuard(settings)?.let { error ->
            _state.update { it.copy(error = error) }
            return
        }
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
        _state.update {
            it.copy(error = null, result = null, syncResult = null, backupResult = null)
        }
    }

    private fun connectionGuard(settings: NasSettings): String? {
        if (settings.protocol == NasProtocol.SFTP && settings.hostKey.isBlank()) {
            return "Falta la huella SSH; configura el NAS"
        }
        return tailscaleStatus.connectionError(settings.host)
    }

    private fun readableError(t: Throwable): String {
        val message = t.message.orEmpty()
        return when {
            message.contains("fingerprint", true) ||
                    message.contains("host key", true) ->
                "La huella SSH no coincide con el servidor"
            message.contains("STATUS_LOGON_FAILURE", true) -> "Credenciales rechazadas"
            message.contains("STATUS_OBJECT_NAME_NOT_FOUND", true) -> "Ruta no encontrada"
            message.contains("STATUS_ACCESS_DENIED", true) -> "Acceso denegado"
            message.contains("timeout", true) -> "El NAS no respondió a tiempo"
            message.contains("UnknownHost", true) ||
                    message.contains("unable to resolve", true) ||
                    message.contains("nodename nor servname", true) ->
                "No se pudo resolver el host. Activa Tailscale si usas MagicDNS."
            message.isBlank() -> "No se pudo conectar con el NAS"
            else -> message
        }
    }

    override fun onCleared() {
        loadJob?.cancel()
        downloadJob?.cancel()
        super.onCleared()
    }

    companion object {
        private val BACKUP_CONTENTS = listOf(
            BackupContent.Settings,
            BackupContent.Playlists,
            BackupContent.PlayInfo,
            BackupContent.Lyrics
        )
    }
}
