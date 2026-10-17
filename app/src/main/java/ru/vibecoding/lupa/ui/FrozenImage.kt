package ru.vibecoding.lupa.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

/** Масштаб и сдвиг стоп-кадра поверх зума камеры. */
@Stable
class FrozenTransform {
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set
    var size by mutableStateOf(IntSize.Zero)

    fun reset() {
        scale = 1f
        offset = Offset.Zero
    }

    /** false — уже упёрлись в предел. */
    fun zoomAround(pivot: Offset, target: Float): Boolean {
        val s = target.coerceIn(1f, MAX_SCALE)
        if (abs(s - scale) < 0.001f) return false
        offset = pivot - (pivot - offset) * (s / scale)
        scale = s
        clamp()
        return true
    }

    fun zoomCenter(factor: Float): Boolean =
        zoomAround(Offset(size.width / 2f, size.height / 2f), scale * factor)

    fun pan(delta: Offset) {
        offset += delta
        clamp()
    }

    /** Поставить точку изображения (в координатах экрана при масштабе 1) в центр. */
    fun centerOn(point: Offset) {
        offset = Offset(size.width / 2f - point.x * scale, size.height / 2f - point.y * scale)
        clamp()
    }

    private fun clamp() {
        val w = size.width.toFloat()
        val h = size.height.toFloat()
        offset = Offset(
            offset.x.coerceIn(w - w * scale, 0f),
            offset.y.coerceIn(h - h * scale, 0f),
        )
    }

    /** Видимая область в пикселях битмапа — для сохранения «как на экране». */
    fun visibleRect(bitmapWidth: Int, bitmapHeight: Int): android.graphics.Rect {
        if (size.width == 0 || size.height == 0) return android.graphics.Rect(0, 0, bitmapWidth, bitmapHeight)
        val kx = bitmapWidth / size.width.toFloat()
        val ky = bitmapHeight / size.height.toFloat()
        val left = -offset.x / scale
        val top = -offset.y / scale
        return android.graphics.Rect(
            (left * kx).roundToInt(),
            (top * ky).roundToInt(),
            ((left + size.width / scale) * kx).roundToInt(),
            ((top + size.height / scale) * ky).roundToInt(),
        )
    }

    companion object {
        const val MAX_SCALE = 8f
    }
}

@Composable
fun FrozenImage(
    bitmap: Bitmap,
    colorFilter: ColorFilter?,
    transform: FrozenTransform,
    description: String,
    modifier: Modifier = Modifier,
) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { transform.size = it }
            .pointerInput(transform) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    if (zoom != 1f) transform.zoomAround(centroid, transform.scale * zoom)
                    transform.pan(pan)
                }
            }
            .pointerInput(transform) {
                detectTapGestures(
                    onDoubleTap = { p ->
                        if (transform.scale > 1.01f) transform.reset() else transform.zoomAround(p, 3f)
                    },
                )
            }
            .semantics { contentDescription = description },
    ) {
        withTransform({
            translate(transform.offset.x, transform.offset.y)
            scale(transform.scale, transform.scale, pivot = Offset.Zero)
        }) {
            drawImage(
                image = image,
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                colorFilter = colorFilter,
            )
        }
    }
}

/** Мини-карта всего кадра с рамкой видимой области. */
@Composable
fun Minimap(
    bitmap: Bitmap,
    colorFilter: ColorFilter?,
    transform: FrozenTransform,
    description: String,
    modifier: Modifier = Modifier,
) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val ratio = bitmap.height.toFloat() / bitmap.width
    Canvas(
        modifier = modifier
            .width(MINIMAP_WIDTH)
            .height(MINIMAP_WIDTH * ratio)
            .border(2.dp, Color.White)
            .pointerInput(transform) {
                detectTapGestures { p ->
                    val k = transform.size.width / size.width.toFloat()
                    transform.centerOn(Offset(p.x * k, p.y * k))
                }
            }
            .semantics { contentDescription = description },
    ) {
        drawImage(
            image = image,
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            colorFilter = colorFilter,
        )
        val k = size.width / transform.size.width.coerceAtLeast(1)
        drawRect(
            color = Accent,
            topLeft = Offset(-transform.offset.x / transform.scale * k, -transform.offset.y / transform.scale * k),
            size = Size(size.width / transform.scale, size.height / transform.scale),
            style = Stroke(width = 2.dp.toPx()),
        )
    }
}

private val MINIMAP_WIDTH = 112.dp
