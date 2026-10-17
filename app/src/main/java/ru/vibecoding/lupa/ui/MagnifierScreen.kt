package ru.vibecoding.lupa.ui

import android.Manifest
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.vibecoding.lupa.Filters
import ru.vibecoding.lupa.LupaCamera
import ru.vibecoding.lupa.MagnifierViewModel
import ru.vibecoding.lupa.R
import ru.vibecoding.lupa.UiEvent

private const val ZOOM_STEP = 0.1f
private const val FROZEN_ZOOM_FACTOR = 1.5f

private data class FocusMark(val position: Offset, val locked: Boolean, val id: Long)

@Composable
fun MagnifierScreen(vm: MagnifierViewModel, camera: LupaCamera) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val transform = remember { FrozenTransform() }
    val frozen = state.frozen
    val live = frozen == null

    val matrixFilter = remember(state.filter, state.brightness, state.contrast) {
        Filters.colorFilter(state.filter, state.brightness, state.contrast)
    }
    val composeFilter = remember(matrixFilter) { matrixFilter?.asComposeColorFilter() }

    var focusMark by remember { mutableStateOf<FocusMark?>(null) }

    // --- Связь состояния с камерой ---

    LaunchedEffect(live, state.frontCamera) {
        if (live) camera.start(state.frontCamera) else camera.stop()
    }
    DisposableEffect(Unit) {
        onDispose { camera.stop() }
    }
    LaunchedEffect(state.torchOn, state.frontCamera) {
        camera.setTorch(state.torchOn && !state.frontCamera)
    }
    LaunchedEffect(matrixFilter) { camera.setFilter(matrixFilter) }
    DisposableEffect(live) {
        view.keepScreenOn = live
        onDispose { view.keepScreenOn = false }
    }
    LaunchedEffect(frozen) {
        transform.reset()
        focusMark = null
    }
    LaunchedEffect(focusMark) {
        val mark = focusMark ?: return@LaunchedEffect
        if (!mark.locked) {
            delay(1200)
            focusMark = null
        }
    }
    LaunchedEffect(state.focusHint) {
        if (state.focusHint) {
            delay(3000)
            vm.dismissFocusHint()
        }
    }

    // --- События: вибрация, сохранено, поделиться ---

    val savedText = stringResource(R.string.saved)
    val openText = stringResource(R.string.open)
    val failedText = stringResource(R.string.save_failed)
    val shareTitle = stringResource(R.string.share_title)
    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                UiEvent.Haptic -> haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                is UiEvent.Saved -> scope.launch {
                    val result = snackbar.showSnackbar(savedText, actionLabel = openText, duration = SnackbarDuration.Short)
                    if (result == SnackbarResult.ActionPerformed) openImage(context, event.uri)
                }
                UiEvent.SaveFailed -> scope.launch { snackbar.showSnackbar(failedText) }
                is UiEvent.Share -> shareImage(context, event.uri, shareTitle)
            }
        }
    }

    val currentFrozen by rememberUpdatedState(frozen)
    val storageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        val bmp = currentFrozen
        if (ok && bmp != null) {
            vm.save(transform.visibleRect(bmp.width, bmp.height))
        } else {
            scope.launch { snackbar.showSnackbar(failedText) }
        }
    }
    fun onSave(bmp: Bitmap) {
        val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            storageLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            vm.save(transform.visibleRect(bmp.width, bmp.height))
        }
    }

    // --- Миниатюры для карусели фильтров (раз в секунду, только пока панель открыта) ---

    var thumb by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(state.filtersOpen, frozen) {
        if (!state.filtersOpen) {
            thumb = null
            return@LaunchedEffect
        }
        while (true) {
            val src = frozen ?: camera.grabFrame()
            if (src != null) {
                thumb = makeThumb(src).asImageBitmap()
                if (src !== frozen) src.recycle()
            }
            if (frozen != null) break
            delay(1000)
        }
    }

    // --- Разметка ---

    val ringLight = state.frontCamera && state.torchOn
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(if (ringLight) Color.White else Color.Black)
                .padding(if (ringLight) 36.dp else 0.dp)
        ) {
            AndroidView(
                factory = {
                    camera.previewView.also { (it.parent as? ViewGroup)?.removeView(it) }
                },
                modifier = Modifier.fillMaxSize(),
            )
            if (frozen == null) {
                LiveGestureLayer(
                    camera = camera,
                    description = stringResource(R.string.live_preview),
                    onFocus = { position, lock ->
                        camera.focus(position.x, position.y, lock)
                        focusMark = FocusMark(position, lock, System.nanoTime())
                        if (lock) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                )
                focusMark?.let { mark ->
                    Canvas(Modifier.fillMaxSize()) {
                        drawCircle(
                            color = if (mark.locked) Accent else Color.White,
                            radius = 36.dp.toPx(),
                            center = mark.position,
                            style = Stroke(width = 3.dp.toPx()),
                        )
                    }
                }
            } else {
                FrozenImage(
                    bitmap = frozen,
                    colorFilter = composeFilter,
                    transform = transform,
                    description = stringResource(R.string.frozen_image),
                )
            }
        }

        // Верх: индикатор зума, подсказки, мини-карта.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 12.dp, start = 16.dp, end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ZoomIndicator(
                ratio = state.zoomRatio * (if (live) 1f else transform.scale),
                onReset = { if (live) camera.setLinearZoom(0f) else transform.reset() },
            )
            when {
                state.focusHint && live -> HintPill(stringResource(R.string.focus_hint))
                focusMark?.locked == true && live -> HintPill(stringResource(R.string.focus_locked))
            }
        }
        if (frozen != null && transform.scale > 1.01f) {
            Minimap(
                bitmap = frozen,
                colorFilter = composeFilter,
                transform = transform,
                description = stringResource(R.string.minimap),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 72.dp, end = 12.dp),
            )
        }

        // Низ: снекбар и панель управления.
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            SnackbarHost(snackbar)
            ControlPanel(
                vm = vm,
                camera = camera,
                frozen = frozen,
                transform = transform,
                thumb = thumb,
                onSave = ::onSave,
                onLimit = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
            )
        }
    }
}

