package ru.vibecoding.lupa.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.vibecoding.lupa.Filters
import ru.vibecoding.lupa.LupaFilter
import ru.vibecoding.lupa.R

/** Кнопка панели: иконка + подпись, зона нажатия не меньше 72×56 dp. */
@Composable
fun PanelButton(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    selected: Boolean = false,
    stateText: String? = null,
) {
    val tint = if (selected) Accent else Color.White
    Column(
        modifier = Modifier
            .widthIn(min = 76.dp)
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Accent.copy(alpha = 0.2f) else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                if (stateText != null) stateDescription = stateText
            }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
        Text(label, color = tint, fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 2)
    }
}

@Composable
fun RoundIconButton(@DrawableRes icon: Int, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(ButtonBackground)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp))
    }
}

@Composable
fun ShutterButton(frozen: Boolean, onClick: () -> Unit) {
    val description = stringResource(if (frozen) R.string.resume else R.string.freeze)
    Box(
        modifier = Modifier
            .size(84.dp)
            .clip(CircleShape)
            .border(4.dp, Color.White, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(66.dp)
                .clip(CircleShape)
                .background(if (frozen) Accent else Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(if (frozen) R.drawable.ic_play else R.drawable.ic_pause),
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(34.dp),
            )
        }
    }
}

@Composable
fun ZoomSlider(linear: Float, ratio: Float, onChange: (Float) -> Unit) {
    val label = stringResource(R.string.zoom_label)
    val value = stringResource(R.string.zoom_value, ratio)
    Slider(
        value = linear,
        onValueChange = onChange,
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = label
                stateDescription = value
            },
        colors = SliderDefaults.colors(
            thumbColor = Accent,
            activeTrackColor = Accent,
            inactiveTrackColor = Color.White.copy(alpha = 0.3f),
        ),
    )
}

@Composable
fun FilterPanel(
    thumb: ImageBitmap?,
    current: LupaFilter,
    brightness: Float,
    contrast: Float,
    fineTuneOpen: Boolean,
    onSelect: (LupaFilter) -> Unit,
    onToggleFineTune: () -> Unit,
    onBrightness: (Float) -> Unit,
    onContrast: (Float) -> Unit,
    onCommit: () -> Unit,
    onReset: () -> Unit,
) {
    Column {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(LupaFilter.entries) { filter ->
                FilterTile(filter, thumb, filter == current, brightness, contrast) { onSelect(filter) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onToggleFineTune, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.fine_tune), color = if (fineTuneOpen) Accent else Color.White, fontSize = 15.sp)
            }
            Spacer(Modifier.weight(1f))
            if (fineTuneOpen) {
                TextButton(onClick = onReset, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.reset), color = Color.White, fontSize = 15.sp)
                }
            }
        }
        if (fineTuneOpen) {
            AdjustSlider(
                label = stringResource(R.string.brightness),
                value = brightness,
                range = Filters.BRIGHTNESS_MIN..Filters.BRIGHTNESS_MAX,
                onChange = onBrightness,
                onCommit = onCommit,
            )
            AdjustSlider(
                label = stringResource(R.string.contrast),
                value = contrast,
                range = Filters.CONTRAST_MIN..Filters.CONTRAST_MAX,
                onChange = onContrast,
                onCommit = onCommit,
            )
        }
    }
}

@Composable
private fun FilterTile(
    filter: LupaFilter,
    thumb: ImageBitmap?,
    selected: Boolean,
    brightness: Float,
    contrast: Float,
    onClick: () -> Unit,
) {
    val colorFilter = remember(filter, brightness, contrast) {
        Filters.colorFilter(filter, brightness, contrast)?.asComposeColorFilter()
    }
    Column(
        modifier = Modifier
            .width(80.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) { this.selected = selected }
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val shape = RoundedCornerShape(10.dp)
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(shape)
                .background(Color.DarkGray)
                .border(if (selected) 3.dp else 1.dp, if (selected) Accent else Color.White.copy(alpha = 0.4f), shape),
        ) {
            if (thumb != null) {
                Image(
                    bitmap = thumb,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    colorFilter = colorFilter,
                    modifier = Modifier.size(72.dp),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(filter.labelRes),
            color = if (selected) Accent else Color.White,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            lineHeight = 14.sp,
        )
    }
}

@Composable
private fun AdjustSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    onCommit: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 14.sp, modifier = Modifier.width(96.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            onValueChangeFinished = onCommit,
            valueRange = range,
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = label },
            colors = SliderDefaults.colors(
                thumbColor = Accent,
                activeTrackColor = Accent,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f),
            ),
        )
    }
}
