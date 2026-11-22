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

package com.mardous.booming.core.model.dsp

import kotlinx.serialization.Serializable

@Serializable
enum class DspBandType {
    LOW_SHELF,
    PEAK,
    HIGH_SHELF
}

@Serializable
data class DspBand(
    val frequencyHz: Float = 1000f,
    val gainDb: Float = 0f,
    val q: Float = 1f,
    val type: DspBandType = DspBandType.PEAK
)

@Serializable
data class DspSettings(
    val enabled: Boolean = false,
    val bands: List<DspBand> = DEFAULT_BANDS,
    val crossfeedEnabled: Boolean = false,
    val crossfeedAmount: Float = 0.25f,
    val convolutionEnabled: Boolean = false,
    val impulseName: String? = null
) {
    fun normalized(): DspSettings {
        val normalizedBands = DEFAULT_BANDS.mapIndexed { index, fallback ->
            bands.getOrNull(index)?.copy(
                frequencyHz = bands.getOrNull(index)!!.frequencyHz.coerceIn(20f, 20000f),
                gainDb = bands.getOrNull(index)!!.gainDb.coerceIn(-12f, 12f),
                q = bands.getOrNull(index)!!.q.coerceIn(0.3f, 6f)
            ) ?: fallback
        }
        return copy(
            bands = normalizedBands,
            crossfeedAmount = crossfeedAmount.coerceIn(0f, 1f)
        )
    }

    companion object {
        val DEFAULT_BANDS = listOf(
            DspBand(80f, 0f, 0.7f, DspBandType.LOW_SHELF),
            DspBand(250f, 0f, 1f, DspBandType.PEAK),
            DspBand(1000f, 0f, 1f, DspBandType.PEAK),
            DspBand(4000f, 0f, 1f, DspBandType.PEAK),
            DspBand(12000f, 0f, 0.7f, DspBandType.HIGH_SHELF)
        )
    }
}