@Composable
private fun ControlPanel(
    vm: MagnifierViewModel,
    camera: LupaCamera,
    frozen: Bitmap?,
    transform: FrozenTransform,
    thumb: ImageBitmap?,
    onSave: (Bitmap) -> Unit,
    onLimit: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val on = stringResource(R.string.state_on)
    val off = stringResource(R.string.state_off)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PanelBackground)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (state.filtersOpen) {
            FilterPanel(
                thumb = thumb,
                current = state.filter,
                brightness = state.brightness,
                contrast = state.contrast,
                fineTuneOpen = state.fineTuneOpen,
                onSelect = vm::selectFilter,
                onToggleFineTune = vm::toggleFineTune,
                onBrightness = vm::setBrightness,
                onContrast = vm::setContrast,
                onCommit = vm::commitAdjust,
                onReset = vm::resetAdjust,
            )
        }
        if (frozen == null) {
            ZoomSlider(linear = state.linearZoom, ratio = state.zoomRatio, onChange = camera::setLinearZoom)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundIconButton(R.drawable.ic_remove, stringResource(R.string.zoom_out)) {
                val moved = if (frozen == null) camera.zoomStep(-ZOOM_STEP) else transform.zoomCenter(1f / FROZEN_ZOOM_FACTOR)
                if (!moved) onLimit()
            }
            ShutterButton(frozen = frozen != null) {
                if (frozen != null) vm.unfreeze() else camera.grabFrame()?.let(vm::freeze)
            }
            RoundIconButton(R.drawable.ic_add, stringResource(R.string.zoom_in)) {
                val moved = if (frozen == null) camera.zoomStep(ZOOM_STEP) else transform.zoomCenter(FROZEN_ZOOM_FACTOR)
                if (!moved) onLimit()
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            if (frozen == null) {
                if (state.hasFlash || state.frontCamera) {
                    PanelButton(
                        icon = R.drawable.ic_flash,
                        label = stringResource(if (state.frontCamera) R.string.ring_light else R.string.torch),
                        selected = state.torchOn,
                        stateText = if (state.torchOn) on else off,
                        onClick = vm::toggleTorch,
                    )
                }
                PanelButton(
                    icon = R.drawable.ic_filter,
                    label = stringResource(R.string.filters),
                    selected = state.filtersOpen,
                    onClick = vm::toggleFilters,
                )
                if (state.hasFrontCamera) {
                    PanelButton(
                        icon = R.drawable.ic_switch_camera,
                        label = stringResource(R.string.switch_camera),
                        onClick = vm::toggleFrontCamera,
                    )
                }
            } else {
                PanelButton(
                    icon = R.drawable.ic_share,
                    label = stringResource(R.string.share),
                    onClick = { vm.share(transform.visibleRect(frozen.width, frozen.height)) },
                )
                PanelButton(
                    icon = R.drawable.ic_save,
                    label = stringResource(R.string.save),
                    onClick = { onSave(frozen) },
                )
                PanelButton(
                    icon = R.drawable.ic_filter,
                    label = stringResource(R.string.filters),
                    selected = state.filtersOpen,
                    onClick = vm::toggleFilters,
                )
            }
        }
    }
}

/** Крупный индикатор зума: появляется при изменении и гаснет через 2 с. Тап — сброс. */
@Composable
private fun ZoomIndicator(ratio: Float, onReset: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(ratio) {
        if (first) {
            first = false
            return@LaunchedEffect
        }
        visible = true
        delay(2000)
        visible = false
    }
    if (!visible) return
    val description = stringResource(R.string.zoom_reset_hint, ratio)
    Text(
        text = stringResource(R.string.zoom_value, ratio),
        color = Color.White,
        fontSize = 34.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(28.dp))
            .background(PanelBackground)
            .clickable(role = Role.Button, onClick = onReset)
            .semantics { contentDescription = description }
            .padding(horizontal = 24.dp, vertical = 8.dp),
    )
}

@Composable
private fun HintPill(text: String) {
    Text(
        text = text,
        color = Color.Black,
        fontSize = 17.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Accent)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    )
}

@Composable
private fun LiveGestureLayer(
    camera: LupaCamera,
    description: String,
    onFocus: (Offset, Boolean) -> Unit,
) {
    val currentOnFocus by rememberUpdatedState(onFocus)
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(camera) {
                detectTransformGestures { _, _, zoom, _ -> if (zoom != 1f) camera.pinch(zoom) }
            }
            .pointerInput(camera) {
                detectTapGestures(
                    onTap = { currentOnFocus(it, false) },
                    onLongPress = { currentOnFocus(it, true) },
                )
            }
            .semantics { contentDescription = description }
    )
}

private fun makeThumb(src: Bitmap): Bitmap {
    val side = minOf(src.width, src.height)
    val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
    val thumb = Bitmap.createScaledBitmap(square, 160, 160, true)
    if (square !== src && square !== thumb) square.recycle()
    return thumb
}

private fun openImage(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, "image/jpeg")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(intent) }
}

private fun shareImage(context: Context, uri: Uri, title: String) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("image/jpeg")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    send.clipData = ClipData.newRawUri("", uri)
    runCatching { context.startActivity(Intent.createChooser(send, title)) }
}
