package snd.komelia.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import snd.komelia.settings.model.AppLanguage

class AppLocaleControllerTest {
    @Test
    fun persistedLanguageValuesMapToStableBcp47Tags() {
        assertNull(AppLanguage.SYSTEM.explicitLocaleTag())
        assertEquals("en", AppLanguage.EN.explicitLocaleTag())
        assertEquals("zh-CN", AppLanguage.ZH_CN.explicitLocaleTag())
    }
}
