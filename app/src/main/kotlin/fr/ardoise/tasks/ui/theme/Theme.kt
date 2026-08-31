package fr.ardoise.tasks.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import fr.ardoise.tasks.render.ArdoisePalette

val Slate = Color(ArdoisePalette.SLATE_RAISED)
val SlateDeep = Color(ArdoisePalette.SLATE_DEEP)
val SlateRaised = Color(ArdoisePalette.SLATE_RAISED)
val Chalk = Color(ArdoisePalette.CHALK)
val ChalkDim = Color(ArdoisePalette.CHALK_DIM)
val Ochre = Color(ArdoisePalette.OCHRE)
val OchreSoft = Color(ArdoisePalette.OCHRE_SOFT)

/**
 * One scheme, used in both system themes.
 *
 * Ardoise is a preview of a lock screen, which is always dark; showing it on a
 * white sheet would misrepresent what the user is configuring. There used to be
 * a near-identical `lightColorScheme` copy alongside this one, which was worse
 * than redundant: every role it did not name kept a *light* default, so
 * components the app never styles explicitly -- the snackbar, the surface
 * containers -- flipped appearance with the system theme.
 */
private val ArdoiseScheme = darkColorScheme(
    primary = Ochre,
    onPrimary = SlateDeep,
    primaryContainer = SlateRaised,
    onPrimaryContainer = OchreSoft,
    secondary = OchreSoft,
    onSecondary = SlateDeep,
    background = SlateDeep,
    onBackground = Chalk,
    surface = Color(0xFF1C1E21),
    onSurface = Chalk,
    surfaceVariant = SlateRaised,
    onSurfaceVariant = ChalkDim,
    inverseSurface = Chalk,
    inverseOnSurface = SlateDeep,
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
fun ArdoiseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ArdoiseScheme,
        typography = ArdoiseTypography,
        content = content,
    )
}
