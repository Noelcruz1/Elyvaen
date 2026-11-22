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

package com.mardous.booming.playback.visualizer

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.mardous.booming.playback.processor.ByteUtils.getInt24
import com.mardous.booming.playback.processor.ByteUtils.putInt24
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

/**
 * Passthrough processor that exposes a small mono waveform for the UI. It
 * lives on the audio thread, where no Visualizer permission is needed.
 */
@OptIn(UnstableApi::class)
class AudioVisualizerProcessor(
    private val state: AudioVisualizerState
) : BaseAudioProcessor() {

    private var encoding = C.ENCODING_PCM_16BIT
    private var channels = 2
    private val ring = FloatArray(AudioVisualizerState.WAVEFORM_SIZE)
    private var ringIndex = 0
    private var lastUpdate = 0L

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_24BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT
        ) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        encoding = inputAudioFormat.encoding
        channels = inputAudioFormat.channelCount
        ring.fill(0f)
        ringIndex = 0
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        val output = replaceOutputBuffer(remaining)
        output.order(ByteOrder.LITTLE_ENDIAN)
        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        val bytesPerSample = when (encoding) {
            C.ENCODING_PCM_16BIT -> 2
            C.ENCODING_PCM_24BIT -> 3
            C.ENCODING_PCM_FLOAT -> 4
            else -> 0
        }
        val frameBytes = bytesPerSample * channels

        if (frameBytes <= 0 || channels !in 1..2) {
            output.put(inputBuffer)
            output.flip()
            return
        }

        while (inputBuffer.remaining() >= frameBytes) {
            val left = readSample(inputBuffer)
            val right = if (channels == 2) readSample(inputBuffer) else left
            writeSample(output, left)
            if (channels == 2) writeSample(output, right)
            ring[ringIndex] = (left + right) * 0.5f
            ringIndex = (ringIndex + 1) % ring.size
        }
        if (inputBuffer.hasRemaining()) {
            output.put(inputBuffer)
        }
        output.flip()

        val now = System.nanoTime()
        if (now - lastUpdate >= TimeUnit.MILLISECONDS.toNanos(33)) {
            lastUpdate = now
            state.submit(ring.copyOf())
        }
    }

    private fun readSample(buffer: ByteBuffer): Float = when (encoding) {
        C.ENCODING_PCM_16BIT -> buffer.short / 32768f
        C.ENCODING_PCM_24BIT -> buffer.getInt24() / 8388608f
        C.ENCODING_PCM_FLOAT -> buffer.float
        else -> 0f
    }

    private fun writeSample(buffer: ByteBuffer, value: Float) {
        val clamped = value.coerceIn(-1f, 1f)
        when (encoding) {
            C.ENCODING_PCM_16BIT ->
                buffer.putShort((clamped * 32767f).toInt().toShort())

            C.ENCODING_PCM_24BIT ->
                buffer.putInt24((clamped * 8388607f).toInt())

            C.ENCODING_PCM_FLOAT ->
                buffer.putFloat(clamped)
        }
    }
}
