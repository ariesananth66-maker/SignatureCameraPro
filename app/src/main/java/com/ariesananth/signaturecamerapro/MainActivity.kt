package com.ariesananth.signaturecamerapro

import android.Manifest
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.graphics.ImageFormat
import android.content.ContentValues
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
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.graphics.drawable.GradientDrawable
import androidx.activity.ComponentActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.MeteringPoint
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var gridView: GridOverlay
    private lateinit var zoomLabel: TextView
    private lateinit var exposureLabel: TextView
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var gridEnabled = false
    private var timerSeconds = 0
    private var exposureIndex = 0
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK
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
        gridView.visibility = View.GONE
        root.addView(gridView, FrameLayout.LayoutParams(-1, -1))

        previewView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) focusAt(event.x, event.y)
            true
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(12, 10, 12, 10)
            setBackgroundColor(0x99000000.toInt())
        }
        val title = TextView(this).apply {
            text = "SIGNATURE  •  CAMERA PRO"
            textSize = 14f
            letterSpacing = 0.12f
            setTextColor(-1)
            gravity = Gravity.CENTER
        }
        zoomLabel = TextView(this).apply {
            text = "ZOOM  1.0x"
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
        top.addView(zoom, LinearLayout.LayoutParams(-1, 34))
        root.addView(top, FrameLayout.LayoutParams(-1, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP))

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(8, 8, 8, 18)
            setBackgroundColor(0xF20B0F19.toInt())
            elevation = 24f
            clipChildren = false
            clipToPadding = false
        }
        val infoRow = LinearLayout(this).apply { gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(-1, -2) }
        exposureLabel = TextView(this).apply {
            text = "EV  0"
            textSize = 12f
            setTextColor(-1)
            gravity = Gravity.CENTER
            setPadding(8, 0, 8, 0)
        }
        infoRow.addView(exposureLabel)
        val evMinus = smallButton("EV −") { changeExposure(-1) }
        val evPlus = smallButton("EV +") { changeExposure(1) }
        infoRow.addView(evMinus, LinearLayout.LayoutParams(0, 44, 1f))
        infoRow.addView(evPlus, LinearLayout.LayoutParams(0, 44, 1f))
        val gridButton = smallButton("GRID OFF") {
            gridEnabled = !gridEnabled
            gridView.visibility = if (gridEnabled) View.VISIBLE else View.GONE
            (it as Button).text = if (gridEnabled) "GRID ON" else "GRID OFF"
        }
        val timerButton = smallButton("TIMER OFF") {
            timerSeconds = when (timerSeconds) { 0 -> 3; 3 -> 10; else -> 0 }
            (it as Button).text = if (timerSeconds == 0) "TIMER OFF" else "TIMER ${timerSeconds}s"
        }
        infoRow.addView(gridButton, LinearLayout.LayoutParams(0, 44, 1f))
        infoRow.addView(timerButton, LinearLayout.LayoutParams(0, 44, 1f))
        val hardwareButton = smallButton("HARDWARE") { showHardwareReport() }
        infoRow.addView(hardwareButton, LinearLayout.LayoutParams(0, 44, 1f))
        panel.addView(infoRow)

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
                cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA)
                    CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                startCamera()
            }
        }
        val capture = Button(this).apply {
            text = "●  PHOTO"
            textSize = 20f
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
                if (timerSeconds == 0) takePhoto()
                else {
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
                val image = imageCapture ?: return@setOnClickListener
                if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) {
                    Toast.makeText(this@MainActivity, "Flash is not available on front camera", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val enabled = image.flashMode != ImageCapture.FLASH_MODE_ON
                image.flashMode = if (enabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
                text = if (enabled) "FLASH ON" else "FLASH OFF"
            }
        }
        mainRow.setPadding(4, 8, 4, 8)
        mainRow.addView(flip, LinearLayout.LayoutParams(0, 64, 1f).apply { setMargins(3, 0, 3, 0) })
        mainRow.addView(capture, LinearLayout.LayoutParams(0, 64, 1.15f).apply { setMargins(3, 0, 3, 0) })
        mainRow.addView(flash, LinearLayout.LayoutParams(0, 64, 1f).apply { setMargins(3, 0, 3, 0) })
        panel.addView(mainRow)
        val hint = TextView(this).apply {
            text = "Tap preview to focus  •  Pinch/slider to zoom"
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
            textSize = 10f
            isAllCaps = false
            setTextColor(android.graphics.Color.WHITE)
            background = GradientDrawable().apply { cornerRadius = 14f; setColor(0xFF475467.toInt()) }
            elevation = 6f
            isClickable = true
            isFocusable = true
            minWidth = 0
            setPadding(4, 0, 4, 0)
            setOnClickListener { action(it) }
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
            val capNames = caps.mapNotNull { cap ->
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
            }
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
                .append("\nPhysical camera IDs: ").append(c.physicalCameraIds.joinToString().ifBlank { "Not exposed" })
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
        activeCamera.cameraControl.startFocusAndMetering(action)
        Toast.makeText(this, "Focusing…", Toast.LENGTH_SHORT).show()
    }

    private fun changeExposure(delta: Int) {
        val activeCamera = camera ?: return
        val range = activeCamera.cameraInfo.exposureState.exposureCompensationRange
        exposureIndex = (exposureIndex + delta).coerceIn(range.lower, range.upper)
        activeCamera.cameraControl.setExposureCompensationIndex(exposureIndex)
        exposureLabel.text = "EV  ${exposureIndex}"
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setFlashMode(ImageCapture.FLASH_MODE_OFF)
                .build()
            try {
                provider.unbindAll()
                camera = provider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
                exposureIndex = 0
                exposureLabel.text = "EV  0"
            } catch (e: Exception) {
                Toast.makeText(this, "Unable to start camera: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val capture = imageCapture ?: return
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "Signature_$timestamp.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
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

    companion object {
        private const val CAMERA_PERMISSION_REQUEST = 1001
    }
}
