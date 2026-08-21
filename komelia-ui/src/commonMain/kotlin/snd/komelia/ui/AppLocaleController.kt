package snd.komelia.ui

import snd.komelia.settings.model.AppLanguage

/** Applies an app-level locale before the resource environment is rebuilt. */
fun interface AppLocaleController {
    fun apply(language: AppLanguage)
}

val NoOpAppLocaleController = AppLocaleController { }

fun AppLanguage.explicitLocaleTag(): String? = when (this) {
    AppLanguage.SYSTEM -> null
    AppLanguage.EN -> "en"
    AppLanguage.ZH_CN -> "zh-CN"
}
