package com.tiss.replaycam.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.util.Range
import android.util.Size
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraManager(application: android.app.Application) : androidx.lifecycle.AndroidViewModel(application) {
    private val context: Context = application.applicationContext

    private val frameBuffer = FrameBuffer(maxDurationSeconds = 35.0)
    private var cameraProvider: ProcessCameraProvider? = null
    private var analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private val _realtimeBitmap = MutableStateFlow<Bitmap?>(null)
    val realtimeBitmap: StateFlow<Bitmap?> = _realtimeBitmap.asStateFlow()

    private val _delayedBitmap = MutableStateFlow<Bitmap?>(null)
    val delayedBitmap: StateFlow<Bitmap?> = _delayedBitmap.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _showSuccess = MutableStateFlow(false)
    val showSuccess: StateFlow<Boolean> = _showSuccess.asStateFlow()

    private val _errorMessage = MutableStateFlow("")
    val errorMessage: StateFlow<String> = _errorMessage.asStateFlow()

    private val _bufferDuration = MutableStateFlow(0.0)
    val bufferDuration: StateFlow<Double> = _bufferDuration.asStateFlow()

    private val _supportedFps = MutableStateFlow<List<Int>>(listOf(30))
    val supportedFps: StateFlow<List<Int>> = _supportedFps.asStateFlow()

    var delaySeconds: Double = 3.0
    var targetFps: Int = 30
    var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    var isMirrored: Boolean = false

    private var lastRealtimeUpdate = 0L
    private var lastDelayedUpdate = 0L
    private val realtimeIntervalNs = 1_000_000_000L / 15
    private val delayedIntervalNs = 1_000_000_000L / 25

    init {
        querySupportedFps()
    }

    private fun querySupportedFps() {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val supported = mutableListOf<Int>()
        try {
            val cameraId = manager.cameraIdList.firstOrNull { id ->
                manager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: manager.cameraIdList.first()
            val chars = manager.getCameraCharacteristics(cameraId)
            val ranges = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            val maxFps = ranges?.maxOfOrNull { it.upper } ?: 30
            if (maxFps >= 30) supported.add(30)
            if (maxFps >= 60) supported.add(60)
            if (maxFps >= 120) supported.add(120)
        } catch (e: Exception) {
            supported.add(30)
        }
        _supportedFps.value = supported
    }

    @SuppressLint("UnsafeOptInUsageError")
    fun startCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCamera(lifecycleOwner, previewView)
        }, ContextCompat.getMainExecutor(context))
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun bindCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        val provider = cameraProvider ?: return
        provider.unbindAll()

        val selector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }

        val analysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(1080, 1920))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()

        analysis.setAnalyzer(analysisExecutor) { imageProxy ->
            processFrame(imageProxy)
        }

        try {
            val camera = provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
            _isRunning.value = true

            val camera2Control = Camera2CameraControl.from(camera.cameraControl)
            val fps = targetFps.coerceIn(1, 120)
            camera2Control.captureRequestOptions = CaptureRequestOptions.Builder()
                .setCaptureRequestOption(
                    CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                    Range(fps, fps)
                )
                .build()
        } catch (e: Exception) {
            _errorMessage.value = "相機啟動失敗：${e.message}"
        }
    }

    private fun processFrame(imageProxy: ImageProxy) {
        val nowNs = System.nanoTime()
        val bitmap = imageProxy.toBitmap(isMirrored) ?: run {
            imageProxy.close()
            return
        }

        val frame = TimestampedFrame(bitmap = bitmap, timestampNanos = nowNs)
        frameBuffer.append(frame)

        if (nowNs - lastRealtimeUpdate >= realtimeIntervalNs) {
            lastRealtimeUpdate = nowNs
            viewModelScope.launch(Dispatchers.Main) {
                _realtimeBitmap.value = bitmap
                _bufferDuration.value = frameBuffer.duration()
            }
        }

        if (nowNs - lastDelayedUpdate >= delayedIntervalNs) {
            lastDelayedUpdate = nowNs
            val targetNs = nowNs - (delaySeconds * 1_000_000_000.0).toLong()
            val delayed = frameBuffer.findFrame(targetNs, toleranceNanos = 500_000_000L)
            if (delayed != null) {
                viewModelScope.launch(Dispatchers.Main) {
                    _delayedBitmap.value = delayed.bitmap
                }
            }
        }

        imageProxy.close()
    }

    fun switchCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK)
            CameraSelector.LENS_FACING_FRONT
        else
            CameraSelector.LENS_FACING_BACK
        isMirrored = false
        frameBuffer.clear()
        bindCamera(lifecycleOwner, previewView)
    }

    fun applyFps(fps: Int, lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        targetFps = fps
        bindCamera(lifecycleOwner, previewView)
    }

    fun saveRecentFrames(durationSeconds: Double, onComplete: (Boolean) -> Unit) {
        if (_isSaving.value) return
        _isSaving.value = true
        val cutoffNs = System.nanoTime() - (durationSeconds * 1_000_000_000.0).toLong()
        val frames = frameBuffer.framesSince(cutoffNs)
        if (frames.isEmpty()) {
            _isSaving.value = false
            onComplete(false)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = com.tiss.replaycam.export.VideoExporter(context).export(frames)
                com.tiss.replaycam.store.ClipStore.getInstance(context).addClip(url)
                withContext(Dispatchers.Main) {
                    _isSaving.value = false
                    _showSuccess.value = true
                    onComplete(true)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _isSaving.value = false
                    _errorMessage.value = "儲存失敗：${e.message}"
                    onComplete(false)
                }
            }
        }
    }

    fun dismissSuccess() { _showSuccess.value = false }

    fun stopCamera() {
        cameraProvider?.unbindAll()
        _isRunning.value = false
    }

    override fun onCleared() {
        super.onCleared()
        analysisExecutor.shutdown()
        frameBuffer.clear()
    }
}

private fun ImageProxy.toBitmap(mirror: Boolean): Bitmap? {
    return try {
        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()
        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, width, height), 60, out)
        val bytes = out.toByteArray()
        var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        if (mirror) {
            val matrix = Matrix().apply { preScale(-1f, 1f) }
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, false)
        }
        bmp
    } catch (e: Exception) {
        null
    }
}
