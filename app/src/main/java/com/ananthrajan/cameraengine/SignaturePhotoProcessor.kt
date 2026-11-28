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

    /**
     * Mild edge-aware chroma cleanup for high-ISO front STANDARD_SDR JPEGs.
     * Luma is carried through unchanged; only Cb/Cr are spatially averaged.
     */
    fun applyHighIsoFrontChromaDenoise(source: Bitmap): Bitmap {
        val working = if (source.isMutable) source else source.copy(Bitmap.Config.ARGB_8888, true) ?: return source
        val width = working.width
        val height = working.height
        val previousRow = IntArray(width)
        val currentRow = IntArray(width)
        val nextRow = IntArray(width)
        val outputRow = IntArray(width)

        working.getPixels(currentRow, 0, width, 0, 0, width, 1)
        currentRow.copyInto(previousRow)
        if (height > 1) working.getPixels(nextRow, 0, width, 0, 1, width, 1)
        else currentRow.copyInto(nextRow)

        var previous = previousRow
        var current = currentRow
        var next = nextRow

        for (y in 0 until height) {
            for (x in 0 until width) {
                val center = current[x]
                val alpha = center ushr 24
                val r0 = ((center ushr 16) and 0xFF).toFloat()
                val g0 = ((center ushr 8) and 0xFF).toFloat()
                val b0 = (center and 0xFF).toFloat()
                val luma = 0.299f * r0 + 0.587f * g0 + 0.114f * b0
                val cb0 = 128f - 0.168736f * r0 - 0.331264f * g0 + 0.5f * b0
                val cr0 = 128f + 0.5f * r0 - 0.418688f * g0 - 0.081312f * b0
                var cbSum = cb0
                var crSum = cr0
                var weightSum = 1f

                for (dy in -1..1) {
                    val ny = (y + dy).coerceIn(0, height - 1)
                    val neighborRow = when (ny) {
                        y - 1 -> previous
                        y -> current
                        else -> next
                    }
                    for (dx in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        val nx = (x + dx).coerceIn(0, width - 1)
                        val pixel = neighborRow[nx]
                        val nr = ((pixel ushr 16) and 0xFF).toFloat()
                        val ng = ((pixel ushr 8) and 0xFF).toFloat()
                        val nb = (pixel and 0xFF).toFloat()
                        val neighborY = 0.299f * nr + 0.587f * ng + 0.114f * nb
                        val neighborCb = 128f - 0.168736f * nr - 0.331264f * ng + 0.5f * nb
                        val neighborCr = 128f + 0.5f * nr - 0.418688f * ng - 0.081312f * nb
                        val spatial = if (dx == 0 || dy == 0) 0.55f else 0.35f
                        val lumaEdge = kotlin.math.abs(neighborY - luma)
                        val chromaDelta = kotlin.math.abs(neighborCb - cb0) + kotlin.math.abs(neighborCr - cr0)
                        val weight = spatial / (1f + lumaEdge * 0.04f + chromaDelta * 0.012f)
                        cbSum += neighborCb * weight
                        crSum += neighborCr * weight
                        weightSum += weight
                    }
                }

                val cb = cbSum / weightSum - 128f
                val cr = crSum / weightSum - 128f
                val outR = (luma + 1.402f * cr).roundToInt().coerceIn(0, 255)
                val outG = (luma - 0.344136f * cb - 0.714136f * cr).roundToInt().coerceIn(0, 255)
                val outB = (luma + 1.772f * cb).roundToInt().coerceIn(0, 255)
                outputRow[x] = (alpha shl 24) or (outR shl 16) or (outG shl 8) or outB
            }
            // Previous/current/next buffers preserve the untouched source rows
            // while the processed row is written back in place.
            working.setPixels(outputRow, 0, width, 0, y, width, 1)
            val recycledBuffer = previous
            previous = current
            current = next
            next = recycledBuffer
            if (y + 2 < height) working.getPixels(next, 0, width, 0, y + 2, width, 1)
        }
        return working
    }

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
