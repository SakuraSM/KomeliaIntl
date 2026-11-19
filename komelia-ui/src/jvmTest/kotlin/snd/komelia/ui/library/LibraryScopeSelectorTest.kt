package snd.komelia.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import snd.komelia.ui.LocalPlatform
import snd.komelia.ui.KomeliaTypography
import snd.komelia.ui.platform.PlatformType
import snd.komga.client.library.KomgaLibraryId

class LibraryScopeSelectorTest {
    @get:Rule val compose = createComposeRule()

    @Test fun libraryNameRemainsVisibleAtPhoneWidth() {
        val libraryId = KomgaLibraryId("test-library")
        val fontScale = mutableFloatStateOf(1f)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale.floatValue), LocalPlatform provides PlatformType.MOBILE) {
                MaterialTheme(typography = KomeliaTypography) {
                    Box(Modifier.width(360.dp)) {
                        LibraryScopeSelector(
                            listOf(LibraryScopeItem(libraryId, "All libraries", unavailable = false, isLocal = false)),
                            libraryId,
                            compact = true,
                            onSelect = {},
                        )
                    }
                }
            }
        }

        for (scale in listOf(1f, 1.3f)) {
            compose.runOnIdle { fontScale.floatValue = scale }
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText("All libraries", useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue(layouts.isNotEmpty())
            assertEquals("All libraries".length, layouts.single().getLineEnd(0, visibleEnd = true), "font scale $scale")
        }
    }
}
