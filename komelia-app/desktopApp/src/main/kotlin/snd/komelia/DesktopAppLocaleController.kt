package snd.komelia

import snd.komelia.settings.model.AppLanguage
import snd.komelia.ui.AppLocaleController
import java.util.Locale

object DesktopAppLocaleController : AppLocaleController {
    private val systemLocale = Locale.getDefault()

    override fun apply(language: AppLanguage) {
        val locale = when (language) {
            AppLanguage.SYSTEM -> systemLocale
            AppLanguage.EN -> Locale.forLanguageTag("en")
            AppLanguage.ZH_CN -> Locale.forLanguageTag("zh-CN")
        }
        if (Locale.getDefault() != locale) Locale.setDefault(locale)
    }
}
