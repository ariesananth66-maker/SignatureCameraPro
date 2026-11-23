package com.ananthrajan.cameraengine

import android.graphics.Bitmap
import kotlin.math.roundToInt

/**
 * Conservative SDR finishing pass.
 *
 * Applies only a very gentle luminance highlight shoulder. It deliberately
 * does not smooth skin, alter hue/white balance, or sharpen edges: those
 * operations need scene-aware validation and can easily damage real detail.
 */
internal object SignaturePhotoProcessor {
    private const val SHOULDER_START = 0.84f
    private const val MAX_HIGHLIGHT_COMPRESSION = 0.08f
    private const val ROW_CHUNK = 48

    fun applyNaturalHighlightRollOff(source: Bitmap): Bitmap {
        val output = if (source.isMutable && source.config == Bitmap.Config.ARGB_8888) {
            source
        } else {
            source.copy(Bitmap.Config.ARGB_8888, true)
        } ?: return source

        val width = output.width
        val height = output.height
        val pixels = IntArray(width * minOf(ROW_CHUNK, height))
        var top = 0
        while (top < height) {
            val rows = minOf(ROW_CHUNK, height - top)
            val count = width * rows
            output.getPixels(pixels, 0, width, 0, top, width, rows)
            for (index in 0 until count) {
                val pixel = pixels[index]
                val alpha = pixel ushr 24
                val red = (pixel ushr 16) and 0xFF
                val green = (pixel ushr 8) and 0xFF
                val blue = pixel and 0xFF
                val luminance = (0.2126f * red + 0.7152f * green + 0.0722f * blue) / 255f
                if (luminance > SHOULDER_START) {
                    val excess = (luminance - SHOULDER_START) / (1f - SHOULDER_START)
                    val scale = (SHOULDER_START + (luminance - SHOULDER_START) *
                        (1f - MAX_HIGHLIGHT_COMPRESSION * excess)) / luminance
                    val r = (red * scale).roundToInt().coerceIn(0, 255)
                    val g = (green * scale).roundToInt().coerceIn(0, 255)
                    val b = (blue * scale).roundToInt().coerceIn(0, 255)
                    pixels[index] = (alpha shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
            output.setPixels(pixels, 0, width, 0, top, width, rows)
            top += rows
        }
        return output
    }
}
