package snd.komelia

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import snd.komelia.settings.model.AppLanguage
import snd.komelia.ui.AppLocaleController

object AndroidAppLocaleController : AppLocaleController {
    override fun apply(language: AppLanguage) {
        val locales = when (language) {
            AppLanguage.SYSTEM -> LocaleListCompat.getEmptyLocaleList()
            AppLanguage.EN -> LocaleListCompat.forLanguageTags("en")
            AppLanguage.ZH_CN -> LocaleListCompat.forLanguageTags("zh-CN")
        }
        if (AppCompatDelegate.getApplicationLocales() != locales) {
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }
}
