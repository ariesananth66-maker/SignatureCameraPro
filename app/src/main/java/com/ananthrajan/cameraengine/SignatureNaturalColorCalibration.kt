package com.ananthrajan.cameraengine

/**
 * Stage 1 natural-colour calibration gate.
 *
 * This file deliberately stores only measured calibration data. It contains no
 * default WB gains, Kelvin values, RGB offsets, or colour-correction matrices.
 * A profile must be built from matched captures and a neutral reference target
 * before a pixel-processing stage is allowed to consume it.
 *
 * This gate does not modify image pixels and is not yet connected to capture.
 */
internal object SignatureNaturalColorCalibration {

    enum class CameraFacing {
        FRONT,
        REAR
    }

    enum class Readiness {
        READY,
        BYPASS_PROFILE_MISSING,
        BYPASS_INVALID_PROFILE
    }

    /**
     * Camera2 values recorded from an actual capture result.
     *
     * The arrays are copied by the caller into immutable lists so the log/sample
     * can be retained without holding mutable Camera2 result objects.
     */
    data class CaptureSample(
        val sampleId: String,
        val cameraFacing: CameraFacing,
        val sensorTimestampNs: Long,
        val physicalCameraId: String?,
        val iso: Int?,
        val exposureTimeNs: Long?,
        val awbMode: Int?,
        val colorCorrectionMode: Int?,
        val awbGains: List<Float>?,
        val colorCorrectionTransform: List<Float>?
    )

    /**
     * A profile is accepted only when it has provenance and measured neutral
     * reference data. Matrices and gains are intentionally mandatory: no
     * guessed or identity defaults are silently substituted.
     *
     * Matrix convention and working colour space must be documented by the
     * calibration producer before any future pixel application is implemented.
     */
    data class MeasuredProfile(
        val profileId: String,
        val cameraFacing: CameraFacing,
        val referenceTargetId: String,
        val referenceIlluminantId: String,
        val sourceSampleIds: List<String>,
        val channelGains: List<Float>,
        val colorCorrectionMatrix3x3: List<Float>,
        val workingColorSpace: String,
        val measurementNotes: String
    )

    data class GateResult(
        val readiness: Readiness,
        val reason: String
    )

    /**
     * Checks data integrity only. It does not claim that a mathematically valid
     * profile is perceptually accurate; that still requires visual validation
     * against the original matched captures and neutral reference.
     */
    fun evaluate(profile: MeasuredProfile?): GateResult {
        if (profile == null) {
            return GateResult(
                Readiness.BYPASS_PROFILE_MISSING,
                "No measured per-camera colour profile is available"
            )
        }

        val valid = profile.profileId.isNotBlank() &&
            profile.referenceTargetId.isNotBlank() &&
            profile.referenceIlluminantId.isNotBlank() &&
            profile.sourceSampleIds.isNotEmpty() &&
            profile.sourceSampleIds.all { it.isNotBlank() } &&
            profile.channelGains.isNotEmpty() &&
            profile.channelGains.all { it.isFinite() && it > 0f } &&
            profile.colorCorrectionMatrix3x3.size == 9 &&
            profile.colorCorrectionMatrix3x3.all { it.isFinite() } &&
            profile.workingColorSpace.isNotBlank() &&
            profile.measurementNotes.isNotBlank()

        return if (valid) {
            GateResult(
                Readiness.READY,
                "Measured profile structure and provenance are present; image validation is still required"
            )
        } else {
            GateResult(
                Readiness.BYPASS_INVALID_PROFILE,
                "Profile is incomplete or contains invalid measured values"
            )
        }
    }
}
