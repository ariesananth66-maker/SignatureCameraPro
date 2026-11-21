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
        val zoomState = camera.cameraInfo.zoomState.value ?: return null
        val safeRatio = ratio.coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio)
        uiState = uiState.copy(zoomRatio = safeRatio)
        return camera.cameraControl.setZoomRatio(safeRatio)
    }

    fun setExposureCompensation(index: Int): ListenableFuture<Int>? {
        val camera = activeCamera ?: return null
        val range = camera.cameraInfo.exposureState.exposureCompensationRange
        val safeIndex = index.coerceIn(range.lower, range.upper)
        uiState = uiState.copy(exposureCompensationIndex = safeIndex)
        return camera.cameraControl.setExposureCompensationIndex(safeIndex)
    }

    fun setTorch(enabled: Boolean): ListenableFuture<Void>? {
        val camera = activeCamera ?: return null
        uiState = uiState.copy(torchEnabled = enabled)
        return camera.cameraControl.enableTorch(enabled)
    }

    fun isAttached(): Boolean = activeCamera != null
}
