package com.ananthrajan.cameraengine

import com.ananthrajan.signaturecamerapro.LegacyCameraActivity

/**
 * Shared activity entry point for Signature Camera variants.
 * The current Pro capture implementation is retained in LegacyCameraActivity
 * while UI theme-specific composition is migrated into this engine boundary.
 */
abstract class BaseSignatureCameraActivity : LegacyCameraActivity() {
    abstract val themeStyle: CameraThemeStyle
}

enum class CameraThemeStyle {
    IPHONE_PRO,
    SAMSUNG_ONE_UI
}
