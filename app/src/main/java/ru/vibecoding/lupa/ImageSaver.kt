package ru.vibecoding.lupa

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ImageSaver {
    private const val SHARE_DIR = "shared"
    private const val ALBUM = "Lupa"
    private const val QUALITY = 92

    /**
     * Видимая часть стоп-кадра с применённым фильтром — то, что на экране.
     * Результат растягивается до размера исходника, чтобы снимок было удобно смотреть.
     */
    fun bake(src: Bitmap, visible: Rect, filter: ColorFilter?): Bitmap {
        val left = visible.left.coerceIn(0, src.width - 1)
        val top = visible.top.coerceIn(0, src.height - 1)
        val crop = Rect(
            left,
            top,
            visible.right.coerceIn(left + 1, src.width),
            visible.bottom.coerceIn(top + 1, src.height),
        )
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = filter }
        Canvas(out).drawBitmap(src, crop, Rect(0, 0, out.width, out.height), paint)
        return out
    }

    @Suppress("DEPRECATION")
    fun saveToGallery(context: Context, bitmap: Bitmap): Uri? {
        val name = "Lupa_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".jpg"
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + ALBUM)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            val ok = runCatching {
                resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) } == true
            }.getOrDefault(false)
            if (!ok) {
                resolver.delete(uri, null, null)
                return null
            }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        }

        // Android 8–9: нужен WRITE_EXTERNAL_STORAGE (запрашивается в UI).
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), ALBUM)
        if (!dir.exists() && !dir.mkdirs()) return null
        val file = File(dir, name)
        val ok = runCatching {
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
        }.getOrDefault(false)
        if (!ok) return null
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.DATA, file.absolutePath)
        }
        return runCatching { resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) }.getOrNull()
    }

    /** Временный файл для «Поделиться»; удаляется при следующем запуске. */
    fun writeShareFile(context: Context, bitmap: Bitmap): Uri? = runCatching {
        val dir = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
        val file = File(dir, "lupa.jpg")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
        FileProvider.getUriForFile(context, context.packageName + ".files", file)
    }.getOrNull()

    fun clearShareCache(context: Context) {
        File(context.cacheDir, SHARE_DIR).deleteRecursively()
    }
}
