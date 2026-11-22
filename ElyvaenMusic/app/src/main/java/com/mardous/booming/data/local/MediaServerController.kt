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
import com.mardous.booming.service.MediaServerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class MediaServerState(
    val starting: Boolean = false,
    val running: Boolean = false,
    val ip: String? = null,
    val port: Int = 0,
    val dlnaName: String? = null,
    val webUrl: String? = null,
    val davUrl: String? = null,
    val error: String? = null
)

class MediaServerController(private val context: Context) {

    private val _state = MutableStateFlow(MediaServerState())
    val state = _state.asStateFlow()

    fun start() {
        val current = _state.value
        if (current.starting || current.running) return
        if (!WifiNetwork.isWifiConnected(context)) {
            _state.update {
                it.copy(error = "Conéctate a una red Wi-Fi para transmitir")
            }
            return
        }
        _state.update { it.copy(starting = true, error = null) }
        try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, MediaServerService::class.java)
                    .setAction(MediaServerService.ACTION_START)
            )
        } catch (t: Throwable) {
            _state.update {
                it.copy(
                    starting = false,
                    running = false,
                    error = t.message ?: "No se pudo iniciar el servidor multimedia"
                )
            }
        }
    }

    fun stop() {
        _state.update {
            it.copy(starting = false, running = false, webUrl = null, davUrl = null)
        }
        runCatching {
            context.stopService(Intent(context, MediaServerService::class.java))
        }
    }

    internal fun onStarted(
        ip: String,
        port: Int,
        dlnaName: String,
        webUrl: String,
        davUrl: String
    ) {
        _state.update {
            it.copy(
                starting = false,
                running = true,
                ip = ip,
                port = port,
                dlnaName = dlnaName,
                webUrl = webUrl,
                davUrl = davUrl,
                error = null
            )
        }
    }

    internal fun onError(message: String) {
        _state.update {
            it.copy(
                starting = false,
                running = false,
                webUrl = null,
                davUrl = null,
                error = message
            )
        }
    }

    internal fun onStopped() {
        _state.update {
            it.copy(
                starting = false,
                running = false,
                ip = null,
                port = 0,
                dlnaName = null,
                webUrl = null,
                davUrl = null
            )
        }
    }
}
