package snd.komelia.ui.strings

import snd.komelia.settings.model.AppLanguage

/**
 * Resolves the [AppStrings] instance to use given a user-selected [language] preference and the
 * host system's BCP-47 language tag (e.g. "zh-CN", "en-US").
 *
 * - [AppLanguage.SYSTEM]: pick the closest supported language based on [systemLanguageTag],
 *   falling back to English.
 * - Explicit choices ([AppLanguage.EN], [AppLanguage.ZH_CN]) override the system locale.
 *
 * To add a new language: add a `XxStrings` object mirroring [EnStrings], extend [AppLanguage],
 * and add cases below.
 */
fun appStringsForLanguage(language: AppLanguage, systemLanguageTag: String): AppStrings {
    return when (language) {
        AppLanguage.EN -> EnStrings
        AppLanguage.ZH_CN -> ZhCnStrings
        AppLanguage.SYSTEM -> {
            val tag = systemLanguageTag.lowercase().replace('_', '-')
            when {
                // Match Chinese variants: zh, zh-CN, zh-Hans, zh-SG, zh-Hans-CN ...
                // Until Traditional Chinese (zh-TW / zh-Hant) is provided, treat all as zh-CN.
                tag == "zh" || tag.startsWith("zh-") -> ZhCnStrings
                else -> EnStrings
            }
        }
    }
}
