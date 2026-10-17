package ru.vibecoding.lupa

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.camera.view.PreviewView
import ru.vibecoding.lupa.ui.LupaApp
import ru.vibecoding.lupa.ui.LupaTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MagnifierViewModel by viewModels()
    private lateinit var camera: LupaCamera

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        // Максимальная яркость только для нашего окна; системная настройка не меняется.
        window.attributes = window.attributes.apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
        }

        val previewView = PreviewView(this).apply {
            // TextureView нужен, чтобы живой фильтр (цветовая матрица слоя) работал.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
        camera = LupaCamera(this, previewView, viewModel)

        if (savedInstanceState == null) handleShortcut(intent)

        setContent {
            LupaTheme {
                LupaApp(viewModel, camera)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShortcut(intent)
    }

    private fun handleShortcut(intent: Intent?) {
        if (intent?.action == ACTION_TORCH) viewModel.setTorch(true)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val delta = when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> ZOOM_STEP
            KeyEvent.KEYCODE_VOLUME_DOWN -> -ZOOM_STEP
            else -> return super.onKeyDown(keyCode, event)
        }
        // Нет живой камеры (стоп-кадр, нет разрешения) — громкость работает как обычно.
        if (!camera.isActive) return super.onKeyDown(keyCode, event)
        if (!camera.zoomStep(delta)) {
            window.decorView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
        return true
    }

    companion object {
        const val ACTION_TORCH = "ru.vibecoding.lupa.TORCH"
        const val ZOOM_STEP = 0.1f
    }
}
