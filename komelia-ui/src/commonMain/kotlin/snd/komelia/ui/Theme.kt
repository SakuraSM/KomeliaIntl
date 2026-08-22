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

private val BlueViolet = Color(0xFF5F5D8E)
private val BlueVioletLight = Color(0xFFC5C2F0)
private val Ochre = Color(0xFF8A6428)
private val ErrorLight = Color(0xFFBA1A1A)
private val ErrorDark = Color(0xFFFFB4AB)

private val LightColors = lightColorScheme(
    primary = BlueViolet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE5E3F5),
    onPrimaryContainer = Color(0xFF25233F),
    secondary = Color(0xFF5E6473),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE1E4EA),
    onSecondaryContainer = Color(0xFF20242C),
    tertiary = Ochre,
    onTertiary = Color(0xFF2A1700),
    tertiaryContainer = Color(0xFFFFE0A3),
    onTertiaryContainer = Color(0xFF261900),
    background = Color(0xFFF8F8FC),
    onBackground = Color(0xFF1B1C20),
    surface = Color(0xFFFCFCFF),
    onSurface = Color(0xFF1B1C20),
    surfaceVariant = Color(0xFFF1F0F8),
    onSurfaceVariant = Color(0xFF555964),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF5F4FC),
    surfaceContainer = Color(0xFFEFEEF8),
    surfaceContainerHigh = Color(0xFFE9E7F4),
    surfaceContainerHighest = Color(0xFFE1DFEE),
    surfaceDim = Color(0xFFE0DFE8),
    surfaceBright = Color.White,
    outline = Color(0xFF777985),
    outlineVariant = Color(0xFFD9D7E4),
    error = ErrorLight,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = BlueVioletLight,
    onPrimary = Color(0xFF292641),
    primaryContainer = Color(0xFF454269),
    onPrimaryContainer = Color(0xFFE6E3FF),
    secondary = Color(0xFFC5C8D2),
    onSecondary = Color(0xFF2B2E36),
    secondaryContainer = Color(0xFF3F424C),
    onSecondaryContainer = Color(0xFFE1E3EB),
    tertiary = Color(0xFFFFC56D),
    onTertiary = Color(0xFF422C00),
    tertiaryContainer = Color(0xFF5F4100),
    onTertiaryContainer = Color(0xFFFFDFA0),
    background = Color(0xFF101014),
    onBackground = Color(0xFFF0F0F3),
    surface = Color(0xFF15151A),
    onSurface = Color(0xFFF0F0F3),
    surfaceVariant = Color(0xFF1D1C24),
    onSurfaceVariant = Color(0xFFC5C6CD),
    surfaceContainerLowest = Color(0xFF0B0C0E),
    surfaceContainerLow = Color(0xFF15151A),
    surfaceContainer = Color(0xFF1D1C24),
    surfaceContainerHigh = Color(0xFF25242E),
    surfaceContainerHighest = Color(0xFF2E2C38),
    surfaceDim = Color(0xFF101014),
    surfaceBright = Color(0xFF35333F),
    outline = Color(0xFF8F909A),
    outlineVariant = Color(0xFF34363E),
    error = ErrorDark,
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val OledColors = DarkColors.copy(
    background = Color.Black,
    surface = Color(0xFF08080C),
    surfaceVariant = Color(0xFF12111A),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF08080C),
    surfaceContainer = Color(0xFF12111A),
    surfaceContainerHigh = Color(0xFF191824),
    surfaceContainerHighest = Color(0xFF22202E),
    surfaceDim = Color.Black,
)

val KomeliaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
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
        pressDurationMillis = 150,
        stateDurationMillis = 180,
        contentDurationMillis = 200,
        containerDurationMillis = 200,
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
