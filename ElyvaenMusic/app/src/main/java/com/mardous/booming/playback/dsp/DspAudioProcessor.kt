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

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.mardous.booming.core.model.dsp.DspBand
import com.mardous.booming.core.model.dsp.DspBandType
import com.mardous.booming.core.model.dsp.DspSettings
import com.mardous.booming.playback.processor.ByteUtils.getInt24
import com.mardous.booming.playback.processor.ByteUtils.putInt24
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Native 16/24-bit and float DSP stage: five parametric bands, a small
 * crossfeed network and an optional 512-tap FIR convolution.
 */
@OptIn(UnstableApi::class)
class DspAudioProcessor : BaseAudioProcessor() {

    @Volatile
    private var settings = DspSettings()

    @Volatile
    private var impulse: ImpulseResponse? = null

    @Volatile
    private var filtersDirty = true

    private var sampleRate = 44100
    private var channelCount = 2
    private var encoding = C.ENCODING_PCM_16BIT

    private val bands = Array(DspSettings.DEFAULT_BANDS.size) {
        arrayOf(Biquad(), Biquad())
    }
    private val crossfeed = Crossfeed()
    private val convolver = Convolver()

    @Synchronized
    fun setSettings(newSettings: DspSettings) {
        settings = newSettings.normalized()
        filtersDirty = true
    }

    @Synchronized
    fun setImpulse(newImpulse: ImpulseResponse?) {
        impulse = newImpulse
        filtersDirty = true
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_24BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT
        ) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        if (inputAudioFormat.channelCount !in 1..2) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        encoding = inputAudioFormat.encoding
        filtersDirty = true
        return inputAudioFormat
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        bands.forEach { pair -> pair.forEach { it.reset() } }
        crossfeed.reset()
        convolver.reset()
        filtersDirty = true
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        val current = settings
        val convolution = if (current.convolutionEnabled) impulse else null
        val needsDsp = current.enabled || current.crossfeedEnabled ||
                convolution != null
        val bytesPerSample = bytesPerSample(encoding)
        val frameBytes = bytesPerSample * channelCount

        if (!needsDsp || frameBytes <= 0) {
            val buffer = replaceOutputBuffer(remaining)
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            buffer.put(inputBuffer)
            buffer.flip()
            return
        }

        if (filtersDirty) {
            configureFilters(current, convolution)
            filtersDirty = false
        }

