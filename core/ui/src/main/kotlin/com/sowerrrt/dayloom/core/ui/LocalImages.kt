package com.sowerrrt.dayloom.core.ui

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun loadSampledImage(
    path: String,
    maxDimensionPixels: Int = DEFAULT_MAX_IMAGE_DIMENSION,
): ImageBitmap? =
    withContext(Dispatchers.IO) {
        require(maxDimensionPixels > 0) { "Maximum image dimension must be positive" }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

        val options =
            BitmapFactory.Options().apply {
                inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, maxDimensionPixels)
            }
        BitmapFactory.decodeFile(path, options)?.asImageBitmap()
    }

internal fun calculateSampleSize(
    width: Int,
    height: Int,
    maxDimensionPixels: Int,
): Int {
    require(maxDimensionPixels > 0) { "Maximum image dimension must be positive" }
    val largestDimension = maxOf(width, height).coerceAtLeast(1)
    var sampleSize = 1
    while (largestDimension / sampleSize > maxDimensionPixels) sampleSize *= 2
    return sampleSize
}

private const val DEFAULT_MAX_IMAGE_DIMENSION = 1_200
