package com.example.service

import android.content.Context
import android.graphics.ImageFormat
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import kotlin.math.abs

/**
 * Detector de movimiento visual basado en CameraX con análisis de luminancia YUV_420_888.
 * Compara fotogramas consecutivos sub-muestreados en una grilla de 32x24 para eliminar ruido de alta frecuencia
 * y detectar cambios significativos en el encuadre.
 */
class CameraMotionDetector(private val context: Context) {

    private val executor = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null

    // Estados observables
    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()

    private val _motionScore = MutableStateFlow(0f)
    val motionScore: StateFlow<Float> = _motionScore.asStateFlow()

    private val _lastDetectionTimestamp = MutableStateFlow(0L)
    val lastDetectionTimestamp: StateFlow<Long> = _lastDetectionTimestamp.asStateFlow()

    // Configuración
    var sensitivityPercent: Int = 30 // 10 a 90%
    var minIntervalBetweenAlertsMs: Long = 4000L
    var isEnabled: Boolean = false

    private var previousGrid: IntArray? = null
    private val gridWidth = 32
    private val gridHeight = 24
    private var onMotionDetectedListener: ((confidence: Float) -> Unit)? = null

    fun setOnMotionDetectedListener(listener: (Float) -> Unit) {
        onMotionDetectedListener = listener
    }

    fun startCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView? = null,
        onStarted: (Boolean) -> Unit = {}
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                val preview = previewView?.let {
                    Preview.Builder().build().also { p ->
                        p.setSurfaceProvider(it.surfaceProvider)
                    }
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(executor) { imageProxy ->
                    processImageFrame(imageProxy)
                }

                cameraProvider?.unbindAll()
                if (preview != null) {
                    cameraProvider?.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalysis)
                } else {
                    cameraProvider?.bindToLifecycle(lifecycleOwner, cameraSelector, imageAnalysis)
                }

                _isMonitoring.value = true
                onStarted(true)
            } catch (_: Exception) {
                _isMonitoring.value = false
                onStarted(false)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun processImageFrame(image: ImageProxy) {
        try {
            if (!isEnabled || !_isMonitoring.value) {
                image.close()
                return
            }

            if (image.format != ImageFormat.YUV_420_888 && image.format != ImageFormat.YUV_422_888) {
                image.close()
                return
            }

            // Usar plano Y (luminancia)
            val yPlane = image.planes[0]
            val buffer: ByteBuffer = yPlane.buffer
            val width = image.width
            val height = image.height
            val rowStride = yPlane.rowStride
            val pixelStride = yPlane.pixelStride

            val currentGrid = IntArray(gridWidth * gridHeight)
            val stepX = width / gridWidth
            val stepY = height / gridHeight

            for (gy in 0 until gridHeight) {
                for (gx in 0 until gridWidth) {
                    val px = (gx * stepX).coerceAtMost(width - 1)
                    val py = (gy * stepY).coerceAtMost(height - 1)
                    val index = py * rowStride + px * pixelStride
                    val lum = buffer.get(index).toInt() and 0xFF
                    currentGrid[gy * gridWidth + gx] = lum
                }
            }

            val prev = previousGrid
            if (prev != null) {
                var changedCells = 0
                val threshold = (255 * (1.0f - (sensitivityPercent / 100f))).toInt().coerceIn(15, 80)

                for (i in currentGrid.indices) {
                    val diff = abs(currentGrid[i] - prev[i])
                    if (diff > threshold) {
                        changedCells++
                    }
                }

                val score = (changedCells.toFloat() / currentGrid.size.toFloat()) * 100f
                _motionScore.value = score

                // Si más del 8% de las celdas cambiaron significativamente
                if (score > 8.0f) {
                    val now = System.currentTimeMillis()
                    if (now - _lastDetectionTimestamp.value > minIntervalBetweenAlertsMs) {
                        _lastDetectionTimestamp.value = now
                        val confidence = (score / 100f).coerceIn(0.1f, 1.0f)
                        onMotionDetectedListener?.invoke(confidence)
                    }
                }
            }

            previousGrid = currentGrid
        } catch (_: Exception) {
        } finally {
            image.close()
        }
    }

    fun stopCamera() {
        try {
            cameraProvider?.unbindAll()
            cameraProvider = null
            _isMonitoring.value = false
            previousGrid = null
            _motionScore.value = 0f
        } catch (_: Exception) {}
    }
}
