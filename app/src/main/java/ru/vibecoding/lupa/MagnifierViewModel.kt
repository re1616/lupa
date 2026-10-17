package ru.vibecoding.lupa

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Context.dataStore by preferencesDataStore(name = "settings")
private val KEY_FILTER = stringPreferencesKey("filter")
private val KEY_BRIGHTNESS = floatPreferencesKey("brightness")
private val KEY_CONTRAST = floatPreferencesKey("contrast")

data class UiState(
    val zoomRatio: Float = 1f,
    val linearZoom: Float = 0f,
    val torchOn: Boolean = false,
    val hasFlash: Boolean = false,
    val frontCamera: Boolean = false,
    val hasFrontCamera: Boolean = false,
    val frozen: Bitmap? = null,
    val filter: LupaFilter = LupaFilter.NONE,
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val filtersOpen: Boolean = false,
    val fineTuneOpen: Boolean = false,
    val focusHint: Boolean = false,
)

sealed interface UiEvent {
    data object Haptic : UiEvent
    data class Saved(val uri: Uri) : UiEvent
    data object SaveFailed : UiEvent
    data class Share(val uri: Uri) : UiEvent
}

class MagnifierViewModel(app: Application) : AndroidViewModel(app), LupaCamera.Callbacks {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    private val store = app.dataStore

    init {
        viewModelScope.launch {
            val prefs = runCatching { store.data.first() }.getOrNull() ?: return@launch
            _state.update {
                it.copy(
                    filter = LupaFilter.fromKey(prefs[KEY_FILTER]),
                    brightness = prefs[KEY_BRIGHTNESS] ?: 0f,
                    contrast = prefs[KEY_CONTRAST] ?: 1f,
                )
            }
        }
        viewModelScope.launch(Dispatchers.IO) { ImageSaver.clearShareCache(app) }
    }

    // --- Камера ---

    override fun onCameraReady(hasFlash: Boolean, hasFrontCamera: Boolean) =
        _state.update { it.copy(hasFlash = hasFlash, hasFrontCamera = hasFrontCamera) }

    override fun onZoomChanged(ratio: Float, linear: Float) =
        _state.update { it.copy(zoomRatio = ratio, linearZoom = linear) }

    override fun onFocusResult(success: Boolean) =
        _state.update { it.copy(focusHint = !success) }

    fun dismissFocusHint() = _state.update { it.copy(focusHint = false) }

    fun setTorch(on: Boolean) = _state.update { it.copy(torchOn = on) }

    fun toggleTorch() = _state.update { it.copy(torchOn = !it.torchOn) }

    fun toggleFrontCamera() = _state.update {
        it.copy(frontCamera = !it.frontCamera, torchOn = false, focusHint = false)
    }

    fun freeze(bitmap: Bitmap) {
        _state.update { it.copy(frozen = bitmap, focusHint = false) }
        haptic()
    }

    fun unfreeze() = _state.update { it.copy(frozen = null) }

    // --- Фильтры ---

    fun toggleFilters() = _state.update { it.copy(filtersOpen = !it.filtersOpen, fineTuneOpen = false) }

    fun toggleFineTune() = _state.update { it.copy(fineTuneOpen = !it.fineTuneOpen) }

    fun selectFilter(filter: LupaFilter) {
        _state.update { it.copy(filter = filter) }
        persist { it[KEY_FILTER] = filter.key }
    }

    fun setBrightness(value: Float) = _state.update { it.copy(brightness = value) }

    fun setContrast(value: Float) = _state.update { it.copy(contrast = value) }

    /** Сохраняем только по отпусканию слайдера, а не на каждое движение. */
    fun commitAdjust() {
        val s = _state.value
        persist {
            it[KEY_BRIGHTNESS] = s.brightness
            it[KEY_CONTRAST] = s.contrast
        }
    }

    fun resetAdjust() {
        _state.update { it.copy(brightness = 0f, contrast = 1f) }
        commitAdjust()
    }

    // --- Сохранение и «Поделиться» ---

    fun save(visible: Rect) = exportFrozen(visible) { context, bmp ->
        val uri = ImageSaver.saveToGallery(context, bmp)
        if (uri != null) UiEvent.Saved(uri) else UiEvent.SaveFailed
    }

    fun share(visible: Rect) = exportFrozen(visible) { context, bmp ->
        val uri = ImageSaver.writeShareFile(context, bmp)
        if (uri != null) UiEvent.Share(uri) else UiEvent.SaveFailed
    }

    private fun exportFrozen(visible: Rect, write: (Context, Bitmap) -> UiEvent) {
        val s = _state.value
        val src = s.frozen ?: return
        val context: Context = getApplication()
        viewModelScope.launch {
            val event = withContext(Dispatchers.IO) {
                runCatching {
                    val baked = ImageSaver.bake(src, visible, Filters.colorFilter(s.filter, s.brightness, s.contrast))
                    write(context, baked).also { baked.recycle() }
                }.getOrDefault(UiEvent.SaveFailed)
            }
            _events.emit(event)
        }
    }

    fun haptic() {
        _events.tryEmit(UiEvent.Haptic)
    }

    private fun persist(block: (MutablePreferences) -> Unit) {
        viewModelScope.launch {
            runCatching { store.edit { block(it) } }
        }
    }
}
