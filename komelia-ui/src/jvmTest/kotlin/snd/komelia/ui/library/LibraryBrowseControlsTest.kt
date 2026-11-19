package snd.komelia.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.lang.reflect.Proxy
import java.util.Locale
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import snd.komelia.AppNotifications
import snd.komelia.ui.KomeliaTypography
import snd.komelia.ui.LocalPlatform
import snd.komelia.ui.platform.PlatformType

class LibraryBrowseControlsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sortLabelRemainsVisibleWithLargeTextOnPhone() {
        val originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
        try {
            val fontScale = mutableFloatStateOf(1f)
            val state = LibrarySeriesTabState(
                seriesApi = noApi(),
                referentialApi = noApi(),
                notifications = AppNotifications(),
                komgaEvents = MutableSharedFlow(),
                settingsRepository = noApi(),
                library = MutableStateFlow(null),
                taskEmitter = null,
                cardWidth = MutableStateFlow(120.dp),
            )
            compose.setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f, fontScale.floatValue), LocalPlatform provides PlatformType.MOBILE) {
                    MaterialTheme(typography = KomeliaTypography) {
                        Box(Modifier.size(360.dp, 200.dp)) { LibraryBrowseControls(state) }
                    }
                }
            }

            for (scale in listOf(1f, 1.3f, 2f)) {
                compose.runOnIdle { fontScale.floatValue = scale }
                val sort = compose.onNodeWithText("Title Ascending", useUnmergedTree = true)
                val layouts = mutableListOf<TextLayoutResult>()
                sort.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertTrue(layouts.isNotEmpty())
                assertEquals("Title Ascending".length, layouts.single().getLineEnd(0, visibleEnd = true), "font scale $scale")
                val statusLayouts = mutableListOf<TextLayoutResult>()
                compose.onNodeWithText("All status", useUnmergedTree = true)
                    .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(statusLayouts) }
                assertEquals("All status".length, statusLayouts.single().getLineEnd(0, visibleEnd = true), "font scale $scale")
                if (scale > 1f) {
                    val grid = compose.onNodeWithContentDescription("Grid view").getUnclippedBoundsInRoot()
                    assertTrue(grid.top > sort.getUnclippedBoundsInRoot().bottom, "Display controls should move below the sort label")
                }
            }
            compose.onNodeWithContentDescription("List view").performClick()
            compose.runOnIdle { assertTrue(state.isList) }
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    private inline fun <reified T> noApi(): T = Proxy.newProxyInstance(
        T::class.java.classLoader,
        arrayOf(T::class.java),
    ) { _, method, _ -> error("Unexpected call to ${method.name}") } as T
}
