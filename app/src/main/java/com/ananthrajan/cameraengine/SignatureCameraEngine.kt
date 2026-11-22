package com.ananthrajan.cameraengine

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
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import android.hardware.camera2.CameraManager
import android.graphics.ImageFormat
import android.content.ContentValues
import android.content.Intent
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
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
import androidx.camera.core.AspectRatio
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.MeteringPoint
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.core.UseCaseGroup
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

enum class CameraThemeStyle { IPHONE_PRO, SAMSUNG_ONE_UI }

abstract class BaseSignatureCameraActivity : ComponentActivity() {
    abstract val themeStyle: CameraThemeStyle
    abstract val isProModeEnabled: Boolean

    private val accentColor: Int
        get() = if (themeStyle == CameraThemeStyle.IPHONE_PRO) 0xFFFFD60A.toInt() else 0xFF4C8DFF.toInt()
    private val accentBorderColor: Int
        get() = if (themeStyle == CameraThemeStyle.IPHONE_PRO) 0x44FFD60A else 0x664C8DFF.toInt()
    private val cameraXController = CameraXController()
    private lateinit var previewView: PreviewView
    private lateinit var gridView: GridOverlay
    private lateinit var focusOverlay: FocusOverlay
    private lateinit var zoomLabel: TextView
    private lateinit var zoomSlider: SeekBar
    private lateinit var zoomRulerView: ZoomRulerView
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
    private lateinit var photoRatioRow: LinearLayout
    private lateinit var photoRatioLabel: TextView
    private lateinit var videoRatioRow: LinearLayout
    private lateinit var photoRatioButtons: List<Button>
    private lateinit var videoRatioButtons: List<Button>
    private lateinit var jpegQualityButton: Button
    private lateinit var settingsToggleButton: Button
    private lateinit var settingsDrawer: ScrollView
    private lateinit var quickControlsPanel: View
    private lateinit var ratioQuickButton: Button
    private lateinit var modeQuickButton: Button
    private lateinit var mainControlsRow: LinearLayout
    private lateinit var dockHint: TextView
    private lateinit var captureButton: Button
    private lateinit var galleryThumbnailView: ImageView
    private lateinit var flashButton: Button
    private lateinit var flashStatusLabel: TextView
    private lateinit var topChrome: LinearLayout
    private lateinit var cameraDockPanel: LinearLayout
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
    private var ultraHdrCaptureEnabled = false
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
    private var viewportRetryCount = 0
    private var cameraStartGeneration = 0L
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
        cameraXController.updateState(cameraXController.uiState.copy(
            photoAspectRatio = when (cameraPrefs.getString("photo_aspect_ratio", "RATIO_4_3")) {
                "RATIO_16_9" -> PhotoAspectRatio.RATIO_16_9
                "FULL" -> PhotoAspectRatio.FULL
                else -> PhotoAspectRatio.RATIO_4_3
            },
            videoAspectRatio = when (cameraPrefs.getString("video_aspect_ratio", "RATIO_16_9")) {
                "RATIO_4_3" -> VideoAspectRatio.RATIO_4_3
                else -> VideoAspectRatio.RATIO_16_9
            }
        ))
        manualIso = cameraPrefs.getInt("manual_iso", 400).coerceIn(50, 51200)
        manualShutterNs = cameraPrefs.getLong("manual_shutter_ns", 10_000_000L).coerceIn(1_000L, 1_000_000_000L)
        manualFocusProgress = cameraPrefs.getInt("manual_focus_progress", 0).coerceIn(0, 1000)
        savedAwbMode = cameraPrefs.getInt("manual_awb_mode", CaptureRequest.CONTROL_AWB_MODE_AUTO)
        buildCameraUi()
        // Wait for PreviewView measurement so its ViewPort is available before
        // binding Preview + ImageCapture into a shared crop group.
        if (hasCameraPermission()) previewView.post { updatePreviewAspectRatioFrame { startCamera() } }
        else requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    // Match the viewfinder bounds to the selected capture ratio so the
    // PreviewView ViewPort and saved output share the same framing.
    private fun updatePreviewAspectRatioFrame(afterLayout: (() -> Unit)? = null) {
        if (!::previewView.isInitialized) {
            afterLayout?.invoke()
            return
        }
        val root = previewView.parent as? FrameLayout
        if (root == null) {
            afterLayout?.invoke()
            return
        }
        root.post {
            val rootWidth = root.width
            val rootHeight = root.height
            if (rootWidth <= 0 || rootHeight <= 0) {
                previewView.post { updatePreviewAspectRatioFrame(afterLayout) }
                return@post
            }

            // FULL uses the entire viewfinder as its crop target. FIT_CENTER
            // letterboxes the front sensor inside this tall frame; fill the shared
            // ViewPort in FULL so preview and still capture use the same framing.
            previewView.scaleType = if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA &&
                (videoMode || cameraXController.uiState.photoAspectRatio != PhotoAspectRatio.FULL)) {
                PreviewView.ScaleType.FIT_CENTER
            } else {
                PreviewView.ScaleType.FILL_CENTER
            }

            val captureAspect = if (videoMode) {
                if (cameraXController.uiState.videoAspectRatio == VideoAspectRatio.RATIO_4_3) 4f / 3f else 16f / 9f
            } else {
                when (cameraXController.uiState.photoAspectRatio) {
                    PhotoAspectRatio.RATIO_4_3, PhotoAspectRatio.FULL -> 4f / 3f
                    PhotoAspectRatio.RATIO_16_9 -> 16f / 9f
                }
            }
            val isPortrait = rootHeight >= rootWidth
            val frameAspect = if (isPortrait) 1f / captureAspect else captureAspect
            val fullFrame = !videoMode && cameraXController.uiState.photoAspectRatio == PhotoAspectRatio.FULL
            val frameWidth: Int
            val frameHeight: Int
            if (fullFrame) {
                frameWidth = rootWidth
                frameHeight = rootHeight
            } else if (frameAspect >= rootWidth.toFloat() / rootHeight.toFloat()) {
                frameWidth = rootWidth
                frameHeight = (rootWidth / frameAspect).toInt().coerceAtLeast(1)
            } else {
                frameHeight = rootHeight
                frameWidth = (rootHeight * frameAspect).toInt().coerceAtLeast(1)
            }

            fun applyFrame(view: View) {
                val params = view.layoutParams as? FrameLayout.LayoutParams
                    ?: FrameLayout.LayoutParams(frameWidth, frameHeight, Gravity.CENTER)
                params.width = frameWidth
                params.height = frameHeight
                params.gravity = Gravity.CENTER
                view.layoutParams = params
            }
            applyFrame(previewView)
            if (::gridView.isInitialized) applyFrame(gridView)
            if (::focusOverlay.isInitialized) applyFrame(focusOverlay)
            if (::screenFlashOverlay.isInitialized) applyFrame(screenFlashOverlay)
            previewView.post { afterLayout?.invoke() }
        }
    }

    private fun buildCameraUi() {
        val root = FrameLayout(this).apply { setBackgroundColor(android.graphics.Color.BLACK) }
        previewView = PreviewView(this).apply {
            scaleType = if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA)
                PreviewView.ScaleType.FIT_CENTER else PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
        root.addView(previewView, FrameLayout.LayoutParams(-1, -1))
        gridView = GridOverlay(this, accentColor)
        gridView.visibility = if (gridEnabled) View.VISIBLE else View.GONE
        root.addView(gridView, FrameLayout.LayoutParams(-1, -1))
        focusOverlay = FocusOverlay(this, accentColor).apply {
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
                    cameraXController.setZoomRatio(ratio)
                    zoomLabel.text = "ZOOM  %.1fx".format(Locale.US, ratio)
                    updateLensSelection(ratio)
                    if (::zoomRulerView.isInitialized) zoomRulerView.setProgress(zoomProgressForRatio(ratio, state.minZoomRatio, state.maxZoomRatio))
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
                    cameraXController.setZoomRatio(ratio)
                    zoomLabel.text = "ZOOM  %.1fx".format(Locale.US, ratio)
                    updateLensSelection(ratio)
                    if (::zoomRulerView.isInitialized) zoomRulerView.setProgress(zoomProgressForRatio(ratio, state.minZoomRatio, state.maxZoomRatio))
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
            setPadding(dp(16), dp(12), dp(16), dp(8))
            // iPhone uses a translucent top chrome; Samsung keeps a denser black control rail.
            setBackgroundColor(
                if (themeStyle == CameraThemeStyle.IPHONE_PRO) 0x66000000
                else android.graphics.Color.BLACK
            )
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
            textSize = 13f
            letterSpacing = 0.04f
            setTextColor(accentColor)
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(2))
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
                    cameraXController.setZoomRatio(ratio)
                    zoomLabel.text = "ZOOM  %.1fx".format(Locale.US, ratio)
                    updateLensSelection(ratio)
                    if (::zoomRulerView.isInitialized) zoomRulerView.setProgress(progress)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        zoomRulerView = ZoomRulerView(this, accentColor) { progress ->
            zoomSlider.progress = progress
            zoomSlider.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
        }
        audioLevelLabel = TextView(this).apply {
            text = "MIC  •  READY"
            textSize = 11f
            letterSpacing = 0.08f
            setTextColor(0xFF9E9EA4.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(3), 0, dp(2))
            contentDescription = "Microphone recording level"
        }
        // Compact iPhone-inspired status strip: keep the live viewfinder dominant.
        // Keep the left side as breathing room; the camera UI should not carry
        // a branding/status banner across the live viewfinder.
        title.text = ""
        title.textSize = 11f
        title.letterSpacing = 0.28f
        title.alpha = 0.90f
        val topHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(2))
        }
        topHeader.addView(title, LinearLayout.LayoutParams(0, dp(42), 1f))
        top.addView(topHeader)
        // Zoom is shown by the lens/zoom rail above the shutter, not as a
        // duplicate status line under the top controls.
        zoomLabel.visibility = View.GONE
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
            progressTintList = ColorStateList.valueOf(0xFF30D158.toInt())
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
                cornerRadius = dp(24).toFloat()
                setColor(0xFF141416.toInt())
                setStroke(dp(1), 0xFF303034.toInt())
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
            setPadding(dp(16), dp(8), dp(16), dp(8))
            // Solid AMOLED dock; no translucent grey wash over the lower edge.
            background = GradientDrawable().apply {
                cornerRadii = floatArrayOf(dp(28).toFloat(), dp(28).toFloat(), dp(28).toFloat(), dp(28).toFloat(), 0f, 0f, 0f, 0f)
                setColor(android.graphics.Color.BLACK)
            }
            elevation = 14f
            // Keep every drawer child inside the dock bounds; scroll content must
            // never paint over the fixed collapse handle or the mode rail.
            clipChildren = true
            clipToPadding = true
        }
        val advancedPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(4), 0, dp(4), dp(6))
            clipChildren = true
        }
        advancedPanel.addView(zoomSlider, LinearLayout.LayoutParams(-1, dp(34)))
        advancedPanel.addView(zoomRulerView, LinearLayout.LayoutParams(-1, dp(18)))
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
        utilityRow.addView(gridButton, LinearLayout.LayoutParams(0, dp(46), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
        utilityRow.addView(timerButton, LinearLayout.LayoutParams(0, dp(46), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
        utilityRow.addView(galleryButton, LinearLayout.LayoutParams(0, dp(46), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
        utilityRow.addView(toolsButton, LinearLayout.LayoutParams(0, dp(46), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
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
        // Four compact quick controls share the available width; PRO remains a
        // full-width secondary action so no button is clipped at the screen edge.
        gridButton.textSize = 9f
        timerButton.textSize = 9f
        galleryButton.textSize = 9f
        toolsButton.textSize = 9f
        listOf(gridButton, timerButton, galleryButton, toolsButton).forEach {
            it.setPadding(dp(2), 0, dp(2), 0)
        }
        advancedPanel.addView(utilityRow, LinearLayout.LayoutParams(-1, dp(50)))
        proButton.visibility = if (isProModeEnabled) View.VISIBLE else View.GONE
        advancedPanel.addView(proButton, LinearLayout.LayoutParams(-1, dp(44)).apply {
            setMargins(dp(3), dp(3), dp(3), dp(3))
        })

        photoExtensionButton = smallButton("PHOTO EFFECT: CHECKING…") { cyclePhotoExtension() }
        photoExtensionButton.isEnabled = false
        advancedPanel.addView(photoExtensionButton, LinearLayout.LayoutParams(-1, dp(48)).apply { setMargins(dp(3), dp(4), dp(3), dp(4)) })
        jpegQualityButton = smallButton("JPEG QUALITY: $jpegQuality%") {
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
            background = GradientDrawable().apply { cornerRadius = dp(18).toFloat(); setColor(0xF0161618.toInt()); setStroke(dp(1), accentBorderColor) }
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
        val styleProMetric: (TextView) -> Unit = { metric ->
            metric.setTextColor(accentColor)
            metric.textSize = 11f
            metric.letterSpacing = 0.045f
            metric.setPadding(dp(10), dp(6), dp(10), dp(6))
            metric.background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(0xFF242426.toInt())
                setStroke(dp(1), accentBorderColor)
            }
        }
        styleProMetric(isoValue)
        styleProMetric(shutterValue)
        val proAccent = ColorStateList.valueOf(accentColor)
        listOf(isoSlider, shutterSlider).forEach { slider ->
            slider.progressTintList = proAccent
            slider.thumbTintList = proAccent
        }
        proPanel.addView(isoValue, LinearLayout.LayoutParams(-2, -2).apply { setMargins(0, dp(3), 0, 0) })
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
        styleProMetric(focusValue)
        focusSlider.progressTintList = proAccent
        focusSlider.thumbTintList = proAccent
        proPanel.addView(focusValue, LinearLayout.LayoutParams(-2, -2).apply { setMargins(0, dp(3), 0, 0) })
        proPanel.addView(focusSlider, LinearLayout.LayoutParams(-1, dp(40)))
        whiteBalanceButton = smallButton("WHITE BALANCE: AUTO") {
            cycleWhiteBalance()
        }
        proPanel.addView(whiteBalanceButton, LinearLayout.LayoutParams(-1, dp(44)).apply { setMargins(0, dp(4), 0, dp(4)) })
        sceneModeButton = smallButton("SCENE: AUTO") { cycleSceneMode() }
        proPanel.addView(sceneModeButton, LinearLayout.LayoutParams(-1, dp(44)).apply { setMargins(0, dp(4), 0, dp(4)) })
        val presetRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        presetRow.addView(smallButton("SAVE PRESET") { saveProPreset() },
            LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(0, dp(4), dp(4), dp(4)) })
        presetRow.addView(smallButton("LOAD PRESET") { loadProPreset() },
            LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(dp(4), dp(4), 0, dp(4)) })
        proPanel.addView(presetRow)
        proPanel.addView(smallButton("CAPTURE RAW / DNG") { view ->
            (view as Button).isEnabled = false
            view.text = "CAPTURING RAW…"
            captureRawDng(view)
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { setMargins(0, dp(8), 0, 0) })
        advancedPanel.addView(proPanel)

        photoRatioRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(2), dp(3), dp(2), dp(3))
        }
        photoRatioButtons = listOf(
            smallButton("4:3") { selectPhotoAspectRatio(PhotoAspectRatio.RATIO_4_3) },
            smallButton("16:9") { selectPhotoAspectRatio(PhotoAspectRatio.RATIO_16_9) },
            smallButton("FULL") { selectPhotoAspectRatio(PhotoAspectRatio.FULL) }
        )
        photoRatioButtons.forEach { photoRatioRow.addView(it, LinearLayout.LayoutParams(0, dp(42), 1f).apply { setMargins(dp(3), 0, dp(3), 0) }) }
        photoRatioLabel = TextView(this).apply { text = "PHOTO ASPECT RATIO"; textSize = 10f; setTextColor(0xFFB8B8BE.toInt()); gravity = Gravity.CENTER }
        advancedPanel.addView(photoRatioLabel)
        advancedPanel.addView(photoRatioRow, LinearLayout.LayoutParams(-1, dp(46)))

        videoRatioRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(2), dp(3), dp(2), dp(3))
        }
        videoRatioButtons = listOf(
            smallButton("16:9") { selectVideoAspectRatio(VideoAspectRatio.RATIO_16_9) },
            smallButton("4:3") { selectVideoAspectRatio(VideoAspectRatio.RATIO_4_3) }
        )
        videoRatioButtons.forEach { videoRatioRow.addView(it, LinearLayout.LayoutParams(0, dp(42), 1f).apply { setMargins(dp(3), 0, dp(3), 0) }) }
        advancedPanel.addView(TextView(this).apply {
            text = "VIDEO ASPECT RATIO"
            textSize = 10f
            setTextColor(0xFFB8B8BE.toInt())
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, dp(22)).apply { topMargin = dp(12) })
        advancedPanel.addView(videoRatioRow, LinearLayout.LayoutParams(-1, dp(46)))
        updateAspectRatioControls()

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
        if (themeStyle == CameraThemeStyle.SAMSUNG_ONE_UI) {
            val moreModeButton = smallButton("MORE") {
                setQuickControlsExpanded(quickControlsPanel.visibility != View.VISIBLE)
            }.apply {
                background = android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
                setTextColor(android.graphics.Color.WHITE)
            }
            videoRow.addView(moreModeButton, LinearLayout.LayoutParams(0, dp(50), 1f).apply {
                setMargins(dp(4), 0, dp(4), 0)
            })
        }
        updateModeButtonAppearance()
        advancedPanel.addView(videoQualityButton, LinearLayout.LayoutParams(-1, dp(44)).apply { setMargins(dp(3), dp(4), dp(3), dp(3)) })
        advancedPanel.addView(videoFpsButton, LinearLayout.LayoutParams(-1, dp(44)).apply { setMargins(dp(3), 0, dp(3), dp(3)) })
        advancedPanel.addView(videoStabilizationButton, LinearLayout.LayoutParams(-1, dp(44)).apply { setMargins(dp(3), 0, dp(3), dp(3)) })
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
        advancedPanel.addView(micNoiseReductionButton, LinearLayout.LayoutParams(-1, dp(44)).apply { setMargins(dp(3), 0, dp(3), dp(4)) })
        updateMicNoiseReductionButton()
        updateModeSpecificSettings()
        updateAspectRatioControls()

        val mainRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, dp(84))
            clipChildren = false
            clipToPadding = false
            elevation = 16f
        }
        val flip = Button(this).apply {
            text = "⟳"
            isAllCaps = false
            textSize = 29f
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
            setTextColor(android.graphics.Color.WHITE)
            background = android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
            elevation = 0f
            isClickable = true
            isFocusable = true
            minWidth = 0
            minHeight = 0
            contentDescription = "Switch front and rear camera"
            setPadding(0, 0, 0, 0)
            setOnClickListener { view ->
                view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                if (recording != null) {
                    Toast.makeText(this@BaseSignatureCameraActivity, "Stop recording before switching cameras", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                cameraXController.setTorch(false)
                torchEnabled = false
                resetManualCapture()
                exposureIndex = 0
                cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA)
                    CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                previewView.scaleType = if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA)
                    PreviewView.ScaleType.FIT_CENTER else PreviewView.ScaleType.FILL_CENTER
                syncFlashButtonLabel()
                selectedPhotoExtension = null
                refreshPhotoExtensions()
                updatePreviewAspectRatioFrame { startCamera() }
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
            minHeight = 0
            includeFontPadding = false
            setPadding(0, 0, 0, 0)
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
            textSize = 20f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(android.graphics.Color.WHITE)
            background = GradientDrawable().apply { cornerRadius = 30f; setColor(0x663A414D); setStroke(1, 0x66FFFFFF) }
            minWidth = 0
            minHeight = dp(40)
            setPadding(dp(2), 0, dp(2), 0)
            setOnClickListener { view ->
                view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) {
                    if (!videoMode) {
                        screenFlashEnabled = !screenFlashEnabled
                        getSharedPreferences("signature_camera_settings", MODE_PRIVATE).edit().putBoolean("screen_flash_enabled", screenFlashEnabled).apply()
                        updateScreenFlashPreview()
                        syncFlashButtonLabel()
                    } else Toast.makeText(this@BaseSignatureCameraActivity, "Front screen light is not available during video recording", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (videoMode) {
                    val enabled = !torchEnabled
                    cameraXController.setTorch(enabled)
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
                    cameraXController.setPhotoFlashMode(photoFlashMode)
                    getSharedPreferences("signature_camera_settings", MODE_PRIVATE).edit().putInt("photo_flash_mode", photoFlashMode).apply()
                    syncFlashButtonLabel()
                }
            }
        }
        fun sideControl(button: View, caption: String, status: Boolean = false): LinearLayout {
            val column = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                clipChildren = false
                clipToPadding = false
                setPadding(0, dp(18), 0, 0)
            }
            button.elevation = 0f
            column.addView(button, LinearLayout.LayoutParams(dp(48), dp(48)))
            val label = if (status) flashStatusLabel else TextView(this).apply {
                text = caption
                textSize = 9f
                letterSpacing = 0.08f
                setTextColor(0xFFB8B8BE.toInt())
                gravity = Gravity.CENTER
                maxLines = 1
            }
            column.addView(label, LinearLayout.LayoutParams(-1, dp(18)))
            return column
        }
        flashStatusLabel = TextView(this).apply {
            textSize = 9f
            letterSpacing = 0.02f
            setTextColor(0xFFD4DCE8.toInt())
            gravity = Gravity.CENTER
            maxLines = 1
        }
        mainControlsRow = mainRow
        mainRow.setPadding(dp(10), dp(8), dp(10), dp(4))
        mainRow.gravity = Gravity.CENTER
        galleryThumbnailView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF242428.toInt())
            }
            clipToOutline = true
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }
            setImageResource(android.R.drawable.ic_menu_gallery)
            contentDescription = "Open phone photo gallery"
            isClickable = true
            isFocusable = true
            setOnClickListener { openPhotoGalleryDirectly() }
        }
        // Three equally weighted dock positions keep both side controls balanced
        // around a fixed 76dp shutter. Side icons share the shutter's vertical axis.
        mainRow.setPadding(dp(10), 0, dp(10), 0)
        mainRow.addView(sideControl(galleryThumbnailView, "GALLERY"), LinearLayout.LayoutParams(0, dp(84), 1f).apply { setMargins(dp(4), 0, dp(4), 0) })
        mainRow.addView(captureButton, LinearLayout.LayoutParams(dp(76), dp(76)).apply { gravity = Gravity.CENTER_VERTICAL })
        mainRow.addView(sideControl(flip, "FLIP"), LinearLayout.LayoutParams(0, dp(84), 1f).apply { setMargins(dp(4), 0, dp(4), 0) })
        syncFlashButtonLabel()
        // Camera-style mode rail sits above the shutter, keeping capture as the primary action.
        videoRow.setPadding(dp(18), dp(3), dp(18), dp(3))
        videoRow.gravity = Gravity.CENTER
        videoRow.background = android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
        videoRow.elevation = 0f
        // iPhone camera hierarchy: shutter row first, shooting-mode rail below.
        panel.addView(mainRow)
        panel.addView(videoRow, LinearLayout.LayoutParams(-1, dp(54)).apply {
            setMargins(dp(18), dp(2), dp(18), dp(2))
        })
        val advancedScroll = ScrollView(this).apply {
            visibility = View.GONE
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            isVerticalScrollBarEnabled = false
            setPadding(0, dp(14), 0, dp(8))
            // The 14dp top inset belongs to the scroll viewport. Clip children
            // to its padded content so rows cannot slide over the fixed "v" bar.
            clipChildren = true
            clipToPadding = true
            clipToOutline = true
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(0xE61C1C1E.toInt())
                setStroke(dp(1), 0x33777777)
            }
            addView(advancedPanel)
        }
        settingsDrawer = advancedScroll
        val advancedSettingsHeight = if (landscapeLayout) dp(104) else dp(260)
        // Settings sheet is attached to the root as a top drop-down overlay.
        fun positionLensRailAboveDock() {
            // Keep the lens selector completely clear of the dock in both collapsed
            // and expanded states; a fixed margin caused it to sit behind settings.
            panel.post {
                val params = lensRow.layoutParams as? FrameLayout.LayoutParams ?: return@post
                params.bottomMargin = panel.height + dp(24)
                lensRow.layoutParams = params
            }
        }
        val advancedToggleButton = smallButton(
            if (themeStyle == CameraThemeStyle.IPHONE_PRO) "⠿" else "☷"
        ) {
            setQuickControlsExpanded(quickControlsPanel.visibility != View.VISIBLE)
            positionLensRailAboveDock()
        }
        settingsToggleButton = advancedToggleButton
        advancedToggleButton.textSize = 17f
        advancedToggleButton.minWidth = 0
        advancedToggleButton.minHeight = dp(40)
        advancedToggleButton.setPadding(0, 0, 0, 0)
        advancedToggleButton.contentDescription = "Open camera controls"
        advancedToggleButton.background = android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
        advancedToggleButton.elevation = 0f
        advancedToggleButton.setTextColor(android.graphics.Color.WHITE)
        if (landscapeLayout) {
            // In landscape, keep the lens rail inside the dock so it cannot be
            // pushed underneath the top status strip by the expanded settings sheet.
            panel.addView(lensRow, 0, LinearLayout.LayoutParams(-1, dp(44)).apply {
                setMargins(dp(12), dp(2), dp(12), dp(2))
            })
        }
        // Keep drawer toggle in the safe top action row, clear of the shutter dock.
        title.gravity = Gravity.CENTER_VERTICAL or Gravity.START
        title.textSize = 10f
        title.letterSpacing = 0.16f
        flashButton.background = android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
        flashButton.elevation = 0f
        topHeader.addView(flashButton, LinearLayout.LayoutParams(dp(42), dp(42)))
        ratioQuickButton = smallButton("4:3") {
            if (videoMode) selectVideoAspectRatio(if (cameraXController.uiState.videoAspectRatio == VideoAspectRatio.RATIO_16_9) VideoAspectRatio.RATIO_4_3 else VideoAspectRatio.RATIO_16_9)
            else selectPhotoAspectRatio(when (cameraXController.uiState.photoAspectRatio) {
                PhotoAspectRatio.RATIO_4_3 -> PhotoAspectRatio.RATIO_16_9
                PhotoAspectRatio.RATIO_16_9 -> PhotoAspectRatio.FULL
                PhotoAspectRatio.FULL -> PhotoAspectRatio.RATIO_4_3
            })
        }.apply { textSize = 10f; setPadding(dp(3), 0, dp(3), 0) }
        ratioQuickButton.background = android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
        ratioQuickButton.elevation = 0f
        topHeader.addView(ratioQuickButton, LinearLayout.LayoutParams(dp(54), dp(40)).apply { leftMargin = dp(3) })
        modeQuickButton = smallButton("PHOTO") { if (videoMode) cycleVideoQuality() else cyclePhotoExtension() }
            .apply { textSize = 9f; setPadding(dp(2), 0, dp(2), 0) }
        modeQuickButton.background = android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
        modeQuickButton.elevation = 0f
        topHeader.addView(modeQuickButton, LinearLayout.LayoutParams(dp(62), dp(40)).apply { leftMargin = dp(3) })
        if (themeStyle == CameraThemeStyle.IPHONE_PRO) {
            // iOS 27 places the six-dot controls entry at the lower-right of the shutter dock.
            val flipControl = mainRow.getChildAt(2)
            if (flipControl != null) {
                mainRow.removeView(flipControl)
                val rightDock = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                }
                rightDock.addView(flipControl, LinearLayout.LayoutParams(0, dp(84), 1f).apply {
                    setMargins(dp(4), 0, dp(4), 0)
                })
                rightDock.addView(advancedToggleButton, LinearLayout.LayoutParams(dp(40), dp(40)).apply {
                    marginStart = dp(3)
                })
                mainRow.addView(rightDock, LinearLayout.LayoutParams(0, dp(84), 1f).apply {
                    setMargins(dp(4), 0, dp(4), 0)
                })
            } else {
                topHeader.addView(advancedToggleButton, LinearLayout.LayoutParams(dp(40), dp(42)))
            }
        } else {
            // Samsung keeps the quick-controls entry in the top control rail.
            topHeader.addView(advancedToggleButton, LinearLayout.LayoutParams(dp(40), dp(42)).apply {
                leftMargin = dp(3)
            })
        }

        // Compact two-row quick panel: one tap to the most-used camera controls.
        val quickGrid = android.widget.GridLayout(this).apply {
            rowCount = 2
            columnCount = 4
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(22).toFloat()
                setColor(0xE61C1C1E.toInt())
                setStroke(dp(1), 0x44777777)
            }
            elevation = dp(10).toFloat()
            visibility = View.GONE
            clipToPadding = true
        }
        fun addQuickTile(glyph: String, caption: String, action: () -> Unit) {
            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                    action()
                }
            }
            val icon = TextView(this).apply {
                text = glyph
                textSize = 19f
                gravity = Gravity.CENTER
                setTextColor(android.graphics.Color.WHITE)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(0xFF303034.toInt())
                    setStroke(dp(1), 0x55777777)
                }
            }
            tile.addView(icon, LinearLayout.LayoutParams(dp(42), dp(42)))
            tile.addView(TextView(this).apply {
                text = caption
                textSize = 9f
                maxLines = 1
                gravity = Gravity.CENTER
                setTextColor(0xFFE5E5EA.toInt())
                setPadding(0, dp(3), 0, 0)
            })
            quickGrid.addView(tile, android.widget.GridLayout.LayoutParams(
                android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f),
                android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f)
            ).apply {
                width = 0
                height = dp(76)
                setMargins(dp(2), dp(1), dp(2), dp(1))
            })
        }
        addQuickTile("⚡", "FLASH") { flashButton.performClick() }
        addQuickTile("◷", "TIMER") { timerButton.performClick() }
        addQuickTile("▣", "RATIO") { ratioQuickButton.performClick() }
        addQuickTile("▦", "GRID") { gridButton.performClick() }
        addQuickTile("HDR", "PHOTO FX") { photoExtensionButton.performClick() }
        addQuickTile("◇", "STABILIZE") { videoStabilizationButton.performClick() }
        if (isProModeEnabled) {
            addQuickTile("PRO", "PRO MODE") { proButton.performClick() }
        } else {
            addQuickTile("▣", "RECENT") { showRecentCaptures() }
        }
        addQuickTile("⚙", "SETTINGS") { setSettingsExpanded(advancedScroll.visibility != View.VISIBLE) }
        quickControlsPanel = quickGrid
        // iOS controls open above the shutter; Samsung quick settings drop from the top.
        val quickGridParams = if (themeStyle == CameraThemeStyle.IPHONE_PRO) {
            FrameLayout.LayoutParams(-1, dp(164), Gravity.BOTTOM).apply {
                leftMargin = dp(12)
                rightMargin = dp(12)
                bottomMargin = dp(176)
            }
        } else {
            FrameLayout.LayoutParams(-1, dp(164), Gravity.TOP).apply {
                leftMargin = dp(12)
                rightMargin = dp(12)
                topMargin = dp(104)
            }
        }
        root.addView(quickGrid, quickGridParams)
        val hint = TextView(this).apply {
            text = "TAP TO FOCUS   •   PINCH TO ZOOM"
            textSize = 10f
            setTextColor(0xFFBBBBBB.toInt())
            gravity = Gravity.CENTER
        }
        dockHint = hint
        // Avoid instructional text competing with the viewfinder and controls.
        hint.visibility = View.GONE
        panel.addView(hint)
        cameraDockPanel = panel
        topChrome = top
        root.addView(panel, FrameLayout.LayoutParams(-1, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        root.addView(advancedScroll, FrameLayout.LayoutParams(-1, dp(240), Gravity.TOP).apply {
            leftMargin = dp(14); rightMargin = dp(14); topMargin = dp(96)
        })
        root.setOnApplyWindowInsetsListener { _, insets ->
            val params = panel.layoutParams as? FrameLayout.LayoutParams
            if (params != null) { params.bottomMargin = insets.systemWindowInsetBottom; panel.layoutParams = params }
            val topParams = top.layoutParams as? FrameLayout.LayoutParams
            if (topParams != null) { topParams.topMargin = insets.systemWindowInsetTop; top.layoutParams = topParams }
            val sheetParams = advancedScroll.layoutParams as? FrameLayout.LayoutParams
            if (sheetParams != null) {
                sheetParams.topMargin = insets.systemWindowInsetTop + top.height + dp(8)
                val free = (root.height - panel.height - sheetParams.topMargin - dp(12)).coerceAtLeast(dp(96))
                sheetParams.height = minOf((root.height * 0.46f).toInt().coerceAtLeast(dp(96)), free)
                advancedScroll.layoutParams = sheetParams
            }
            insets
        }
        root.requestApplyInsets()
        // Recalculate after the dock has been measured so the floating lens rail
        // never overlaps the shutter controls or the expanded settings sheet.
        setContentView(root)
        if (themeStyle == CameraThemeStyle.IPHONE_PRO) {
            panel.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                val quickParams = quickGrid.layoutParams as? FrameLayout.LayoutParams
                if (quickParams != null) {
                    val desiredMargin = panel.height + dp(8)
                    if (quickParams.bottomMargin != desiredMargin) {
                        quickParams.bottomMargin = desiredMargin
                        quickGrid.layoutParams = quickParams
                    }
                }
            }
        }
        if (!landscapeLayout) {
            positionLensRailAboveDock()
            panel.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                positionLensRailAboveDock()
            }
        }
        lensRow.bringToFront()
        top.bringToFront()
        advancedToggleButton.bringToFront()
        refreshGalleryThumbnail()
        updateChromeForAspectRatio()
        updateModeSpecificSettings()
        updateQuickTopControls()
    }

    private fun updateChromeForAspectRatio() {
        if (!::topChrome.isInitialized || !::cameraDockPanel.isInitialized) return
        // FULL is currently a still-photo mode; video supports 16:9 and 4:3 only.
        val fullPhoto = !videoMode &&
            cameraXController.uiState.photoAspectRatio == PhotoAspectRatio.FULL
        val chromeColor = if (fullPhoto) 0x77000000.toInt() else android.graphics.Color.BLACK
        topChrome.setBackgroundColor(chromeColor)
        cameraDockPanel.background = GradientDrawable().apply {
            cornerRadii = floatArrayOf(
                dp(28).toFloat(), dp(28).toFloat(), dp(28).toFloat(), dp(28).toFloat(),
                0f, 0f, 0f, 0f
            )
            setColor(if (fullPhoto) 0x88000000.toInt() else android.graphics.Color.BLACK)
        }
    }

    private fun setQuickControlsExpanded(expanded: Boolean) {
        if (!::quickControlsPanel.isInitialized) return
        if (expanded && recording != null) {
            Toast.makeText(this, "Stop recording before opening quick controls", Toast.LENGTH_SHORT).show()
            return
        }
        if (expanded) setSettingsExpanded(false)
        quickControlsPanel.visibility = if (expanded) View.VISIBLE else View.GONE
        updateLensRailVisibility()
        if (::settingsToggleButton.isInitialized) {
            settingsToggleButton.contentDescription = if (expanded) "Close quick controls" else "Open quick controls"
        }
    }

    private fun setSettingsExpanded(expanded: Boolean) {
        if (!::settingsDrawer.isInitialized) return
        if (::quickControlsPanel.isInitialized) quickControlsPanel.visibility = View.GONE
        if (expanded && recording != null) {
            Toast.makeText(this, "Stop recording before opening camera settings", Toast.LENGTH_SHORT).show()
            return
        }
        if (expanded) {
            val root = previewView.parent as? FrameLayout
            val h = root?.height ?: 0
            if (h > 0) {
                val topInset = root?.rootWindowInsets?.systemWindowInsetTop ?: 0
                val topHeight = if (::topChrome.isInitialized) topChrome.height else dp(56)
                val dockHeight = if (::cameraDockPanel.isInitialized) cameraDockPanel.height else dp(220)
                val topMargin = topInset + topHeight + dp(8)
                val free = (h - dockHeight - topMargin - dp(12)).coerceAtLeast(dp(96))
                settingsDrawer.layoutParams = settingsDrawer.layoutParams.apply { height = minOf((h * 0.46f).toInt().coerceAtLeast(dp(96)), free) }
            }
            settingsDrawer.scrollTo(0, 0)
        }
        settingsDrawer.visibility = if (expanded) View.VISIBLE else View.GONE
        updateLensRailVisibility()
        if (::settingsToggleButton.isInitialized) {
            settingsToggleButton.text = if (expanded) "⌄" else "⌃"
            settingsToggleButton.contentDescription = if (expanded) "Hide camera settings" else "Open camera settings"
        }
        if (::mainControlsRow.isInitialized) mainControlsRow.visibility = View.VISIBLE
        if (::dockHint.isInitialized) dockHint.visibility = if (expanded) View.GONE else View.VISIBLE
    }

    // Keep the selectable lens rail out of the same vertical region occupied by
    // either expandable panel. The shutter/mode dock remains anchored and visible.
    private fun updateLensRailVisibility() {
        if (!::lensRow.isInitialized) return
        val panelOpen = (::quickControlsPanel.isInitialized && quickControlsPanel.visibility == View.VISIBLE) ||
            (::settingsDrawer.isInitialized && settingsDrawer.visibility == View.VISIBLE)
        lensRow.visibility = if (panelOpen) View.GONE else View.VISIBLE
    }

    private fun updateModeSpecificSettings() {
        if (!::photoExtensionButton.isInitialized || !::jpegQualityButton.isInitialized) return
        photoExtensionButton.visibility = if (videoMode) View.GONE else View.VISIBLE
        jpegQualityButton.visibility = if (videoMode) View.GONE else View.VISIBLE
        photoRatioLabel.visibility = if (videoMode) View.GONE else View.VISIBLE
        photoRatioRow.visibility = if (videoMode) View.GONE else View.VISIBLE
        if (::videoQualityButton.isInitialized) {
            val visibility = if (videoMode) View.VISIBLE else View.GONE
            videoQualityButton.visibility = visibility
            videoFpsButton.visibility = visibility
            videoStabilizationButton.visibility = visibility
            micNoiseReductionButton.visibility = visibility
        }
    }

    private fun setCameraMode(enableVideo: Boolean) {
        if (recording != null) {
            Toast.makeText(this, "Stop recording before changing mode", Toast.LENGTH_SHORT).show()
            return
        }
        if (videoMode == enableVideo) return
        // Mode rebinding disables the hardware torch; clear its UI state as well.
        cameraXController.setTorch(false)
        torchEnabled = false
        videoMode = enableVideo
        cameraXController.setVideoMode(enableVideo)
        captureButton.text = if (videoMode) "● REC" else "●"
        captureButton.contentDescription = if (videoMode) "Start video recording" else "Take photo"
        syncFlashButtonLabel()
        updateModeButtonAppearance()
        updateModeSpecificSettings()
        updateChromeForAspectRatio()
        // Keep the settings sheet's current open/closed state across PHOTO/VIDEO changes.
        updatePreviewAspectRatioFrame { startCamera() }
    }

    private fun updateModeButtonAppearance() {
        if (!::photoModeButton.isInitialized || !::videoModeButton.isInitialized) return
        photoModeButton.background = android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
        videoModeButton.background = android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
        photoModeButton.setTextColor(if (!videoMode) accentColor else android.graphics.Color.WHITE)
        videoModeButton.setTextColor(if (videoMode) accentColor else android.graphics.Color.WHITE)
        photoModeButton.alpha = if (videoMode) 0.72f else 1f
        videoModeButton.alpha = if (videoMode) 1f else 0.72f
        photoModeButton.text = if (videoMode) "PHOTO" else "•  PHOTO"
        videoModeButton.text = if (videoMode) "•  VIDEO" else "VIDEO"
        // The live mic meter belongs to video capture; keep photo mode's viewfinder uncluttered.
        if (::audioLevelLabel.isInitialized) {
            audioLevelLabel.visibility = if (videoMode) View.VISIBLE else View.GONE
        }
        if (::audioLevelBar.isInitialized) {
            audioLevelBar.visibility = if (videoMode) View.VISIBLE else View.GONE
        }
        updateMicNoiseReductionButton()
        updateCaptureButtonAppearance()
        updateAspectRatioControls()
    }


    private fun updateAspectRatioControls() {
        if (!::photoRatioRow.isInitialized || !::videoRatioRow.isInitialized) return
        photoRatioRow.visibility = if (videoMode) View.GONE else View.VISIBLE
        videoRatioRow.visibility = if (videoMode) View.VISIBLE else View.GONE
        updateQuickTopControls()
        val selectedPhoto = cameraXController.uiState.photoAspectRatio
        photoRatioButtons.forEachIndexed { index, button ->
            val selected = when (index) {
                0 -> selectedPhoto == PhotoAspectRatio.RATIO_4_3
                1 -> selectedPhoto == PhotoAspectRatio.RATIO_16_9
                else -> selectedPhoto == PhotoAspectRatio.FULL
            }
            button.setTextColor(if (selected) accentColor else android.graphics.Color.WHITE)
            button.background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(if (selected) 0xFF34343A.toInt() else 0xFF1C1C1E.toInt())
                setStroke(dp(1), if (selected) accentColor else 0x33777777)
            }
        }
        val selectedVideo = cameraXController.uiState.videoAspectRatio
        videoRatioButtons.forEachIndexed { index, button ->
            val selected = if (index == 0) selectedVideo == VideoAspectRatio.RATIO_16_9
                else selectedVideo == VideoAspectRatio.RATIO_4_3
            button.setTextColor(if (selected) accentColor else android.graphics.Color.WHITE)
            button.background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(if (selected) 0xFF34343A.toInt() else 0xFF1C1C1E.toInt())
                setStroke(dp(1), if (selected) accentColor else 0x33777777)
            }
        }
    }

    private fun selectPhotoAspectRatio(ratio: PhotoAspectRatio) {
        if (recording != null) {
            Toast.makeText(this, "Stop recording before changing aspect ratio", Toast.LENGTH_SHORT).show()
            return
        }
        if (cameraXController.uiState.photoAspectRatio == ratio) return
        cameraXController.updateState(cameraXController.uiState.copy(photoAspectRatio = ratio))
        getSharedPreferences("signature_camera_settings", MODE_PRIVATE).edit()
            .putString("photo_aspect_ratio", ratio.name).apply()
        updateAspectRatioControls()
        updateQuickTopControls()
        updateChromeForAspectRatio()
        updatePreviewAspectRatioFrame { startCamera() }
    }

    private fun selectVideoAspectRatio(ratio: VideoAspectRatio) {
        if (recording != null) {
            Toast.makeText(this, "Stop recording before changing aspect ratio", Toast.LENGTH_SHORT).show()
            return
        }
        if (cameraXController.uiState.videoAspectRatio == ratio) return
        cameraXController.updateState(cameraXController.uiState.copy(videoAspectRatio = ratio))
        getSharedPreferences("signature_camera_settings", MODE_PRIVATE).edit()
            .putString("video_aspect_ratio", ratio.name).apply()
        updateAspectRatioControls()
        updateQuickTopControls()
        updateChromeForAspectRatio()
        updatePreviewAspectRatioFrame { startCamera() }
    }

    // CameraX standard aspect strategies are 4:3 and 16:9. FULL selects the
    // highest available 4:3 still stream; exact sensor dimensions are device-specific.
    private fun currentResolutionSelector(isVideo: Boolean = videoMode): ResolutionSelector {
        val strategy = if (isVideo) {
            if (cameraXController.uiState.videoAspectRatio == VideoAspectRatio.RATIO_4_3)
                AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
            else AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
        } else {
            when (cameraXController.uiState.photoAspectRatio) {
                PhotoAspectRatio.RATIO_16_9 -> AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
                PhotoAspectRatio.RATIO_4_3, PhotoAspectRatio.FULL -> AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
            }
        }
        return ResolutionSelector.Builder()
            .setAspectRatioStrategy(strategy)
            .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
            .build()
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
        // The dock gives this view an exact 76x76dp square. A 7dp inset leaves
        // a true 62x62dp inner circle; recording uses a compact stop-square.
        val inset = if (isRecording) dp(23) else dp(7)
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
        updateQuickTopControls()
    }

    private fun updateQuickTopControls() {
        if (::ratioQuickButton.isInitialized) ratioQuickButton.text = if (videoMode) {
            if (cameraXController.uiState.videoAspectRatio == VideoAspectRatio.RATIO_4_3) "4:3" else "16:9"
        } else when (cameraXController.uiState.photoAspectRatio) {
            PhotoAspectRatio.RATIO_4_3 -> "4:3"; PhotoAspectRatio.RATIO_16_9 -> "16:9"; PhotoAspectRatio.FULL -> "FULL"
        }
        if (::modeQuickButton.isInitialized) modeQuickButton.text = if (videoMode) {
            val q = when (videoQuality) { Quality.UHD -> "4K"; Quality.FHD -> "FHD"; Quality.HD -> "HD"; else -> "SD" }
            "$q ${if (videoFps == 0) "AUTO" else videoFps}"
        } else when (selectedPhotoExtension) {
            ExtensionMode.NIGHT -> "NIGHT"; ExtensionMode.HDR -> "HDR"; ExtensionMode.BOKEH -> "BOKEH"; else -> if (ultraHdrCaptureEnabled) "U HDR" else "PHOTO"
        }
    }

    private fun smallButton(label: String, action: (View) -> Unit) =
        Button(this).apply {
            text = label
            textSize = 13f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            includeFontPadding = false
            isAllCaps = false
            setTextColor(android.graphics.Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = 24f
                setColor(0xFF1C1C1E.toInt())
                setStroke(1, 0x33777777)
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

    private fun addDigitalZoomFallback() {
        val presets = if (themeStyle == CameraThemeStyle.IPHONE_PRO) {
            listOf(0.5f, 1f, 2f, 4f, 8f)
        } else {
            listOf(0.5f, 1f, 2f, 3f, 5f, 10f)
        }
        presets.forEach { ratio ->
            val label = if (ratio % 1f == 0f) ratio.toInt().toString()
                else "%.1f".format(Locale.US, ratio)
            val button = Button(this).apply {
                text = "${label}×"
                textSize = 11f
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
                isAllCaps = false
                setTextColor(if (ratio == 1f) accentColor else android.graphics.Color.WHITE)
                tag = ratio
                background = GradientDrawable().apply {
                    cornerRadius = dp(22).toFloat()
                    setColor(if (ratio == 1f) 0xFF303034.toInt() else 0xFF1C1C1E.toInt())
                    setStroke(dp(1), if (ratio == 1f) accentBorderColor else 0xFF343438.toInt())
                }
                minWidth = 0
                minHeight = dp(48)
                setPadding(dp(10), 0, dp(10), 0)
                contentDescription = "Set camera zoom to ${label}x"
                setOnClickListener {
                    val state = camera?.cameraInfo?.zoomState?.value ?: return@setOnClickListener
                    if (ratio < state.minZoomRatio || ratio > state.maxZoomRatio) {
                        Toast.makeText(this@BaseSignatureCameraActivity, "Zoom ${label}x is not supported by this camera", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    cameraXController.setZoomRatio(ratio)
                    zoomLabel.text = "ZOOM  ${label}x"
                    updateLensSelection(ratio)
                    it.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                }
            }
            lensRow.addView(button, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                setMargins(dp(3), 0, dp(3), 0)
            })
        }
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
        } ?: rearIds.firstOrNull() ?: run {
            addDigitalZoomFallback()
            return
        }
        val logical = manager.getCameraCharacteristics(rearId)
        val logicalFocal = logical.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull() ?: run {
            addDigitalZoomFallback()
            return
        }
        val logicalSize = logical.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) ?: run {
            addDigitalZoomFallback()
            return
        }
        val baseEquivalent = logicalFocal / logicalSize.width
        // Derive 35mm-equivalent focal length from each exposed physical
        // camera's focal length and sensor width; never hardcode 16/24/72mm.
        val options = mutableListOf<Triple<String, Float, Int>>()
        val baseEquivalentMm = (baseEquivalent * 36f).toInt().coerceAtLeast(1)
        options.add(Triple("1.0x", 1f, baseEquivalentMm))
        logical.physicalCameraIds.forEach { physicalId ->
            try {
                val physical = manager.getCameraCharacteristics(physicalId)
                val focal = physical.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull()
                val size = physical.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                if (focal != null && size != null && size.width > 0f) {
                    val ratio = (focal / size.width) / baseEquivalent
                    val equivalentMm = ((focal / size.width) * 36f).toInt().coerceAtLeast(1)
                    if (ratio.isFinite() && ratio in 0.25f..10f &&
                        options.none { kotlin.math.abs(it.second - ratio) < 0.12f }) {
                        options.add(Triple("%.1fx".format(Locale.US, ratio), ratio, equivalentMm))
                    }
                }
            } catch (_: Exception) { }
        }
        val sorted = options.distinctBy { it.first }.sortedBy { it.second }
        if (sorted.size <= 1) {
            addDigitalZoomFallback()
            return
        }
        sorted.forEach { (label, ratio, focalLengthMm) ->
            val button = Button(this)
            button.text = "${label.replace("x", "×")}\n${focalLengthMm}mm"
            button.textSize = 11f
            button.setLineSpacing(0f, 0.9f)
            button.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
            button.isAllCaps = false
            button.setTextColor(if (kotlin.math.abs(ratio - 1f) < 0.12f) accentColor else android.graphics.Color.WHITE)
            button.tag = ratio
            button.background = GradientDrawable().apply {
                cornerRadius = dp(22).toFloat()
                setColor(if (kotlin.math.abs(ratio - 1f) < 0.12f) 0xFF303034.toInt() else 0xFF1C1C1E.toInt())
                setStroke(dp(1), if (kotlin.math.abs(ratio - 1f) < 0.12f) 0xFFE5E5EA.toInt() else 0xFF343438.toInt())
            }
            button.minWidth = 0
            button.minHeight = dp(48)
            button.setPadding(dp(10), 0, dp(10), 0)
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
                cameraXController.setZoomRatio(ratio)
                zoomLabel.text = "LENS VIEW  $label"
                updateLensSelection(ratio)
                button.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
            }
            lensRow.addView(button, LinearLayout.LayoutParams(0, dp(52), 1f).apply { setMargins(dp(3), 0, dp(3), 0) })
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
                cornerRadius = dp(22).toFloat()
                setColor(if (selected) 0xFFF2F2F7.toInt() else 0xFF1C1C1E.toInt())
                setStroke(dp(1), if (selected) 0xFFF2F2F7.toInt() else 0xFF343438.toInt())
            }
            button.setTextColor(if (selected) 0xFF111114.toInt() else android.graphics.Color.WHITE)
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
                    val pnr = pc.get(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES)?.joinToString()
                    val pfps = pc.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)?.joinToString()
                    val pstab = pc.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)?.joinToString()
                    "ID $physicalId: focal=${pf ?: "?"}mm, sensor=${ps?.let { "%.2f x %.2fmm".format(Locale.US, it.width, it.height) } ?: "?"}, array=${pa?.let { "${it.width}x${it.height}" } ?: "?"}, JPEG=${pj?.let { "${it.width}x${it.height}" } ?: "?"}, NR modes=${pnr ?: "?"}, FPS=${pfps ?: "?"}, video stabilization=${pstab ?: "?"}"
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
            // Capture in the phone's current display orientation, then keep that
            // orientation stable for the duration of this recording.
            @Suppress("DEPRECATION")
            activeVideo.targetRotation = windowManager.defaultDisplay.rotation
            recording = activeVideo.output
                .prepareRecording(this, outputOptions)
                .withAudioEnabled()
                .start(ContextCompat.getMainExecutor(this)) { event ->
                    when (event) {
                        is VideoRecordEvent.Start -> {
                            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LOCKED
                            setSettingsExpanded(false)
                            cameraXController.updateState(cameraXController.uiState.copy(isRecording = true))
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
                            cameraXController.updateState(cameraXController.uiState.copy(isRecording = false))
                            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
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
            cameraXController.updateState(cameraXController.uiState.copy(isRecording = false))
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
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
            // Read FPS and stabilization capabilities from the exact CameraX-bound
            // camera. A facing-only lookup can accidentally inspect a different
            // rear sensor (for example, an auxiliary telephoto/ultrawide camera).
            val activeCameraId = Camera2CameraInfo.from(activeCamera.cameraInfo).cameraId
            val activeCharacteristics = if (manager.cameraIdList.contains(activeCameraId)) {
                manager.getCameraCharacteristics(activeCameraId)
            } else null
            availableFpsRanges = activeCharacteristics
                ?.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
                ?: emptyArray()
            val fixedRates = availableFpsRanges.filter { it.lower == it.upper }.map { it.lower }.distinct()
            if (videoFps != 0 && (videoFps !in fixedRates || videoFps !in listOf(30, 60))) {
                videoFps = when {
                    fixedRates.contains(30) -> 30
                    fixedRates.contains(60) -> 60
                    else -> 0
                }
            }
            videoFpsButton.text = if (videoFps == 0) "FRAME RATE: AUTO" else "FRAME RATE: $videoFps FPS"
            updateQuickTopControls()
            val stabilizationModes = activeCharacteristics
                ?.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)
                ?: intArrayOf(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF)
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
        updateQuickTopControls()
        if (recording == null) startCamera()
    }

    private fun cycleVideoFps() {
        if (recording != null) {
            Toast.makeText(this, "Stop recording before changing frame rate", Toast.LENGTH_SHORT).show()
            return
        }
        val choices = listOf(30, 60, 0).filter { fps -> fps == 0 || availableFpsRanges.any { it.lower == fps && it.upper == fps } }
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
        updateQuickTopControls()
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
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            if (videoFps > 0) {
                val fpsRange = availableFpsRanges.firstOrNull { it.lower == videoFps && it.upper == videoFps }
                if (fpsRange != null) builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange)
            } else {
                val lowLightRange = availableFpsRanges
                    .filter { it.lower <= 15 && it.upper >= 30 }
                    .minByOrNull { it.lower }
                if (lowLightRange != null) builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, lowLightRange)
            }
            builder.setCaptureRequestOption(
                CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                if (videoStabilizationEnabled) CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON
                else CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF
            )
            // Prefer the camera HAL's high-quality temporal/spatial denoising for
            // video only when the active sensor explicitly advertises support.
            // Never force an unsupported mode or add software smoothing.
            val manager = getSystemService(CAMERA_SERVICE) as CameraManager
            // Match the exact active CameraX camera on multi-camera devices.
            val cameraId = Camera2CameraInfo.from(activeCamera.cameraInfo).cameraId
            val noiseModes = (if (manager.cameraIdList.contains(cameraId)) {
                manager.getCameraCharacteristics(cameraId)
                    .get(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES)
            } else null) ?: intArrayOf()
            if (noiseModes.contains(CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY)) {
                builder.setCaptureRequestOption(
                    CaptureRequest.NOISE_REDUCTION_MODE,
                    CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY
                )
            }
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

    private fun saveProPreset() {
        getSharedPreferences("signature_camera_settings", MODE_PRIVATE).edit()
            .putInt("pro_preset_iso", manualIso)
            .putLong("pro_preset_shutter_ns", manualShutterNs)
            .putInt("pro_preset_focus_progress", manualFocusProgress)
            .putInt("pro_preset_awb_mode", savedAwbMode)
            .putInt("pro_preset_exposure_index", exposureIndex)
            .apply()
        Toast.makeText(this, "Pro preset saved", Toast.LENGTH_SHORT).show()
    }

    private fun loadProPreset() {
        val prefs = getSharedPreferences("signature_camera_settings", MODE_PRIVATE)
        if (!prefs.contains("pro_preset_iso")) {
            Toast.makeText(this, "No saved Pro preset yet", Toast.LENGTH_SHORT).show()
            return
        }
        manualIso = prefs.getInt("pro_preset_iso", manualIso).coerceIn(activeIsoMin, activeIsoMax)
        manualShutterNs = prefs.getLong("pro_preset_shutter_ns", manualShutterNs)
            .coerceIn(activeShutterMinNs, activeShutterMaxNs)
        manualFocusProgress = prefs.getInt("pro_preset_focus_progress", manualFocusProgress).coerceIn(0, 1000)
        val presetAwb = prefs.getInt("pro_preset_awb_mode", CaptureRequest.CONTROL_AWB_MODE_AUTO)
        awbModeIndex = supportedAwbModes.indexOf(presetAwb).takeIf { it >= 0 } ?: 0
        savedAwbMode = supportedAwbModes.getOrElse(awbModeIndex) { CaptureRequest.CONTROL_AWB_MODE_AUTO }
        isoSlider.progress = (((manualIso - activeIsoMin).toFloat() /
            (activeIsoMax - activeIsoMin).coerceAtLeast(1)) * isoSlider.max).toInt().coerceIn(0, isoSlider.max)
        shutterSlider.progress = shutterProgressFromNs(manualShutterNs)
        focusSlider.progress = manualFocusProgress
        manualFocusDiopters = minimumFocusDistance * (manualFocusProgress / 1000f)
        manualFocusEnabled = manualFocusProgress > 0 && minimumFocusDistance > 0f
        focusValue.text = if (manualFocusEnabled) "FOCUS  %.2f D".format(Locale.US, manualFocusDiopters) else "FOCUS  AUTO"
        whiteBalanceButton.text = "WHITE BALANCE: ${when (savedAwbMode) {
            CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT -> "INCANDESCENT"
            CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT -> "FLUORESCENT"
            CaptureRequest.CONTROL_AWB_MODE_WARM_FLUORESCENT -> "WARM FLUORESCENT"
            CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT -> "DAYLIGHT"
            CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT -> "CLOUDY"
            CaptureRequest.CONTROL_AWB_MODE_TWILIGHT -> "TWILIGHT"
            CaptureRequest.CONTROL_AWB_MODE_SHADE -> "SHADE"
            else -> "AUTO"
        }}"
        val evRange = camera?.cameraInfo?.exposureState?.exposureCompensationRange
        if (evRange != null) {
            exposureIndex = prefs.getInt("pro_preset_exposure_index", 0).coerceIn(evRange.lower, evRange.upper)
            cameraXController.setExposureCompensation(exposureIndex)
            val step = camera?.cameraInfo?.exposureState?.exposureCompensationStep
            val ev = if (step != null && step.denominator != 0) exposureIndex * step.numerator.toFloat() / step.denominator.toFloat() else 0f
            exposureLabel.text = "EV  ${if (ev > 0) "+" else ""}%.1f".format(Locale.US, ev)
        }
        if (proModeEnabled) applyManualCapture()
        prefs.edit().putInt("manual_iso", manualIso).putLong("manual_shutter_ns", manualShutterNs)
            .putInt("manual_focus_progress", manualFocusProgress).putInt("manual_awb_mode", savedAwbMode).apply()
        Toast.makeText(this, "Pro preset loaded", Toast.LENGTH_SHORT).show()
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
        cameraXController.setExposureCompensation(exposureIndex)
        exposureLabel.text = "EV  0.0"
        Toast.makeText(this, "Exposure reset to 0 EV", Toast.LENGTH_SHORT).show()
    }

    private fun changeExposure(delta: Int) {
        val activeCamera = camera ?: return
        val range = activeCamera.cameraInfo.exposureState.exposureCompensationRange
        exposureIndex = (exposureIndex + delta).coerceIn(range.lower, range.upper)
        cameraXController.setExposureCompensation(exposureIndex)
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
        val ultraHdrSuffix = if (ultraHdrCaptureEnabled && !videoMode) " • ULTRA HDR" else ""
        photoExtensionButton.text = "PHOTO EFFECT: $label$ultraHdrSuffix"
        val effectDescription = when {
            !extensionsInitialized -> "Checking manufacturer camera effects"
            supportedPhotoExtensions.isEmpty() -> "This camera does not expose Bokeh, Night or HDR extensions"
            selectedPhotoExtension == ExtensionMode.BOKEH -> "OEM camera Bokeh extension enabled"
            selectedPhotoExtension == ExtensionMode.NIGHT -> "OEM camera Night extension enabled"
            selectedPhotoExtension == ExtensionMode.HDR -> "OEM camera HDR extension enabled"
            else -> "OEM camera effects disabled; tap to choose an available effect"
        }
        photoExtensionButton.contentDescription = effectDescription +
            if (ultraHdrCaptureEnabled && !videoMode) ". Ultra HDR photo output is active." else ""
        photoExtensionButton.isEnabled = extensionsInitialized && supportedPhotoExtensions.isNotEmpty() && !videoMode
        updateQuickTopControls()
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
    private fun applyStillImageQualityOptions(builder: ImageCapture.Builder, provider: ProcessCameraProvider, selector: CameraSelector) {
        if (selectedPhotoExtension != null) return
        try {
            val manager = getSystemService(CAMERA_SERVICE) as CameraManager
            // Use the exact Camera2 ID selected by CameraX instead of guessing
            // from the first camera that shares the same front/rear facing.
            val cameraId = Camera2CameraInfo.from(provider.getCameraInfo(selector)).cameraId
            if (!manager.cameraIdList.contains(cameraId)) return
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

    private fun applyUltraHdrIfSupported(
        builder: ImageCapture.Builder,
        provider: ProcessCameraProvider,
        selector: CameraSelector
    ) {
        ultraHdrCaptureEnabled = false
        if (videoMode || selectedPhotoExtension != null) return
        try {
            val cameraInfo = provider.getCameraInfo(selector)
            val supportedFormats = ImageCapture.getImageCaptureCapabilities(cameraInfo).supportedOutputFormats
            if (supportedFormats.contains(ImageCapture.OUTPUT_FORMAT_JPEG_ULTRA_HDR)) {
                builder.setOutputFormat(ImageCapture.OUTPUT_FORMAT_JPEG_ULTRA_HDR)
                ultraHdrCaptureEnabled = true
            }
        } catch (_: Exception) {
            // Keep standard JPEG if this camera/HAL does not expose Ultra HDR.
            ultraHdrCaptureEnabled = false
        }
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    // Bind preview and capture use cases to one PreviewView viewport. This makes
    // CameraX apply a shared crop rectangle instead of letting the saved still
    // image use a wider sensor frame than the FILL_CENTER viewfinder.
    private fun bindCameraUseCases(
        provider: ProcessCameraProvider,
        selector: CameraSelector,
        preview: Preview,
        vararg useCases: UseCase
    ): Camera {
        val viewPort = previewView.viewPort
        if (viewPort != null) {
            val group = UseCaseGroup.Builder()
                .setViewPort(viewPort)
                .addUseCase(preview)
                .apply { useCases.forEach { addUseCase(it) } }
                .build()
            return provider.bindToLifecycle(this, selector, group).also { attachCameraController(it) }
        }
        // If the view has not produced a viewport yet, preserve the previous
        // binding path rather than delaying camera startup or failing capture.
        return provider.bindToLifecycle(this, selector, preview, *useCases).also { attachCameraController(it) }
    }

    // Phase 1 bridge: keep MainActivity's existing CameraX binding and capture
    // orchestration intact while publishing the active camera + UI snapshot.
    private fun attachCameraController(activeCamera: Camera) {
        cameraXController.attach(activeCamera)
        cameraXController.updateState(
            CameraUiState(
                isVideoMode = videoMode,
                zoomRatio = activeCamera.cameraInfo.zoomState.value?.zoomRatio ?: 1f,
                exposureCompensationIndex = exposureIndex,
                photoFlashMode = photoFlashMode,
                torchEnabled = torchEnabled,
                videoQuality = when (videoQuality) {
                    Quality.UHD -> "UHD"
                    Quality.FHD -> "FHD"
                    Quality.HD -> "HD"
                    else -> "SD"
                },
                videoFps = videoFps,
                videoStabilizationEnabled = videoStabilizationEnabled,
                micCleanEnabled = micNoiseReductionEnabled,
                jpegQuality = jpegQuality,
                ultraHdrEnabled = ultraHdrCaptureEnabled,
                photoAspectRatio = cameraXController.uiState.photoAspectRatio,
                videoAspectRatio = cameraXController.uiState.videoAspectRatio,
                isRecording = recording != null
            )
        )
    }

    @OptIn(ExperimentalAudioApi::class)
    private fun startCamera() {
        if (recording != null) {
            Toast.makeText(this, "Stop recording before changing camera settings", Toast.LENGTH_SHORT).show()
            return
        }
        val generation = ++cameraStartGeneration
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            if (generation != cameraStartGeneration || isFinishing || isDestroyed) return@addListener
            // PreviewView can be measured before its transformation info (and
            // therefore its ViewPort) is ready. Retry briefly rather than
            // silently binding preview and still capture with unrelated crops.
            if (previewView.viewPort == null && viewportRetryCount < 10) {
                viewportRetryCount++
                previewView.postDelayed({
                    if (generation == cameraStartGeneration && !isFinishing && !isDestroyed) startCamera()
                }, 50L)
                return@addListener
            }
            if (previewView.viewPort != null) viewportRetryCount = 0
            val provider = future.get()
            initializePhotoExtensions(provider)
            val activeResolutionSelector = currentResolutionSelector(videoMode)
            val preview = Preview.Builder().setResolutionSelector(activeResolutionSelector).build().also { it.surfaceProvider = previewView.surfaceProvider }
            try {
                provider.unbindAll()
                val activeSelector = if (!videoMode && selectedPhotoExtension != null) {
                    val manager = extensionsManager
                    if (manager == null) cameraSelector else manager.getExtensionEnabledCameraSelector(cameraSelector, selectedPhotoExtension!!)
                } else cameraSelector
                if (videoMode) {
                    imageCapture = null
                    val recorderAspectRatio = if (cameraXController.uiState.videoAspectRatio == VideoAspectRatio.RATIO_4_3) AspectRatio.RATIO_4_3 else AspectRatio.RATIO_16_9
                    val recorderBuilder = Recorder.Builder()
                        .setAspectRatio(recorderAspectRatio)
                        .setQualitySelector(QualitySelector.from(videoQuality, FallbackStrategy.lowerQualityOrHigherThan(Quality.HD)))
                    if (micNoiseReductionEnabled) {
                        recorderBuilder.setAudioProcessors(listOf(VoiceNoiseReductionProcessor()))
                    }
                    val recorder = recorderBuilder.build()
                    val videoUseCase = VideoCapture.withOutput(recorder)
                    videoCapture = videoUseCase
                    camera = bindCameraUseCases(provider, cameraSelector, preview, videoUseCase)
                    updateVideoCapabilities()
                    photoExtensionButton.isEnabled = false
                } else {
                    videoCapture = null
                    val photoResolution = activeResolutionSelector
                    val imageBuilder = ImageCapture.Builder()
                        .setResolutionSelector(photoResolution)
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .setJpegQuality(jpegQuality)
                        .setFlashMode(photoFlashMode)
                    applyStillImageQualityOptions(imageBuilder, provider, activeSelector)
                    applyUltraHdrIfSupported(imageBuilder, provider, activeSelector)
                    val stillUseCase = imageBuilder.build()
                    imageCapture = stillUseCase
                    camera = bindCameraUseCases(provider, activeSelector, preview, stillUseCase)
                    refreshPhotoExtensions()
                }
                camera?.cameraInfo?.zoomState?.observe(this) { state ->
                    cameraXController.updateState(cameraXController.uiState.copy(zoomRatio = state.zoomRatio))
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
                try {
                    Camera2CameraControl.from(camera!!.cameraControl).clearCaptureRequestOptions()
                    camera!!.cameraControl.setExposureCompensationIndex(0)
                } catch (_: Exception) { }
                exposureIndex = 0
                exposureLabel.text = "EV  0"
                // The reset above clears all Camera2 interop options. Re-apply
                // the selected video FPS and recording options only after the
                // final camera bind/reset, otherwise 60 FPS silently falls back
                // to the camera's default repeating-request range.
                if (videoMode) applyVideoRequestOptions()
            } catch (e: Exception) {
                if (ultraHdrCaptureEnabled && selectedPhotoExtension == null && !videoMode) {
                    ultraHdrCaptureEnabled = false
                    updatePhotoExtensionLabel()
                    Toast.makeText(this, "Ultra HDR could not bind on this camera mode; using standard JPEG", Toast.LENGTH_LONG).show()
                    try {
                        provider.unbindAll()
                        val fallbackPreview = Preview.Builder().setResolutionSelector(currentResolutionSelector(false)).build().also { it.surfaceProvider = previewView.surfaceProvider }
                        val fallbackImageBuilder = ImageCapture.Builder()
                            .setResolutionSelector(currentResolutionSelector(false))
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                            .setJpegQuality(jpegQuality)
                            .setFlashMode(photoFlashMode)
                        applyStillImageQualityOptions(fallbackImageBuilder, provider, cameraSelector)
                        val fallbackStillUseCase = fallbackImageBuilder.build()
                        imageCapture = fallbackStillUseCase
                        camera = bindCameraUseCases(provider, cameraSelector, fallbackPreview, fallbackStillUseCase)
                        refreshPhotoExtensions()
                        updateManualRanges()
                        updateScreenFlashPreview()
                    } catch (fallback: Exception) {
                        Toast.makeText(this, "Unable to start camera: ${fallback.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                } else if (selectedPhotoExtension != null) {
                    selectedPhotoExtension = null
                    updatePhotoExtensionLabel()
                    Toast.makeText(this, "OEM photo effect could not start; returning to standard camera", Toast.LENGTH_LONG).show()
                    try {
                        provider.unbindAll()
                        val fallbackPreview = Preview.Builder().setResolutionSelector(currentResolutionSelector(false)).build().also { it.surfaceProvider = previewView.surfaceProvider }
                        val fallbackImageBuilder = ImageCapture.Builder()
                            .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY).setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY).build())
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                            .setJpegQuality(jpegQuality)
                            .setFlashMode(photoFlashMode)
                        applyStillImageQualityOptions(fallbackImageBuilder, provider, cameraSelector)
                        applyUltraHdrIfSupported(fallbackImageBuilder, provider, cameraSelector)
                        val fallbackStillUseCase = fallbackImageBuilder.build()
                        imageCapture = fallbackStillUseCase
                        camera = bindCameraUseCases(provider, cameraSelector, fallbackPreview, fallbackStillUseCase)
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

    private fun queryLatestPhotoUri(): Uri? {
        val projection = arrayOf(MediaStore.Images.Media._ID)
        return try {
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?",
                arrayOf("Signature_%"),
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(0)
                    android.content.ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                    )
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun refreshGalleryThumbnail() {
        if (!::galleryThumbnailView.isInitialized) return
        val uri = queryLatestPhotoUri()
        galleryThumbnailView.tag = uri
        if (uri == null) {
            galleryThumbnailView.setImageResource(android.R.drawable.ic_menu_gallery)
            return
        }
        Thread {
            val bitmap = try {
                val id = uri.lastPathSegment?.toLongOrNull()
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    contentResolver.loadThumbnail(uri, android.util.Size(dp(96), dp(96)), null)
                } else if (id != null) {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Thumbnails.getThumbnail(
                        contentResolver, id, MediaStore.Images.Thumbnails.MINI_KIND, null
                    )
                } else null
            } catch (_: Exception) {
                null
            }
            runOnUiThread {
                if (!isFinishing && ::galleryThumbnailView.isInitialized && galleryThumbnailView.tag == uri) {
                    if (bitmap != null) galleryThumbnailView.setImageBitmap(bitmap)
                    else galleryThumbnailView.setImageResource(android.R.drawable.ic_menu_gallery)
                }
            }
        }.start()
    }

    private fun openPhotoGalleryDirectly() {
        val latestUri = queryLatestPhotoUri()
        val viewIntent = latestUri?.let {
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(it, "image/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        try {
            if (viewIntent != null) {
                startActivity(viewIntent)
            } else {
                startActivity(Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" })
            }
        } catch (_: android.content.ActivityNotFoundException) {
            try {
                startActivity(Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" })
            } catch (_: android.content.ActivityNotFoundException) {
                Toast.makeText(this, "No photo gallery app is available", Toast.LENGTH_SHORT).show()
            }
        }
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
                        val cell = (convertView as? LinearLayout) ?: LinearLayout(this@BaseSignatureCameraActivity).apply {
                            orientation = LinearLayout.VERTICAL
                            gravity = Gravity.CENTER
                            setPadding(4, 4, 4, 8)
                            background = GradientDrawable().apply {
                                cornerRadius = 12f
                                setColor(0xFF202B3B.toInt())
                            }
                            addView(ImageView(this@BaseSignatureCameraActivity).apply {
                                layoutParams = LinearLayout.LayoutParams(-1, 112)
                                scaleType = ImageView.ScaleType.CENTER_CROP
                                setImageResource(android.R.drawable.ic_menu_gallery)
                            })
                            addView(TextView(this@BaseSignatureCameraActivity).apply {
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
                        Toast.makeText(this@BaseSignatureCameraActivity, "No app available to open this file", Toast.LENGTH_SHORT).show()
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
            if (cameraXController.uiState.photoAspectRatio == PhotoAspectRatio.FULL) {
                capture.takePicture(ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        val saved = try { saveFullFramePhoto(image) } catch (_: Exception) { false } finally { image.close() }
                        if (useScreenFlash) hideScreenFlash()
                        if (::captureButton.isInitialized) captureButton.isEnabled = true
                        if (saved) refreshGalleryThumbnail()
                        Toast.makeText(this@BaseSignatureCameraActivity, if (saved) "FULL photo saved to Gallery" else "FULL photo save failed", Toast.LENGTH_SHORT).show()
                    }
                    override fun onError(exception: ImageCaptureException) {
                        if (useScreenFlash) hideScreenFlash()
                        if (::captureButton.isInitialized) captureButton.isEnabled = true
                        Toast.makeText(this@BaseSignatureCameraActivity, "Photo failed: ${exception.message}", Toast.LENGTH_LONG).show()
                    }
                })
            } else {
                capture.takePicture(options, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        if (useScreenFlash) hideScreenFlash()
                        if (::captureButton.isInitialized) captureButton.isEnabled = true
                        refreshGalleryThumbnail()
                        Toast.makeText(this@BaseSignatureCameraActivity, "Photo saved to Gallery", Toast.LENGTH_SHORT).show()
                    }
                    override fun onError(exception: ImageCaptureException) {
                        if (useScreenFlash) hideScreenFlash()
                        if (::captureButton.isInitialized) captureButton.isEnabled = true
                        Toast.makeText(this@BaseSignatureCameraActivity, "Photo failed: ${exception.message}", Toast.LENGTH_LONG).show()
                    }
                })
            }
        }
        if (useScreenFlash) handler.postDelayed(captureAction, 120L) else captureAction.run()
    }

    private fun saveFullFramePhoto(image: ImageProxy): Boolean {
        val plane = image.planes.firstOrNull() ?: return false
        val buffer = plane.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return false
        val rotation = image.imageInfo.rotationDegrees
        val upright = if (rotation != 0) {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also {
                if (it !== decoded) decoded.recycle()
            }
        } else decoded
        val root = previewView.parent as? FrameLayout
        val targetAspect = if (root != null && root.width > 0 && root.height > 0) root.width.toFloat() / root.height.toFloat()
            else resources.displayMetrics.widthPixels.toFloat() / resources.displayMetrics.heightPixels.toFloat()
        val sourceAspect = upright.width.toFloat() / upright.height.toFloat()
        val cropWidth: Int
        val cropHeight: Int
        if (sourceAspect > targetAspect) {
            cropHeight = upright.height
            cropWidth = (cropHeight * targetAspect).toInt().coerceIn(1, upright.width)
        } else {
            cropWidth = upright.width
            cropHeight = (cropWidth / targetAspect).toInt().coerceIn(1, upright.height)
        }
        val cropped = Bitmap.createBitmap(upright, (upright.width - cropWidth) / 2, (upright.height - cropHeight) / 2, cropWidth, cropHeight)
        if (cropped !== upright) upright.recycle()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "Signature_FULL_$stamp.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SignatureCameraPro")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        var uri: Uri? = null
        return try {
            val savedUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Could not create image file")
            uri = savedUri
            contentResolver.openOutputStream(savedUri)?.use { stream ->
                if (!cropped.compress(Bitmap.CompressFormat.JPEG, jpegQuality.coerceIn(85, 100), stream)) {
                    throw IllegalStateException("JPEG encoding failed")
                }
            } ?: throw IllegalStateException("Could not open image file")
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                contentResolver.update(savedUri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            }
            true
        } catch (_: Exception) {
            uri?.let { try { contentResolver.delete(it, null, null) } catch (_: Exception) { } }
            false
        } finally {
            if (!cropped.isRecycled) cropped.recycle()
        }
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
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                previewView.post { startCamera() }
            }
            else Toast.makeText(this, "Camera permission is required.", Toast.LENGTH_LONG).show()
        } else if (requestCode == AUDIO_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED && videoMode) toggleVideoRecording()
            else Toast.makeText(this, "Microphone permission denied; video audio cannot be recorded", Toast.LENGTH_LONG).show()
        }
    }

    private fun zoomProgressForRatio(ratio: Float, min: Float, max: Float): Int {
        val span = (max / min).coerceAtLeast(1f)
        if (span <= 1f) return 0
        return ((kotlin.math.ln((ratio / min).coerceAtLeast(1f)) / kotlin.math.ln(span)) * 100f).toInt().coerceIn(0, 100)
    }

    private class ZoomRulerView(
        context: android.content.Context,
        private val accentColor: Int,
        private val onProgressChanged: (Int) -> Unit
    ) : View(context) {
        private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF8B8B91.toInt()
            strokeWidth = context.resources.displayMetrics.density
        }
        private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            strokeWidth = context.resources.displayMetrics.density * 1.6f
        }
        private var progress = 0

        fun setProgress(value: Int) {
            progress = value.coerceIn(0, 100)
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val count = 41
            val usable = (width - paddingLeft - paddingRight).toFloat()
            val step = usable / (count - 1)
            val centerY = height / 2f
            for (i in 0 until count) {
                val x = paddingLeft + i * step
                val major = i % 5 == 0
                val active = kotlin.math.abs(i * 100f / (count - 1) - progress) < (100f / (count - 1) / 2f)
                val paint = if (active) activePaint else tickPaint
                val length = if (major) height * 0.78f else height * 0.38f
                canvas.drawLine(x, centerY - length / 2f, x, centerY + length / 2f, paint)
            }
            val markerX = paddingLeft + usable * progress / 100f
            canvas.drawLine(markerX, 0f, markerX, height.toFloat(), activePaint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                    val usable = (width - paddingLeft - paddingRight).toFloat().coerceAtLeast(1f)
                    val value = (((event.x - paddingLeft) / usable) * 100f).toInt().coerceIn(0, 100)
                    setProgress(value)
                    onProgressChanged(value)
                    return true
                }
                MotionEvent.ACTION_CANCEL -> return true
            }
            return true
        }
    }

    private class GridOverlay(context: android.content.Context, accentColor: Int) : View(context) {
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

    private class FocusOverlay(context: android.content.Context, private val accentColor: Int) : View(context) {
        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            strokeWidth = 3f
            style = Paint.Style.STROKE
        }
        private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            strokeWidth = 5f
            strokeCap = Paint.Cap.ROUND
            style = Paint.Style.STROKE
        }
        private var focusX = 0f
        private var focusY = 0f
        private var resultColor = accentColor

        fun showAt(x: Float, y: Float) {
            focusX = x
            focusY = y
            resultColor = accentColor
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
