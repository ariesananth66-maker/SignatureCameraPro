# Signature Camera Pro

A native Android camera app built with Kotlin and CameraX.

## Current starter features

- Live CameraX preview
- Rear/front camera switching
- Photo capture at CameraX maximum-quality capture mode
- Saves JPEG photos to the device Gallery
- Flash on/off control
- Runtime camera permission handling

## Requirements

- Android Studio with Android SDK 36
- JDK 17
- Android device running Android 8.0 (API 26) or later

The app is configured with compile/target SDK 36 and can run on Android 17 devices. It does not yet use Android 17-only APIs.

## Build

Open this repository in Android Studio, allow Gradle sync, then run the `:app` configuration on a physical Android device. A physical device is recommended because camera hardware is required.

You can also build a debug APK with Gradle 8.13:

```bash
gradle assembleDebug
```

The APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

## Roadmap

Manual focus, ISO, shutter speed, exposure compensation, RAW capture, zoom controls, and a settings screen are planned. Availability depends on the camera hardware and Android Camera2 capabilities; these features are not implemented in the starter build yet.
