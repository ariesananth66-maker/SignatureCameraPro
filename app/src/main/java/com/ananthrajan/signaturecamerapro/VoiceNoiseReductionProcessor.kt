package com.ananthrajan.signaturecamerapro

import android.media.AudioFormat as PlatformAudioFormat
import androidx.camera.video.AudioProcessor
import androidx.camera.video.ExperimentalAudioApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Conservative real-time voice cleanup for CameraX video audio.
 *
 * This is deliberately not an aggressive spectral denoiser: it removes low-frequency
 * handling/room rumble and gently attenuates the noise floor between spoken phrases,
 * while keeping the original sample rate, channel count, timing and PCM bit depth.
 */
@OptIn(ExperimentalAudioApi::class)
internal class VoiceNoiseReductionProcessor : AudioProcessor {
    private var sampleRate = 44_100
    private var channelCount = 1
    private var highPassAlpha = 0.99f
    private var previousInput = FloatArray(1)
    private var previousOutput = FloatArray(1)
    private var smoothedGain = 1f
    private var processingEnabled = false

    private var outputBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var outputReady = false

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        // CameraX audio must never be lost if an OEM supplies a different PCM encoding.
        // In that case, keep the original stream untouched and preserve recording audio.
        processingEnabled = inputAudioFormat.encoding == PlatformAudioFormat.ENCODING_PCM_16BIT
        if (!processingEnabled) {
            outputReady = false
            outputBuffer = AudioProcessor.EMPTY_BUFFER
            return inputAudioFormat
        }
        sampleRate = inputAudioFormat.sampleRate.coerceAtLeast(8_000)
        channelCount = inputAudioFormat.channelCount.coerceAtLeast(1)
        highPassAlpha = exp((-2.0 * PI * 85.0 / sampleRate).toFloat()).coerceIn(0f, 1f)
        previousInput = FloatArray(channelCount)
        previousOutput = FloatArray(channelCount)
        smoothedGain = 1f
        outputReady = false
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!processingEnabled) {
            outputBuffer = inputBuffer
            outputReady = inputBuffer.hasRemaining()
            return
        }
        val input = inputBuffer.asReadOnlyBuffer().order(ByteOrder.nativeOrder())
        val sampleCount = input.remaining() / 2
        if (sampleCount <= 0) {
            inputBuffer.position(inputBuffer.limit())
            outputReady = false
            outputBuffer = AudioProcessor.EMPTY_BUFFER
            return
        }

        val filteredSamples = FloatArray(sampleCount)
        var energy = 0.0
        for (i in 0 until sampleCount) {
            val raw = input.short.toFloat() / 32768f
            val channel = i % channelCount
            // One-pole high-pass filter: reduce handling, vibration and low wind rumble.
            val filtered = highPassAlpha * (previousOutput[channel] + raw - previousInput[channel])
            previousInput[channel] = raw
            previousOutput[channel] = filtered
            filteredSamples[i] = filtered
            energy += filtered * filtered
        }
        inputBuffer.position(inputBuffer.limit())

        // A gentle downward expander lowers quiet background noise between phrases.
        // The low threshold avoids clamping normal speech or applying artificial gain.
        val rms = sqrt(energy / sampleCount)
        val quietFloor = 0.0006
        val openLevel = 0.0032
        val blend = ((rms - quietFloor) / (openLevel - quietFloor)).coerceIn(0.0, 1.0)
        val targetGain = (0.18 + 0.82 * blend).toFloat()
        val smoothing = if (targetGain > smoothedGain) 0.62f else 0.10f
        smoothedGain += (targetGain - smoothedGain) * smoothing

        val outputBytes = sampleCount * 2
        if (outputBuffer.capacity() < outputBytes) {
            outputBuffer = ByteBuffer.allocateDirect(outputBytes).order(ByteOrder.nativeOrder())
        }
        outputBuffer.clear()
        for (sample in filteredSamples) {
            val cleaned = (sample * smoothedGain * 32767f).roundToInt().coerceIn(-32768, 32767)
            outputBuffer.putShort(cleaned.toShort())
        }
        outputBuffer.flip()
        outputReady = true
    }

    override fun getOutput(): ByteBuffer {
        if (!outputReady) return AudioProcessor.EMPTY_BUFFER
        outputReady = false
        return outputBuffer
    }

    override fun flush() {
        outputReady = false
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        previousInput.fill(0f)
        previousOutput.fill(0f)
        smoothedGain = 1f
    }

    override fun reset() {
        flush()
        processingEnabled = false
        sampleRate = 44_100
        channelCount = 1
        highPassAlpha = 0.99f
        previousInput = FloatArray(1)
        previousOutput = FloatArray(1)
    }
}
