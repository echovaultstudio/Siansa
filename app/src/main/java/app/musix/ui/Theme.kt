package app.musix.ui

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

private val Dark = darkColorScheme(
    primary = Color(0xFFC9B8FF),
    secondary = Color(0xFFFFD38A),
    background = Color(0xFF120E1F),
    surface = Color(0xFF120E1F),
)

private val Light = lightColorScheme(
    primary = Color(0xFF5B45C8),
    secondary = Color(0xFF8A5A00),
)

/** Follows the system light/dark setting and uses wallpaper colours on Android 12+. */
@Composable
fun MusixTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
