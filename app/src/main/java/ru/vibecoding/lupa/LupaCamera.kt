package ru.vibecoding.lupa

import android.graphics.Bitmap
import android.graphics.ColorFilter
import android.graphics.Paint
import android.view.View
import androidx.activity.ComponentActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Работа с камерой через CameraX. Камера привязана к жизненному циклу Activity:
 * в фоне она выключается сама.
 */
class LupaCamera(
    private val activity: ComponentActivity,
    val previewView: PreviewView,
    private val callbacks: Callbacks,
) {
    interface Callbacks {
        fun onCameraReady(hasFlash: Boolean, hasFrontCamera: Boolean)
        fun onZoomChanged(ratio: Float, linear: Float)
        fun onFocusResult(success: Boolean)
    }

    private val mainExecutor = ContextCompat.getMainExecutor(activity)
    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var torchWanted = false
    private var useFront = false
    private var started = false

    val isActive: Boolean get() = camera != null

    fun start(front: Boolean) {
        useFront = front
        started = true
        provider?.let { bind(it); return }
        val future = ProcessCameraProvider.getInstance(activity)
        future.addListener({
            val p = runCatching { future.get() }.getOrNull() ?: return@addListener
            provider = p
            if (started) bind(p)
        }, mainExecutor)
    }

    fun stop() {
        started = false
        camera?.cameraInfo?.zoomState?.removeObservers(activity)
        provider?.unbindAll()
        camera = null
    }

    private fun bind(p: ProcessCameraProvider) {
        camera?.cameraInfo?.zoomState?.removeObservers(activity)
        p.unbindAll()
        camera = null

        val hasFront = runCatching { p.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) }.getOrDefault(false)
        val hasBack = runCatching { p.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) }.getOrDefault(false)
        val selector = when {
            useFront && hasFront -> CameraSelector.DEFAULT_FRONT_CAMERA
            hasBack -> CameraSelector.DEFAULT_BACK_CAMERA
            else -> CameraSelector.DEFAULT_FRONT_CAMERA
        }

        val builder = Preview.Builder()
        val info = runCatching { selector.filter(p.availableCameraInfos).firstOrNull() }.getOrNull()
        val stabilization = info != null && runCatching {
            Preview.getPreviewCapabilities(info).isStabilizationSupported
        }.getOrDefault(false)
        if (stabilization) builder.setPreviewStabilizationEnabled(true)
        val preview = builder.build()
        preview.setSurfaceProvider(previewView.surfaceProvider)

        val cam = runCatching { p.bindToLifecycle(activity, selector, preview) }.getOrNull() ?: return
        camera = cam
        cam.cameraInfo.zoomState.observe(activity) { z ->
            callbacks.onZoomChanged(z.zoomRatio, z.linearZoom)
        }
        val hasFlash = cam.cameraInfo.hasFlashUnit()
        callbacks.onCameraReady(hasFlash, hasFront && hasBack)
        if (torchWanted && hasFlash) cam.cameraControl.enableTorch(true)
    }

    fun setTorch(on: Boolean) {
        torchWanted = on
        val cam = camera ?: return
        if (cam.cameraInfo.hasFlashUnit()) cam.cameraControl.enableTorch(on)
    }

    fun setLinearZoom(value: Float) {
        camera?.cameraControl?.setLinearZoom(value.coerceIn(0f, 1f))
    }

    /** Шаг зума; false — уже упёрлись в предел (повод для вибрации). */
    fun zoomStep(delta: Float): Boolean {
        val z = camera?.cameraInfo?.zoomState?.value ?: return false
        val target = (z.linearZoom + delta).coerceIn(0f, 1f)
        if (abs(target - z.linearZoom) < 0.001f) return false
        setLinearZoom(target)
        return true
    }

    fun pinch(factor: Float) {
        val cam = camera ?: return
        val z = cam.cameraInfo.zoomState.value ?: return
        cam.cameraControl.setZoomRatio((z.zoomRatio * factor).coerceIn(z.minZoomRatio, z.maxZoomRatio))
    }

    fun focus(x: Float, y: Float, lock: Boolean) {
        val cam = camera ?: return
        val point = previewView.meteringPointFactory.createPoint(x, y)
        val builder = FocusMeteringAction.Builder(
            point,
            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE,
        )
        if (lock) builder.disableAutoCancel() else builder.setAutoCancelDuration(4, TimeUnit.SECONDS)
        val future = cam.cameraControl.startFocusAndMetering(builder.build())
        future.addListener({
            // Отмену (новый тап) не считаем неудачей.
            val ok = runCatching { future.get().isFocusSuccessful }.getOrDefault(true)
            callbacks.onFocusResult(ok)
        }, mainExecutor)
    }

    /** Текущий кадр в размере экрана, как он показан пользователю. */
    fun grabFrame(): Bitmap? = previewView.bitmap

    /** Живой фильтр: аппаратный слой с цветовой матрицей, без OpenGL и анализа кадров. */
    fun setFilter(filter: ColorFilter?) {
        if (filter == null) {
            previewView.setLayerType(View.LAYER_TYPE_NONE, null)
        } else {
            previewView.setLayerType(View.LAYER_TYPE_HARDWARE, Paint().apply { colorFilter = filter })
        }
    }
}
