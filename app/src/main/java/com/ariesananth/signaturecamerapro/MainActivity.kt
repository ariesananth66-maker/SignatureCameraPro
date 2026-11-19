package com.ariesananth.signaturecamerapro

import android.Manifest
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
import android.widget.TextView
import android.widget.Toast
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
            setBackgroundColor(0xDD000000.toInt())
        }
        val infoRow = LinearLayout(this).apply { gravity = Gravity.CENTER }
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
        infoRow.addView(evMinus)
        infoRow.addView(evPlus)
        val gridButton = smallButton("GRID OFF") {
            gridEnabled = !gridEnabled
            gridView.visibility = if (gridEnabled) View.VISIBLE else View.GONE
            (it as Button).text = if (gridEnabled) "GRID ON" else "GRID OFF"
        }
        val timerButton = smallButton("TIMER OFF") {
            timerSeconds = when (timerSeconds) { 0 -> 3; 3 -> 10; else -> 0 }
            (it as Button).text = if (timerSeconds == 0) "TIMER OFF" else "TIMER ${timerSeconds}s"
        }
        infoRow.addView(gridButton)
        infoRow.addView(timerButton)
        panel.addView(infoRow)

        val mainRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val flip = Button(this).apply {
            text = "FRONT / BACK"
            isAllCaps = false
            textSize = 12f
            setTextColor(android.graphics.Color.WHITE)
            backgroundTintList = ColorStateList.valueOf(0xFF344054.toInt())
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
            backgroundTintList = ColorStateList.valueOf(0xFF1677FF.toInt())
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
        setContentView(root)
    }

    private fun smallButton(label: String, action: (View) -> Unit) =
        Button(this).apply {
            text = label
            textSize = 10f
            isAllCaps = false
            setTextColor(android.graphics.Color.WHITE)
            backgroundTintList = ColorStateList.valueOf(0xFF475467.toInt())
            minWidth = 0
            setPadding(4, 0, 4, 0)
            setOnClickListener { action(it) }
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
