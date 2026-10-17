package ru.vibecoding.lupa

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import androidx.annotation.StringRes

/** [key] хранится в настройках — не менять у существующих фильтров. */
enum class LupaFilter(val key: String, @StringRes val labelRes: Int) {
    NONE("none", R.string.filter_none),
    INVERT("invert", R.string.filter_invert),
    GRAY("gray", R.string.filter_gray),
    YELLOW_ON_BLACK("yellow_black", R.string.filter_yellow_black),
    BLACK_ON_YELLOW("black_yellow", R.string.filter_black_yellow),
    WHITE_ON_BLUE("white_blue", R.string.filter_white_blue),
    YELLOW_ON_BLUE("yellow_blue", R.string.filter_yellow_blue);

    companion object {
        fun fromKey(key: String?): LupaFilter = entries.firstOrNull { it.key == key } ?: NONE
    }
}

object Filters {
    private const val WR = 0.299f
    private const val WG = 0.587f
    private const val WB = 0.114f

    const val BRIGHTNESS_MIN = -1f
    const val BRIGHTNESS_MAX = 1f
    const val CONTRAST_MIN = 0.5f
    const val CONTRAST_MAX = 2.5f

    /** null — изображение без изменений (фильтр не нужен вовсе). */
    fun colorFilter(filter: LupaFilter, brightness: Float, contrast: Float): ColorMatrixColorFilter? {
        val adjusted = brightness != 0f || contrast != 1f
        if (filter == LupaFilter.NONE && !adjusted) return null
        return ColorMatrixColorFilter(matrix(filter, brightness, contrast))
    }

    private fun matrix(filter: LupaFilter, brightness: Float, contrast: Float): ColorMatrix {
        // Сначала яркость/контраст, потом цветовая схема: так контраст
        // усиливает разделение текста и фона до перекраски в две краски.
        val t = (1f - contrast) * 128f + brightness * 100f
        val result = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, t,
                0f, contrast, 0f, 0f, t,
                0f, 0f, contrast, 0f, t,
                0f, 0f, 0f, 1f, 0f,
            )
        )
        result.postConcat(schemeMatrix(filter))
        return result
    }

    private fun schemeMatrix(filter: LupaFilter): ColorMatrix = when (filter) {
        LupaFilter.NONE -> ColorMatrix()
        LupaFilter.INVERT -> ColorMatrix(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f,
            )
        )
        LupaFilter.GRAY -> ColorMatrix(
            floatArrayOf(
                WR, WG, WB, 0f, 0f,
                WR, WG, WB, 0f, 0f,
                WR, WG, WB, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            )
        )
        LupaFilter.YELLOW_ON_BLACK -> duotone(fg = 0xFFFF00, bg = 0x000000)
        LupaFilter.BLACK_ON_YELLOW -> duotone(fg = 0x000000, bg = 0xFFFF00)
        LupaFilter.WHITE_ON_BLUE -> duotone(fg = 0xFFFFFF, bg = 0x0033CC)
        LupaFilter.YELLOW_ON_BLUE -> duotone(fg = 0xFFFF00, bg = 0x0033CC)
    }

    /**
     * Тёмное (обычно текст) становится цветом [fg], светлое (фон) — цветом [bg].
     * out = fg - L * (fg - bg), где L — яркость пикселя 0..1.
     */
    private fun duotone(fg: Int, bg: Int): ColorMatrix {
        val m = FloatArray(20)
        for (c in 0..2) {
            val shift = 16 - c * 8
            val f = ((fg shr shift) and 0xFF).toFloat()
            val b = ((bg shr shift) and 0xFF).toFloat()
            val d = (f - b) / 255f
            val row = c * 5
            m[row] = -d * WR
            m[row + 1] = -d * WG
            m[row + 2] = -d * WB
            m[row + 4] = f
        }
        m[18] = 1f
        return ColorMatrix(m)
    }
}
