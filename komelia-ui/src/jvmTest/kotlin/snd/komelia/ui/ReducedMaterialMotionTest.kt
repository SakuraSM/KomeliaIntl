package snd.komelia.ui

import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import snd.komelia.settings.model.AppTheme
import kotlin.test.assertEquals

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
class ReducedMaterialMotionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun reducedMotionReachesMaterialControlsAsWellAsPageTransitions() {
        lateinit var scheme: MotionScheme
        compose.setContent { KomeliaTheme(AppTheme.LIGHT, isReducedMotion = true) {
            scheme = MaterialTheme.motionScheme
        } }
        compose.waitForIdle()
        for (spec in listOf(
            scheme.defaultSpatialSpec<Float>(), scheme.fastSpatialSpec(), scheme.slowSpatialSpec(),
            scheme.defaultEffectsSpec(), scheme.fastEffectsSpec(), scheme.slowEffectsSpec(),
        )) {
            val animation = TargetBasedAnimation(spec, Float.VectorConverter, 0f, 1f)
            assertEquals(0L, animation.durationNanos)
            assertEquals(1f, animation.getValueFromNanos(0L))
        }
    }
}
