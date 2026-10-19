package io.github.pronablua.deviceidchanger

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val lightColors = lightColorScheme(
    primary = Color(0xFF195C43),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC6EBD6),
    onPrimaryContainer = Color(0xFF073C29),
    secondary = Color(0xFF4D6356),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD0E8D7),
    onSecondaryContainer = Color(0xFF102017),
    surface = Color(0xFFF7FAF5),
    onSurface = Color(0xFF171D19),
    onSurfaceVariant = Color(0xFF414942),
    surfaceContainer = Color(0xFFEDF2EB),
    surfaceContainerLow = Color(0xFFF0F5EE),
    surfaceContainerHigh = Color(0xFFE7EDE5),
    background = Color(0xFFF7FAF5),
    outlineVariant = Color(0xFFC0CBC1)
)

private val darkColors = darkColorScheme(
    primary = Color(0xFF9CD4B6),
    onPrimary = Color(0xFF003823),
    primaryContainer = Color(0xFF004F34),
    onPrimaryContainer = Color(0xFFB8F1D1),
    secondary = Color(0xFFB3CCBA),
    onSecondary = Color(0xFF203629),
    secondaryContainer = Color(0xFF364C3F),
    onSecondaryContainer = Color(0xFFD0E8D7),
    surface = Color(0xFF101512),
    onSurface = Color(0xFFDFE5DE),
    onSurfaceVariant = Color(0xFFC0C9C1),
    surfaceContainer = Color(0xFF1D241F),
    surfaceContainerLow = Color(0xFF181F1A),
    surfaceContainerHigh = Color(0xFF283029),
    background = Color(0xFF101512),
    outlineVariant = Color(0xFF414C43)
)

@Composable
fun DeviceIdTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
    } else {
        if (dark) darkColors else lightColors
    }
    MaterialTheme(colorScheme = colors, typography = Typography(), shapes = Shapes(), content = content)
}
