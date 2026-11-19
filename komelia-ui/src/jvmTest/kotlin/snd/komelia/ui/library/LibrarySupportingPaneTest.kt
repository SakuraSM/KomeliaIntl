package snd.komelia.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.library_all_libraries
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.navbar_libraries
import kotlinx.coroutines.runBlocking
import kotlin.test.assertTrue
import org.jetbrains.compose.resources.getString
import org.junit.Rule
import org.junit.Test
import snd.komelia.ui.LocalKomeliaLayout
import snd.komelia.ui.common.components.LocalPageTopInset
import snd.komelia.ui.komeliaLayoutSpec
import snd.komelia.ui.platform.PlatformType
import snd.komelia.ui.platform.WindowSizeClass
import snd.komga.client.library.KomgaLibraryId
import kotlin.test.assertEquals

class LibrarySupportingPaneTest {
    @get:Rule val compose = createComposeRule()

    @Test fun headingStaysAboveTheFirstSelectableLibrary() {
        var selected = false
        val heading = runBlocking { getString(Res.string.navbar_libraries) }
        val allLibraries = runBlocking { getString(Res.string.library_all_libraries) }
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalPageTopInset provides 0.dp,
                    LocalKomeliaLayout provides komeliaLayoutSpec(PlatformType.DESKTOP, WindowSizeClass.FULL),
                ) {
                    Box(Modifier.size(320.dp, 600.dp)) {
                        LibrarySupportingPane(emptyList(), null) { selected = true }
                    }
                }
            }
        }

        val titleBounds = compose.onNodeWithText(heading).getUnclippedBoundsInRoot()
        val itemBounds = compose.onNodeWithText(allLibraries).getUnclippedBoundsInRoot()
        assertTrue(itemBounds.top >= titleBounds.bottom, "Library heading overlaps the first scope option")
        compose.onNodeWithText(allLibraries).performClick()
        compose.runOnIdle { assertTrue(selected) }
    }

    @Test fun longLibraryListStillScrollsAndSelectsTheLastItem() {
        val libraries = (1..20).map {
            LibraryScopeItem(KomgaLibraryId("library-$it"), "Library $it", unavailable = false, isLocal = false)
        }
        var selected: KomgaLibraryId? = null
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalPageTopInset provides 0.dp,
                    LocalKomeliaLayout provides komeliaLayoutSpec(PlatformType.DESKTOP, WindowSizeClass.FULL),
                ) {
                    Box(Modifier.size(320.dp, 400.dp)) {
                        LibrarySupportingPane(libraries, null) { selected = it }
                    }
                }
            }
        }

        compose.onNodeWithText("Library 20").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(KomgaLibraryId("library-20"), selected) }
    }
}
