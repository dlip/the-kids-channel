@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.thekidschannel.ui

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal class NormalizingRenderersFactory(
    context: Context,
    private val normalizeAudio: Boolean,
) : DefaultRenderersFactory(context) {
    init {
        setExtensionRendererMode(EXTENSION_RENDERER_MODE_ON)
    }

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean,
    ): AudioSink? {
        if (!normalizeAudio) {
            return super.buildAudioSink(
                context,
                enableFloatOutput,
                enableAudioTrackPlaybackParams,
            )
        }
        return DefaultAudioSink.Builder(context)
            .setAudioProcessors(arrayOf(AudioLevelingProcessor()))
            .setEnableFloatOutput(false)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .build()
    }
}

internal class AudioLevelingProcessor : BaseAudioProcessor() {
    private var currentGain = 1f

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat =
        if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) {
            inputAudioFormat
        } else {
            AudioFormat.NOT_SET
        }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val sampleCount = inputBuffer.remaining() / Short.SIZE_BYTES
        if (sampleCount == 0) return

        val rms = pcm16Rms(inputBuffer)
        val desiredGain = targetGainFor(rms)
        val smoothing = if (desiredGain < currentGain) {
            GAIN_REDUCTION_SPEED
        } else {
            GAIN_BOOST_SPEED
        }
        currentGain += (desiredGain - currentGain) * smoothing

        val outputBuffer = replaceOutputBuffer(inputBuffer.remaining())
        while (inputBuffer.remaining() >= Short.SIZE_BYTES) {
            val normalized = inputBuffer.short / Short.MAX_VALUE.toFloat()
            val limited = softLimit(normalized * currentGain)
            outputBuffer.putShort(
                (limited * Short.MAX_VALUE)
                    .roundToInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    .toShort(),
            )
        }
        outputBuffer.flip()
    }

    override fun onFlush() {
        currentGain = 1f
    }
}

internal fun pcm16Rms(buffer: ByteBuffer): Float {
    val samples = buffer.asReadOnlyBuffer().order(buffer.order())
    val sampleCount = samples.remaining() / Short.SIZE_BYTES
    if (sampleCount == 0) return 0f
    var squareSum = 0.0
    while (samples.remaining() >= Short.SIZE_BYTES) {
        val normalized = samples.short / Short.MAX_VALUE.toDouble()
        squareSum += normalized * normalized
    }
    return sqrt(squareSum / sampleCount).toFloat()
}

internal fun targetGainFor(rms: Float): Float {
    if (rms < SILENCE_RMS) return 1f
    return (TARGET_RMS / rms).coerceIn(MIN_GAIN, MAX_GAIN)
}

private fun softLimit(sample: Float): Float {
    val magnitude = abs(sample)
    if (magnitude <= LIMITER_START) return sample
    val limitedMagnitude = LIMITER_START +
        (LIMITER_CEILING - LIMITER_START) *
        (1f - 1f / (1f + (magnitude - LIMITER_START) / LIMITER_WIDTH))
    return if (sample < 0) -limitedMagnitude else limitedMagnitude
}

private const val TARGET_RMS = 0.18f
private const val SILENCE_RMS = 0.0005f
private const val MIN_GAIN = 0.1f
private const val MAX_GAIN = 20f
private const val GAIN_REDUCTION_SPEED = 0.25f
private const val GAIN_BOOST_SPEED = 0.04f
private const val LIMITER_START = 0.9f
private const val LIMITER_CEILING = 0.98f
private const val LIMITER_WIDTH = 0.08f
