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
import com.mardous.booming.data.local.NasProtocol
import com.mardous.booming.data.local.NasSettings
import com.mardous.booming.data.local.NasSettingsStore
import com.mardous.booming.data.remote.nas.NasConnectionTester
import com.mardous.booming.data.remote.nas.NasTestResult
import com.mardous.booming.data.remote.tailscale.TailscaleStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class NasSettingsUiState(
    val protocol: NasProtocol = NasProtocol.WEBDAV,
    val host: String = "",
    val port: String = "",
    val share: String = "",
    val path: String = "",
    val username: String = "",
    val password: String = "",
    val domain: String = "",
    val useTls: Boolean = true,
    val tailscaleInstalled: Boolean = false,
    val vpnActive: Boolean = false,
    val tailnetAddress: String? = null,
    val magicDnsHost: Boolean = false,
    val testing: Boolean = false,
    val testResult: NasTestResult? = null,
    val saved: Boolean = false,
    val storageAvailable: Boolean = true,
    val validationError: String? = null
)

class NasSettingsViewModel(
    private val store: NasSettingsStore,
    private val tester: NasConnectionTester,
    private val tailscaleStatus: TailscaleStatus
) : ViewModel() {

    private val _state = MutableStateFlow(NasSettingsUiState())
    val state = _state.asStateFlow()

    init {
        val saved = store.load()
        val tailscale = tailscaleStatus.snapshot()
        _state.value = NasSettingsUiState(
            protocol = saved.protocol,
            host = saved.host,
            port = saved.port.takeIf { it > 0 }?.toString().orEmpty(),
            share = saved.share,
            path = saved.path,
            username = saved.username,
            password = saved.password,
            domain = saved.domain,
            useTls = saved.useTls,
            tailscaleInstalled = tailscale.installed,
            vpnActive = tailscale.vpnActive,
            tailnetAddress = tailscale.tailnetAddress,
            magicDnsHost = tailscaleStatus.isMagicDnsHost(saved.host),
            storageAvailable = store.isAvailable
        )
    }

    fun setProtocol(protocol: NasProtocol) = update { it.copy(protocol = protocol, testResult = null) }
    fun setHost(host: String) = update {
        it.copy(
            host = host,
            magicDnsHost = tailscaleStatus.isMagicDnsHost(host),
            testResult = null
        )
    }
    fun setPort(port: String) = update { it.copy(port = port.filter(Char::isDigit), testResult = null) }
    fun setShare(share: String) = update { it.copy(share = share, testResult = null) }
    fun setPath(path: String) = update { it.copy(path = path, testResult = null) }
    fun setUsername(username: String) = update { it.copy(username = username, testResult = null) }
    fun setPassword(password: String) = update { it.copy(password = password, testResult = null) }
    fun setDomain(domain: String) = update { it.copy(domain = domain, testResult = null) }
    fun setUseTls(useTls: Boolean) = update { it.copy(useTls = useTls, testResult = null) }

    fun refreshTailscale() = update {
        val tailscale = tailscaleStatus.snapshot()
        it.copy(
            tailscaleInstalled = tailscale.installed,
            vpnActive = tailscale.vpnActive,
            tailnetAddress = tailscale.tailnetAddress,
            magicDnsHost = tailscaleStatus.isMagicDnsHost(it.host)
        )
    }

    fun save() {
        val settings = state.value.toSettings()
        val error = validate(settings)
        if (error != null) {
            update { it.copy(validationError = error) }
            return
        }
        if (!store.isAvailable) {
            update { it.copy(validationError = "El almacenamiento cifrado no está disponible") }
            return
        }
        store.save(settings)
        update { it.copy(saved = true, validationError = null) }
    }

    fun test() {
        val settings = state.value.toSettings()
        val error = validate(settings)
        if (error != null) {
            update { it.copy(validationError = error) }
            return
        }
        if (state.value.testing) return
        val tailscaleError = tailscaleStatus.connectionError(settings.host)
        if (tailscaleError != null) {
            update {
                it.copy(
                    testing = false,
                    testResult = NasTestResult.Failure(tailscaleError),
                    validationError = null
                )
            }
            return
        }
        viewModelScope.launch {
            update { it.copy(testing = true, testResult = null, validationError = null) }
            val result = tester.test(settings)
            update { it.copy(testing = false, testResult = result) }
        }
    }

    fun clearMessages() = update { it.copy(saved = false, validationError = null, testResult = null) }

    fun openTailscale() = tailscaleStatus.openApp()

    private fun validate(settings: NasSettings): String? {
        if (settings.host.isBlank()) return "Escribe el host o la IP del NAS"
        if (settings.port != 0 && settings.port !in 1..65535) return "Puerto no válido"
        if (settings.protocol == NasProtocol.SMB && settings.share.isBlank()) {
            return "Escribe el recurso compartido SMB"
        }
        return null
    }

    private fun NasSettingsUiState.toSettings(): NasSettings {
        val rawHost = host.trim()
        val useHttpsScheme = rawHost.startsWith("https://", ignoreCase = true)
        val normalizedHost = rawHost
            .let {
                when {
                    it.startsWith("https://", ignoreCase = true) -> it.substring(8)
                    it.startsWith("http://", ignoreCase = true) -> it.substring(7)
                    else -> it
                }
            }
            .substringBefore('/')
        return NasSettings(
            protocol = protocol,
            host = normalizedHost,
            port = port.toIntOrNull() ?: 0,
            share = share.trim(),
            path = path.trim(),
            username = username,
            password = password,
            domain = domain.trim(),
            useTls = useTls || useHttpsScheme
        )
    }

    private fun update(block: (NasSettingsUiState) -> NasSettingsUiState) {
        _state.value = block(_state.value)
    }
}
