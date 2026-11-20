package com.ariesananth.signaturecamerapro

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
import android.net.Uri
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
    private lateinit var videoModeButton: Button
    private lateinit var videoQualityButton: Button
    private lateinit var videoFpsButton: Button
    private lateinit var videoStabilizationButton: Button
    private lateinit var captureButton: Button
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var videoMode = false
    private var videoQuality = Quality.FHD
    private var videoFps = 30
    private var videoStabilizationEnabled = false
    private var availableFpsRanges: Array<Range<Int>> = emptyArray()
    private var activeIsoMin = 100
    private var activeIsoMax = 12800
    private var activeShutterMinNs = 51_265L
    private var activeShutterMaxNs = 170_494_792L
    private var manualIso = 400
    private var manualShutterNs = 10_000_000L
    private var minimumFocusDistance = 0f
    private var manualFocusDiopters = 0f
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
    private var exposureIndex = 0
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK
        val cameraPrefs = getSharedPreferences("signature_camera_settings", MODE_PRIVATE)
        gridEnabled = cameraPrefs.getBoolean("grid_enabled", false)
        timerSeconds = cameraPrefs.getInt("timer_seconds", 0).let { if (it == 0 || it == 3 || it == 10) it else 0 }
        buildCameraUi()
        if (hasCameraPermission()) startCamera()
        else requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST)
    }

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

        val scaleDetector = android.view.ScaleGestureDetector(this,
            object : android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: android.view.ScaleGestureDetector): Boolean {
                    val state = camera?.cameraInfo?.zoomState?.value ?: return true
                    val ratio = (state.zoomRatio * detector.scaleFactor)
                        .coerceIn(state.minZoomRatio, state.maxZoomRatio)
                    camera?.cameraControl?.setZoomRatio(ratio)
                    zoomLabel.text = "ZOOM  %.1fx".format(Locale.US, ratio)
                    return true
                }
            })
        var multiTouchGesture = false
        previewView.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_POINTER_DOWN -> multiTouchGesture = true
                MotionEvent.ACTION_UP -> {
                    if (!multiTouchGesture) focusAt(event.x, event.y)
                    multiTouchGesture = false
                }
                MotionEvent.ACTION_CANCEL -> multiTouchGesture = false
            }
            true
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(12, 10, 12, 10)
            background = GradientDrawable().apply { cornerRadius = 22f; setColor(0xCC080B12.toInt()); setStroke(1, 0x44FFFFFF) }
        }
        val title = TextView(this).apply {
            text = "MOTOROLA SIGNATURE"
            textSize = 14f
            letterSpacing = 0.12f
            setTextColor(-1)
            gravity = Gravity.CENTER
        }
        zoomLabel = TextView(this).apply {
            text = "CAMERA  •  ZOOM 1.0x"
            textSize = 12f
            setTextColor(-1)
            gravity = Gravity.CENTER
            setPadding(0, 6, 0, 2)
        }
        val zoom = SeekBar(this).apply {
            max = 100
            progress = 0
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val state = camera?.cameraInfo?.zoomState?.value ?: return
                    val ratio = state.minZoomRatio + (state.maxZoomRatio - state.minZoomRatio) * progress / 100f
                    camera?.cameraControl?.setZoomRatio(ratio)
                    zoomLabel.text = "ZOOM  %.1fx".format(Locale.US, ratio)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        top.addView(title)
        top.addView(zoomLabel)
        top.addView(zoom, LinearLayout.LayoutParams(-1, 42))
        lensRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(0, 4, 0, 2) }
        top.addView(lensRow, LinearLayout.LayoutParams(-1, 44))
        root.addView(top, FrameLayout.LayoutParams(-1, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP))

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(12, 10, 12, 16)
            background = GradientDrawable().apply { cornerRadii = floatArrayOf(28f, 28f, 28f, 28f, 0f, 0f, 0f, 0f); setColor(0xF20A0E18.toInt()); setStroke(1, 0x33FFFFFF) }
            elevation = 24f
            clipChildren = false
            clipToPadding = false
        }
        // Spacious two-row control deck: exposure controls stay separate from utility toggles.
        val exposureRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }
        exposureLabel = TextView(this).apply {
            text = "EV  0"
            textSize = 13f
            setTextColor(-1)
            gravity = Gravity.CENTER
            setPadding(10, 0, 10, 0)
        }
        exposureRow.addView(exposureLabel, LinearLayout.LayoutParams(0, 48, 1f))
        exposureRow.addView(smallButton("−  EV") { changeExposure(-1) }, LinearLayout.LayoutParams(0, 46, 1f).apply { setMargins(3, 0, 3, 0) })
        exposureRow.addView(smallButton("+  EV") { changeExposure(1) }, LinearLayout.LayoutParams(0, 46, 1f).apply { setMargins(3, 0, 3, 0) })
        panel.addView(exposureRow)

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
        utilityRow.addView(gridButton, LinearLayout.LayoutParams(0, 48, 1f).apply { setMargins(2, 0, 2, 0) })
        utilityRow.addView(timerButton, LinearLayout.LayoutParams(0, 44, 1f).apply { setMargins(2, 0, 2, 0) })
        utilityRow.addView(galleryButton, LinearLayout.LayoutParams(0, 44, 1f).apply { setMargins(2, 0, 2, 0) })
        utilityRow.addView(toolsButton, LinearLayout.LayoutParams(0, 44, 1f).apply { setMargins(2, 0, 2, 0) })
        val proButton = smallButton("PRO CONTROLS") {
            if (!proModeEnabled && selectedPhotoExtension != null) {
                Toast.makeText(this, "Turn off the OEM photo effect before using manual Pro controls", Toast.LENGTH_SHORT).show()
                return@smallButton
            }
            proModeEnabled = !proModeEnabled
            proPanel.visibility = if (proModeEnabled) View.VISIBLE else View.GONE
            (it as Button).text = if (proModeEnabled) "PRO ON" else "PRO CONTROLS"
            if (!proModeEnabled) resetManualCapture()
        }
        utilityRow.addView(proButton, LinearLayout.LayoutParams(0, 48, 1f).apply { setMargins(3, 0, 3, 0) })
        panel.addView(utilityRow)

        photoExtensionButton = smallButton("PHOTO EFFECT: CHECKING…") { cyclePhotoExtension() }
        photoExtensionButton.isEnabled = false
        panel.addView(photoExtensionButton, LinearLayout.LayoutParams(-1, 44).apply { setMargins(3, 4, 3, 4) })

        proPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(10, 8, 10, 8)
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
                    if (fromUser && proModeEnabled) applyManualCapture()
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
                    if (fromUser && proModeEnabled) applyManualCapture()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        proPanel.addView(isoValue)
        proPanel.addView(isoSlider, LinearLayout.LayoutParams(-1, 40))
        proPanel.addView(shutterValue)
        proPanel.addView(shutterSlider, LinearLayout.LayoutParams(-1, 40))
        focusValue = TextView(this).apply { text = "FOCUS  AUTO"; textSize = 12f; setTextColor(-1) }
        focusSlider = SeekBar(this).apply {
            max = 1000
            progress = 0
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    manualFocusDiopters = minimumFocusDistance * (progress.coerceIn(0, 1000) / 1000f)
                    focusValue.text = if (progress == 0) "FOCUS  INFINITY" else "FOCUS  %.2f D".format(Locale.US, manualFocusDiopters)
                    if (fromUser && proModeEnabled && minimumFocusDistance > 0f) {
                        manualFocusEnabled = true
                        applyManualCapture()
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        proPanel.addView(focusValue)
        proPanel.addView(focusSlider, LinearLayout.LayoutParams(-1, 40))
        whiteBalanceButton = smallButton("WHITE BALANCE: AUTO") {
            cycleWhiteBalance()
        }
        proPanel.addView(whiteBalanceButton, LinearLayout.LayoutParams(-1, 44).apply { setMargins(0, 4, 0, 4) })
        sceneModeButton = smallButton("SCENE: AUTO") { cycleSceneMode() }
        proPanel.addView(sceneModeButton, LinearLayout.LayoutParams(-1, 44).apply { setMargins(0, 4, 0, 4) })
        proPanel.addView(smallButton("CAPTURE RAW / DNG") { view ->
            (view as Button).isEnabled = false
            view.text = "CAPTURING RAW…"
            captureRawDng(view)
        }, LinearLayout.LayoutParams(-1, 48).apply { setMargins(0, 8, 0, 0) })
        panel.addView(proPanel)

        val videoRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 5, 0, 5)
        }
        videoModeButton = smallButton("PHOTO MODE") {
            if (recording != null) {
                Toast.makeText(this, "Stop recording before changing mode", Toast.LENGTH_SHORT).show()
                return@smallButton
            }
            videoMode = !videoMode
            videoModeButton.text = if (videoMode) "VIDEO MODE" else "PHOTO MODE"
            captureButton.text = if (videoMode) "● REC" else "●"
            startCamera()
        }
        videoQualityButton = smallButton("FHD") { cycleVideoQuality() }
        videoFpsButton = smallButton("30 FPS") { cycleVideoFps() }
        videoStabilizationButton = smallButton("STABILIZATION OFF") { toggleVideoStabilization() }
        videoRow.addView(videoModeButton, LinearLayout.LayoutParams(0, 44, 1.15f).apply { setMargins(3, 0, 3, 0) })
        videoRow.addView(videoQualityButton, LinearLayout.LayoutParams(0, 44, 0.8f).apply { setMargins(3, 0, 3, 0) })
        videoRow.addView(videoFpsButton, LinearLayout.LayoutParams(0, 44, 0.8f).apply { setMargins(3, 0, 3, 0) })
        videoRow.addView(videoStabilizationButton, LinearLayout.LayoutParams(0, 44, 1.2f).apply { setMargins(3, 0, 3, 0) })
        panel.addView(videoRow)

        val mainRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(-1, -2)
            clipChildren = false
            clipToPadding = false
            elevation = 16f
        }
        val flip = Button(this).apply {
            text = "FRONT / BACK"
            isAllCaps = false
            textSize = 12f
            setTextColor(android.graphics.Color.WHITE)
            background = GradientDrawable().apply { cornerRadius = 18f; setColor(0xFF344054.toInt()) }
            elevation = 8f
            isClickable = true
            isFocusable = true
            minWidth = 0
            minHeight = 58
            setPadding(4, 0, 4, 0)
            setOnClickListener {
                if (recording != null) {
                    Toast.makeText(this@MainActivity, "Stop recording before switching cameras", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA)
                    CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                selectedPhotoExtension = null
                refreshPhotoExtensions()
                startCamera()
            }
        }
        captureButton = Button(this).apply {
            text = "●"
            textSize = 30f
            isAllCaps = false
            setTextColor(android.graphics.Color.WHITE)
            background = GradientDrawable().apply { cornerRadius = 36f; setColor(0xFF1677FF.toInt()) }
            elevation = 10f
            isClickable = true
            isFocusable = true
            minWidth = 0
            minHeight = 64
            setPadding(4, 0, 4, 0)
            contentDescription = "Take photo"
            setOnClickListener {
                if (videoMode) {
                    toggleVideoRecording()
                } else if (timerSeconds == 0) {
                    takePhoto()
                } else {
                    val delay = timerSeconds
                    text = "..."
                    isEnabled = false
                    handler.postDelayed({
                        isEnabled = true
                        text = "●"
                        takePhoto()
                    }, delay * 1000L)
                }
            }
        }
        val flash = Button(this).apply {
            text = "FLASH OFF"
            isAllCaps = false
            textSize = 12f
            setTextColor(android.graphics.Color.WHITE)
            backgroundTintList = ColorStateList.valueOf(0xFF344054.toInt())
            minWidth = 0
            minHeight = 58
            setPadding(4, 0, 4, 0)
            setOnClickListener {
                if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) {
                    Toast.makeText(this@MainActivity, "Flash is not available on front camera", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (videoMode) {
                    val enabled = text != "TORCH ON"
                    camera?.cameraControl?.enableTorch(enabled)
                    text = if (enabled) "TORCH ON" else "TORCH OFF"
                } else {
                    val image = imageCapture ?: return@setOnClickListener
                    val enabled = image.flashMode != ImageCapture.FLASH_MODE_ON
                    image.flashMode = if (enabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
                    text = if (enabled) "FLASH ON" else "FLASH OFF"
                }
            }
        }
        mainRow.setPadding(4, 8, 4, 8)
        mainRow.addView(flip, LinearLayout.LayoutParams(0, 64, 1f).apply { setMargins(3, 0, 3, 0) })
        mainRow.addView(captureButton, LinearLayout.LayoutParams(0, 64, 1.15f).apply { setMargins(3, 0, 3, 0) })
        mainRow.addView(flash, LinearLayout.LayoutParams(0, 64, 1f).apply { setMargins(3, 0, 3, 0) })
        panel.addView(mainRow)
        val hint = TextView(this).apply {
            text = "TAP TO FOCUS     •     PINCH TO ZOOM"
            textSize = 11f
            setTextColor(0xFFBBBBBB.toInt())
            gravity = Gravity.CENTER
        }
        panel.addView(hint)
        root.addView(panel, FrameLayout.LayoutParams(-1, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        panel.bringToFront()
        setContentView(root)
    }

    private fun smallButton(label: String, action: (View) -> Unit) =
        Button(this).apply {
            text = label
            textSize = 13f
            isAllCaps = false
            setTextColor(android.graphics.Color.WHITE)
            background = GradientDrawable().apply { cornerRadius = 14f; setColor(0xFF475467.toInt()) }
            elevation = 6f
            isClickable = true
            isFocusable = true
            minWidth = 0
            minHeight = 52
            contentDescription = label
            setPadding(10, 0, 10, 0)
            setOnClickListener { action(it) }
        }

    private fun discoverRearLenses() {
        lensRow.removeAllViews()
        val manager = getSystemService(CAMERA_SERVICE) as CameraManager
        val rearId = manager.cameraIdList.firstOrNull { id -> manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK } ?: return
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
            button.text = label
            button.textSize = 12f
            button.isAllCaps = false
            button.setTextColor(android.graphics.Color.WHITE)
            button.background = GradientDrawable().apply { cornerRadius = 14f; setColor(if (kotlin.math.abs(ratio - 1f) < 0.12f) 0xFF1677FF.toInt() else 0xFF344054.toInt()) }
            button.minWidth = 0
            button.minHeight = 48
            button.setPadding(8, 0, 8, 0)
            // OEM extensions can change the active camera pipeline; avoid presenting
            // optical-lens presets as reliable while a vendor effect is active.
            button.isEnabled = selectedPhotoExtension == null
            button.alpha = if (button.isEnabled) 1f else 0.45f
            button.contentDescription = if (button.isEnabled)
                "Switch logical rear camera to the $label lens view"
            else "Lens presets are temporarily unavailable while an OEM photo effect is active"
            button.setOnClickListener {
                if (selectedPhotoExtension != null) {
                    Toast.makeText(this, "Turn off the OEM photo effect to use lens presets", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (cameraSelector != CameraSelector.DEFAULT_BACK_CAMERA) return@setOnClickListener
                val state = camera?.cameraInfo?.zoomState?.value ?: return@setOnClickListener
                val target = ratio.coerceIn(state.minZoomRatio, state.maxZoomRatio)
                camera?.cameraControl?.setZoomRatio(target)
                zoomLabel.text = "LENS VIEW  $label"
                for (i in 0 until lensRow.childCount) {
                    val child = lensRow.getChildAt(i) as? Button ?: continue
                    child.background = GradientDrawable().apply { cornerRadius = 14f; setColor(if (child.text == label) 0xFF1677FF.toInt() else 0xFF344054.toInt()) }
                }
            }
            lensRow.addView(button, LinearLayout.LayoutParams(0, 48, 1f).apply { setMargins(3, 0, 3, 0) })
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
            manualFocusDiopters = 0f
            awbModeIndex = 0
            focusSlider.progress = 0
            focusValue.text = "FOCUS  AUTO"
            whiteBalanceButton.text = "WHITE BALANCE: AUTO"
        } catch (_: Exception) { }
    }

    private fun toggleVideoRecording() {
        val activeVideo = videoCapture ?: run {
            Toast.makeText(this, "Video camera is not ready", Toast.LENGTH_SHORT).show()
            return
        }
        val current = recording
        if (current != null) {
            current.stop()
            captureButton.isEnabled = false
            captureButton.text = "SAVING…"
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
        val output = MediaStoreOutputOptions.Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(values)
            .build()
        try {
            recording = activeVideo.output.prepareRecording(this, output)
                .start(ContextCompat.getMainExecutor(this)) { event ->
                    when (event) {
                        is VideoRecordEvent.Start -> {
                            captureButton.text = "■ STOP"
                            Toast.makeText(this, "Video recording started", Toast.LENGTH_SHORT).show()
                        }
                        is VideoRecordEvent.Finalize -> {
                            recording = null
                            captureButton.isEnabled = true
                            captureButton.text = "● REC"
                            if (event.hasError()) {
                                Toast.makeText(this, "Video failed: ${event.error}", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(this, "Video saved to Gallery", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
        } catch (e: Exception) {
            recording = null
            captureButton.isEnabled = true
            captureButton.text = "● REC"
            Toast.makeText(this, "Cannot start video: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
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
            videoQualityButton.text = when (videoQuality) {
                Quality.UHD -> "4K"
                Quality.FHD -> "FHD"
                Quality.HD -> "HD"
                else -> "SD"
            }
            val manager = getSystemService(CAMERA_SERVICE) as CameraManager
            val facing = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA)
                CameraCharacteristics.LENS_FACING_BACK else CameraCharacteristics.LENS_FACING_FRONT
            val id = manager.cameraIdList.firstOrNull {
                manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == facing
            }
            availableFpsRanges = id?.let {
                manager.getCameraCharacteristics(it).get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            } ?: emptyArray()
            if (availableFpsRanges.none { it.lower == videoFps && it.upper == videoFps }) videoFps = 30
            videoFpsButton.text = "$videoFps FPS"
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
        videoQualityButton.text = when (videoQuality) {
            Quality.UHD -> "4K"
            Quality.FHD -> "FHD"
            Quality.HD -> "HD"
            else -> "SD"
        }
        if (recording == null) startCamera()
    }

    private fun cycleVideoFps() {
        if (recording != null) {
            Toast.makeText(this, "Stop recording before changing frame rate", Toast.LENGTH_SHORT).show()
            return
        }
        val choices = listOf(30, 60).filter { fps -> availableFpsRanges.any { it.lower == fps && it.upper == fps } }
        if (choices.isEmpty()) {
            Toast.makeText(this, "30/60 FPS modes are not exposed by this camera", Toast.LENGTH_SHORT).show()
            return
        }
        val index = choices.indexOf(videoFps).let { if (it < 0) -1 else it }
        videoFps = choices[(index + 1) % choices.size]
        videoFpsButton.text = "$videoFps FPS"
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
        applyVideoRequestOptions()
    }

    private fun applyVideoRequestOptions() {
        val activeCamera = camera ?: return
        if (!videoMode) return
        try {
            val builder = CaptureRequestOptions.Builder()
            val fpsRange = availableFpsRanges.firstOrNull { it.lower == videoFps && it.upper == videoFps }
            if (fpsRange != null) builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange)
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
            else -> "AUTO"
        }
        sceneModeButton.text = "SCENE: $label"
        sceneModeButton.contentDescription = "Camera hardware scene preset: $label. Not computational portrait blur or multi-frame night processing."
        sceneModeButton.isEnabled = supportedSceneModes.size > 1
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

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

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
                    val recorder = Recorder.Builder()
                        .setQualitySelector(QualitySelector.from(videoQuality, FallbackStrategy.lowerQualityOrHigherThan(Quality.HD)))
                        .build()
                    videoCapture = VideoCapture.withOutput(recorder)
                    camera = provider.bindToLifecycle(this, cameraSelector, preview, videoCapture)
                    updateVideoCapabilities()
                    photoExtensionButton.isEnabled = false
                } else {
                    videoCapture = null
                    imageCapture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .setFlashMode(ImageCapture.FLASH_MODE_OFF)
                        .build()
                    camera = provider.bindToLifecycle(this, activeSelector, preview, imageCapture)
                    refreshPhotoExtensions()
                }
                updateManualRanges()
                if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) discoverRearLenses() else {
                    lensRow.removeAllViews()
                    lensRow.addView(TextView(this).apply { text = "FRONT CAMERA"; textSize = 10f; setTextColor(0xFFCCCCCC.toInt()); gravity = Gravity.CENTER })
                }
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
                        imageCapture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).setFlashMode(ImageCapture.FLASH_MODE_OFF).build()
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
            val id = manager.cameraIdList.firstOrNull {
                manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == wantedFacing
            } ?: return
            val characteristics = manager.getCameraCharacteristics(id)
            minimumFocusDistance = characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
            manualFocusEnabled = false
            focusSlider.isEnabled = minimumFocusDistance > 0f
            focusValue.text = if (minimumFocusDistance > 0f) "FOCUS  AUTO  (0–%.1f D)".format(Locale.US, minimumFocusDistance) else "FOCUS  AUTO (FIXED FOCUS)"
            focusSlider.progress = 0
            supportedAwbModes = (characteristics.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)
                ?: intArrayOf(CaptureRequest.CONTROL_AWB_MODE_AUTO))
                .filter { it in CaptureRequest.CONTROL_AWB_MODE_AUTO..CaptureRequest.CONTROL_AWB_MODE_SHADE }
                .toIntArray()
                .let { modes -> if (modes.contains(CaptureRequest.CONTROL_AWB_MODE_AUTO)) modes else intArrayOf(CaptureRequest.CONTROL_AWB_MODE_AUTO) + modes }
            awbModeIndex = 0
            whiteBalanceButton.text = "WHITE BALANCE: AUTO"
            val availableScenes = characteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES)
                ?: intArrayOf(CaptureRequest.CONTROL_SCENE_MODE_DISABLED)
            val preferredScenes = listOf(
                CaptureRequest.CONTROL_SCENE_MODE_DISABLED,
                CaptureRequest.CONTROL_SCENE_MODE_PORTRAIT,
                CaptureRequest.CONTROL_SCENE_MODE_NIGHT
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
        val cameraId = manager.cameraIdList.firstOrNull {
            manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == facing
        }
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
            val provider = providerFuture.get()
            provider.unbindAll()

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

    private fun takePhoto() {
        val capture = imageCapture ?: return
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
        capture.takePicture(options, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                Toast.makeText(this@MainActivity, "Photo saved to Gallery", Toast.LENGTH_SHORT).show()
            }
            override fun onError(exception: ImageCaptureException) {
                Toast.makeText(this@MainActivity, "Photo failed: ${exception.message}", Toast.LENGTH_LONG).show()
            }
        })
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) startCamera()
            else Toast.makeText(this, "Camera permission is required.", Toast.LENGTH_LONG).show()
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
    }
}
