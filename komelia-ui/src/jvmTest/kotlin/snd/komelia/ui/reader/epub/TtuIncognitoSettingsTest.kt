package snd.komelia.ui.reader.epub

import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import snd.komelia.AppNotifications
import snd.komelia.settings.EpubReaderSettingsRepository
import snd.komelia.settings.model.EpubDisplaySettings
import snd.komelia.settings.model.TtsuReaderSettings
import snd.komelia.ui.BookSiblingsContext
import snd.komelia.ui.platform.PlatformType
import snd.komga.client.book.KomgaBookId
import kotlin.test.Test
import kotlin.test.assertEquals

class TtuIncognitoSettingsTest {
    @Test fun editingIncognitoSettingsKeepsTheSavedAutoBookmarkPreference() = runTest {
        for (autoBookmark in listOf(true, false)) {
            var saved = TtsuReaderSettings(autoBookmark = autoBookmark)
            val repository = proxy<EpubReaderSettingsRepository> { name, args ->
                when (name) {
                    "getTtsuReaderSettings" -> saved
                    "putTtsuReaderSettings" -> { saved = args[0] as TtsuReaderSettings; Unit }
                    else -> error("Unexpected settings operation $name")
                }
            }
            fun reader(trackProgress: Boolean) = TtsuReaderState(
                bookId = KomgaBookId("synthetic"),
                book = null,
                bookApi = proxy { name, _ -> error("Unexpected book operation $name") },
                notifications = AppNotifications(),
                markReadProgress = trackProgress,
                serverUrl = MutableStateFlow(""),
                epubSettingsRepository = repository,
                displaySettings = MutableStateFlow(EpubDisplaySettings()),
                localeTag = "en",
                fontsRepository = proxy { name, _ -> error("Unexpected font operation $name") },
                windowState = proxy { name, _ -> error("Unexpected window operation $name") },
                platformType = PlatformType.MOBILE,
                coroutineScope = backgroundScope,
                bookSiblingsContext = BookSiblingsContext.Series,
            )

            val incognito = reader(trackProgress = false)
            val edited = incognito.getReaderSettings().copy(fontSize = 21)
            incognito.putReaderSettings(edited)
            val normal = reader(trackProgress = true).getReaderSettings()
            assertEquals(autoBookmark, normal.autoBookmark)
            assertEquals(21, normal.fontSize)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T> proxy(crossinline answer: (String, Array<out Any?>) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
            answer(method.name, args.orEmpty())
        } as T
}
