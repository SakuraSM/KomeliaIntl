package snd.komelia.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import snd.komelia.settings.model.AppTheme

private val Indigo = Color(0xFF4F46E5)
private val IndigoLight = Color(0xFFA5B4FC)
private val Amber = Color(0xFFF59E0B)
private val ErrorLight = Color(0xFFBA1A1A)
private val ErrorDark = Color(0xFFFFB4AB)

private val LightColors = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = Color(0xFF1E1B4B),
    secondary = Color(0xFF5B5F97),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3E4FF),
    onSecondaryContainer = Color(0xFF191A3F),
    tertiary = Amber,
    onTertiary = Color(0xFF2A1700),
    tertiaryContainer = Color(0xFFFFE0A3),
    onTertiaryContainer = Color(0xFF261900),
    background = Color(0xFFF7F8FC),
    onBackground = Color(0xFF171A21),
    surface = Color.White,
    onSurface = Color(0xFF171A21),
    surfaceVariant = Color(0xFFEEF1F6),
    onSurfaceVariant = Color(0xFF4D5563),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF4F5FA),
    surfaceContainer = Color(0xFFEEF1F6),
    surfaceContainerHigh = Color(0xFFE7EAF1),
    surfaceContainerHighest = Color(0xFFDDE1EA),
    surfaceDim = Color(0xFFD9DCE5),
    surfaceBright = Color.White,
    outline = Color(0xFF747C8A),
    outlineVariant = Color(0xFFD7DCE5),
    error = ErrorLight,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = IndigoLight,
    onPrimary = Color(0xFF17153F),
    primaryContainer = Color(0xFF3730A3),
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = Color(0xFFC4C5FF),
    onSecondary = Color(0xFF2B2D5B),
    secondaryContainer = Color(0xFF414370),
    onSecondaryContainer = Color(0xFFE3E4FF),
    tertiary = Color(0xFFFFC56D),
    onTertiary = Color(0xFF422C00),
    tertiaryContainer = Color(0xFF5F4100),
    onTertiaryContainer = Color(0xFFFFDFA0),
    background = Color(0xFF0D0F14),
    onBackground = Color(0xFFF1F3F7),
    surface = Color(0xFF12151C),
    onSurface = Color(0xFFF1F3F7),
    surfaceVariant = Color(0xFF1A1F2A),
    onSurfaceVariant = Color(0xFFC5CBD5),
    surfaceContainerLowest = Color(0xFF090B0F),
    surfaceContainerLow = Color(0xFF12151C),
    surfaceContainer = Color(0xFF1A1F2A),
    surfaceContainerHigh = Color(0xFF202633),
    surfaceContainerHighest = Color(0xFF29303E),
    surfaceDim = Color(0xFF0D0F14),
    surfaceBright = Color(0xFF343B49),
    outline = Color(0xFF8D95A3),
    outlineVariant = Color(0xFF303745),
    error = ErrorDark,
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val OledColors = DarkColors.copy(
    background = Color.Black,
    surface = Color(0xFF07080B),
    surfaceVariant = Color(0xFF101218),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF07080B),
    surfaceContainer = Color(0xFF101218),
    surfaceContainerHigh = Color(0xFF171A22),
    surfaceContainerHighest = Color(0xFF20242E),
    surfaceDim = Color.Black,
)

val KomeliaShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

val KomeliaTypography = Typography(
    displaySmall = TextStyle(fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.SemiBold),
    headlineLarge = TextStyle(fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

object KomeliaSpacing {
    val extraSmall: Dp = 4.dp
    val small: Dp = 8.dp
    val medium: Dp = 12.dp
    val large: Dp = 16.dp
    val extraLarge: Dp = 24.dp
    val huge: Dp = 32.dp
}

@Immutable
data class KomeliaMotionSpec(
    val isReducedMotion: Boolean,
    val pressDurationMillis: Int,
    val stateDurationMillis: Int,
    val contentDurationMillis: Int,
    val containerDurationMillis: Int,
    val standardEasing: Easing,
) {
    fun duration(durationMillis: Int): Int = if (isReducedMotion) 0 else durationMillis
}

private val StandardMotionEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

val LocalKomeliaMotion = staticCompositionLocalOf {
    KomeliaMotionSpec(
        isReducedMotion = false,
        pressDurationMillis = 120,
        stateDurationMillis = 180,
        contentDurationMillis = 240,
        containerDurationMillis = 300,
        standardEasing = StandardMotionEasing,
    )
}

@Composable
fun KomeliaTheme(
    appTheme: AppTheme,
    isReducedMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val theme = Theme.valueOf(appTheme.name)
    val motion = LocalKomeliaMotion.current.copy(isReducedMotion = isReducedMotion)
    androidx.compose.runtime.CompositionLocalProvider(LocalKomeliaMotion provides motion) {
        MaterialTheme(
            colorScheme = theme.colorScheme,
            typography = KomeliaTypography,
            shapes = KomeliaShapes,
            content = content,
        )
    }
}

enum class Theme(
    val colorScheme: ColorScheme,
    val type: ThemeType,
) {
    DARK(DarkColors, ThemeType.DARK),
    LIGHT(LightColors, ThemeType.LIGHT),
    DARKER(OledColors, ThemeType.DARK);

    enum class ThemeType {
        LIGHT,
        DARK,
    }

    companion object {
        fun AppTheme.toTheme(): Theme = valueOf(name)
        fun Theme.toAppTheme(): AppTheme = AppTheme.valueOf(name)
    }
}
