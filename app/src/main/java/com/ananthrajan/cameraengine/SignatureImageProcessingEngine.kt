package com.ananthrajan.cameraengine

/**
 * Central planning layer for the Signature Camera Pro post-processing pipeline.
 *
 * This first version is deliberately a routing/architecture scaffold only:
 * it does not modify pixels and is not connected to the capture/save path.
 * Each processing stage will be implemented and validated independently
 * before this planner is integrated into production capture.
 */
internal object SignatureImageProcessingEngine {

    enum class Stage {
        NATURAL_COLOR,
        SMART_HDR,
        NIGHT_MULTI_FRAME,
        DETAIL_ENHANCEMENT,
        PORTRAIT_SEGMENTATION
    }

    enum class OutputPath {
        STANDARD_SDR_JPEG,
        FULL_FRAME_SDR_JPEG,
        ULTRA_HDR_PASSTHROUGH,
        OEM_EXTENSION_PASSTHROUGH,
        RAW_DNG_PASSTHROUGH
    }

    data class CaptureProfile(
        val cameraFacing: CameraFacing,
        val processingRoute: PhotoProcessingRoute,
        val iso: Int?,
        val isRawOrDng: Boolean = false,
        val hasMultipleFrames: Boolean = false,
        val hasDepthData: Boolean = false
    )

    enum class CameraFacing {
        FRONT,
        REAR,
        UNKNOWN
    }

    data class UserOptions(
        val naturalColorEnabled: Boolean = true,
        val smartHdrEnabled: Boolean = true,
        val nightMultiFrameEnabled: Boolean = true,
        val detailEnhancementEnabled: Boolean = true,
        val portraitEnabled: Boolean = false
    )

    data class ProcessingPlan(
        val outputPath: OutputPath,
        val stages: List<Stage>,
        val mayReencodeSdrJpeg: Boolean,
        val bypassReason: String? = null
    )

    /**
     * Produces a deterministic plan from capture metadata.
     *
     * Safety rules:
     * - RAW/DNG, Ultra HDR and OEM extensions are never sent through Bitmap
     *   processing or JPEG re-encoding.
     * - Multi-frame night processing is planned only when a burst actually
     *   exists; a single JPEG is never treated as a multi-frame input.
     * - Portrait segmentation is planned only when depth data is available.
     * - This method only plans work; it performs no image processing.
     */
    fun createPlan(
        capture: CaptureProfile,
        options: UserOptions = UserOptions()
    ): ProcessingPlan {
        if (capture.isRawOrDng) {
            return passthrough(OutputPath.RAW_DNG_PASSTHROUGH, "RAW/DNG must remain untouched")
        }

        when (capture.processingRoute) {
            PhotoProcessingRoute.ULTRA_HDR ->
                return passthrough(OutputPath.ULTRA_HDR_PASSTHROUGH, "Preserve Ultra HDR gain map")
            PhotoProcessingRoute.OEM_EXTENSION ->
                return passthrough(OutputPath.OEM_EXTENSION_PASSTHROUGH, "Preserve OEM extension output")
            PhotoProcessingRoute.FULL_FRAME_SDR -> {
                // Full-frame SDR can be processed later, but Stage 1 integration
                // must not silently change the already validated Phase 1 route.
            }
            PhotoProcessingRoute.STANDARD_SDR -> Unit
        }

        val outputPath = when (capture.processingRoute) {
            PhotoProcessingRoute.FULL_FRAME_SDR -> OutputPath.FULL_FRAME_SDR_JPEG
            else -> OutputPath.STANDARD_SDR_JPEG
        }

        val stages = buildList {
            if (options.naturalColorEnabled) add(Stage.NATURAL_COLOR)
            if (options.smartHdrEnabled) add(Stage.SMART_HDR)
            if (options.nightMultiFrameEnabled && capture.hasMultipleFrames) {
                add(Stage.NIGHT_MULTI_FRAME)
            }
            if (options.detailEnhancementEnabled) add(Stage.DETAIL_ENHANCEMENT)
            if (options.portraitEnabled && capture.hasDepthData) {
                add(Stage.PORTRAIT_SEGMENTATION)
            }
        }

        return ProcessingPlan(
            outputPath = outputPath,
            stages = stages,
            mayReencodeSdrJpeg = stages.isNotEmpty()
        )
    }

    private fun passthrough(path: OutputPath, reason: String) = ProcessingPlan(
        outputPath = path,
        stages = emptyList(),
        mayReencodeSdrJpeg = false,
        bypassReason = reason
    )
}
