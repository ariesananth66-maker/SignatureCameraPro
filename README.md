# Signature Camera Pro

Native Android camera application built with Kotlin and CameraX.

## Current app

- App ID: `com.ananthrajan.signaturecamerapro`
- Current source version: **1.2.0** (version code 21)
- Minimum Android version: Android 8.0 (API 26)
- Compile / target SDK: 36
- JDK: 17

## Camera features

- CameraX live preview with rear/front switching
- Still photo capture using CameraX maximum-quality mode and the highest available 4:3 resolution strategy
- Shared PreviewView viewport for preview and capture crop alignment, with a short readiness retry before binding
- Flash controls, front-camera screen flash, tap-to-focus, pinch zoom, and exposure compensation
- Pro controls for manual focus and other Camera2 capabilities where the device exposes them
- Optional RAW/DNG capture only when the selected camera advertises RAW support
- Optional manufacturer photo extensions (such as Night, HDR, or Bokeh) only when the device exposes them
- Ultra HDR JPEG output when CameraX and the selected camera report support; otherwise standard JPEG
- Hardware still-image noise-reduction, hot-pixel, shading, and aberration modes are requested only when advertised by the camera HAL
- Video recording through CameraX Recorder
- Optional conservative microphone cleanup for supported PCM 16-bit audio: low-frequency rumble filtering and a shallow downward expander with time-based attack/release tuned to preserve quieter speech

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

A GitHub Actions workflow also builds, audits package metadata, checks APK alignment and signature, calculates a SHA-256 checksum, and uploads a debug APK artifact. When release-signing secrets are configured, it additionally builds, verifies, and uploads a signed release APK.

## Release signing

The default CI artifact is a debug-signed APK. To enable a stable signed release APK, add these repository Actions secrets: `ANDROID_KEYSTORE_BASE64` (base64-encoded JKS/PKCS12 keystore), `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD`. Keep the keystore and passwords private; never commit them to the repository or paste them into chat. The same signing key must be retained for future app updates.

### Create the signing key on Windows

Run this in PowerShell on your own computer (with JDK 17 installed). Choose and safely store the passwords when prompted:

```powershell
keytool -genkeypair -v -keystore signature-camera-release.jks -alias signature-camera-pro -keyalg RSA -keysize 2048 -validity 10000
```

Keep `signature-camera-release.jks` in a safe, backed-up location. Do not upload it to the repository. Convert it to one line of Base64 for the GitHub secret:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("$PWD\signature-camera-release.jks")) | Set-Clipboard
```

In GitHub, open **Settings → Secrets and variables → Actions → New repository secret** and add:
- `ANDROID_KEYSTORE_BASE64`: paste the Base64 text from the clipboard
- `ANDROID_KEYSTORE_PASSWORD`: the keystore password you chose
- `ANDROID_KEY_ALIAS`: `signature-camera-pro`
- `ANDROID_KEY_PASSWORD`: the key password you chose (it may be the same as the keystore password)

Never share these values in chat. Back up the original keystore and passwords securely; if they are lost, future updates cannot be signed with the same identity. After adding the secrets, run **Actions → Android CI → Run workflow** to produce the signed release artifact.
