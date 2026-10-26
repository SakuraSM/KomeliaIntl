package snd.komelia.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
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

private val SkyBlue = Color(0xFF69C9FF)
private val DeepBlue = Color(0xFF00639A)
private val ErrorLight = Color(0xFFBA1A1A)
private val ErrorDark = Color(0xFFFFB4AB)

private val LightColors = lightColorScheme(
    primary = DeepBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD0E8FA),
    onPrimaryContainer = Color(0xFF00324F),
    secondary = Color(0xFF526474),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE7F0),
    onSecondaryContainer = Color(0xFF182B3B),
    tertiary = DeepBlue,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD0E8FA),
    onTertiaryContainer = Color(0xFF00324F),
    background = Color(0xFFF5F8FC),
    onBackground = Color(0xFF172330),
    surface = Color(0xFFFAFCFF),
    onSurface = Color(0xFF172330),
    surfaceVariant = Color(0xFFEDF2F8),
    onSurfaceVariant = Color(0xFF4D5E70),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF2F6FB),
    surfaceContainer = Color(0xFFEAF0F7),
    surfaceContainerHigh = Color(0xFFE2EAF3),
    surfaceContainerHighest = Color(0xFFD8E3EE),
    surfaceDim = Color(0xFFDDE6EF),
    surfaceBright = Color.White,
    outline = Color(0xFF718294),
    outlineVariant = Color(0xFFCCD8E5),
    error = ErrorLight,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = SkyBlue,
    onPrimary = Color(0xFF00324A),
    primaryContainer = Color(0xFF203B53),
    onPrimaryContainer = Color(0xFFD5EEFF),
    secondary = Color(0xFFB6C5D6),
    onSecondary = Color(0xFF203043),
    secondaryContainer = Color(0xFF26394E),
    onSecondaryContainer = Color(0xFFDCE7F4),
    tertiary = SkyBlue,
    onTertiary = Color(0xFF00324A),
    tertiaryContainer = SkyBlue,
    onTertiaryContainer = Color(0xFF00324A),
    background = Color(0xFF0D131C),
    onBackground = Color(0xFFF4F7FC),
    surface = Color(0xFF101923),
    onSurface = Color(0xFFF4F7FC),
    surfaceVariant = Color(0xFF152131),
    onSurfaceVariant = Color(0xFFADB8C9),
    surfaceContainerLowest = Color(0xFF0E1823),
    surfaceContainerLow = Color(0xFF111D2A),
    surfaceContainer = Color(0xFF172536),
    surfaceContainerHigh = Color(0xFF1D2E42),
    surfaceContainerHighest = Color(0xFF293C53),
    surfaceDim = Color(0xFF0D131C),
    surfaceBright = Color(0xFF31465E),
    outline = Color(0xFF7D90A8),
    outlineVariant = Color(0xFF2B3B4E),
    error = ErrorDark,
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val OledColors = DarkColors.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceVariant = Color(0xFF0B1520),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF07101A),
    surfaceContainer = Color(0xFF101D2B),
    surfaceContainerHigh = Color(0xFF172638),
    surfaceContainerHighest = Color(0xFF21334A),
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
            motionScheme = if (isReducedMotion) ReducedMaterialMotion else MaterialTheme.motionScheme,
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private object ReducedMaterialMotion : MotionScheme {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = snap()
}
