package com.ananthrajan.cameraengine

import android.os.Bundle
import com.ananthrajan.signaturecamerapro.LegacyCameraActivity

/**
 * Shared activity entry point for Signature Camera variants.
 * The current Pro capture implementation is retained in LegacyCameraActivity
 * while UI theme-specific composition is migrated into this engine boundary.
 */
abstract class BaseSignatureCameraActivity : LegacyCameraActivity() {
    abstract val themeStyle: CameraThemeStyle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Apply the selected camera-family system chrome after the legacy camera
        // screen has created its view hierarchy.
        when (themeStyle) {
            CameraThemeStyle.IPHONE_PRO -> {
                window.statusBarColor = android.graphics.Color.BLACK
                window.navigationBarColor = android.graphics.Color.BLACK
            }
            CameraThemeStyle.SAMSUNG_ONE_UI -> {
                window.statusBarColor = android.graphics.Color.BLACK
                window.navigationBarColor = android.graphics.Color.BLACK
            }
        }
    }
}

enum class CameraThemeStyle {
    IPHONE_PRO,
    SAMSUNG_ONE_UI
}
