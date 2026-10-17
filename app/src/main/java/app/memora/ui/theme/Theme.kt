package app.memora.ui.theme

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

private val MemoraDarkColorScheme = darkColorScheme(
    primary = AccentIndigo,
    onPrimary = Color.White,
    primaryContainer = AccentIndigo.copy(alpha = 0.2f),
    onPrimaryContainer = Color.White,
    secondary = AccentIndigo,
    onSecondary = Color.White,
    background = DarkBackground,
    onBackground = Color.White,
    surface = DarkSurface,
    onSurface = Color.White,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = Color(0xFF9CA3AF),
    outline = DarkOutline,
    outlineVariant = DarkOutline,
)

private val MemoraLightColorScheme = lightColorScheme(
    primary = PrimaryNavy,
    onPrimary = Color.White,
    primaryContainer = AccentIndigo.copy(alpha = 0.12f),
    onPrimaryContainer = AccentIndigo,
    secondary = AccentIndigo,
    onSecondary = Color.White,
    secondaryContainer = AccentIndigo.copy(alpha = 0.12f),
    onSecondaryContainer = AccentIndigo,
    background = SurfaceCool,
    onBackground = PrimaryNavy,
    surface = CardWhite,
    onSurface = PrimaryNavy,
    surfaceVariant = SurfaceCool,
    onSurfaceVariant = TextCaption,
    outline = BorderLight,
    outlineVariant = BorderLight,
)

@Composable
fun MemoraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Preserve Memora's signature calm utility identity
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> MemoraDarkColorScheme
        else -> MemoraLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}


