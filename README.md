# Signature Camera Pro

Native Android camera application built with Kotlin and CameraX.

## Current app

- App ID: `com.ananthrajan.signaturecamerapro`
- Current source version: **1.1.8** (version code 19)
- Minimum Android version: Android 8.0 (API 26)
- Compile / target SDK: 36
- JDK: 17

## Camera features

- CameraX live preview with rear/front switching
- Still photo capture using CameraX maximum-quality mode and the highest available 4:3 resolution strategy
- Shared PreviewView viewport for preview and capture crop alignment when the view has a valid viewport
- Flash controls, front-camera screen flash, tap-to-focus, pinch zoom, and exposure compensation
- Pro controls for manual focus and other Camera2 capabilities where the device exposes them
- Optional RAW/DNG capture only when the selected camera advertises RAW support
- Optional manufacturer photo extensions (such as Night, HDR, or Bokeh) only when the device exposes them
- Ultra HDR JPEG output when CameraX and the selected camera report support; otherwise standard JPEG
- Hardware still-image noise-reduction, hot-pixel, shading, and aberration modes are requested only when advertised by the camera HAL
- Video recording through CameraX Recorder
- Optional conservative microphone cleanup for supported PCM 16-bit audio: low-frequency rumble filtering and a shallow downward expander tuned to preserve quieter speech

## Important limitations

- Camera quality depends on the phone's camera hardware, firmware, Camera2 HAL, lighting, and supported CameraX capabilities.
- The app does **not** implement an AI denoiser or a custom multi-frame Night/HDR computational photography pipeline.
- Microphone cleanup is not full broadband or AI noise cancellation; compare recordings with it enabled and disabled, especially for quiet speech and outdoor wind.
- Shared viewport binding is intended to reduce preview/photo framing differences, but the result must be checked on the target phone.
- Feature availability can differ between lenses and between front and rear cameras.

## Build locally

Requirements:
- Android Studio with Android SDK 36
- JDK 17
- Gradle wrapper from this repository

Build the debug APK:

```bash
./gradlew assembleDebug
```

Output:

```
app/build/outputs/apk/debug/app-debug.apk
```

A GitHub Actions workflow also builds, audits package metadata, checks APK alignment and signature, calculates a SHA-256 checksum, and uploads a debug APK artifact.

## Release signing

The CI artifact is a debug-signed APK, not a Play Store release. A stable release signing key must be configured securely before producing an installable release build intended for long-term updates. Never commit a keystore or signing passwords to the repository.
