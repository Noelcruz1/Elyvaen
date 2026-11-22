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

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class ImpulseResponse(
    val left: FloatArray,
    val right: FloatArray,
    val sampleRate: Int
) {
    val length: Int get() = left.size

    /**
     * Linear resampling to the active output rate and truncation to a
     * practical FIR length. The DAP can handle this without a dedicated DSP.
     */
    fun resample(targetRate: Int, maxTaps: Int): ImpulseResponse {
        if (sampleRate <= 0 || sampleRate == targetRate) {
            return copy(
                left = left.take(maxTaps).toFloatArray(),
                right = right.take(maxTaps).toFloatArray()
            )
        }
        val ratio = targetRate.toFloat() / sampleRate
        val outputLength = min(maxTaps, max(1, (left.size * ratio).toInt()))
        val outLeft = FloatArray(outputLength)
        val outRight = FloatArray(outputLength)
        for (index in 0 until outputLength) {
            val source = index / ratio
            val low = source.toInt().coerceIn(0, left.lastIndex)
            val high = (low + 1).coerceAtMost(left.lastIndex)
            val fraction = source - low
            outLeft[index] = left[low] + (left[high] - left[low]) * fraction
            outRight[index] = right[low] + (right[high] - right[low]) * fraction
        }
        return ImpulseResponse(outLeft, outRight, targetRate)
    }
}

object WavImpulseLoader {

    private const val MAX_FRAMES = 4096

    fun load(input: InputStream): ImpulseResponse {
        val bytes = input.readBytes()
        require(bytes.size >= 44) { "WAV demasiado pequeño" }
        require(String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF") { "No es un WAV RIFF" }
        require(String(bytes, 8, 4, Charsets.US_ASCII) == "WAVE") { "No es un WAV" }

        var format = 1
        var channels = 1
        var sampleRate = 44100
        var bits = 16
        var dataOffset = -1
        var dataSize = 0

        var offset = 12
        while (offset + 8 <= bytes.size) {
            val id = String(bytes, offset, 4, Charsets.US_ASCII)
            val size = ByteBuffer.wrap(bytes, offset + 4, 4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .int
            val body = offset + 8
            if (size < 0 || body + size > bytes.size) break
            when (id) {
                "fmt " -> {
                    require(size >= 16) { "fmt inválido" }
                    val buffer = ByteBuffer.wrap(bytes, body, size).order(ByteOrder.LITTLE_ENDIAN)
                    format = buffer.short.toInt() and 0xFFFF
                    channels = buffer.short.toInt() and 0xFFFF
                    sampleRate = buffer.int
                    buffer.int
                    buffer.short
                    bits = buffer.short.toInt() and 0xFFFF
                }

                "data" -> {
                    dataOffset = body
                    dataSize = min(size, bytes.size - body)
                }
            }
            offset = body + size + (size and 1)
        }

        require(dataOffset >= 0) { "El WAV no contiene datos" }
        require(channels in 1..2) { "Solo se admiten impulsos mono o estéreo" }
        require(bits in intArrayOf(8, 16, 24, 32)) { "Bits por muestra no soportados" }
        require(format == 1 || format == 3) { "Formato WAV no soportado" }
        if (format == 3) require(bits == 32) { "Float WAV debe ser 32-bit" }

        val bytesPerSample = bits / 8
        val frameBytes = bytesPerSample * channels
        val frames = min(MAX_FRAMES, dataSize / frameBytes)
        require(frames > 0) { "Impulso vacío" }

        val left = FloatArray(frames)
        val right = FloatArray(frames)
        val data = ByteBuffer.wrap(bytes, dataOffset, dataSize).order(ByteOrder.LITTLE_ENDIAN)
        for (frame in 0 until frames) {
            val l = readSample(data, format, bits)
            val r = if (channels == 2) readSample(data, format, bits) else l
            left[frame] = l
            right[frame] = r
        }

        var peak = 0f
        for (i in 0 until frames) {
            peak = max(peak, max(abs(left[i]), abs(right[i])))
        }
        if (peak > 0f) {
            val gain = 0.95f / peak
            for (i in 0 until frames) {
                left[i] *= gain
                right[i] *= gain
            }
        }
        return ImpulseResponse(left, right, sampleRate.coerceAtLeast(1))
    }

    private fun readSample(buffer: ByteBuffer, format: Int, bits: Int): Float {
        return when {
            format == 3 -> buffer.float
            bits == 8 -> ((buffer.get().toInt() and 0xFF) - 128) / 128f
            bits == 16 -> buffer.short / 32768f
            bits == 24 -> {
                val b0 = buffer.get().toInt() and 0xFF
                val b1 = buffer.get().toInt() and 0xFF
                val b2 = buffer.get().toInt()
                (((b2 shl 16) or (b1 shl 8) or b0)) / 8388608f
            }

            else -> buffer.int / 2147483648f
        }
    }
}
