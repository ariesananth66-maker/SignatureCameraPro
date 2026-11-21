package com.ananthrajan.signaturecamerapro

import android.Manifest
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.DngCreator
import android.media.Image
import android.media.ImageReader
import java.io.File
import android.net.Uri
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import android.hardware.camera2.CameraManager
import android.graphics.ImageFormat
import android.content.ContentValues
import android.content.Intent
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.graphics.drawable.GradientDrawable
import androidx.activity.ComponentActivity
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.ExperimentalAudioApi
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import android.util.Range
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.MeteringPoint
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.pow

class MainActivity : ComponentActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var gridView: GridOverlay
    private lateinit var focusOverlay: FocusOverlay
    private lateinit var zoomLabel: TextView
    private lateinit var zoomSlider: SeekBar
    private lateinit var audioLevelLabel: TextView
    private lateinit var recordingTimer: android.widget.Chronometer
    private lateinit var audioLevelBar: android.widget.ProgressBar
    private lateinit var exposureLabel: TextView
    private lateinit var lensRow: LinearLayout
    private lateinit var proPanel: LinearLayout
    private lateinit var isoValue: TextView
    private lateinit var shutterValue: TextView
    private lateinit var isoSlider: SeekBar
    private lateinit var shutterSlider: SeekBar
    private lateinit var focusValue: TextView
    private lateinit var focusSlider: SeekBar
    private lateinit var whiteBalanceButton: Button
    private lateinit var sceneModeButton: Button
    private lateinit var photoExtensionButton: Button
    private lateinit var photoModeButton: Button
    private lateinit var videoModeButton: Button
    private lateinit var videoQualityButton: Button
    private lateinit var videoFpsButton: Button
    private lateinit var videoStabilizationButton: Button
    private lateinit var micNoiseReductionButton: Button
    private lateinit var captureButton: Button
    private lateinit var flashButton: Button
    private lateinit var flashStatusLabel: TextView
    private lateinit var screenFlashOverlay: View
    private var screenFlashHideRunnable: Runnable? = null
    private var screenFlashEnabled = true
    private var photoFlashMode = ImageCapture.FLASH_MODE_OFF
    private var torchEnabled = false
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var micPeakAmplitude = -120.0
    private var micAmplitudeTotal = 0.0
    private var micAmplitudeSamples = 0
    private var micAudioState = androidx.camera.video.AudioStats.AUDIO_STATE_DISABLED
    private var lastMicLevelSummary = "MIC LEVEL UNMEASURED"
    private var videoMode = false
    private var videoQuality = Quality.FHD
    private var videoFps = 30
    private var videoStabilizationEnabled = false
    private var micNoiseReductionEnabled = true
    private var availableFpsRanges: Array<Range<Int>> = emptyArray()
    private var activeIsoMin = 100
    private var activeIsoMax = 12800
    private var activeShutterMinNs = 51_265L
    private var activeShutterMaxNs = 170_494_792L
    private var manualIso = 400
    private var manualShutterNs = 10_000_000L
    private var minimumFocusDistance = 0f
    private var manualFocusDiopters = 0f
    private var manualFocusProgress = 0
    private var savedAwbMode = CaptureRequest.CONTROL_AWB_MODE_AUTO
    private var supportedAwbModes = intArrayOf(CaptureRequest.CONTROL_AWB_MODE_AUTO)
    private var supportedSceneModes = intArrayOf(CaptureRequest.CONTROL_SCENE_MODE_DISABLED)
    private var sceneModeIndex = 0
    private var awbModeIndex = 0
    private var manualFocusEnabled = false
    private var proModeEnabled = false
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var extensionsManager: ExtensionsManager? = null
    private var extensionsInitialized = false
    private var supportedPhotoExtensions = mutableListOf<Int>()
    private var selectedPhotoExtension: Int? = null
    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var gridEnabled = false
    private var timerSeconds = 0
    private var jpegQuality = 95
    private var exposureIndex = 0
    private val handler = Handler(Looper.getMainLooper())
    private var countdownActive = false
    private var countdownRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK
        val cameraPrefs = getSharedPreferences("signature_camera_settings", MODE_PRIVATE)
        gridEnabled = cameraPrefs.getBoolean("grid_enabled", false)
        timerSeconds = cameraPrefs.getInt("timer_seconds", 0).let { if (it == 0 || it == 3 || it == 10) it else 0 }
        if (!cameraPrefs.getBoolean("quality_profile_upgrade_v1", false)) {
            cameraPrefs.edit()
                .putBoolean("quality_profile_upgrade_v1", true)
                .putInt("jpeg_quality", 100)
                .putString("video_quality", "UHD")
                .apply()
        }
        jpegQuality = cameraPrefs.getInt("jpeg_quality", 100).let { if (it == 90 || it == 95 || it == 100) it else 100 }
        photoFlashMode = cameraPrefs.getInt("photo_flash_mode", ImageCapture.FLASH_MODE_OFF).let { if (it == ImageCapture.FLASH_MODE_AUTO || it == ImageCapture.FLASH_MODE_ON || it == ImageCapture.FLASH_MODE_OFF) it else ImageCapture.FLASH_MODE_OFF }
        screenFlashEnabled = cameraPrefs.getBoolean("screen_flash_enabled", true)
        videoQuality = when (cameraPrefs.getString("video_quality", "UHD")) {
            "UHD" -> Quality.UHD
            "HD" -> Quality.HD
            "SD" -> Quality.SD
            else -> Quality.FHD
        }
        videoFps = cameraPrefs.getInt("video_fps", 30).let { if (it == 60) 60 else if (it == 30) 30 else 0 }
        videoStabilizationEnabled = cameraPrefs.getBoolean("video_stabilization", false)
        micNoiseReductionEnabled = cameraPrefs.getBoolean("mic_noise_reduction_enabled", true)
        manualIso = cameraPrefs.getInt("manual_iso", 400).coerceIn(50, 51200)
        manualShutterNs = cameraPrefs.getLong("manual_shutter_ns", 10_000_000L).coerceIn(1_000L, 1_000_000_000L)
        manualFocusProgress = cameraPrefs.getInt("manual_focus_progress", 0).coerceIn(0, 1000)
        savedAwbMode = cameraPrefs.getInt("manual_awb_mode", CaptureRequest.CONTROL_AWB_MODE_AUTO)
        buildCameraUi()
        if (hasCameraPermission()) startCamera()
        else requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun buildCameraUi() {
        val root = FrameLayout(this).apply { setBackgroundColor(android.graphics.Color.BLACK) }
        previewView = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
        root.addView(previewView, FrameLayout.LayoutParams(-1, -1))
        gridView = GridOverlay(this)
        gridView.visibility = if (gridEnabled) View.VISIBLE else View.GONE
        root.addView(gridView, FrameLayout.LayoutParams(-1, -1))
        focusOverlay = FocusOverlay(this).apply {
            visibility = View.GONE
            isClickable = false
            isFocusable = false
        }
        root.addView(focusOverlay, FrameLayout.LayoutParams(-1, -1))
        screenFlashOverlay = View(this).apply {
            setBackgroundColor(android.graphics.Color.WHITE)
            alpha = 0f
            visibility = View.GONE
            isClickable = false
        }
        root.addView(screenFlashOverlay, FrameLayout.LayoutParams(-1, -1))

        val scaleDetector = android.view.ScaleGestureDetector(this,
            object : android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: android.view.ScaleGestureDetector): Boolean {
                    val state = camera?.cameraInfo?.zoomState?.value ?: return true
                    val ratio = (state.zoomRatio * detector.scaleFactor)
                        .coerceIn(state.minZoomRatio, state.maxZoomRatio)
                    camera?.cameraControl?.setZoomRatio(ratio)
                    zoomLabel.text = "ZOOM  %.1fx".format(Locale.US, ratio)
                    updateLensSelection(ratio)
                    return true
                }
            })
        var multiTouchGesture = false
        var doubleTapDetected = false
        val tapGestureDetector = android.view.GestureDetector(this,
            object : android.view.GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(event: MotionEvent): Boolean {
                    val state = camera?.cameraInfo?.zoomState?.value ?: return true
                    val ratio = 1f.coerceIn(state.minZoomRatio, state.maxZoomRatio)
                    camera?.cameraControl?.setZoomRatio(ratio)
                    zoomLabel.text = "ZOOM  %.1fx".format(Locale.US, ratio)
                    updateLensSelection(ratio)
                    doubleTapDetected = true
                    return true
                }
            })
        previewView.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            tapGestureDetector.onTouchEvent(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_POINTER_DOWN -> multiTouchGesture = true
                MotionEvent.ACTION_UP -> {
                    if (!multiTouchGesture && !doubleTapDetected) focusAt(event.x, event.y)
                    doubleTapDetected = false
                    multiTouchGesture = false
                }
                MotionEvent.ACTION_CANCEL -> {
                    doubleTapDetected = false
                    multiTouchGesture = false
                }
            }
            true
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(14), dp(12), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = 0f
                colors = intArrayOf(0xDD05070B.toInt(), 0x0005070B)
                orientation = GradientDrawable.Orientation.TOP_BOTTOM
            }
        }
        val title = TextView(this).apply {
            text = "SIGNATURE  •  CAMERA"
            textSize = 13f
            letterSpacing = 0.18f
            setTextColor(-1)
            gravity = Gravity.CENTER
        }
        zoomLabel = TextView(this).apply {
            text = "CAMERA  •  ZOOM 1.0x"
            textSize = 14f
            setTextColor(-1)
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, dp(2))
        }
        zoomSlider = SeekBar(this).apply {
            max = 100
            progress = 0
            contentDescription = "Camera zoom control"
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    val state = camera?.cameraInfo?.zoomState?.value ?: return
                    // Logarithmic zoom mapping gives finer control near 1x.
                    val zoomSpan = (state.maxZoomRatio / state.minZoomRatio).coerceAtLeast(1f)
                    val ratio = state.minZoomRatio * zoomSpan.pow(progress / 100f)
                    camera?.cameraControl?.setZoomRatio(ratio)
                    zoomLabel.text = "ZOOM  %.1fx".format(Locale.US, ratio)
                    updateLensSelection(ratio)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        audioLevelLabel = TextView(this).apply {
            text = "MIC  •  READY"
            textSize = 11f
            letterSpacing = 0.08f
            setTextColor(0xFFB8C7D9.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(3), 0, dp(2))
            contentDescription = "Microphone recording level"
        }
        // Compact iPhone-inspired status strip: keep the live viewfinder dominant.
        title.text = "SIGNATURE"
        title.textSize = 12f
        title.letterSpacing = 0.24f
        title.alpha = 0.82f
        top.addView(title)
        top.addView(zoomLabel)
        recordingTimer = android.widget.Chronometer(this).apply {
            text = "● REC  00:00"
            format = "● REC  %s"
            textSize = 12f
            letterSpacing = 0.08f
            setTextColor(0xFFFF626B.toInt())
            gravity = Gravity.CENTER
            visibility = View.GONE
            contentDescription = "Video recording duration"
        }
        top.addView(recordingTimer)
        top.addView(audioLevelLabel)
        audioLevelLabel.visibility = View.GONE
        audioLevelBar = android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            progressTintList = ColorStateList.valueOf(0xFF55D6A0.toInt())
            progressBackgroundTintList = ColorStateList.valueOf(0x55777777)
            contentDescription = "Live microphone input level"
        }
        top.addView(audioLevelBar, LinearLayout.LayoutParams(dp(144), dp(5)).apply {
            topMargin = dp(3)
            bottomMargin = dp(3)
        })
        audioLevelBar.visibility = View.GONE
        lensRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(4), dp(6), dp(4))
            background = GradientDrawable().apply {
                cornerRadius = 24f
                setColor(0x55232B38)
                setStroke(1, 0x447F91A8)
            }
        }
        // Lens selector is floated above the bottom camera dock, like a native camera app.
        root.addView(top, FrameLayout.LayoutParams(-1, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP))
        val landscapeLayout = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        if (!landscapeLayout) {
            root.addView(lensRow, FrameLayout.LayoutParams(-1, dp(48), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                setMargins(dp(30), 0, dp(30), dp(238))
            })
        }

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(8), dp(14), dp(8))
            // Minimal translucent camera dock; the preview remains edge-to-edge.
            background = GradientDrawable().apply {
                cornerRadii = floatArrayOf(30f, 30f, 30f, 30f, 0f, 0f, 0f, 0f)
                colors = intArrayOf(0xE90A0D13.toInt(), 0xF5090B10.toInt())
                orientation = GradientDrawable.Orientation.TOP_BOTTOM
                setStroke(1, 0x337F91A8)
            }
            elevation = 14f
            clipChildren = false
            clipToPadding = false
        }
        val advancedPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(6), dp(4), dp(6))
        }
        advancedPanel.addView(zoomSlider, LinearLayout.LayoutParams(-1, dp(42)))
        // Secondary settings are tucked away so the live viewfinder stays dominant.
        val exposureRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }
        exposureLabel = TextView(this).apply {
            text = "EV  0"
            textSize = 15f
            setTextColor(-1)
            gravity = Gravity.CENTER
            setPadding(dp(10), 0, dp(10), 0)
        }
        exposureLabel.contentDescription = "Exposure compensation. Tap to reset to zero EV."
        exposureLabel.isClickable = true
        exposureLabel.isFocusable = true
        exposureLabel.setOnClickListener { resetExposureCompensation() }
        exposureRow.addView(exposureLabel, LinearLayout.LayoutParams(0, dp(48), 1f))
        exposureRow.addView(smallButton("−  EV") { changeExposure(-1) }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { setMargins(dp(3), 0, dp(3), 0) })
        exposureRow.addView(smallButton("+  EV") { changeExposure(1) }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { setMargins(dp(3), 0, dp(3), 0) })
        advancedPanel.addView(exposureRow)

        val utilityRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }
        val gridButton = smallButton(if (gridEnabled) "GRID ON" else "GRID OFF") {
            gridEnabled = !gridEnabled
            gridView.visibility = if (gridEnabled) View.VISIBLE else View.GONE
            getSharedPreferences("signature_camera_settings", MODE_PRIVATE).edit()
                .putBoolean("grid_enabled", gridEnabled).apply()
            (it as Button).text = if (gridEnabled) "GRID ON" else "GRID OFF"
        }
        val timerButton = smallButton(if (timerSeconds == 0) "TIMER OFF" else "TIMER ${timerSeconds}s") {
            timerSeconds = when (timerSeconds) { 0 -> 3; 3 -> 10; else -> 0 }
            getSharedPreferences("signature_camera_settings", MODE_PRIVATE).edit()
                .putInt("timer_seconds", timerSeconds).apply()
            (it as Button).text = if (timerSeconds == 0) "TIMER OFF" else "TIMER ${timerSeconds}s"
        }
        val toolsButton = smallButton("DEVICE INFO") { showHardwareReport() }
        val galleryButton = smallButton("RECENT") { showRecentCaptures() }
        utilityRow.addView(gridButton, LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
        utilityRow.addView(timerButton, LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
        utilityRow.addView(galleryButton, LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
        utilityRow.addView(toolsButton, LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
        val proButton = smallButton("PRO CONTROLS") {
            if (!proModeEnabled && selectedPhotoExtension != null) {
                Toast.makeText(this, "Turn off the OEM photo effect before using manual Pro controls", Toast.LENGTH_SHORT).show()
                return@smallButton
            }
            proModeEnabled = !proModeEnabled
            proPanel.visibility = if (proModeEnabled) View.VISIBLE else View.GONE
            (it as Button).text = if (proModeEnabled) "PRO ON" else "PRO CONTROLS"
            if (proModeEnabled) {
                manualFocusEnabled = manualFocusProgress > 0 && minimumFocusDistance > 0f
                applyManualCapture()
            } else resetManualCapture()
        }
        utilityRow.addView(proButton, LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(dp(3), 0, dp(3), 0) })
        advancedPanel.addView(utilityRow)

        photoExtensionButton = smallButton("PHOTO EFFECT: CHECKING…") { cyclePhotoExtension() }
        photoExtensionButton.isEnabled = false
        advancedPanel.addView(photoExtensionButton, LinearLayout.LayoutParams(-1, dp(48)).apply { setMargins(dp(3), dp(4), dp(3), dp(4)) })
        val jpegQualityButton = smallButton("JPEG QUALITY: $jpegQuality%") {
            jpegQuality = when (jpegQuality) { 90 -> 95; 95 -> 100; else -> 90 }
            getSharedPreferences("signature_camera_settings", MODE_PRIVATE).edit()
                .putInt("jpeg_quality", jpegQuality).apply()
            (it as Button).text = "JPEG QUALITY: $jpegQuality%"
            Toast.makeText(this, "JPEG quality set to $jpegQuality%", Toast.LENGTH_SHORT).show()
            if (!videoMode) startCamera()
        }
        advancedPanel.addView(jpegQualityButton, LinearLayout.LayoutParams(-1, dp(48)).apply { setMargins(dp(3), 0, dp(3), dp(4)) })

        proPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = GradientDrawable().apply { cornerRadius = 16f; setColor(0xFF172235.toInt()) }
            visibility = View.GONE
        }
        isoValue = TextView(this).apply { text = "ISO 400"; textSize = 12f; setTextColor(-1) }
        isoSlider = SeekBar(this).apply {
            max = 100; progress = 25
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    manualIso = (activeIsoMin + (activeIsoMax - activeIsoMin) * progress / 100f).toInt().coerceIn(activeIsoMin, activeIsoMax)
                    isoValue.text = "ISO $manualIso"
                    if (fromUser) {
                        getSharedPreferences("signature_camera_settings", MODE_PRIVATE).edit().putInt("manual_iso", manualIso).apply()
                        if (proModeEnabled) applyManualCapture()
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        shutterValue = TextView(this).apply { text = "SHUTTER 10.00 ms"; textSize = 12f; setTextColor(-1) }
        shutterSlider = SeekBar(this).apply {
            max = 1000; progress = 500
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    manualShutterNs = shutterNsFromProgress(progress)
                    shutterValue.text = "SHUTTER ${formatShutter(manualShutterNs)}"
                    if (fromUser) {
                        getSharedPreferences("signature_camera_settings", MODE_PRIVATE).edit().putLong("manual_shutter_ns", manualShutterNs).apply()
                        if (proModeEnabled) applyManualCapture()
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        proPanel.addView(isoValue)
        proPanel.addView(isoSlider, LinearLayout.LayoutParams(-1, dp(40)))
        proPanel.addView(shutterValue)
        proPanel.addView(shutterSlider, LinearLayout.LayoutParams(-1, dp(40)))
        focusValue = TextView(this).apply { text = "FOCUS  AUTO"; textSize = 12f; setTextColor(-1) }
        focusSlider = SeekBar(this).apply {
            max = 1000
            progress = 0
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    manualFocusDiopters = minimumFocusDistance * (progress.coerceIn(0, 1000) / 1000f)
                    focusValue.text = if (progress == 0) "FOCUS  INFINITY" else "FOCUS  %.2f D".format(Locale.US, manualFocusDiopters)
                    if (fromUser) {
                        manualFocusProgress = progress.coerceIn(0, 1000)
                        getSharedPreferences("signature_camera_settings", MODE_PRIVATE).edit().putInt("manual_focus_progress", manualFocusProgress).apply()
                        if (proModeEnabled && minimumFocusDistance > 0f) {
                            manualFocusEnabled = manualFocusProgress > 0
                            applyManualCapture()
                        }
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        proPanel.addView(focusValue)
        proPanel.addView(focusSlider, LinearLayout.LayoutParams(-1, dp(40)))
        whiteBalanceButton = smallButton("WHITE BALANCE: AUTO") {
            cycleWhiteBalance()
        }
        proPanel.addView(whiteBalanceButton, LinearLayout.LayoutParams(-1, dp(44)).apply { setMargins(0, dp(4), 0, dp(4)) })
        sceneModeButton = smallButton("SCENE: AUTO") { cycleSceneMode() }
        proPanel.addView(sceneModeButton, LinearLayout.LayoutParams(-1, dp(44)).apply { setMargins(0, dp(4), 0, dp(4)) })
        proPanel.addView(smallButton("CAPTURE RAW / DNG") { view ->
            (view as Button).isEnabled = false
            view.text = "CAPTURING RAW…"
            captureRawDng(view)
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { setMargins(0, dp(8), 0, 0) })
        advancedPanel.addView(proPanel)

        val videoRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(5), 0, dp(5))
        }
        photoModeButton = smallButton("PHOTO") { setCameraMode(false) }
        videoModeButton = smallButton("VIDEO") { setCameraMode(true) }
        videoQualityButton = smallButton("VIDEO QUALITY: " + when (videoQuality) {
            Quality.UHD -> "4K"
            Quality.FHD -> "FHD"
            Quality.HD -> "HD"
            else -> "SD"
        }) { cycleVideoQuality() }
        videoFpsButton = smallButton(if (videoFps == 0) "FRAME RATE: AUTO" else "FRAME RATE: $videoFps FPS") { cycleVideoFps() }
        videoStabilizationButton = smallButton("STABILIZATION OFF") { toggleVideoStabilization() }
        videoRow.addView(photoModeButton, LinearLayout.LayoutParams(0, dp(50), 1f).apply { setMargins(dp(4), 0, dp(4), 0) })
        videoRow.addView(videoModeButton, LinearLayout.LayoutParams(0, dp(50), 1f).apply { setMargins(dp(4), 0, dp(4), 0) })
        updateModeButtonAppearance()
        advancedPanel.addView(videoQualityButton, LinearLayout.LayoutParams(-1, dp(48)).apply { setMargins(dp(3), dp(4), dp(3), dp(4)) })
        advancedPanel.addView(videoFpsButton, LinearLayout.LayoutParams(-1, dp(48)).apply { setMargins(dp(3), 0, dp(3), dp(4)) })
        advancedPanel.addView(videoStabilizationButton, LinearLayout.LayoutParams(-1, dp(48)).apply { setMargins(dp(3), 0, dp(3), dp(4)) })
        micNoiseReductionButton = smallButton(if (micNoiseReductionEnabled) "MIC CLEAN: ON" else "MIC CLEAN: OFF") {
            if (!videoMode) {
                Toast.makeText(this, "Microphone cleanup is for video recording", Toast.LENGTH_SHORT).show()
                return@smallButton
            }
            if (recording != null) {
                Toast.makeText(this, "Stop recording before changing microphone processing", Toast.LENGTH_SHORT).show()
                return@smallButton
            }
            micNoiseReductionEnabled = !micNoiseReductionEnabled
            getSharedPreferences("signature_camera_settings", MODE_PRIVATE).edit()
                .putBoolean("mic_noise_reduction_enabled", micNoiseReductionEnabled).apply()
            updateMicNoiseReductionButton()
            startCamera()
        }
        advancedPanel.addView(micNoiseReductionButton, LinearLayout.LayoutParams(-1, dp(48)).apply { setMargins(dp(3), 0, dp(3), dp(4)) })
        updateMicNoiseReductionButton()

        val mainRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(-1, -2)
            clipChildren = false
            clipToPadding = false
            elevation = 16f
        }
        val flip = Button(this).apply {
            text = "↻"
            isAllCaps = false
            textSize = 28f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(android.graphics.Color.WHITE)
            background = GradientDrawable().apply { cornerRadius = 30f; setColor(0x663A414D); setStroke(1, 0x66FFFFFF) }
            elevation = 8f
            isClickable = true
            isFocusable = true
            minWidth = 0
            minHeight = dp(58)
            contentDescription = "Switch front and rear camera"
            setPadding(dp(4), 0, dp(4), 0)
            setOnClickListener { view ->
                view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                if (recording != null) {
                    Toast.makeText(this@MainActivity, "Stop recording before switching cameras", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                camera?.cameraControl?.enableTorch(false)
                torchEnabled = false
                cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA)
                    CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                syncFlashButtonLabel()
                selectedPhotoExtension = null
                refreshPhotoExtensions()
                startCamera()
            }
        }
        captureButton = Button(this).apply {
            text = "●"
            textSize = 34f
            isAllCaps = false
            setTextColor(android.graphics.Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFFF5F5F5.toInt())
                setStroke(5, 0xFF9AA4B2.toInt())
            }
            elevation = 10f
            isClickable = true
            isFocusable = true
            minWidth = 0
            minHeight = dp(88)
            setPadding(dp(4), 0, dp(4), 0)
            contentDescription = "Take photo or control video recording"
            setOnClickListener { view ->
                view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                triggerCaptureAction()
            }
        }
        updateCaptureButtonAppearance()
        flashButton = Button(this).apply {
            text = "⚡"
            isAllCaps = false
            textSize = 24f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(android.graphics.Color.WHITE)
            background = GradientDrawable().apply { cornerRadius = 30f; setColor(0x663A414D); setStroke(1, 0x66FFFFFF) }
            minWidth = 0
            minHeight = dp(58)
            setPadding(dp(4), 0, dp(4), 0)
            setOnClickListener { view ->
                view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) {
                    if (!videoMode) {
                        screenFlashEnabled = !screenFlashEnabled
                        getSharedPreferences("signature_camera_settings", MODE_PRIVATE).edit().putBoolean("screen_flash_enabled", screenFlashEnabled).apply()
                        updateScreenFlashPreview()
                        syncFlashButtonLabel()
                    } else Toast.makeText(this@MainActivity, "Front screen light is not available during video recording", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (videoMode) {
                    val enabled = !torchEnabled
                    camera?.cameraControl?.enableTorch(enabled)
                    torchEnabled = enabled
                    syncFlashButtonLabel()
                } else {
                    val image = imageCapture ?: return@setOnClickListener
                    photoFlashMode = when (photoFlashMode) {
                        ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO
                        ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                        else -> ImageCapture.FLASH_MODE_OFF
                    }
                    image.flashMode = photoFlashMode
                    getSharedPreferences("signature_camera_settings", MODE_PRIVATE).edit().putInt("photo_flash_mode", photoFlashMode).apply()
                    syncFlashButtonLabel()
                }
            }
        }
        fun sideControl(button: Button, caption: String, status: Boolean = false): LinearLayout {
            val column = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                clipChildren = false
                clipToPadding = false
            }
            button.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xCC263243.toInt())
                setStroke(1, 0x668FA6C4)
            }
            button.elevation = 5f
            column.addView(button, LinearLayout.LayoutParams(dp(58), dp(58)))
            val label = if (status) flashStatusLabel else TextView(this).apply {
                text = caption
                textSize = 10f
                letterSpacing = 0.06f
                setTextColor(0xFFD4DCE8.toInt())
                gravity = Gravity.CENTER
                maxLines = 1
            }
            column.addView(label, LinearLayout.LayoutParams(-1, dp(22)))
            return column
        }
        flashStatusLabel = TextView(this).apply {
            textSize = 9f
            letterSpacing = 0.02f
            setTextColor(0xFFD4DCE8.toInt())
            gravity = Gravity.CENTER
            maxLines = 1
        }
        mainRow.setPadding(dp(10), dp(8), dp(10), dp(4))
        mainRow.gravity = Gravity.CENTER
        mainRow.addView(sideControl(flip, "CAMERA"), LinearLayout.LayoutParams(0, dp(82), 1f).apply { setMargins(dp(8), 0, dp(8), 0) })
        mainRow.addView(captureButton, LinearLayout.LayoutParams(dp(88), dp(88)).apply { setMargins(dp(10), 0, dp(10), 0) })
        syncFlashButtonLabel()
        mainRow.addView(sideControl(flashButton, "FLASH", true), LinearLayout.LayoutParams(0, dp(82), 1f).apply { setMargins(dp(8), 0, dp(8), 0) })
        // Camera-style mode rail sits above the shutter, keeping capture as the primary action.
        videoRow.setPadding(dp(18), dp(3), dp(18), dp(3))
        videoRow.gravity = Gravity.CENTER
        videoRow.background = GradientDrawable().apply {
            cornerRadius = 24f
            setColor(0x44242B36)
            setStroke(1, 0x227F91A8)
        }
        videoRow.elevation = 4f
        panel.addView(videoRow, LinearLayout.LayoutParams(-1, dp(54)).apply {
            setMargins(dp(18), dp(2), dp(18), dp(2))
        })
        panel.addView(mainRow)
        val advancedScroll = ScrollView(this).apply {
            visibility = View.GONE
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(advancedPanel)
        }
        val advancedSettingsHeight = if (landscapeLayout) dp(104) else dp(260)
        panel.addView(advancedScroll, LinearLayout.LayoutParams(-1, advancedSettingsHeight))
        fun positionLensRailAboveDock() {
            // Keep the lens selector completely clear of the dock in both collapsed
            // and expanded states; a fixed margin caused it to sit behind settings.
            panel.post {
                val params = lensRow.layoutParams as? FrameLayout.LayoutParams ?: return@post
                params.bottomMargin = panel.height + dp(8)
                lensRow.layoutParams = params
            }
        }
        val advancedToggleButton = smallButton("⚙  CAMERA SETTINGS") {
            val show = advancedScroll.visibility != View.VISIBLE
            advancedScroll.visibility = if (show) View.VISIBLE else View.GONE
            (it as Button).text = if (show) "⌃  HIDE SETTINGS" else "⚙  CAMERA SETTINGS"
            positionLensRailAboveDock()
        }
        if (landscapeLayout) {
            // In landscape, keep the lens rail inside the dock so it cannot be
            // pushed underneath the top status strip by the expanded settings sheet.
            panel.addView(lensRow, 0, LinearLayout.LayoutParams(-1, dp(44)).apply {
                setMargins(dp(12), dp(2), dp(12), dp(2))
            })
        }
        panel.addView(advancedToggleButton, LinearLayout.LayoutParams(-1, dp(46)).apply { setMargins(dp(4), dp(4), dp(4), dp(2)) })
        val hint = TextView(this).apply {
            text = "TAP TO FOCUS   •   PINCH TO ZOOM"
            textSize = 10f
            setTextColor(0xFFBBBBBB.toInt())
            gravity = Gravity.CENTER
        }
        panel.addView(hint)
        root.addView(panel, FrameLayout.LayoutParams(-1, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        // Recalculate after the dock has been measured so the floating lens rail
        // never overlaps the shutter controls or the expanded settings sheet.
        setContentView(root)
        if (!landscapeLayout) {
            positionLensRailAboveDock()
            panel.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                positionLensRailAboveDock()
            }
        }
        lensRow.bringToFront()
    }

    private fun setCameraMode(enableVideo: Boolean) {
        if (recording != null) {
            Toast.makeText(this, "Stop recording before changing mode", Toast.LENGTH_SHORT).show()
            return
        }
        if (videoMode == enableVideo) return
        // Mode rebinding disables the hardware torch; clear its UI state as well.
        camera?.cameraControl?.enableTorch(false)
        torchEnabled = false
        videoMode = enableVideo
        captureButton.text = if (videoMode) "● REC" else "●"
        captureButton.contentDescription = if (videoMode) "Start video recording" else "Take photo"
        syncFlashButtonLabel()
        updateModeButtonAppearance()
        startCamera()
    }

    private fun updateModeButtonAppearance() {
        if (!::photoModeButton.isInitialized || !::videoModeButton.isInitialized) return
        fun modeBackground(selected: Boolean) = GradientDrawable().apply {
            cornerRadius = 24f
            setColor(if (selected) 0xFF1677FF.toInt() else 0xCC202630.toInt())
            setStroke(if (selected) 2 else 1, if (selected) 0xFF9BC5FF.toInt() else 0x557F8DA3)
        }
        photoModeButton.background = modeBackground(!videoMode)
        videoModeButton.background = modeBackground(videoMode)
        photoModeButton.alpha = if (videoMode) 0.78f else 1f
        videoModeButton.alpha = if (videoMode) 1f else 0.78f
        photoModeButton.text = if (videoMode) "PHOTO" else "●  PHOTO"
        videoModeButton.text = if (videoMode) "●  VIDEO" else "VIDEO"
        // The live mic meter belongs to video capture; keep photo mode's viewfinder uncluttered.
        if (::audioLevelLabel.isInitialized) {
            audioLevelLabel.visibility = if (videoMode) View.VISIBLE else View.GONE
        }
        if (::audioLevelBar.isInitialized) {
            audioLevelBar.visibility = if (videoMode) View.VISIBLE else View.GONE
        }
        updateMicNoiseReductionButton()
        updateCaptureButtonAppearance()
    }

    private fun updateMicNoiseReductionButton() {
        if (!::micNoiseReductionButton.isInitialized) return
        micNoiseReductionButton.text = when {
            !videoMode -> "MIC CLEAN: VIDEO ONLY"
            micNoiseReductionEnabled -> "MIC CLEAN: ON"
            else -> "MIC CLEAN: OFF"
        }
        micNoiseReductionButton.isEnabled = videoMode && recording == null
        micNoiseReductionButton.alpha = if (videoMode) 1f else 0.48f
        micNoiseReductionButton.contentDescription =
            "Video microphone cleanup ${if (micNoiseReductionEnabled) "enabled" else "disabled"}; reduces low-frequency rumble and background noise during pauses"
    }

    // A camera-style shutter: white ring for stills, red center for video,
    // and a red stop-square while a recording is active.
    private fun updateCaptureButtonAppearance() {
        if (!::captureButton.isInitialized) return
        val isRecording = recording != null
        val outer = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(android.graphics.Color.TRANSPARENT)
            setStroke(5, android.graphics.Color.WHITE)
        }
        val inner = GradientDrawable().apply {
            if (isRecording) {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 6f
                setColor(0xFFFF3B50.toInt())
            } else {
                shape = GradientDrawable.OVAL
                setColor(if (videoMode) 0xFFFF3B50.toInt() else android.graphics.Color.WHITE)
            }
        }
        val inset = ((captureButton.width.takeIf { it > 0 } ?: 88) * if (isRecording) 0.30f else 0.22f).toInt()
        captureButton.background = android.graphics.drawable.LayerDrawable(arrayOf(outer, inner)).apply {
            setLayerInset(1, inset, inset, inset, inset)
        }
        // The drawable carries the shutter symbol; avoid the old low-contrast text glyph.
        captureButton.text = ""
        captureButton.setTextColor(android.graphics.Color.TRANSPARENT)
        captureButton.contentDescription = when {
            isRecording -> "Stop video recording"
            videoMode -> "Start video recording"
            else -> "Take photo"
        }
    }

    private fun syncFlashButtonLabel() {
        if (!::flashButton.isInitialized) return
        val state = when {
            cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA -> if (videoMode) "FRONT LIGHT N/A" else if (screenFlashEnabled) "SCREEN ON" else "SCREEN OFF"
            videoMode -> if (torchEnabled) "TORCH ON" else "TORCH OFF"
            photoFlashMode == ImageCapture.FLASH_MODE_AUTO -> "FLASH AUTO"
            photoFlashMode == ImageCapture.FLASH_MODE_ON -> "FLASH ON"
            else -> "FLASH OFF"
        }
        flashButton.text = "⚡"
        flashButton.contentDescription = state
        if (::flashStatusLabel.isInitialized) flashStatusLabel.text = state
    }

    private fun smallButton(label: String, action: (View) -> Unit) =
        Button(this).apply {
            text = label
            textSize = 15f
            isAllCaps = false
            setTextColor(android.graphics.Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = 24f
                setColor(0xFF252A33.toInt())
                setStroke(1, 0x44FFFFFF)
            }
            elevation = 2f
            isClickable = true
            isFocusable = true
            minWidth = 0
            minHeight = dp(48)
            contentDescription = label
            setPadding(dp(12), 0, dp(12), 0)
            setOnClickListener { action(it) }
        }

    private fun discoverRearLenses() {
        lensRow.removeAllViews()
        val manager = getSystemService(CAMERA_SERVICE) as CameraManager
        val rearIds = manager.cameraIdList.filter { id ->
            manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        }
        val rearId = rearIds.firstOrNull { id ->
            val chars = manager.getCameraCharacteristics(id)
            val caps = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
            caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA) &&
                chars.physicalCameraIds.isNotEmpty()
        } ?: rearIds.firstOrNull() ?: return
        val logical = manager.getCameraCharacteristics(rearId)
        val logicalFocal = logical.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull() ?: return
        val logicalSize = logical.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) ?: return
        val baseEquivalent = logicalFocal / logicalSize.width
        val options = mutableListOf<Pair<String, Float>>()
        options.add("1.0x" to 1f)
        logical.physicalCameraIds.forEach { physicalId ->
            try {
                val physical = manager.getCameraCharacteristics(physicalId)
                val focal = physical.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull()
                val size = physical.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                if (focal != null && size != null && size.width > 0f) {
                    val ratio = (focal / size.width) / baseEquivalent
                    if (ratio.isFinite() && ratio in 0.25f..10f && options.none { kotlin.math.abs(it.second - ratio) < 0.12f }) options.add("%.1fx".format(Locale.US, ratio) to ratio)
                }
            } catch (_: Exception) { }
        }
        val sorted = options.distinctBy { it.first }.sortedBy { it.second }
        if (sorted.size <= 1) {
            lensRow.addView(TextView(this).apply { text = "REAR LENS DETAILS NOT EXPOSED"; textSize = 10f; setTextColor(0xFFCCCCCC.toInt()); gravity = Gravity.CENTER })
            return
        }
        sorted.forEach { (label, ratio) ->
            val button = Button(this)
            button.text = label.replace("x", "×")
            button.textSize = 13f
            button.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
            button.isAllCaps = false
            button.setTextColor(android.graphics.Color.WHITE)
            button.tag = ratio
            button.background = GradientDrawable().apply {
                cornerRadius = 18f
                setColor(if (kotlin.math.abs(ratio - 1f) < 0.12f) 0xFF1677FF.toInt() else 0xCC263243.toInt())
                setStroke(1, if (kotlin.math.abs(ratio - 1f) < 0.12f) 0xFF70B5FF.toInt() else 0x668FA6C4)
            }
            button.minWidth = 0
            button.minHeight = dp(44)
            button.setPadding(dp(8), 0, dp(8), 0)
            button.elevation = 3f
            // Never label a clamped digital zoom as a different physical lens preset.
            // Keep presets disabled when the active CameraX logical camera cannot reach them.
            val zoomState = camera?.cameraInfo?.zoomState?.value
            val inSupportedRange = zoomState != null &&
                ratio >= zoomState.minZoomRatio && ratio <= zoomState.maxZoomRatio
            // Keep lens presets available in OEM Night/HDR modes when the active
            // logical camera reports that zoom ratio as supported.
            button.isEnabled = inSupportedRange
            button.alpha = if (button.isEnabled) 1f else 0.42f
            button.contentDescription = if (!inSupportedRange) {
                "$label lens preset is outside the supported zoom range"
            } else {
                "Switch logical rear camera to the $label lens view"
            }
            button.setOnClickListener {
                if (cameraSelector != CameraSelector.DEFAULT_BACK_CAMERA) return@setOnClickListener
                val state = camera?.cameraInfo?.zoomState?.value ?: return@setOnClickListener
                if (ratio < state.minZoomRatio || ratio > state.maxZoomRatio) {
                    Toast.makeText(this, "$label is outside the supported zoom range", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                camera?.cameraControl?.setZoomRatio(ratio)
                zoomLabel.text = "LENS VIEW  $label"
                updateLensSelection(ratio)
                button.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
            }
            lensRow.addView(button, LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(dp(3), 0, dp(3), 0) })
        }
    }

    private fun updateLensButtonAvailability(minZoom: Float, maxZoom: Float) {
        if (!::lensRow.isInitialized) return
        for (i in 0 until lensRow.childCount) {
            val button = lensRow.getChildAt(i) as? Button ?: continue
            val ratio = button.tag as? Float ?: continue
            val available = cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA &&
                ratio >= minZoom && ratio <= maxZoom
            button.isEnabled = available
            button.alpha = if (available) 1f else 0.42f
            val label = button.text.toString().replace("×", "x")
            button.contentDescription = if (available) {
                "Switch rear camera to $label lens view"
            } else {
                "$label lens preset is outside the active camera zoom range"
            }
        }
    }

    private fun updateLensSelection(zoomRatio: Float) {
        if (!::lensRow.isInitialized) return
        var nearest: Button? = null
        var nearestDistance = Float.MAX_VALUE
        for (i in 0 until lensRow.childCount) {
            val button = lensRow.getChildAt(i) as? Button ?: continue
            val preset = button.tag as? Float ?: continue
            val distance = kotlin.math.abs(kotlin.math.ln(zoomRatio.coerceAtLeast(0.01f) / preset))
            if (distance < nearestDistance) {
                nearestDistance = distance
                nearest = button
            }
        }
        for (i in 0 until lensRow.childCount) {
            val button = lensRow.getChildAt(i) as? Button ?: continue
            val selected = button === nearest && nearestDistance < 0.22f
            button.background = GradientDrawable().apply {
                cornerRadius = 18f
                setColor(if (selected) 0xFF1677FF.toInt() else 0xCC263243.toInt())
                setStroke(1, if (selected) 0xFF70B5FF.toInt() else 0x668FA6C4)
            }
            button.isSelected = selected
        }
    }

    private fun showHardwareReport() {
        val manager = getSystemService(CAMERA_SERVICE) as CameraManager
        val report = StringBuilder("MOTOROLA SIGNATURE • CAMERA2 HARDWARE REPORT\n")
        report.append("Android ").append(android.os.Build.VERSION.RELEASE)
            .append(" (API ").append(android.os.Build.VERSION.SDK_INT).append(")\n")
        for (id in manager.cameraIdList) {
            val c = manager.getCameraCharacteristics(id)
            val facing = when (c.get(CameraCharacteristics.LENS_FACING)) {
                CameraCharacteristics.LENS_FACING_BACK -> "BACK"
                CameraCharacteristics.LENS_FACING_FRONT -> "FRONT"
                CameraCharacteristics.LENS_FACING_EXTERNAL -> "EXTERNAL"
                else -> "UNKNOWN"
            }
            val caps = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
            val capNames = caps.map { cap ->
                when (cap) {
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE -> "PHOTO/VIDEO"
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR -> "MANUAL SENSOR"
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING -> "MANUAL PROCESSING"
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW -> "RAW/DNG"
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA -> "LOGICAL MULTI-CAMERA"
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO -> "HIGH-SPEED VIDEO"
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_ULTRA_HIGH_RESOLUTION_SENSOR -> "ULTRA-HIGH-RES"
                    else -> null
                }
            }.filterNotNull()
            val level = when (c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)) {
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL 3"
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
                else -> "UNKNOWN"
            }
            val focal = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.joinToString()
            val iso = c.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
            val shutter = c.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
            val ev = c.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
            val maxZoom = c.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)
            val physical = c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
            val physicalDetails = c.physicalCameraIds.mapNotNull { physicalId ->
                try {
                    val pc = manager.getCameraCharacteristics(physicalId)
                    val pf = pc.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.joinToString()
                    val ps = pc.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                    val pa = pc.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
                    val pm = pc.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    val pj = pm?.getOutputSizes(ImageFormat.JPEG)?.maxByOrNull { it.width.toLong() * it.height }
                    "ID $physicalId: focal=${pf ?: "?"}mm, sensor=${ps?.let { "%.2f x %.2fmm".format(Locale.US, it.width, it.height) } ?: "?"}, array=${pa?.let { "${it.width}x${it.height}" } ?: "?"}, JPEG=${pj?.let { "${it.width}x${it.height}" } ?: "?"}"
                } catch (_: Exception) { "ID $physicalId: characteristics restricted by vendor" }
            }
            val pixelArray = c.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
            val streamMap = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val jpeg = streamMap?.getOutputSizes(ImageFormat.JPEG)
                ?.maxByOrNull { it.width.toLong() * it.height }
            val raw = streamMap?.getOutputSizes(ImageFormat.RAW_SENSOR)
                ?.maxByOrNull { it.width.toLong() * it.height }
            val fps = c.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)?.joinToString()
            val noiseModes = c.get(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES)?.joinToString()
            val hotPixelModes = c.get(CameraCharacteristics.HOT_PIXEL_AVAILABLE_HOT_PIXEL_MODES)?.joinToString()
            val shadingModes = c.get(CameraCharacteristics.SHADING_AVAILABLE_MODES)?.joinToString()
            val aberrationModes = c.get(CameraCharacteristics.COLOR_CORRECTION_AVAILABLE_ABERRATION_MODES)?.joinToString()
            val sceneModes = c.get(CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES)?.joinToString()
            report.append("\n\nCamera ID ").append(id).append(" • ").append(facing)
                .append("\nHardware level: ").append(level)
                .append("\nCapabilities: ").append(capNames.joinToString().ifBlank { "Standard only / not reported" })
                .append("\nPhysical camera IDs: ").append(c.physicalCameraIds.toList().joinToString().ifBlank { "Not exposed" })
                .append("\nPhysical camera details: ").append(physicalDetails.joinToString(" | ").ifBlank { "Not available" })
                .append("\nFocal length(s): ").append(focal ?: "Not reported")
                .append("\nSensor size: ").append(physical?.let { "%.2f x %.2f mm".format(Locale.US, it.width, it.height) } ?: "Not reported")
                .append("\nPixel array: ").append(pixelArray?.let { "${it.width} x ${it.height}" } ?: "Not reported")
                .append("\nMax JPEG stream: ").append(jpeg?.let { "${it.width} x ${it.height}" } ?: "Not reported")
                .append("\nMax RAW stream: ").append(raw?.let { "${it.width} x ${it.height}" } ?: "Not supported/reported")
                .append("\nISO range: ").append(iso?.let { "${it.lower}–${it.upper}" } ?: "Not reported")
                .append("\nShutter range (ns): ").append(shutter?.let { "${it.lower}–${it.upper}" } ?: "Not reported")
                .append("\nEV compensation steps: ").append(ev?.let { "${it.lower} to ${it.upper}" } ?: "Not reported")
                .append("\nMax digital zoom: ").append(maxZoom ?: "Not reported")
                .append("\nNoise-reduction modes: ").append(noiseModes ?: "Not reported")
                .append("\nHot-pixel modes: ").append(hotPixelModes ?: "Not reported")
                .append("\nShading modes: ").append(shadingModes ?: "Not reported")
                .append("\nAberration-correction modes: ").append(aberrationModes ?: "Not reported")
                .append("\nScene modes (HDR/Night): ").append(sceneModes ?: "Not reported")
                .append("\nAE frame-rate ranges: ").append(fps ?: "Not reported")
        }
        val body = TextView(this).apply {
            text = report.toString()
            textSize = 13f
            setTextColor(android.graphics.Color.WHITE)
            setPadding(20, 16, 20, 16)
        }
        val scroll = ScrollView(this).apply {
            addView(body)
            setBackgroundColor(0xFF101827.toInt())
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Device camera capabilities")
            .setView(scroll)
            .setPositiveButton("CLOSE", null)
            .setNeutralButton("SHARE REPORT") { _, _ ->
                val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_TEXT, report.toString())
                }
                startActivity(android.content.Intent.createChooser(send, "Share camera report"))
            }
            .show()
    }

    private fun focusAt(x: Float, y: Float) {
        val activeCamera = camera ?: return
        val point: MeteringPoint = previewView.meteringPointFactory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .setAutoCancelDuration(3, TimeUnit.SECONDS).build()
        previewView.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
        focusOverlay.showAt(x, y)
        val focusFuture = activeCamera.cameraControl.startFocusAndMetering(action)
        focusFuture.addListener({
            try {
                focusOverlay.setFocusResult(focusFuture.get().isFocusSuccessful)
            } catch (_: Exception) {
                focusOverlay.setFocusResult(false)
            }
        }, ContextCompat.getMainExecutor(this))
        Toast.makeText(this, "Focusing…", Toast.LENGTH_SHORT).show()
    }

    private fun applyManualCapture() {
        val activeCamera = camera ?: return
        try {
            val builder = CaptureRequestOptions.Builder()
                .setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                .setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, manualIso)
                .setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, manualShutterNs)
                .setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, supportedAwbModes.getOrElse(awbModeIndex) { CaptureRequest.CONTROL_AWB_MODE_AUTO })
            if (manualFocusEnabled && minimumFocusDistance > 0f) {
                builder.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
                builder.setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, manualFocusDiopters)
            }
            val options = builder.build()
            Camera2CameraControl.from(activeCamera.cameraControl).setCaptureRequestOptions(options)
        } catch (e: Exception) {
            Toast.makeText(this, "Manual exposure unavailable: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun resetManualCapture() {
        try {
            val activeCamera = camera ?: return
            Camera2CameraControl.from(activeCamera.cameraControl).clearCaptureRequestOptions()
            manualFocusEnabled = false
            manualFocusDiopters = minimumFocusDistance * (manualFocusProgress / 1000f)
            focusSlider.progress = manualFocusProgress
            focusValue.text = if (minimumFocusDistance > 0f && manualFocusProgress > 0) "FOCUS  %.2f D (SAVED)".format(Locale.US, manualFocusDiopters) else "FOCUS  AUTO"
            val savedMode = supportedAwbModes.getOrElse(awbModeIndex) { CaptureRequest.CONTROL_AWB_MODE_AUTO }
            whiteBalanceButton.text = "WHITE BALANCE: ${when (savedMode) { CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT -> "INCANDESCENT"; CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT -> "FLUORESCENT"; CaptureRequest.CONTROL_AWB_MODE_WARM_FLUORESCENT -> "WARM FLUORESCENT"; CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT -> "DAYLIGHT"; CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT -> "CLOUDY"; CaptureRequest.CONTROL_AWB_MODE_TWILIGHT -> "TWILIGHT"; CaptureRequest.CONTROL_AWB_MODE_SHADE -> "SHADE"; else -> "AUTO" }}"
        } catch (_: Exception) { }
    }

    private fun toggleVideoRecording() {
        val activeVideo = videoCapture ?: run {
            Toast.makeText(this, "Video camera is not ready", Toast.LENGTH_SHORT).show()
            return
        }
        if (recording != null) {
            recording?.stop()
            captureButton.isEnabled = false
            captureButton.text = "SAVING…"
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), AUDIO_PERMISSION_REQUEST)
            Toast.makeText(this, "Allow microphone access to record video with sound", Toast.LENGTH_LONG).show()
            return
        }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "Signature_Video_$stamp.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/SignatureCameraPro")
            }
        }
        val outputOptions = MediaStoreOutputOptions.Builder(
            contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(values).build()
        try {
            micPeakAmplitude = -120.0
            micAmplitudeTotal = 0.0
            micAmplitudeSamples = 0
            micAudioState = androidx.camera.video.AudioStats.AUDIO_STATE_DISABLED
            lastMicLevelSummary = "MIC LEVEL MEASURING"
            audioLevelBar.progress = 0
            audioLevelLabel.text = "MIC  •  STARTING"
            recording = activeVideo.output
                .prepareRecording(this, outputOptions)
                .withAudioEnabled()
                .start(ContextCompat.getMainExecutor(this)) { event ->
                    when (event) {
                        is VideoRecordEvent.Start -> {
                            recordingTimer.base = android.os.SystemClock.elapsedRealtime()
                            recordingTimer.visibility = View.VISIBLE
                            recordingTimer.start()
                            updateCaptureButtonAppearance()
                            audioLevelLabel.text = "MIC  •  RECORDING"
                            Toast.makeText(this, "Video recording started", Toast.LENGTH_SHORT).show()
                        }
                        is VideoRecordEvent.Status -> {
                            val stats = event.recordingStats.audioStats
                            micAudioState = stats.audioState
                            val amplitude = stats.audioAmplitude.coerceIn(0.0, 1.0)
                            val dbfs = 20.0 * kotlin.math.log10(amplitude.coerceAtLeast(0.00001))
                            if (stats.audioState == androidx.camera.video.AudioStats.AUDIO_STATE_ACTIVE && amplitude > 0.0) {
                                micPeakAmplitude = maxOf(micPeakAmplitude, dbfs)
                                micAmplitudeTotal += amplitude
                                micAmplitudeSamples++
                            }
                            audioLevelBar.progress = (amplitude * 100.0).toInt().coerceIn(0, 100)
                            audioLevelLabel.text = when (stats.audioState) {
                                androidx.camera.video.AudioStats.AUDIO_STATE_ACTIVE ->
                                    if (amplitude > 0.0) "MIC  •  ${dbfs.toInt()} dBFS" else "MIC  •  ACTIVE"
                                androidx.camera.video.AudioStats.AUDIO_STATE_MUTED -> "MIC  •  MUTED"
                                androidx.camera.video.AudioStats.AUDIO_STATE_SOURCE_SILENCED -> "MIC  •  SOURCE SILENCED"
                                androidx.camera.video.AudioStats.AUDIO_STATE_SOURCE_ERROR -> "MIC  •  SOURCE ERROR"
                                androidx.camera.video.AudioStats.AUDIO_STATE_ENCODER_ERROR -> "MIC  •  ENCODER ERROR"
                                else -> "MIC  •  WAITING"
                            }
                        }
                        is VideoRecordEvent.Finalize -> {
                            recording = null
                            updateCaptureButtonAppearance()
                            recordingTimer.stop()
                            recordingTimer.visibility = View.GONE
                            audioLevelBar.progress = 0
                            if (micAmplitudeSamples > 0) {
                                val averageAmplitude = micAmplitudeTotal / micAmplitudeSamples
                                val averageDbfs = 20.0 * kotlin.math.log10(averageAmplitude.coerceAtLeast(0.00001))
                                lastMicLevelSummary = "MIC PEAK ${micPeakAmplitude.toInt()} dBFS  •  AVG ${averageDbfs.toInt()} dBFS"
                            } else {
                                lastMicLevelSummary = when (micAudioState) {
                                    androidx.camera.video.AudioStats.AUDIO_STATE_SOURCE_SILENCED -> "MIC SOURCE SILENCED"
                                    androidx.camera.video.AudioStats.AUDIO_STATE_SOURCE_ERROR -> "MIC SOURCE ERROR"
                                    androidx.camera.video.AudioStats.AUDIO_STATE_ENCODER_ERROR -> "MIC ENCODER ERROR"
                                    androidx.camera.video.AudioStats.AUDIO_STATE_MUTED -> "MIC MUTED"
                                    else -> "MIC INPUT NOT DETECTED"
                                }
                            }
                            captureButton.isEnabled = true
                            captureButton.text = "●"
                            captureButton.contentDescription = "Start video recording"
                            if (event.hasError()) {
                                audioLevelLabel.text = "MIC  •  VIDEO ERROR"
                                Toast.makeText(this, "Video failed: ${event.error}", Toast.LENGTH_LONG).show()
                            } else {
                                audioLevelLabel.text = lastMicLevelSummary
                                Toast.makeText(this, "Video saved to Movies/SignatureCameraPro", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
        } catch (e: Exception) {
            recording = null
            updateCaptureButtonAppearance()
            if (::recordingTimer.isInitialized) {
                recordingTimer.stop()
                recordingTimer.visibility = View.GONE
            }
            captureButton.isEnabled = true
            captureButton.text = "●"
            captureButton.contentDescription = "Start video recording"
            audioLevelLabel.text = "MIC  •  START FAILED"
            Toast.makeText(this, "Cannot start recording: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun saveVideoPreferences() {
        getSharedPreferences("signature_camera_settings", MODE_PRIVATE).edit()
            .putString("video_quality", when (videoQuality) {
                Quality.UHD -> "UHD"
                Quality.HD -> "HD"
                Quality.SD -> "SD"
                else -> "FHD"
            })
            .putInt("video_fps", videoFps)
            .putBoolean("video_stabilization", videoStabilizationEnabled)
            .apply()
    }

    private fun findCameraIdForFacing(manager: CameraManager, facing: Int): String? {
        val matchingIds = manager.cameraIdList.filter { id ->
            manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == facing
        }
        // CameraX's default rear selector normally binds the logical multi-camera.
        // Prefer that same ID when reading FPS, focus and sensor capabilities.
        return matchingIds.firstOrNull { id ->
            val characteristics = manager.getCameraCharacteristics(id)
            val capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
            capabilities.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA) &&
                characteristics.physicalCameraIds.isNotEmpty()
        } ?: matchingIds.firstOrNull()
    }

    private fun updateVideoCapabilities() {
        val activeCamera = camera ?: return
        try {
            val supported = QualitySelector.getSupportedQualities(activeCamera.cameraInfo)
            val chosen = when {
                supported.contains(videoQuality) -> videoQuality
                supported.contains(Quality.FHD) -> Quality.FHD
                supported.contains(Quality.HD) -> Quality.HD
                supported.contains(Quality.SD) -> Quality.SD
                else -> supported.firstOrNull()
            }
            if (chosen != null) videoQuality = chosen
            videoQualityButton.text = "VIDEO QUALITY: " + when (videoQuality) {
                Quality.UHD -> "4K"
                Quality.FHD -> "FHD"
                Quality.HD -> "HD"
                else -> "SD"
            }
            val manager = getSystemService(CAMERA_SERVICE) as CameraManager
            val facing = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA)
                CameraCharacteristics.LENS_FACING_BACK else CameraCharacteristics.LENS_FACING_FRONT
            val id = findCameraIdForFacing(manager, facing)
            availableFpsRanges = id?.let {
                manager.getCameraCharacteristics(it).get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            } ?: emptyArray()
            val fixedRates = availableFpsRanges.filter { it.lower == it.upper }.map { it.lower }.distinct()
            if (videoFps !in fixedRates || videoFps !in listOf(30, 60)) {
                videoFps = when {
                    fixedRates.contains(30) -> 30
                    fixedRates.contains(60) -> 60
                    else -> 0
                }
            }
            videoFpsButton.text = if (videoFps == 0) "FRAME RATE: AUTO" else "FRAME RATE: $videoFps FPS"
            val stabilizationModes = id?.let {
                manager.getCameraCharacteristics(it).get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)
            } ?: intArrayOf(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF)
            videoStabilizationButton.isEnabled = stabilizationModes.contains(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON)
            if (!videoStabilizationButton.isEnabled) {
                videoStabilizationEnabled = false
                videoStabilizationButton.text = "STABILIZATION N/A"
            } else {
                videoStabilizationButton.text = if (videoStabilizationEnabled) "STABILIZATION ON" else "STABILIZATION OFF"
            }
            saveVideoPreferences()
            applyVideoRequestOptions()
        } catch (e: Exception) {
            Toast.makeText(this, "Video capabilities unavailable: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun cycleVideoQuality() {
        if (recording != null) {
            Toast.makeText(this, "Stop recording before changing video quality", Toast.LENGTH_SHORT).show()
            return
        }
        val activeCamera = camera ?: return
        val supported = QualitySelector.getSupportedQualities(activeCamera.cameraInfo)
        val choices = listOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD).filter { supported.contains(it) }
        if (choices.isEmpty()) {
            Toast.makeText(this, "No supported video quality found", Toast.LENGTH_SHORT).show()
            return
        }
        val currentIndex = choices.indexOf(videoQuality).let { if (it < 0) 0 else it }
        videoQuality = choices[(currentIndex + 1) % choices.size]
        videoQualityButton.text = "VIDEO QUALITY: " + when (videoQuality) {
            Quality.UHD -> "4K"
            Quality.FHD -> "FHD"
            Quality.HD -> "HD"
            else -> "SD"
        }
        saveVideoPreferences()
        if (recording == null) startCamera()
    }

    private fun cycleVideoFps() {
        if (recording != null) {
            Toast.makeText(this, "Stop recording before changing frame rate", Toast.LENGTH_SHORT).show()
            return
        }
        val choices = listOf(30, 60).filter { fps -> availableFpsRanges.any { it.lower == fps && it.upper == fps } }
        if (choices.isEmpty()) {
            videoFps = 0
            videoFpsButton.text = "FRAME RATE: AUTO"
            Toast.makeText(this, "Fixed 30/60 FPS is unavailable; using automatic frame rate", Toast.LENGTH_SHORT).show()
            saveVideoPreferences()
            applyVideoRequestOptions()
            return
        }
        val index = choices.indexOf(videoFps)
        videoFps = choices[(index + 1) % choices.size]
        videoFpsButton.text = "FRAME RATE: $videoFps FPS"
        saveVideoPreferences()
        applyVideoRequestOptions()
    }

    private fun toggleVideoStabilization() {
        if (recording != null) {
            Toast.makeText(this, "Stop recording before changing stabilization", Toast.LENGTH_SHORT).show()
            return
        }
        if (!videoStabilizationButton.isEnabled) {
            Toast.makeText(this, "Video stabilization is not supported", Toast.LENGTH_SHORT).show()
            return
        }
        videoStabilizationEnabled = !videoStabilizationEnabled
        videoStabilizationButton.text = if (videoStabilizationEnabled) "STABILIZATION ON" else "STABILIZATION OFF"
        saveVideoPreferences()
        applyVideoRequestOptions()
    }

    private fun applyVideoRequestOptions() {
        val activeCamera = camera ?: return
        if (!videoMode) return
        try {
            val builder = CaptureRequestOptions.Builder()
            if (videoFps > 0) {
                val fpsRange = availableFpsRanges.firstOrNull { it.lower == videoFps && it.upper == videoFps }
                if (fpsRange != null) builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange)
            }
            builder.setCaptureRequestOption(
                CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                if (videoStabilizationEnabled) CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON
                else CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF
            )
            Camera2CameraControl.from(activeCamera.cameraControl).setCaptureRequestOptions(builder.build())
        } catch (e: Exception) {
            Toast.makeText(this, "Video setting not applied: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun cycleWhiteBalance() {
        if (supportedAwbModes.isEmpty()) return
        awbModeIndex = (awbModeIndex + 1) % supportedAwbModes.size
        val mode = supportedAwbModes[awbModeIndex]
        savedAwbMode = mode
        getSharedPreferences("signature_camera_settings", MODE_PRIVATE).edit().putInt("manual_awb_mode", mode).apply()
        val label = when (mode) {
            CaptureRequest.CONTROL_AWB_MODE_AUTO -> "AUTO"
            CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT -> "INCANDESCENT"
            CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT -> "FLUORESCENT"
            CaptureRequest.CONTROL_AWB_MODE_WARM_FLUORESCENT -> "WARM FLUORESCENT"
            CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT -> "DAYLIGHT"
            CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT -> "CLOUDY"
            CaptureRequest.CONTROL_AWB_MODE_TWILIGHT -> "TWILIGHT"
            CaptureRequest.CONTROL_AWB_MODE_SHADE -> "SHADE"
            else -> "AUTO"
        }
        whiteBalanceButton.text = "WHITE BALANCE: $label"
        if (proModeEnabled) applyManualCapture()
    }

    private fun cycleSceneMode() {
        if (selectedPhotoExtension != null) {
            Toast.makeText(this, "Turn off the photo effect before using hardware scene presets", Toast.LENGTH_SHORT).show()
            return
        }
        if (videoMode) {
            Toast.makeText(this, "Scene presets are available in Photo mode only", Toast.LENGTH_SHORT).show()
            return
        }
        if (supportedSceneModes.size <= 1) {
            Toast.makeText(this, "Portrait/Night scene modes are not exposed by this camera", Toast.LENGTH_SHORT).show()
            return
        }
        if (proModeEnabled) {
            Toast.makeText(this, "Turn off Pro controls before changing scene mode", Toast.LENGTH_SHORT).show()
            return
        }
        sceneModeIndex = (sceneModeIndex + 1) % supportedSceneModes.size
        val mode = supportedSceneModes[sceneModeIndex]
        try {
            val activeCamera = camera ?: return
            val builder = CaptureRequestOptions.Builder()
            if (mode == CaptureRequest.CONTROL_SCENE_MODE_DISABLED) {
                builder.setCaptureRequestOption(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                builder.setCaptureRequestOption(CaptureRequest.CONTROL_SCENE_MODE, CaptureRequest.CONTROL_SCENE_MODE_DISABLED)
            } else {
                builder.setCaptureRequestOption(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_USE_SCENE_MODE)
                builder.setCaptureRequestOption(CaptureRequest.CONTROL_SCENE_MODE, mode)
            }
            Camera2CameraControl.from(activeCamera.cameraControl).setCaptureRequestOptions(builder.build())
            updateSceneModeLabel()
        } catch (e: Exception) {
            Toast.makeText(this, "Scene mode unavailable: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateSceneModeLabel() {
        if (!::sceneModeButton.isInitialized) return
        val mode = supportedSceneModes.getOrElse(sceneModeIndex) { CaptureRequest.CONTROL_SCENE_MODE_DISABLED }
        val label = when (mode) {
            CaptureRequest.CONTROL_SCENE_MODE_PORTRAIT -> "PORTRAIT (HW)"
            CaptureRequest.CONTROL_SCENE_MODE_NIGHT -> "NIGHT (HW)"
            CaptureRequest.CONTROL_SCENE_MODE_HDR -> "HDR (HW)"
            else -> "AUTO"
        }
        sceneModeButton.text = "SCENE: $label"
        sceneModeButton.contentDescription = "Camera hardware scene preset: $label. HDR and Night depend on Motorola camera HAL support; this is not a custom multi-frame pipeline."
        sceneModeButton.isEnabled = supportedSceneModes.size > 1
    }

    private fun resetExposureCompensation() {
        val activeCamera = camera ?: return
        val range = activeCamera.cameraInfo.exposureState.exposureCompensationRange
        exposureIndex = 0.coerceIn(range.lower, range.upper)
        activeCamera.cameraControl.setExposureCompensationIndex(exposureIndex)
        exposureLabel.text = "EV  0.0"
        Toast.makeText(this, "Exposure reset to 0 EV", Toast.LENGTH_SHORT).show()
    }

    private fun changeExposure(delta: Int) {
        val activeCamera = camera ?: return
        val range = activeCamera.cameraInfo.exposureState.exposureCompensationRange
        exposureIndex = (exposureIndex + delta).coerceIn(range.lower, range.upper)
        activeCamera.cameraControl.setExposureCompensationIndex(exposureIndex)
        val step = activeCamera.cameraInfo.exposureState.exposureCompensationStep
        val evStops = exposureIndex * step.numerator.toFloat() / step.denominator.toFloat()
        exposureLabel.text = "EV  ${if (evStops > 0) "+" else ""}%.1f".format(Locale.US, evStops)
    }

    private fun cyclePhotoExtension() {
        if (videoMode) {
            Toast.makeText(this, "Photo effects are available in Photo mode only", Toast.LENGTH_SHORT).show()
            return
        }
        if (proModeEnabled) {
            Toast.makeText(this, "Turn off Pro controls before enabling a photo effect", Toast.LENGTH_SHORT).show()
            return
        }
        if (!extensionsInitialized) {
            Toast.makeText(this, "Checking manufacturer camera effects…", Toast.LENGTH_SHORT).show()
            return
        }
        if (supportedPhotoExtensions.isEmpty()) {
            Toast.makeText(this, "No OEM Bokeh, Night or HDR extension is exposed for this camera", Toast.LENGTH_LONG).show()
            return
        }
        val currentIndex = selectedPhotoExtension?.let { supportedPhotoExtensions.indexOf(it) + 1 } ?: 0
        selectedPhotoExtension = if (currentIndex >= supportedPhotoExtensions.size) null else supportedPhotoExtensions[currentIndex]
        updatePhotoExtensionLabel()
        startCamera()
    }

    private fun updatePhotoExtensionLabel() {
        if (!::photoExtensionButton.isInitialized) return
        val label = when {
            !extensionsInitialized -> "CHECKING…"
            supportedPhotoExtensions.isEmpty() -> "NOT AVAILABLE"
            selectedPhotoExtension == ExtensionMode.BOKEH -> "BOKEH"
            selectedPhotoExtension == ExtensionMode.NIGHT -> "NIGHT"
            selectedPhotoExtension == ExtensionMode.HDR -> "HDR"
            else -> "OFF"
        }
        photoExtensionButton.text = "PHOTO EFFECT: $label"
        photoExtensionButton.contentDescription = when {
            !extensionsInitialized -> "Checking manufacturer camera effects"
            supportedPhotoExtensions.isEmpty() -> "This camera does not expose Bokeh, Night or HDR extensions"
            selectedPhotoExtension == ExtensionMode.BOKEH -> "OEM camera Bokeh extension enabled"
            selectedPhotoExtension == ExtensionMode.NIGHT -> "OEM camera Night extension enabled"
            selectedPhotoExtension == ExtensionMode.HDR -> "OEM camera HDR extension enabled"
            else -> "OEM camera effects disabled; tap to choose an available effect"
        }
        photoExtensionButton.isEnabled = extensionsInitialized && supportedPhotoExtensions.isNotEmpty() && !videoMode
    }

    private fun refreshPhotoExtensions() {
        val manager = extensionsManager
        if (manager == null) {
            supportedPhotoExtensions.clear()
            updatePhotoExtensionLabel()
            return
        }
        supportedPhotoExtensions = listOf(ExtensionMode.BOKEH, ExtensionMode.NIGHT, ExtensionMode.HDR)
            .filter { mode ->
                try { manager.isExtensionAvailable(cameraSelector, mode) } catch (_: Exception) { false }
            }.toMutableList()
        if (selectedPhotoExtension != null && !supportedPhotoExtensions.contains(selectedPhotoExtension)) {
            selectedPhotoExtension = null
        }
        updatePhotoExtensionLabel()
    }

    private fun initializePhotoExtensions(provider: ProcessCameraProvider) {
        if (extensionsInitialized || extensionsManager != null) return
        try {
            val future = ExtensionsManager.getInstanceAsync(applicationContext, provider)
            future.addListener({
                try {
                    extensionsManager = future.get()
                    extensionsInitialized = true
                    refreshPhotoExtensions()
                } catch (_: Exception) {
                    extensionsInitialized = true
                    supportedPhotoExtensions.clear()
                    updatePhotoExtensionLabel()
                }
            }, ContextCompat.getMainExecutor(this))
        } catch (_: Exception) {
            extensionsInitialized = true
            supportedPhotoExtensions.clear()
            updatePhotoExtensionLabel()
        }
    }

    // Ask the device camera HAL for its best supported still-image noise/detail
    // processing. Unsupported modes are never forced, and OEM extensions keep
    // ownership of their own Night/HDR processing pipeline.
    private fun applyStillImageQualityOptions(builder: ImageCapture.Builder) {
        if (selectedPhotoExtension != null) return
        try {
            val manager = getSystemService(CAMERA_SERVICE) as CameraManager
            val desiredFacing = if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) {
                CameraCharacteristics.LENS_FACING_FRONT
            } else {
                CameraCharacteristics.LENS_FACING_BACK
            }
            val cameraIds = manager.cameraIdList.filter { id ->
                manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == desiredFacing
            }
            // Prefer the logical multi-camera ID so its reported capabilities and
            // physical-lens group match the camera currently bound by CameraX.
            val cameraId = cameraIds.firstOrNull { id ->
                val chars = manager.getCameraCharacteristics(id)
                val caps = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
                caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA) &&
                    chars.physicalCameraIds.isNotEmpty()
            } ?: cameraIds.firstOrNull() ?: return
            val characteristics = manager.getCameraCharacteristics(cameraId)
            val noiseModes = characteristics.get(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES)
                ?: intArrayOf()
            val hotPixelModes = characteristics.get(CameraCharacteristics.HOT_PIXEL_AVAILABLE_HOT_PIXEL_MODES)
                ?: intArrayOf()
            val shadingModes = characteristics.get(CameraCharacteristics.SHADING_AVAILABLE_MODES)
                ?: intArrayOf()
            val aberrationModes = characteristics.get(CameraCharacteristics.COLOR_CORRECTION_AVAILABLE_ABERRATION_MODES)
                ?: intArrayOf()
            val extender = Camera2Interop.Extender(builder)
            // Request only image-cleanup modes that this camera HAL explicitly advertises.
            // Keep edge enhancement at the OEM default to avoid sharpening noise and halos.
            if (noiseModes.contains(CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY)) {
                extender.setCaptureRequestOption(
                    CaptureRequest.NOISE_REDUCTION_MODE,
                    CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY
                )
            }
            if (hotPixelModes.contains(CaptureRequest.HOT_PIXEL_MODE_HIGH_QUALITY)) {
                extender.setCaptureRequestOption(
                    CaptureRequest.HOT_PIXEL_MODE,
                    CaptureRequest.HOT_PIXEL_MODE_HIGH_QUALITY
                )
            }
            if (shadingModes.contains(CaptureRequest.SHADING_MODE_HIGH_QUALITY)) {
                extender.setCaptureRequestOption(
                    CaptureRequest.SHADING_MODE,
                    CaptureRequest.SHADING_MODE_HIGH_QUALITY
                )
            }
            if (aberrationModes.contains(CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY)) {
                extender.setCaptureRequestOption(
                    CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE,
                    CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY
                )
            }
        } catch (_: Exception) {
            // Fall back to the camera vendor's default still-image pipeline.
        }
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    @OptIn(ExperimentalAudioApi::class)
    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            initializePhotoExtensions(provider)
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            try {
                provider.unbindAll()
                val activeSelector = if (!videoMode && selectedPhotoExtension != null) {
                    val manager = extensionsManager
                    if (manager == null) cameraSelector else manager.getExtensionEnabledCameraSelector(cameraSelector, selectedPhotoExtension!!)
                } else cameraSelector
                if (videoMode) {
                    imageCapture = null
                    val recorderBuilder = Recorder.Builder()
                        .setQualitySelector(QualitySelector.from(videoQuality, FallbackStrategy.lowerQualityOrHigherThan(Quality.HD)))
                    if (micNoiseReductionEnabled) {
                        recorderBuilder.setAudioProcessors(listOf(VoiceNoiseReductionProcessor()))
                    }
                    val recorder = recorderBuilder.build()
                    videoCapture = VideoCapture.withOutput(recorder)
                    camera = provider.bindToLifecycle(this, cameraSelector, preview, videoCapture)
                    updateVideoCapabilities()
                    photoExtensionButton.isEnabled = false
                } else {
                    videoCapture = null
                    val photoResolution = ResolutionSelector.Builder()
                        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                        .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                        .build()
                    val imageBuilder = ImageCapture.Builder()
                        .setResolutionSelector(photoResolution)
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .setJpegQuality(jpegQuality)
                        .setFlashMode(photoFlashMode)
                    applyStillImageQualityOptions(imageBuilder)
                    imageCapture = imageBuilder.build()
                    camera = provider.bindToLifecycle(this, activeSelector, preview, imageCapture)
                    refreshPhotoExtensions()
                }
                camera?.cameraInfo?.zoomState?.observe(this) { state ->
                    zoomLabel.text = "ZOOM  %.1fx".format(Locale.US, state.zoomRatio)
                    val span = kotlin.math.ln(state.maxZoomRatio / state.minZoomRatio)
                    val fraction = if (span > 0f && span.isFinite())
                        (kotlin.math.ln(state.zoomRatio / state.minZoomRatio) / span).coerceIn(0f, 1f)
                    else 0f
                    zoomSlider.progress = (fraction * zoomSlider.max).toInt()
                    updateLensButtonAvailability(state.minZoomRatio, state.maxZoomRatio)
                    updateLensSelection(state.zoomRatio)
                }
                updateManualRanges()
                if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) discoverRearLenses() else {
                    lensRow.removeAllViews()
                    lensRow.addView(TextView(this).apply { text = "FRONT CAMERA"; textSize = 10f; setTextColor(0xFFCCCCCC.toInt()); gravity = Gravity.CENTER })
                }
                camera?.cameraInfo?.zoomState?.value?.let { updateLensSelection(it.zoomRatio) }
                updateScreenFlashPreview()
                exposureIndex = 0
                exposureLabel.text = "EV  0"
            } catch (e: Exception) {
                if (selectedPhotoExtension != null) {
                    selectedPhotoExtension = null
                    updatePhotoExtensionLabel()
                    Toast.makeText(this, "OEM photo effect could not start; returning to standard camera", Toast.LENGTH_LONG).show()
                    try {
                        provider.unbindAll()
                        val fallbackPreview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                        val fallbackImageBuilder = ImageCapture.Builder()
                            .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY).setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY).build())
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                            .setJpegQuality(jpegQuality)
                            .setFlashMode(photoFlashMode)
                        applyStillImageQualityOptions(fallbackImageBuilder)
                        imageCapture = fallbackImageBuilder.build()
                        camera = provider.bindToLifecycle(this, cameraSelector, fallbackPreview, imageCapture)
                        updateManualRanges()
                    } catch (fallback: Exception) {
                        Toast.makeText(this, "Unable to start camera: ${fallback.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(this, "Unable to start camera: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun updateManualRanges() {
        try {
            val manager = getSystemService(CAMERA_SERVICE) as CameraManager
            val wantedFacing = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA)
                CameraCharacteristics.LENS_FACING_BACK else CameraCharacteristics.LENS_FACING_FRONT
            val id = findCameraIdForFacing(manager, wantedFacing) ?: return
            val characteristics = manager.getCameraCharacteristics(id)
            minimumFocusDistance = characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
            manualFocusEnabled = manualFocusProgress > 0 && minimumFocusDistance > 0f
            focusSlider.isEnabled = minimumFocusDistance > 0f
            focusSlider.progress = if (minimumFocusDistance > 0f) manualFocusProgress else 0
            manualFocusDiopters = minimumFocusDistance * (focusSlider.progress / 1000f)
            focusValue.text = if (minimumFocusDistance > 0f && manualFocusProgress > 0) "FOCUS  %.2f D".format(Locale.US, manualFocusDiopters) else if (minimumFocusDistance > 0f) "FOCUS  AUTO  (0–%.1f D)".format(Locale.US, minimumFocusDistance) else "FOCUS  AUTO (FIXED FOCUS)"
            supportedAwbModes = (characteristics.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)
                ?: intArrayOf(CaptureRequest.CONTROL_AWB_MODE_AUTO))
                .filter { it in CaptureRequest.CONTROL_AWB_MODE_AUTO..CaptureRequest.CONTROL_AWB_MODE_SHADE }
                .toIntArray()
                .let { modes -> if (modes.contains(CaptureRequest.CONTROL_AWB_MODE_AUTO)) modes else intArrayOf(CaptureRequest.CONTROL_AWB_MODE_AUTO) + modes }
            awbModeIndex = supportedAwbModes.indexOf(savedAwbMode).takeIf { it >= 0 } ?: 0
            savedAwbMode = supportedAwbModes[awbModeIndex]
            whiteBalanceButton.text = "WHITE BALANCE: ${when (savedAwbMode) { CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT -> "INCANDESCENT"; CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT -> "FLUORESCENT"; CaptureRequest.CONTROL_AWB_MODE_WARM_FLUORESCENT -> "WARM FLUORESCENT"; CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT -> "DAYLIGHT"; CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT -> "CLOUDY"; CaptureRequest.CONTROL_AWB_MODE_TWILIGHT -> "TWILIGHT"; CaptureRequest.CONTROL_AWB_MODE_SHADE -> "SHADE"; else -> "AUTO" }}"
            val availableScenes = characteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES)
                ?: intArrayOf(CaptureRequest.CONTROL_SCENE_MODE_DISABLED)
            val preferredScenes = listOf(
                CaptureRequest.CONTROL_SCENE_MODE_DISABLED,
                CaptureRequest.CONTROL_SCENE_MODE_PORTRAIT,
                CaptureRequest.CONTROL_SCENE_MODE_NIGHT,
                CaptureRequest.CONTROL_SCENE_MODE_HDR
            )
            supportedSceneModes = preferredScenes.filter {
                it == CaptureRequest.CONTROL_SCENE_MODE_DISABLED || availableScenes.contains(it)
            }.toIntArray()
            sceneModeIndex = 0
            updateSceneModeLabel()
            characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)?.let {
                activeIsoMin = it.lower
                activeIsoMax = it.upper
            }
            characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)?.let {
                activeShutterMinNs = it.lower.coerceAtLeast(1L)
                activeShutterMaxNs = it.upper.coerceAtLeast(activeShutterMinNs)
            }
            manualIso = manualIso.coerceIn(activeIsoMin, activeIsoMax)
            manualShutterNs = manualShutterNs.coerceIn(activeShutterMinNs, activeShutterMaxNs)
            isoValue.text = "ISO $manualIso  ($activeIsoMin–$activeIsoMax)"
            shutterValue.text = "SHUTTER ${formatShutter(manualShutterNs)}  (${formatShutter(activeShutterMinNs)}–${formatShutter(activeShutterMaxNs)})"
            isoSlider.progress = (((manualIso - activeIsoMin).toFloat() / (activeIsoMax - activeIsoMin).coerceAtLeast(1)) * 100).toInt().coerceIn(0, 100)
            shutterSlider.progress = shutterProgressFromNs(manualShutterNs)
            if (proModeEnabled && selectedPhotoExtension == null && !videoMode) applyManualCapture()
        } catch (e: Exception) {
            Toast.makeText(this, "Could not read camera manual ranges", Toast.LENGTH_SHORT).show()
        }
    }
    // Logarithmic shutter mapping gives useful precision across microseconds to milliseconds.
    private fun shutterNsFromProgress(progress: Int): Long {
        val min = activeShutterMinNs.toDouble()
        val max = activeShutterMaxNs.toDouble().coerceAtLeast(min)
        if (max <= min) return min.toLong()
        val fraction = progress.coerceIn(0, 1000) / 1000.0
        return (min * (max / min).pow(fraction)).toLong().coerceIn(activeShutterMinNs, activeShutterMaxNs)
    }

    private fun shutterProgressFromNs(value: Long): Int {
        val min = activeShutterMinNs.toDouble()
        val max = activeShutterMaxNs.toDouble().coerceAtLeast(min)
        if (max <= min) return 0
        val fraction = kotlin.math.ln(value.coerceIn(activeShutterMinNs, activeShutterMaxNs) / min) /
            kotlin.math.ln(max / min)
        return (fraction * 1000.0).toInt().coerceIn(0, 1000)
    }

    private fun formatShutter(valueNs: Long): String =
        if (valueNs < 1_000_000L) "${"%.0f".format(Locale.US, valueNs / 1000.0)} µs"
        else "${"%.2f".format(Locale.US, valueNs / 1_000_000.0)} ms"

    /**
     * RAW capture uses a short-lived Camera2 still session because CameraX ImageCapture
     * outputs JPEG/YUV only. CameraX is unbound for the RAW shot and rebound afterwards.
     */
    private fun captureRawDng(trigger: Button) {
        val manager = getSystemService(CAMERA_SERVICE) as CameraManager
        val facing = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA)
            CameraCharacteristics.LENS_FACING_BACK else CameraCharacteristics.LENS_FACING_FRONT
        val cameraId = findCameraIdForFacing(manager, facing)
        if (cameraId == null) {
            trigger.isEnabled = true
            trigger.text = "CAPTURE RAW / DNG"
            Toast.makeText(this, "Camera not available", Toast.LENGTH_LONG).show()
            return
        }

        val characteristics = manager.getCameraCharacteristics(cameraId)
        val capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
        val rawSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?.getOutputSizes(ImageFormat.RAW_SENSOR)
        if (!capabilities.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW) || rawSizes.isNullOrEmpty()) {
            trigger.isEnabled = true
            trigger.text = "CAPTURE RAW / DNG"
            Toast.makeText(this, "RAW/DNG is not exposed by this camera", Toast.LENGTH_LONG).show()
            return
        }
        val rawSize = rawSizes.maxByOrNull { it.width.toLong() * it.height } ?: run {
            trigger.isEnabled = true
            trigger.text = "CAPTURE RAW / DNG"
            return
        }

        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = try {
                providerFuture.get().also { it.unbindAll() }
            } catch (e: Exception) {
                runOnUiThread {
                    trigger.isEnabled = true
                    trigger.text = "CAPTURE RAW / DNG"
                    Toast.makeText(this, "RAW camera setup failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    startCamera()
                }
                return@addListener
            }

            var device: CameraDevice? = null
            var session: CameraCaptureSession? = null
            var reader: ImageReader? = null
            var rawImage: Image? = null
            var captureResult: TotalCaptureResult? = null
            var finished = false
            val callbackHandler = Handler(Looper.getMainLooper())

            fun restore(message: String?, success: Boolean = false) {
                if (finished) return
                finished = true
                try { rawImage?.close() } catch (_: Exception) { }
                try { session?.close() } catch (_: Exception) { }
                try { device?.close() } catch (_: Exception) { }
                try { reader?.close() } catch (_: Exception) { }
                runOnUiThread {
                    trigger.isEnabled = true
                    trigger.text = "CAPTURE RAW / DNG"
                    if (message != null) Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    startCamera()
                }
            }

            fun saveWhenReady() {
                val image = rawImage ?: return
                val result = captureResult ?: return
                if (finished) return
                finished = true
                Thread {
                    var savedUri: Uri? = null
                    var error: String? = null
                    try {
                        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                        val values = ContentValues().apply {
                            put(MediaStore.Images.Media.DISPLAY_NAME, "Signature_RAW_$stamp.dng")
                            put(MediaStore.Images.Media.MIME_TYPE, "image/x-adobe-dng")
                            if (android.os.Build.VERSION.SDK_INT >= 29) {
                                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SignatureCameraPro")
                            }
                        }
                        savedUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                            ?: throw IllegalStateException("Could not create Gallery entry")
                        val output = contentResolver.openOutputStream(savedUri!!)
                            ?: throw IllegalStateException("Could not open DNG output")
                        output.use { stream ->
                            val creator = DngCreator(characteristics, result)
                            try { creator.writeImage(stream, image) } finally { creator.close() }
                        }
                    } catch (e: Exception) {
                        error = e.localizedMessage ?: "DNG write failed"
                        savedUri?.let { contentResolver.delete(it, null, null) }
                    } finally {
                        try { image.close() } catch (_: Exception) { }
                        try { session?.close() } catch (_: Exception) { }
                        try { device?.close() } catch (_: Exception) { }
                        try { reader?.close() } catch (_: Exception) { }
                    }
                    runOnUiThread {
                        trigger.isEnabled = true
                        trigger.text = "CAPTURE RAW / DNG"
                        Toast.makeText(this, if (error == null) "RAW DNG saved to Gallery" else "RAW failed: $error", Toast.LENGTH_LONG).show()
                        startCamera()
                    }
                }.start()
            }

            try {
                reader = ImageReader.newInstance(rawSize.width, rawSize.height, ImageFormat.RAW_SENSOR, 2)
                reader!!.setOnImageAvailableListener({ source ->
                    val next = source.acquireNextImage() ?: return@setOnImageAvailableListener
                    if (rawImage == null) rawImage = next else next.close()
                    saveWhenReady()
                }, callbackHandler)

                manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                    override fun onOpened(opened: CameraDevice) {
                        if (finished) { opened.close(); return }
                        device = opened
                        try {
                            opened.createCaptureSession(listOf(reader!!.surface), object : CameraCaptureSession.StateCallback() {
                                override fun onConfigured(configured: CameraCaptureSession) {
                                    if (finished) { configured.close(); return }
                                    session = configured
                                    try {
                                        val request = opened.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                                            addTarget(reader!!.surface)
                                            set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                                            set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                                        }.build()
                                        configured.capture(request, object : CameraCaptureSession.CaptureCallback() {
                                            override fun onCaptureCompleted(
                                                session: CameraCaptureSession,
                                                request: CaptureRequest,
                                                result: TotalCaptureResult
                                            ) {
                                                captureResult = result
                                                saveWhenReady()
                                            }
                                            override fun onCaptureFailed(
                                                session: CameraCaptureSession,
                                                request: CaptureRequest,
                                                failure: android.hardware.camera2.CaptureFailure
                                            ) {
                                                restore("RAW capture failed")
                                            }
                                        }, callbackHandler)
                                    } catch (e: Exception) {
                                        restore("RAW capture error: ${e.localizedMessage}")
                                    }
                                }
                                override fun onConfigureFailed(failed: CameraCaptureSession) {
                                    restore("RAW camera session could not be configured")
                                }
                            }, callbackHandler)
                        } catch (e: Exception) {
                            restore("RAW session error: ${e.localizedMessage}")
                        }
                    }
                    override fun onDisconnected(disconnected: CameraDevice) {
                        disconnected.close()
                        restore("Camera disconnected during RAW capture")
                    }
                    override fun onError(failed: CameraDevice, errorCode: Int) {
                        failed.close()
                        restore("Camera2 error: $errorCode")
                    }
                }, callbackHandler)
            } catch (e: Exception) {
                restore("RAW capture unavailable: ${e.localizedMessage}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun showRecentCaptures() {
        data class CaptureItem(val title: String, val uri: Uri, val mime: String, val added: Long)
        val items = mutableListOf<CaptureItem>()
        val sources = listOf(
            Triple(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaStore.Images.Media.DISPLAY_NAME, "image"),
            Triple(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, MediaStore.Video.Media.DISPLAY_NAME, "video")
        )
        try {
            sources.forEach { (collection, nameColumn, kind) ->
                val projection = arrayOf(
                    MediaStore.MediaColumns._ID,
                    nameColumn,
                    MediaStore.MediaColumns.DATE_ADDED,
                    MediaStore.MediaColumns.MIME_TYPE
                )
                contentResolver.query(
                    collection, projection, "${nameColumn} LIKE ?", arrayOf("Signature_%"),
                    "${MediaStore.MediaColumns.DATE_ADDED} DESC"
                )?.use { cursor ->
                    val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val nameIndex = cursor.getColumnIndexOrThrow(nameColumn)
                    val dateIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                    val mimeIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idIndex)
                        val uri = Uri.withAppendedPath(collection, id.toString())
                        val mime = cursor.getString(mimeIndex) ?: if (kind == "image") "image/jpeg" else "video/mp4"
                        items.add(CaptureItem(cursor.getString(nameIndex) ?: "Capture", uri, mime, cursor.getLong(dateIndex)))
                    }
                }
            }
            val recent = items.sortedByDescending { it.added }.take(40)
            if (recent.isEmpty()) {
                Toast.makeText(this, "No SignatureCameraPro captures found yet", Toast.LENGTH_SHORT).show()
                return
            }
            val grid = GridView(this).apply {
                numColumns = 3
                verticalSpacing = 8
                horizontalSpacing = 8
                stretchMode = GridView.STRETCH_COLUMN_WIDTH
                setPadding(10, 10, 10, 10)
                adapter = object : BaseAdapter() {
                    override fun getCount() = recent.size
                    override fun getItem(position: Int) = recent[position]
                    override fun getItemId(position: Int) = position.toLong()
                    override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                        val item = getItem(position)
                        val cell = (convertView as? LinearLayout) ?: LinearLayout(this@MainActivity).apply {
                            orientation = LinearLayout.VERTICAL
                            gravity = Gravity.CENTER
                            setPadding(4, 4, 4, 8)
                            background = GradientDrawable().apply {
                                cornerRadius = 12f
                                setColor(0xFF202B3B.toInt())
                            }
                            addView(ImageView(this@MainActivity).apply {
                                layoutParams = LinearLayout.LayoutParams(-1, 112)
                                scaleType = ImageView.ScaleType.CENTER_CROP
                                setImageResource(android.R.drawable.ic_menu_gallery)
                            })
                            addView(TextView(this@MainActivity).apply {
                                textSize = 10f
                                maxLines = 2
                                gravity = Gravity.CENTER
                                setTextColor(android.graphics.Color.WHITE)
                                setPadding(3, 5, 3, 0)
                            })
                        }
                        val thumb = cell.getChildAt(0) as ImageView
                        val title = cell.getChildAt(1) as TextView
                        thumb.tag = item.uri
                        thumb.setImageResource(if (item.mime.startsWith("video")) android.R.drawable.ic_media_play else android.R.drawable.ic_menu_gallery)
                        title.text = item.title
                        if (android.os.Build.VERSION.SDK_INT >= 29) {
                            Thread {
                                val bitmap = try { contentResolver.loadThumbnail(item.uri, android.util.Size(320, 320), null) } catch (_: Exception) { null }
                                if (bitmap != null) runOnUiThread {
                                    if (thumb.tag == item.uri) thumb.setImageBitmap(bitmap)
                                }
                            }.start()
                        }
                        return cell
                    }
                }
                setOnItemClickListener { _, _, position, _ ->
                    val item = recent[position]
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(item.uri, item.mime)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                    } catch (_: Exception) {
                        Toast.makeText(this@MainActivity, "No app available to open this file", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            AlertDialog.Builder(this)
                .setTitle("RECENT CAPTURES  •  ${recent.size}")
                .setView(grid)
                .setNegativeButton("CLOSE", null)
                .show()
        } catch (e: Exception) {
            Toast.makeText(this, "Gallery unavailable: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun triggerCaptureAction() {
        if (!::captureButton.isInitialized || !captureButton.isEnabled) return
        if (countdownActive) {
            cancelCaptureCountdown()
            return
        }
        if (videoMode) {
            toggleVideoRecording()
        } else if (timerSeconds == 0) {
            takePhoto()
        } else {
            countdownActive = true
            runCaptureCountdown(timerSeconds)
        }
    }

    private fun cancelCaptureCountdown() {
        countdownRunnable?.let { handler.removeCallbacks(it) }
        countdownRunnable = null
        countdownActive = false
        if (::captureButton.isInitialized) {
            captureButton.text = "●"
            captureButton.contentDescription = "Take photo"
            captureButton.isEnabled = true
        }
        Toast.makeText(this, "Timer cancelled", Toast.LENGTH_SHORT).show()
    }

    private fun runCaptureCountdown(secondsRemaining: Int) {
        if (isFinishing || isDestroyed) {
            countdownActive = false
            return
        }
        if (secondsRemaining <= 0) {
            countdownRunnable = null
            countdownActive = false
            captureButton.text = "●"
            captureButton.contentDescription = "Take photo"
            captureButton.isEnabled = true
            takePhoto()
            return
        }
        captureButton.text = "CANCEL $secondsRemaining"
        captureButton.contentDescription = "Cancel photo timer, $secondsRemaining seconds remaining"
        countdownRunnable = Runnable { runCaptureCountdown(secondsRemaining - 1) }
        handler.postDelayed(countdownRunnable!!, 1000L)
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean {
        if ((keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP ||
                keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN) &&
            event.repeatCount == 0 && ::captureButton.isInitialized) {
            triggerCaptureAction()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun takePhoto() {
        val capture = imageCapture ?: return
        if (::captureButton.isInitialized && !captureButton.isEnabled) return
        if (::captureButton.isInitialized) {
            captureButton.isEnabled = false
            captureButton.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
        }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "Signature_$timestamp.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SignatureCameraPro")
            }
        }
        val options = ImageCapture.OutputFileOptions.Builder(
            contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
        ).build()
        val useScreenFlash = cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA && screenFlashEnabled
        if (useScreenFlash) {
            screenFlashHideRunnable?.let { handler.removeCallbacks(it) }
            screenFlashOverlay.visibility = View.VISIBLE
            screenFlashOverlay.alpha = 1f
            // Allow one display refresh before requesting exposure so the panel
            // is already bright when the front camera captures the frame.
            screenFlashHideRunnable = Runnable { hideScreenFlash() }
            handler.postDelayed(screenFlashHideRunnable!!, 2500L)
        }
        val captureAction = Runnable {
            capture.takePicture(options, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    if (useScreenFlash) hideScreenFlash()
                    if (::captureButton.isInitialized) captureButton.isEnabled = true
                    Toast.makeText(this@MainActivity, "Photo saved to Gallery", Toast.LENGTH_SHORT).show()
                }
                override fun onError(exception: ImageCaptureException) {
                    if (useScreenFlash) hideScreenFlash()
                    if (::captureButton.isInitialized) captureButton.isEnabled = true
                    Toast.makeText(this@MainActivity, "Photo failed: ${exception.message}", Toast.LENGTH_LONG).show()
                }
            })
        }
        if (useScreenFlash) handler.postDelayed(captureAction, 120L) else captureAction.run()
    }

    // A low-level white viewfinder fill gives the front camera preview a
    // usable selfie light; the full-white pulse is reserved for the exposure.
    private fun updateScreenFlashPreview() {
        if (!::screenFlashOverlay.isInitialized) return
        val showPreviewFill = cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA &&
            !videoMode && screenFlashEnabled
        screenFlashOverlay.animate().cancel()
        screenFlashOverlay.alpha = if (showPreviewFill) 0.30f else 0f
        screenFlashOverlay.visibility = if (showPreviewFill) View.VISIBLE else View.GONE
    }

    private fun hideScreenFlash() {
        screenFlashHideRunnable?.let { handler.removeCallbacks(it) }
        screenFlashHideRunnable = null
        updateScreenFlashPreview()
    }

    override fun onStop() {
        hideScreenFlash()
        // Finalize any active recording when the camera screen leaves the foreground.
        if (recording != null) {
            // CameraX owns both audio and video streams and finalizes the combined MP4.
            try { recording?.stop() } catch (_: Exception) { }
        }
        countdownRunnable?.let { handler.removeCallbacks(it) }
        countdownRunnable = null
        countdownActive = false
        if (::captureButton.isInitialized && captureButton.text.toString().startsWith("CANCEL")) {
            captureButton.text = if (videoMode) "● REC" else "●"
            captureButton.contentDescription = if (videoMode) "Start video recording" else "Take photo"
            captureButton.isEnabled = true
        }
        super.onStop()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) startCamera()
            else Toast.makeText(this, "Camera permission is required.", Toast.LENGTH_LONG).show()
        } else if (requestCode == AUDIO_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED && videoMode) toggleVideoRecording()
            else Toast.makeText(this, "Microphone permission denied; video audio cannot be recorded", Toast.LENGTH_LONG).show()
        }
    }

    private class GridOverlay(context: android.content.Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x88FFFFFF.toInt()
            strokeWidth = 1.2f
            style = Paint.Style.STROKE
        }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            canvas.drawLine(w / 3f, 0f, w / 3f, h, paint)
            canvas.drawLine(w * 2f / 3f, 0f, w * 2f / 3f, h, paint)
            canvas.drawLine(0f, h / 3f, w, h / 3f, paint)
            canvas.drawLine(0f, h * 2f / 3f, w, h * 2f / 3f, paint)
        }
    }

    private class FocusOverlay(context: android.content.Context) : View(context) {
        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFD54F.toInt()
            strokeWidth = 3f
            style = Paint.Style.STROKE
        }
        private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFD54F.toInt()
            strokeWidth = 5f
            strokeCap = Paint.Cap.ROUND
            style = Paint.Style.STROKE
        }
        private var focusX = 0f
        private var focusY = 0f
        private var resultColor = 0xFFFFD54F.toInt()

        fun showAt(x: Float, y: Float) {
            focusX = x
            focusY = y
            resultColor = 0xFFFFD54F.toInt()
            ringPaint.color = resultColor
            cornerPaint.color = resultColor
            visibility = View.VISIBLE
            invalidate()
            animate().cancel()
            alpha = 1f
            animate().alpha(0f).setStartDelay(1200).setDuration(300).withEndAction {
                visibility = View.GONE
                alpha = 1f
            }.start()
        }

        fun setFocusResult(success: Boolean) {
            resultColor = if (success) 0xFF69F0AE.toInt() else 0xFFFF5252.toInt()
            ringPaint.color = resultColor
            cornerPaint.color = resultColor
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val radius = 34f
            canvas.drawCircle(focusX, focusY, radius, ringPaint)
            val length = 13f
            canvas.drawLine(focusX - radius, focusY, focusX - radius + length, focusY, cornerPaint)
            canvas.drawLine(focusX + radius - length, focusY, focusX + radius, focusY, cornerPaint)
            canvas.drawLine(focusX, focusY - radius, focusX, focusY - radius + length, cornerPaint)
            canvas.drawLine(focusX, focusY + radius - length, focusX, focusY + radius, cornerPaint)
        }
    }

    companion object {
        private const val CAMERA_PERMISSION_REQUEST = 1001
        private const val AUDIO_PERMISSION_REQUEST = 1002
    }
}
