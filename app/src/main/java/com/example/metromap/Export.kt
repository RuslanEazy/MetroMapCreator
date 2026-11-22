package com.example.metromap

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas as ComposeCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.max
import kotlin.math.min

/** Рендерит всю схему в Bitmap на белом фоне. null — если схема пустая. */
fun renderMapBitmap(map: MetroMap, tm: TextMeasurer, density: Density): Bitmap? {
    if (map.stations.isEmpty()) return null

    val padL = 100f
    val padT = 100f
    val padR = 360f // место под подписи справа
    val padB = 100f
    val minX = map.stations.minOf { it.x }
    val maxX = map.stations.maxOf { it.x }
    val minY = map.stations.minOf { it.y }
    val maxY = map.stations.maxOf { it.y }
    val worldW = maxX - minX + padL + padR
    val worldH = maxY - minY + padT + padB

    val s = min(2f, 4096f / max(worldW, worldH))
    val w = (worldW * s).toInt().coerceAtLeast(1)
    val h = (worldH * s).toInt().coerceAtLeast(1)

    val image = ImageBitmap(w, h)
    val canvas = ComposeCanvas(image)
    CanvasDrawScope().draw(density, LayoutDirection.Ltr, canvas, Size(w.toFloat(), h.toFloat())) {
        drawRect(Color.White)
        withTransform({
            scale(s, s, pivot = Offset.Zero)
            translate(padL - minX, padT - minY)
        }) {
            drawMetroMap(map, tm, LightPalette, null)
        }
    }
    return image.asAndroidBitmap()
}

/** Сохраняет PNG в галерею (Pictures/MetroMaps). */
fun saveToGallery(context: Context, bitmap: Bitmap, title: String): Boolean {
    val safe = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "metro-map" }
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "$safe.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/MetroMaps")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
    return try {
        resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        true
    } catch (e: Exception) {
        resolver.delete(uri, null, null)
        false
    }
}
