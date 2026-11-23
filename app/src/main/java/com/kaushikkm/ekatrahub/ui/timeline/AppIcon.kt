package com.kaushikkm.ekatrahub.ui.timeline

import android.content.Context
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * App icons are loaded on a background thread and kept in memory.
 * Loading them on the main thread while scrolling is a classic cause of jank.
 */
private object AppIconCache {
    private const val SIZE_PX = 96
    private val cache = LruCache<String, ImageBitmap>(100)

    fun peek(packageName: String): ImageBitmap? = cache.get(packageName)

    suspend fun load(context: Context, packageName: String): ImageBitmap? =
        withContext(Dispatchers.IO) {
            cache.get(packageName) ?: runCatching {
                val bitmap = context.packageManager.getApplicationIcon(packageName).toBitmap(SIZE_PX, SIZE_PX)
                bitmap.prepareToDraw() // upload to the GPU now, not during the next frame
                bitmap.asImageBitmap()
            }.getOrNull()?.also { cache.put(packageName, it) }
        }
}

/** Black-and-white filter for icons of notifications you've already read. */
private val GrayscaleFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

@Composable
fun AppIcon(
    packageName: String,
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    grayscale: Boolean = false,
) {
    val context = LocalContext.current
    val icon by produceState(initialValue = AppIconCache.peek(packageName), packageName) {
        if (value == null) value = AppIconCache.load(context.applicationContext, packageName)
    }
    val bitmap = icon
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = modifier.size(size),
            colorFilter = if (grayscale) GrayscaleFilter else null,
        )
    } else {
        // Placeholder: first letter of the app name.
        Box(
            modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}
