package com.kaushikkm.ekatrahub.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Lotus palette, used on phones older than Android 12 (newer ones use the wallpaper colours).
private val LightColors = lightColorScheme(
    primary = Color(0xFF8E3B67),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD8E7),
    secondary = Color(0xFF5C4B7D),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF6B8D1),
    onPrimary = Color(0xFF55203E),
    primaryContainer = Color(0xFF712B54),
    secondary = Color(0xFFCFBDFE),
)

@Composable
fun EkatraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
