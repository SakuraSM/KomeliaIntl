package snd.komelia.ui.book

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import snd.komelia.ui.KomeliaDesktopReadingTypography
import snd.komelia.ui.LocalKomeliaLayout
import snd.komelia.ui.LocalWindowWidth
import snd.komelia.ui.home.homeReadingBook
import snd.komelia.ui.komeliaLayoutSpec
import snd.komelia.ui.platform.PlatformType
import snd.komelia.ui.platform.WindowSizeClass

class DesktopReadingActionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun continueReadingFitsTheExistingDesktopButton() {
        val originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
        try {
            var read = false
            val fontScale = mutableFloatStateOf(1f)
            compose.setContent {
                CompositionLocalProvider(
                    LocalDensity provides Density(1f, fontScale.floatValue),
                    LocalWindowWidth provides WindowSizeClass.FULL,
                    LocalKomeliaLayout provides komeliaLayoutSpec(PlatformType.DESKTOP, WindowSizeClass.FULL),
                ) {
                    MaterialTheme(typography = KomeliaDesktopReadingTypography) {
                        Box(Modifier.size(600.dp, 180.dp)) {
                            ReadingActionFooter(homeReadingBook("book"), { read = it })
                        }
                    }
                }
            }

            for (scale in listOf(1f, 1.3f, 1.6f, 2f)) {
                compose.runOnIdle { fontScale.floatValue = scale }
                val label = compose.onNodeWithText("Continue reading", useUnmergedTree = true)
                val layouts = mutableListOf<TextLayoutResult>()
                label.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertEquals(1, layouts.single().lineCount, "font scale $scale")
                assertEquals("Continue reading".length, layouts.single().getLineEnd(0, visibleEnd = true), "font scale $scale")
                val bounds = label.getUnclippedBoundsInRoot()
                assertTrue(bounds.bottom > bounds.top)
            }
            compose.onNodeWithText("Continue reading").performClick()
            compose.runOnIdle { assertTrue(read) }
        } finally {
            Locale.setDefault(originalLocale)
        }
    }
}
