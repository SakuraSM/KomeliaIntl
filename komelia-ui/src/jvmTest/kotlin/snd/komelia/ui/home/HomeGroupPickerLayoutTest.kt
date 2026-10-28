package snd.komelia.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import snd.komelia.settings.model.AppSurfaceStyle
import snd.komelia.ui.common.components.KomeliaDropdownMenu
import snd.komelia.ui.common.components.LocalSurfaceAppearance
import snd.komelia.ui.common.components.SurfaceAppearance
import kotlin.test.assertEquals

class HomeGroupPickerLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun classicMenuMeasuresAndSelectsAScrolledGroup() = checkMenu(AppSurfaceStyle.CLASSIC)

    @Test fun glassMenuMeasuresAndSelectsAScrolledGroup() = checkMenu(AppSurfaceStyle.LIQUID_GLASS)

    private fun checkMenu(style: AppSurfaceStyle) {
        val selected = mutableStateOf(HOME_ALL_TAB_ID)
        val expanded = mutableStateOf(true)
        val entries = (0..12).map { HomeToolbarEntry(it, "Group $it", it) }
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalSurfaceAppearance provides SurfaceAppearance(style = style)) {
                    Box {
                        KomeliaDropdownMenu(expanded.value, onDismissRequest = { expanded.value = false }) {
                            HomeGroupPickerItems(entries, selected.value, Modifier.width(320.dp)) {
                                selected.value = it
                                expanded.value = false
                            }
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("Group 12").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(12, selected.value) }
        compose.onNodeWithText("Group 12").assertDoesNotExist()
    }
}
