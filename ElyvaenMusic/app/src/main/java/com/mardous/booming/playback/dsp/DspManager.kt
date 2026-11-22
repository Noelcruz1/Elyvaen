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

package com.mardous.booming.playback.dsp

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.mardous.booming.core.model.dsp.DspBand
import com.mardous.booming.core.model.dsp.DspSettings
import com.mardous.booming.data.local.DspSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Persists the DSP profile, loads the selected impulse response and pushes
 * both into the audio thread's processor.
 */
class DspManager(
    private val context: Context,
    private val processor: DspAudioProcessor
) {

    private val store = DspSettingsStore(context)
    private val _settings = MutableStateFlow(store.load())
    val settings = _settings.asStateFlow()

    private val impulseDirectory = File(context.filesDir, "dsp").apply { mkdirs() }
    private val impulseFile = File(impulseDirectory, "impulse.wav")

    init {
        val current = _settings.value
        processor.setSettings(current)
        if (current.impulseName != null && impulseFile.isFile) {
            runCatching {
                impulseFile.inputStream().use { WavImpulseLoader.load(it) }
            }.onSuccess { processor.setImpulse(it) }
        }
    }

    fun update(transform: (DspSettings) -> DspSettings) {
        val updated = transform(_settings.value).normalized()
        _settings.value = updated
        store.save(updated)
        processor.setSettings(updated)
    }

    fun setEnabled(enabled: Boolean) = update { it.copy(enabled = enabled) }

    fun setBand(index: Int, band: DspBand) = update { current ->
        val bands = current.bands.toMutableList()
        if (index in bands.indices) bands[index] = band
        current.copy(bands = bands)
    }

    fun setCrossfeed(enabled: Boolean, amount: Float) = update {
        it.copy(crossfeedEnabled = enabled, crossfeedAmount = amount)
    }

    fun setConvolution(enabled: Boolean) = update {
        it.copy(convolutionEnabled = enabled && impulseFile.isFile)
    }

    suspend fun importImpulse(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val displayName = queryDisplayName(uri) ?: "Impulso personalizado"
            val impulse = context.contentResolver.openInputStream(uri)?.use {
                WavImpulseLoader.load(it)
            } ?: throw IllegalStateException("No se pudo abrir el impulso")
            impulseDirectory.mkdirs()
            context.contentResolver.openInputStream(uri)?.use { input ->
                impulseFile.outputStream().use { output -> input.copyTo(output) }
            } ?: throw IllegalStateException("No se pudo copiar el impulso")
            processor.setImpulse(impulse)
            update {
                it.copy(
                    convolutionEnabled = true,
                    impulseName = displayName
                )
            }
            displayName
        }
    }

    fun clearImpulse() {
        impulseFile.delete()
        processor.setImpulse(null)
        update { it.copy(convolutionEnabled = false, impulseName = null) }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
    }
}
