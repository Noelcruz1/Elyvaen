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
import android.content.Intent
import androidx.core.content.ContextCompat
import com.mardous.booming.data.remote.wifi.WifiNetwork
import com.mardous.booming.data.remote.wifi.WifiUploadEvent
import com.mardous.booming.service.WifiUploadService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class WifiUploadState(
    val starting: Boolean = false,
    val running: Boolean = false,
    val url: String? = null,
    val port: Int = 0,
    val error: String? = null,
    val uploads: List<WifiUploadEvent> = emptyList(),
    val totalBytes: Long = 0
)

/**
 * Single source of truth shared by the Hub screen and the foreground upload
 * service. The service owns the socket; this controller only exposes state.
 */
class WifiUploadController(private val context: Context) {

    private val _state = MutableStateFlow(WifiUploadState())
    val state = _state.asStateFlow()

    fun start() {
        val current = _state.value
        if (current.starting || current.running) return
        if (!WifiNetwork.isWifiConnected(context)) {
            _state.update {
                it.copy(error = "Conéctate a una red Wi-Fi para subir música")
            }
            return
        }
        _state.update { it.copy(starting = true, error = null) }
        try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, WifiUploadService::class.java)
                    .setAction(WifiUploadService.ACTION_START)
            )
        } catch (t: Throwable) {
            _state.update {
                it.copy(
                    starting = false,
                    running = false,
                    error = t.message ?: "No se pudo iniciar la subida por Wi-Fi"
                )
            }
        }
    }

    fun stop() {
        _state.update { it.copy(starting = false, running = false, url = null, port = 0) }
        runCatching {
            context.stopService(Intent(context, WifiUploadService::class.java))
        }
    }

    fun clearHistory() {
        _state.update { it.copy(uploads = emptyList(), totalBytes = 0) }
    }

    internal fun onServerStarted(url: String, port: Int) {
        _state.update {
            it.copy(
                starting = false,
                running = true,
                url = url,
                port = port,
                error = null
            )
        }
    }

    internal fun onError(message: String) {
        _state.update {
            it.copy(
                starting = false,
                running = false,
                url = null,
                port = 0,
                error = message
            )
        }
    }

    internal fun onStopped() {
        _state.update {
            it.copy(starting = false, running = false, url = null, port = 0)
        }
    }

    internal fun onUpload(event: WifiUploadEvent) {
        _state.update { current ->
            current.copy(
                uploads = (listOf(event) + current.uploads).take(MAX_EVENTS),
                totalBytes = current.totalBytes + if (event.success) event.bytes else 0
            )
        }
    }

    companion object {
        private const val MAX_EVENTS = 20
    }
}
