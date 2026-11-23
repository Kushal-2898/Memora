package app.memora.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MemoraLightColorScheme = lightColorScheme(
    primary = DeepForestGreen,
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
    surfaceTint = Color.Transparent,
    surfaceBright = CardWhite,
    surfaceDim = SurfaceCool,
    surfaceContainerLowest = CardWhite,
    surfaceContainerLow = CardWhite,
    surfaceContainer = CardWhite,
    surfaceContainerHigh = CardWhite,
    surfaceContainerHighest = SurfaceCool,
    outline = BorderLight,
    outlineVariant = BorderLight,
)

@Composable
fun MemoraTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = MemoraLightColorScheme,
        typography = Typography,
        content = content,
    )
}



