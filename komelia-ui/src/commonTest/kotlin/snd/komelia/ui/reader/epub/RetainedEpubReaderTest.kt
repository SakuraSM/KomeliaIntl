package snd.komelia.ui.reader.epub

import androidx.compose.ui.graphics.Color
import cafe.adriel.voyager.navigator.Navigator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.settings.model.EpubReaderType.KOMGA_EPUB
import snd.komelia.settings.model.EpubReaderType.TTSU_EPUB
import snd.komelia.ui.LoadState
import snd.webview.KomeliaWebview
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertFailsWith

class RetainedEpubReaderTest {
    @Test fun changingTheReaderPreferenceReleasesTheOldImplementation() = runTest {
        val retained = RetainedEpubReader()
        val ttu = FakeReader()
        val komga = FakeReader()
        assertSame(ttu, retained.select(TTSU_EPUB) { ttu })
        assertSame(komga, retained.select(KOMGA_EPUB) { komga })
        assertEquals(1, ttu.disposals)
        assertEquals(0, komga.disposals)
        retained.dispose()
        retained.dispose()
        assertEquals(1, komga.disposals)
    }

    @Test fun reenteringWithTheSameReaderKeepsItsSessionAndProgress() = runTest {
        val retained = RetainedEpubReader()
        val reader = FakeReader().apply { savedPosition = 42 }
        retained.select(TTSU_EPUB) { reader }
        repeat(10) { assertSame(reader, retained.select(TTSU_EPUB) { error("Must reuse the reader") }) }
        assertEquals(42, reader.savedPosition)
        assertEquals(0, reader.disposals)
    }

    @Test fun cancelledReplacementLeavesThePreviousReaderAvailable() = runTest {
        val retained = RetainedEpubReader()
        val reader = FakeReader()
        retained.select(TTSU_EPUB) { reader }
        assertFailsWith<CancellationException> {
            retained.select(KOMGA_EPUB) { throw CancellationException("Screen closed") }
        }
        assertSame(reader, retained.select(TTSU_EPUB) { error("Previous reader is still usable") })
        assertEquals(0, reader.disposals)
    }
}

private class FakeReader : EpubReaderState {
    var disposals = 0
    var savedPosition = 0
    override val state = MutableStateFlow<LoadState<Unit>>(LoadState.Success(Unit))
    override val book = MutableStateFlow<KomeliaBook?>(null)
    override val contentReady = MutableStateFlow(true)
    override val backgroundColor = MutableStateFlow(Color.White)
    override suspend fun initialize(navigator: Navigator) = Unit
    override fun onWebviewCreated(webview: KomeliaWebview) = Unit
    override fun onBackButtonPress() = Unit
    override fun closeWebview() = Unit
    override fun dispose() { disposals++ }
}
