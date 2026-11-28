package com.ananthrajan.cameraengine

/**
 * Selects the safe output path before any pixel processing is attempted.
 *
 * Vendor-managed formats must bypass Bitmap re-encoding: Ultra HDR carries a
 * gain map and OEM extensions may carry vendor-specific processing/metadata.
 * RAW/DNG is captured by the separate Camera2 pipeline and never enters here.
 */
internal enum class PhotoProcessingRoute {
    STANDARD_SDR,
    FULL_FRAME_SDR,
    ULTRA_HDR,
    OEM_EXTENSION
}

internal object PhotoProcessingPolicy {
    fun select(
        fullFrame: Boolean,
        ultraHdr: Boolean,
        oemExtension: Boolean
    ): PhotoProcessingRoute = when {
        oemExtension -> PhotoProcessingRoute.OEM_EXTENSION
        ultraHdr -> PhotoProcessingRoute.ULTRA_HDR
        fullFrame -> PhotoProcessingRoute.FULL_FRAME_SDR
        else -> PhotoProcessingRoute.STANDARD_SDR
    }
}