        val output = replaceOutputBuffer(remaining)
        output.order(ByteOrder.LITTLE_ENDIAN)
        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)

        while (inputBuffer.remaining() >= frameBytes) {
            var left = readSample(inputBuffer, encoding)
            var right = if (channelCount == 2) readSample(inputBuffer, encoding) else left

            if (current.enabled) {
                for (bandIndex in bands.indices) {
                    left = bands[bandIndex][0].process(left)
                    if (channelCount == 2) {
                        right = bands[bandIndex][1].process(right)
                    }
                }
            }
            if (channelCount == 2 && current.crossfeedEnabled) {
                crossfeed.process(left, right, current.crossfeedAmount)
                left = crossfeed.outLeft
                right = crossfeed.outRight
            }
            if (convolution != null) {
                if (channelCount == 2) {
                    convolver.processStereo(left, right)
                    left = convolver.outLeft
                    right = convolver.outRight
                } else {
                    left = convolver.processMono(left)
                    right = left
                }
            }

            writeSample(output, encoding, left)
            if (channelCount == 2) writeSample(output, encoding, right)
        }
        if (inputBuffer.hasRemaining()) {
            output.put(inputBuffer)
        }
        output.flip()
    }

    private fun configureFilters(current: DspSettings, convolution: ImpulseResponse?) {
        current.bands.forEachIndexed { index, band ->
            bands[index][0].configure(band, sampleRate)
            bands[index][1].configure(band, sampleRate)
        }
        crossfeed.configure(sampleRate)
        convolver.configure(
            impulse = convolution?.resample(sampleRate, MAX_TAPS),
            sampleRate = sampleRate
        )
    }

    private fun bytesPerSample(encoding: Int): Int = when (encoding) {
        C.ENCODING_PCM_16BIT -> 2
        C.ENCODING_PCM_24BIT -> 3
        C.ENCODING_PCM_FLOAT -> 4
        else -> 0
    }

    private fun readSample(buffer: ByteBuffer, encoding: Int): Float = when (encoding) {
        C.ENCODING_PCM_16BIT -> buffer.short / 32768f
        C.ENCODING_PCM_24BIT -> buffer.getInt24() / 8388608f
        C.ENCODING_PCM_FLOAT -> buffer.float
        else -> 0f
    }

    private fun writeSample(buffer: ByteBuffer, encoding: Int, value: Float) {
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

    private class Biquad {
        private var b0 = 1f
        private var b1 = 0f
        private var b2 = 0f
        private var a1 = 0f
        private var a2 = 0f
        private var x1 = 0f
        private var x2 = 0f
        private var y1 = 0f
        private var y2 = 0f

        fun configure(band: DspBand, sampleRate: Int) {
            val frequency = band.frequencyHz.coerceIn(20f, sampleRate * 0.49f)
            val q = band.q.coerceIn(0.3f, 6f)
            val a = 10.0.pow((band.gainDb / 40.0)).toFloat()
            val w0 = 2.0 * PI * frequency / sampleRate
            val cosW = cos(w0).toFloat()
            val sinW = sin(w0).toFloat()
            val alpha = sinW / (2f * q)

            when (band.type) {
                DspBandType.PEAK -> {
                    val a0 = 1f + alpha / a
                    b0 = (1f + alpha * a) / a0
                    b1 = (-2f * cosW) / a0
                    b2 = (1f - alpha * a) / a0
                    a1 = (-2f * cosW) / a0
                    a2 = (1f - alpha / a) / a0
                }

                DspBandType.LOW_SHELF -> {
                    val sqrtA = sqrt(a)
                    val alphaS = sinW / 2f * sqrt(2f)
                    val a0 = (a + 1f) + (a - 1f) * cosW + 2f * sqrtA * alphaS
                    b0 = a * ((a + 1f) - (a - 1f) * cosW + 2f * sqrtA * alphaS) / a0
                    b1 = 2f * a * ((a - 1f) - (a + 1f) * cosW) / a0
                    b2 = a * ((a + 1f) - (a - 1f) * cosW - 2f * sqrtA * alphaS) / a0
                    a1 = -2f * ((a - 1f) + (a + 1f) * cosW) / a0
                    a2 = ((a + 1f) + (a - 1f) * cosW - 2f * sqrtA * alphaS) / a0
                }

                DspBandType.HIGH_SHELF -> {
                    val sqrtA = sqrt(a)
                    val alphaS = sinW / 2f * sqrt(2f)
                    val a0 = (a + 1f) - (a - 1f) * cosW + 2f * sqrtA * alphaS
                    b0 = a * ((a + 1f) + (a - 1f) * cosW + 2f * sqrtA * alphaS) / a0
                    b1 = -2f * a * ((a - 1f) + (a + 1f) * cosW) / a0
                    b2 = a * ((a + 1f) + (a - 1f) * cosW - 2f * sqrtA * alphaS) / a0
                    a1 = 2f * ((a - 1f) - (a + 1f) * cosW) / a0
                    a2 = ((a + 1f) - (a - 1f) * cosW - 2f * sqrtA * alphaS) / a0
                }
            }
        }

        fun process(input: Float): Float {
            val output = b0 * input + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1
            x1 = input
            y2 = y1
            y1 = output
            return output
        }

        fun reset() {
            x1 = 0f
            x2 = 0f
            y1 = 0f
            y2 = 0f
        }
    }

    private class Crossfeed {
        private var delayLeft = FloatArray(1)
        private var delayRight = FloatArray(1)
        private var index = 0
        private var lowLeft = 0f
        private var lowRight = 0f
        private var alpha = 0f
        private var delaySamples = 0

        var outLeft = 0f
            private set
        var outRight = 0f
            private set

        fun configure(sampleRate: Int) {
            delaySamples = (sampleRate * 0.0003f).toInt().coerceIn(1, 32)
            delayLeft = FloatArray(delaySamples + 1)
            delayRight = FloatArray(delaySamples + 1)
            index = 0
            lowLeft = 0f
            lowRight = 0f
            alpha = 1f - exp((-2.0 * PI * 700.0 / sampleRate).toFloat())
        }

        fun process(left: Float, right: Float, amount: Float) {
            val size = delayLeft.size
            val read = (index - delaySamples + size) % size
            val delayedLeft = delayLeft[read]
            val delayedRight = delayRight[read]
            lowLeft += alpha * (delayedLeft - lowLeft)
            lowRight += alpha * (delayedRight - lowRight)
            val gain = amount.coerceIn(0f, 1f)
            outLeft = left + gain * lowRight
            outRight = right + gain * lowLeft
            delayLeft[index] = left
            delayRight[index] = right
            index = (index + 1) % size
        }

        fun reset() {
            delayLeft.fill(0f)
            delayRight.fill(0f)
            index = 0
            lowLeft = 0f
            lowRight = 0f
        }
    }

    private class Convolver {
        private var left = FloatArray(0)
        private var right = FloatArray(0)
        private var historyLeft = FloatArray(1)
        private var historyRight = FloatArray(1)
        private var index = 0

        var outLeft = 0f
            private set
        var outRight = 0f
            private set

        fun configure(impulse: ImpulseResponse?, sampleRate: Int) {
            left = impulse?.left?.take(MAX_TAPS)?.toFloatArray() ?: FloatArray(0)
            right = impulse?.right?.take(MAX_TAPS)?.toFloatArray() ?: FloatArray(0)
            historyLeft = FloatArray(left.size.coerceAtLeast(1))
            historyRight = FloatArray(right.size.coerceAtLeast(1))
            index = 0
        }

        fun processStereo(inputLeft: Float, inputRight: Float) {
            if (left.isEmpty()) {
                outLeft = inputLeft
                outRight = inputRight
                return
            }
            historyLeft[index] = inputLeft
            historyRight[index] = inputRight
            var leftSum = 0f
            var rightSum = 0f
            var read = index
            for (tap in left.indices) {
                leftSum += left[tap] * historyLeft[read]
                rightSum += right[tap] * historyRight[read]
                read = if (read == 0) historyLeft.lastIndex else read - 1
            }
            outLeft = leftSum
            outRight = rightSum
            index = (index + 1) % historyLeft.size
        }

        fun processMono(input: Float): Float {
            if (left.isEmpty()) return input
            historyLeft[index] = input
            var sum = 0f
            var read = index
            for (tap in left.indices) {
                sum += left[tap] * historyLeft[read]
                read = if (read == 0) historyLeft.lastIndex else read - 1
            }
            index = (index + 1) % historyLeft.size
            return sum
        }

        fun reset() {
            historyLeft.fill(0f)
            historyRight.fill(0f)
            index = 0
        }
    }

    companion object {
        private const val MAX_TAPS = 512
    }
}
