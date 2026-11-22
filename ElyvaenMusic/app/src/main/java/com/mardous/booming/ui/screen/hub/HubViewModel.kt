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
import android.net.Uri
import android.os.storage.StorageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.content.ContextCompat
import com.mardous.booming.data.local.AudioImportManager
import com.mardous.booming.data.local.HubStorageSummary
import com.mardous.booming.data.local.ImportProgress
import com.mardous.booming.data.local.ImportResult
import com.mardous.booming.data.local.NasSettings
import com.mardous.booming.data.local.NasSettingsStore
import com.mardous.booming.data.local.StorageOverview
import com.mardous.booming.data.local.WifiUploadController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HubUiState(
    val loading: Boolean = true,
    val storage: HubStorageSummary = HubStorageSummary(),
    val nas: NasSettings = NasSettings(),
    val importing: Boolean = false,
    val progress: ImportProgress? = null,
    val result: ImportResult? = null,
    val importError: String? = null,
    val clearingCache: Boolean = false,
    val cacheCleared: Long? = null
)

class HubViewModel(
    private val context: Context,
    private val storageOverview: StorageOverview,
    private val importer: AudioImportManager,
    private val nasStore: NasSettingsStore,
    private val wifiController: WifiUploadController
) : ViewModel() {

    private val _state = MutableStateFlow(HubUiState())
    val state = _state.asStateFlow()

    val wifi = wifiController.state

    private var importJob: Job? = null
    private var storageCallback: StorageManager.StorageVolumeCallback? = null

    init {
        refresh()
        observeVolumes()
        observeWifiUploads()
    }

    fun refresh() {
        viewModelScope.launch {
            val (summary, nas) = withContext(Dispatchers.IO) {
                storageOverview.snapshot() to nasStore.load()
            }
            _state.update {
                it.copy(loading = false, storage = summary, nas = nas)
            }
        }
    }

    fun importTree(uri: Uri) {
        if (_state.value.importing) return
        importJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    importing = true,
                    progress = null,
                    result = null,
                    importError = null,
                    cacheCleared = null
                )
            }
            try {
                val result = importer.importTree(uri) { progress ->
                    _state.update { it.copy(progress = progress) }
                }
                _state.update {
                    it.copy(importing = false, progress = null, result = result)
                }
            } catch (cancellation: CancellationException) {
                _state.update { it.copy(importing = false, progress = null) }
                throw cancellation
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        importing = false,
                        progress = null,
                        importError = t.message ?: "No se pudo importar la carpeta"
                    )
                }
            } finally {
                refreshStorage()
            }
        }
    }

    fun cancelImport() {
        importJob?.cancel()
        importJob = null
        _state.update { it.copy(importing = false, progress = null) }
    }

    fun clearCache() {
        if (_state.value.clearingCache) return
        viewModelScope.launch {
            _state.update { it.copy(clearingCache = true, cacheCleared = null) }
            val freed = runCatching { storageOverview.clearCache() }.getOrDefault(0L)
            _state.update { it.copy(clearingCache = false, cacheCleared = freed) }
            refreshStorage()
        }
    }

    fun startWifi() = wifiController.start()
    fun stopWifi() = wifiController.stop()
    fun clearWifiHistory() = wifiController.clearHistory()

    fun dismissMessages() {
        _state.update {
            it.copy(result = null, importError = null, cacheCleared = null)
        }
    }

    private fun refreshStorage() {
        viewModelScope.launch {
            val summary = withContext(Dispatchers.IO) { storageOverview.snapshot() }
            _state.update { it.copy(loading = false, storage = summary) }
        }
    }

    private fun observeWifiUploads() {
        viewModelScope.launch {
            var lastTimestamp = 0L
            wifiController.state.collect { wifi ->
                val newest = wifi.uploads.firstOrNull()?.timestamp ?: 0L
                if (newest != lastTimestamp) {
                    lastTimestamp = newest
                    if (newest > 0L) refreshStorage()
                }
            }
        }
    }

    private fun observeVolumes() {
        val manager = context.getSystemService(StorageManager::class.java) ?: return
        val callback = object : StorageManager.StorageVolumeCallback() {
            override fun onStateChanged(volume: android.os.storage.StorageVolume) {
                refresh()
            }
        }
        storageCallback = callback
        runCatching {
            manager.registerStorageVolumeCallback(
                ContextCompat.getMainExecutor(context),
                callback
            )
        }
    }

    override fun onCleared() {
        importJob?.cancel()
        storageCallback?.let { callback ->
            runCatching {
                context.getSystemService(StorageManager::class.java)
                    ?.unregisterStorageVolumeCallback(callback)
            }
        }
        storageCallback = null
        super.onCleared()
    }
}
