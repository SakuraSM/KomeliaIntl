package snd.komelia.ui.library

import androidx.compose.runtime.CompositionLocalProvider
import snd.komelia.ui.LocalPlatform
import snd.komelia.ui.LocalWindowWidth
import snd.komelia.ui.platform.PlatformType
import snd.komelia.ui.platform.WindowSizeClass
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.unit.dp
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.library_tab_readlists_count
import org.jetbrains.compose.resources.pluralStringResource
import org.junit.Rule
import org.junit.Test
import snd.komelia.ui.library.view.LibraryReadListsContent

class ReadListCountTest {
    @get:Rule val compose = createComposeRule()

    @Test fun summaryCountsReadListsInsteadOfCollections() {
        var expected = ""
        compose.setContent { CompositionLocalProvider(
            LocalPlatform provides PlatformType.MOBILE,
            LocalWindowWidth provides WindowSizeClass.COMPACT,
        ) { MaterialTheme {
            expected = pluralStringResource(Res.plurals.library_tab_readlists_count, 3, 3)
            LibraryReadListsContent(emptyList(), 3, {}, {}, false, 1, 1, 20, {}, {}, 240.dp)
        } } }
        compose.onNodeWithText(expected).assertIsDisplayed()
    }
}
