package com.ariesanth.signaturecamerapro

import android.media.AudioFormat
import androidx.camera.video.AudioProcessor
import androidx.camera.video.ExperimentalAudioApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sign

/**
 * Applies a modest digital lift to quiet camera audio before AAC encoding.
 *
 * This does not change microphone hardware sensitivity. It raises lower-level PCM samples
 * while smoothly compressing peaks to reduce hard clipping.
 */
@OptIn(ExperimentalAudioApi::class)
internal class VideoAudioGainProcessor : AudioProcessor {
    private var configured = false
    private var outputBuffer: ByteBuffer = emptyBuffer()

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        require(inputAudioFormat.encoding == AudioFormat.ENCODING_PCM_16BIT) {
            "VideoAudioGainProcessor requires 16-bit PCM input; received encoding=${inputAudioFormat.encoding}"
        }
        configured = true
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        check(configured) { "Audio processor is not configured" }
        inputBuffer.order(ByteOrder.nativeOrder())
        val byteCount = inputBuffer.remaining()
        require(byteCount % 2 == 0) { "PCM16 input must contain complete samples" }

        val processed = ByteBuffer.allocateDirect(byteCount).order(ByteOrder.nativeOrder())
        while (inputBuffer.remaining() >= 2) {
            val sample = inputBuffer.short.toInt()
            val normalized = sample / 32768.0
            val amplified = normalized * INPUT_GAIN
            val magnitude = abs(amplified)
            val limited = if (magnitude <= LIMIT_KNEE) {
                amplified
            } else {
                sign(amplified) * (
                    LIMIT_KNEE + LIMIT_RANGE *
                        (1.0 - exp(-(magnitude - LIMIT_KNEE) / LIMIT_RANGE))
                    )
            }
            val pcm = (limited * 32768.0).toInt().coerceIn(-32768, 32767)
            processed.putShort(pcm.toShort())
        }
        processed.flip()
        outputBuffer = processed
    }

    override fun getOutput(): ByteBuffer {
        val result = outputBuffer
        outputBuffer = emptyBuffer()
        return result
    }

    override fun flush() {
        outputBuffer = emptyBuffer()
    }

    override fun reset() {
        outputBuffer = emptyBuffer()
        configured = false
    }

    private fun emptyBuffer(): ByteBuffer =
        ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())

    private companion object {
        // About +5 dB for quieter sounds; peaks are smoothly limited below full scale.
        const val INPUT_GAIN = 1.8
        const val LIMIT_KNEE = 0.78
        const val LIMIT_RANGE = 0.20
    }
}
