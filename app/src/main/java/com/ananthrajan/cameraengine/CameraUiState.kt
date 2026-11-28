package com.ananthrajan.cameraengine

/**
 * Immutable snapshot of the camera controls currently selected by the UI.
 * Values only: no CameraX references, so the UI can be migrated independently.
 */
data class CameraUiState(
    val isVideoMode: Boolean = false,
    val zoomRatio: Float = 1f,
    val exposureCompensationIndex: Int = 0,
    val photoFlashMode: Int = 0,
    val torchEnabled: Boolean = false,
    val photoAspectRatio: PhotoAspectRatio = PhotoAspectRatio.RATIO_4_3,
    val videoAspectRatio: VideoAspectRatio = VideoAspectRatio.RATIO_16_9,
    val videoQuality: String = "FHD",
    val videoFps: Int = 30,
    val videoStabilizationEnabled: Boolean = false,
    val micCleanEnabled: Boolean = true,
    val jpegQuality: Int = 100,
    val ultraHdrEnabled: Boolean = false,
    val isRecording: Boolean = false
)

enum class PhotoAspectRatio {
    RATIO_4_3,
    RATIO_16_9,
    FULL
}

enum class VideoAspectRatio {
    RATIO_16_9,
    RATIO_4_3
}
