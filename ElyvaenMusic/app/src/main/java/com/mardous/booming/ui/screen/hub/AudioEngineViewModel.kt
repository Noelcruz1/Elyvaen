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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mardous.booming.core.model.dsp.DspBand
import com.mardous.booming.core.model.dsp.DspSettings
import com.mardous.booming.playback.dsp.DspManager
import com.mardous.booming.ui.screen.dap.Uac2Control
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AudioEngineUiState(
    val settings: DspSettings = DspSettings(),
    val uac2: Uac2Control.LinkStatus = Uac2Control.LinkStatus(),
    val importing: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

class AudioEngineViewModel(
    private val context: Context,
    private val manager: DspManager
) : ViewModel() {

    private val _state = MutableStateFlow(
        AudioEngineUiState(
            settings = manager.settings.value,
            uac2 = Uac2Control.refresh(context)
        )
    )
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            manager.settings.collect { settings ->
                _state.update { it.copy(settings = settings) }
            }
        }
    }

    fun refreshUac2() {
        _state.update { it.copy(uac2 = Uac2Control.refresh(context)) }
    }

    fun setNative(native: Boolean) {
        val success = Uac2Control.setQuality(context, native)
        if (!success) {
            _state.update {
                it.copy(error = "No se pudo cambiar el modo nativo del UAC2")
            }
        }
        refreshUac2()
    }

    fun setDspEnabled(enabled: Boolean) {
        manager.setEnabled(enabled)
    }

    fun updateBand(index: Int, transform: (DspBand) -> DspBand) {
        val band = _state.value.settings.bands.getOrNull(index) ?: return
        manager.setBand(index, transform(band))
    }

    fun setCrossfeed(enabled: Boolean, amount: Float) {
        manager.setCrossfeed(enabled, amount)
    }

    fun setConvolution(enabled: Boolean) {
        if (enabled && _state.value.settings.impulseName == null) {
            _state.update { it.copy(error = "Elige un archivo WAV de impulso primero") }
            return
        }
        manager.setConvolution(enabled)
    }

    fun importImpulse(uri: Uri) {
        if (_state.value.importing) return
        viewModelScope.launch {
            _state.update { it.copy(importing = true, error = null, message = null) }
            val result = withContext(Dispatchers.IO) { manager.importImpulse(uri) }
            result.onSuccess { name ->
                _state.update {
                    it.copy(importing = false, message = "Impulso cargado: $name")
                }
            }.onFailure { failure ->
                _state.update {
                    it.copy(
                        importing = false,
                        error = failure.message ?: "No se pudo cargar el impulso"
                    )
                }
            }
        }
    }

    fun clearImpulse() {
        manager.clearImpulse()
        _state.update { it.copy(message = "Impulso eliminado") }
    }

    fun resetEqualizer() {
        manager.update { current ->
            current.copy(bands = DspSettings.DEFAULT_BANDS)
        }
        _state.update { it.copy(message = "EQ paramétrico restablecido") }
    }

    fun dismissMessage() {
        _state.update { it.copy(message = null, error = null) }
    }
}
