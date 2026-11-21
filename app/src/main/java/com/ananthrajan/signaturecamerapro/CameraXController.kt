package com.ananthrajan.signaturecamerapro

import androidx.camera.core.Camera
import com.google.common.util.concurrent.ListenableFuture

/**
 * Thin CameraX control bridge for the existing MainActivity binding flow.
 * It deliberately does not bind/unbind use cases or own capture workflows.
 */
class CameraXController {
    private var activeCamera: Camera? = null

    var uiState: CameraUiState = CameraUiState()
        private set

    fun attach(camera: Camera) {
        activeCamera = camera
    }

    fun detach() {
        activeCamera = null
    }

    fun updateState(state: CameraUiState) {
        uiState = state
    }

    fun setZoomRatio(ratio: Float): ListenableFuture<Void>? {
        val camera = activeCamera ?: return null
        if (uiState.isRecording) return null
        val zoomState = camera.cameraInfo.zoomState.value ?: return null
        return camera.cameraControl.setZoomRatio(
            ratio.coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio)
        )
    }

    fun setExposureCompensation(index: Int): ListenableFuture<Void>? {
        val camera = activeCamera ?: return null
        if (uiState.isRecording) return null
        val range = camera.cameraInfo.exposureState.exposureCompensationRange
        return camera.cameraControl.setExposureCompensationIndex(
            index.coerceIn(range.lower, range.upper)
        )
    }

    fun setTorch(enabled: Boolean): ListenableFuture<Void>? {
        val camera = activeCamera ?: return null
        if (uiState.isRecording) return null
        return camera.cameraControl.enableTorch(enabled)
    }

    fun isAttached(): Boolean = activeCamera != null
}
