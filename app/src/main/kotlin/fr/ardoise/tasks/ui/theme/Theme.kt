package fr.ardoise.tasks.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import fr.ardoise.tasks.render.ArdoisePalette

val Slate = Color(ArdoisePalette.SLATE_L)
val SlateDeep = Color(ArdoisePalette.SLATE_DEEP_L)
val SlateRaised = Color(ArdoisePalette.SLATE_RAISED_L)
val Chalk = Color(ArdoisePalette.CHALK_L)
val ChalkDim = Color(ArdoisePalette.CHALK_DIM_L)
val Ochre = Color(ArdoisePalette.OCHRE_L)
val OchreSoft = Color(ArdoisePalette.OCHRE_SOFT_L)

private val ArdoiseDark = darkColorScheme(
    primary = Ochre,
    onPrimary = SlateDeep,
    primaryContainer = SlateRaised,
    onPrimaryContainer = OchreSoft,
    secondary = OchreSoft,
    onSecondary = SlateDeep,
    background = SlateDeep,
    onBackground = Chalk,
    surface = Slate,
    onSurface = Chalk,
    surfaceVariant = SlateRaised,
    onSurfaceVariant = ChalkDim,
    outline = Color(0x33F2EFE9),
    outlineVariant = Color(0x1FF2EFE9),
    error = Color(0xFFE0745F),
    onError = SlateDeep,
)

/**
 * Ardoise keeps its slate palette in light mode too.
 *
 * The app is a preview of a lock screen that is always dark; showing it on a
 * white sheet would misrepresent what the user is about to configure.
 */
private val ArdoiseLight = lightColorScheme(
    primary = Ochre,
    onPrimary = Chalk,
    primaryContainer = SlateRaised,
    onPrimaryContainer = OchreSoft,
    secondary = OchreSoft,
    onSecondary = SlateDeep,
    background = SlateDeep,
    onBackground = Chalk,
    surface = Slate,
    onSurface = Chalk,
    surfaceVariant = SlateRaised,
    onSurfaceVariant = ChalkDim,
    outline = Color(0x33F2EFE9),
    outlineVariant = Color(0x1FF2EFE9),
    error = Color(0xFFE0745F),
    onError = SlateDeep,
)

private val ArdoiseTypography = Typography(
    displaySmall = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.6.sp),
)

@Composable
fun ArdoiseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) ArdoiseDark else ArdoiseLight,
        typography = ArdoiseTypography,
        content = content,
    )
}
