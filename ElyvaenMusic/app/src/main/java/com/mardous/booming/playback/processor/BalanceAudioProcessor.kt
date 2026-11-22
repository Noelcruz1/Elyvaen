package com.mardous.booming.playback.processor

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.mardous.booming.playback.processor.ByteUtils.getInt24
import com.mardous.booming.playback.processor.ByteUtils.putInt24
import java.nio.ByteBuffer
import java.nio.ByteOrder

@OptIn(UnstableApi::class)
class BalanceAudioProcessor(
    private var leftGain: Float = 1.0f,
    private var rightGain: Float = 1.0f
) : BaseAudioProcessor() {

    @Synchronized
    fun setBalance(left: Float, right: Float) {
        leftGain = left
        rightGain = right
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_24BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT
        ) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val buffer = replaceOutputBuffer(remaining)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT -> {
                if (inputAudioFormat.channelCount == 2) {
                    while (inputBuffer.remaining() >= 4) {
                        val left = inputBuffer.short
                        val right = inputBuffer.short
                        buffer.putShort(
                            (left * leftGain).toInt()
                                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                                .toShort()
                        )
                        buffer.putShort(
                            (right * rightGain).toInt()
                                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                                .toShort()
                        )
                    }
                } else if (inputAudioFormat.channelCount == 1) {
                    val gain = (leftGain + rightGain) / 2f
                    while (inputBuffer.remaining() >= 2) {
                        val sample = inputBuffer.short
                        buffer.putShort(
                            (sample * gain).toInt()
                                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                                .toShort()
                        )
                    }
                }
            }

            C.ENCODING_PCM_24BIT -> {
                if (inputAudioFormat.channelCount == 2) {
                    while (inputBuffer.remaining() >= 6) {
                        val left = inputBuffer.getInt24()
                        val right = inputBuffer.getInt24()
                        buffer.putInt24(
                            (left * leftGain).toInt()
                                .coerceIn(ByteUtils.Int24_MIN_VALUE, ByteUtils.Int24_MAX_VALUE)
                        )
                        buffer.putInt24(
                            (right * rightGain).toInt()
                                .coerceIn(ByteUtils.Int24_MIN_VALUE, ByteUtils.Int24_MAX_VALUE)
                        )
                    }
                } else if (inputAudioFormat.channelCount == 1) {
                    val gain = (leftGain + rightGain) / 2f
                    while (inputBuffer.remaining() >= 3) {
                        buffer.putInt24(
                            (inputBuffer.getInt24() * gain).toInt()
                                .coerceIn(ByteUtils.Int24_MIN_VALUE, ByteUtils.Int24_MAX_VALUE)
                        )
                    }
                }
            }

            C.ENCODING_PCM_FLOAT -> {
                if (inputAudioFormat.channelCount == 2) {
                    while (inputBuffer.remaining() >= 8) {
                        buffer.putFloat(inputBuffer.float * leftGain)
                        buffer.putFloat(inputBuffer.float * rightGain)
                    }
                } else if (inputAudioFormat.channelCount == 1) {
                    val gain = (leftGain + rightGain) / 2f
                    while (inputBuffer.remaining() >= 4) {
                        buffer.putFloat(inputBuffer.float * gain)
                    }
                }
            }
        }

        if (inputBuffer.hasRemaining()) {
            buffer.put(inputBuffer)
        }

        buffer.flip()
    }
}
